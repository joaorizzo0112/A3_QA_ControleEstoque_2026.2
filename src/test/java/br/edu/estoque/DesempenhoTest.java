package br.edu.estoque;

import static org.junit.jupiter.api.Assertions.*;

import br.edu.estoque.dominio.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DesempenhoTest extends BaseTeste {
  @Test
  void RNF001_RNF002_percentil95ComCemMilLotes() throws Exception {
    banco.transacionar(
        d -> {
          for (int n = 0; n < 100_000; n++) {
            Lote l =
                new Lote("CARGA-" + n, "SKU-001", "CD-01", bd("1"), bd("2"), data(60), agora());
            d.lotes.put(l.id(), l);
          }
          return null;
        });
    for (int n = 0; n < 4; n++) s.saldoFisico("SKU-001", "CD-01");
    long[] consulta = new long[20], movimento = new long[20];
    for (int n = 0; n < 20; n++) {
      long inicio = System.nanoTime();
      decimal("100000", s.saldoFisico("SKU-001", "CD-01"));
      consulta[n] = System.nanoTime() - inicio;
    }
    for (int n = 0; n < 20; n++) {
      long inicio = System.nanoTime();
      s.registrarSaida("SKU-001", "CD-01", bd("0.001"), chave());
      movimento[n] = System.nanoTime() - inicio;
    }
    Arrays.sort(consulta);
    Arrays.sort(movimento);
    double pConsulta = consulta[18] / 1_000_000.0, pMovimento = movimento[18] / 1_000_000.0;
    Path destino = Path.of("target/evidencias/desempenho.txt");
    Files.createDirectories(destino.getParent());
    Files.writeString(
        destino,
        "100000 lotes; 20 amostras por operação; P95 pelo posto ceil(0.95*N).\nConsulta P95 (ms): "
            + pConsulta
            + "\nMovimentação P95 (ms): "
            + pMovimento
            + "\nArmazenamento em memória; sem rede e sem persistência em disco.\n");
    assertTrue(pConsulta <= 2000, "Consulta P95=" + pConsulta + "ms");
    assertTrue(pMovimento <= 1000, "Movimentação P95=" + pMovimento + "ms");
  }
}
