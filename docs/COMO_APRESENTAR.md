# Roteiro de execução e apresentação

## Preparar

1. Abra a pasta do `pom.xml` no VS Code.
2. Execute `.\mvnw.cmd clean verify` no PowerShell.
3. Confira a linha com o total de testes, zero falhas/erros e `BUILD SUCCESS`.
4. Execute `java -jar .\target\controle-estoque.jar --memoria`.
5. Abra `http://localhost:8080` e entre com a conta de demonstração descrita no README.

O modo `--memoria` permite reiniciar a apresentação com dados conhecidos, sem alterar o arquivo de uma execução persistente.

## Demonstrar na interface

| Etapa | Ação | Resultado esperado |
| --- | --- | --- |
| Posição | Consultar SKU-001 em CD-01 | Físico 20,000; reservado 0,000; disponível 20,000 |
| Reserva | Reservar 5 unidades com expiração futura | Físico 20,000; reservado 5,000; disponível 15,000 |
| Saída inválida | Tentar saída de 15,001 | Rejeição; os três saldos anteriores permanecem |
| Saída válida | Retirar 3 unidades | Físico 17,000; reservado 5,000; disponível 12,000 |
| Atendimento | Atender a reserva de 5 | Físico 12,000; reservado 0,000; disponível 12,000; reserva ATENDIDA |
| Transferência | Transferir 4 para LOJA-01 | CD-01 = 8,000; LOJA-01 = 4,000; total global 12,000 |
| Custo | Consultar o custo na loja | R$ 12,50; validade e fornecedor preservados |
| Ajuste | Gestor acrescenta 1 no lote do CD-01, com motivo válido | CD-01 = 9,000; movimento AJUSTE e evento registrados |
| Inventário | Abrir, iniciar, contar 8 no lote do SKU-001, revisar e aprovar | Inventário CONCLUIDO; um ajuste de −1; CD-01 = 8,000 |
| Relatório | Filtrar SKU-001 e o período da apresentação | Movimentos nas bordas incluídos; valores e perdas disponíveis; exportação CSV |
| Auditoria | Consultar eventos | Operações bem-sucedidas e tentativa rejeitada, com ator e correlação |
| Permissões | Entrar como auditor | Consulta disponível; mutações bloqueadas também no servidor |

Use produtos/lotes novos para demonstrar entradas adicionais, pois a repetição de lote para o mesmo produto e fornecedor é rejeitada.

## Demonstração automática no terminal

```powershell
java -jar .\target\controle-estoque.jar --demo
```

Saídas esperadas do roteiro determinístico:

```text
Saldo físico: 20.000 | disponível: 20.000
Após reservar 5: físico=20.000 | reservado=5.000 | disponível=15.000
Após sair 3: físico=17.000 | disponível=12.000
Após atender a reserva: físico=12.000 | disponível=12.000
Após transferir 4: CD-01=8.000 | LOJA-01=4.000
Custo médio na loja: R$ 12.50
Inventário: CONCLUIDO | saldo final CD-01=8.000
Alertas gerados: 3
```

O terminal usa a representação decimal do Java para facilitar a comparação com os testes; a interface formata quantidades, moeda e datas em pt-BR. O relógio dessa demonstração é fixo; o servidor usa a data real no fuso America/Sao_Paulo.

## Explicar os testes

Para cada caso apresentado: cite o ID e a regra, mostre a preparação e a entrada, execute a ação, compare esperado e obtido e explique os efeitos colaterais que devem permanecer intactos quando há rejeição. Use o relatório desta entrega para o resultado observado e preserve os arquivos históricos das falhas iniciais.
