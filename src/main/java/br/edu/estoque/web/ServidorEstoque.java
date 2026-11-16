package br.edu.estoque.web;

import br.edu.estoque.dominio.*;
import br.edu.estoque.repositorio.BancoEmMemoria;
import br.edu.estoque.servico.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Servidor acadêmico local. O contexto de autorização vem da sessão, nunca do corpo da requisição.
 */
public final class ServidorEstoque implements AutoCloseable {
  private record Conta(Ator ator, byte[] sal, byte[] hash) {}

  private record Sessao(Ator ator, String csrf, Instant expiraEm) {}

  private final BancoEmMemoria banco;
  private final Clock clock;
  private final HttpServer http;
  private final ObjectMapper json = Json.mapper();
  private final Map<String, Conta> contas = new HashMap<>();
  private final Map<String, Sessao> sessoes = new ConcurrentHashMap<>();
  private final ExecutorService executor = Executors.newFixedThreadPool(8);
  private final ScheduledExecutorService processo =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "processo-estoque");
            t.setDaemon(true);
            return t;
          });
  private final Map<String, List<Instant>> tentativas = new ConcurrentHashMap<>();
  private final Instant inicio;

  public ServidorEstoque(BancoEmMemoria banco, Clock clock, int porta, Map<String, String> senhas)
      throws IOException {
    this.banco = banco;
    this.clock = clock;
    inicio = clock.instant();
    cadastrarConta("gestor", new Ator("gestor", Perfil.GESTOR, Set.of("*")), senhas.get("gestor"));
    cadastrarConta(
        "operador",
        new Ator("operador", Perfil.OPERADOR, Set.of("CD-01", "LOJA-01")),
        senhas.get("operador"));
    cadastrarConta(
        "auditor", new Ator("auditor", Perfil.AUDITOR, Set.of("*")), senhas.get("auditor"));
    http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), porta), 64);
    http.setExecutor(executor);
    http.createContext("/", this::tratar);
  }

  private void cadastrarConta(String nome, Ator ator, String senha) {
    if (senha == null || senha.length() < 8)
      throw new IllegalArgumentException("Configure senhas com pelo menos oito caracteres");
    byte[] sal = new byte[16];
    new SecureRandom().nextBytes(sal);
    contas.put(nome, new Conta(ator, sal, hash(senha, sal)));
  }

  private byte[] hash(String senha, byte[] sal) {
    PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), sal, 120_000, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Não foi possível preparar a autenticação", e);
    } finally {
      spec.clearPassword();
    }
  }

  public void iniciar() {
    http.start();
    processo.scheduleWithFixedDelay(
        () -> {
          try {
            new ServicoEstoque(banco, clock, new Ator("processo", Perfil.PROCESSO, Set.of("*")))
                .gerarAlertas();
          } catch (RuntimeException e) {
            System.err.println("Processamento adiado. Atendimento: " + Erros.correlacao(e));
          }
          sessoes.entrySet().removeIf(e -> !e.getValue().expiraEm().isAfter(clock.instant()));
        },
        0,
        60,
        TimeUnit.SECONDS);
  }

  public int porta() {
    return http.getAddress().getPort();
  }

  public void close() {
    processo.shutdownNow();
    http.stop(0);
    executor.shutdownNow();
  }

  private void cabecalhos(HttpExchange x) {
    Headers h = x.getResponseHeaders();
    h.set("X-Content-Type-Options", "nosniff");
    h.set("X-Frame-Options", "DENY");
    h.set(
        "Content-Security-Policy",
        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src"
            + " 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
    h.set("Referrer-Policy", "no-referrer");
    h.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
    h.set("Cross-Origin-Opener-Policy", "same-origin");
    h.set("Cache-Control", "no-store");
  }

  private void responder(HttpExchange x, int status, Object corpo) throws IOException {
    byte[] bytes = json.writeValueAsBytes(corpo);
    x.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    x.sendResponseHeaders(status, bytes.length);
    x.getResponseBody().write(bytes);
  }

  private Map<String, Object> corpo(HttpExchange x) throws IOException {
    String tipo =
        Objects.toString(x.getRequestHeaders().getFirst("Content-Type"), "").split(";", 2)[0];
    if (!tipo.equalsIgnoreCase("application/json"))
      throw new IllegalArgumentException("Envie os dados no formato JSON");
    byte[] bytes = x.getRequestBody().readNBytes(65_537);
    if (bytes.length > 65_536) throw new IllegalArgumentException("Requisição muito grande");
    try {
      Map<String, Object> dados =
          json.readValue(bytes, new TypeReference<Map<String, Object>>() {});
      if (dados == null)
        throw new IllegalArgumentException("Dados inválidos. Confira os campos informados.");
      return dados;
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Dados inválidos. Confira os campos informados.");
    }
  }

  private String str(Map<String, ?> b, String campo) {
    Object v = b.get(campo);
    if (v == null) return null;
    if (v instanceof String || v instanceof Number) return v.toString();
    throw new IllegalArgumentException("Campo inválido: " + campo);
  }

  private java.math.BigDecimal decimal(Map<String, ?> b, String campo) {
    try {
      return new java.math.BigDecimal(Objects.toString(str(b, campo), ""));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Número inválido: " + campo);
    }
  }

  private UUID uuid(String texto) {
    try {
      return UUID.fromString(Objects.toString(texto, ""));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Identificador inválido");
    }
  }

  private LocalDate data(String s) {
    try {
      return LocalDate.parse(Objects.toString(s, ""));
    } catch (DateTimeException e) {
      throw new IllegalArgumentException("Data inválida");
    }
  }

  private ZonedDateTime instante(String s) {
    try {
      return ZonedDateTime.parse(Objects.toString(s, ""));
    } catch (DateTimeException e) {
      try {
        return LocalDateTime.parse(Objects.toString(s, "")).atZone(ZoneId.of("America/Sao_Paulo"));
      } catch (DateTimeException outra) {
        throw new IllegalArgumentException("Data e hora inválidas");
      }
    }
  }

  private <E extends Enum<E>> E enumeracao(Class<E> classe, String s) {
    try {
      return Enum.valueOf(classe, Objects.toString(s, "").toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Opção inválida");
    }
  }

  private Map<String, String> parametros(HttpExchange x) {
    Map<String, String> r = new HashMap<>();
    String q = x.getRequestURI().getRawQuery();
    if (q == null) return r;
    for (String parte : q.split("&")) {
      String[] v = parte.split("=", 2);
      r.put(
          URLDecoder.decode(v[0], StandardCharsets.UTF_8),
          v.length == 1 ? "" : URLDecoder.decode(v[1], StandardCharsets.UTF_8));
    }
    return r;
  }

  private String cookie(HttpExchange x) {
    for (String h : x.getRequestHeaders().getOrDefault("Cookie", List.of()))
      for (String c : h.split(";")) {
        String[] p = c.trim().split("=", 2);
        if (p.length == 2 && p[0].equals("ESTOQUE_SESSAO")) return p[1];
      }
    return null;
  }

  private void origem(HttpExchange x) {
    String o = x.getRequestHeaders().getFirst("Origin");
    String host = x.getRequestHeaders().getFirst("Host");
    if (o != null && !o.equals("http://" + host))
      throw new SecurityException("Operação não autorizada");
  }

  private Sessao sessao(HttpExchange x) {
    String token = cookie(x);
    Sessao s = token == null ? null : sessoes.get(token);
    return s != null && s.expiraEm().isAfter(clock.instant()) ? s : null;
  }

  private Object info(Sessao s) {
    return Map.of("usuario", s.ator(), "csrf", s.csrf());
  }

  private void login(HttpExchange x) throws IOException {
    origem(x);
    Map<String, Object> b = corpo(x);
    String usuario = Objects.toString(str(b, "usuario"), "").trim().toLowerCase(Locale.ROOT),
        senha = Objects.toString(str(b, "senha"), "");
    String ip = x.getRemoteAddress().getAddress().getHostAddress();
    List<Instant> falhas =
        tentativas.computeIfAbsent(ip, k -> Collections.synchronizedList(new ArrayList<>()));
    synchronized (falhas) {
      falhas.removeIf(i -> i.isBefore(clock.instant().minusSeconds(60)));
      if (falhas.size() >= 5) {
        responder(x, 429, Map.of("mensagem", "Aguarde um minuto antes de tentar novamente."));
        return;
      }
    }
    Conta conta = contas.get(usuario);
    byte[] sal = conta == null ? new byte[16] : conta.sal();
    byte[] candidato = hash(senha, sal);
    if (conta == null || !MessageDigest.isEqual(candidato, conta.hash())) {
      falhas.add(clock.instant());
      responder(x, 401, Map.of("mensagem", "Usuário ou senha inválidos."));
      return;
    }
    falhas.clear();
    String token = UUID.randomUUID().toString();
    Sessao s =
        new Sessao(conta.ator(), UUID.randomUUID().toString(), clock.instant().plusSeconds(1800));
    sessoes.put(token, s);
    x.getResponseHeaders()
        .add(
            "Set-Cookie",
            "ESTOQUE_SESSAO=" + token + "; HttpOnly; SameSite=Strict; Path=/; Max-Age=1800");
    responder(x, 200, info(s));
  }

  private void tratar(HttpExchange x) throws IOException {
    cabecalhos(x);
    try {
      String path = x.getRequestURI().getPath(), metodo = x.getRequestMethod();
      if (path.equals("/api/saude") && metodo.equals("GET")) {
        responder(
            x,
            200,
            Map.of(
                "estado",
                "disponivel",
                "inicio",
                inicio,
                "segundosAtivo",
                Duration.between(inicio, clock.instant()).getSeconds()));
        return;
      }
      if (path.equals("/api/login") && metodo.equals("POST")) {
        login(x);
        return;
      }
      if (!path.startsWith("/api/")) {
        estatico(x, path, metodo);
        return;
      }
      Sessao sessao = sessao(x);
      if (sessao == null) {
        responder(x, 401, Map.of("mensagem", "Entre para continuar."));
        return;
      }
      if (!metodo.equals("GET") && !metodo.equals("POST")) {
        responder(x, 405, Map.of("mensagem", "Método não permitido."));
        return;
      }
      Map<String, Object> b = Map.of();
      if (metodo.equals("POST")) {
        origem(x);
        if (!sessao.csrf().equals(x.getRequestHeaders().getFirst("X-CSRF-Token")))
          throw new SecurityException("Operação não autorizada");
        b = corpo(x);
      }
      if (path.equals("/api/sessao") && metodo.equals("GET")) {
        responder(x, 200, info(sessao));
        return;
      }
      if (path.equals("/api/sair") && metodo.equals("POST")) {
        sessoes.remove(cookie(x));
        x.getResponseHeaders()
            .add("Set-Cookie", "ESTOQUE_SESSAO=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0");
        responder(x, 200, Map.of("ok", true));
        return;
      }
      ServicoEstoque s = new ServicoEstoque(banco, clock, sessao.ator());
      Map<String, String> q = parametros(x);
      Object resultado;
      if (metodo.equals("GET")) {
        resultado =
            switch (path) {
              case "/api/produtos" -> s.produtos();
              case "/api/depositos" -> s.depositos();
              case "/api/reservas" -> s.reservas();
              case "/api/inventarios" -> s.inventarios();
              case "/api/alertas" -> s.alertas();
              case "/api/lotes" -> s.lotes(q.get("sku"), q.get("deposito"));
              case "/api/posicao" -> s.posicao(q.get("sku"), q.get("deposito"));
              case "/api/posicoes" -> {
                List<Posicao> ps = new ArrayList<>();
                for (Produto p : s.produtos())
                  for (Deposito d : s.depositos()) ps.add(s.posicao(p.sku(), d.codigo()));
                yield ps;
              }
              case "/api/movimentos" ->
                  s.movimentos(
                      q.get("sku"),
                      q.get("deposito"),
                      q.get("tipo") == null || q.get("tipo").isBlank()
                          ? null
                          : enumeracao(TipoMovimento.class, q.get("tipo")),
                      instante(q.get("inicio")),
                      instante(q.get("fim")));
              case "/api/relatorio" ->
                  s.relatorio(
                      q.get("sku"),
                      q.get("deposito"),
                      instante(q.get("inicio")),
                      instante(q.get("fim")));
              case "/api/eventos" -> s.eventos(instante(q.get("inicio")), instante(q.get("fim")));
              default -> {
                if (path.startsWith("/api/produtos/") && path.endsWith("/historico"))
                  yield s.historicoProduto(path.split("/")[3]);
                throw new NoSuchElementException("Página não encontrada");
              }
            };
      } else {
        String chave = str(b, "chave");
        resultado =
            switch (path) {
              case "/api/produtos" ->
                  s.cadastrarProduto(
                      str(b, "sku"),
                      str(b, "nome"),
                      enumeracao(Unidade.class, str(b, "unidade")),
                      decimal(b, "minimo"));
              case "/api/depositos" -> s.cadastrarDeposito(str(b, "codigo"), str(b, "nome"));
              case "/api/entradas" ->
                  s.registrarEntrada(
                      str(b, "sku"),
                      str(b, "deposito"),
                      str(b, "fornecedor"),
                      str(b, "numero"),
                      decimal(b, "quantidade"),
                      decimal(b, "custo"),
                      data(str(b, "validade")),
                      chave);
              case "/api/saidas" ->
                  s.registrarSaida(
                      str(b, "sku"), str(b, "deposito"), decimal(b, "quantidade"), chave);
              case "/api/reservas" ->
                  s.reservar(
                      str(b, "sku"),
                      str(b, "deposito"),
                      decimal(b, "quantidade"),
                      instante(str(b, "expiraEm")),
                      chave);
              case "/api/transferencias" -> {
                Object obj = b.get("itens");
                if (!(obj instanceof List<?> lista))
                  throw new IllegalArgumentException("Informe os itens da transferência");
                List<ItemTransferencia> itens = new ArrayList<>();
                for (Object item : lista) {
                  if (!(item instanceof Map<?, ?> m))
                    throw new IllegalArgumentException("Item inválido");
                  Map<String, Object> v = new HashMap<>();
                  m.forEach((k, val) -> v.put(k.toString(), val));
                  itens.add(new ItemTransferencia(str(v, "sku"), decimal(v, "quantidade")));
                }
                yield s.transferirItens(str(b, "origem"), str(b, "destino"), itens, chave);
              }
              case "/api/ajustes" ->
                  s.ajustarLote(
                      uuid(str(b, "loteId")), decimal(b, "variacao"), str(b, "motivo"), chave);
              case "/api/descartes" ->
                  s.descartar(
                      uuid(str(b, "loteId")), decimal(b, "quantidade"), str(b, "motivo"), chave);
              case "/api/inventarios" -> s.abrirInventario(str(b, "deposito"), chave);
              case "/api/alertas/gerar" -> s.gerarAlertas();
              default -> acaoDetalhe(s, path, b, chave);
            };
      }
      responder(x, 200, resultado == null ? Map.of("ok", true) : resultado);
    } catch (RuntimeException e) {
      String correlacao = Erros.correlacao(e);
      responder(
          x,
          Erros.status(e),
          Map.of(
              "codigo",
              Erros.codigo(e),
              "mensagem",
              Erros.mensagemPublica(e),
              "correlacao",
              correlacao));
    } finally {
      x.close();
    }
  }

  private Object acaoDetalhe(ServicoEstoque s, String path, Map<String, Object> b, String chave) {
    String[] p = path.split("/");
    if (p.length != 5) throw new NoSuchElementException("Operação não encontrada");
    String grupo = p[2], id = p[3], acao = p[4];
    if (grupo.equals("produtos"))
      return switch (acao) {
        case "alterar" ->
            s.alterarProduto(
                id,
                str(b, "nome"),
                enumeracao(Unidade.class, str(b, "unidade")),
                decimal(b, "minimo"));
        case "inativar" -> s.inativarProduto(id);
        default -> throw new NoSuchElementException("Operação não encontrada");
      };
    if (grupo.equals("depositos") && acao.equals("inativar")) return s.inativarDeposito(id);
    if (grupo.equals("reservas"))
      return switch (acao) {
        case "atender" -> s.atenderReserva(uuid(id), chave);
        case "cancelar" -> s.cancelarReserva(uuid(id));
        default -> throw new NoSuchElementException("Operação não encontrada");
      };
    if (grupo.equals("inventarios"))
      return switch (acao) {
        case "iniciar" -> s.iniciarInventario(uuid(id));
        case "contar" ->
            s.registrarContagem(uuid(id), uuid(str(b, "loteId")), decimal(b, "quantidade"));
        case "revisar" -> s.revisarInventario(uuid(id));
        case "cancelar" -> s.cancelarInventario(uuid(id));
        case "concluir" -> {
          Object obj = b.get("aprovados");
          if (!(obj instanceof List<?> ids))
            throw new IllegalArgumentException("Informe os itens aprovados");
          Set<UUID> aprovados = new HashSet<>();
          for (Object v : ids) aprovados.add(uuid(v.toString()));
          yield s.concluirInventario(uuid(id), aprovados, str(b, "motivo"));
        }
        default -> throw new NoSuchElementException("Operação não encontrada");
      };
    throw new NoSuchElementException("Operação não encontrada");
  }

  private void estatico(HttpExchange x, String path, String metodo) throws IOException {
    if (!metodo.equals("GET")) {
      responder(x, 405, Map.of("mensagem", "Método não permitido."));
      return;
    }
    String recurso =
        switch (path) {
          case "/", "/index.html" -> "index.html";
          case "/app.js" -> "app.js";
          case "/style.css" -> "style.css";
          case "/favicon.svg" -> "favicon.svg";
          default -> null;
        };
    if (recurso == null) {
      responder(x, 404, Map.of("mensagem", "Página não encontrada."));
      return;
    }
    try (InputStream in = ServidorEstoque.class.getResourceAsStream("/web/" + recurso)) {
      if (in == null) {
        responder(x, 404, Map.of("mensagem", "Página não encontrada."));
        return;
      }
      byte[] bytes = in.readAllBytes();
      String tipo =
          recurso.endsWith(".js")
              ? "text/javascript"
              : recurso.endsWith(".css")
                  ? "text/css"
                  : recurso.endsWith(".svg") ? "image/svg+xml" : "text/html";
      x.getResponseHeaders().set("Content-Type", tipo + "; charset=utf-8");
      x.sendResponseHeaders(200, bytes.length);
      x.getResponseBody().write(bytes);
    }
  }
}
