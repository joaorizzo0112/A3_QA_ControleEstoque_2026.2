package br.edu.estoque;

import static org.junit.jupiter.api.Assertions.*;

import br.edu.estoque.dominio.*;
import br.edu.estoque.servico.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class RegrasNegocioTest extends BaseTeste {
  @ParameterizedTest
  @ValueSource(strings = {"0", "-0.001", "0.0001", "1.0001", "1000000.000"})
  void RN003_rejeitaQuantidadesForaDoDominio(String q) {
    assertThrows(IllegalArgumentException.class, () -> entrada("L1", q, "1", 60));
    assertTrue(banco.lotes.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0.001", "0.002", "999999.998", "999999.999"})
  void RF005_aceitaQuantidadesNoDominio(String q) {
    entrada("L1", q, "1", 60);
    decimal(q, s.saldoFisico("SKU-001", "CD-01"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "0.009", "10000000.00"})
  void RN004_rejeitaCustoForaDoDominio(String c) {
    assertThrows(IllegalArgumentException.class, () -> entrada("L1", "1", c, 60));
    assertTrue(banco.lotes.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0.01", "0.02", "9999999.98", "9999999.99"})
  void RN024_custosNosLimites(String c) {
    decimal(c, entrada("L1", "1", c, 60).custo());
  }

  @Test
  void RN024_arredondaHalfUp() {
    decimal("1.01", entrada("L1", "1", "1.005", 60).custo());
  }

  @ParameterizedTest
  @ValueSource(strings = {"AB", "ABCDEFGHIJKLMNOPQRSTU", "!@#", ""})
  void RF001_rejeitaSkuForaDosLimites(String sku) {
    assertThrows(
        IllegalArgumentException.class,
        () -> s.cadastrarProduto(sku, "Teste", Unidade.UN, bd("0")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ABC", "ABCD", "ABCDEFGHIJKLMNOPQRST", "ABCDEFGHIJKLMNOPQRS"})
  void RF001_aceitaSkuNosLimites(String sku) {
    assertEquals(sku, s.cadastrarProduto(sku, "Teste", Unidade.UN, bd("0")).sku());
  }

  @Test
  void RF002_alteracaoMantemHistorico() {
    s.alterarProduto("SKU-001", "Novo nome", Unidade.M, bd("2"));
    assertEquals("Cabo de rede", s.historicoProduto("SKU-001").get(0).nome());
    assertEquals("Novo nome", s.produtos().get(0).nome());
    assertEquals(Unidade.M, s.produtos().get(0).unidade());
  }

  @Test
  void RF002_alteracaoInvalidaNaoMudaDados() {
    assertThrows(
        IllegalArgumentException.class, () -> s.alterarProduto("SKU-001", "", Unidade.M, bd("2")));
    assertEquals("Cabo de rede", s.produtos().get(0).nome());
    assertTrue(s.historicoProduto("SKU-001").isEmpty());
  }

  @Test
  void RF003_inativacaoSemReserva() {
    s.inativarProduto("SKU-001");
    assertFalse(s.produtos().get(0).ativo());
    assertThrows(NoSuchElementException.class, () -> entrada("L1", "1", "2", 60));
  }

  @Test
  void RF003_inativacaoComReservaBloqueada() {
    entrada("L1", "10", "2", 60);
    reservar("1");
    assertThrows(IllegalStateException.class, () -> s.inativarProduto("SKU-001"));
    assertTrue(s.produtos().get(0).ativo());
  }

  @Test
  void RN002_saidaEReservaExigemProdutoAtivo() {
    entrada("L1", "10", "2", 60);
    s.inativarProduto("SKU-001");
    assertThrows(
        NoSuchElementException.class, () -> s.registrarSaida("SKU-001", "CD-01", bd("1"), chave()));
    assertThrows(NoSuchElementException.class, () -> reservar("1"));
    decimal("10", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void RF004_codigoEquivalenteDuplicado() {
    assertThrows(IllegalStateException.class, () -> s.cadastrarDeposito(" cd-01 ", "Outro"));
    assertEquals(2, s.depositos().size());
  }

  @Test
  void RN002_depositoInativoBloqueiaNovosMovimentos() {
    entrada("L1", "10", "2", 60);
    s.inativarDeposito("CD-01");
    assertThrows(NoSuchElementException.class, () -> entrada("L2", "1", "2", 60));
    assertThrows(NoSuchElementException.class, () -> reservar("1"));
  }

  @Test
  void RF006_loteDuplicadoMesmoFornecedor() {
    entrada("L1", "1", "2", 60);
    assertThrows(IllegalStateException.class, () -> entrada("L1", "1", "2", 60));
    decimal("1", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void RF006_numeroIgualFornecedorDiferente() {
    entrada("L1", "1", "2", 60);
    s.registrarEntrada("SKU-001", "CD-01", "OUTRO", "L1", bd("1"), bd("2"), data(60), chave());
    decimal("2", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void RN004_loteIdentificavel() {
    assertThrows(IllegalArgumentException.class, () -> entrada(" ", "1", "2", 60));
  }

  @Test
  void RF005_dataAcimaDoLimite() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            s.registrarEntrada(
                "SKU-001", "CD-01", "L1", bd("1"), bd("2"), LocalDate.of(2101, 1, 1)));
  }

  @Test
  void RF005_dataLimiteSuperiorAceita() {
    s.registrarEntrada("SKU-001", "CD-01", "L1", bd("1"), bd("2"), LocalDate.of(2100, 12, 31));
    decimal("1", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void UC05_rejeitaReservaNoPassado() {
    entrada("L1", "10", "2", 60);
    assertThrows(
        IllegalArgumentException.class,
        () -> s.reservar("SKU-001", "CD-01", bd("1"), agora().minusNanos(1), chave()));
    assertTrue(s.reservas().isEmpty());
  }

  @Test
  void UC05_rejeitaReservaNoPresente() {
    entrada("L1", "10", "2", 60);
    assertThrows(
        IllegalArgumentException.class,
        () -> s.reservar("SKU-001", "CD-01", bd("1"), agora(), chave()));
  }

  @Test
  void RF010_atendimentoBaixaSomenteReservaUmaVez() {
    entrada("L1", "10", "2", 60);
    Reserva r = reservar("3");
    String key = chave();
    Movimentacao m = s.atenderReserva(r.id(), key);
    assertSame(m, s.atenderReserva(r.id(), key));
    decimal("7", s.saldoFisico("SKU-001", "CD-01"));
    decimal("0", s.saldoReservado("SKU-001", "CD-01"));
    assertEquals(EstadoReserva.ATENDIDA, banco.reservas.get(r.id()).estado());
  }

  @Test
  void RF010_reservaCanceladaNaoAtendida() {
    entrada("L1", "10", "2", 60);
    Reserva r = reservar("3");
    s.cancelarReserva(r.id());
    assertThrows(IllegalStateException.class, () -> s.atenderReserva(r.id(), chave()));
    decimal("10", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void RN011_reservaAtendidaNaoPodeSerCancelada() {
    entrada("L1", "10", "2", 60);
    Reserva r = reservar("3");
    s.atenderReserva(r.id(), chave());
    assertThrows(IllegalStateException.class, () -> s.cancelarReserva(r.id()));
    assertEquals(EstadoReserva.ATENDIDA, banco.reservas.get(r.id()).estado());
  }

  @Test
  void RN011_estadoExpiradoTerminal() {
    entrada("L1", "10", "2", 60);
    Reserva r = reservar("3");
    clock.avancar(Duration.ofHours(1));
    s.expirarReservas();
    assertThrows(IllegalStateException.class, () -> s.cancelarReserva(r.id()));
    assertThrows(IllegalStateException.class, () -> s.atenderReserva(r.id(), chave()));
    assertEquals(EstadoReserva.EXPIRADA, banco.reservas.get(r.id()).estado());
  }

  @Test
  void RN012_expiracaoAntesNoEDepoisDoLimite() {
    entrada("L1", "10", "2", 60);
    Reserva r = reservar("3");
    clock.avancar(Duration.ofHours(1).minusNanos(1));
    assertEquals(0, s.expirarReservas());
    decimal("7", s.saldoDisponivel("SKU-001", "CD-01"));
    clock.avancar(Duration.ofNanos(1));
    assertEquals(1, s.expirarReservas());
    clock.avancar(Duration.ofNanos(1));
    assertEquals(0, s.expirarReservas());
    decimal("10", s.saldoDisponivel("SKU-001", "CD-01"));
  }

  @Test
  void RN013_depositosIguaisRejeitados() {
    entrada("L1", "10", "2", 60);
    assertThrows(
        IllegalArgumentException.class,
        () -> s.transferir("SKU-001", "CD-01", "cd-01", bd("1"), chave()));
    decimal("10", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void RF013_preservaCustoValidadeFornecedor() {
    Lote l = entrada("L1", "10", "12.50", 30);
    s.transferir("SKU-001", "CD-01", "LOJA-01", bd("4"), chave());
    Lote destino = s.lotes("SKU-001", "LOJA-01").get(0);
    assertEquals(l.raiz(), destino.raiz());
    assertEquals(l.validade(), destino.validade());
    assertEquals(l.fornecedor(), destino.fornecedor());
    decimal("12.50", destino.custo());
    decimal(
        "125.00",
        s.posicao("SKU-001", "CD-01")
            .valorEstoque()
            .add(s.posicao("SKU-001", "LOJA-01").valorEstoque()));
  }

  @Test
  void RF013_transferenciaRespeitaReserva() {
    entrada("L1", "10", "2", 60);
    reservar("8");
    assertThrows(
        IllegalStateException.class,
        () -> s.transferir("SKU-001", "CD-01", "LOJA-01", bd("2.001"), chave()));
    decimal("10", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void RNF018_entradaIdempotente() {
    String k = chave();
    Lote a = s.registrarEntrada("SKU-001", "CD-01", "F", "L1", bd("2"), bd("2"), data(30), k),
        b = s.registrarEntrada("SKU-001", "CD-01", "F", "L1", bd("2.000"), bd("2.00"), data(30), k);
    assertEquals(a.id(), b.id());
    decimal("2", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void RNF018_saidaIdempotente() {
    entrada("L1", "10", "2", 60);
    String k = chave();
    s.registrarSaida("SKU-001", "CD-01", bd("3"), k);
    s.registrarSaida("SKU-001", "CD-01", bd("3"), k);
    decimal("7", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void RNF018_reservaIdempotente() {
    entrada("L1", "10", "2", 60);
    String k = chave();
    Reserva a = s.reservar("SKU-001", "CD-01", bd("3"), agora().plusHours(1), k),
        b = s.reservar("SKU-001", "CD-01", bd("3"), agora().plusHours(1), k);
    assertEquals(a.id(), b.id());
    decimal("3", s.saldoReservado("SKU-001", "CD-01"));
  }

  @Test
  void RNF018_ajusteIdempotente() {
    Lote l = entrada("L1", "10", "2", 60);
    String k = chave();
    s.ajustarLote(l.id(), bd("1"), "Ajuste autorizado de teste", k);
    s.ajustarLote(l.id(), bd("1"), "Ajuste autorizado de teste", k);
    decimal("11", s.saldoFisico("SKU-001", "CD-01"));
  }

  @ParameterizedTest
  @ValueSource(ints = {15, 65})
  void interface_chaveForaDosLimites(int tam) {
    entrada("L1", "10", "2", 60);
    assertThrows(
        IllegalArgumentException.class,
        () -> s.registrarSaida("SKU-001", "CD-01", bd("1"), "a".repeat(tam)));
  }

  @ParameterizedTest
  @ValueSource(ints = {16, 17, 63, 64})
  void interface_chaveNosLimites(int tam) {
    entrada("L1", "10", "2", 60);
    s.registrarSaida("SKU-001", "CD-01", bd("1"), "a".repeat(tam));
    decimal("9", s.saldoFisico("SKU-001", "CD-01"));
  }

  @ParameterizedTest
  @ValueSource(ints = {9, 201})
  void RN017_motivoForaDosLimites(int tam) {
    Lote l = entrada("L1", "10", "2", 60);
    assertThrows(
        IllegalArgumentException.class,
        () -> s.ajustarLote(l.id(), bd("1"), "a".repeat(tam), chave()));
    decimal("10", s.saldoFisico("SKU-001", "CD-01"));
  }

  @ParameterizedTest
  @ValueSource(ints = {10, 11, 199, 200})
  void RN017_motivoNosLimites(int tam) {
    Lote l = entrada("L1", "10", "2", 60);
    s.ajustarLote(l.id(), bd("1"), "  " + "a".repeat(tam) + "  ", chave());
    decimal("11", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void RF015_ajusteNaoPodeViolarReservado() {
    Lote l = entrada("L1", "10", "2", 60);
    reservar("8");
    assertThrows(
        IllegalStateException.class,
        () -> s.ajustarLote(l.id(), bd("-3"), "Ajuste autorizado de teste", chave()));
    decimal("10", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void RN009_descarteAutorizadoDeVencido() {
    Lote l = entrada("L1", "10", "2", 0);
    clock.avancar(Duration.ofDays(1));
    Movimentacao m = s.descartar(l.id(), bd("10"), "Descarte por validade vencida", chave());
    assertEquals(TipoMovimento.DESCARTE, m.tipo());
    decimal("0", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void RN021_reposicaoAbaixoIgualAcima() {
    entrada("L1", "5.001", "2", 60);
    assertFalse(s.requerReposicao("SKU-001", "CD-01"));
    s.registrarSaida("SKU-001", "CD-01", bd("0.001"), chave());
    assertTrue(s.requerReposicao("SKU-001", "CD-01"));
    s.registrarSaida("SKU-001", "CD-01", bd("0.001"), chave());
    assertTrue(s.requerReposicao("SKU-001", "CD-01"));
  }

  @Test
  void RN022_vencimento29_30_31Dias() {
    Lote a = entrada("A", "1", "2", 29),
        b = entrada("B", "1", "2", 30),
        c = entrada("C", "1", "2", 31);
    Set<UUID> ids = new HashSet<>();
    s.lotesProximosDoVencimento().forEach(l -> ids.add(l.id()));
    assertEquals(Set.of(a.id(), b.id()), ids);
    assertFalse(ids.contains(c.id()));
  }

  @Test
  void UC13_alertasSemDuplicacaoDiaria() {
    entrada("L1", "5", "2", 30);
    List<Alerta> a = s.gerarAlertas(), b = s.gerarAlertas();
    assertEquals(a, b);
    assertEquals(a.size(), s.alertas().size());
  }

  @Test
  void UC13_naoAlertaProdutoInativoNemLoteEsgotado() {
    Lote l = entrada("L1", "1", "2", 30);
    s.registrarSaida("SKU-001", "CD-01", bd("1"), chave());
    s.inativarProduto("SKU-001");
    assertTrue(s.gerarAlertas().isEmpty());
    assertTrue(s.lotesProximosDoVencimento().isEmpty());
  }

  @Test
  void RF024_relatorioConsolidaValorEPerda() {
    Lote l = entrada("L1", "10", "2", 60);
    s.registrarSaida("SKU-001", "CD-01", bd("2"), chave());
    s.descartar(l.id(), bd("1"), "Descarte de material danificado", chave());
    Relatorio r = s.relatorio("SKU-001", "CD-01", agora().minusDays(1), agora().plusDays(1));
    decimal("14", r.valorEstoque());
    decimal("2", r.valorPerdas());
    decimal("1", r.perdas());
    assertEquals(3, r.movimentos().size());
  }

  @Test
  void UC14_relatorioVazioEIntervaloInvalido() {
    Relatorio r = s.relatorio(null, null, agora(), agora());
    assertTrue(r.movimentos().isEmpty());
    decimal("0", r.valorEstoque());
    assertThrows(
        IllegalArgumentException.class,
        () -> s.relatorio(null, null, agora().plusDays(1), agora()));
  }

  @Test
  void RF025_auditaSucessoEFalhaSemDetalhesTecnicos() {
    entrada("L1", "1", "2", 60);
    assertThrows(
        IllegalStateException.class, () -> s.registrarSaida("SKU-001", "CD-01", bd("2"), chave()));
    List<Evento> es = s.eventos(agora().minusDays(1), agora().plusDays(1));
    assertTrue(es.stream().anyMatch(e -> e.resultado().equals("FALHA")));
    assertTrue(
        es.stream()
            .allMatch(
                e ->
                    e.ator() != null
                        && e.instante() != null
                        && e.recurso() != null
                        && e.correlacao() != null));
    assertThrows(UnsupportedOperationException.class, () -> es.clear());
  }

  @Test
  void RF026_operadorOperaMasNaoAjusta() {
    Lote l = entrada("L1", "10", "2", 60);
    ServicoEstoque op = como(Perfil.OPERADOR, "CD-01", "LOJA-01");
    op.registrarSaida("SKU-001", "CD-01", bd("1"), chave());
    assertThrows(
        SecurityException.class,
        () -> op.ajustarLote(l.id(), bd("1"), "Ajuste autorizado de teste", chave()));
  }

  @Test
  void RF026_auditorConsultaMasNaoAltera() {
    entrada("L1", "10", "2", 60);
    ServicoEstoque a = como(Perfil.AUDITOR, "*");
    decimal("10", a.saldoFisico("SKU-001", "CD-01"));
    assertFalse(a.eventos(agora().minusDays(1), agora().plusDays(1)).isEmpty());
    assertThrows(
        SecurityException.class, () -> a.registrarSaida("SKU-001", "CD-01", bd("1"), chave()));
    assertThrows(
        SecurityException.class, () -> a.cadastrarProduto("SKU-002", "Outro", Unidade.UN, bd("0")));
  }

  @Test
  void RF026_depositoForaDaPermissao() {
    entrada("L1", "10", "2", 60);
    ServicoEstoque op = como(Perfil.OPERADOR, "LOJA-01");
    assertThrows(SecurityException.class, () -> op.saldoFisico("SKU-001", "CD-01"));
    assertThrows(
        SecurityException.class, () -> op.registrarSaida("SKU-001", "CD-01", bd("1"), chave()));
    assertThrows(
        SecurityException.class,
        () -> op.transferir("SKU-001", "CD-01", "LOJA-01", bd("1"), chave()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"2000-01-01", "2000-01-02", "2100-12-30", "2100-12-31"})
  void limiteDeDataAceito(String dia) {
    RelogioAjustavel antigo = new RelogioAjustavel(Instant.parse("1999-12-30T12:00:00Z"));
    ServicoEstoque servico = new ServicoEstoque(banco, antigo);
    servico.registrarEntrada("SKU-001", "CD-01", "L1", bd("1"), bd("2"), LocalDate.parse(dia));
    decimal("1", servico.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void limiteDeDataInferiorRejeitado() {
    RelogioAjustavel antigo = new RelogioAjustavel(Instant.parse("1999-12-30T12:00:00Z"));
    ServicoEstoque servico = new ServicoEstoque(banco, antigo);
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                servico.registrarEntrada(
                    "SKU-001", "CD-01", "L1", bd("1"), bd("2"), LocalDate.of(1999, 12, 31)));
    assertEquals("Data inválida", e.getMessage());
  }

  @Test
  void RN012_estadoConsultadoExpiraSemDependerDaRotina() {
    entrada("L1", "10", "2", 60);
    reservar("3");
    clock.avancar(Duration.ofHours(1));
    assertEquals(EstadoReserva.EXPIRADA, s.reservas().get(0).estado());
    decimal("10", s.saldoDisponivel("SKU-001", "CD-01"));
  }

  @Test
  void fusoDaOrganizacaoDefineValidade() {
    RelogioAjustavel relogio = new RelogioAjustavel(Instant.parse("2026-11-15T02:00:00Z"));
    ServicoEstoque servico = new ServicoEstoque(banco, relogio);
    servico.registrarEntrada(
        "SKU-001", "CD-01", "L1", bd("1"), bd("2"), LocalDate.of(2026, 11, 14));
    decimal("1", servico.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void UC02_limiteConfiguradoAceitaAteLimiteRejeitaExcesso() {
    var limitado = new br.edu.estoque.repositorio.BancoEmMemoria(null, 3);
    var servico = new ServicoEstoque(limitado, clock);
    base(servico);
    servico.cadastrarDeposito("CD-02", "Terceiro");
    assertEquals(3, servico.depositos().size());
    assertThrows(IllegalStateException.class, () -> servico.cadastrarDeposito("CD-03", "Quarto"));
    assertEquals(3, servico.depositos().size());
  }

  @Test
  void UC02_limiteInvalidoRejeitado() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new br.edu.estoque.repositorio.BancoEmMemoria(null, 0));
  }

  @Test
  void RF026_operadorNaoPodeReutilizarChaveDeOutroDeposito() {
    entrada("L1", "10", "2", 60);
    String k = chave();
    s.transferir("SKU-001", "CD-01", "LOJA-01", bd("1"), k);
    ServicoEstoque op = como(Perfil.OPERADOR, "LOJA-01");
    assertThrows(
        SecurityException.class, () -> op.transferir("SKU-001", "CD-01", "LOJA-01", bd("1"), k));
  }
}
