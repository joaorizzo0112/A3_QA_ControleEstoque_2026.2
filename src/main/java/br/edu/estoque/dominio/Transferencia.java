package br.edu.estoque.dominio;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;

public record Transferencia(
    UUID id,
    String sku,
    String origem,
    String destino,
    BigDecimal quantidade,
    String chave,
    ZonedDateTime instante,
    List<UUID> lotesDestino,
    String estado,
    List<ItemTransferencia> itens)
    implements Serializable {
  public Transferencia {
    lotesDestino = List.copyOf(lotesDestino);
    itens = List.copyOf(itens);
  }
}
