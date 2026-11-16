package br.edu.estoque.dominio;

import java.io.Serializable;
import java.util.Set;

public record Ator(String nome, Perfil perfil, Set<String> depositos) implements Serializable {
  public Ator {
    nome = Validacao.texto(nome, "Ator inválido");
    if (perfil == null || depositos == null) throw new IllegalArgumentException("Ator inválido");
    depositos = Set.copyOf(depositos);
  }

  public static Ator gestorLocal() {
    return new Ator("gestor-local", Perfil.GESTOR, Set.of("*"));
  }

  public boolean permite(String deposito) {
    return depositos.contains("*") || depositos.contains(Validacao.codigo(deposito));
  }
}
