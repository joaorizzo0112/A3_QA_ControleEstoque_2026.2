package br.edu.estoque;

import static org.junit.jupiter.api.Assertions.*;

import br.edu.estoque.dominio.*;
import br.edu.estoque.repositorio.*;
import br.edu.estoque.servico.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class PersistenciaTest extends BaseTeste {
  @TempDir Path pasta;

  @Test
  void RNF018_recuperaDadosEIdempotenciaAposReinicio() {
    Path arquivo = pasta.resolve("estoque.bin");
    banco = new BancoEmMemoria(new ArmazenamentoArquivo(arquivo));
    s = new ServicoEstoque(banco, clock);
    base(s);
    entrada("L1", "10", "2", 60);
    String k = chave();
    Transferencia a = s.transferir("SKU-001", "CD-01", "LOJA-01", bd("3"), k);
    ServicoEstoque reiniciado =
        new ServicoEstoque(new BancoEmMemoria(new ArmazenamentoArquivo(arquivo)), clock);
    Transferencia b = reiniciado.transferir("SKU-001", "CD-01", "LOJA-01", bd("3"), k);
    assertEquals(a.id(), b.id());
    decimal("7", reiniciado.saldoFisico("SKU-001", "CD-01"));
    assertFalse(reiniciado.eventos(agora().minusDays(1), agora().plusDays(1)).isEmpty());
  }

  @Test
  void RNF017_conservaHistoricoDepoisDeCincoAnos() {
    Path arquivo = pasta.resolve("estoque.bin");
    banco = new BancoEmMemoria(new ArmazenamentoArquivo(arquivo));
    s = new ServicoEstoque(banco, clock);
    base(s);
    entrada("L1", "10", "2", 60);
    ZonedDateTime inicio = agora().minusDays(1);
    clock.avancar(Duration.ofDays(366 * 5));
    ServicoEstoque reiniciado =
        new ServicoEstoque(new BancoEmMemoria(new ArmazenamentoArquivo(arquivo)), clock);
    assertEquals(1, reiniciado.movimentos(null, null, null, inicio, agora()).size());
    assertTrue(reiniciado.eventos(inicio, agora()).size() >= 4);
  }

  @Test
  void arquivoCorrompidoNaoEApagadoNemInicializado() throws Exception {
    Path arquivo = pasta.resolve("estoque.bin");
    Files.writeString(arquivo, "conteudo inválido");
    assertThrows(
        IllegalStateException.class, () -> new BancoEmMemoria(new ArmazenamentoArquivo(arquivo)));
    assertEquals("conteudo inválido", Files.readString(arquivo));
  }

  @Test
  void historicoDeInventarioEReservaSobreviveAoReinicio() {
    Path arquivo = pasta.resolve("estoque.bin");
    banco = new BancoEmMemoria(new ArmazenamentoArquivo(arquivo));
    s = new ServicoEstoque(banco, clock);
    base(s);
    Lote l = entrada("L1", "10", "2", 60);
    Reserva r = reservar("1");
    Inventario i = s.abrirInventario("CD-01", chave());
    s.iniciarInventario(i.id());
    s.registrarContagem(i.id(), l.id(), bd("9"));
    ServicoEstoque novo =
        new ServicoEstoque(new BancoEmMemoria(new ArmazenamentoArquivo(arquivo)), clock);
    assertEquals(r.id(), novo.reservas().get(0).id());
    assertEquals(1, novo.inventarios().get(0).historico().size());
  }
}
