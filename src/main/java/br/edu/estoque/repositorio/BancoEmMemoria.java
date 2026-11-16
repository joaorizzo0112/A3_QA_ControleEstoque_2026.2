package br.edu.estoque.repositorio;

import br.edu.estoque.dominio.*;
import java.io.*;
import java.util.*;
import java.util.function.Function;

/** Um monitor por banco protege a operação inteira, inclusive auditoria e persistência. */
public final class BancoEmMemoria {
  public record Operacao(String assinatura, Serializable resultado) implements Serializable {}

  public static final class Dados implements Serializable {
    private static final long serialVersionUID = 1L;
    public final Map<String, Produto> produtos = new LinkedHashMap<>();
    public final Map<String, Deposito> depositos = new LinkedHashMap<>();
    public final Map<UUID, Lote> lotes = new LinkedHashMap<>();
    public final Map<UUID, Reserva> reservas = new LinkedHashMap<>();
    public final Map<String, Transferencia> transferencias = new LinkedHashMap<>();
    public final Map<String, UUID> lotesOriginais = new LinkedHashMap<>();
    public final Map<String, Operacao> operacoes = new LinkedHashMap<>();
    public final Map<UUID, Inventario> inventarios = new LinkedHashMap<>();
    public final List<Movimentacao> movimentos = new ArrayList<>();
    public final List<Evento> eventos = new ArrayList<>();
    public final List<VersaoProduto> historicoProdutos = new ArrayList<>();
    public final Map<String, Alerta> alertas = new LinkedHashMap<>();

    public Dados copia() {
      Dados d = new Dados();
      produtos.forEach((k, v) -> d.produtos.put(k, v.copia()));
      depositos.forEach((k, v) -> d.depositos.put(k, v.copia()));
      lotes.forEach((k, v) -> d.lotes.put(k, v.copia()));
      reservas.forEach((k, v) -> d.reservas.put(k, v.copia()));
      inventarios.forEach((k, v) -> d.inventarios.put(k, v.copia()));
      d.transferencias.putAll(transferencias);
      d.lotesOriginais.putAll(lotesOriginais);
      d.operacoes.putAll(operacoes);
      d.movimentos.addAll(movimentos);
      d.eventos.addAll(eventos);
      d.historicoProdutos.addAll(historicoProdutos);
      d.alertas.putAll(alertas);
      return d;
    }

    private void restaurar(Dados d) {
      produtos.clear();
      produtos.putAll(d.produtos);
      depositos.clear();
      depositos.putAll(d.depositos);
      lotes.clear();
      lotes.putAll(d.lotes);
      reservas.clear();
      reservas.putAll(d.reservas);
      transferencias.clear();
      transferencias.putAll(d.transferencias);
      lotesOriginais.clear();
      lotesOriginais.putAll(d.lotesOriginais);
      operacoes.clear();
      operacoes.putAll(d.operacoes);
      inventarios.clear();
      inventarios.putAll(d.inventarios);
      movimentos.clear();
      movimentos.addAll(d.movimentos);
      eventos.clear();
      eventos.addAll(d.eventos);
      historicoProdutos.clear();
      historicoProdutos.addAll(d.historicoProdutos);
      alertas.clear();
      alertas.putAll(d.alertas);
    }
  }

  private final Dados dados;
  private final Armazenamento armazenamento;
  private final int limiteDepositos;
  // Views somente de leitura mantêm a compatibilidade com os testes da primeira entrega.
  public final Map<String, Produto> produtos;
  public final Map<String, Deposito> depositos;
  public final Map<UUID, Lote> lotes;
  public final Map<UUID, Reserva> reservas;
  public final Map<String, Transferencia> transferencias;

  public BancoEmMemoria() {
    this(null);
  }

  public BancoEmMemoria(Armazenamento armazenamento) {
    this(armazenamento, Integer.MAX_VALUE);
  }

  public int limiteDepositos() {
    return limiteDepositos;
  }

  public BancoEmMemoria(Armazenamento armazenamento, int limiteDepositos) {
    if (limiteDepositos < 1) throw new IllegalArgumentException("Limite de depósitos inválido");
    this.limiteDepositos = limiteDepositos;
    this.armazenamento = armazenamento;
    try {
      dados = armazenamento == null ? new Dados() : armazenamento.carregar();
    } catch (IOException e) {
      throw new IllegalStateException("Não foi possível carregar os dados locais", e);
    }
    produtos = Collections.unmodifiableMap(dados.produtos);
    depositos = Collections.unmodifiableMap(dados.depositos);
    lotes = Collections.unmodifiableMap(dados.lotes);
    reservas = Collections.unmodifiableMap(dados.reservas);
    transferencias = Collections.unmodifiableMap(dados.transferencias);
  }

  public synchronized <T> T ler(Function<Dados, T> consulta) {
    return consulta.apply(dados);
  }

  public synchronized <T> T transacionar(Function<Dados, T> acao) {
    Dados anterior = dados.copia();
    try {
      T resultado = acao.apply(dados);
      if (armazenamento != null) armazenamento.salvar(dados);
      return resultado;
    } catch (RuntimeException | IOException e) {
      dados.restaurar(anterior);
      if (e instanceof RuntimeException r) throw r;
      throw new IllegalStateException("Não foi possível confirmar a operação", e);
    }
  }

  public void limpar() {
    transacionar(
        d -> {
          d.restaurar(new Dados());
          return null;
        });
  }
}
