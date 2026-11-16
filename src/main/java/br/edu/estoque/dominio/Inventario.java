package br.edu.estoque.dominio;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;

public final class Inventario implements Serializable {
  private static final long serialVersionUID = 1L;
  private final UUID id;
  private final String deposito;
  private final ZonedDateTime abertura;
  private final Map<UUID, BigDecimal> referencia;
  private final Map<UUID, BigDecimal> contagens = new LinkedHashMap<>();
  private final List<RegistroContagem> historico = new ArrayList<>();
  private final List<UUID> ajustes = new ArrayList<>();
  private EstadoInventario estado = EstadoInventario.ABERTO;
  private String assinaturaConclusao;

  public Inventario(String deposito, ZonedDateTime abertura, Map<UUID, BigDecimal> referencia) {
    id = UUID.randomUUID();
    this.deposito = deposito;
    this.abertura = abertura;
    this.referencia = new LinkedHashMap<>(referencia);
  }

  private Inventario(Inventario i) {
    id = i.id;
    deposito = i.deposito;
    abertura = i.abertura;
    referencia = new LinkedHashMap<>(i.referencia);
    contagens.putAll(i.contagens);
    historico.addAll(i.historico);
    ajustes.addAll(i.ajustes);
    estado = i.estado;
    assinaturaConclusao = i.assinaturaConclusao;
  }

  public Inventario copia() {
    return new Inventario(this);
  }

  public UUID id() {
    return id;
  }

  public String deposito() {
    return deposito;
  }

  public ZonedDateTime abertura() {
    return abertura;
  }

  public EstadoInventario estado() {
    return estado;
  }

  public Map<UUID, BigDecimal> referencia() {
    return Map.copyOf(referencia);
  }

  public Map<UUID, BigDecimal> contagens() {
    return Map.copyOf(contagens);
  }

  public List<RegistroContagem> historico() {
    return List.copyOf(historico);
  }

  public List<UUID> ajustes() {
    return List.copyOf(ajustes);
  }

  public String assinaturaConclusao() {
    return assinaturaConclusao;
  }

  public void iniciar() {
    exigir(EstadoInventario.ABERTO);
    estado = EstadoInventario.EM_CONTAGEM;
  }

  public void contar(RegistroContagem c) {
    exigir(EstadoInventario.EM_CONTAGEM);
    contagens.put(c.loteId(), c.quantidade());
    historico.add(c);
  }

  public void revisar() {
    exigir(EstadoInventario.EM_CONTAGEM);
    if (contagens.isEmpty()) throw new IllegalStateException("Inventário sem contagens");
    estado = EstadoInventario.EM_REVISAO;
  }

  public void concluir(String assinatura, List<UUID> ajustes) {
    exigir(EstadoInventario.EM_REVISAO);
    this.assinaturaConclusao = assinatura;
    this.ajustes.addAll(ajustes);
    estado = EstadoInventario.CONCLUIDO;
  }

  public void cancelar() {
    if (estado == EstadoInventario.CONCLUIDO || estado == EstadoInventario.CANCELADO)
      throw new IllegalStateException("Inventário encerrado");
    estado = EstadoInventario.CANCELADO;
  }

  private void exigir(EstadoInventario e) {
    if (estado != e) throw new IllegalStateException("Estado de inventário inválido");
  }
}
