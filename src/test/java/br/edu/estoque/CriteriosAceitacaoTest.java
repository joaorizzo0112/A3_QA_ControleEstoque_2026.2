package br.edu.estoque;

import static org.junit.jupiter.api.Assertions.*;

import br.edu.estoque.dominio.*;
import br.edu.estoque.repositorio.*;
import br.edu.estoque.servico.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** IDs preservados conforme páginas 30–32 da ERS. */
class CriteriosAceitacaoTest extends BaseTeste {
  @Test
  void CA001_entradaMinima() {
    entrada("L1", "0.001", "0.01", 1);
    decimal("0.001", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void CA002_quantidadeZero() {
    assertThrows(IllegalArgumentException.class, () -> entrada("L1", "0", "1", 1));
    assertTrue(banco.lotes.isEmpty());
  }

  @Test
  void CA003_quartaCasa() {
    assertThrows(IllegalArgumentException.class, () -> entrada("L1", "1.0001", "1", 1));
    assertTrue(banco.lotes.isEmpty());
  }

  @Test
  void CA004_normalizaSku() {
    Produto p = s.cadastrarProduto(" sku-002 ", "Outro", Unidade.UN, bd("0"));
    assertEquals("SKU-002", p.sku());
  }

  @Test
  void CA005_duplicataEquivalente() {
    assertThrows(
        IllegalStateException.class,
        () -> s.cadastrarProduto(" sku-001 ", "Outro", Unidade.UN, bd("0")));
    assertEquals(1, s.produtos().size());
  }

  @Test
  void CA006_disponivelDescontaReserva() {
    entrada("L1", "10", "1", 60);
    reservar("3");
    decimal("10", s.saldoFisico("SKU-001", "CD-01"));
    decimal("3", s.saldoReservado("SKU-001", "CD-01"));
    decimal("7", s.saldoDisponivel("SKU-001", "CD-01"));
  }

  @Test
  void CA007_saidaExataDisponivel() {
    entrada("L1", "10", "1", 60);
    reservar("3");
    s.registrarSaida("SKU-001", "CD-01", bd("7"), chave());
    decimal("0", s.saldoDisponivel("SKU-001", "CD-01"));
    decimal("3", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void CA008_saidaExcedenteMinimaSemEfeito() {
    Lote l = entrada("L1", "10", "1", 60);
    reservar("3");
    assertThrows(
        IllegalStateException.class,
        () -> s.registrarSaida("SKU-001", "CD-01", bd("7.001"), chave()));
    decimal("10", s.saldoFisico("SKU-001", "CD-01"));
    decimal("7", s.saldoDisponivel("SKU-001", "CD-01"));
    decimal("10", s.lotes("SKU-001", "CD-01").get(0).quantidade());
  }

  @Test
  void CA009_fefoIndependeDaOrdemDeEntrada() {
    Lote b = entrada("B", "5", "2", 25), a = entrada("A", "5", "2", 5);
    s.registrarSaida("SKU-001", "CD-01", bd("5"), chave());
    decimal("0", banco.lotes.get(a.id()).quantidade());
    decimal("5", banco.lotes.get(b.id()).quantidade());
  }

  @Test
  void CA010_desempatePelaEntrada() {
    Lote a = entrada("A", "5", "2", 25);
    clock.avancar(Duration.ofSeconds(1));
    Lote b = entrada("B", "5", "2", 25);
    s.registrarSaida("SKU-001", "CD-01", bd("5"), chave());
    decimal("0", banco.lotes.get(a.id()).quantidade());
    decimal("5", banco.lotes.get(b.id()).quantidade());
  }

  @Test
  void CA011_vencimentoAposEntradaBloqueiaSaida() {
    entrada("L1", "10", "1", 0);
    clock.avancar(Duration.ofDays(1));
    assertThrows(
        IllegalStateException.class, () -> s.registrarSaida("SKU-001", "CD-01", bd("1"), chave()));
    decimal("10", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void CA012_reservaTodoDisponivel() {
    entrada("L1", "10", "1", 60);
    reservar("10");
    decimal("0", s.saldoDisponivel("SKU-001", "CD-01"));
    decimal("10", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void CA013_reservaExcedenteMinima() {
    entrada("L1", "10", "1", 60);
    assertThrows(IllegalStateException.class, () -> reservar("10.001"));
    assertTrue(banco.reservas.isEmpty());
  }

  @Test
  void CA014_expiraNoInstanteExato() {
    entrada("L1", "10", "1", 60);
    Reserva r = s.reservar("SKU-001", "CD-01", bd("3"), agora().plusSeconds(1), chave());
    clock.avancar(Duration.ofSeconds(1));
    s.expirarReservas();
    assertEquals(EstadoReserva.EXPIRADA, banco.reservas.get(r.id()).estado());
    decimal("10", s.saldoDisponivel("SKU-001", "CD-01"));
  }

  @Test
  void CA015_cancelarDuasVezesLiberaUma() {
    entrada("L1", "10", "1", 60);
    Reserva r = reservar("3");
    s.cancelarReserva(r.id());
    s.cancelarReserva(r.id());
    decimal("10", s.saldoDisponivel("SKU-001", "CD-01"));
    decimal("0", s.saldoReservado("SKU-001", "CD-01"));
  }

  @Test
  void CA016_transferenciaPreservaSaldoGlobal() {
    entrada("L1", "10", "2", 60);
    s.transferir("SKU-001", "CD-01", "LOJA-01", bd("4"), chave());
    decimal("10", s.saldoFisico("SKU-001", "CD-01").add(s.saldoFisico("SKU-001", "LOJA-01")));
  }

  static final class DiscoComFalha implements Armazenamento {
    boolean falhar;

    public BancoEmMemoria.Dados carregar() {
      return new BancoEmMemoria.Dados();
    }

    public void salvar(BancoEmMemoria.Dados d) throws IOException {
      if (falhar) {
        falhar = false;
        throw new IOException("falha de disco simulada");
      }
    }
  }

  @Test
  void CA017_falhaNaConfirmacaoPreservaAmbos() {
    DiscoComFalha disco = new DiscoComFalha();
    banco = new BancoEmMemoria(disco);
    s = new ServicoEstoque(banco, clock);
    base(s);
    entrada("L1", "10", "2", 60);
    String key = chave();
    disco.falhar = true;
    assertThrows(
        IllegalStateException.class,
        () -> s.transferir("SKU-001", "CD-01", "LOJA-01", bd("4"), key));
    decimal("10", s.saldoFisico("SKU-001", "CD-01"));
    decimal("0", s.saldoFisico("SKU-001", "LOJA-01"));
    assertTrue(banco.transferencias.isEmpty());
    assertEquals(
        1, s.movimentos(null, null, null, agora().minusDays(1), agora().plusDays(1)).size());
    s.transferir("SKU-001", "CD-01", "LOJA-01", bd("4"), key);
    decimal("6", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void CA018_idempotenciaIgual() {
    entrada("L1", "10", "2", 60);
    String k = chave();
    Transferencia a = s.transferir("SKU-001", "CD-01", "LOJA-01", bd("4"), k),
        b = s.transferir("sku-001", "cd-01", "loja-01", bd("4.000"), k);
    assertSame(a, b);
    decimal("6", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void CA019_idempotenciaDivergente() {
    entrada("L1", "10", "2", 60);
    String k = chave();
    s.transferir("SKU-001", "CD-01", "LOJA-01", bd("4"), k);
    assertThrows(
        IllegalStateException.class, () -> s.transferir("SKU-001", "CD-01", "LOJA-01", bd("5"), k));
    decimal("6", s.saldoFisico("SKU-001", "CD-01"));
    decimal("4", s.saldoFisico("SKU-001", "LOJA-01"));
  }

  private Inventario contado(Lote lote, String q) {
    Inventario i = s.abrirInventario("CD-01", chave());
    s.iniciarInventario(i.id());
    s.registrarContagem(i.id(), lote.id(), bd(q));
    s.revisarInventario(i.id());
    return i;
  }

  @Test
  void CA020_contagemZero() {
    Lote l = entrada("L1", "10", "2", 60);
    Inventario i = contado(l, "0");
    s.concluirInventario(i.id(), Set.of(l.id()), "Contagem física aprovada");
    decimal("0", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void CA021_concluirDuasVezesNaoDuplica() {
    Lote l = entrada("L1", "10", "2", 60);
    Inventario i = contado(l, "8");
    Inventario a = s.concluirInventario(i.id(), Set.of(l.id()), "Contagem física aprovada"),
        b = s.concluirInventario(i.id(), Set.of(l.id()), "Contagem física aprovada");
    assertEquals(a.ajustes(), b.ajustes());
    assertEquals(1, b.ajustes().size());
    decimal("8", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void CA022_alertaNaIgualdade() {
    entrada("L1", "5", "2", 60);
    assertTrue(s.requerReposicao("SKU-001", "CD-01"));
    assertTrue(
        s.gerarAlertas().stream()
            .anyMatch(a -> a.tipo().equals("REPOSICAO") && a.deposito().equals("CD-01")));
  }

  @Test
  void CA023_alertaEm30Dias() {
    Lote l = entrada("L1", "5", "2", 30);
    assertTrue(s.lotesProximosDoVencimento().stream().anyMatch(x -> x.id().equals(l.id())));
    assertTrue(s.gerarAlertas().stream().anyMatch(a -> l.id().equals(a.loteId())));
  }

  @Test
  void CA024_intervaloInclusivo() {
    entrada("L1", "1", "2", 60);
    ZonedDateTime inicio = agora();
    clock.avancar(Duration.ofSeconds(1));
    entrada("L2", "1", "2", 60);
    ZonedDateTime fim = agora();
    assertEquals(2, s.movimentos("SKU-001", "CD-01", TipoMovimento.ENTRADA, inicio, fim).size());
    assertThrows(IllegalArgumentException.class, () -> s.movimentos(null, null, null, fim, inicio));
  }
}
