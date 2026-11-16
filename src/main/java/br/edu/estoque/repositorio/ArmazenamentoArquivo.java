package br.edu.estoque.repositorio;

import java.io.*;
import java.nio.file.*;

/** Snapshot local opcional. Não remove histórico; conserva também chaves de idempotência. */
public final class ArmazenamentoArquivo implements Armazenamento {
  private final Path arquivo;

  public ArmazenamentoArquivo(Path arquivo) {
    this.arquivo = arquivo.toAbsolutePath();
  }

  public BancoEmMemoria.Dados carregar() throws IOException {
    if (!Files.exists(arquivo)) return new BancoEmMemoria.Dados();
    try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(arquivo))) {
      in.setObjectInputFilter(
          info -> {
            if (info.depth() > 80
                || info.references() > 2_000_000
                || info.streamBytes() > 100_000_000) return ObjectInputFilter.Status.REJECTED;
            Class<?> c = info.serialClass();
            if (c == null) return ObjectInputFilter.Status.UNDECIDED;
            while (c.isArray()) c = c.getComponentType();
            String nome = c.getName();
            return c.isPrimitive()
                    || nome.startsWith("br.edu.estoque.")
                    || nome.startsWith("java.util.")
                    || nome.startsWith("java.time.")
                    || nome.startsWith("java.math.")
                    || nome.startsWith("java.lang.")
                ? ObjectInputFilter.Status.ALLOWED
                : ObjectInputFilter.Status.REJECTED;
          });
      Object obj = in.readObject();
      if (!(obj instanceof BancoEmMemoria.Dados d)) throw new IOException("Formato local inválido");
      return d;
    } catch (ClassNotFoundException | RuntimeException e) {
      throw new IOException("Formato local inválido", e);
    }
  }

  public void salvar(BancoEmMemoria.Dados dados) throws IOException {
    Path pasta = arquivo.getParent();
    Files.createDirectories(pasta);
    Path temporario = Files.createTempFile(pasta, "estoque-", ".tmp");
    try {
      try (FileOutputStream file = new FileOutputStream(temporario.toFile());
          ObjectOutputStream out = new ObjectOutputStream(file)) {
        out.writeObject(dados);
        out.flush();
        file.getFD().sync();
      }
      Files.move(
          temporario, arquivo, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporario);
    }
  }
}
