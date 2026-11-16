package br.edu.estoque.servico;

import br.edu.estoque.dominio.*;
import br.edu.estoque.repositorio.BancoEmMemoria;
import br.edu.estoque.repositorio.BancoEmMemoria.Dados;
import java.io.Serializable;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.*;

/** Regras da ERS 1.0. Cada mutação constitui uma única transação do banco compartilhado. */
public final class ServicoEstoque {
  private final BancoEmMemoria banco;
  private final Clock relogio;
  private final Ator ator;

  public ServicoEstoque(BancoEmMemoria banco, Clock relogio) {
    this(banco, relogio, Ator.gestorLocal());
  }

  public ServicoEstoque(BancoEmMemoria banco, Clock relogio, Ator ator) {
    this.banco = Objects.requireNonNull(banco);
    this.relogio = Objects.requireNonNull(relogio);
    this.ator = Objects.requireNonNull(ator);
  }

  private ZonedDateTime agora() {
    return ZonedDateTime.now(relogio).withZoneSameInstant(ZoneId.of("America/Sao_Paulo"));
  }

  private LocalDate hoje() {
    return agora().toLocalDate();
  }

  private void permitir(String deposito, Perfil... perfis) {
    if (ator.perfil() != Perfil.GESTOR && Arrays.stream(perfis).noneMatch(p -> p == ator.perfil()))
      throw new SecurityException("Operação não autorizada");
    if (deposito != null && !ator.permite(deposito))
      throw new SecurityException("Operação não autorizada");
  }

  private void leituraPermitida(String deposito) {
    permitir(deposito, Perfil.OPERADOR, Perfil.AUDITOR, Perfil.PROCESSO);
  }

  private String recurso(Object r, String padrao) {
    if (r instanceof Produto p) return "produto:" + p.id();
    if (r instanceof Deposito d) return "deposito:" + d.id();
    if (r instanceof Lote l) return "lote:" + l.id();
    if (r instanceof Reserva v) return "reserva:" + v.id();
    if (r instanceof Transferencia t) return "transferencia:" + t.id();
    if (r instanceof Movimentacao m) return "movimento:" + m.id();
    if (r instanceof Inventario i) return "inventario:" + i.id();
    return padrao;
  }

  private <T> T gravar(String acao, String recurso, Function<Dados, T> trabalho) {
    String correlacao = UUID.randomUUID().toString();
    try {
      return banco.transacionar(
          d -> {
            T resultado = trabalho.apply(d);
            d.eventos.add(
                new Evento(
                    UUID.randomUUID(),
                    ator.nome(),
                    acao,
                    recurso(resultado, recurso),
                    agora(),
                    "SUCESSO",
                    "OK",
                    correlacao));
            return resultado;
          });
    } catch (RuntimeException e) {
      String id = Erros.correlacao(e);
      try {
        banco.transacionar(
            d -> {
              d.eventos.add(
                  new Evento(
                      UUID.randomUUID(),
                      ator.nome(),
                      acao,
                      recurso,
                      agora(),
                      "FALHA",
                      Erros.codigo(e),
                      id));
              return null;
            });
      } catch (RuntimeException falhaAuditoria) {
        /* A operação original continua abortada se o armazenamento estiver indisponível. */
      }
      throw e;
    }
  }

  private String assinatura(String acao, Object... campos) {
    // Codificação com tamanho evita colisões entre valores que contenham separadores.
    StringBuilder s = new StringBuilder(acao);
    for (Object campo : campos) {
      String v = Objects.toString(campo, "");
      s.append('|').append(v.length()).append(':').append(v);
    }
    return s.toString();
  }

  @SuppressWarnings("unchecked")
  private <T extends Serializable> T idempotente(
      Dados d, String chave, String assinatura, Supplier<T> trabalho) {
    String k = Validacao.chave(chave);
    BancoEmMemoria.Operacao anterior = d.operacoes.get(k);
    if (anterior != null) {
      if (!anterior.assinatura().equals(assinatura))
        throw new IllegalStateException("Chave já usada com outros dados");
      return (T) anterior.resultado();
    }
    T resultado = trabalho.get();
    d.operacoes.put(k, new BancoEmMemoria.Operacao(assinatura, resultado));
    return resultado;
  }

  private Produto produto(Dados d, String sku, boolean ativo) {
    Produto p = d.produtos.get(Validacao.sku(sku));
    if (p == null || (ativo && !p.ativo()))
      throw new NoSuchElementException("Produto não encontrado ou inativo");
    return p;
  }

  private Deposito deposito(Dados d, String codigo, boolean ativo) {
    Deposito v = d.depositos.get(Validacao.codigo(codigo));
    if (v == null || (ativo && !v.ativo()))
      throw new NoSuchElementException("Depósito não encontrado ou inativo");
    return v;
  }

  private Lote lote(Dados d, UUID id) {
    Lote l = d.lotes.get(id);
    if (l == null) throw new NoSuchElementException("Lote não encontrado");
    return l;
  }

  private Reserva reserva(Dados d, UUID id) {
    Reserva r = d.reservas.get(id);
    if (r == null) throw new NoSuchElementException("Reserva não encontrada");
    return r;
  }

  private Inventario inventario(Dados d, UUID id) {
    Inventario i = d.inventarios.get(id);
    if (i == null) throw new NoSuchElementException("Inventário não encontrado");
    return i;
  }

  private List<Lote> lotes(Dados d, String sku, String deposito) {
    return d.lotes.values().stream()
        .filter(
            l ->
                l.sku().equals(sku) && l.deposito().equals(deposito) && l.quantidade().signum() > 0)
        .toList();
  }

  private BigDecimal fisico(Dados d, String sku, String deposito) {
    return lotes(d, sku, deposito).stream()
        .map(Lote::quantidade)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(3);
  }

  private BigDecimal reservado(Dados d, String sku, String deposito) {
    ZonedDateTime instante = agora();
    return d.reservas.values().stream()
        .filter(
            r ->
                r.sku().equals(sku)
                    && r.deposito().equals(deposito)
                    && r.estado() == EstadoReserva.ATIVA
                    && r.expiraEm().isAfter(instante))
        .map(Reserva::quantidade)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(3);
  }

  private BigDecimal disponivel(Dados d, String sku, String deposito) {
    return fisico(d, sku, deposito).subtract(reservado(d, sku, deposito));
  }

  private BigDecimal valor(Dados d, String sku, String deposito) {
    return lotes(d, sku, deposito).stream()
        .map(l -> l.quantidade().multiply(l.custo()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal media(Dados d, String sku, String deposito) {
    BigDecimal quantidade = fisico(d, sku, deposito);
    return quantidade.signum() == 0
        ? BigDecimal.ZERO.setScale(2)
        : valor(d, sku, deposito).divide(quantidade, 2, RoundingMode.HALF_UP);
  }

  private int expirar(Dados d) {
    ZonedDateTime instante = agora();
    int total = 0;
    for (Reserva r : d.reservas.values())
      if (ator.permite(r.deposito())
          && r.estado() == EstadoReserva.ATIVA
          && !r.expiraEm().isAfter(instante)) {
        r.estado(EstadoReserva.EXPIRADA);
        total++;
        d.eventos.add(
            new Evento(
                UUID.randomUUID(),
                "processo",
                "EXPIRAR_RESERVA",
                "reserva:" + r.id(),
                instante,
                "SUCESSO",
                "OK",
                UUID.randomUUID().toString()));
      }
    return total;
  }

  private Movimentacao movimento(
      Dados d,
      TipoMovimento tipo,
      String sku,
      String origem,
      String destino,
      BigDecimal quantidade,
      BigDecimal valor,
      String chave,
      Map<UUID, BigDecimal> itens,
      String motivo) {
    Movimentacao m =
        new Movimentacao(
            UUID.randomUUID(),
            tipo,
            sku,
            origem,
            destino,
            quantidade,
            valor.setScale(2, RoundingMode.HALF_UP),
            agora(),
            chave,
            ator.nome(),
            itens,
            motivo);
    d.movimentos.add(m);
    return m;
  }

  private record Parcela(Lote lote, BigDecimal quantidade) {}

  private List<Parcela> retirar(
      Dados d, String sku, String deposito, BigDecimal quantidade, boolean vencidos) {
    LocalDate data = hoje();
    List<Lote> ordenados =
        lotes(d, sku, deposito).stream()
            .filter(l -> vencidos || !l.validade().isBefore(data))
            .sorted(
                Comparator.comparing(Lote::validade)
                    .thenComparing(Lote::entrada)
                    .thenComparing(Lote::id))
            .toList();
    BigDecimal elegivel =
        ordenados.stream().map(Lote::quantidade).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (elegivel.compareTo(quantidade) < 0) throw new IllegalStateException("Lote vencido");
    BigDecimal restante = quantidade;
    List<Parcela> parcelas = new ArrayList<>();
    for (Lote l : ordenados) {
      if (restante.signum() == 0) break;
      BigDecimal q = l.quantidade().min(restante);
      parcelas.add(new Parcela(l.copia(), q));
      l.retirar(q);
      restante = restante.subtract(q);
    }
    return parcelas;
  }

  private Map<UUID, BigDecimal> mapa(List<Parcela> p) {
    Map<UUID, BigDecimal> r = new LinkedHashMap<>();
    p.forEach(x -> r.put(x.lote().id(), x.quantidade()));
    return r;
  }

  private BigDecimal valorParcelas(List<Parcela> p) {
    return p.stream()
        .map(x -> x.quantidade().multiply(x.lote().custo()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private void exigirDisponivel(Dados d, String sku, String deposito, BigDecimal q) {
    if (disponivel(d, sku, deposito).compareTo(q) < 0)
      throw new IllegalStateException(
          fisico(d, sku, deposito).compareTo(q) < 0
              ? "Saldo insuficiente"
              : "Saldo disponível insuficiente");
  }

  public Produto cadastrarProduto(String sku, String nome, Unidade unidade, BigDecimal minimo) {
    return gravar(
        "CADASTRAR_PRODUTO",
        "produto",
        d -> {
          permitir(null, Perfil.GESTOR);
          Produto p = new Produto(sku, nome, unidade, minimo);
          if (d.produtos.containsKey(p.sku()))
            throw new IllegalStateException("Produto já cadastrado");
          d.produtos.put(p.sku(), p);
          return p;
        });
  }

  public Produto alterarProduto(String sku, String nome, Unidade unidade, BigDecimal minimo) {
    return gravar(
        "ALTERAR_PRODUTO",
        "produto",
        d -> {
          permitir(null, Perfil.GESTOR);
          Produto p = produto(d, sku, false);
          d.historicoProdutos.add(
              new VersaoProduto(
                  p.sku(),
                  p.nome(),
                  p.unidade(),
                  p.estoqueMinimo(),
                  p.ativo(),
                  ator.nome(),
                  agora()));
          p.alterar(nome, unidade, minimo);
          return p;
        });
  }

  public Produto inativarProduto(String sku) {
    return gravar(
        "INATIVAR_PRODUTO",
        "produto",
        d -> {
          permitir(null, Perfil.GESTOR);
          expirar(d);
          Produto p = produto(d, sku, true);
          if (d.reservas.values().stream()
              .anyMatch(r -> r.sku().equals(p.sku()) && r.estado() == EstadoReserva.ATIVA))
            throw new IllegalStateException("Produto possui reserva ativa");
          d.historicoProdutos.add(
              new VersaoProduto(
                  p.sku(),
                  p.nome(),
                  p.unidade(),
                  p.estoqueMinimo(),
                  p.ativo(),
                  ator.nome(),
                  agora()));
          p.inativar();
          return p;
        });
  }

  public List<VersaoProduto> historicoProduto(String sku) {
    return banco.ler(
        d -> {
          leituraPermitida(null);
          produto(d, sku, false);
          return d.historicoProdutos.stream()
              .filter(h -> h.sku().equals(Validacao.sku(sku)))
              .toList();
        });
  }

  public Deposito cadastrarDeposito(String codigo, String nome) {
    return gravar(
        "CADASTRAR_DEPOSITO",
        "deposito",
        d -> {
          permitir(null, Perfil.GESTOR);
          Deposito v = new Deposito(codigo, nome);
          if (d.depositos.containsKey(v.codigo()))
            throw new IllegalStateException("Depósito já cadastrado");
          if (d.depositos.size() >= banco.limiteDepositos())
            throw new IllegalStateException("Limite de depósitos atingido");
          d.depositos.put(v.codigo(), v);
          return v;
        });
  }

  public Deposito inativarDeposito(String codigo) {
    return gravar(
        "INATIVAR_DEPOSITO",
        "deposito",
        d -> {
          permitir(codigo, Perfil.GESTOR);
          Deposito v = deposito(d, codigo, true);
          v.inativar();
          return v;
        });
  }

  public List<Produto> produtos() {
    return banco.ler(
        d -> {
          leituraPermitida(null);
          return d.produtos.values().stream().map(Produto::copia).toList();
        });
  }

  public List<Deposito> depositos() {
    return banco.ler(
        d -> {
          leituraPermitida(null);
          return d.depositos.values().stream()
              .filter(x -> ator.permite(x.codigo()))
              .map(Deposito::copia)
              .toList();
        });
  }

  public List<Lote> lotes(String sku, String deposito) {
    return banco.ler(
        d -> {
          leituraPermitida(deposito);
          String s = produto(d, sku, false).sku(), v = deposito(d, deposito, false).codigo();
          return d.lotes.values().stream()
              .filter(l -> l.sku().equals(s) && l.deposito().equals(v))
              .map(Lote::copia)
              .toList();
        });
  }

  public Lote registrarEntrada(
      String sku,
      String deposito,
      String numero,
      BigDecimal quantidade,
      BigDecimal custo,
      LocalDate validade) {
    return registrarEntrada(
        sku, deposito, "PADRAO", numero, quantidade, custo, validade, UUID.randomUUID().toString());
  }

  public Lote registrarEntrada(
      String sku,
      String deposito,
      String fornecedor,
      String numero,
      BigDecimal quantidade,
      BigDecimal custo,
      LocalDate validade,
      String chave) {
    return gravar(
        "REGISTRAR_ENTRADA",
        "lote",
        d -> {
          permitir(deposito, Perfil.OPERADOR);
          String s = produto(d, sku, true).sku(),
              v = deposito(d, deposito, true).codigo(),
              f = Validacao.codigo(fornecedor),
              n = Validacao.texto(numero, "Lote inválido");
          BigDecimal q = Validacao.quantidade(quantidade, false), c = Validacao.custo(custo);
          Validacao.data(validade);
          String assinatura = assinatura("ENTRADA", s, v, f, n, q, c, validade);
          UUID id =
              idempotente(
                  d,
                  chave,
                  assinatura,
                  () -> {
                    if (validade.isBefore(hoje()))
                      throw new IllegalArgumentException("Lote vencido");
                    String identidade = assinatura("LOTE", s, f, n);
                    if (d.lotesOriginais.containsKey(identidade))
                      throw new IllegalStateException(
                          "Lote já cadastrado para produto e fornecedor");
                    Lote l = new Lote(UUID.randomUUID(), null, n, s, v, f, q, c, validade, agora());
                    d.lotes.put(l.id(), l);
                    d.lotesOriginais.put(identidade, l.raiz());
                    movimento(
                        d,
                        TipoMovimento.ENTRADA,
                        s,
                        null,
                        v,
                        q,
                        q.multiply(c),
                        chave,
                        Map.of(l.id(), q),
                        null);
                    return l.id();
                  });
          return d.lotes.get(id);
        });
  }

  public Posicao posicao(String sku, String deposito) {
    return banco.ler(
        d -> {
          leituraPermitida(deposito);
          String s = produto(d, sku, false).sku(), v = deposito(d, deposito, false).codigo();
          return posicao(d, s, v);
        });
  }

  private Posicao posicao(Dados d, String sku, String deposito) {
    BigDecimal f = fisico(d, sku, deposito), r = reservado(d, sku, deposito);
    return new Posicao(
        sku,
        deposito,
        f,
        r,
        f.subtract(r),
        media(d, sku, deposito),
        valor(d, sku, deposito).setScale(2, RoundingMode.HALF_UP),
        f.signum() >= 0 && r.signum() >= 0 && f.compareTo(r) >= 0);
  }

  public BigDecimal saldoFisico(String sku, String deposito) {
    return posicao(sku, deposito).fisico();
  }

  public BigDecimal saldoReservado(String sku, String deposito) {
    return posicao(sku, deposito).reservado();
  }

  public BigDecimal saldoDisponivel(String sku, String deposito) {
    return posicao(sku, deposito).disponivel();
  }

  public BigDecimal custoMedio(String sku, String deposito) {
    return posicao(sku, deposito).custoMedio();
  }

  public Reserva reservar(
      String sku, String deposito, BigDecimal quantidade, ZonedDateTime expiraEm) {
    return reservar(sku, deposito, quantidade, expiraEm, UUID.randomUUID().toString());
  }

  public Reserva reservar(
      String sku, String deposito, BigDecimal quantidade, ZonedDateTime expiraEm, String chave) {
    return gravar(
        "RESERVAR",
        "reserva",
        d -> {
          permitir(deposito, Perfil.OPERADOR);
          expirar(d);
          String s = produto(d, sku, true).sku(), v = deposito(d, deposito, true).codigo();
          BigDecimal q = Validacao.quantidade(quantidade, false);
          if (expiraEm == null) throw new IllegalArgumentException("Expiração inválida");
          Validacao.data(expiraEm.toLocalDate());
          UUID id =
              idempotente(
                  d,
                  chave,
                  assinatura("RESERVAR", s, v, q, expiraEm.toInstant()),
                  () -> {
                    if (!expiraEm.isAfter(agora()))
                      throw new IllegalArgumentException("Expiração deve ser futura");
                    if (disponivel(d, s, v).compareTo(q) < 0)
                      throw new IllegalStateException("Saldo disponível insuficiente");
                    Reserva r = new Reserva(s, v, q, expiraEm);
                    d.reservas.put(r.id(), r);
                    return r.id();
                  });
          return d.reservas.get(id);
        });
  }

  public Reserva cancelarReserva(UUID id) {
    return gravar(
        "CANCELAR_RESERVA",
        "reserva:" + id,
        d -> {
          Reserva r = reserva(d, id);
          permitir(r.deposito(), Perfil.OPERADOR);
          expirar(d);
          if (r.estado() == EstadoReserva.CANCELADA) return r;
          if (r.estado() != EstadoReserva.ATIVA)
            throw new IllegalStateException("Reserva não está ativa");
          r.estado(EstadoReserva.CANCELADA);
          return r;
        });
  }

  public Movimentacao atenderReserva(UUID id, String chave) {
    return gravar(
        "ATENDER_RESERVA",
        "reserva:" + id,
        d -> {
          Reserva r = reserva(d, id);
          permitir(r.deposito(), Perfil.OPERADOR);
          expirar(d);
          return idempotente(
              d,
              chave,
              assinatura("ATENDER", id),
              () -> {
                if (r.estado() != EstadoReserva.ATIVA)
                  throw new IllegalStateException("Reserva não está ativa");
                produto(d, r.sku(), true);
                deposito(d, r.deposito(), true);
                List<Parcela> baixa = retirar(d, r.sku(), r.deposito(), r.quantidade(), false);
                r.estado(EstadoReserva.ATENDIDA);
                return movimento(
                    d,
                    TipoMovimento.SAIDA,
                    r.sku(),
                    r.deposito(),
                    null,
                    r.quantidade(),
                    valorParcelas(baixa),
                    chave,
                    mapa(baixa),
                    "Atendimento de reserva " + r.id());
              });
        });
  }

  public int expirarReservas() {
    return gravar(
        "EXPIRAR_RESERVAS",
        "reservas",
        d -> {
          permitir(null, Perfil.PROCESSO);
          return expirar(d);
        });
  }

  public List<Reserva> reservas() {
    return banco.ler(
        d -> {
          leituraPermitida(null);
          return d.reservas.values().stream()
              .filter(r -> ator.permite(r.deposito()))
              .map(
                  r -> {
                    Reserva copia = r.copia();
                    if (copia.estado() == EstadoReserva.ATIVA && !copia.expiraEm().isAfter(agora()))
                      copia.estado(EstadoReserva.EXPIRADA);
                    return copia;
                  })
              .toList();
        });
  }

  public void registrarSaida(String sku, String deposito, BigDecimal quantidade) {
    registrarSaida(sku, deposito, quantidade, UUID.randomUUID().toString());
  }

  public Movimentacao registrarSaida(
      String sku, String deposito, BigDecimal quantidade, String chave) {
    return gravar(
        "REGISTRAR_SAIDA",
        "movimento",
        d -> {
          permitir(deposito, Perfil.OPERADOR);
          expirar(d);
          String s = produto(d, sku, true).sku(), v = deposito(d, deposito, true).codigo();
          BigDecimal q = Validacao.quantidade(quantidade, false);
          return idempotente(
              d,
              chave,
              assinatura("SAIDA", s, v, q),
              () -> {
                exigirDisponivel(d, s, v, q);
                List<Parcela> baixa = retirar(d, s, v, q, false);
                return movimento(
                    d,
                    TipoMovimento.SAIDA,
                    s,
                    v,
                    null,
                    q,
                    valorParcelas(baixa),
                    chave,
                    mapa(baixa),
                    null);
              });
        });
  }

  public Transferencia transferir(
      String sku, String origem, String destino, BigDecimal quantidade, String chave) {
    return transferirItens(origem, destino, List.of(new ItemTransferencia(sku, quantidade)), chave);
  }

  public Transferencia transferirItens(
      String origem, String destino, List<ItemTransferencia> itens, String chave) {
    return gravar(
        "TRANSFERIR",
        "transferencia",
        d -> {
          permitir(origem, Perfil.OPERADOR);
          permitir(destino, Perfil.OPERADOR);
          expirar(d);
          String o = deposito(d, origem, true).codigo(), v = deposito(d, destino, true).codigo();
          if (o.equals(v)) throw new IllegalArgumentException("Depósitos iguais");
          if (itens == null || itens.isEmpty())
            throw new IllegalArgumentException("Informe os itens da transferência");
          Map<String, BigDecimal> agrupados = new TreeMap<>();
          for (ItemTransferencia item : itens) {
            if (item == null) throw new IllegalArgumentException("Item inválido");
            produto(d, item.sku(), true);
            agrupados.merge(item.sku(), item.quantidade(), BigDecimal::add);
          }
          List<ItemTransferencia> normalizados =
              agrupados.entrySet().stream()
                  .map(e -> new ItemTransferencia(e.getKey(), e.getValue()))
                  .toList();
          String assinatura = assinatura("TRANSFERIR", o, v, normalizados);
          return idempotente(
              d,
              chave,
              assinatura,
              () -> {
                for (ItemTransferencia item : normalizados)
                  exigirDisponivel(d, item.sku(), o, item.quantidade());
                List<UUID> destinoLotes = new ArrayList<>();
                for (ItemTransferencia item : normalizados) {
                  List<Parcela> baixa = retirar(d, item.sku(), o, item.quantidade(), false);
                  for (Parcela parcela : baixa) {
                    Lote l = parcela.lote();
                    Lote existente =
                        d.lotes.values().stream()
                            .filter(x -> x.raiz().equals(l.raiz()) && x.deposito().equals(v))
                            .findFirst()
                            .orElse(null);
                    if (existente == null) {
                      existente =
                          new Lote(
                              UUID.randomUUID(),
                              l.raiz(),
                              l.numero(),
                              l.sku(),
                              v,
                              l.fornecedor(),
                              parcela.quantidade(),
                              l.custo(),
                              l.validade(),
                              l.entrada());
                      d.lotes.put(existente.id(), existente);
                    } else existente.adicionar(parcela.quantidade());
                    destinoLotes.add(existente.id());
                  }
                  movimento(
                      d,
                      TipoMovimento.TRANSFERENCIA,
                      item.sku(),
                      o,
                      v,
                      item.quantidade(),
                      valorParcelas(baixa),
                      chave,
                      mapa(baixa),
                      null);
                }
                BigDecimal total =
                    normalizados.stream()
                        .map(ItemTransferencia::quantidade)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(3);
                Transferencia t =
                    new Transferencia(
                        UUID.randomUUID(),
                        normalizados.size() == 1 ? normalizados.get(0).sku() : null,
                        o,
                        v,
                        total,
                        chave,
                        agora(),
                        destinoLotes,
                        "CONCLUIDA",
                        normalizados);
                d.transferencias.put(chave, t);
                return t;
              });
        });
  }

  public Movimentacao ajustarLote(UUID loteId, BigDecimal variacao, String motivo, String chave) {
    return gravar(
        "AJUSTAR",
        "lote:" + loteId,
        d -> {
          Lote l = lote(d, loteId);
          try {
            permitir(l.deposito(), Perfil.GESTOR);
          } catch (SecurityException e) {
            throw new SecurityException("Ajuste não autorizado");
          }
          produto(d, l.sku(), true);
          deposito(d, l.deposito(), true);
          expirar(d);
          BigDecimal q = Validacao.variacao(variacao);
          String justificativa = Validacao.motivo(motivo);
          return idempotente(
              d,
              chave,
              assinatura("AJUSTAR", loteId, q, justificativa),
              () -> aplicarAjuste(d, l, q, justificativa, chave));
        });
  }

  private Movimentacao aplicarAjuste(Dados d, Lote l, BigDecimal q, String motivo, String chave) {
    BigDecimal finalLote = l.quantidade().add(q);
    Validacao.quantidade(finalLote, true);
    if (fisico(d, l.sku(), l.deposito()).add(q).compareTo(reservado(d, l.sku(), l.deposito())) < 0)
      throw new IllegalStateException("Saldo reservado impede o ajuste");
    if (q.signum() > 0) l.adicionar(q);
    else l.retirar(q.negate());
    return movimento(
        d,
        TipoMovimento.AJUSTE,
        l.sku(),
        q.signum() < 0 ? l.deposito() : null,
        q.signum() > 0 ? l.deposito() : null,
        q,
        q.multiply(l.custo()),
        chave,
        Map.of(l.id(), q),
        motivo);
  }

  public Movimentacao descartar(UUID loteId, BigDecimal quantidade, String motivo, String chave) {
    return gravar(
        "DESCARTAR",
        "lote:" + loteId,
        d -> {
          Lote l = lote(d, loteId);
          permitir(l.deposito(), Perfil.GESTOR);
          produto(d, l.sku(), true);
          deposito(d, l.deposito(), true);
          expirar(d);
          BigDecimal q = Validacao.quantidade(quantidade, false);
          String justificativa = Validacao.motivo(motivo);
          return idempotente(
              d,
              chave,
              assinatura("DESCARTAR", loteId, q, justificativa),
              () -> {
                exigirDisponivel(d, l.sku(), l.deposito(), q);
                l.retirar(q);
                return movimento(
                    d,
                    TipoMovimento.DESCARTE,
                    l.sku(),
                    l.deposito(),
                    null,
                    q,
                    q.multiply(l.custo()),
                    chave,
                    Map.of(l.id(), q),
                    justificativa);
              });
        });
  }

  public Inventario abrirInventario(String deposito, String chave) {
    return gravar(
        "ABRIR_INVENTARIO",
        "inventario",
        d -> {
          permitir(deposito, Perfil.GESTOR);
          String v = deposito(d, deposito, true).codigo();
          UUID id =
              idempotente(
                  d,
                  chave,
                  assinatura("ABRIR_INVENTARIO", v),
                  () -> {
                    if (d.inventarios.values().stream()
                        .anyMatch(
                            i ->
                                i.deposito().equals(v)
                                    && i.estado() != EstadoInventario.CANCELADO
                                    && i.estado() != EstadoInventario.CONCLUIDO))
                      throw new IllegalStateException("Inventário em andamento neste depósito");
                    Map<UUID, BigDecimal> referencia = new LinkedHashMap<>();
                    d.lotes.values().stream()
                        .filter(l -> l.deposito().equals(v))
                        .forEach(l -> referencia.put(l.id(), l.quantidade()));
                    Inventario i = new Inventario(v, agora(), referencia);
                    d.inventarios.put(i.id(), i);
                    return i.id();
                  });
          return d.inventarios.get(id).copia();
        });
  }

  public Inventario iniciarInventario(UUID id) {
    return gravar(
        "INICIAR_INVENTARIO",
        "inventario:" + id,
        d -> {
          Inventario i = inventario(d, id);
          permitir(i.deposito(), Perfil.GESTOR);
          deposito(d, i.deposito(), true);
          i.iniciar();
          return i.copia();
        });
  }

  public Inventario registrarContagem(UUID id, UUID loteId, BigDecimal quantidade) {
    return gravar(
        "CONTAR_INVENTARIO",
        "inventario:" + id,
        d -> {
          Inventario i = inventario(d, id);
          permitir(i.deposito(), Perfil.GESTOR);
          Lote l = lote(d, loteId);
          produto(d, l.sku(), true);
          deposito(d, i.deposito(), true);
          if (!i.deposito().equals(l.deposito()) || !i.referencia().containsKey(loteId))
            throw new IllegalArgumentException("Lote não pertence à referência do inventário");
          i.contar(
              new RegistroContagem(
                  loteId, Validacao.quantidade(quantidade, true), ator.nome(), agora()));
          return i.copia();
        });
  }

  public Inventario revisarInventario(UUID id) {
    return gravar(
        "REVISAR_INVENTARIO",
        "inventario:" + id,
        d -> {
          Inventario i = inventario(d, id);
          permitir(i.deposito(), Perfil.GESTOR);
          i.revisar();
          return i.copia();
        });
  }

  public Inventario cancelarInventario(UUID id) {
    return gravar(
        "CANCELAR_INVENTARIO",
        "inventario:" + id,
        d -> {
          Inventario i = inventario(d, id);
          permitir(i.deposito(), Perfil.GESTOR);
          i.cancelar();
          return i.copia();
        });
  }

  public Inventario concluirInventario(UUID id, Set<UUID> aprovados, String motivo) {
    return gravar(
        "CONCLUIR_INVENTARIO",
        "inventario:" + id,
        d -> {
          Inventario i = inventario(d, id);
          permitir(i.deposito(), Perfil.GESTOR);
          deposito(d, i.deposito(), true);
          expirar(d);
          String justificativa = Validacao.motivo(motivo);
          if (aprovados == null)
            throw new IllegalArgumentException("Informe as contagens aprovadas");
          String assinatura =
              assinatura("CONCLUIR", id, aprovados.stream().sorted().toList(), justificativa);
          if (i.estado() == EstadoInventario.CONCLUIDO) {
            if (!assinatura.equals(i.assinaturaConclusao()))
              throw new IllegalStateException("Chave já usada com outros dados");
            return i.copia();
          }
          if (i.estado() != EstadoInventario.EM_REVISAO)
            throw new IllegalStateException("Inventário não permite esta operação");
          if (!i.contagens().keySet().containsAll(aprovados))
            throw new IllegalArgumentException("Contagem aprovada não encontrada");
          Map<UUID, BigDecimal> variacoes = new LinkedHashMap<>();
          for (UUID loteId : aprovados) {
            Lote l = lote(d, loteId);
            produto(d, l.sku(), true);
            if (l.quantidade().compareTo(i.referencia().get(loteId)) != 0)
              throw new IllegalStateException(
                  "Inventário desatualizado: houve movimentação após a abertura");
            BigDecimal diferenca = i.contagens().get(loteId).subtract(i.referencia().get(loteId));
            if (diferenca.signum() != 0) variacoes.put(loteId, diferenca);
          }
          List<UUID> ajustes = new ArrayList<>();
          for (var item :
              variacoes.entrySet().stream()
                  .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed())
                  .toList()) {
            String chave =
                UUID.nameUUIDFromBytes(
                        (id.toString() + item.getKey()).getBytes(StandardCharsets.UTF_8))
                    .toString();
            ajustes.add(
                aplicarAjuste(d, lote(d, item.getKey()), item.getValue(), justificativa, chave)
                    .id());
          }
          i.concluir(assinatura, ajustes);
          return i.copia();
        });
  }

  public List<Inventario> inventarios() {
    return banco.ler(
        d -> {
          leituraPermitida(null);
          return d.inventarios.values().stream()
              .filter(i -> ator.permite(i.deposito()))
              .map(Inventario::copia)
              .toList();
        });
  }

  public boolean requerReposicao(String sku, String deposito) {
    return banco.ler(
        d -> {
          leituraPermitida(deposito);
          Produto p = produto(d, sku, false);
          Deposito v = deposito(d, deposito, false);
          return p.ativo()
              && v.ativo()
              && disponivel(d, p.sku(), v.codigo()).compareTo(p.estoqueMinimo()) <= 0;
        });
  }

  public List<Lote> lotesProximosDoVencimento() {
    return banco.ler(
        d -> {
          leituraPermitida(null);
          LocalDate data = hoje();
          return d.lotes.values().stream()
              .filter(
                  l ->
                      ator.permite(l.deposito())
                          && l.quantidade().signum() > 0
                          && d.produtos.get(l.sku()).ativo()
                          && d.depositos.get(l.deposito()).ativo()
                          && !l.validade().isBefore(data)
                          && !l.validade().isAfter(data.plusDays(30)))
              .map(Lote::copia)
              .toList();
        });
  }

  public List<Alerta> gerarAlertas() {
    return gravar(
        "GERAR_ALERTAS",
        "alertas",
        d -> {
          permitir(null, Perfil.PROCESSO);
          expirar(d);
          LocalDate data = hoje();
          for (Produto p : d.produtos.values())
            if (p.ativo())
              for (Deposito v : d.depositos.values())
                if (v.ativo()
                    && ator.permite(v.codigo())
                    && disponivel(d, p.sku(), v.codigo()).compareTo(p.estoqueMinimo()) <= 0) {
                  String key = assinatura("REPOSICAO", data, p.sku(), v.codigo());
                  d.alertas.putIfAbsent(
                      key,
                      new Alerta(
                          UUID.randomUUID(),
                          "REPOSICAO",
                          p.sku(),
                          v.codigo(),
                          null,
                          data,
                          "Estoque disponível no mínimo ou abaixo dele."));
                }
          for (Lote l : d.lotes.values())
            if (ator.permite(l.deposito())
                && l.quantidade().signum() > 0
                && d.produtos.get(l.sku()).ativo()
                && d.depositos.get(l.deposito()).ativo()
                && !l.validade().isBefore(data)
                && !l.validade().isAfter(data.plusDays(30))) {
              String key = assinatura("VALIDADE", data, l.id());
              d.alertas.putIfAbsent(
                  key,
                  new Alerta(
                      UUID.randomUUID(),
                      "VALIDADE",
                      l.sku(),
                      l.deposito(),
                      l.id(),
                      data,
                      "Lote com vencimento em até 30 dias."));
            }
          return d.alertas.values().stream()
              .filter(a -> a.dia().equals(data) && ator.permite(a.deposito()))
              .toList();
        });
  }

  public List<Alerta> alertas() {
    return banco.ler(
        d -> {
          leituraPermitida(null);
          return d.alertas.values().stream().filter(a -> ator.permite(a.deposito())).toList();
        });
  }

  private void intervalo(ZonedDateTime inicio, ZonedDateTime fim) {
    if (inicio == null || fim == null || inicio.isAfter(fim))
      throw new IllegalArgumentException("Intervalo inválido");
  }

  private List<Movimentacao> filtrar(
      Dados d,
      String sku,
      String deposito,
      TipoMovimento tipo,
      ZonedDateTime inicio,
      ZonedDateTime fim) {
    intervalo(inicio, fim);
    String s = sku == null || sku.isBlank() ? null : Validacao.sku(sku),
        v = deposito == null || deposito.isBlank() ? null : Validacao.codigo(deposito);
    if (v != null) leituraPermitida(v);
    return d.movimentos.stream()
        .filter(
            m ->
                (s == null || m.sku().equals(s))
                    && (v == null || v.equals(m.origem()) || v.equals(m.destino()))
                    && (tipo == null || m.tipo() == tipo)
                    && !m.instante().isBefore(inicio)
                    && !m.instante().isAfter(fim)
                    && (m.origem() == null || ator.permite(m.origem()))
                    && (m.destino() == null || ator.permite(m.destino())))
        .toList();
  }

  public List<Movimentacao> movimentos(
      String sku, String deposito, TipoMovimento tipo, ZonedDateTime inicio, ZonedDateTime fim) {
    return banco.ler(
        d -> {
          leituraPermitida(deposito);
          return filtrar(d, sku, deposito, tipo, inicio, fim);
        });
  }

  public List<Evento> eventos(ZonedDateTime inicio, ZonedDateTime fim) {
    return banco.ler(
        d -> {
          permitir(null, Perfil.AUDITOR);
          intervalo(inicio, fim);
          // Auditoria global é restrita aos perfis com acesso a todos os depósitos.
          if (!ator.depositos().contains("*"))
            throw new SecurityException("Operação não autorizada");
          return d.eventos.stream()
              .filter(e -> !e.instante().isBefore(inicio) && !e.instante().isAfter(fim))
              .toList();
        });
  }

  public Relatorio relatorio(String sku, String deposito, ZonedDateTime inicio, ZonedDateTime fim) {
    return banco.ler(
        d -> {
          permitir(deposito, Perfil.AUDITOR);
          intervalo(inicio, fim);
          List<Movimentacao> ms = filtrar(d, sku, deposito, null, inicio, fim);
          List<Posicao> posicoes = new ArrayList<>();
          for (Produto p : d.produtos.values())
            if (sku == null || sku.isBlank() || p.sku().equals(Validacao.sku(sku)))
              for (Deposito v : d.depositos.values())
                if (ator.permite(v.codigo())
                    && (deposito == null
                        || deposito.isBlank()
                        || v.codigo().equals(Validacao.codigo(deposito))))
                  posicoes.add(posicao(d, p.sku(), v.codigo()));
          BigDecimal entradas =
              ms.stream()
                  .filter(m -> m.tipo() == TipoMovimento.ENTRADA)
                  .map(Movimentacao::quantidade)
                  .reduce(BigDecimal.ZERO, BigDecimal::add);
          BigDecimal saidas =
              ms.stream()
                  .filter(m -> m.tipo() == TipoMovimento.SAIDA)
                  .map(Movimentacao::quantidade)
                  .reduce(BigDecimal.ZERO, BigDecimal::add);
          BigDecimal perdas =
              ms.stream()
                  .filter(
                      m ->
                          m.tipo() == TipoMovimento.DESCARTE
                              || (m.tipo() == TipoMovimento.AJUSTE && m.quantidade().signum() < 0))
                  .map(m -> m.quantidade().abs())
                  .reduce(BigDecimal.ZERO, BigDecimal::add);
          BigDecimal valorPerdas =
              ms.stream()
                  .filter(
                      m ->
                          m.tipo() == TipoMovimento.DESCARTE
                              || (m.tipo() == TipoMovimento.AJUSTE && m.quantidade().signum() < 0))
                  .map(m -> m.valor().abs())
                  .reduce(BigDecimal.ZERO, BigDecimal::add);
          BigDecimal valorEstoque =
              posicoes.stream()
                  .map(Posicao::valorEstoque)
                  .reduce(BigDecimal.ZERO, BigDecimal::add)
                  .setScale(2, RoundingMode.HALF_UP);
          // Giro financeiro = custo das saídas / média entre valor inicial e valor final no
          // período.
          BigDecimal finalPeriodo = BigDecimal.ZERO;
          for (Movimentacao m : d.movimentos)
            if (!m.instante().isAfter(fim)
                && (sku == null || sku.isBlank() || m.sku().equals(Validacao.sku(sku)))) {
              if (m.destino() != null
                  && ator.permite(m.destino())
                  && (deposito == null
                      || deposito.isBlank()
                      || m.destino().equals(Validacao.codigo(deposito))))
                finalPeriodo = finalPeriodo.add(m.valor());
              if (m.origem() != null
                  && ator.permite(m.origem())
                  && (deposito == null
                      || deposito.isBlank()
                      || m.origem().equals(Validacao.codigo(deposito))))
                finalPeriodo = finalPeriodo.subtract(m.valor().abs());
            }
          BigDecimal fluxo = BigDecimal.ZERO;
          for (Movimentacao m : ms) {
            if (m.destino() != null
                && (deposito == null
                    || deposito.isBlank()
                    || m.destino().equals(Validacao.codigo(deposito))))
              fluxo = fluxo.add(m.valor());
            if (m.origem() != null
                && (deposito == null
                    || deposito.isBlank()
                    || m.origem().equals(Validacao.codigo(deposito))))
              fluxo = fluxo.subtract(m.valor().abs());
          }
          BigDecimal mediaValor =
              finalPeriodo
                  .add(finalPeriodo.subtract(fluxo))
                  .divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
          BigDecimal custoSaidas =
              ms.stream()
                  .filter(m -> m.tipo() == TipoMovimento.SAIDA)
                  .map(Movimentacao::valor)
                  .reduce(BigDecimal.ZERO, BigDecimal::add);
          BigDecimal giro =
              mediaValor.signum() <= 0
                  ? BigDecimal.ZERO.setScale(4)
                  : custoSaidas.divide(mediaValor, 4, RoundingMode.HALF_UP);
          return new Relatorio(
              inicio,
              fim,
              posicoes,
              ms,
              entradas.setScale(3),
              saidas.setScale(3),
              perdas.setScale(3),
              valorPerdas.setScale(2),
              valorEstoque,
              giro);
        });
  }
}
