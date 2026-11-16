package br.edu.estoque.repositorio;

import java.io.IOException;

public interface Armazenamento {
  BancoEmMemoria.Dados carregar() throws IOException;

  void salvar(BancoEmMemoria.Dados dados) throws IOException;
}
