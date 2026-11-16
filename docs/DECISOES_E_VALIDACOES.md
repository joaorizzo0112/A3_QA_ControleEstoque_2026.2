# Decisões de implementação e validações externas

## Escopo acadêmico

Java 17 é a referência. A interface usa o servidor HTTP do JDK e arquivos HTML/CSS/JavaScript locais. Node.js é opcional e serve aos testes de navegador. Não há dependência de rede nas regras de domínio. A persistência em memória continua sendo a base; um snapshot local foi acrescentado para conservar o histórico entre execuções.

A versão de partida implementava apenas parte do documento. As correções preservam as finalidades dos CT001–CT015 e ampliam a suíte pelos RF, RN e CA do PDF fornecido. A seção 10 desse PDF é “Interfaces e dados”; os nomes CT001–CT015 são identificadores históricos do projeto, não uma enumeração presente nesse PDF.

## Decisões explícitas

| Tema | Decisão |
| --- | --- |
| Gestor | Também pode executar as operações de estoque. O operador recebe permissões operacionais; o auditor consulta; o processo automático gera alertas e expira reservas. |
| Depósitos do operador | As contas locais de demonstração permitem CD-01 e LOJA-01. A API Java recebe um `Ator` com seu conjunto de depósitos permitidos. |
| API Java de compatibilidade | O construtor de serviço com dois argumentos usa gestor local. Destina-se a testes e código Java confiável; o HTTP usa obrigatoriamente o ator da sessão. |
| Chaves | Mesma chave exige mesma operação e mesmo conteúdo normalizado. A comparação é global no banco e sobrevive ao reinício quando o snapshot está ativo. Sobrecargas Java sem chave criam uma nova operação; HTTP exige a chave nas operações repetíveis. |
| Lote duplicado | A entrada inicial é única por SKU, fornecedor e número. Uma transferência preserva a raiz do lote e pode repartir sua quantidade entre depósitos. |
| Transferência | Conserva quantidade por produto, custo, validade, fornecedor e entrada original; vários itens são confirmados juntos. |
| Custo | Os custos de entrada são normalizados com HALF_UP para duas casas, dentro dos limites. O custo médio é ponderado pelos lotes que ainda possuem saldo. |
| Inventário | Contagem é comparada à referência capturada na abertura. Lotes movimentados depois da abertura são rejeitados na conclusão para impedir sobrescrita de movimentos posteriores. Só as contagens aprovadas geram ajustes. |
| Inventário e reservas | Ajustes positivos precedem os negativos na conciliação; o resultado nunca fica abaixo das reservas vigentes. |
| Expiração | A disponibilidade e o estado consultado consideram o instante limite, inclusive. O processo periódico materializa a expiração e gera auditoria. |
| Giro | O PDF não fixa fórmula. Foi adotado giro financeiro: custo das saídas no período dividido pela média dos valores de estoque no início e no fim do período. Quando o denominador é zero, retorna zero. |
| Posição em relatório | A lista de movimentos e perdas usa o intervalo inclusivo. A posição exibida é a posição atual, identificada como tal na tela. |
| Limite de depósitos | UC-02 cita um limite, mas o documento não informa seu valor. O mecanismo é configurável por `-Destoque.limiteDepositos=N` e está testado abaixo/no/acima do limite de teste. Por padrão não há teto de negócio adicional; confirmar o número contratual com o docente. |
| Retenção | Não há exclusão automática de movimentos ou auditoria. O arquivo local e suas cópias de segurança precisam ser conservados pela instalação para cumprir o período real de retenção. |

## Validações que exigem ambiente e acompanhamento

Aprovação de testes automatizados não comprova, sozinha, todos os requisitos de operação.

| Requisito | Roteiro complementar |
| --- | --- |
| RNF-010 — WCAG 2.2 AA | Percorrer todos os formulários apenas com teclado; verificar foco, erros, ampliação a 200%/400%, leitor de tela e conteúdo com dados longos. Associar às verificações axe e registrar navegador, versão e resultado. |
| RNF-011 — 99,5% | Monitorar `/api/saude` no horário operacional e calcular tempo disponível / tempo previsto durante o mês. Uma execução local de testes não representa um mês de disponibilidade. |
| RNF-016 — navegadores | Executar `npm run test:web` e o roteiro manual nas duas versões estáveis mais recentes de Chrome, Edge e Firefox. Registrar seis combinações de produto/versão, sistema, data, falhas e evidências. |
| RNF-017 — cinco anos | Exercitar restauração do snapshot e manter cópias de segurança conforme o prazo. O teste temporal comprova ausência de expurgo programado no cenário simulado. |
| Desempenho operacional | Repetir a medição no computador da apresentação e na configuração de armazenamento utilizada. O benchmark JUnit mede o núcleo em memória; não inclui latência de rede ou disco. |

A integração contínua executa Java 17/21 em Linux/Windows e os fluxos web em Chromium/Firefox. Cada execução precisa ser avaliada por seu resultado efetivo; configuração de workflow não é evidência de aprovação.

## Referências técnicas

- [Servidor HTTP do JDK 17](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html)
- [Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/)
- [JUnit 5.11.0](https://docs.junit.org/5.11.0/user-guide/)
- Especificação de Requisitos do Sistema de Controle de Estoque, versão 1.0, fornecida separadamente ao grupo
