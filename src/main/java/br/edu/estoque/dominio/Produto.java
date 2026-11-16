package br.edu.estoque.dominio;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;

public final class Produto implements Serializable {
  private static final long serialVersionUID = 1L;
  private final UUID id;
  private final String sku;
  private String nome;
  private Unidade unidade;
  private BigDecimal estoqueMinimo;
  private boolean ativo = true;

  public Produto(String sku, String nome, Unidade unidade, BigDecimal minimo) {
    this.id = UUID.randomUUID();
    this.sku = Validacao.sku(sku);
    alterar(nome, unidade, minimo);
  }

  private Produto(Produto p) {
    id = p.id;
    sku = p.sku;
    nome = p.nome;
    unidade = p.unidade;
    estoqueMinimo = p.estoqueMinimo;
    ativo = p.ativo;
  }

  public Produto copia() {
    return new Produto(this);
  }

  public void alterar(String nome, Unidade unidade, BigDecimal minimo) {
    this.nome = Validacao.texto(nome, "Nome inválido");
    if (unidade == null) throw new IllegalArgumentException("Unidade inválida");
    this.unidade = unidade;
    this.estoqueMinimo = Validacao.quantidade(minimo, true);
  }

  public void inativar() {
    ativo = false;
  }

  public UUID id() {
    return id;
  }

  public String sku() {
    return sku;
  }

  public String nome() {
    return nome;
  }

  public Unidade unidade() {
    return unidade;
  }

  public BigDecimal estoqueMinimo() {
    return estoqueMinimo;
  }

  public boolean ativo() {
    return ativo;
  }
}
