package br.edu.estoque.dominio;

import java.io.Serializable;
import java.math.BigDecimal;

public record ItemTransferencia(String sku, BigDecimal quantidade) implements Serializable {
  public ItemTransferencia {
    sku = Validacao.sku(sku);
    quantidade = Validacao.quantidade(quantidade, false);
  }
}
