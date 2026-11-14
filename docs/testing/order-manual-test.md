
# Testes manuais: endpoints de pedidos

Registro dos testes manuais feitos no Postman contra os endpoints de pedidos do CoffeePilot: registro com baixa automática de ingredientes, consulta e listagem, mudança de status e cancelamento com devolução de estoque. O documento cobre a consolidação do consumo, a atomicidade do registro, o erro estruturado de estoque insuficiente, a numeração por estabelecimento, as regras de cancelamento por status e por perfil, as permissões, o isolamento entre estabelecimentos e a consistência entre pedidos, itens e movimentações de estoque.

## Ambiente

| Item | Valor |
|---|---|
| API | `http://localhost:8080` |
| Backend | Spring Boot 4.1.1, Java 25 |
| Banco | PostgreSQL 17 (Docker, porta 5433) |
| Ferramenta | Postman |
| Autenticação | JWT (HS256), enviado como `Authorization: Bearer <token>` |

## Endpoints testados

| Método | Rota | Perfis permitidos |
|---|---|---|
| POST | `/orders` | OWNER, MANAGER, ATTENDANT |
| GET | `/orders?status=` | OWNER, MANAGER, ATTENDANT |
| GET | `/orders/{orderId}` | OWNER, MANAGER, ATTENDANT |
| PATCH | `/orders/{orderId}/start` | OWNER, MANAGER, ATTENDANT |
| PATCH | `/orders/{orderId}/complete` | OWNER, MANAGER, ATTENDANT |
| PATCH | `/orders/{orderId}/cancel` | OWNER e MANAGER em qualquer pedido não concluído; ATTENDANT só em pedidos pendentes |

O STOCKIST não acessa pedidos. Fluxo de status: `PENDING → IN_PREPARATION → COMPLETED`, com `CANCELLED` saindo de `PENDING` e de `IN_PREPARATION`. Concluído e cancelado são estados finais. O corpo do cancelamento é opcional.

## O que os testes provam

- **Registro e baixa na mesma transação:** o pedido é gravado com o nome, o preço e o total do momento da venda, e o consumo dos ingredientes vira uma movimentação `CONSUMPTION` por ingrediente.
- **Consumo consolidado:** quando vários produtos do pedido usam o mesmo ingrediente, o consumo é somado antes da baixa. Cada produto sozinho caberia no saldo, e o pedido só falha pela soma.
- **Produto sem receita:** entra no pedido normalmente, sai com `stockTracked: false` e não gera movimentação.
- **Atomicidade:** pedido recusado por estoque insuficiente ou por ingrediente inativo não deixa pedido, itens, movimentações nem saldo alterado, e o número do pedido volta junto.
- **Erro estruturado de estoque:** o 409 lista todos os ingredientes com problema de uma vez, com nome, unidade, quantidade pedida, quantidade disponível e o motivo (`INSUFFICIENT_STOCK` ou `INACTIVE_INGREDIENT`).
- **Numeração por estabelecimento:** o número do pedido é um contador próprio, sem buracos, e reinicia por estabelecimento. Pedidos cancelados continuam ocupando o número.
- **Itens na ordem do id do produto:** o `POST` e o `GET` devolvem os itens na mesma ordem, sem depender da collation do banco.
- **Status:** só as transições permitidas funcionam, e as proibidas devolvem 409 sem alterar o pedido. Iniciar e concluir não mexem no estoque.
- **Cancelamento:** em `PENDING` devolve exatamente o que as movimentações do pedido consumiram. Em `IN_PREPARATION` não devolve, exige justificativa e perfil OWNER ou MANAGER. Pedido concluído ou já cancelado não pode ser cancelado, e o estoque não volta duas vezes.
- **Ingrediente desativado depois do pedido:** não impede o cancelamento nem a devolução do estoque.
- **Estorno manual de consumo:** é recusado, porque o estoque de um pedido só volta pelo cancelamento do pedido.
- **Permissões:** o STOCKIST recebe 403 em todas as rotas, e o 403 vem antes de qualquer consulta ao pedido. O ATTENDANT cancela só pedidos pendentes.
- **Isolamento:** um estabelecimento não vê, não altera nem usa produtos de outro, e as respostas (404 e 400) são idênticas às de um recurso inexistente, sem revelar o estado do pedido alheio.

---

## Setup

O banco foi recriado do zero antes deste módulo, então todas as contas e dados foram criados de novo pela API. Foram usadas as mesmas contas e o mesmo padrão dos módulos anteriores.

| # | O que foi feito | Resultado |
|---|---|---|
| S1 a S6 | `POST /auth/register` dos estabelecimentos A e B, `POST /employees` para MANAGER, ATTENDANT e STOCKIST da A, e login das cinco contas | Cinco contas e cinco tokens ✅ |
| S7 a S11 | `POST /ingredients`: Café, Leite, Ovo, Chocolate em pó (A) e Leite (B), com saldo inicial | Cinco ingredientes ✅ |
| S12 a S19 | `POST /products`: Cappuccino, Espresso, Brownie, Água, Mocha e Suco (A, este desativado em seguida) e Cappuccino (B) | Sete produtos ✅ |
| S20 a S24 | `PUT /products/{id}/recipe` dos produtos com receita | Nove itens de receita ✅ |

Conferência no banco: 5 usuários, 5 ingredientes, 7 produtos, 9 itens de receita, 5 movimentações (as `INITIAL`) e 0 pedidos.

### Dados de partida

| Ingrediente | id | Unidade | Saldo inicial |
|---|---|---|---|
| Café (A) | 1 | G | 1000 |
| Leite (A) | 2 | ML | 3000 |
| Ovo (A) | 3 | UNIT | 20 |
| Chocolate em pó (A) | 4 | G | 500 |
| Leite (B) | 5 | ML | 1000 |

| Produto | id | Preço | Receita |
|---|---|---|---|
| Cappuccino (A) | 1 | 12.50 | Café 30, Leite 150 |
| Espresso (A) | 2 | 8.00 | Café 18 |
| Brownie (A) | 3 | 9.00 | Ovo 2, Chocolate 40 |
| Água (A) | 4 | 5.00 | sem receita |
| Mocha (A) | 5 | 15.00 | Café 30, Leite 100, Chocolate 20 |
| Suco (A, inativo) | 6 | 7.00 | sem receita |
| Cappuccino (B) | 7 | 13.00 | Leite (B) 200 |

### Ids de pedidos

| id | Nº | Estabelecimento | Origem |
|---|---|---|---|
| 1 | 1 | A | O1 |
| 2 | 2 | A | O2 |
| 3 | 3 | A | O3 |
| 4 | 4 | A | O4 |
| 5 | 1 | B | O5 |
| 10 | 5 | A | N11 |
| 11 | 6 | A | C11 |
| 12 | 7 | A | R1 |

---

## Bloco 1: Criação

`POST /orders`. Os itens saem em ordem crescente do id do produto.

| # | Token | Corpo | Esperado | Resultado |
|---|---|---|---|---|
| O1 | ATTENDANT A | Mesa 5, "Sem açúcar", 2 Cappuccinos e 1 Espresso | 201, nº 1, total 33.00 | 201 ✅ `createdByName` "Atendente A", ambos `stockTracked: true` |
| O2 | MANAGER A | 3 Brownies e 2 Águas | 201, nº 2, total 37.00 | 201 ✅ Brownie `true` e **Água `false`** no mesmo pedido, mesa e observações nulas |
| O3 | OWNER A | 1 Água | 201, nº 3, total 5.00 | 201 ✅ pedido só com produto sem receita |
| O4 | OWNER A | Mocha, Cappuccino e Espresso | 201, nº 4, total 35.50 | 201 ✅ itens na ordem Cappuccino, Espresso e Mocha (ids 1, 2 e 5) |
| O5 | OWNER B | 2 Cappuccinos da B | 201, **nº 1** (numeração por estabelecimento), total 26.00 | 201 ✅ |
| O6 | OWNER A e OWNER B | `GET /ingredients?size=100` | Saldos conforme o consumo | ✅ ver tabela abaixo |

Saldos conferidos no O6:

| Ingrediente | Antes | Consumo | Saldo observado |
|---|---|---|---|
| Café (A) | 1000 | O1: 78, O4: 78 | 844 ✅ |
| Leite (A) | 3000 | O1: 300, O4: 250 | 2450 ✅ |
| Ovo (A) | 20 | O2: 6 | 14 ✅ |
| Chocolate em pó (A) | 500 | O2: 120, O4: 20 | 360 ✅ |
| Leite (B) | 1000 | O5: 400 | 600 ✅ |

Conferência no banco: 8 movimentações `CONSUMPTION` (pedido 1: Café -78 e Leite -300; pedido 2: Ovo -6 e Chocolate -120; pedido 4: Café -78, Leite -250 e Chocolate -20; pedido da B: Leite -400), com os saldos encadeados e a descrição "Pedido nº N". O pedido 3 não gerou movimentação.

O O1 e o O4 mostram a consolidação: o Café do O4 (Cappuccino 30, Espresso 18 e Mocha 30) virou uma única movimentação de -78.

---

## Bloco 2: Validação do corpo

`POST /orders` com OWNER A. Todos devolvem 400 com "Dados inválidos.", e nenhum grava nada nem consome número de pedido.

| # | Corpo | Esperado | Resultado |
|---|---|---|---|
| V1 | `{}` | 400, campo `items` | 400 ✅ "A lista de itens é obrigatória." |
| V2 | `{"items":null}` | 400, campo `items` | 400 ✅ (mesma resposta) |
| V3 | `{"items":[]}` | 400, campo `items` | 400 ✅ "O pedido deve ter de 1 a 50 itens." |
| V4 | 51 itens | 400, campo `items` | 400 ✅ (mesma mensagem) |
| V5 | `{"items":[null]}` | 400 | 400 ✅ campo `items[0]`, "Item do pedido inválido." |
| V6 | item sem `productId` | 400 | 400 ✅ campo `items[0].productId`, "O produto é obrigatório." |
| V7 | `productId` 0 | 400 | 400 ✅ campo `items[0].productId`, "O produto informado é inválido." |
| V8 | item sem `quantity` | 400 | 400 ✅ campo `items[0].quantity`, "A quantidade é obrigatória." |
| V9 | `quantity` 0 | 400 | 400 ✅ "A quantidade de cada item deve estar entre 1 e 999." |
| V10 | `quantity` 1000 | 400 | 400 ✅ (mesma mensagem) |
| V11 | `tableReference` com 51 caracteres | 400, campo `tableReference` | 400 ✅ "A mesa ou comanda deve ter no máximo 50 caracteres." |
| V12 (extra) | `notes` com 501 caracteres | 400, campo `notes` | 400 ✅ "As observações devem ter no máximo 500 caracteres." |
| V13 | `GET /orders` (conferência) | 200, 4 pedidos | 200 ✅ `totalElements` 4, o mais recente continua o nº 4 |

O V4, o V11 e o V12 foram gerados fora do Postman (no PowerShell), colados no corpo da requisição. Os erros de item trazem o índice do elemento (`items[0].quantity`), como nas receitas, para o frontend apontar a linha com problema.

---

## Bloco 3: Regras de negócio e estoque insuficiente

`POST /orders` com OWNER A, exceto onde indicado. Estes casos passam pelo DTO e são barrados no service, por isso `fieldErrors` vem vazio.

| # | Corpo ou requisição | Esperado | Resultado |
|---|---|---|---|
| N1 | Cappuccino duas vezes | 400 | 400 ✅ "Produto repetido no pedido." |
| N2 | Suco (inativo) | 400 | 400 ✅ `O produto "Suco" está inativo.` |
| N3 | produto 9999 | 400 | 400 ✅ "Produto não encontrado." |
| N4 | Cappuccino da B | 400, mesma resposta do N3 | 400 ✅ "Produto não encontrado." |
| N5 | 11 Brownies | 409, dois problemas | 409 ✅ `stockIssues`: Ovo (22 contra 14) e Chocolate em pó (440 contra 360), ambos `INSUFFICIENT_STOCK` |
| N6 | 15 Cappuccinos e 25 Espressos | 409, um problema | 409 ✅ Café 900 contra 844 (cada produto sozinho caberia) |
| N7 | `GET /ingredients` e `GET /orders` | saldos intactos, 4 pedidos | ✅ Café 844, Chocolate 360, Leite 2450, Ovo 14 e 4 pedidos |
| N8 | `PATCH /ingredients/4/status` com `{"active":false}` | 200 | 200 ✅ |
| N9 | 1 Brownie (Chocolate inativo) | 409, `INACTIVE_INGREDIENT` | 409 ✅ Chocolate, `required` 40 e `available` 360 |
| N10 | 11 Brownies | 409, os dois motivos juntos | 409 ✅ Ovo `INSUFFICIENT_STOCK` e Chocolate `INACTIVE_INGREDIENT` |
| N11 | 1 Cappuccino | 201, **nº 5** e **id 10**, total 12.50 | 201 ✅ total 12.50 |
| N12 | `PATCH /ingredients/4/status` com `{"active":true}` | 200 | 200 ✅ |
| N13 | `GET /ingredients` e `GET /orders` | saldos e pedidos | ✅ Café 814, Leite 2300, Ovo 14, Chocolate 360 e 5 pedidos |

**Nota do N11:** o corpo enviado foi um Cappuccino (produto 1), total 12.50, que consome Café 30 e Leite 150 (844 → 814 e 2450 → 2300). A linha da tabela foi ajustada para refletir a requisição realmente executada. Os saldos do N13 confirmam o consumo.

Conferência no banco: 6 pedidos (ids 1 a 5 e 10), sem nenhuma linha dos quatro pedidos recusados.

O que esses resultados provam:

- O N5 e o N10 listam todos os ingredientes com problema de uma vez, em ordem crescente de id, em vez de parar no primeiro.
- O N6 prova o consumo consolidado: o Café do Cappuccino (450) e o do Espresso (450) só passam do saldo quando somados.
- O N11 mostra o número **5**, e não 9: o contador voltou junto com as transações que falharam, sem buracos. O `id` 10, por outro lado, mostra os ids 6 a 9 consumidos pelos pedidos desfeitos.
- O Chocolate inativo bloqueou só os pedidos que dependem dele, e o N11 passou normalmente.

---

## Bloco 4: Consulta e listagem

| # | Token | Requisição | Esperado | Resultado |
|---|---|---|---|---|
| G1 | OWNER A | `GET /orders/1` | 200, dados completos | 200 ✅ mesa, observações e os dois itens |
| G2 | ATTENDANT A | `GET /orders/2` (criado pelo Gerente) | 200, itens Brownie e depois Água | 200 ✅ mesma ordem do `POST` do O2 |
| G3 | MANAGER A | `GET /orders/10` | 200, nº 5 | 200 ✅ |
| G4 | OWNER A | `GET /orders/9999` | 404 | 404 ✅ "Pedido não encontrado." |
| G5 | OWNER A | `GET /orders?status=PENDING` | 200, 5 pedidos, mais recentes primeiro | 200 ✅ ids 10, 4, 3, 2, 1, sem `items` no resumo |
| G6 | OWNER A | `GET /orders?status=COMPLETED` | 200, lista vazia | 200 ✅ `totalElements` 0 |
| G7 | OWNER A | `GET /orders?size=500` | 200, `pageSize` 100 | 200 ✅ |
| G8 | OWNER A | `GET /orders?sort=total,asc` | 200, a ordem do cliente é ignorada | 200 ✅ continua do mais recente para o mais antigo |
| G9 | OWNER A | `GET /orders?page=1&size=2` | 200, pedidos 3 e 2, `totalPages` 3 | 200 ✅ |
| G10 | OWNER A | `GET /orders?status=INVALIDO` | 400 | 400 ✅ "Valor inválido para o parâmetro 'status'." |

O G2 era o teste mais importante do bloco: pela ordem de nome com collation, a Água viria antes do Brownie. O `GET` devolveu a mesma ordem do `POST` (por id do produto).

---

## Bloco 5: Mudança de status

| # | Token | Requisição | Esperado | Resultado |
|---|---|---|---|---|
| S1 | ATTENDANT A | `PATCH /orders/1/start` | 200, `IN_PREPARATION` | 200 ✅ itens e total inalterados |
| S2 | OWNER A | `PATCH /orders/1/start` | 409 | 409 ✅ "Apenas pedidos pendentes podem entrar em preparação." |
| S3 | MANAGER A | `PATCH /orders/1/complete` | 200, `COMPLETED` | 200 ✅ `completedAt` preenchido |
| S4 | OWNER A | `PATCH /orders/1/complete` | 409 | 409 ✅ "Apenas pedidos em preparação podem ser concluídos." |
| S5 | OWNER A | `PATCH /orders/1/start` | 409 | 409 ✅ pedido concluído não volta a ser preparado |
| S6 | ATTENDANT A | `PATCH /orders/2/complete` | 409 | 409 ✅ pedido pendente não pode ser concluído direto |
| S7 | OWNER A | `PATCH /orders/9999/start` | 404 | 404 ✅ "Pedido não encontrado." |
| S8 | OWNER A | `PATCH /orders/2/start` | 200, `IN_PREPARATION` | 200 ✅ |
| S9 | OWNER A | `GET /ingredients?size=100` | saldos iguais | ✅ Café 814, Leite 2300, Ovo 14, Chocolate 360 |

O S9 prova que iniciar e concluir não mexem no estoque: o consumo acontece só no registro do pedido.

---

## Bloco 6: Cancelamento

| # | Token | Requisição | Esperado | Resultado |
|---|---|---|---|---|
| C1 | ATTENDANT A | `PATCH /orders/10/cancel`, sem corpo | 200, `CANCELLED`, origem `PENDING` | 200 ✅ "Atendente A", justificativa nula |
| C2 | OWNER A | `PATCH /orders/4/cancel` com `{"reason":"  Cliente desistiu  "}` | 200, justificativa aparada | 200, mas **justificativa nula** (ver nota) |
| C3 | OWNER A | `PATCH /orders/3/cancel` (só Água) | 200, sem efeito no estoque | 200 ✅ |
| C4 | ATTENDANT A | `PATCH /orders/2/cancel` com justificativa | 403 | 403 ✅ "Acesso negado.", pedido continua em preparação |
| C5 | OWNER A | `PATCH /orders/2/cancel`, sem corpo | 400 | 400 ✅ "A justificativa é obrigatória para cancelar um pedido em preparação." |
| C6 | OWNER A | `PATCH /orders/2/cancel` com `{"reason":"   "}` | 400, mesma mensagem | 400 ✅ |
| C7 | MANAGER A | `PATCH /orders/2/cancel` com `{"reason":"Cliente foi embora"}` | 200, origem `IN_PREPARATION` | 200 ✅ justificativa gravada |
| C8 | OWNER A | `PATCH /orders/1/cancel` | 409 | 409 ✅ "Pedidos concluídos não podem ser cancelados." |
| C9 | OWNER A | `PATCH /orders/10/cancel` | 409 | 409 ✅ "O pedido já foi cancelado." |
| C10 | OWNER A | `GET /ingredients?size=100` | saldos | ✅ Café 922, Leite 2700, Ovo 14, Chocolate 380 |
| C11 | OWNER A | `POST /orders` com 1 Espresso | 201, nº 6, id 11 | 201 ✅ |
| C12 | OWNER A | `PATCH /orders/11/cancel`, Body raw JSON, `{"reason":"  Cliente desistiu  "}` | 200, justificativa "Cliente desistiu" | 200 ✅ gravada sem os espaços, Café de volta a 922 |

O que o bloco prova:

- Os cancelamentos em `PENDING` (C1, C2 e C11/C12) devolveram o estoque do que cada pedido consumiu: o pedido 10 devolveu Café 30 e Leite 150, e o pedido 4 devolveu Café 78, Leite 250 e Chocolate 20.
- O cancelamento em preparação (C7) **não** devolveu estoque: o Ovo (14) e o Chocolate do pedido 2 (120) continuam consumidos.
- O ATTENDANT recebe 403 em pedido em preparação (C4), mas cancela pedidos pendentes (C1).

Conferência no banco: o pedido 1 `COMPLETED`, os pedidos 3, 4, 10 e 11 `CANCELLED` com origem `PENDING`, e o pedido 2 `CANCELLED` com origem `IN_PREPARATION` e justificativa "Cliente foi embora". Cinco movimentações `REVERSAL` no primeiro momento (pedidos 10 e 4), todas com `reverses_movement_id` preenchido, descrição "Cancelamento do pedido nº N", e o `order_id` herdado do consumo original. Os pedidos 2 e 3 não geraram estorno.

---

## Bloco 7: Ingrediente desativado e estorno manual

| # | Token | Requisição | Esperado | Resultado |
|---|---|---|---|---|
| R1 | OWNER A | `POST /orders` com 2 Mochas | 201, nº 7, id 12, total 30.00 | 201 ✅ |
| R2 | OWNER A | `PATCH /ingredients/4/status` com `{"active":false}` | 200, Chocolate inativo | 200 ✅ saldo 340 (os 40 g do pedido já consumidos) |
| R3 | OWNER A | `PATCH /orders/12/cancel`, sem corpo | 200, o cancelamento **não fica preso** pelo ingrediente inativo | 200 ✅ `CANCELLED`, origem `PENDING` |
| R4 | OWNER A | `GET /ingredients?size=100` | Café 922, Leite 2700, Chocolate 380 | ✅ o estoque voltou, e o Chocolate continua `active: false` |
| R5 | OWNER A | `PATCH /ingredients/4/status` com `{"active":true}` | 200 | 200 ✅ |
| R6 | OWNER A | `POST /stock/movements/6/reversals` (consumo do pedido 1) | 400 | 400 ✅ "Movimentações de consumo de pedidos não podem ser estornadas manualmente. Cancele o pedido." |
| R7 | OWNER A | `POST /stock/movements/16/reversals` (estorno do pedido 10) | 400 | 400 ✅ "Um estorno não pode ser estornado." |
| R8 | OWNER A | `GET /ingredients?size=100` | saldos iguais ao R4 | ✅ Café 922, Leite 2700, Ovo 14, Chocolate 380 |

O R2 a R4 cobrem o ponto mais delicado do módulo: um ingrediente desativado depois do pedido não pode impedir o cancelamento nem o retorno do estoque. O R6 fecha a brecha de um OWNER estornar à mão um consumo e deixar o pedido inconsistente. O R6 usa o consumo do pedido 1, que está concluído e não tem outra saída de devolução.

---

## Bloco 8: Permissões e isolamento

### Permissões

Os corpos são válidos de propósito, para o 403 vir da permissão e não da validação.

| # | Token | Requisição | Esperado | Resultado |
|---|---|---|---|---|
| P1 | STOCKIST A | `POST /orders` | 403 | 403 ✅ "Acesso negado." |
| P2 | STOCKIST A | `GET /orders` | 403 | 403 ✅ |
| P3 | STOCKIST A | `GET /orders/1` | 403 | 403 ✅ |
| P4 | STOCKIST A | `PATCH /orders/1/start` | 403 | 403 ✅ |
| P5 | STOCKIST A | `PATCH /orders/1/complete` | 403 | 403 ✅ |
| P6 | STOCKIST A | `PATCH /orders/1/cancel` | 403 | 403 ✅ |
| P7 | sem token | `GET /orders` | 401 | 401 ✅ "Autenticação necessária." |
| P8 | sem token | `POST /orders` | 401 | 401 ✅ |
| P9 | OWNER A | `GET /orders/1` | 200, `COMPLETED`, sem alteração | 200 ✅ |

Os P4 a P6 usam o pedido concluído de propósito: se o 403 não viesse antes de qualquer consulta ao pedido, a resposta seria 409. As respostas do P8 e do P9 foram registradas com os rótulos trocados, mas as duas são as esperadas.

### Isolamento entre estabelecimentos

| # | Token | Requisição | Esperado | Resultado |
|---|---|---|---|---|
| I1 | OWNER B | `GET /orders/1` (pedido da A) | 404 | 404 ✅ "Pedido não encontrado." |
| I2 | OWNER B | `PATCH /orders/1/start` | 404 | 404 ✅ |
| I3 | OWNER B | `PATCH /orders/1/cancel` | **404**, e não 409 | 404 ✅ o estado do pedido alheio não vaza |
| I4 | OWNER B | `POST /orders` com produto da A | 400 | 400 ✅ "Produto não encontrado." |
| I5 | OWNER B | `GET /orders` | 200, só o pedido da B | 200 ✅ `totalElements` 1, id 5 |
| I6 | OWNER A | `GET /orders/5` (pedido da B) | 404 | 404 ✅ |
| I7 | OWNER A | `GET /orders` | 200, 7 pedidos, sem o da B | 200 ✅ ids 12, 11, 10, 4, 3, 2 e 1 |
| I8 | OWNER B | `PATCH /orders/5/cancel`, sem corpo | 200, `CANCELLED` | 200 ✅ "Dono B", origem `PENDING` |
| I9 | OWNER B | `GET /ingredients` | Leite 1000 | ✅ 600 mais os 400 devolvidos |
| I10 | OWNER A | `GET /ingredients?size=100` | sem mudança | ✅ Café 922, Leite 2700, Ovo 14, Chocolate 380 |

As respostas de isolamento (404 e 400) são idênticas às de um pedido ou produto inexistente, então o cliente não distingue "existe em outro estabelecimento" de "não existe". O I8 e o I9 mostram que o isolamento não impede o uso normal, e o I10 prova que nada disso tocou o estoque da A.

---

## Verificações finais no banco

**V1: o saldo de cada ingrediente bate com a soma dos deltas e com o saldo da última movimentação**

```sql
SELECT i.id, i.name, i.current_quantity,
       COALESCE(SUM(m.quantity_delta), 0) AS soma_deltas,
       (SELECT m2.balance_after FROM stock_movements m2
        WHERE m2.ingredient_id = i.id ORDER BY m2.id DESC LIMIT 1) AS ultimo_saldo
FROM ingredients i
LEFT JOIN stock_movements m ON m.ingredient_id = i.id
GROUP BY i.id, i.name, i.current_quantity
ORDER BY i.id;
```

| id | name | current_quantity | soma_deltas | ultimo_saldo |
|---|---|---|---|---|
| 1 | Café | 922.000 | 922.000 | 922.000 |
| 2 | Leite | 2700.000 | 2700.000 | 2700.000 |
| 3 | Ovo | 14.000 | 14.000 | 14.000 |
| 4 | Chocolate em pó | 380.000 | 380.000 | 380.000 |
| 5 | Leite (B) | 1000.000 | 1000.000 | 1000.000 |

**V2: o total do pedido é a soma dos subtotais dos itens** (regra que fica no service, e não no banco)

```sql
SELECT o.id, o.total, SUM(i.subtotal) AS soma_itens
FROM orders o
JOIN order_items i ON i.order_id = o.id
GROUP BY o.id, o.total
HAVING o.total <> SUM(i.subtotal);
```

Resultado: 0 linhas ✅

**V3: o contador de numeração acompanha o maior número registrado**

```sql
SELECT s.coffee_shop_id, s.last_number,
       MAX(o.order_number) AS maior_numero, COUNT(o.id) AS pedidos
FROM order_sequences s
JOIN orders o ON o.coffee_shop_id = s.coffee_shop_id
GROUP BY s.coffee_shop_id, s.last_number
ORDER BY s.coffee_shop_id;
```

| coffee_shop_id | last_number | maior_numero | pedidos |
|---|---|---|---|
| 1 | 7 | 7 | 7 |
| 2 | 1 | 1 | 1 |

**V3.1: não existem números ausentes entre 1 e `last_number`**

A consulta abaixo verifica individualmente todos os números esperados na sequência de cada estabelecimento. O resultado observado foi **0 linhas**.

```sql
SELECT
    s.coffee_shop_id,
    numbers.order_number AS missing_number
FROM order_sequences s
CROSS JOIN LATERAL generate_series(1, s.last_number)
    AS numbers(order_number)
LEFT JOIN orders o
    ON o.coffee_shop_id = s.coffee_shop_id
   AND o.order_number = numbers.order_number
WHERE o.id IS NULL
ORDER BY s.coffee_shop_id, numbers.order_number;
```

Resultado:

```text
 coffee_shop_id | missing_number
----------------+----------------
(0 rows)
```

A V3 confirma que o contador acompanha o maior número registrado. A V3.1 complementa essa verificação e confirma que não há lacunas internas nas sequências atuais dos estabelecimentos.

**V4: consumos e estornos por pedido**

```sql
SELECT o.id AS pedido, o.status, o.cancelled_from_status,
       COUNT(*) FILTER (WHERE m.type = 'CONSUMPTION') AS consumos,
       COUNT(*) FILTER (WHERE m.type = 'REVERSAL') AS estornos
FROM orders o
JOIN stock_movements m ON m.order_id = o.id
GROUP BY o.id, o.status, o.cancelled_from_status
ORDER BY o.id;
```

| pedido | status | origem | consumos | estornos |
|---|---|---|---|---|
| 1 | COMPLETED | (nulo) | 2 | 0 |
| 2 | CANCELLED | IN_PREPARATION | 2 | 0 |
| 4 | CANCELLED | PENDING | 3 | 3 |
| 5 | CANCELLED | PENDING | 1 | 1 |
| 10 | CANCELLED | PENDING | 2 | 2 |
| 11 | CANCELLED | PENDING | 1 | 1 |
| 12 | CANCELLED | PENDING | 3 | 3 |

O pedido 3 (só Água) não aparece, porque nunca teve movimentação. Todo pedido cancelado a partir de `PENDING` tem tantos estornos quanto consumos, e o concluído e o cancelado em preparação não têm estorno.

**V5: cada estorno devolve exatamente o que o consumo original tirou**

```sql
SELECT r.id AS estorno
FROM stock_movements r
JOIN stock_movements c ON c.id = r.reverses_movement_id
WHERE r.type = 'REVERSAL'
  AND c.type = 'CONSUMPTION'
  AND (r.order_id IS DISTINCT FROM c.order_id
       OR r.ingredient_id <> c.ingredient_id
       OR r.quantity_delta <> -c.quantity_delta);
```

Resultado: 0 linhas ✅

**V6: nenhuma movimentação ligada a pedido de outro estabelecimento**

```sql
SELECT m.id
FROM stock_movements m
JOIN orders o ON o.id = m.order_id
WHERE o.coffee_shop_id <> m.coffee_shop_id;
```

Resultado: 0 linhas ✅

Soma das movimentações por pedido (conferência feita durante o Bloco 7): os pedidos totalmente estornados (4, 10, 11 e 12) somam zero, e os que não devolveram estoque mantêm o consumo (pedido 1: -378, pedido 2: -126 e pedido 5: -400, este da B e ainda pendente naquele momento).

---

## Resumo

| Bloco | Testes | Resultado |
|---|---|---|
| Setup | 24 passos | ✅ |
| 1. Criação | 6 | ✅ |
| 2. Validação do corpo | 13 (11 planejados, 1 extra e 1 conferência) | ✅ |
| 3. Regras de negócio e estoque insuficiente | 13 | ✅ |
| 4. Consulta e listagem | 10 | ✅ |
| 5. Mudança de status | 9 | ✅ |
| 6. Cancelamento | 12 (10 planejados e 2 de repetição do C2) | ✅ |
| 7. Ingrediente desativado e estorno manual | 8 | ✅ |
| 8. Permissões e isolamento | 19 | ✅ |
| Verificações finais | 7 (incluindo V3.1) | ✅ |
| **Total** | **90 testes + 7 verificações (mais 24 passos de setup)** | **Nenhum defeito de comportamento encontrado** |
