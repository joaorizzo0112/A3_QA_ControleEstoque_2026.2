package br.edu.estoque.dominio;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

public final class Reserva implements Serializable {
  private static final long serialVersionUID = 1L;
  private final UUID id;
  private final String sku, deposito;
  private final BigDecimal quantidade;
  private final ZonedDateTime expiraEm;
  private EstadoReserva estado = EstadoReserva.ATIVA;

  public Reserva(String sku, String deposito, BigDecimal quantidade, ZonedDateTime expiraEm) {
    id = UUID.randomUUID();
    this.sku = Validacao.sku(sku);
    this.deposito = Validacao.codigo(deposito);
    this.quantidade = Validacao.quantidade(quantidade, false);
    if (expiraEm == null) throw new IllegalArgumentException("Expiração inválida");
    Validacao.data(expiraEm.toLocalDate());
    this.expiraEm = expiraEm;
  }

  private Reserva(Reserva r) {
    id = r.id;
    sku = r.sku;
    deposito = r.deposito;
    quantidade = r.quantidade;
    expiraEm = r.expiraEm;
    estado = r.estado;
  }

  public Reserva copia() {
    return new Reserva(this);
  }

  public UUID id() {
    return id;
  }

  public String sku() {
    return sku;
  }

  public String deposito() {
    return deposito;
  }

  public BigDecimal quantidade() {
    return quantidade;
  }

  public ZonedDateTime expiraEm() {
    return expiraEm;
  }

  public EstadoReserva estado() {
    return estado;
  }

  public void estado(EstadoReserva novo) {
    if (estado != EstadoReserva.ATIVA || novo == null || novo == EstadoReserva.ATIVA)
      throw new IllegalStateException("Reserva não está ativa");
    estado = novo;
  }
}
