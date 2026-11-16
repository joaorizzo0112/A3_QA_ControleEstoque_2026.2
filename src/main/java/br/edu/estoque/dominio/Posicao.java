package br.edu.estoque.dominio;

import java.math.BigDecimal;

public record Posicao(
    String sku,
    String deposito,
    BigDecimal fisico,
    BigDecimal reservado,
    BigDecimal disponivel,
    BigDecimal custoMedio,
    BigDecimal valorEstoque,
    boolean consistente) {}
