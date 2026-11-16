/* Fluxos reais de navegador. Dependências de desenvolvimento; a aplicação usa apenas Java. */
const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const { spawn } = require("node:child_process");
const net = require("node:net");
const { createRequire } = require("node:module");
const carregar = process.env.ESTOQUE_MODULOS_TESTE
  ? createRequire(path.join(process.env.ESTOQUE_MODULOS_TESTE, "package.json"))
  : require;
const playwright = carregar("playwright");
const axeSource = fs.readFileSync(
  process.env.ESTOQUE_AXE_TESTE || require.resolve("axe-core/axe.min.js"),
  "utf8",
);
const evidencias = path.resolve("target/evidencias");
fs.mkdirSync(evidencias, { recursive: true });
const nomeBrowser = process.env.BROWSER || "chromium";
const resultados = {
  navegador: nomeBrowser,
  versao: null,
  fluxos: [],
  acessibilidade: [],
};
let processo, browser;
const dormir = (ms) => new Promise((r) => setTimeout(r, ms));
async function portaLivre() {
  const s = net.createServer();
  await new Promise((r) => s.listen(0, "127.0.0.1", r));
  const p = s.address().port;
  await new Promise((r) => s.close(r));
  return p;
}
async function rodar() {
  let url = process.env.ESTOQUE_TEST_URL;
  if (!url) {
    const porta = await portaLivre();
    processo = spawn(
      process.env.JAVA_EXECUTAVEL || "java",
      [
        "-jar",
        "target/controle-estoque.jar",
        "--memoria",
        "--porta",
        String(porta),
      ],
      { stdio: "pipe" },
    );
    processo.stderr.on("data", (b) => process.stderr.write(b));
    url = "http://127.0.0.1:" + porta;
    for (let n = 0; n < 100; n++) {
      try {
        if ((await fetch(url + "/api/saude")).ok) break;
      } catch {}
      await dormir(100);
    }
  }
  const opcoes = { headless: true };
  if (
    nomeBrowser === "chromium" &&
    process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH
  )
    opcoes.executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH;
  if (nomeBrowser === "chromium") opcoes.args = ["--no-sandbox"];
  if (process.env.PLAYWRIGHT_CHANNEL)
    opcoes.channel = process.env.PLAYWRIGHT_CHANNEL;
  browser = await playwright[nomeBrowser].launch(opcoes);
  resultados.versao = browser.version();
  const contexto = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    locale: "pt-BR",
  });
  const page = await contexto.newPage(),
    erros = [];
  page.on("pageerror", (e) => erros.push(e.message));
  await page.goto(url);
  async function acessibilidade(tela) {
    await page.evaluate(axeSource);
    const r = await page.evaluate(async () =>
      axe.run(document, {
        runOnly: {
          type: "tag",
          values: ["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"],
        },
      }),
    );
    resultados.acessibilidade.push({
      tela,
      violacoes: r.violations.map((v) => ({
        id: v.id,
        impacto: v.impact,
        descricao: v.description,
        alvos: v.nodes.map((n) => n.target),
      })),
    });
    assert.equal(
      r.violations.length,
      0,
      "Acessibilidade: " +
        tela +
        " " +
        JSON.stringify(
          r.violations.map((v) => ({
            id: v.id,
            alvos: v.nodes.map((n) => n.target),
          })),
        ),
    );
  }
  await acessibilidade("login");
  async function login(usuario, senha) {
    await page.locator("#form-login [name=usuario]").fill(usuario);
    await page.locator("#form-login [name=senha]").fill(senha);
    await page.locator("#form-login button").click();
    await page.locator("#aplicacao").waitFor({ state: "visible" });
    await page.waitForFunction(
      () =>
        !document.body.hasAttribute("aria-busy") &&
        document.querySelector("#tabela-posicoes table"),
    );
  }
  async function enviar(id) {
    const b = page.locator("#" + id + " button[type=submit]");
    await b.click();
    await page.waitForFunction(
      (id) =>
        !document.querySelector("#" + id + " button[type=submit]").disabled,
      id,
    );
    assert.equal(
      await page
        .locator("#aviso")
        .evaluate((n) => n.classList.contains("erro")),
      false,
      await page.locator("#aviso").innerText(),
    );
  }
  async function nav(id) {
    await page.locator('[data-pagina="' + id + '"]').click();
  }
  async function inventario(acao) {
    const seletor = '[data-inventario-acao="' + acao + '"]';
    await page.locator(seletor).click();
    await page.waitForFunction(
      (s) => !document.querySelector(s).disabled,
      seletor,
    );
    assert.equal(
      await page
        .locator("#aviso")
        .evaluate((n) => n.classList.contains("erro")),
      false,
      await page.locator("#aviso").innerText(),
    );
  }
  await login("gestor", process.env.ESTOQUE_SENHA_GESTOR || "a3-gestor-2026");
  resultados.fluxos.push("login gestor e posição inicial");
  await nav("produtos");
  await page.locator("#form-produto [name=sku]").fill("QA-003");
  await page
    .locator("#form-produto [name=nome]")
    .fill("Produto de conferência");
  await enviar("form-produto");
  assert.match(await page.locator("#tabela-produtos").innerText(), /QA-003/);
  resultados.fluxos.push("cadastro de produto");
  await nav("movimentar");
  const mov = page.locator("#form-movimento");
  await mov.locator("[name=sku]").selectOption("QA-003");
  await mov.locator("[name=deposito]").selectOption("CD-01");
  await mov.locator("[name=numero]").fill("QA-LOTE");
  await mov.locator("[name=custo]").fill("4,00");
  await mov.locator("[name=quantidade]").fill("10,000");
  const validade = new Date();
  validade.setUTCDate(validade.getUTCDate() + 90);
  await mov
    .locator("[name=validade]")
    .fill(validade.toISOString().slice(0, 10));
  await enviar("form-movimento");
  resultados.fluxos.push("entrada com decimal pt-BR");
  await nav("reservas");
  const res = page.locator("#form-reserva");
  await res.locator("[name=sku]").selectOption("QA-003");
  await res.locator("[name=deposito]").selectOption("CD-01");
  await res.locator("[name=quantidade]").fill("3,000");
  const expira = new Date();
  expira.setUTCDate(expira.getUTCDate() + 1);
  await res
    .locator("[name=expiraEm]")
    .fill(expira.toISOString().slice(0, 10) + "T18:00");
  await enviar("form-reserva");
  const atender = page
    .locator("#tabela-reservas tr")
    .filter({ hasText: "QA-003" })
    .getByRole("button", { name: "Atender", exact: true });
  await atender.click();
  await page.waitForFunction(() =>
    document.querySelector("#tabela-reservas").textContent.includes("ATENDIDA"),
  );
  resultados.fluxos.push("criação e atendimento de reserva");
  await nav("transferir");
  await page.locator("#form-transferencia [name=origem]").selectOption("CD-01");
  await page
    .locator("#form-transferencia [name=destino]")
    .selectOption("LOJA-01");
  await page.locator("#itens-transferencia select").selectOption("QA-003");
  await page.locator("#itens-transferencia input").fill("2,000");
  await enviar("form-transferencia");
  resultados.fluxos.push("transferência");
  await nav("inventarios");
  await page.locator("#form-inventario select").selectOption("CD-01");
  await enviar("form-inventario");
  await inventario("iniciar");
  const opcao = await page
    .locator("#lote-contagem option")
    .filter({ hasText: "QA-LOTE" })
    .getAttribute("value");
  await page.locator("#lote-contagem").selectOption(opcao);
  await page.locator("#form-contagem [name=quantidade]").fill("4,000");
  await enviar("form-contagem");
  await inventario("revisar");
  await page
    .locator("#form-conclusao textarea")
    .fill("Conferência física aprovada no teste");
  await enviar("form-conclusao");
  assert.match(
    await page.locator("#inventario-resumo").innerText(),
    /CONCLUIDO/,
  );
  resultados.fluxos.push("inventário completo com ajuste aprovado");
  await nav("relatorios");
  await page.locator("#form-relatorio [name=sku]").selectOption("QA-003");
  await enviar("form-relatorio");
  assert.match(await page.locator("#tabela-relatorio").innerText(), /QA-003/);
  const download = page.waitForEvent("download");
  await page.locator("#exportar-csv").click();
  const arquivo = await download;
  await arquivo.saveAs(path.join(evidencias, "relatorio-navegador.csv"));
  resultados.fluxos.push("relatório e exportação CSV");
  for (const pagina of [
    "inicio",
    "produtos",
    "movimentar",
    "reservas",
    "transferir",
    "inventarios",
    "alertas",
    "relatorios",
    "auditoria",
  ]) {
    await nav(pagina);
    if (pagina === "auditoria")
      await page.locator("#tabela-auditoria table").waitFor();
    await acessibilidade(pagina);
  }
  await nav("inicio");
  await page.screenshot({
    path: path.join(evidencias, "interface-desktop.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  for (const pagina of [
    "inicio",
    "produtos",
    "movimentar",
    "reservas",
    "inventarios",
  ]) {
    await nav(pagina);
    assert.equal(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
      true,
      "Conteúdo excede tela móvel em " + pagina,
    );
    await acessibilidade(pagina + "-mobile");
  }
  await nav("inicio");
  await page.locator("#tabela-posicoes").focus();
  await page.keyboard.press("ArrowRight");
  await page.waitForFunction(
    () => document.querySelector("#tabela-posicoes").scrollLeft > 0,
  );
  resultados.fluxos.push("rolagem horizontal da tabela pelo teclado");
  await page.screenshot({
    path: path.join(evidencias, "interface-mobile.png"),
    fullPage: true,
  });
  resultados.fluxos.push("layout móvel sem rolagem horizontal da página");
  await page.locator("#sair").click();
  await page.locator("#login").waitFor({ state: "visible" });
  await login(
    "auditor",
    process.env.ESTOQUE_SENHA_AUDITOR || "a3-auditor-2026",
  );
  assert.equal(
    await page.locator("[data-pagina=inventarios]").isVisible(),
    false,
  );
  await nav("movimentar");
  assert.equal(await page.locator("#form-movimento").isVisible(), false);
  resultados.fluxos.push("auditor com interface de consulta");
  assert.deepEqual(erros, [], "Erros JavaScript no navegador");
  resultados.sucesso = true;
}
rodar()
  .catch((e) => {
    resultados.sucesso = false;
    resultados.erro = e.message;
    console.error(e);
    process.exitCode = 1;
  })
  .finally(async () => {
    if (browser) await browser.close();
    if (processo) processo.kill();
    fs.writeFileSync(
      path.join(evidencias, "interface-" + nomeBrowser + ".json"),
      JSON.stringify(resultados, null, 2),
    );
    console.log(JSON.stringify(resultados, null, 2));
  });
