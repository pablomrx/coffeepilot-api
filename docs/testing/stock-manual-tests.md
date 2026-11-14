# Testes manuais: endpoints de estoque

Registro dos testes manuais feitos no Postman contra os endpoints de estoque do CoffeePilot: entrada, ajuste, estorno e histórico de movimentações. O documento cobre regras de negócio, validação, permissões, isolamento entre estabelecimentos, estado do banco e a consistência entre o saldo dos ingredientes e o histórico de movimentações.

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
| POST | `/ingredients/{ingredientId}/stock/entries` | OWNER, MANAGER, STOCKIST |
| POST | `/ingredients/{ingredientId}/stock/adjustments` | OWNER, MANAGER, STOCKIST |
| GET | `/ingredients/{ingredientId}/stock/movements` | OWNER, MANAGER, STOCKIST |
| POST | `/stock/movements/{movementId}/reversals` | OWNER, MANAGER |

Todos os resultados abaixo são os observados na execução. Onde a execução foi diferente do plano original, está anotado.

## O que os testes provam

- **Saldo por movimentação:** cada entrada, ajuste e estorno cria uma movimentação com `balanceAfter` encadeado, e o saldo do ingrediente acompanha na mesma operação.
- **Validação:** quantidades zero, negativas, com mais de 3 casas decimais ou com mais de 11 dígitos inteiros são rejeitadas com 400 e o campo correto.
- **Ajuste:** o motivo e a justificativa são obrigatórios, e perda, vencimento e dano exigem variação negativa.
- **Saldo:** operação que deixaria o saldo negativo (409) ou acima do máximo (400) é barrada sem alterar nada.
- **Estorno:** exige justificativa, cria uma movimentação `REVERSAL` com delta oposto, e não pode ser repetido, aplicado a outro estorno ou ao saldo inicial.
- **Permissões:** ATTENDANT não acessa o estoque, e STOCKIST não estorna. As permissões são verificadas no backend.
- **Isolamento:** um estabelecimento não enxerga nem altera ingredientes e movimentações de outro, e a resposta (404) é idêntica à de um recurso inexistente.
- **Sessão:** o estado do banco vale mais que o token antigo (usuário inativo e cargo rebaixado).
- **Consistência:** ao final, o saldo de cada ingrediente bate com o `balance_after` da última movimentação e com a soma dos deltas.

---

## Setup

Contas do estabelecimento A: OWNER, MANAGER, STOCKIST e ATTENDANT. Conta do estabelecimento B: OWNER. Os ingredientes foram inseridos por SQL, junto com a movimentação `INITIAL` correspondente, porque o cadastro de ingredientes pela API ainda não existe.

| # | O que foi feito | Resultado |
|---|---|---|
| S1 | `POST /auth/register` do estabelecimento A (OWNER) e login | Conta criada, token em `tokenOwnerA` ✅ |
| S2 | Criação de MANAGER, STOCKIST e ATTENDANT via `POST /employees` e login de cada um | 201 nas três, três tokens salvos ✅ |
| S3 | `POST /auth/register` do estabelecimento B (OWNER) e login | Conta criada, token em `tokenOwnerB` ✅ |
| S4 | SQL: `Café` (G), estabelecimento A, saldo 1000, mínimo 200, com `INITIAL` | Inserido ✅ |
| S5 | SQL: `Ovo` (UNIT), estabelecimento A, saldo 10, com `INITIAL` | Inserido ✅ |
| S6 | SQL: `Leite` (ML), estabelecimento B, saldo 500, com `INITIAL` | Inserido ✅ |
| S7 | SQL: `Açúcar` (G), estabelecimento A, inativo, sem movimentações | Inserido ✅ |

### Ids usados

O primeiro `INSERT` de um teste anterior de constraint consumiu o id 1 da identity, por isso os ids de ingredientes e movimentações não começam em 1.

| Variável | Valor | Descrição |
|---|---|---|
| `idCafe` | 2 | Café, estabelecimento A |
| `idOvo` | 3 | Ovo, estabelecimento A |
| `idLeite` | 4 | Leite, estabelecimento B |
| `idAcucar` | 5 | Açúcar, estabelecimento A, inativo |
| `idInitialCafe` | 5 | Movimentação `INITIAL` do Café |
| `idInitialOvo` | 6 | Movimentação `INITIAL` do Ovo |
| `idInitialLeite` | 7 | Movimentação `INITIAL` do Leite |

Usuários: 1 `owner.a`, 2 `manager.a`, 3 `stockist.a`, 4 `attendant.a` (estabelecimento 1) e 5 `owner.b` (estabelecimento 2).

---

## Bloco 1: Entrada

`POST /ingredients/2/stock/entries`. Saldo inicial do Café: 1000.

| # | Token | Corpo | Esperado | Resultado |
|---|---|---|---|---|
| E1 | OWNER A | `{"quantity": 100, "description": "Compra de café"}` | 201, `ENTRY`, saldo 1100 | 201 ✅ (id 8, `createdByName` "Dono A") |
| E2 | MANAGER | `{"quantity": 50.5}` | 201, saldo 1150.5, `description` nulo | 201 ✅ (id 9) |
| E3 | STOCKIST | `{"quantity": 49.5, "description": "   "}` | 201, saldo 1200, espaços viram `null` | 201 ✅ (id 10) |
| E4 | OWNER A | `{"quantity": 0}` | 400, campo `quantity` | 400 ✅ "A quantidade deve ser maior que zero." |
| E5 | OWNER A | `{"quantity": -5}` | 400, campo `quantity` | 400 ✅ (mesma mensagem) |
| E6 | OWNER A | `{"quantity": 1.2345}` | 400, campo `quantity` | 400 ✅ "até 11 dígitos inteiros e 3 decimais" |
| E7 | OWNER A | `{"quantity": 100000000000}` | 400, campo `quantity` | 400 ✅ (mesma mensagem, 12 dígitos inteiros) |

Conferência no banco: saldo do Café `1200.000` e quatro movimentações (`INITIAL` e três `ENTRY`) com `balance_after` encadeado (1000, 1100, 1150.5, 1200). As quatro falhas não deixaram rastro.

---

## Bloco 2: Ajuste

`POST /ingredients/2/stock/adjustments`. Saldo antes do bloco: 1200.

| # | Token | Corpo | Esperado | Resultado |
|---|---|---|---|---|
| A1 | STOCKIST | `{"quantityDelta": -200, "reason": "LOSS", "justification": "Derramou ao preparar"}` | 201, saldo 1000 | 201 ✅ (id 11, "Estoquista A") |
| A2 | MANAGER | `{"quantityDelta": 300, "reason": "COUNT_DIFFERENCE", "justification": "Contagem física"}` | 201, saldo 1300 | 201 ✅ (id 12) |
| A3 | OWNER A | `{"quantityDelta": 10, "reason": "LOSS", "justification": "Erro de sinal"}` | 400 | 400 ✅ "Perda, vencimento e dano exigem uma variação negativa." |
| A4 | OWNER A | `{"quantityDelta": 10, "reason": "EXPIRED", "justification": "Erro de sinal"}` | 400 | 400 ✅ (mesma mensagem) |
| A5 | OWNER A | `{"quantityDelta": -10, "justification": "Sem motivo"}` | 400, campo `reason` | 400 ✅ "O motivo é obrigatório." |
| A6 | OWNER A | `{"quantityDelta": -10, "reason": "XYZ", "justification": "Motivo inválido"}` | 400, corpo ilegível | 400 ✅ "Corpo da requisição inválido ou ilegível." |
| A7 | OWNER A | `{"quantityDelta": -10, "reason": "OTHER"}` | 400, campo `justification` | 400 ✅ "A justificativa é obrigatória." |
| A8 | OWNER A | `{"quantityDelta": 0, "reason": "OTHER", "justification": "Sem variação"}` | 400 | 400 ✅|

Conferência no banco: saldo `1300.000` e seis movimentações. A3 a A8 não deixaram rastro.

---

## Bloco 3: Saldo insuficiente e limites

Saldo do Café: 1300.

| # | Requisição | Corpo | Esperado | Resultado |
|---|---|---|---|---|
| L1 | Ajuste, OWNER A | `{"quantityDelta": -5000, "reason": "LOSS", "justification": "Perda maior que o saldo"}` | 409 | 409 ✅ "Saldo insuficiente: a operação deixaria o estoque negativo." |
| L2 | Entrada, OWNER A | `{"quantity": 99999999999}` | 400 | 400 ✅ "O saldo resultante excede o valor máximo permitido." |

Conferência no banco: saldo `1300.000` e 6 movimentações (nada novo). O L2 passa pelo `@Digits` e é barrado pelo service, pelo saldo resultante.

---

## Bloco 4: Estorno

`POST /stock/movements/{id}/reversals`, onde `{id}` é o id da **movimentação** a estornar.

| # | Token | Rota (id) | Corpo | Esperado | Resultado |
|---|---|---|---|---|---|
| R1 | MANAGER | 8 | `{"description": "Entrada registrada em duplicidade"}` | 201, delta -100, saldo 1200, `reversesMovementId` 8 | 201 ✅ (id 13) |
| R2 | OWNER A | 11 | `{"description": "Ajuste feito por engano"}` | 201, delta +200, saldo 1400, `reversesMovementId` 11 | 201 ✅ (id 14) |
| R3 | OWNER A | 8 | `{"description": "Tentativa repetida"}` | 409 | 409 ✅ "Esta movimentação já foi estornada." |
| R4 | OWNER A | 13 (estorno do R1) | `{"description": "Estornar estorno"}` | 400 | 400 ✅ "Um estorno não pode ser estornado." |
| R5 | OWNER A | 5 (`INITIAL`) | `{"description": "Estornar saldo inicial"}` | 400 | 400 ✅ "O saldo inicial não pode ser estornado. Registre um ajuste com o motivo correção." |
| R6 | OWNER A | 9 | `{}` | 400, campo `description` | 400 ✅ "A justificativa do estorno é obrigatória." |
| R7 | OWNER A | 9 | `{"description": "   "}` | 400, campo `description` | 400 ✅ (mesma mensagem) |
| R8 | OWNER A | 9999 | `{"description": "Inexistente"}` | 404 | 404 ✅ "Movimentação não encontrada." |

**Nota sobre o R2:** o corpo enviado no Postman foi o do R3, então a descrição gravada no banco é "Tentativa repetida", e não "Ajuste feito por engano". O resultado do teste (201, delta e saldo) é válido.

Conferência no banco: saldo `1400.000` e oito movimentações. Os ids 13 e 14 são `REVERSAL` com `reverses_movement_id` 8 e 11 e `description` preenchido. Isso também comprova o caminho feliz do CHECK `ck_stock_movements_reversal_requires_description`. A movimentação 9 permaneceu sem estorno.

Evolução do saldo: 1300 → R1: 1200 → R2: 1400. R3 a R8 não alteraram o saldo.

---

## Bloco 5: Permissões

| # | Token | Requisição | Esperado | Resultado |
|---|---|---|---|---|
| P1 | ATTENDANT | `POST /ingredients/2/stock/entries` | 403 | 403 ✅ "Acesso negado." |
| P2 | ATTENDANT | `POST /ingredients/2/stock/adjustments` | 403 | 403 ✅ |
| P3 | STOCKIST | `POST /stock/movements/9/reversals` | 403 | 403 ✅ (movimentação 9 intacta) |
| P4 | Sem token | `GET /ingredients/2/stock/movements` | 401 | 401 ✅ "Autenticação necessária." |

Conferência no banco: saldo `1400.000` e 8 movimentações.

---

## Bloco 6: Isolamento entre estabelecimentos

| # | Token | Requisição | Esperado | Resultado |
|---|---|---|---|---|
| I1 | OWNER B | `POST /ingredients/2/stock/entries` (Café, da A) | 404 | 404 ✅ "Ingrediente não encontrado." |
| I2 | OWNER B | `POST /stock/movements/9/reversals` (movimentação da A) | 404 | 404 ✅ "Movimentação não encontrada." |
| I3 | OWNER A | `POST /ingredients/4/stock/entries` (Leite, da B) | 404 | 404 ✅ "Ingrediente não encontrado." |

As mensagens são idênticas às de um recurso inexistente, então o cliente não distingue "existe em outro estabelecimento" de "não existe". Conferência no banco: Café `1400.000`, Leite `500.000` e 9 movimentações no total (8 do Café e 1 do Leite).

---

## Bloco 7: Histórico

| # | Token | Requisição | Esperado | Resultado |
|---|---|---|---|---|
| H1 | STOCKIST | `GET /ingredients/2/stock/movements` | 200, 8 itens em ordem decrescente | 200 ✅ ids 14, 13, 12, 11, 10, 9, 8, 5; `totalElements` 8; `createdByName` em todos; `reversesMovementId` 8 e 11 nos estornos |
| H2 | OWNER A | `GET ...?page=1&size=3` | 200, segunda página | 200 ✅ ids 11, 10, 9; `totalElements` 8; `totalPages` 3 |
| H3 | ATTENDANT | `GET /ingredients/2/stock/movements` | 403 | 403 ✅ "Acesso negado." |

**Observações:**

- O `createdAt` do histórico vem em UTC (`Z`), enquanto as respostas das escritas vinham com `-03:00`. É só serialização do mesmo instante (por exemplo, o id 14: `23:01:03-03:00` na escrita e `02:01:03Z` no histórico).
- O `Page` do Spring é devolvido sem adaptação, com campos de infraestrutura (`pageable`, `sort`, `empty`). Um `PageResponse` enxuto fica como melhoria futura.

---

## Bloco 8: Estado no banco (token antigo)

Cada teste foi precedido de um `UPDATE` no banco e seguido da restauração do estado.

| # | Preparação | Requisição | Esperado | Resultado |
|---|---|---|---|---|
| B1 | `UPDATE users SET is_active = FALSE` para `stockist.a@teste.com` | STOCKIST: `POST /ingredients/2/stock/entries` com o token antigo | 401 | 401 ✅ (o filtro JWT barra usuário inativo) |
| B2 | `UPDATE users SET role = 'ATTENDANT'` para `manager.a@teste.com` | MANAGER: `POST /ingredients/2/stock/entries` com o token antigo | 403 | 403 ✅ (vale o cargo do banco, não o do token) |

Conferência no banco: saldo do Café continuou `1400.000`.

---

## Verificações finais no banco

**V1: o saldo bate com o `balance_after` da última movimentação**

```sql
SELECT i.id, i.name, i.current_quantity,
(SELECT m.balance_after FROM stock_movements m
WHERE m.ingredient_id = i.id
ORDER BY m.id DESC LIMIT 1) AS last_balance
FROM ingredients i
ORDER BY i.id;
```

| id | name | current_quantity | last_balance |
|---|---|---|---|
| 2 | Café | 1400.000 | 1400.000 |
| 3 | Ovo | 10.000 | 10.000 |
| 4 | Leite | 500.000 | 500.000 |
| 5 | Açúcar | 0.000 | (sem movimentações) |

**V2: o saldo bate com a soma dos deltas**

```sql
SELECT i.id, i.name, i.current_quantity, COALESCE(SUM(m.quantity_delta), 0) AS soma_deltas
FROM ingredients i
LEFT JOIN stock_movements m ON m.ingredient_id = i.id
GROUP BY i.id, i.name, i.current_quantity
ORDER BY i.id;
```

| id | name | current_quantity | soma_deltas |
|---|---|---|---|
| 2 | Café | 1400.000 | 1400.000 |
| 3 | Ovo | 10.000 | 10.000 |
| 4 | Leite | 500.000 | 500.000 |
| 5 | Açúcar | 0.000 | 0 |

Resultado: ✅ nos dois casos, para todos os ingredientes.

---

## Resumo

| Bloco | Testes | Resultado |
|---|---|---|
| Setup | 7 | ✅ |
| 1. Entrada | 7 | ✅ |
| 2. Ajuste | 8 | ✅ |
| 3. Saldo insuficiente e limites | 2 | ✅ |
| 4. Estorno | 8 | ✅ |
| 5. Permissões | 4 | ✅ |
| 6. Isolamento | 3 | ✅ |
| 7. Histórico | 3 | ✅ |
| 8. Estado no banco | 2 | ✅ |
| Verificações finais | 2 | ✅ |
| **Total** | **37 testes + 2 verificações (mais 7 de setup)** | **Nenhum defeito de comportamento encontrado** |
