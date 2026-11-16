# Guia completo dos testes CT001–CT015

## Base utilizada

Os CT001–CT015 identificam os cenários históricos da primeira versão do projeto. Nesta entrega, os testes são complementados pelos RF, RN e critérios CA-001–CA-024 da Especificação de Requisitos fornecida. A suíte usa JUnit Jupiter 5.11.0. A rastreabilidade revisada e os resultados da suíte ampliada estão em `docs/MATRIZ_RASTREABILIDADE.md` e `docs/RELATORIO_EXECUCAO.md`.

## Como executar

1. Instale o JDK 17 ou superior e Maven. O `pom.xml` define `maven.compiler.release` como `17`; portanto, o projeto compila para Java 17, mesmo quando executado com um JDK mais recente.
2. Abra o terminal na pasta `estoque` (a mesma pasta do `pom.xml`).
3. Execute:

   ```powershell
   mvn clean test
   ```

4. Consulte `target/surefire-reports/` para os relatórios gerados pelo JUnit/Surefire.

No VS Code, também é possível abrir `ServicoEstoqueTest.java` e usar os comandos **Run Test** disponibilizados pela extensão Java Test Runner.

O projeto está configurado para Java 17, e a execução registrada foi compilada com `--release 17`. Isso não representa uma migração para Java 25. O Maven Surefire executa a suíte JUnit Jupiter. O executor Java independente é uma ferramenta auxiliar; seus resultados devem ser identificados separadamente dos relatórios Maven/JUnit.

## Casos de teste

> **Notação:** valores em código como `5.000` representam cinco unidades com três casas decimais, e não cinco mil unidades.

### CT001 — Cadastro com sucesso de um produto válido

- Requisito: RF-001 | Tipo: funcional | Prioridade: alta.
- Preparação: banco em memória vazio e serviço instanciado com relógio fixo.
- Entrada: SKU SKU-001, nome Cabo de rede, unidade UN, estoque mínimo 5.000.
- Ação: chamar cadastrarProduto.
- Esperado: produto criado e armazenado em banco.produtos, sem exceção.
- Verificação do teste: objeto não nulo, SKU e nome corretos e mesma instância presente no mapa.
- Por que importa: confirma o caminho feliz do cadastro e a persistência no repositório em memória.

### CT002 — Rejeição de SKU com formato inválido

- Requisito: RN-001 | Tipo: negativo | Prioridade: alta.
- Entrada: SKU !@#, nome Teste, unidade UN, mínimo 1.
- Ação: tentar cadastrar o produto.
- Esperado: IllegalArgumentException com mensagem SKU inválido.
- Verificação: conferir tipo e mensagem da exceção e que nenhum produto foi inserido.
- Por que importa: impede identificadores fora do padrão [A-Za-z0-9_-]{3,20}.

### CT003 — Cadastro com sucesso de um depósito

- Requisito: RF-004 | Tipo: funcional | Prioridade: alta.
- Entrada: código cd-01, nome Centro de distribuição.
- Ação: chamar cadastrarDeposito.
- Esperado: depósito criado, código normalizado para CD-01 e registro salvo no mapa.
- Verificação: comparar o código e confirmar a mesma instância em banco.depositos.
- Por que importa: garante identificação padronizada dos depósitos.

### CT004 — Rejeição de código de depósito inválido ou em branco

- Requisito: RN-002 | Tipo: negativo | Prioridade: alta.
- Entrada: código vazio "", nome Depósito inválido.
- Ação: chamar cadastrarDeposito.
- Esperado: IllegalArgumentException com Código inválido.
- Verificação: conferir exceção e mapa de depósitos vazio.
- Por que importa: evita cadastrar depósito sem identificador.

### CT005 — Registro de entrada de lote com sucesso

- Requisito: RF-005 | Tipo: funcional | Prioridade: crítica.
- Pré-condição: produto SKU-001 e depósito CD-01 cadastrados.
- Entrada: lote L001, quantidade 20.000, custo 12.50, validade 60 dias à frente do relógio de teste.
- Ação: chamar registrarEntrada.
- Esperado: lote criado, vinculado ao produto e depósito, armazenado e saldo físico atualizado para 20.000.
- Verificação: lote não nulo, número correto, um lote no mapa e saldo físico correto.
- Por que importa: valida a principal operação de reposição de estoque.

### CT006 — Rejeição de lote com data de validade vencida

- Requisito: RN-005 | Tipo: negativo | Prioridade: alta.
- Pré-condição: produto e depósito cadastrados.
- Entrada: lote com validade um dia anterior à data fornecida pelo Clock.
- Ação: chamar registrarEntrada.
- Esperado: IllegalArgumentException com Lote vencido.
- Verificação: conferir mensagem e que o lote não foi armazenado.
- Por que importa: evita inserir mercadoria já vencida como estoque regular.
- Nota de determinismo: o teste usa relógio fixo em 15/11/2026 UTC; não depende da data real do computador.

### CT007 — Cálculo correto do saldo físico e disponível

- Requisito: RF-019 | Tipo: funcional | Prioridade: crítica.
- Pré-condição: produto e depósito cadastrados.
- Entrada: dois lotes do mesmo SKU/depósito: 10.000 e 15.000.
- Ação: consultar saldoFisico e saldoDisponivel.
- Esperado: ambos iguais a 25.000, pois não existe reserva ativa no cenário.
- Verificação: comparar valores decimais numericamente.
- Por que importa: confirma a consolidação dos lotes para consulta de estoque.

### CT008 — Realização de reserva de estoque com sucesso

- Requisito: RF-009 | Tipo: funcional | Prioridade: crítica.
- Pré-condição: saldo de 20.000 no depósito.
- Entrada: reservar 5.000, com expiração futura.
- Ação: chamar reservar.
- Esperado: objeto Reserva no estado ATIVA, armazenado no mapa.
- Verificação: estado, persistência e saldo físico preservado.
- Por que importa: garante que a reserva seja registrada sem retirar fisicamente os itens.
- Implementação corrigida: saldoDisponivel desconta as reservas ativas do saldo físico.
- Limite do caso original: o cenário verifica a criação da reserva e a preservação do saldo físico. A confirmação do desconto de reservas é uma verificação complementar.

### CT009 — Tentativa de reserva com saldo insuficiente

- Requisito: RN-010 | Tipo: negativo | Prioridade: crítica.
- Pré-condição: saldo de 10.000.
- Entrada: solicitar reserva de 15.000.
- Ação: chamar reservar.
- Esperado: IllegalStateException com Saldo disponível insuficiente.
- Verificação: confirmar exceção e que nenhuma reserva foi criada.
- Por que importa: impede comprometer quantidade que o depósito não possui disponível.

### CT010 — Expiração de reservas

- Preparação: estoque físico de 10,000 unidades e relógio controlado.
- Entrada: reserva de 2,000 com expiração em T+1 segundo.
- Ação: avançar o relógio a T+2 segundos e executar a expiração.
- Esperado: estado EXPIRADA. O CA-014 complementa o cenário no instante exato.
- Ajuste: a versão antiga criava uma reserva já vencida, em desacordo com o UC-05. A preparação agora respeita a criação com expiração futura.

### CT011 — Registro de saída via critério FEFO

- Requisito: RN-008 | Tipo: funcional | Prioridade: crítica.
- Pré-condição: dois lotes do mesmo produto e depósito.
- Entrada: LOTE-A com validade em 5 dias e LOTE-B em 25 dias, ambos com 5.000.
- Ação: registrar saída de 5.000.
- Esperado: consumir primeiro o lote com validade mais próxima: LOTE-A fica em zero e LOTE-B permanece com 5.000.
- Verificação: conferir a quantidade restante de cada lote.
- Por que importa: reduz perdas por vencimento.
- Ocorrência anterior: a ordenação de uma lista não modificável interrompia a saída com UnsupportedOperationException; a seleção também usava a entrada em vez da validade.
- Correção aplicada: criar uma cópia modificável dos lotes e ordenar pela validade, com desempate pela entrada.
- Resultado da execução Maven/JUnit após a correção: CT011 passou, conforme `resultado-apos-correcao.txt`.

### CT012 — Bloqueio de saída superior ao saldo físico

- Requisito: RN-007 | Tipo: negativo | Prioridade: crítica.
- Pré-condição: saldo físico de 10.000.
- Entrada: saída solicitada de 15.000.
- Ação: chamar registrarSaida.
- Esperado: IllegalStateException com Saldo insuficiente.
- Verificação: confirmar exceção e saldo ainda em 10.000.
- Por que importa: garante que uma operação inválida não reduza o estoque.

### CT013 — Transferência bem-sucedida entre depósitos distintos

- Requisito: RF-013 | Tipo: funcional | Prioridade: crítica.
- Pré-condição: depósitos CD-01 e CD-02; origem com 10.000.
- Entrada: transferir 5.000, chave CHAVE-1.
- Ação: chamar transferir.
- Esperado: origem com 5.000, destino com 5.000 e objeto Transferencia retornado.
- Verificação: validar chave e os dois saldos.
- Por que importa: verifica o débito e o crédito correspondentes em uma transferência bem-sucedida.
- Limitação: este cenário não testa atomicidade diante de uma falha intermediária. Essa garantia exige um teste adicional com injeção de falha.
- Histórico e correção: a transferência era interrompida pelo erro na ordenação da saída. O resultado Maven/JUnit após a correção registra CT013 como aprovado; consulte `resultado-apos-correcao.txt`.

### CT014 — Idempotência de transferência usando a mesma chave

- Requisito: RF-014 | Tipo: funcional | Prioridade: crítica.
- Pré-condição: mesma configuração do CT013.
- Entrada: executar duas vezes a transferência de 5.000 com a mesma chave e conteúdo.
- Ação: chamar transferir duas vezes.
- Esperado: segunda chamada retorna a transferência original sem novo débito/crédito.
- Verificação: mesma instância/resultado, origem e destino com 5.000 cada e apenas uma transferência registrada.
- Por que importa: evita duplicidade quando uma requisição é repetida.
- Limitação: o documento também prevê rejeitar mesma chave com conteúdo diferente (RN-016); isso não está coberto pelo CT014 original.
- Histórico e correção: o erro na saída impedia concluir a primeira transferência. O resultado Maven/JUnit após a correção registra CT014 como aprovado; consulte `resultado-apos-correcao.txt`.

### CT015 — Cálculo do custo médio ponderado

- Requisito: RF-020 | Tipo: funcional | Prioridade: alta.
- Pré-condição: dois lotes do mesmo SKU/depósito.
- Entrada: 10 unidades a R$ 10,00 e 30 unidades a R$ 20,00.
- Ação: chamar custoMedio.
- Cálculo esperado: (10 × 10,00 + 30 × 20,00) ÷ (10 + 30) = 17,50.
- Esperado: R$ 17,50, com duas casas decimais e arredondamento HALF_UP.
- Verificação: comparar numericamente com 17.50.
- Por que importa: itens em quantidades diferentes devem influenciar proporcionalmente o custo médio.
- Ocorrência anterior: a média simples retornava R$ 15,00 para esses dados.
- Correção aplicada: somar quantidade multiplicada pelo custo de cada lote com saldo positivo e dividir pela quantidade total restante.
- Resultado da execução Maven/JUnit após a correção: CT015 passou, conforme `resultado-apos-correcao.txt`.

## Histórico e resultado após as correções

O resultado anterior do executor Java independente, registrado em `resultado-antes-correcao.txt`, aponta 11 casos aprovados e quatro falhas (CT011, CT013, CT014 e CT015). Esse arquivo não é um relatório Maven/JUnit. Após as correções, `resultado-apos-correcao.txt` registra uma execução Maven/JUnit com 15 testes, zero falhas, zero erros e zero testes ignorados, compilada com `--release 17`.

| Caso | Resultado anterior (executor independente) | Resultado Maven/JUnit após a correção |
|---|---|---|
| CT011 | Erro na ordenação dos lotes | Aprovado |
| CT013 | Transferência interrompida pela saída | Aprovado |
| CT014 | Primeira transferência interrompida | Aprovado |
| CT015 | Custo médio diferente do esperado | Aprovado |

O resultado acima descreve a execução automatizada registrada; não afirma aprovação manual por testadores. O relatório técnico registra os cenários, as ocorrências iniciais, as correções e as evidências da reexecução.

As evidências Maven/JUnit devem acompanhar o relatório. Os arquivos do Surefire ficam em `target/surefire-reports/`. Os resultados do executor independente devem permanecer identificados como tais.

Para executar a suíte e salvar a saída do terminal no PowerShell:

```powershell
mvn clean test 2>&1 | Tee-Object -FilePath ".\resultado-apos-correcao.txt"
```

O guia descreve os cenários e o histórico; os relatórios gerados na execução são as evidências dos resultados.

## Como apresentar

Para cada CT, mostre: código e objetivo, pré-condição, dados de entrada, ação, resultado esperado e resultado obtido. Para qualquer falha, apresente a mensagem/stack trace e explique o impacto. Não altere o resultado obtido para “passou” sem corrigir o código e executar novamente.

## Suíte ampliada

A versão 2.0.0 acrescenta critérios de aceitação, regras, inventário, concorrência, recuperação, HTTP e desempenho. Os registros de 15 aprovações acima são históricos. Consulte `docs/RELATORIO_EXECUCAO.md` para as 159 execuções JUnit desta entrega e suas limitações.
