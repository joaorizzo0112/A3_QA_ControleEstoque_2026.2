package br.edu.estoque.dominio;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record VersaoProduto(
    String sku,
    String nome,
    Unidade unidade,
    BigDecimal estoqueMinimo,
    boolean ativo,
    String ator,
    ZonedDateTime instante)
    implements Serializable {}
