# Testes manuais: endpoints de ingredientes

Registro dos testes manuais feitos no Postman contra os endpoints de ingredientes do CoffeePilot: cadastro (com saldo inicial), listagem com filtros, detalhe, atualização parcial e ativação/desativação. O documento cobre regras de negócio, validação, permissões, isolamento entre cafeterias e a consistência entre o saldo dos ingredientes e o histórico de movimentações.

## Ambiente

| Item | Valor |
|---|---|
| API | `http://localhost:8080` |
| Backend | Spring Boot 4.1.1, Java 25 |
| Banco | PostgreSQL 17 (Docker, porta 5433), migrations V1 a V4 |
| Ferramenta | Postman |
| Autenticação | JWT (HS256), enviado como `Authorization: Bearer <token>` |

## Endpoints testados

| Método | Rota | Perfis permitidos |
|---|---|---|
| POST | `/ingredients` | OWNER, MANAGER, STOCKIST |
| GET | `/ingredients?active=&lowStock=&page=&size=` | OWNER, MANAGER, STOCKIST |
| GET | `/ingredients/{id}` | OWNER, MANAGER, STOCKIST |
| PATCH | `/ingredients/{id}` | OWNER, MANAGER, STOCKIST |
| PATCH | `/ingredients/{id}/status` | OWNER, MANAGER, STOCKIST |

Todos os resultados abaixo são os observados na execução. Onde a execução foi diferente do plano original, está anotado.

## O que os testes provam

- **Cadastro atômico:** o ingrediente e a movimentação `INITIAL` são gravados na mesma transação. Se o saldo inicial for inválido, nada é gravado.
- **Nome único por cafeteria:** a checagem ignora caixa e espaços nas pontas, e o mesmo nome pode existir em cafeterias diferentes.
- **Unidade `UNIT`:** aceita só números inteiros, no saldo inicial e no estoque mínimo.
- **Atualização parcial:** só os campos enviados mudam. Unidade, saldo e status enviados no corpo são ignorados.
- **Saldo preservado na edição:** o `PATCH` não sobrescreve o saldo (a linha é bloqueada durante a edição).
- **Listagem:** filtros `active` e `lowStock` opcionais, ordenação fixa por nome e id (o `sort` do cliente é descartado), página limitada a 100 itens.
- **Estoque baixo:** conta o saldo igual ao mínimo, ignora ingredientes inativos e os de mínimo zero.
- **Desativação:** ingrediente inativo não recebe movimentações, mas o histórico continua visível e ele pode ser reativado.
- **Permissões:** ATTENDANT não acessa o módulo. As permissões são verificadas no backend.
- **Isolamento:** uma cafeteria não enxerga nem altera ingredientes de outra, e a resposta (404) é idêntica à de um recurso inexistente.
- **Consistência:** ao final, o saldo de cada ingrediente bate com o `balance_after` da última movimentação e com a soma dos deltas.

---

## Setup

Reaproveitou o ambiente dos testes de estoque, sem recriar o banco.

| Item | Valor |
|---|---|
| Cafeteria A | OWNER, MANAGER, STOCKIST e ATTENDANT |
| Cafeteria B | OWNER |
| Ingredientes pré-existentes | Café (id 2, saldo 1400, mínimo 200), Ovo (3, `UNIT`, saldo 10), Açúcar (5, inativo) na cafeteria A, e Leite (4, saldo 500) na B |

Os ingredientes criados pela API recebem ids novos. O id 10 foi consumido pelo C6, cujo cadastro foi desfeito, então o ingrediente seguinte teria o id 11.

---

## Bloco 1: Cadastro

`POST /ingredients`.

| # | Token | Corpo | Esperado | Resultado |
|---|---|---|---|---|
| C1 | OWNER A | `{"name": "Farinha", "unit": "G", "minimumQuantity": 500, "initialQuantity": 2000}` | 201, saldo 2000, `lowStock` false | 201 ✅ (id 6) |
| C2 | STOCKIST | `{"name": "Chocolate", "unit": "G"}` | 201, saldo 0, sem movimentação | 201 ✅ (id 7) |
| C3 | MANAGER | `{"name": "Copo descartável", "unit": "UNIT", "minimumQuantity": 50, "initialQuantity": 200}` | 201, saldo 200 | 201 ✅ (id 8) |
| C4 | OWNER A | `{"name": "  FARINHA  ", "unit": "G"}` | 409 | 409 ✅ "Já existe um ingrediente com este nome." |
| C5 | OWNER A | `{"name": "Leite", "unit": "ML", "initialQuantity": 100}` | 201 (o nome existe só na cafeteria B) | 201 ✅ (id 9) |
| C6 | OWNER A | `{"name": "Ovo Caipira", "unit": "UNIT", "initialQuantity": 2.5}` | 400, nada gravado | 400 ✅ "O saldo inicial deve ser um número inteiro para ingredientes em UNIT." |
| C7a | OWNER A | `{"name": "   ", "unit": "G"}` | 400, campo `name` | 400 ✅ "O nome é obrigatório." |
| C7b | OWNER A | `{"name": "Sal", "unit": "KG"}` | 400 | 400 ✅ "Corpo da requisição inválido ou ilegível." |
| C7c | OWNER A | `{"name": "Sal", "unit": "G", "initialQuantity": -5}` | 400, campo `initialQuantity` | 400 ✅ "O saldo inicial não pode ser negativo." |
| C8 | OWNER A | `{"name": "Fermento", "unit": "G", "minimumQuantity": 1.2345}` | 400, campo `minimumQuantity` | 400 ✅ "até 11 dígitos inteiros e 3 decimais" |

**Conferência no banco:**

- Os ingredientes novos da cafeteria A são Farinha (2000), Chocolate (0), Copo descartável (200) e Leite (100). Os dois "Leite" coexistem, um em cada cafeteria.
- Não existem "Ovo Caipira", "Sal" nem "Fermento".
- Há três movimentações `INITIAL` novas: Farinha (id 15, 2000, Dono A), Copo descartável (id 16, 200, Gerente A) e Leite da A (id 17, 100, Dono A). O Chocolate não tem movimentação.
- **Atomicidade (C6):** o id 10 de ingrediente foi consumido pela identity e a transação desfez o cadastro. Os ids de movimentação seguem sem salto (15, 16, 17).

**Observação (C2):** o `currentQuantity` voltou como `0` (e não `0.000`), porque, sem saldo inicial, o campo mantém o valor padrão da entidade, com escala 0. O banco guarda `0.000`. Ajuste cosmético pendente.

---

## Bloco 2: Listagem

`GET /ingredients`. Estado da cafeteria A: 7 ingredientes, 6 ativos e o Açúcar inativo.

| # | Token | Requisição | Esperado | Resultado |
|---|---|---|---|---|
| L1 | STOCKIST | `/ingredients` | 200, 7 itens por nome, inclui o inativo | 200 ✅ `totalElements` 7, Açúcar primeiro (o acento não altera a ordem) |
| L2a | OWNER A | `?active=true` | 200, 6 itens | 200 ✅ sem o Açúcar |
| L2b | OWNER A | `?active=false` | 200, 1 item | 200 ✅ só o Açúcar |
| L3a | OWNER A | `?lowStock=true` (sem ingredientes em estoque baixo) | 200, vazio | 200 ✅ `totalElements` 0 |
| L3b | OWNER A | `?lowStock=true` (Farinha com saldo 500 e mínimo 500) | 200, só a Farinha | 200 ✅ `lowStock: true` no limite exato |
| L4 | OWNER A | `?active=false&lowStock=true` | 400 | 400 ✅ "Estoque baixo considera apenas ingredientes ativos." |
| L5a | OWNER A | `?page=1&size=2` | 200, segunda página | 200 ✅ Chocolate e Copo descartável, `totalPages` 4 |
| L5b | OWNER A | `?size=500&sort=currentQuantity,desc` | 200, `size` 100, ordem por nome | 200 ✅ o `sort` foi ignorado |
| L6 | OWNER A | `?active=talvez` | 400 | **500 na primeira execução**, 400 ✅ depois da correção |

**Preparação do L3b:** foi feito um ajuste `LOSS` de -1500 na Farinha (saldo 500). Ao final do bloco, uma entrada de +1500 (movimentação 19) devolveu o saldo a 2000.



**Observação (L1, L2 e L5):** a `CAST(:active AS Boolean)` da query `findByFilters` funcionou na execução, com parâmetro nulo e preenchido.

---

## Bloco 3: Detalhe

`GET /ingredients/{id}`.

| # | Token | Requisição | Esperado | Resultado |
|---|---|---|---|---|
| D1 | STOCKIST | `/ingredients/2` | 200, Café | 200 ✅ saldo 1400, mínimo 200, `lowStock` false |
| D2a | OWNER B | `/ingredients/2` | 404 (o Café é da A) | 404 ✅ "Ingrediente não encontrado." |
| D2b | OWNER B | `/ingredients/9999` | 404, mesma mensagem | 404 ✅ idêntico ao D2a, só muda o `path` |
| D3 | OWNER A | `/ingredients/abc` | 400 | 400 ✅ "Valor inválido para o parâmetro 'ingredientId'." |

---

## Bloco 4: Atualização

`PATCH /ingredients/{id}`.

| # | Token | Rota (id) | Corpo | Esperado | Resultado |
|---|---|---|---|---|---|
| U1 | STOCKIST | 7 | `{"name": "Chocolate em pó"}` | 200, só o nome muda | 200 ✅ |
| U2 | MANAGER | 8 | `{"minimumQuantity": 80}` | 200, só o mínimo muda | 200 ✅ |
| U3 | OWNER A | 7 | `{"name": "  FARINHA  "}` | 409 | 409 ✅ |
| U4 | OWNER A | 7 | `{"name": "chocolate EM PÓ"}` | 200 (o próprio ingrediente, só com outra caixa) | 200 ✅ |
| U5 | OWNER A | 7 | `{}` | 400 | 400 ✅ "Informe ao menos um campo para atualizar." |
| U6 | OWNER A | 6 | `{"unit": "ML", "currentQuantity": 1, "active": false, "name": "Farinha de trigo"}` | 200, só o nome muda | 200 ✅ unidade `G`, saldo 2000 e `active: true` mantidos |
| U7 | OWNER A | 8 | `{"minimumQuantity": 10.5}` | 400 | 400 ✅ "Quantity for UNIT must be a whole number." |

**Conferência no banco:**

| id | name | unit | current_quantity | minimum_quantity | is_active |
|---|---|---|---|---|---|
| 6 | Farinha de trigo | G | 2000.000 | 500.000 | t |
| 7 | chocolate EM PÓ | G | 0.000 | 0.000 | t |
| 8 | Copo descartável | UNIT | 200.000 | 80.000 | t |

O saldo da Farinha continuou 2000 depois do U6, mesmo com o `PATCH` gravando a linha inteira. O `updated_at` mudou nos três.


---

## Bloco 5: Status

`PATCH /ingredients/{id}/status`, usando o Ovo (id 3, `UNIT`, saldo 10).

| # | Token | Requisição | Corpo | Esperado | Resultado |
|---|---|---|---|---|---|
| ST1 | STOCKIST | `PATCH /ingredients/3/status` | `{"active": false}` | 200, saldo intacto | 200 ✅ `active: false`, saldo 10 |
| ST2 | OWNER A | `POST /ingredients/3/stock/entries` | `{"quantity": 5}` | 409 | 409 ✅ "Ingrediente inativo não pode receber movimentações." (ver nota) |
| ST3 | OWNER A | `GET /ingredients/3/stock/movements` | nenhum | 200, histórico visível | 200 ✅ `INITIAL` (id 6), `totalElements` 1 |
| ST4 | MANAGER | `PATCH /ingredients/3/status` | `{"active": true}` | 200 | 200 ✅ `active: true`, saldo 10 |
| ST5 | OWNER A | `PATCH /ingredients/3/status` | `{}` | 400, campo `active` | 400 ✅ "O status é obrigatório." |

---

## Bloco 6: Permissões

| # | Token | Requisição | Esperado | Resultado |
|---|---|---|---|---|
| PM1 | ATTENDANT | `POST /ingredients` | 403 | 403 ✅ "Acesso negado." |
| PM2 | ATTENDANT | `GET /ingredients` | 403 | 403 ✅ |
| PM3 | ATTENDANT | `PATCH /ingredients/2` | 403 | 403 ✅ |
| PM4 | ATTENDANT | `PATCH /ingredients/2/status` | 403 | 403 ✅ |
| PM5 | Sem token | `GET /ingredients` | 401 | 401 ✅ "Autenticação necessária." |

---

## Bloco 7: Isolamento entre cafeterias

| # | Token | Requisição | Esperado | Resultado |
|---|---|---|---|---|
| IS1 | OWNER B | `PATCH /ingredients/2` com `{"name": "Invadido"}` | 404 | 404 ✅ "Ingrediente não encontrado." |
| IS2 | OWNER B | `PATCH /ingredients/2/status` com `{"active": false}` | 404 | 404 ✅ |
| IS3 | OWNER A | `PATCH /ingredients/4` com `{"name": "Invadido"}` | 404 (o Leite id 4 é da B) | 404 ✅ |
| IS4 | OWNER B | `GET /ingredients` | 200, só o Leite da B | 200 ✅ `totalElements` 1 |

**Conferência no banco:** Café ("Café", ativo, cafeteria 1) e Leite ("Leite", ativo, cafeteria 2) intactos, e nenhum ingrediente "Invadido". As mensagens de 404 são idênticas às de um id inexistente.

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
| 6 | Farinha de trigo | 2000.000 | 2000.000 |
| 7 | chocolate EM PÓ | 0.000 | (sem movimentações) |
| 8 | Copo descartável | 200.000 | 200.000 |
| 9 | Leite | 100.000 | 100.000 |

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
| 6 | Farinha de trigo | 2000.000 | 2000.000 |
| 7 | chocolate EM PÓ | 0.000 | 0 |
| 8 | Copo descartável | 200.000 | 200.000 |
| 9 | Leite | 100.000 | 100.000 |

**V3: nenhum ingrediente tem mais de um `INITIAL`**

```sql
SELECT ingredient_id, count(*) AS qtd_initial
FROM stock_movements
WHERE type = 'INITIAL'
GROUP BY ingredient_id
HAVING count(*) > 1;
```

Resultado: nenhuma linha.

Resultado das três verificações: ✅. A Farinha é o caso mais completo, com `INITIAL` (+2000), ajuste (-1500) e entrada (+1500).

---

## Resumo

| Bloco | Casos | Resultado                                   |
|---|---|---------------------------------------------|
| 1. Cadastro | 8 (10 requisições) | ✅                                           |
| 2. Listagem | 9 | ✅ (um defeito encontrado e corrigido no L6) |
| 3. Detalhe | 4 | ✅                                           |
| 4. Atualização | 7 | ✅                                           |
| 5. Status | 5 | ✅ (ST2 refeito por token expirado)          |
| 6. Permissões | 5 | ✅                                           |
| 7. Isolamento | 4 | ✅                                           |
| Verificações finais | 3 | ✅                                           |
| **Total** | **42 casos (44 requisições) + 3 verificações** | todos os casos passaram no teste            |
