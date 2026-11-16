package br.edu.estoque.dominio;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.UUID;

public record Evento(
    UUID id,
    String ator,
    String acao,
    String recurso,
    ZonedDateTime instante,
    String resultado,
    String codigo,
    String correlacao)
    implements Serializable {}
