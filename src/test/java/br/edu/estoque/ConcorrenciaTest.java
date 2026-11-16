package br.edu.estoque;

import static org.junit.jupiter.api.Assertions.*;

import br.edu.estoque.dominio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConcorrenciaTest extends BaseTeste {
  private void simultaneas(int n, Runnable acao) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(n);
    CountDownLatch prontas = new CountDownLatch(n), partida = new CountDownLatch(1);
    List<Future<?>> fs = new ArrayList<>();
    try {
      for (int x = 0; x < n; x++)
        fs.add(
            pool.submit(
                () -> {
                  prontas.countDown();
                  try {
                    partida.await();
                    acao.run();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                  }
                }));
      assertTrue(prontas.await(5, TimeUnit.SECONDS));
      partida.countDown();
      for (Future<?> f : fs) f.get(15, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void RNF003_saidasConcorrentesNaoDuplicamConsumo() throws Exception {
    entrada("L1", "10", "2", 60);
    AtomicInteger aceitas = new AtomicInteger();
    simultaneas(
        24,
        () -> {
          try {
            s.registrarSaida("SKU-001", "CD-01", bd("1"), chave());
            aceitas.incrementAndGet();
          } catch (IllegalStateException esperado) {
            assertTrue(esperado.getMessage().contains("Saldo"));
          }
        });
    assertEquals(10, aceitas.get());
    decimal("0", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void RNF018_transferenciaConcorrenteComMesmaChave() throws Exception {
    entrada("L1", "10", "2", 60);
    String key = chave();
    Set<UUID> ids = ConcurrentHashMap.newKeySet();
    simultaneas(16, () -> ids.add(s.transferir("SKU-001", "CD-01", "LOJA-01", bd("3"), key).id()));
    assertEquals(1, ids.size());
    decimal("7", s.saldoFisico("SKU-001", "CD-01"));
    decimal("3", s.saldoFisico("SKU-001", "LOJA-01"));
  }

  @Test
  void RN010_reservasConcorrentesNaoExcedemFisico() throws Exception {
    entrada("L1", "10", "2", 60);
    AtomicInteger aceitas = new AtomicInteger();
    simultaneas(
        20,
        () -> {
          try {
            reservar("1");
            aceitas.incrementAndGet();
          } catch (IllegalStateException esperado) {
            assertTrue(esperado.getMessage().contains("Saldo"));
          }
        });
    assertEquals(10, aceitas.get());
    decimal("10", s.saldoReservado("SKU-001", "CD-01"));
    decimal("0", s.saldoDisponivel("SKU-001", "CD-01"));
  }

  @Test
  void RF013_multiplosItensSaoAtomicos() {
    entrada("L1", "10", "2", 60);
    s.cadastrarProduto("SKU-002", "Outro", Unidade.UN, bd("0"));
    s.registrarEntrada("SKU-002", "CD-01", "F", "L2", bd("1"), bd("3"), data(60), chave());
    assertThrows(
        IllegalStateException.class,
        () ->
            s.transferirItens(
                "CD-01",
                "LOJA-01",
                List.of(
                    new ItemTransferencia("SKU-001", bd("3")),
                    new ItemTransferencia("SKU-002", bd("2"))),
                chave()));
    decimal("10", s.saldoFisico("SKU-001", "CD-01"));
    decimal("1", s.saldoFisico("SKU-002", "CD-01"));
    decimal("0", s.saldoFisico("SKU-001", "LOJA-01"));
  }

  @Test
  void RF013_multiplosItensConcluidosJuntos() {
    entrada("L1", "10", "2", 60);
    s.cadastrarProduto("SKU-002", "Outro", Unidade.UN, bd("0"));
    s.registrarEntrada("SKU-002", "CD-01", "F", "L2", bd("2"), bd("3"), data(60), chave());
    Transferencia t =
        s.transferirItens(
            "CD-01",
            "LOJA-01",
            List.of(
                new ItemTransferencia("SKU-001", bd("3")),
                new ItemTransferencia("SKU-002", bd("2"))),
            chave());
    assertEquals(2, t.itens().size());
    decimal("3", s.saldoFisico("SKU-001", "LOJA-01"));
    decimal("2", s.saldoFisico("SKU-002", "LOJA-01"));
  }
}
