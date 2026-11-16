package br.edu.estoque.dominio;

import java.math.*;
import java.time.*;
import java.util.Locale;

public final class Validacao {
  public static final BigDecimal MAX_QUANTIDADE = new BigDecimal("999999.999");
  public static final BigDecimal MAX_CUSTO = new BigDecimal("9999999.99");

  private Validacao() {}

  public static String texto(String s, String mensagem) {
    if (s == null || s.isBlank()) throw new IllegalArgumentException(mensagem);
    return s.trim();
  }

  public static String sku(String s) {
    String v = texto(s, "SKU inválido").toUpperCase(Locale.ROOT);
    if (!v.matches("[A-Z0-9_-]{3,20}")) throw new IllegalArgumentException("SKU inválido");
    return v;
  }

  public static String codigo(String s) {
    return texto(s, "Código inválido").toUpperCase(Locale.ROOT);
  }

  public static BigDecimal quantidade(BigDecimal q, boolean aceitaZero) {
    if (q == null
        || (aceitaZero ? q.signum() < 0 : q.signum() <= 0)
        || q.scale() > 3
        || q.compareTo(MAX_QUANTIDADE) > 0)
      throw new IllegalArgumentException("Quantidade inválida");
    return q.setScale(3, RoundingMode.HALF_UP);
  }

  public static BigDecimal variacao(BigDecimal q) {
    if (q == null || q.signum() == 0) throw new IllegalArgumentException("Quantidade inválida");
    quantidade(q.abs(), false);
    return q.setScale(3, RoundingMode.HALF_UP);
  }

  public static BigDecimal custo(BigDecimal c) {
    if (c == null || c.compareTo(new BigDecimal("0.01")) < 0 || c.compareTo(MAX_CUSTO) > 0)
      throw new IllegalArgumentException("Custo inválido");
    return c.setScale(2, RoundingMode.HALF_UP);
  }

  public static LocalDate data(LocalDate d) {
    if (d == null || d.isBefore(LocalDate.of(2000, 1, 1)) || d.isAfter(LocalDate.of(2100, 12, 31)))
      throw new IllegalArgumentException("Data inválida");
    return d;
  }

  public static String chave(String c) {
    String v = texto(c, "Chave inválida");
    if (v.length() < 16 || v.length() > 64) throw new IllegalArgumentException("Chave inválida");
    return v;
  }

  public static String motivo(String s) {
    String v = texto(s, "Motivo deve possuir de 10 a 200 caracteres");
    if (v.length() < 10 || v.length() > 200)
      throw new IllegalArgumentException("Motivo deve possuir de 10 a 200 caracteres");
    return v;
  }
}
