package br.edu.estoque;

import static org.junit.jupiter.api.Assertions.*;

import br.edu.estoque.dominio.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class InventarioTest extends BaseTeste {
  @Test
  void RF016_snapshotNaoMudaComMovimentacaoPosterior() {
    Lote l = entrada("L1", "10", "2", 60);
    Inventario i = s.abrirInventario("CD-01", chave());
    s.registrarSaida("SKU-001", "CD-01", bd("1"), chave());
    decimal("10", i.referencia().get(l.id()));
    assertThrows(UnsupportedOperationException.class, () -> i.referencia().clear());
  }

  @Test
  void RF017_contagemNegativaRejeitada() {
    Lote l = entrada("L1", "10", "2", 60);
    Inventario i = s.abrirInventario("CD-01", chave());
    s.iniciarInventario(i.id());
    assertThrows(
        IllegalArgumentException.class, () -> s.registrarContagem(i.id(), l.id(), bd("-0.001")));
    assertTrue(s.inventarios().get(0).contagens().isEmpty());
  }

  @Test
  void UC10_substituiContagemComHistorico() {
    Lote l = entrada("L1", "10", "2", 60);
    Inventario i = s.abrirInventario("CD-01", chave());
    s.iniciarInventario(i.id());
    s.registrarContagem(i.id(), l.id(), bd("8"));
    Inventario finalI = s.registrarContagem(i.id(), l.id(), bd("9"));
    decimal("9", finalI.contagens().get(l.id()));
    assertEquals(2, finalI.historico().size());
    decimal("10", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void UC10_transicoesInvalidasNaoGeramAjuste() {
    Lote l = entrada("L1", "10", "2", 60);
    Inventario i = s.abrirInventario("CD-01", chave());
    assertThrows(IllegalStateException.class, () -> s.registrarContagem(i.id(), l.id(), bd("8")));
    assertThrows(IllegalStateException.class, () -> s.revisarInventario(i.id()));
    assertThrows(
        IllegalStateException.class,
        () -> s.concluirInventario(i.id(), Set.of(l.id()), "Conciliação autorizada"));
    decimal("10", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void RF018_somenteItensAprovadosSaoAjustados() {
    Lote a = entrada("A", "5", "2", 60), b = entrada("B", "5", "2", 60);
    Inventario i = s.abrirInventario("CD-01", chave());
    s.iniciarInventario(i.id());
    s.registrarContagem(i.id(), a.id(), bd("4"));
    s.registrarContagem(i.id(), b.id(), bd("3"));
    s.revisarInventario(i.id());
    Inventario finalI = s.concluirInventario(i.id(), Set.of(a.id()), "Conciliação autorizada");
    decimal("9", s.saldoFisico("SKU-001", "CD-01"));
    assertEquals(1, finalI.ajustes().size());
  }

  @Test
  void RF018_movimentoPosteriorImpedeSobrescreverSaldo() {
    Lote l = entrada("L1", "10", "2", 60);
    Inventario i = s.abrirInventario("CD-01", chave());
    s.iniciarInventario(i.id());
    s.registrarContagem(i.id(), l.id(), bd("8"));
    s.revisarInventario(i.id());
    s.registrarSaida("SKU-001", "CD-01", bd("1"), chave());
    assertThrows(
        IllegalStateException.class,
        () -> s.concluirInventario(i.id(), Set.of(l.id()), "Conciliação autorizada"));
    decimal("9", s.saldoFisico("SKU-001", "CD-01"));
    assertEquals(EstadoInventario.EM_REVISAO, s.inventarios().get(0).estado());
  }

  @Test
  void RF018_falhaEmUmItemDesfazTodosOsAjustes() {
    Lote a = entrada("A", "5", "2", 60), b = entrada("B", "5", "2", 60);
    reservar("8");
    Inventario i = s.abrirInventario("CD-01", chave());
    s.iniciarInventario(i.id());
    s.registrarContagem(i.id(), a.id(), bd("4"));
    s.registrarContagem(i.id(), b.id(), bd("1"));
    s.revisarInventario(i.id());
    assertThrows(
        IllegalStateException.class,
        () -> s.concluirInventario(i.id(), Set.of(a.id(), b.id()), "Conciliação autorizada"));
    decimal("10", s.saldoFisico("SKU-001", "CD-01"));
    assertTrue(s.inventarios().get(0).ajustes().isEmpty());
  }

  @Test
  void RF018_naoDuplicaConclusaoComDadosDiferentes() {
    Lote l = entrada("L1", "10", "2", 60);
    Inventario i = s.abrirInventario("CD-01", chave());
    s.iniciarInventario(i.id());
    s.registrarContagem(i.id(), l.id(), bd("8"));
    s.revisarInventario(i.id());
    s.concluirInventario(i.id(), Set.of(l.id()), "Conciliação autorizada");
    assertThrows(
        IllegalStateException.class,
        () -> s.concluirInventario(i.id(), Set.of(), "Outra conciliação autorizada"));
    decimal("8", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void UC10_canceladoNaoAlteraSaldoNemPermiteContagem() {
    Lote l = entrada("L1", "10", "2", 60);
    Inventario i = s.abrirInventario("CD-01", chave());
    s.cancelarInventario(i.id());
    assertThrows(IllegalStateException.class, () -> s.iniciarInventario(i.id()));
    decimal("10", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void RF018_redistribuicaoEntreLotesPreservaReservas() {
    Lote a = entrada("A", "5", "2", 60), b = entrada("B", "5", "2", 60);
    reservar("10");
    Inventario i = s.abrirInventario("CD-01", chave());
    s.iniciarInventario(i.id());
    s.registrarContagem(i.id(), a.id(), bd("1"));
    s.registrarContagem(i.id(), b.id(), bd("9"));
    s.revisarInventario(i.id());
    s.concluirInventario(i.id(), Set.of(a.id(), b.id()), "Redistribuição física aprovada");
    decimal("10", s.saldoFisico("SKU-001", "CD-01"));
    decimal("0", s.saldoDisponivel("SKU-001", "CD-01"));
  }

  @Test
  void RF026_operadorNaoAbreNemConcluiInventario() {
    Lote l = entrada("L1", "10", "2", 60);
    Inventario i = s.abrirInventario("CD-01", chave());
    assertThrows(
        SecurityException.class,
        () -> como(Perfil.OPERADOR, "CD-01").abrirInventario("CD-01", chave()));
    assertThrows(
        SecurityException.class,
        () ->
            como(Perfil.OPERADOR, "CD-01")
                .concluirInventario(i.id(), Set.of(l.id()), "Conciliação autorizada"));
  }
}
