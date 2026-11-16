package br.edu.estoque.servico;

import java.util.*;

public final class Erros {
  private Erros() {}

  private static final Map<Throwable, String> CORRELACOES =
      Collections.synchronizedMap(new WeakHashMap<>());

  public static String correlacao(Throwable e) {
    return CORRELACOES.computeIfAbsent(e, k -> UUID.randomUUID().toString());
  }

  public static String codigo(Throwable e) {
    String m = Objects.toString(e.getMessage(), "");
    if (e instanceof SecurityException) return m.contains("Ajuste") ? "ESTQ-007" : "ESTQ-010";
    if (m.contains("SKU inválido")) return "ESTQ-001";
    if (m.contains("Produto já cadastrado")) return "ESTQ-002";
    if (m.contains("Saldo") || m.contains("reservado")) return "ESTQ-003";
    if (m.contains("Lote vencido")) return "ESTQ-004";
    if (m.contains("Reserva não está ativa")) return "ESTQ-005";
    if (m.contains("Chave já")) return "ESTQ-006";
    if (m.contains("Motivo") || m.contains("Ajuste")) return "ESTQ-007";
    if (m.contains("Inventário") || m.contains("inventário")) return "ESTQ-008";
    return "ESTQ-009";
  }

  public static int status(Throwable e) {
    if (e instanceof SecurityException) return 403;
    if (e instanceof NoSuchElementException) return 404;
    if (e instanceof IllegalArgumentException)
      return Objects.toString(e.getMessage(), "").contains("Expiração") ? 422 : 400;
    if (e.getCause() != null) return 500;
    return e instanceof IllegalStateException ? 409 : 500;
  }

  public static String mensagemPublica(Throwable e) {
    if (status(e) == 500)
      return "Não foi possível concluir. Tente novamente e informe o identificador de atendimento.";
    if (e instanceof SecurityException)
      return codigo(e).equals("ESTQ-007") ? "Ajuste não autorizado." : "Operação não autorizada.";
    return Objects.toString(e.getMessage(), "Operação não permitida para o estado atual.");
  }
}
