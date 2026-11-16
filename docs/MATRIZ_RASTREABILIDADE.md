# Matriz de rastreabilidade — ERS Controle de Estoque 1.0

Referência: PDF de 34 páginas, emitido em 20/09/2026 por Enderson Nobre Santos. Implementação de trabalho baseada no commit `ee691c9b56f846a4ad40412c07eb4073b41062b7`.

“Implementado” descreve o código entregue. A evidência de execução e as validações externas estão no [relatório](RELATORIO_EXECUCAO.md). O total de testes inclui cada invocação dos testes parametrizados.

## Requisitos funcionais

As operações estão em `src/main/java/br/edu/estoque/servico/ServicoEstoque.java`. As classes de teste estão em `src/test/java/br/edu/estoque/`.

| RF | Operação implementada | Testes principais |
| --- | --- | --- |
| RF-001 | `cadastrarProduto`, SKU normalizado e único | CT001, CT002, CA004, CA005; limites em RegrasNegocioTest |
| RF-002 | `alterarProduto`, `historicoProduto` | RF002_alteracaoMantemHistorico; RF002_alteracaoInvalidaNaoMudaDados |
| RF-003 | `inativarProduto`, bloqueio por reserva ativa | RF003_inativacaoSemReserva; RF003_inativacaoComReservaBloqueada |
| RF-004 | `cadastrarDeposito` | CT003, CT004; RF004_codigoEquivalenteDuplicado; UC02_limiteConfiguradoAceitaAteLimiteRejeitaExcesso |
| RF-005 | `registrarEntrada`, validações de quantidade, custo e data | CT005, CT006, CA001–003; testes parametrizados de domínio e datas |
| RF-006 | Chave de lote por SKU + fornecedor + número | RF006_loteDuplicadoMesmoFornecedor; RF006_numeroIgualFornecedorDiferente |
| RF-007 | `registrarSaida`, saldo disponível e FEFO | CT011, CT012, CA007–010; RNF003_saidasConcorrentesNaoDuplicamConsumo |
| RF-008 | Seleção de lotes não vencidos e descarte separado | CA011; RN009_descarteAutorizadoDeVencido |
| RF-009 | `reservar`, quantidade sem alteração do físico | CT008, CT009, CA006, CA012, CA013; UC05_* |
| RF-010 | `atenderReserva`, FEFO e atendimento único | RF010_atendimentoBaixaSomenteReservaUmaVez; RF010_reservaCanceladaNaoAtendida; HTTP |
| RF-011 | `cancelarReserva`, liberação única e estados terminais | CA015; RN011_* |
| RF-012 | `expirarReservas`, limite inclusivo e disponibilidade efetiva | CT010, CA014; RN012_* |
| RF-013 | `transferir`, `transferirItens`, transação única | CT013, CA016, CA017; RF013_* e ConcorrenciaTest |
| RF-014 | Idempotência por chave e conteúdo canônico | CT014, CA018, CA019; concorrência e reinício |
| RF-015 | `ajustarLote`, gestor, justificativa e invariantes | RN017_*, RF015_ajusteNaoPodeViolarReservado; RNF018_ajusteIdempotente |
| RF-016 | `abrirInventario`, referência imutável | RF016_snapshotNaoMudaComMovimentacaoPosterior; UC10_* |
| RF-017 | `registrarContagem`, substituição com histórico | CA020; RF017_contagemNegativaRejeitada; UC10_substituiContagemComHistorico |
| RF-018 | Revisão, aprovação, conclusão atômica e repetível | CA021; RF018_*; RF026_operadorNaoAbreNemConcluiInventario; matriz de 17 transições proibidas e cancelamento nos três estados permitidos |
| RF-019 | `posicao`, físico, reservado, disponível e consistência | CT007, CA006–008; consultas e autorização por depósito |
| RF-020 | `custoMedio`, média ponderada e HALF_UP | CT015; RN024_arredondaHalfUp; RF013_preservaCustoValidadeFornecedor |
| RF-021 | `requerReposicao`, `gerarAlertas` | CA022; RN021_reposicaoAbaixoIgualAcima; UC13_* |
| RF-022 | `lotesProximosDoVencimento`, `gerarAlertas` | CA023; RN022_vencimento29_30_31Dias; UC13_* |
| RF-023 | `movimentos`, produto, local, tipo e período inclusivo | CA024; filtros usados no relatório e interface |
| RF-024 | `relatorio`, posição, giro financeiro, perdas e valor; CSV web | RF024_relatorioConsolidaValorEPerda; UC14_relatorioVazioEIntervaloInvalido |
| RF-025 | Eventos de sucesso e falha, recurso e correlação | RF025_auditaSucessoEFalhaSemDetalhesTecnicos; PersistenciaTest |
| RF-026 | Ator explícito, perfil e depósitos permitidos | RF026_*; ServidorHttpTest; permissões de inventário |

## Regras e limites

| RN | Cobertura |
| --- | --- |
| RN-001 | Normalização, espaços externos, unicidade e limites de SKU |
| RN-002 | Produto/depósito ativo para novas operações e bloqueio de inativos |
| RN-003 | Quantidade positiva, três casas, mínimo, máximo e valores excedentes |
| RN-004 | Custo positivo, limites e lote identificável |
| RN-005 | Entrada vencida rejeitada e validade de hoje aceita |
| RN-006 | Físico menos reservas vigentes, sem alteração do físico na reserva |
| RN-007 | Saída exata e excesso mínimo sem efeitos colaterais |
| RN-008 | Vencimento mais próximo e desempate pela entrada |
| RN-009 | Saída vencida bloqueada e descarte autorizado registrado |
| RN-010 | Reserva exata, excedente mínimo e disputa concorrente |
| RN-011 | Atendimento/cancelamento únicos; atendida e expirada são terminais |
| RN-012 | Antes, no instante e após expiração, inclusive consulta efetiva |
| RN-013 | Depósitos diferentes, ativos e autorizados |
| RN-014 | Conservação por SKU e rollback de falha na confirmação |
| RN-015 | Repetição com conteúdo equivalente, inclusive quantidade normalizada |
| RN-016 | Mesma chave com conteúdo divergente rejeitada |
| RN-017 | Gestor e motivo após trim: 9/10/11 e 199/200/201 caracteres |
| RN-018 | No máximo um ajuste por item na conclusão, inclusive repetição |
| RN-019 | Contagem negativa rejeitada; zero aceito |
| RN-020 | Custo ponderado após entradas e valor preservado na transferência |
| RN-021 | Disponível mínimo − 0,001 / mínimo / mínimo + 0,001 |
| RN-022 | Validade em 29 / 30 / 31 dias |
| RN-023 | Início e fim inclusivos; intervalo invertido rejeitado |
| RN-024 | BigDecimal; quantidades com três casas; custos com duas e HALF_UP |

Outros limites: quantidade 0 / 0,001 / 0,002 e 999.999,998 / 999.999,999 / 1.000.000,000; custo 0,009 / 0,01 / 0,02 e 9.999.999,98 / 9.999.999,99 / 10.000.000,00; chave de 15/16/17 e 63/64/65 caracteres; datas nos limites de 2000 e 2100. Alguns vizinhos de quantidade são exercitados também nas saídas e reservas.

## Critérios de aceitação

Cada linha possui um método próprio em `CriteriosAceitacaoTest`.

| CA | Cenário |
| --- | --- |
| CA-001 | Entrada de 0,001 |
| CA-002 | Rejeição de quantidade zero |
| CA-003 | Rejeição de 1,0001 |
| CA-004 | SKU convertido para maiúsculas |
| CA-005 | SKU equivalente não duplica |
| CA-006 | Físico 10, reservado 3, disponível 7 |
| CA-007 | Saída de todo o disponível |
| CA-008 | Saída excedente em 0,001 sem efeito |
| CA-009 | FEFO por validade |
| CA-010 | FEFO com empate pela entrada |
| CA-011 | Bloqueio de lote que venceu após a entrada |
| CA-012 | Reserva de todo o disponível |
| CA-013 | Reserva excedente em 0,001 |
| CA-014 | Expiração no instante exato |
| CA-015 | Cancelamento repetido libera uma vez |
| CA-016 | Transferência conserva saldo global |
| CA-017 | Falha na confirmação restaura ambos os depósitos |
| CA-018 | Repetição igual retorna transferência original |
| CA-019 | Repetição divergente é rejeitada |
| CA-020 | Contagem zero aceita e conciliada |
| CA-021 | Conclusão repetida não duplica ajustes |
| CA-022 | Alerta no mínimo exato |
| CA-023 | Alerta em 30 dias exatos |
| CA-024 | Registros nas duas bordas do intervalo |

## Requisitos não funcionais

| RNF | Implementação e forma de verificação |
| --- | --- |
| RNF-001 | Teste P95 com 100 mil lotes; resultado em `evidencias/desempenho.txt` |
| RNF-002 | Teste P95 de movimentação; medição em memória, no ambiente registrado |
| RNF-003 | Monitor compartilhado por banco; testes de saídas e reservas simultâneas |
| RNF-004 | Snapshot anterior, rollback e confirmação conjunta com movimento/auditoria |
| RNF-005 | BigDecimal e HALF_UP; custo e quantidade testados nos limites |
| RNF-006 | Perfil e depósito na camada de serviço; sessão autenticada no HTTP |
| RNF-007 | Eventos estruturados sem corpo de requisição, senha ou token; identidades de demonstração sem documentos pessoais |
| RNF-008 | Evento imutável com ator, instante, ação, recurso, resultado e correlação |
| RNF-009 | HTTP com mensagens controladas, códigos de domínio e correlação; JSON inválido sem stack trace |
| RNF-010 | Interface semântica, foco visível e testes axe automatizados; auditoria manual WCAG permanece necessária; axe aprovado em 15 telas/estados por navegador |
| RNF-011 | Endpoint `/api/saude`; meta de 99,5% depende da instalação e de acompanhamento mensal |
| RNF-012 | Repetição segura por chave e conteúdo; rollback conserva capacidade de retry |
| RNF-013 | Regras exercitadas sem navegador/rede; HTTP está em suíte separada |
| RNF-014 | Identificador de correlação nas falhas públicas e nos eventos |
| RNF-015 | `pt-BR`, BRL e America/Sao_Paulo na interface; teste de virada de dia |
| RNF-016 | Testes web e CI em Chromium/Firefox; duas versões estáveis de Chrome/Edge/Firefox dependem da matriz manual |
| RNF-017 | Sem expurgo automático; snapshot conserva movimentos/eventos; teste temporal simula mais de cinco anos |
| RNF-018 | Chaves persistidas para entrada, saída, reserva, transferência e ajuste; concorrência e reinício testados |

Os RNFs com acompanhamento temporal ou avaliação humana não são apresentados como certificados pela aprovação dos testes JUnit.
