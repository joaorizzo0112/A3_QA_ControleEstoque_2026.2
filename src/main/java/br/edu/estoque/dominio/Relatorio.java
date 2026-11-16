package br.edu.estoque.dominio;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public record Relatorio(
    ZonedDateTime inicio,
    ZonedDateTime fim,
    List<Posicao> posicoes,
    List<Movimentacao> movimentos,
    BigDecimal entradas,
    BigDecimal saidas,
    BigDecimal perdas,
    BigDecimal valorPerdas,
    BigDecimal valorEstoque,
    BigDecimal giro) {
  public Relatorio {
    posicoes = List.copyOf(posicoes);
    movimentos = List.copyOf(movimentos);
  }
}
