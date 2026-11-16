package br.edu.estoque;

import static org.junit.jupiter.api.Assertions.*;

import br.edu.estoque.dominio.*;
import br.edu.estoque.web.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

class ServidorHttpTest extends BaseTeste {
  ServidorEstoque servidor;
  HttpClient client;
  ObjectMapper json = Json.mapper();
  String cookie, csrf, base;

  @BeforeEach
  void iniciar() throws Exception {
    servidor =
        new ServidorEstoque(
            banco,
            clock,
            0,
            Map.of(
                "gestor",
                "senha-gestor",
                "operador",
                "senha-operador",
                "auditor",
                "senha-auditor"));
    servidor.iniciar();
    base = "http://localhost:" + servidor.porta();
    client = HttpClient.newHttpClient();
    cookie = null;
    csrf = null;
  }

  @AfterEach
  void fechar() {
    servidor.close();
  }

  HttpResponse<String> request(String path, Object body, boolean protegido) throws Exception {
    HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(base + path));
    if (cookie != null) r.header("Cookie", cookie);
    if (body != null) {
      r.header("Content-Type", "application/json");
      if (protegido && csrf != null) r.header("X-CSRF-Token", csrf);
      r.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    }
    return client.send(r.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  Map<String, Object> objeto(String s) throws Exception {
    return json.readValue(s, new TypeReference<Map<String, Object>>() {});
  }

  void login(String usuario) throws Exception {
    HttpResponse<String> r =
        request("/api/login", Map.of("usuario", usuario, "senha", "senha-" + usuario), false);
    assertEquals(200, r.statusCode());
    cookie = r.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    csrf = objeto(r.body()).get("csrf").toString();
  }

  @Test
  void RNF006_requerSessaoParaConsultar() throws Exception {
    assertEquals(401, request("/api/produtos", null, false).statusCode());
  }

  @Test
  void RNF006_loginInvalidoNaoRevelaConta() throws Exception {
    HttpResponse<String> r =
        request("/api/login", Map.of("usuario", "inexistente", "senha", "qualquer"), false);
    assertEquals(401, r.statusCode());
    assertEquals("Usuário ou senha inválidos.", objeto(r.body()).get("mensagem"));
  }

  @Test
  void RNF006_cookieHttpOnlyECsrf() throws Exception {
    HttpResponse<String> r =
        request("/api/login", Map.of("usuario", "gestor", "senha", "senha-gestor"), false);
    String c = r.headers().firstValue("Set-Cookie").orElseThrow();
    assertTrue(c.contains("HttpOnly"));
    assertTrue(c.contains("SameSite=Strict"));
    login("gestor");
    assertEquals(
        403,
        request(
                "/api/produtos",
                Map.of("sku", "SKU-002", "nome", "Teste", "unidade", "UN", "minimo", "0"),
                false)
            .statusCode());
  }

  @Test
  void RNF006_perfilDoCorpoNaoConcedePermissao() throws Exception {
    login("operador");
    HttpResponse<String> r =
        request(
            "/api/produtos",
            Map.of(
                "sku", "SKU-002", "nome", "Teste", "unidade", "UN", "minimo", "0", "perfil",
                "GESTOR"),
            true);
    assertEquals(403, r.statusCode());
    assertEquals(1, banco.produtos.size());
  }

  @Test
  void RF005_entradaViaHttpComPrecisaoDecimal() throws Exception {
    login("operador");
    Map<String, Object> b =
        Map.of(
            "sku",
            "sku-001",
            "deposito",
            "CD-01",
            "fornecedor",
            "F1",
            "numero",
            "L1",
            "quantidade",
            "0.001",
            "custo",
            "12.50",
            "validade",
            data(30).toString(),
            "chave",
            chave());
    HttpResponse<String> a = request("/api/entradas", b, true),
        z = request("/api/entradas", b, true);
    assertEquals(200, a.statusCode());
    assertEquals(200, z.statusCode());
    assertEquals(objeto(a.body()).get("id"), objeto(z.body()).get("id"));
    decimal("0.001", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void interface_http409ParaConflito() throws Exception {
    login("gestor");
    HttpResponse<String> r =
        request(
            "/api/saidas",
            Map.of("sku", "SKU-001", "deposito", "CD-01", "quantidade", "1", "chave", chave()),
            true);
    assertEquals(409, r.statusCode());
    assertEquals("ESTQ-003", objeto(r.body()).get("codigo"));
    assertNotNull(objeto(r.body()).get("correlacao"));
  }

  @Test
  void interface_http422ParaExpiracaoPassada() throws Exception {
    entrada("L1", "10", "2", 60);
    login("operador");
    HttpResponse<String> r =
        request(
            "/api/reservas",
            Map.of(
                "sku",
                "SKU-001",
                "deposito",
                "CD-01",
                "quantidade",
                "1",
                "expiraEm",
                agora().minusSeconds(1).toString(),
                "chave",
                chave()),
            true);
    assertEquals(422, r.statusCode());
    assertTrue(banco.reservas.isEmpty());
  }

  @Test
  void RNF009_jsonInvalidoNaoExpoeDetalhes() throws Exception {
    login("gestor");
    HttpRequest r =
        HttpRequest.newBuilder(URI.create(base + "/api/produtos"))
            .header("Content-Type", "application/json")
            .header("Cookie", cookie)
            .header("X-CSRF-Token", csrf)
            .POST(HttpRequest.BodyPublishers.ofString("{invalido"))
            .build();
    HttpResponse<String> resposta = client.send(r, HttpResponse.BodyHandlers.ofString());
    assertEquals(400, resposta.statusCode());
    assertFalse(resposta.body().contains("jackson"));
    assertFalse(resposta.body().contains("java."));
    assertTrue(resposta.body().contains("correlacao"));
  }

  @Test
  void RNF009_jsonNuloRejeitadoComoValidacao() throws Exception {
    login("gestor");
    HttpRequest r =
        HttpRequest.newBuilder(URI.create(base + "/api/produtos"))
            .header("Content-Type", "application/json")
            .header("Cookie", cookie)
            .header("X-CSRF-Token", csrf)
            .POST(HttpRequest.BodyPublishers.ofString("null"))
            .build();
    assertEquals(400, client.send(r, HttpResponse.BodyHandlers.ofString()).statusCode());
  }

  @Test
  void interface_webSemDependenciasExternasEComPoliticaDeConteudo() throws Exception {
    HttpResponse<String> r = request("/", null, false);
    assertEquals(200, r.statusCode());
    assertTrue(r.body().contains("lang=\"pt-BR\""));
    assertTrue(r.body().contains("Pular para o conteúdo"));
    assertTrue(
        r.headers()
            .firstValue("Content-Security-Policy")
            .orElseThrow()
            .contains("default-src 'self'"));
    assertEquals(200, request("/app.js", null, false).statusCode());
    assertEquals(200, request("/style.css", null, false).statusCode());
    assertEquals(404, request("/pom.xml", null, false).statusCode());
  }

  @Test
  void RNF011_endpointDeSaude() throws Exception {
    HttpResponse<String> r = request("/api/saude", null, false);
    assertEquals(200, r.statusCode());
    assertEquals("disponivel", objeto(r.body()).get("estado"));
  }

  @Test
  void RNF006_logoutInvalidaSessao() throws Exception {
    login("gestor");
    assertEquals(200, request("/api/sair", Map.of(), true).statusCode());
    assertEquals(401, request("/api/produtos", null, false).statusCode());
  }

  @Test
  void RF010_reservaPodeSerAtendidaPelaInterface() throws Exception {
    entrada("L1", "10", "2", 60);
    login("operador");
    HttpResponse<String> r =
        request(
            "/api/reservas",
            Map.of(
                "sku",
                "SKU-001",
                "deposito",
                "CD-01",
                "quantidade",
                "3",
                "expiraEm",
                agora().plusHours(1).toString(),
                "chave",
                chave()),
            true);
    String id = objeto(r.body()).get("id").toString();
    assertEquals(
        200,
        request("/api/reservas/" + id + "/atender", Map.of("chave", chave()), true).statusCode());
    decimal("7", s.saldoFisico("SKU-001", "CD-01"));
  }

  @Test
  void RNF006_bloqueiaOrigemExternaMesmoComCookie() throws Exception {
    login("gestor");
    HttpRequest r =
        HttpRequest.newBuilder(URI.create(base + "/api/alertas/gerar"))
            .header("Content-Type", "application/json")
            .header("Cookie", cookie)
            .header("X-CSRF-Token", csrf)
            .header("Origin", "https://outro.exemplo")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();
    assertEquals(403, client.send(r, HttpResponse.BodyHandlers.ofString()).statusCode());
  }

  @Test
  void RNF006_limitaTentativasDeLogin() throws Exception {
    for (int i = 0; i < 5; i++)
      assertEquals(
          401,
          request("/api/login", Map.of("usuario", "gestor", "senha", "errada"), false)
              .statusCode());
    assertEquals(
        429,
        request("/api/login", Map.of("usuario", "gestor", "senha", "senha-gestor"), false)
            .statusCode());
  }
}
