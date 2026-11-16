package br.edu.estoque;

import static org.junit.jupiter.api.Assertions.*;

import br.edu.estoque.dominio.*;
import br.edu.estoque.repositorio.*;
import br.edu.estoque.servico.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;

abstract class BaseTeste {
  BancoEmMemoria banco;
  ServicoEstoque s;
  RelogioAjustavel clock;

  @BeforeEach
  void preparar() {
    clock = new RelogioAjustavel(Instant.parse("2026-11-15T12:00:00Z"));
    banco = new BancoEmMemoria();
    s = new ServicoEstoque(banco, clock);
    base(s);
  }

  static BigDecimal bd(String v) {
    return new BigDecimal(v);
  }

  void base(ServicoEstoque servico) {
    servico.cadastrarProduto("SKU-001", "Cabo de rede", Unidade.UN, bd("5.000"));
    servico.cadastrarDeposito("CD-01", "Origem");
    servico.cadastrarDeposito("LOJA-01", "Destino");
  }

  String chave() {
    return UUID.randomUUID().toString();
  }

  LocalDate data(int dias) {
    return LocalDate.now(clock).plusDays(dias);
  }

  ZonedDateTime agora() {
    return ZonedDateTime.now(clock);
  }

  Lote entrada(String numero, String q, String c, int dias) {
    return s.registrarEntrada(
        "SKU-001", "CD-01", "FORN-01", numero, bd(q), bd(c), data(dias), chave());
  }

  Reserva reservar(String q) {
    return s.reservar("SKU-001", "CD-01", bd(q), agora().plusHours(1), chave());
  }

  void decimal(String esperado, BigDecimal obtido) {
    assertEquals(0, bd(esperado).compareTo(obtido), "Esperado " + esperado + "; obtido " + obtido);
  }

  ServicoEstoque como(Perfil perfil, String... dep) {
    return new ServicoEstoque(
        banco, clock, new Ator(perfil.name().toLowerCase(Locale.ROOT), perfil, Set.of(dep)));
  }
}
