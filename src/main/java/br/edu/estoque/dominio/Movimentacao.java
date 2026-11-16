package br.edu.estoque.dominio;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;

public record Movimentacao(
    UUID id,
    TipoMovimento tipo,
    String sku,
    String origem,
    String destino,
    BigDecimal quantidade,
    BigDecimal valor,
    ZonedDateTime instante,
    String chave,
    String ator,
    Map<UUID, BigDecimal> lotes,
    String motivo)
    implements Serializable {
  public Movimentacao {
    lotes = Collections.unmodifiableMap(new LinkedHashMap<>(lotes));
  }
}
