# Sistema de Controle de Estoque

Projeto acadêmico desenvolvido para a disciplina de Garantia da Qualidade de Software da UNIFACS.

Implementa operações de controle de estoque com persistência em memória e relógio injetável, permitindo testes com datas e horários controlados.

## Requisitos

- JDK 17 ou superior.
- Apache Maven.

A compilação está configurada para Java 17. O projeto também pode ser compilado e executado com o JDK 21 utilizado nos testes.

## Funcionalidades

- Cadastro de produtos e depósitos.
- Registro de entradas e saídas de lotes.
- Consulta de saldo físico, reservado e disponível.
- Criação, cancelamento e expiração de reservas.
- Seleção de lotes pelo critério FEFO.
- Transferência de estoque entre depósitos.
- Cálculo do custo médio ponderado.
- Consulta de necessidade de reposição e proximidade do vencimento.

## Execução dos testes

Na pasta que contém o arquivo `pom.xml`, execute:

```powershell
mvn clean test
```

A suíte `ServicoEstoqueTest` contém os casos CT001 a CT015 e utiliza JUnit Jupiter, executado pelo Maven Surefire.

Os relatórios são gerados em `target/surefire-reports/`.

## Compilação e demonstração

Para gerar o pacote:

```powershell
mvn package
```

Para executar a demonstração:

```powershell
java -cp target/classes br.edu.estoque.app.Aplicacao
```

## Organização

- `src/main/java/br/edu/estoque/dominio`: entidades e enumerações.
- `src/main/java/br/edu/estoque/repositorio`: armazenamento em memória.
- `src/main/java/br/edu/estoque/servico`: operações e regras de negócio.
- `src/main/java/br/edu/estoque/app`: demonstração da aplicação.
- `src/test/java/br/edu/estoque`: testes JUnit e executor independente auxiliar.
- `target`: arquivos gerados pelo Maven.

## Documentação dos testes

O guia CT001–CT015 descreve os cenários e seus resultados esperados.

O relatório técnico registra as entradas, saídas esperadas, resultados obtidos, defeitos identificados e reexecuções após as correções.

Os registros anteriores às correções devem ser preservados como histórico. Os resultados atuais devem corresponder às evidências da execução da versão corrigida.

A especificação de requisitos é a referência para avaliar o comportamento do sistema.

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

## Instituição

UNIFACS — Universidade Salvador  
Garantia e Qualidade de Software — A3 — 2026.2
