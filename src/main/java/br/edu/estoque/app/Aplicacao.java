package br.edu.estoque.app;

import br.edu.estoque.dominio.*;
import br.edu.estoque.repositorio.*;
import br.edu.estoque.servico.ServicoEstoque;
import br.edu.estoque.web.ServidorEstoque;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

public final class Aplicacao {
  private Aplicacao() {}

  public static void main(String[] args) throws Exception {
    List<String> opcoes = Arrays.asList(args);
    if (opcoes.contains("--demo")) {
      demonstracao();
      return;
    }
    int porta = Integer.parseInt(valor(opcoes, "--porta", "8080"));
    if (porta < 1 || porta > 65535)
      throw new IllegalArgumentException("Escolha uma porta entre 1 e 65535");
    int limiteDepositos =
        Integer.parseInt(
            System.getProperty("estoque.limiteDepositos", Integer.toString(Integer.MAX_VALUE)));
    BancoEmMemoria banco =
        opcoes.contains("--memoria")
            ? new BancoEmMemoria(null, limiteDepositos)
            : new BancoEmMemoria(
                new ArmazenamentoArquivo(Path.of(valor(opcoes, "--dados", ".dados/estoque.bin"))),
                limiteDepositos);
    Clock clock = Clock.system(ZoneId.of("America/Sao_Paulo"));
    if (banco.produtos.isEmpty()) prepararDemonstracao(banco, clock);
    Map<String, String> senhas =
        Map.of(
            "gestor",
            senha("GESTOR", "a3-gestor-2026"),
            "operador",
            senha("OPERADOR", "a3-operador-2026"),
            "auditor",
            senha("AUDITOR", "a3-auditor-2026"));
    ServidorEstoque servidor = new ServidorEstoque(banco, clock, porta, senhas);
    Runtime.getRuntime().addShutdownHook(new Thread(servidor::close));
    servidor.iniciar();
    System.out.println("Controle de Estoque: http://localhost:" + servidor.porta());
    System.out.println(
        "Contas de demonstração e instruções disponíveis no README.md. Encerre com Ctrl+C.");
  }

  private static String senha(String perfil, String padrao) {
    return System.getenv().getOrDefault("ESTOQUE_SENHA_" + perfil, padrao);
  }

  private static String valor(List<String> args, String opcao, String padrao) {
    int i = args.indexOf(opcao);
    if (i < 0) return padrao;
    if (i + 1 >= args.size()) throw new IllegalArgumentException("Informe o valor de " + opcao);
    return args.get(i + 1);
  }

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }

  public static void prepararDemonstracao(BancoEmMemoria banco, Clock clock) {
    ServicoEstoque s = new ServicoEstoque(banco, clock);
    s.cadastrarProduto("SKU-001", "Cabo de rede", Unidade.UN, bd("5.000"));
    s.cadastrarProduto("SKU-002", "Material a granel", Unidade.KG, bd("10.000"));
    s.cadastrarDeposito("CD-01", "Centro de distribuição");
    s.cadastrarDeposito("LOJA-01", "Loja principal");
    s.registrarEntrada(
        "SKU-001",
        "CD-01",
        "FORN-01",
        "LOTE-001",
        bd("20.000"),
        bd("12.50"),
        LocalDate.now(clock).plusDays(90),
        "demonstracao-entrada-001");
    s.registrarEntrada(
        "SKU-002",
        "CD-01",
        "FORN-02",
        "LOTE-002",
        bd("15.500"),
        bd("8.75"),
        LocalDate.now(clock).plusDays(20),
        "demonstracao-entrada-002");
  }

  public static void demonstracao() {
    Clock clock =
        Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    BancoEmMemoria banco = new BancoEmMemoria();
    prepararDemonstracao(banco, clock);
    ServicoEstoque s = new ServicoEstoque(banco, clock);
    System.out.printf(
        "Saldo físico: %s | disponível: %s%n",
        s.saldoFisico("SKU-001", "CD-01"), s.saldoDisponivel("SKU-001", "CD-01"));
    Reserva r =
        s.reservar(
            "SKU-001",
            "CD-01",
            bd("5.000"),
            ZonedDateTime.now(clock).plusDays(1),
            "demonstracao-reserva-001");
    System.out.println(
        "Após reservar 5: físico=20.000 | reservado=5.000 | disponível="
            + s.saldoDisponivel("SKU-001", "CD-01"));
    s.registrarSaida("SKU-001", "CD-01", bd("3.000"), "demonstracao-saida-001");
    System.out.println(
        "Após sair 3: físico="
            + s.saldoFisico("SKU-001", "CD-01")
            + " | disponível="
            + s.saldoDisponivel("SKU-001", "CD-01"));
    s.atenderReserva(r.id(), "demonstracao-atendimento-001");
    System.out.println(
        "Após atender a reserva: físico="
            + s.saldoFisico("SKU-001", "CD-01")
            + " | disponível="
            + s.saldoDisponivel("SKU-001", "CD-01"));
    s.transferir("SKU-001", "CD-01", "LOJA-01", bd("4.000"), "demonstracao-transferencia-001");
    System.out.println(
        "Após transferir 4: CD-01="
            + s.saldoFisico("SKU-001", "CD-01")
            + " | LOJA-01="
            + s.saldoFisico("SKU-001", "LOJA-01"));
    System.out.println("Custo médio na loja: R$ " + s.custoMedio("SKU-001", "LOJA-01"));
    Lote l = s.lotes("SKU-001", "CD-01").get(0);
    s.ajustarLote(l.id(), bd("1.000"), "Ajuste de conferência física", "demonstracao-ajuste-001");
    Inventario i = s.abrirInventario("CD-01", "demonstracao-inventario-001");
    s.iniciarInventario(i.id());
    s.registrarContagem(i.id(), l.id(), bd("8.000"));
    s.revisarInventario(i.id());
    i = s.concluirInventario(i.id(), Set.of(l.id()), "Divergência aprovada no inventário");
    System.out.println(
        "Inventário: " + i.estado() + " | saldo final CD-01=" + s.saldoFisico("SKU-001", "CD-01"));
    System.out.println("Alertas gerados: " + s.gerarAlertas().size());
  }
}
