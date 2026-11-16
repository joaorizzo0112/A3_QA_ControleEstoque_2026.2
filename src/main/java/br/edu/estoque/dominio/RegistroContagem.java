package br.edu.estoque.dominio;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

public record RegistroContagem(
    UUID loteId, BigDecimal quantidade, String ator, ZonedDateTime instante)
    implements Serializable {}
