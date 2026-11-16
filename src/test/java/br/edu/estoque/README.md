# Testes do controle de estoque

- `ServicoEstoqueTest`: CT001–CT015, preservados e corrigidos nos dados de entrada necessários.
- `CriteriosAceitacaoTest`: CA-001–CA-024 do PDF.
- `RegrasNegocioTest`: limites, reservas, alertas, custo, permissões e repetição segura.
- `InventarioTest`: estados, contagens, aprovação e conflitos com movimentações.
- `ConcorrenciaTest`: disputas por saldo e transferências de vários itens.
- `PersistenciaTest`: reinício, histórico e arquivo corrompido.
- `ServidorHttpTest`: autenticação, autorização, contratos HTTP e interface servida.
- `DesempenhoTest`: P95 de saldo e movimentação com 100 mil lotes.

Execute `./mvnw clean verify` ou `.\mvnw.cmd clean verify` na raiz do projeto.

`BaseTeste` e `RelogioAjustavel` isolam os cenários sem depender da data real. `ExecucaoCT001aCT015` é um executor independente auxiliar.
