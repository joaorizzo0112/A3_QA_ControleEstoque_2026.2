package br.edu.estoque;

import br.edu.estoque.dominio.*;
import br.edu.estoque.repositorio.BancoEmMemoria;
import br.edu.estoque.servico.ServicoEstoque;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes automatizados CT001–CT015, derivados da seção 10
 * do documento "Especificação e Planejamento de Testes de Software".
 */
class ServicoEstoqueTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC);

    private BancoEmMemoria banco;
    private ServicoEstoque servico;

    @BeforeEach
    void configurar() {
        banco = new BancoEmMemoria();
        servico = new ServicoEstoque(banco, CLOCK);
    }

    private BigDecimal bd(String valor) { return new BigDecimal(valor); }
    private LocalDate validade(int dias) { return LocalDate.now(CLOCK).plusDays(dias); }

    private void cadastrarBase() {
        servico.cadastrarProduto("SKU-001", "Cabo de rede", Unidade.UN, bd("5.000"));
        servico.cadastrarDeposito("CD-01", "Centro de distribuição");
    }

    private Lote entrada(String numero, String qtd, String custo, int dias) {
        return servico.registrarEntrada("SKU-001", "CD-01", numero,
                bd(qtd), bd(custo), validade(dias));
    }

    // CT001 — RF-001: cadastrar produto válido e verificar persistência.
    @Test
    void CT001_cadastraProdutoValido() {
        Produto produto = servico.cadastrarProduto(
                "SKU-001", "Cabo de rede", Unidade.UN, bd("5.000"));

        assertNotNull(produto);
        assertEquals("SKU-001", produto.sku());
        assertEquals("Cabo de rede", produto.nome());
        assertSame(produto, banco.produtos.get("SKU-001"));
    }

    // CT002 — RN-001: rejeitar SKU fora do padrão [A-Za-z0-9_-]{3,20}.
    @Test
    void CT002_rejeitaSkuInvalido() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> servico.cadastrarProduto("!@#", "Teste", Unidade.UN, bd("1")));

        assertEquals("SKU inválido", erro.getMessage());
        assertTrue(banco.produtos.isEmpty());
    }

    // CT003 — RF-004: cadastrar depósito e normalizar o código para maiúsculas.
    @Test
    void CT003_cadastraDepositoComSucesso() {
        Deposito deposito = servico.cadastrarDeposito("cd-01", "Centro de distribuição");

        assertEquals("CD-01", deposito.codigo());
        assertSame(deposito, banco.depositos.get("CD-01"));
    }

    // CT004 — RN-002: rejeitar código vazio.
    @Test
    void CT004_rejeitaCodigoDeDepositoEmBranco() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> servico.cadastrarDeposito("", "Depósito inválido"));

        assertEquals("Código inválido", erro.getMessage());
        assertTrue(banco.depositos.isEmpty());
    }

    // CT005 — RF-005: registrar lote válido e conferir saldo.
    @Test
    void CT005_registraEntradaDeLote() {
        cadastrarBase();

        Lote lote = entrada("L001", "20.000", "12.50", 60);

        assertNotNull(lote);
        assertEquals("L001", lote.numero());
        assertEquals(1, banco.lotes.size());
        assertEquals(0, bd("20.000").compareTo(
                servico.saldoFisico("SKU-001", "CD-01")));
    }

    // CT006 — RN-005: validade anterior ao Clock deve ser recusada.
    @Test
    void CT006_rejeitaLoteVencido() {
        cadastrarBase();

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> entrada("L-VENC", "5.000", "2.00", -1));

        assertEquals("Lote vencido", erro.getMessage());
        assertTrue(banco.lotes.isEmpty());
    }

    // CT007 — RF-019: somar lotes para saldo físico e disponível sem reservas.
    @Test
    void CT007_calculaSaldoFisicoEDisponivel() {
        cadastrarBase();
        entrada("L1", "10.000", "2.00", 60);
        entrada("L2", "15.000", "2.00", 90);

        assertEquals(0, bd("25.000").compareTo(
                servico.saldoFisico("SKU-001", "CD-01")));
        assertEquals(0, bd("25.000").compareTo(
                servico.saldoDisponivel("SKU-001", "CD-01")));
    }

    // CT008 — RF-009: criar reserva e conferir estado ATIVA.
    @Test
    void CT008_realizaReservaComSucesso() {
        cadastrarBase();
        entrada("L1", "20.000", "2.00", 60);

        Reserva reserva = servico.reservar("SKU-001", "CD-01", bd("5.000"),
                ZonedDateTime.now(CLOCK).plusDays(1));

        assertEquals(EstadoReserva.ATIVA, reserva.estado());
        assertSame(reserva, banco.reservas.get(reserva.id()));
        assertEquals(0, bd("20.000").compareTo(
                servico.saldoFisico("SKU-001", "CD-01")));
    }

    // CT009 — RN-010: não permitir reserva acima do saldo disponível.
    @Test
    void CT009_rejeitaReservaComSaldoInsuficiente() {
        cadastrarBase();
        entrada("L1", "10.000", "2.00", 60);

        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> servico.reservar("SKU-001", "CD-01", bd("15.000"),
                        ZonedDateTime.now(CLOCK).plusDays(1)));

        assertEquals("Saldo disponível insuficiente", erro.getMessage());
        assertTrue(banco.reservas.isEmpty());
    }

    // CT010 — RF-012: expirar reserva cujo instante já passou.
    @Test
    void CT010_expiraReservasVencidas() {
        cadastrarBase();
        entrada("L1", "10.000", "2.00", 60);
        Reserva reserva = servico.reservar("SKU-001", "CD-01", bd("2.000"),
                ZonedDateTime.now(CLOCK).minusSeconds(1));

        servico.expirarReservas();

        assertEquals(EstadoReserva.EXPIRADA, reserva.estado());
    }

    // CT011 — RN-008: FEFO consome primeiro o lote com validade mais próxima.
    @Test
    void CT011_saidaConsomeLotePelaRegraFefo() {
        cadastrarBase();
        Lote loteMaisProximo = entrada("LOTE-A", "5.000", "2.00", 5);
        Lote loteMaisDistante = entrada("LOTE-B", "5.000", "2.00", 25);

        servico.registrarSaida("SKU-001", "CD-01", bd("5.000"));

        assertEquals(0, bd("0.000").compareTo(loteMaisProximo.quantidade()),
                "O lote com vencimento mais próximo deve ser consumido primeiro.");
        assertEquals(0, bd("5.000").compareTo(loteMaisDistante.quantidade()),
                "O lote com validade posterior deve permanecer intacto.");
    }

    // CT012 — RN-007: bloquear saída maior que o saldo e preservar o estoque.
    @Test
    void CT012_bloqueiaSaidaMaiorQueSaldo() {
        cadastrarBase();
        entrada("L1", "10.000", "2.00", 60);

        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> servico.registrarSaida("SKU-001", "CD-01", bd("15.000")));

        assertEquals("Saldo insuficiente", erro.getMessage());
        assertEquals(0, bd("10.000").compareTo(
                servico.saldoFisico("SKU-001", "CD-01")));
    }

    // CT013 — RF-013: debitar origem e creditar destino na transferência.
    @Test
    void CT013_transfereEstoqueEntreDepositos() {
        cadastrarBase();
        servico.cadastrarDeposito("CD-02", "Filial");
        entrada("L1", "10.000", "2.00", 60);

        Transferencia transferencia = servico.transferir(
                "SKU-001", "CD-01", "CD-02", bd("5.000"), "CHAVE-1");

        assertNotNull(transferencia);
        assertEquals("CHAVE-1", transferencia.chave());
        assertEquals(0, bd("5.000").compareTo(
                servico.saldoFisico("SKU-001", "CD-01")));
        assertEquals(0, bd("5.000").compareTo(
                servico.saldoFisico("SKU-001", "CD-02")));
    }

    // CT014 — RF-014: repetir chave e conteúdo não pode movimentar novamente.
    @Test
    void CT014_transferenciaComMesmaChaveEhIdempotente() {
        cadastrarBase();
        servico.cadastrarDeposito("CD-02", "Filial");
        entrada("L1", "10.000", "2.00", 60);

        Transferencia primeira = servico.transferir(
                "SKU-001", "CD-01", "CD-02", bd("5.000"), "CHAVE-1");
        Transferencia segunda = servico.transferir(
                "SKU-001", "CD-01", "CD-02", bd("5.000"), "CHAVE-1");

        assertSame(primeira, segunda);
        assertEquals(0, bd("5.000").compareTo(
                servico.saldoFisico("SKU-001", "CD-01")));
        assertEquals(0, bd("5.000").compareTo(
                servico.saldoFisico("SKU-001", "CD-02")));
        assertEquals(1, banco.transferencias.size());
    }

    // CT015 — RF-020: média ponderada = soma(qtd*custo) / soma(qtd).
    @Test
    void CT015_calculaCustoMedioPonderado() {
        cadastrarBase();
        entrada("L1", "10.000", "10.00", 60);
        entrada("L2", "30.000", "20.00", 90);

        BigDecimal custoMedio = servico.custoMedio("SKU-001", "CD-01");

        assertEquals(0, bd("17.50").compareTo(custoMedio));
    }
}
