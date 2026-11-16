package br.edu.estoque;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

public final class RelogioAjustavel extends Clock {
  private final AtomicReference<Instant> instante;
  private final ZoneId zona;

  public RelogioAjustavel(Instant instante) {
    this(new AtomicReference<>(instante), ZoneId.of("America/Sao_Paulo"));
  }

  private RelogioAjustavel(AtomicReference<Instant> instante, ZoneId zona) {
    this.instante = instante;
    this.zona = zona;
  }

  public void avancar(Duration intervalo) {
    instante.updateAndGet(i -> i.plus(intervalo));
  }

  public ZoneId getZone() {
    return zona;
  }

  public Clock withZone(ZoneId zona) {
    return new RelogioAjustavel(instante, zona);
  }

  public Instant instant() {
    return instante.get();
  }
}
