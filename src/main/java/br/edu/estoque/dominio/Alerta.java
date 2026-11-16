package br.edu.estoque.dominio;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

public record Alerta(
    UUID id, String tipo, String sku, String deposito, UUID loteId, LocalDate dia, String mensagem)
    implements Serializable {}
