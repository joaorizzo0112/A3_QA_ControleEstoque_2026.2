package br.edu.estoque.dominio;

import java.io.Serializable;
import java.util.UUID;

public final class Deposito implements Serializable {
  private static final long serialVersionUID = 1L;
  private final UUID id;
  private final String codigo, nome;
  private boolean ativo = true;

  public Deposito(String codigo, String nome) {
    id = UUID.randomUUID();
    this.codigo = Validacao.codigo(codigo);
    this.nome = Validacao.texto(nome, "Nome inválido");
  }

  private Deposito(Deposito d) {
    id = d.id;
    codigo = d.codigo;
    nome = d.nome;
    ativo = d.ativo;
  }

  public Deposito copia() {
    return new Deposito(this);
  }

  public UUID id() {
    return id;
  }

  public String codigo() {
    return codigo;
  }

  public String nome() {
    return nome;
  }

  public boolean ativo() {
    return ativo;
  }

  public void inativar() {
    ativo = false;
  }
}
