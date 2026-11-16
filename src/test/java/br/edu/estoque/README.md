# Testes do Sistema de Controle de Estoque

Esta pasta contém os casos CT001 a CT015, derivados da especificação de requisitos do projeto.

## Arquivos

- `ServicoEstoqueTest.java`: suíte de testes automatizados com JUnit Jupiter.
- `ExecucaoCT001aCT015.java`: executor independente auxiliar dos cenários.

## Execução com Maven

Abra o terminal na pasta `estoque`, onde está o arquivo `pom.xml`, e execute:

```powershell
mvn clean test
```

O Maven compila o projeto e executa os testes JUnit pelo plugin Surefire.

## Resultados

Os relatórios da execução ficam em `target/surefire-reports/`.

Para cada caso, o relatório técnico registra as entradas, o resultado esperado, o resultado obtido e a conclusão. Nos casos corrigidos, também são registrados o defeito inicial e o resultado da reexecução.