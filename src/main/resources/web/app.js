"use strict";
const $ = (id) => document.getElementById(id);
const estado = {
  sessao: null,
  produtos: [],
  depositos: [],
  lotes: [],
  reservas: [],
  inventarios: [],
  alertas: [],
  posicoes: [],
  movimentos: [],
  pagina: "inicio",
  inventario: null,
  relatorio: null,
};
const quant = new Intl.NumberFormat("pt-BR", {
  minimumFractionDigits: 3,
  maximumFractionDigits: 3,
});
const moeda = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});
const dataHora = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeStyle: "short",
  timeZone: "America/Sao_Paulo",
});
const rotulos = {
  inicio: "Visão geral",
  produtos: "Produtos e depósitos",
  movimentar: "Lotes e movimentos",
  reservas: "Reservas",
  transferir: "Transferências",
  inventarios: "Inventário",
  alertas: "Alertas",
  relatorios: "Relatórios",
  auditoria: "Auditoria",
};
const pendencias = new Map();
const novoId = () => crypto.randomUUID();
const gestor = () => estado.sessao?.usuario.perfil === "GESTOR";
const operar = () =>
  ["GESTOR", "OPERADOR"].includes(estado.sessao?.usuario.perfil);
const gestao = () =>
  ["GESTOR", "AUDITOR"].includes(estado.sessao?.usuario.perfil);
function el(tipo, texto, classe) {
  const n = document.createElement(tipo);
  if (texto !== undefined) n.textContent = texto;
  if (classe) n.className = classe;
  return n;
}
function numero(texto) {
  return String(texto ?? "")
    .trim()
    .replace(",", ".");
}
function diaHoje() {
  const p = new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Sao_Paulo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());
  const v = (t) => p.find((x) => x.type === t).value;
  return `${v("year")}-${v("month")}-${v("day")}`;
}
function formatarData(iso) {
  return iso ? dataHora.format(new Date(iso)) : "—";
}
function aviso(mensagem, erro = false, correlacao) {
  const n = $("aviso");
  n.replaceChildren(el("span", mensagem));
  if (correlacao) n.append(el("small", `Atendimento: ${correlacao}`));
  n.classList.toggle("erro", erro);
  n.hidden = false;
  if (erro) n.focus();
  clearTimeout(aviso.timer);
  aviso.timer = setTimeout(() => (n.hidden = true), erro ? 20000 : 7000);
}
async function api(path, body) {
  const headers = {};
  const op = { headers, credentials: "same-origin" };
  if (body !== undefined) {
    op.method = "POST";
    headers["Content-Type"] = "application/json";
    if (estado.sessao) headers["X-CSRF-Token"] = estado.sessao.csrf;
    op.body = JSON.stringify(body);
  }
  let resposta;
  try {
    resposta = await fetch(path, op);
  } catch (e) {
    throw new Error(
      "A conexão foi interrompida. Tente novamente; a operação manterá o mesmo identificador.",
    );
  }
  const dados = await resposta.json();
  if (!resposta.ok) {
    const e = new Error(dados.mensagem || "Não foi possível concluir.");
    e.correlacao = dados.correlacao;
    e.status = resposta.status;
    if (e.status === 401 && estado.sessao) mostrarLogin();
    throw e;
  }
  return dados;
}
function comChave(nome, body) {
  const assinatura = JSON.stringify(body);
  let p = pendencias.get(nome);
  if (!p || p.assinatura !== assinatura) {
    p = { assinatura, chave: novoId() };
    pendencias.set(nome, p);
  }
  return { ...body, chave: p.chave };
}
async function executar(nome, path, body, mensagem) {
  const r = await api(path, comChave(nome, body));
  pendencias.delete(nome);
  await atualizar();
  aviso(mensagem);
  return r;
}
function acao(texto, nome, id, classe) {
  const b = el("button", texto, classe);
  b.type = "button";
  b.dataset.acao = nome;
  b.dataset.id = id;
  return b;
}
function etiqueta(valor) {
  const nome = String(valor).replaceAll("_", " ");
  let classe = "etiqueta";
  if (["CANCELADA", "CANCELADO", "EXPIRADA", "INATIVO"].includes(valor))
    classe += " encerrado";
  if (["EM_REVISAO", "REPOSICAO", "VALIDADE"].includes(valor))
    classe += " atencao";
  if (valor === "FALHA") classe += " erro";
  return el("span", nome, classe);
}
function tabela(id, colunas, linhas, legenda) {
  const alvo = $(id);
  alvo.replaceChildren();
  if (!linhas.length) {
    alvo.append(el("p", "Nenhum registro encontrado.", "vazio"));
    return;
  }
  const t = el("table");
  t.append(el("caption", legenda || `${linhas.length} registro(s)`));
  const th = el("thead"),
    tr = el("tr");
  colunas.forEach((c) => {
    const n = el("th", c);
    n.scope = "col";
    tr.append(n);
  });
  th.append(tr);
  t.append(th);
  const tbody = el("tbody");
  linhas.forEach((celulas) => {
    const r = el("tr");
    celulas.forEach((c) => {
      const td = el("td");
      if (c instanceof Node) td.append(c);
      else td.textContent = c ?? "—";
      r.append(td);
    });
    tbody.append(r);
  });
  t.append(tbody);
  alvo.append(t);
}
function acoes(...botoes) {
  const r = el("div", undefined, "acoes");
  r.append(...botoes);
  return r;
}
function indicador(alvo, itens) {
  alvo.replaceChildren(
    ...itens.map(([nome, valor]) => {
      const n = el("div", undefined, "indicador");
      n.append(el("span", nome), el("strong", valor));
      return n;
    }),
  );
}
function preencher(select, itens, valor, texto, todos = false) {
  const atual = select.value;
  select.replaceChildren();
  if (todos) select.add(new Option("Todos", ""));
  itens.forEach((i) => select.add(new Option(texto(i), valor(i))));
  if ([...select.options].some((o) => o.value === atual)) select.value = atual;
}
function listas() {
  document.querySelectorAll("[data-produtos]").forEach((s) =>
    preencher(
      s,
      estado.produtos.filter((p) => p.ativo || s.hasAttribute("data-todos")),
      (p) => p.sku,
      (p) => `${p.sku} · ${p.nome}`,
      s.hasAttribute("data-todos"),
    ),
  );
  document.querySelectorAll("[data-depositos]").forEach((s) =>
    preencher(
      s,
      estado.depositos.filter((d) => d.ativo || s.hasAttribute("data-todos")),
      (d) => d.codigo,
      (d) => `${d.codigo} · ${d.nome}`,
      s.hasAttribute("data-todos"),
    ),
  );
  document.querySelectorAll("[data-lotes]").forEach((s) =>
    preencher(
      s,
      estado.lotes,
      (l) => l.id,
      (l) =>
        `${l.numero} · ${l.sku} · ${l.deposito} · ${quant.format(l.quantidade)}`,
    ),
  );
  preencher(
    $("inventario-atual"),
    estado.inventarios,
    (i) => i.id,
    (i) =>
      `${i.deposito} · ${formatarData(i.abertura)} · ${i.estado.replaceAll("_", " ")}`,
  );
  if (
    estado.inventario &&
    estado.inventarios.some((i) => i.id === estado.inventario)
  )
    $("inventario-atual").value = estado.inventario;
  estado.inventario = $("inventario-atual").value || null;
}
function permissoes() {
  document
    .querySelectorAll("[data-gestor]")
    .forEach((n) => (n.hidden = !gestor()));
  document
    .querySelectorAll("[data-operar]")
    .forEach((n) => (n.hidden = !operar()));
  document
    .querySelectorAll("[data-gestao]")
    .forEach((n) => (n.hidden = !gestao()));
}
function mostrarLogin() {
  document.querySelector(".pular").href = "#form-login";
  estado.sessao = null;
  $("aplicacao").hidden = true;
  $("login").hidden = false;
  $("form-login").elements.senha.value = "";
  $("form-login").elements.usuario.focus();
}
async function entrar(sessao) {
  document.querySelector(".pular").href = "#conteudo";
  estado.sessao = sessao;
  $("login").hidden = true;
  $("aplicacao").hidden = false;
  $("nome-conta").textContent = sessao.usuario.nome;
  $("perfil-conta").textContent = sessao.usuario.perfil;
  permissoes();
  await atualizar();
  pagina("inicio");
}
function pagina(nome) {
  estado.pagina = nome;
  document
    .querySelectorAll(".pagina")
    .forEach((p) => (p.hidden = p.id !== `pagina-${nome}`));
  document.querySelectorAll("[data-pagina]").forEach((b) => {
    if (b.dataset.pagina === nome) b.setAttribute("aria-current", "page");
    else b.removeAttribute("aria-current");
  });
  $("titulo-pagina").textContent = rotulos[nome];
  $("titulo-pagina").focus();
  if (nome === "auditoria") auditoria().catch(erro);
}
function erro(e) {
  aviso(e.message, true, e.correlacao);
}
const periodoCompleto = () =>
  new URLSearchParams({
    inicio: "2000-01-01T00:00:00-03:00",
    fim: "2100-12-31T23:59:59.999999999-03:00",
  });
async function atualizar() {
  if (!estado.sessao) return;
  document.body.setAttribute("aria-busy", "true");
  try {
    [
      estado.produtos,
      estado.depositos,
      estado.reservas,
      estado.inventarios,
      estado.alertas,
      estado.posicoes,
      estado.movimentos,
    ] = await Promise.all([
      api("/api/produtos"),
      api("/api/depositos"),
      api("/api/reservas"),
      api("/api/inventarios"),
      api("/api/alertas"),
      api("/api/posicoes"),
      api("/api/movimentos?" + periodoCompleto()),
    ]);
    const lotes = await Promise.all(
      estado.produtos.flatMap((p) =>
        estado.depositos.map((d) =>
          api(
            "/api/lotes?" +
              new URLSearchParams({ sku: p.sku, deposito: d.codigo }),
          ),
        ),
      ),
    );
    estado.lotes = lotes.flat();
    listas();
    renderizar();
    permissoes();
    if (estado.pagina === "auditoria") await auditoria();
  } finally {
    document.body.removeAttribute("aria-busy");
  }
}
function renderizar() {
  const ativos = estado.lotes.filter((l) => Number(l.quantidade) > 0),
    alertas = estado.alertas.filter((a) => a.dia === diaHoje());
  indicador($("indicadores"), [
    ["Produtos ativos", estado.produtos.filter((p) => p.ativo).length],
    ["Lotes com saldo", ativos.length],
    ["Alertas hoje", alertas.length],
    [
      "Valor em estoque",
      moeda.format(
        estado.posicoes.reduce((a, p) => a + Number(p.valorEstoque), 0),
      ),
    ],
  ]);
  tabela(
    "tabela-posicoes",
    ["Produto", "Depósito", "Físico", "Reservado", "Disponível", "Custo médio"],
    estado.posicoes.map((p) => [
      p.sku,
      p.deposito,
      quant.format(p.fisico),
      quant.format(p.reservado),
      p.consistente ? quant.format(p.disponivel) : etiqueta("FALHA"),
      moeda.format(p.custoMedio),
    ]),
  );
  tabela(
    "tabela-produtos",
    ["SKU", "Nome", "Unidade", "Mínimo", "Estado", "Ações"],
    estado.produtos.map((p) => [
      p.sku,
      p.nome,
      p.unidade,
      quant.format(p.estoqueMinimo),
      etiqueta(p.ativo ? "ATIVO" : "INATIVO"),
      gestor()
        ? acoes(
            acao("Editar", "editar-produto", p.sku),
            ...(p.ativo
              ? [acao("Inativar", "inativar-produto", p.sku, "perigo")]
              : []),
          )
        : "Consulta",
    ]),
  );
  tabela(
    "tabela-depositos",
    ["Código", "Nome", "Estado", "Ações"],
    estado.depositos.map((d) => [
      d.codigo,
      d.nome,
      etiqueta(d.ativo ? "ATIVO" : "INATIVO"),
      gestor() && d.ativo
        ? acao("Inativar", "inativar-deposito", d.codigo, "perigo")
        : "Consulta",
    ]),
  );
  tabela(
    "tabela-lotes",
    ["Lote", "Produto / depósito", "Saldo", "Custo", "Validade"],
    estado.lotes.map((l) => [
      l.numero,
      `${l.sku} / ${l.deposito}`,
      quant.format(l.quantidade),
      moeda.format(l.custo),
      l.validade.split("-").reverse().join("/"),
    ]),
  );
  tabela(
    "tabela-reservas",
    ["Produto", "Depósito", "Quantidade", "Expiração", "Estado", "Ações"],
    estado.reservas.map((r) => [
      r.sku,
      r.deposito,
      quant.format(r.quantidade),
      formatarData(r.expiraEm),
      etiqueta(r.estado),
      operar() && r.estado === "ATIVA"
        ? acoes(
            acao("Atender", "atender-reserva", r.id),
            acao("Cancelar", "cancelar-reserva", r.id, "perigo"),
          )
        : "—",
    ]),
  );
  tabela(
    "tabela-transferencias",
    ["Data", "Produto", "Origem", "Destino", "Quantidade"],
    estado.movimentos
      .filter((m) => m.tipo === "TRANSFERENCIA")
      .map((m) => [
        formatarData(m.instante),
        m.sku,
        m.origem,
        m.destino,
        quant.format(m.quantidade),
      ]),
  );
  tabela(
    "tabela-alertas",
    ["Dia", "Tipo", "Produto", "Depósito", "Mensagem"],
    estado.alertas
      .slice()
      .reverse()
      .map((a) => [
        a.dia.split("-").reverse().join("/"),
        etiqueta(a.tipo),
        a.sku,
        a.deposito,
        a.mensagem,
      ]),
  );
  inventarioAtual();
  camposMovimento();
}
function camposMovimento() {
  const f = $("form-movimento"),
    tipo = f.elements.tipo.value,
    entrada = tipo === "ENTRADA",
    lote = ["AJUSTE", "DESCARTE"].includes(tipo);
  $("campos-entrada").hidden = !entrada;
  $("campos-produto-movimento").hidden = lote;
  $("campo-lote-ajuste").hidden = !lote;
  $("campo-motivo").hidden = !lote;
  $("rotulo-quantidade").textContent =
    tipo === "AJUSTE" ? "Variação da quantidade" : "Quantidade";
  ["sku", "deposito"].forEach((n) => (f.elements[n].required = !lote));
  ["fornecedor", "numero", "custo", "validade"].forEach(
    (n) => (f.elements[n].required = entrada),
  );
  f.elements.loteId.required = lote;
  f.elements.motivo.required = lote;
}
function inventarioAtual() {
  const i = estado.inventarios.find((x) => x.id === estado.inventario),
    alvo = $("inventario-resumo");
  alvo.replaceChildren();
  $("acoes-inventario").hidden = !i;
  $("form-contagem").hidden = !i || i.estado !== "EM_CONTAGEM";
  $("form-conclusao").hidden = !i || i.estado !== "EM_REVISAO";
  if (!i) {
    alvo.append(
      el("p", "Abra um inventário para iniciar a conferência.", "vazio"),
    );
    $("tabela-contagens").replaceChildren();
    $("historico-contagens").replaceChildren();
    return;
  }
  alvo.append(
    el("p", `Depósito ${i.deposito} · aberto em ${formatarData(i.abertura)}`),
    etiqueta(i.estado),
  );
  document.querySelectorAll("[data-inventario-acao]").forEach((b) => {
    const a = b.dataset.inventarioAcao;
    b.hidden =
      a === "iniciar"
        ? i.estado !== "ABERTO"
        : a === "revisar"
          ? i.estado !== "EM_CONTAGEM"
          : ["CONCLUIDO", "CANCELADO"].includes(i.estado);
  });
  const lotes = estado.lotes.filter((l) => Object.hasOwn(i.referencia, l.id));
  preencher(
    $("lote-contagem"),
    lotes,
    (l) => l.id,
    (l) =>
      `${l.numero} · ${l.sku} · referência ${quant.format(i.referencia[l.id])}`,
  );
  const contagens = Object.entries(i.contagens);
  tabela(
    "tabela-contagens",
    ["Aprovar", "Lote", "Referência", "Contado", "Diferença"],
    contagens.map(([id, q]) => {
      const l = estado.lotes.find((x) => x.id === id),
        label = el("label", undefined, "check"),
        check = el("input");
      check.type = "checkbox";
      check.name = "aprovado";
      check.value = id;
      check.checked = true;
      check.disabled = i.estado !== "EM_REVISAO";
      label.append(check, el("span", `Aprovar ${l?.numero || id}`));
      return [
        label,
        l?.numero || id,
        quant.format(i.referencia[id]),
        quant.format(q),
        quant.format(Number(q) - Number(i.referencia[id])),
      ];
    }),
  );
  tabela(
    "historico-contagens",
    ["Data", "Lote", "Quantidade", "Responsável"],
    i.historico.map((h) => [
      formatarData(h.instante),
      estado.lotes.find((l) => l.id === h.loteId)?.numero || h.loteId,
      quant.format(h.quantidade),
      h.ator,
    ]),
  );
}
function adicionarItem() {
  const linha = el("div", undefined, "item-transferencia");
  const produto = el("label", "Produto"),
    select = el("select");
  select.dataset.produtos = "";
  select.name = "sku-item";
  select.required = true;
  produto.append(select);
  const quantidade = el("label", "Quantidade"),
    input = el("input");
  input.name = "quantidade-item";
  input.inputMode = "decimal";
  input.required = true;
  quantidade.append(input);
  const remover = el("button", "Remover");
  remover.type = "button";
  remover.addEventListener("click", () => {
    if ($("itens-transferencia").children.length > 1) linha.remove();
    else aviso("A transferência precisa de pelo menos um produto.", true);
  });
  linha.append(produto, quantidade, remover);
  $("itens-transferencia").append(linha);
  preencher(
    select,
    estado.produtos.filter((p) => p.ativo),
    (p) => p.sku,
    (p) => `${p.sku} · ${p.nome}`,
  );
}
function formulario(id, trabalho) {
  $(id).addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget,
      button = event.submitter;
    button.disabled = true;
    try {
      await trabalho(form);
    } catch (e) {
      erro(e);
    } finally {
      button.disabled = false;
    }
  });
}
formulario("form-login", async (f) => {
  const s = await api("/api/login", {
    usuario: f.elements.usuario.value,
    senha: f.elements.senha.value,
  });
  f.elements.senha.value = "";
  await entrar(s);
});
formulario("form-produto", async (f) => {
  const b = {
      sku: f.elements.sku.value,
      nome: f.elements.nome.value,
      unidade: f.elements.unidade.value,
      minimo: numero(f.elements.minimo.value),
    },
    path = f.dataset.editando
      ? `/api/produtos/${encodeURIComponent(f.dataset.editando)}/alterar`
      : "/api/produtos";
  await executar("produto", path, b, "Produto salvo.");
  resetProduto();
});
function resetProduto() {
  const f = $("form-produto");
  delete f.dataset.editando;
  f.reset();
  f.elements.sku.readOnly = false;
  $("produto-form-titulo").textContent = "Cadastrar produto";
  $("cancelar-edicao").hidden = true;
}
formulario("form-deposito", async (f) => {
  await executar(
    "deposito",
    "/api/depositos",
    { codigo: f.elements.codigo.value, nome: f.elements.nome.value },
    "Depósito cadastrado.",
  );
  f.reset();
});
formulario("form-movimento", async (f) => {
  const v = f.elements,
    tipo = v.tipo.value;
  let b, path;
  if (tipo === "ENTRADA") {
    b = {
      sku: v.sku.value,
      deposito: v.deposito.value,
      fornecedor: v.fornecedor.value,
      numero: v.numero.value,
      quantidade: numero(v.quantidade.value),
      custo: numero(v.custo.value),
      validade: v.validade.value,
    };
    path = "/api/entradas";
  } else if (tipo === "SAIDA") {
    b = {
      sku: v.sku.value,
      deposito: v.deposito.value,
      quantidade: numero(v.quantidade.value),
    };
    path = "/api/saidas";
  } else {
    b = { loteId: v.loteId.value, motivo: v.motivo.value };
    b[tipo === "AJUSTE" ? "variacao" : "quantidade"] = numero(
      v.quantidade.value,
    );
    path = tipo === "AJUSTE" ? "/api/ajustes" : "/api/descartes";
  }
  await executar("movimento", path, b, "Movimentação registrada.");
  v.quantidade.value = "";
});
formulario("form-reserva", async (f) => {
  const v = f.elements;
  await executar(
    "reserva",
    "/api/reservas",
    {
      sku: v.sku.value,
      deposito: v.deposito.value,
      quantidade: numero(v.quantidade.value),
      expiraEm: v.expiraEm.value,
    },
    "Reserva criada.",
  );
  v.quantidade.value = "";
});
formulario("form-transferencia", async (f) => {
  const itens = [...$("itens-transferencia").children].map((n) => ({
    sku: n.querySelector("select").value,
    quantidade: numero(n.querySelector("input").value),
  }));
  await executar(
    "transferencia",
    "/api/transferencias",
    {
      origem: f.elements.origem.value,
      destino: f.elements.destino.value,
      itens,
    },
    "Transferência concluída.",
  );
});
formulario("form-inventario", async (f) => {
  const i = await executar(
    "inventario",
    "/api/inventarios",
    { deposito: f.elements.deposito.value },
    "Inventário aberto.",
  );
  estado.inventario = i.id;
  $("inventario-atual").value = i.id;
  inventarioAtual();
});
formulario("form-contagem", async (f) => {
  await executar(
    "contagem",
    `/api/inventarios/${estado.inventario}/contar`,
    {
      loteId: f.elements.loteId.value,
      quantidade: numero(f.elements.quantidade.value),
    },
    "Contagem registrada.",
  );
  f.elements.quantidade.value = "";
});
formulario("form-conclusao", async (f) => {
  const aprovados = [
    ...document.querySelectorAll("input[name=aprovado]:checked"),
  ].map((n) => n.value);
  await executar(
    "conclusao",
    `/api/inventarios/${estado.inventario}/concluir`,
    { aprovados, motivo: f.elements.motivo.value },
    "Inventário concluído.",
  );
});
formulario("form-relatorio", async (f) => {
  const v = f.elements,
    q = new URLSearchParams({
      sku: v.sku.value,
      deposito: v.deposito.value,
      inicio: v.inicio.value + "T00:00:00",
      fim: v.fim.value + "T23:59:59.999999999",
    });
  const r = await api("/api/relatorio?" + q);
  estado.relatorio = r;
  indicador($("resumo-relatorio"), [
    ["Valor atual", moeda.format(r.valorEstoque)],
    ["Perdas no período", moeda.format(r.valorPerdas)],
    [
      "Giro financeiro",
      Number(r.giro).toLocaleString("pt-BR", { maximumFractionDigits: 4 }),
    ],
  ]);
  tabela(
    "tabela-relatorio",
    ["Data", "Tipo", "Produto", "Origem", "Destino", "Quantidade", "Valor"],
    r.movimentos.map((m) => [
      formatarData(m.instante),
      m.tipo,
      m.sku,
      m.origem,
      m.destino,
      quant.format(m.quantidade),
      moeda.format(m.valor),
    ]),
  );
  $("exportar-csv").disabled = false;
  aviso("Relatório atualizado.");
});
async function auditoria() {
  if (!gestao()) return;
  const eventos = await api("/api/eventos?" + periodoCompleto());
  tabela(
    "tabela-auditoria",
    ["Instante", "Ator", "Ação", "Recurso", "Resultado", "Atendimento"],
    eventos
      .slice()
      .reverse()
      .map((e) => [
        formatarData(e.instante),
        e.ator,
        e.acao,
        el("span", e.recurso, "monoespaco"),
        etiqueta(e.resultado),
        el("span", e.correlacao, "monoespaco"),
      ]),
  );
}
document.addEventListener("click", async (event) => {
  const b = event.target.closest("[data-acao]");
  if (!b) return;
  const nome = b.dataset.acao,
    id = b.dataset.id;
  b.disabled = true;
  try {
    if (nome === "editar-produto") {
      const p = estado.produtos.find((x) => x.sku === id),
        f = $("form-produto");
      f.dataset.editando = id;
      for (const n of ["sku", "nome", "unidade"]) f.elements[n].value = p[n];
      f.elements.minimo.value = quant
        .format(p.estoqueMinimo)
        .replaceAll(".", "");
      f.elements.sku.readOnly = true;
      $("produto-form-titulo").textContent = "Editar produto";
      $("cancelar-edicao").hidden = false;
      f.elements.nome.focus();
    } else if (nome === "inativar-produto")
      await executar(
        nome + id,
        `/api/produtos/${encodeURIComponent(id)}/inativar`,
        {},
        "Produto inativado.",
      );
    else if (nome === "inativar-deposito")
      await executar(
        nome + id,
        `/api/depositos/${encodeURIComponent(id)}/inativar`,
        {},
        "Depósito inativado.",
      );
    else if (nome === "atender-reserva")
      await executar(
        nome + id,
        `/api/reservas/${id}/atender`,
        {},
        "Reserva atendida.",
      );
    else if (nome === "cancelar-reserva")
      await executar(
        nome + id,
        `/api/reservas/${id}/cancelar`,
        {},
        "Reserva cancelada.",
      );
  } catch (e) {
    erro(e);
  } finally {
    b.disabled = false;
  }
});
document.querySelectorAll("[data-inventario-acao]").forEach((b) =>
  b.addEventListener("click", async () => {
    b.disabled = true;
    try {
      await executar(
        "inventario-" + b.dataset.inventarioAcao,
        `/api/inventarios/${estado.inventario}/${b.dataset.inventarioAcao}`,
        {},
        "Inventário atualizado.",
      );
    } catch (e) {
      erro(e);
    } finally {
      b.disabled = false;
    }
  }),
);
document
  .querySelectorAll("[data-pagina]")
  .forEach((b) => b.addEventListener("click", () => pagina(b.dataset.pagina)));
$("inventario-atual").addEventListener("change", () => {
  estado.inventario = $("inventario-atual").value;
  inventarioAtual();
});
$("tipo-movimento").addEventListener("change", camposMovimento);
$("adicionar-item").addEventListener("click", adicionarItem);
$("cancelar-edicao").addEventListener("click", resetProduto);
$("atualizar").addEventListener("click", () => atualizar().catch(erro));
$("gerar-alertas").addEventListener("click", () =>
  executar("alertas", "/api/alertas/gerar", {}, "Alertas verificados.").catch(
    erro,
  ),
);
$("sair").addEventListener("click", async () => {
  try {
    await api("/api/sair", {});
    pendencias.clear();
    mostrarLogin();
  } catch (e) {
    erro(e);
  }
});
$("exportar-csv").addEventListener("click", () => {
  if (!estado.relatorio) return;
  const seguro = (v) => {
    let s = String(v ?? "");
    if (/^[=+\-@\t\r]/.test(s)) s = "'" + s;
    return '"' + s.replaceAll('"', '""') + '"';
  };
  const linhas = [
    ["Data", "Tipo", "Produto", "Origem", "Destino", "Quantidade", "Valor"],
    ...estado.relatorio.movimentos.map((m) => [
      formatarData(m.instante),
      m.tipo,
      m.sku,
      m.origem,
      m.destino,
      quant.format(m.quantidade),
      moeda.format(m.valor),
    ]),
  ];
  const url = URL.createObjectURL(
    new Blob(
      ["\uFEFF" + linhas.map((l) => l.map(seguro).join(";")).join("\r\n")],
      { type: "text/csv;charset=utf-8" },
    ),
  );
  const a = el("a");
  a.href = url;
  a.download = "relatorio-estoque.csv";
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
});
$("form-reserva").elements.expiraEm.value = diaHoje() + "T23:59";
$("form-relatorio").elements.inicio.value = diaHoje().slice(0, 8) + "01";
$("form-relatorio").elements.fim.value = diaHoje();
adicionarItem();
api("/api/sessao")
  .then(entrar)
  .catch((e) => {
    if (e.status !== 401) erro(e);
  });
