package br.edu.estoque.servico;

import br.edu.estoque.dominio.Deposito;
import br.edu.estoque.dominio.EstadoReserva;
import br.edu.estoque.dominio.Lote;
import br.edu.estoque.dominio.Produto;
import br.edu.estoque.dominio.Reserva;
import br.edu.estoque.dominio.Transferencia;
import br.edu.estoque.dominio.Unidade;
import br.edu.estoque.repositorio.BancoEmMemoria;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

public final class ServicoEstoque {
    private final BancoEmMemoria banco;
    private final Clock relogio;

    public ServicoEstoque(BancoEmMemoria banco, Clock relogio) {
        this.banco = banco;
        this.relogio = relogio;
    }

    public Produto cadastrarProduto(
            String sku, String nome, Unidade unidade, BigDecimal minimo) {
        Produto produto = new Produto(sku, nome, unidade, minimo);
        if (banco.produtos.containsKey(produto.sku())) {
            throw new IllegalStateException("Produto já cadastrado");
        }
        banco.produtos.put(produto.sku(), produto);
        return produto;
    }

    public Deposito cadastrarDeposito(String codigo, String nome) {
        Deposito deposito = new Deposito(codigo, nome);
        if (banco.depositos.containsKey(deposito.codigo())) {
            throw new IllegalStateException("Depósito já cadastrado");
        }
        banco.depositos.put(deposito.codigo(), deposito);
        return deposito;
    }

    public Lote registrarEntrada(
            String sku,
            String deposito,
            String numero,
            BigDecimal quantidade,
            BigDecimal custo,
            LocalDate validade) {
        validarQuantidade(quantidade);
        if (custo == null || custo.signum() <= 0) {
            throw new IllegalArgumentException("Custo inválido");
        }

        Produto produto = produto(sku);
        Deposito depositoCadastrado = deposito(deposito);
        ZonedDateTime agora = ZonedDateTime.now(relogio);
        if (validade.isBefore(agora.toLocalDate())) {
            throw new IllegalArgumentException("Lote vencido");
        }

        Lote lote = new Lote(
                numero,
                produto.sku(),
                depositoCadastrado.codigo(),
                quantidade,
                custo,
                validade,
                agora);
        banco.lotes.put(lote.id(), lote);
        return lote;
    }

    public BigDecimal saldoFisico(String sku, String deposito) {
        return lotes(sku, deposito).stream()
                .map(Lote::quantidade)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(3, RoundingMode.HALF_UP);
    }

    public BigDecimal saldoReservado(String sku, String deposito) {
        expirarReservas();
        return banco.reservas.values().stream()
                .filter(reserva -> reserva.sku().equals(sku)
                        && reserva.deposito().equals(deposito)
                        && reserva.estado() == EstadoReserva.ATIVA)
                .map(Reserva::quantidade)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(3, RoundingMode.HALF_UP);
    }

    public BigDecimal saldoDisponivel(String sku, String deposito) {
        return saldoFisico(sku, deposito)
                .subtract(saldoReservado(sku, deposito))
                .setScale(3, RoundingMode.HALF_UP);
    }

    public Reserva reservar(
            String sku,
            String deposito,
            BigDecimal quantidade,
            ZonedDateTime expiraEm) {
        validarQuantidade(quantidade);
        if (saldoDisponivel(sku, deposito).compareTo(quantidade) < 0) {
            throw new IllegalStateException("Saldo disponível insuficiente");
        }

        Reserva reserva = new Reserva(sku, deposito, quantidade, expiraEm);
        banco.reservas.put(reserva.id(), reserva);
        return reserva;
    }

    public void cancelarReserva(UUID id) {
        Reserva reserva = reserva(id);
        reserva.estado(EstadoReserva.CANCELADA);
    }

    public void expirarReservas() {
        ZonedDateTime agora = ZonedDateTime.now(relogio);
        banco.reservas.values().stream()
                .filter(reserva -> reserva.estado() == EstadoReserva.ATIVA
                        && !reserva.expiraEm().isAfter(agora))
                .forEach(reserva -> reserva.estado(EstadoReserva.EXPIRADA));
    }

    public void registrarSaida(String sku, String deposito, BigDecimal quantidade) {
        validarQuantidade(quantidade);
        if (saldoFisico(sku, deposito).compareTo(quantidade) < 0) {
            throw new IllegalStateException("Saldo insuficiente");
        }

        List<Lote> lotesOrdenados = new ArrayList<>(lotes(sku, deposito));
        lotesOrdenados.sort(
                Comparator.comparing(Lote::validade).thenComparing(Lote::entrada));

        BigDecimal restante = quantidade;
        for (Lote lote : lotesOrdenados) {
            if (restante.signum() == 0) {
                break;
            }
            BigDecimal retirar = lote.quantidade().min(restante);
            lote.retirar(retirar);
            restante = restante.subtract(retirar);
        }
    }

    public Transferencia transferir(
            String sku,
            String origem,
            String destino,
            BigDecimal quantidade,
            String chave) {
        Transferencia anterior = banco.transferencias.get(chave);
        if (anterior != null) {
            return anterior;
        }

        validarQuantidade(quantidade);
        produto(sku);
        Deposito depositoOrigem = deposito(origem);
        Deposito depositoDestino = deposito(destino);
        if (depositoOrigem.codigo().equals(depositoDestino.codigo())) {
            throw new IllegalArgumentException("Depósitos iguais");
        }

        registrarSaida(sku, depositoOrigem.codigo(), quantidade);
        registrarEntrada(
                sku,
                depositoDestino.codigo(),
                "TR-" + UUID.randomUUID(),
                quantidade,
                new BigDecimal("1.00"),
                LocalDate.now(relogio).plusYears(1));

        Transferencia transferencia = new Transferencia(
                UUID.randomUUID(),
                sku,
                depositoOrigem.codigo(),
                depositoDestino.codigo(),
                quantidade,
                chave,
                ZonedDateTime.now(relogio));
        banco.transferencias.put(chave, transferencia);
        return transferencia;
    }

    public boolean requerReposicao(String sku, String deposito) {
        return saldoDisponivel(sku, deposito)
                .compareTo(produto(sku).estoqueMinimo()) < 0;
    }

    public List<Lote> lotesProximosDoVencimento() {
        LocalDate hoje = LocalDate.now(relogio);
        return banco.lotes.values().stream()
                .filter(lote -> lote.quantidade().signum() > 0
                        && !lote.validade().isAfter(hoje.plusDays(29)))
                .toList();
    }

    public BigDecimal custoMedio(String sku, String deposito) {
        BigDecimal quantidadeTotal = BigDecimal.ZERO;
        BigDecimal valorTotal = BigDecimal.ZERO;

        for (Lote lote : lotes(sku, deposito)) {
            quantidadeTotal = quantidadeTotal.add(lote.quantidade());
            valorTotal = valorTotal.add(lote.quantidade().multiply(lote.custo()));
        }

        if (quantidadeTotal.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return valorTotal.divide(quantidadeTotal, 2, RoundingMode.HALF_UP);
    }

    private void validarQuantidade(BigDecimal quantidade) {
        if (quantidade == null || quantidade.signum() <= 0 || quantidade.scale() > 3) {
            throw new IllegalArgumentException("Quantidade inválida");
        }
    }

    private Produto produto(String sku) {
        Produto produto = banco.produtos.get(sku);
        if (produto == null || !produto.ativo()) {
            throw new NoSuchElementException("Produto não encontrado");
        }
        return produto;
    }

    private Deposito deposito(String codigo) {
        Deposito deposito = banco.depositos.get(codigo.toUpperCase(Locale.ROOT));
        if (deposito == null || !deposito.ativo()) {
            throw new NoSuchElementException("Depósito não encontrado");
        }
        return deposito;
    }

    private Reserva reserva(UUID id) {
        Reserva reserva = banco.reservas.get(id);
        if (reserva == null) {
            throw new NoSuchElementException("Reserva não encontrada");
        }
        return reserva;
    }

    private List<Lote> lotes(String sku, String deposito) {
        return banco.lotes.values().stream()
                .filter(lote -> lote.sku().equals(sku)
                        && lote.deposito().equals(deposito)
                        && lote.quantidade().signum() > 0)
                .toList();
    }
}
