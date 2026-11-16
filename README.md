# Sistema de Controle de Estoque

Projeto acadêmico da UNIFACS para a A3 de Garantia e Qualidade de Software — 2026.2. Implementa o núcleo de estoque em **Java 17**, uma interface web local e testes automatizados com **JUnit 5 e Maven**.

A referência funcional é a Especificação de Requisitos, versão 1.0, fornecida separadamente ao grupo. A [matriz de rastreabilidade](docs/MATRIZ_RASTREABILIDADE.md) relaciona os requisitos às operações e aos testes. O [relatório de execução](docs/RELATORIO_EXECUCAO.md) apresenta entradas, resultados e limitações verificadas.

## Executar no Windows

Abra a pasta que contém `pom.xml` no VS Code. No terminal PowerShell:

```powershell
java -version
javac -version
.\mvnw.cmd clean verify
java -jar .\target\controle-estoque.jar
```

Abra **http://localhost:8080** no navegador. Encerre com `Ctrl+C`.

O Maven Wrapper baixa a versão configurada do Maven na primeira execução e valida seu checksum. É necessário ter **JDK 17 ou superior e internet para baixar as dependências**; não é necessário instalar o comando `mvn`. Também é possível executar `iniciar.cmd` para compilar e abrir o servidor no terminal.

Linux e macOS:

```bash
./mvnw clean verify
java -jar target/controle-estoque.jar
```

Se a porta estiver ocupada:

```powershell
java -jar .\target\controle-estoque.jar --porta 8081
```

## Contas da demonstração local

| Usuário | Senha inicial | Permissões |
| --- | --- | --- |
| `gestor` | `a3-gestor-2026` | Configurações, operações, inventário, relatórios e auditoria |
| `operador` | `a3-operador-2026` | Entradas, saídas, reservas e transferências em `CD-01` e `LOJA-01` |
| `auditor` | `a3-auditor-2026` | Consulta de dados, relatórios e auditoria |

Essas credenciais são públicas e destinam-se ao cenário acadêmico local. O servidor aceita conexões apenas da própria máquina. As senhas podem ser substituídas pelas variáveis `ESTOQUE_SENHA_GESTOR`, `ESTOQUE_SENHA_OPERADOR` e `ESTOQUE_SENHA_AUDITOR`, com pelo menos oito caracteres. Sessões usam cookie HttpOnly, prazo de 30 minutos e proteção contra requisições forjadas.

Na primeira abertura são criados produtos e lotes de demonstração com datas relativas ao dia atual. Os dados permanecem em memória durante a execução e são confirmados em `.dados/estoque.bin`, junto com movimentos, auditoria e chaves de operação. Esse arquivo é local e está no `.gitignore`.

Para uma apresentação que comece sempre com os dados iniciais:

```powershell
java -jar .\target\controle-estoque.jar --memoria
```

## Funcionalidades

- Produtos: cadastro, alteração com histórico e inativação protegida por reservas.
- Depósitos: cadastro, consulta e controle de atividade.
- Lotes: fornecedor, identificação única, quantidade, custo e validade.
- Saída FEFO: usa lotes não vencidos e respeita o saldo disponível.
- Reservas: criação, atendimento, cancelamento e expiração no instante limite.
- Transferências: um ou vários produtos, preservação dos lotes e confirmação atômica.
- Ajustes e descarte: autorização de gestor, justificativa e registro de perdas.
- Inventário: referência de saldo, contagens com histórico, revisão e conciliação aprovada.
- Alertas: reposição no mínimo ou abaixo dele e vencimento em até 30 dias, sem duplicação diária.
- Gestão: posição, movimentos com filtros inclusivos, valor de estoque, perdas, giro e CSV.
- Auditoria: ator, ação, recurso, instante, resultado e identificador de atendimento.
- Confiabilidade: operações concorrentes, idempotência e recuperação das chaves após reinício.

## Testes e evidências

```powershell
.\mvnw.cmd clean verify
```

A execução completa compila o Java, roda os testes JUnit, produz o JAR executável e gera cobertura JaCoCo. Os casos CT001–CT015 foram preservados e complementados; os critérios CA-001–CA-024 estão em uma classe própria. Há verificações de regras, inventário, concorrência, persistência, HTTP e desempenho com 100 mil lotes.

| Evidência | Local |
| --- | --- |
| Resultado observado e entradas/saídas | [Relatório de execução](docs/RELATORIO_EXECUCAO.md) |
| Mapeamento RF, RN, CA e RNF | [Matriz de rastreabilidade](docs/MATRIZ_RASTREABILIDADE.md) |
| Log Maven desta entrega | [maven-verify.txt](docs/evidencias/maven-verify.txt) |
| Resultados JUnit da sua execução | `target/surefire-reports/` |
| Cobertura da sua execução | `target/site/jacoco/index.html` |
| Medição de desempenho | `target/evidencias/desempenho.txt` |

O executor `ExecucaoCT001aCT015` é auxiliar e seus resultados são identificados separadamente dos resultados JUnit. Os arquivos `resultado-antes-correcao.txt` e `resultado-apos-correcao*.txt` preservam a execução histórica da primeira suíte de 15 testes.

Os testes opcionais de navegador usam Node.js e Playwright:

```bash
npm install --ignore-scripts
npx playwright install chromium firefox
npm run test:web
```

No PowerShell, para Firefox: `$env:BROWSER="firefox"; npm run test:web`. Eles usam uma instância temporária em memória, percorrem os formulários, verificam acessibilidade automaticamente e gravam evidências em `target/evidencias/`. A integração contínua está em [qualidade.yml](.github/workflows/qualidade.yml).

A conformidade integral de acessibilidade, a compatibilidade nas duas últimas versões estáveis de Chrome/Edge/Firefox e a disponibilidade mensal exigem as validações descritas em [Decisões e validações](docs/DECISOES_E_VALIDACOES.md).

## Demonstração no terminal

```powershell
java -jar .\target\controle-estoque.jar --demo
```

Apresenta, em sequência, saldo inicial, reserva, saída, atendimento, transferência, custo médio e conclusão de inventário. Veja as [saídas esperadas e o roteiro](docs/COMO_APRESENTAR.md).

No VS Code, utilize **Run Java** ou **Debug Java** com as extensões Java recomendadas. A execução isolada de `Aplicacao.java` por um comando `javac Aplicacao.java` não compila os demais pacotes do projeto.

## Organização

| Pasta | Conteúdo |
| --- | --- |
| `src/main/java/br/edu/estoque/dominio` | Entidades, estados, validações e resultados |
| `src/main/java/br/edu/estoque/repositorio` | Banco em memória, transações e snapshot local |
| `src/main/java/br/edu/estoque/servico` | Regras de negócio e autorização |
| `src/main/java/br/edu/estoque/web` | HTTP, JSON e sessões |
| `src/main/java/br/edu/estoque/app` | Inicialização e demonstração |
| `src/main/resources/web` | Interface HTML, CSS e JavaScript sem dependências externas |
| `src/test/java/br/edu/estoque` | Suítes JUnit e executor auxiliar |
| `docs` | Especificação, matriz, relatório e evidências |

## Equipe

- Adriano Santos Moura
- Guilherme Ribeiro dos Santos
- Gustavo Lopes Souza
- João Carvalho Rizzo Summers
- Kate Araujo dos Reis
- Vitória Souza Silva

## Professores

- Luiz Henrique Souza Almeida Santos
- Enderson Nobre Santos

**UNIFACS — Universidade Salvador · A3 · 2026.2**
