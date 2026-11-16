package br.edu.estoque;

import br.edu.estoque.dominio.*;
import br.edu.estoque.repositorio.BancoEmMemoria;
import br.edu.estoque.servico.ServicoEstoque;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Execução independente dos 15 casos da seção 10 da especificação.
 * Não depende de bibliotecas externas; compila com Java 17+.
 */
public final class ExecucaoCT001aCT015 {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC);
    private static final List<String> RESULTADOS = new ArrayList<>();
    private static int passou, falhou;
    private record Fixture(BancoEmMemoria banco, ServicoEstoque servico) {}

    public static void main(String[] args) {
        executar("CT001", "Cadastro com sucesso de um produto válido", ExecucaoCT001aCT015::ct001);
        executar("CT002", "Rejeição de SKU com formato inválido", ExecucaoCT001aCT015::ct002);
        executar("CT003", "Cadastro com sucesso de um depósito", ExecucaoCT001aCT015::ct003);
        executar("CT004", "Rejeição de código de depósito inválido ou em branco", ExecucaoCT001aCT015::ct004);
        executar("CT005", "Registro de entrada de lote com sucesso", ExecucaoCT001aCT015::ct005);
        executar("CT006", "Rejeição de lote com data de validade vencida", ExecucaoCT001aCT015::ct006);
        executar("CT007", "Cálculo correto do saldo físico e disponível", ExecucaoCT001aCT015::ct007);
        executar("CT008", "Realização de reserva de estoque com sucesso", ExecucaoCT001aCT015::ct008);
        executar("CT009", "Tentativa de reserva com saldo insuficiente", ExecucaoCT001aCT015::ct009);
        executar("CT010", "Expiração automática de reservas vencidas", ExecucaoCT001aCT015::ct010);
        executar("CT011", "Registro de saída via critério FEFO", ExecucaoCT001aCT015::ct011);
        executar("CT012", "Bloqueio de saída superior ao saldo físico", ExecucaoCT001aCT015::ct012);
        executar("CT013", "Transferência atômica entre depósitos distintos", ExecucaoCT001aCT015::ct013);
        executar("CT014", "Idempotência de transferência usando a mesma chave", ExecucaoCT001aCT015::ct014);
        executar("CT015", "Cálculo do custo médio ponderado", ExecucaoCT001aCT015::ct015);
        System.out.println("\nRESUMO: " + passou + " PASSOU; " + falhou + " FALHOU; TOTAL " + (passou + falhou));
        RESULTADOS.add(0, "RESUMO: " + passou + " PASSOU; " + falhou + " FALHOU; TOTAL " + (passou + falhou));
        try { java.nio.file.Files.write(java.nio.file.Path.of("resultado-testes-CT001-CT015.txt"), RESULTADOS); }
        catch (Exception e) { throw new RuntimeException(e); }
        if (falhou > 0) System.exit(1);
    }

    @FunctionalInterface private interface Caso { void testar() throws Exception; }
    private static void executar(String id, String nome, Caso caso) {
        try { caso.testar(); passou++; String s = "PASSOU | " + id + " | " + nome; RESULTADOS.add(s); System.out.println(s); }
        catch (Throwable e) { falhou++; String s = "FALHOU | " + id + " | " + nome + " | " + e.getClass().getSimpleName() + ": " + e.getMessage(); RESULTADOS.add(s); System.out.println(s); }
    }
    private static Fixture fixture() { BancoEmMemoria b = new BancoEmMemoria(); return new Fixture(b, new ServicoEstoque(b, CLOCK)); }
    private static void base(Fixture f) { f.servico.cadastrarProduto("SKU-001", "Cabo de rede", Unidade.UN, bd("5.000")); f.servico.cadastrarDeposito("CD-01", "Centro de distribuição"); }
    private static BigDecimal bd(String n) { return new BigDecimal(n); }
    private static void igual(Object esperado, Object obtido, String msg) { if (!Objects.equals(esperado, obtido)) throw new AssertionError(msg + " | esperado=" + esperado + ", obtido=" + obtido); }
    private static void decimal(String esperado, BigDecimal obtido, String msg) { if (bd(esperado).compareTo(obtido) != 0) throw new AssertionError(msg + " | esperado=" + esperado + ", obtido=" + obtido); }
    private static <T extends Throwable> T excecao(Class<T> tipo, Runnable acao, String msg) { try { acao.run(); } catch (Throwable e) { if (tipo.isInstance(e)) return tipo.cast(e); throw new AssertionError(msg + " | exceção incorreta: " + e, e); } throw new AssertionError(msg + " | nenhuma exceção lançada"); }
    private static LocalDate validade(int dias) { return LocalDate.now(CLOCK).plusDays(dias); }
    private static Lote entrada(Fixture f, String numero, String qtd, String custo, int dias) { return f.servico.registrarEntrada("SKU-001", "CD-01", numero, bd(qtd), bd(custo), validade(dias)); }

    private static void ct001() { Fixture f=fixture(); Produto p=f.servico.cadastrarProduto("SKU-001","Cabo de rede",Unidade.UN,bd("5.000")); igual(p,f.banco.produtos.get("SKU-001"),"produto deve ser persistido"); }
    private static void ct002() { Fixture f=fixture(); IllegalArgumentException e=excecao(IllegalArgumentException.class,()->f.servico.cadastrarProduto("!@#","Teste",Unidade.UN,bd("1")),"SKU inválido deve ser rejeitado"); igual("SKU inválido",e.getMessage(),"mensagem"); }
    private static void ct003() { Fixture f=fixture(); Deposito d=f.servico.cadastrarDeposito("cd-01","Centro de distribuição"); igual("CD-01",d.codigo(),"código normalizado"); igual(d,f.banco.depositos.get("CD-01"),"depósito persistido"); }
    private static void ct004() { Fixture f=fixture(); IllegalArgumentException e=excecao(IllegalArgumentException.class,()->f.servico.cadastrarDeposito("","Depósito Inválido"),"código em branco deve ser rejeitado"); igual("Código inválido",e.getMessage(),"mensagem"); }
    private static void ct005() { Fixture f=fixture(); base(f); Lote l=entrada(f,"L001","20.000","12.50",60); igual("L001",l.numero(),"lote criado"); igual(1,f.banco.lotes.size(),"lote armazenado"); decimal("20.000",f.servico.saldoFisico("SKU-001","CD-01"),"saldo após entrada"); }
    private static void ct006() { Fixture f=fixture(); base(f); IllegalArgumentException e=excecao(IllegalArgumentException.class,()->entrada(f,"L-VENC","5.000","2.00",-1),"lote vencido deve ser rejeitado"); igual("Lote vencido",e.getMessage(),"mensagem"); }
    private static void ct007() { Fixture f=fixture(); base(f); entrada(f,"L1","10.000","2.00",60); entrada(f,"L2","15.000","2.00",90); decimal("25.000",f.servico.saldoFisico("SKU-001","CD-01"),"saldo físico"); decimal("25.000",f.servico.saldoDisponivel("SKU-001","CD-01"),"saldo disponível sem reservas"); }
    private static void ct008() { Fixture f=fixture(); base(f); entrada(f,"L1","20.000","2.00",60); Reserva r=f.servico.reservar("SKU-001","CD-01",bd("5.000"),ZonedDateTime.now(CLOCK).plusDays(1)); igual(EstadoReserva.ATIVA,r.estado(),"reserva ativa"); igual(r,f.banco.reservas.get(r.id()),"reserva persistida"); }
    private static void ct009() { Fixture f=fixture(); base(f); entrada(f,"L1","10.000","2.00",60); IllegalStateException e=excecao(IllegalStateException.class,()->f.servico.reservar("SKU-001","CD-01",bd("15.000"),ZonedDateTime.now(CLOCK).plusDays(1)),"reserva maior que saldo"); igual("Saldo disponível insuficiente",e.getMessage(),"mensagem"); }
    private static void ct010() { Fixture f=fixture(); base(f); entrada(f,"L1","10.000","2.00",60); Reserva r=f.servico.reservar("SKU-001","CD-01",bd("2.000"),ZonedDateTime.now(CLOCK).minusSeconds(1)); f.servico.expirarReservas(); igual(EstadoReserva.EXPIRADA,r.estado(),"reserva expirada"); }
    private static void ct011() { Fixture f=fixture(); base(f); Lote a=entrada(f,"LOTE-A","5.000","2.00",5); Lote b=entrada(f,"LOTE-B","5.000","2.00",25); f.servico.registrarSaida("SKU-001","CD-01",bd("5.000")); decimal("0.000",a.quantidade(),"lote com vencimento mais próximo deveria ser consumido primeiro"); decimal("5.000",b.quantidade(),"lote de validade posterior deveria permanecer"); }
    private static void ct012() { Fixture f=fixture(); base(f); entrada(f,"L1","10.000","2.00",60); IllegalStateException e=excecao(IllegalStateException.class,()->f.servico.registrarSaida("SKU-001","CD-01",bd("15.000")),"saída superior ao saldo"); igual("Saldo insuficiente",e.getMessage(),"mensagem"); decimal("10.000",f.servico.saldoFisico("SKU-001","CD-01"),"saldo não pode mudar após falha"); }
    private static void ct013() { Fixture f=fixture(); base(f); f.servico.cadastrarDeposito("CD-02","Filial"); entrada(f,"L1","10.000","2.00",60); Transferencia t=f.servico.transferir("SKU-001","CD-01","CD-02",bd("5.000"),"CHAVE-1"); decimal("5.000",f.servico.saldoFisico("SKU-001","CD-01"),"débito na origem"); decimal("5.000",f.servico.saldoFisico("SKU-001","CD-02"),"crédito no destino"); igual("CHAVE-1",t.chave(),"transferência retornada"); }
    private static void ct014() { Fixture f=fixture(); base(f); f.servico.cadastrarDeposito("CD-02","Filial"); entrada(f,"L1","10.000","2.00",60); Transferencia t1=f.servico.transferir("SKU-001","CD-01","CD-02",bd("5.000"),"CHAVE-1"); Transferencia t2=f.servico.transferir("SKU-001","CD-01","CD-02",bd("5.000"),"CHAVE-1"); igual(t1,t2,"segunda chamada retorna transferência original"); decimal("5.000",f.servico.saldoFisico("SKU-001","CD-01"),"não debitar novamente"); decimal("5.000",f.servico.saldoFisico("SKU-001","CD-02"),"não creditar novamente"); }
    private static void ct015() { Fixture f=fixture(); base(f); entrada(f,"L1","10.000","10.00",60); entrada(f,"L2","30.000","20.00",90); decimal("17.50",f.servico.custoMedio("SKU-001","CD-01"),"custo médio ponderado"); }
}
