package br.edu.estoque.dominio;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public final class Lote implements Serializable {
  private static final long serialVersionUID = 1L;
  private final UUID id, raiz;
  private final String numero, sku, deposito, fornecedor;
  private BigDecimal quantidade;
  private final BigDecimal custo;
  private final LocalDate validade;
  private final ZonedDateTime entrada;

  public Lote(
      String numero,
      String sku,
      String deposito,
      BigDecimal quantidade,
      BigDecimal custo,
      LocalDate validade,
      ZonedDateTime entrada) {
    this(
        UUID.randomUUID(),
        null,
        numero,
        sku,
        deposito,
        "PADRAO",
        quantidade,
        custo,
        validade,
        entrada);
  }

  public Lote(
      UUID id,
      UUID raiz,
      String numero,
      String sku,
      String deposito,
      String fornecedor,
      BigDecimal quantidade,
      BigDecimal custo,
      LocalDate validade,
      ZonedDateTime entrada) {
    this.id = id;
    this.raiz = raiz == null ? id : raiz;
    this.numero = Validacao.texto(numero, "Lote inválido");
    this.sku = Validacao.sku(sku);
    this.deposito = Validacao.codigo(deposito);
    this.fornecedor = Validacao.codigo(fornecedor);
    this.quantidade = Validacao.quantidade(quantidade, true);
    this.custo = Validacao.custo(custo);
    this.validade = Validacao.data(validade);
    this.entrada = entrada;
  }

  public Lote copia() {
    return new Lote(
        id, raiz, numero, sku, deposito, fornecedor, quantidade, custo, validade, entrada);
  }

  public UUID id() {
    return id;
  }

  public UUID raiz() {
    return raiz;
  }

  public String numero() {
    return numero;
  }

  public String sku() {
    return sku;
  }

  public String deposito() {
    return deposito;
  }

  public String fornecedor() {
    return fornecedor;
  }

  public BigDecimal quantidade() {
    return quantidade;
  }

  public BigDecimal custo() {
    return custo;
  }

  public LocalDate validade() {
    return validade;
  }

  public ZonedDateTime entrada() {
    return entrada;
  }

  public void adicionar(BigDecimal q) {
    quantidade = Validacao.quantidade(quantidade.add(Validacao.quantidade(q, false)), true);
  }

  public void retirar(BigDecimal q) {
    quantidade = Validacao.quantidade(quantidade.subtract(Validacao.quantidade(q, false)), true);
  }
}
