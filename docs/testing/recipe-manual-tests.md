# Testes manuais: endpoints de receitas

Registro dos testes manuais feitos no Postman contra os endpoints de receitas do CoffeePilot: consulta e substituição da receita de um produto. O documento cobre a troca atômica dos itens, validação do corpo, regras de negócio (ingrediente repetido, inativo, de outro estabelecimento e quantidade por unidade), permissões, isolamento entre cafeterias e o estado do banco.

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
| GET | `/products/{productId}/recipe` | OWNER, MANAGER, STOCKIST |
| PUT | `/products/{productId}/recipe` | OWNER, MANAGER |

O `PUT` substitui a receita inteira. A lista vazia é válida (produto sem receita não consome estoque), mas `items` ausente ou `null` é rejeitado.

Todos os resultados abaixo são os observados na execução. Onde a execução foi diferente do plano original, está anotado.

## O que os testes provam

- **Substituição atômica:** o `DELETE` em massa seguido dos `INSERT` funciona no PostgreSQL real, inclusive reaproveitando o mesmo ingrediente numa receita nova. Um item inválido no meio do corpo derruba a operação inteira e a receita anterior permanece intacta.
- **`updated_at` do produto:** cada `PUT` avança o `updatedAt` do produto, inclusive com receita vazia, e o valor persiste (confirmado por `GET` posterior).
- **Ordem:** a resposta lista os itens por nome do ingrediente, independentemente da ordem enviada.
- **Validação:** lista ausente, nula, com mais de 50 itens, item nulo, ingrediente ausente ou não positivo, quantidade ausente, zero ou com mais de 3 casas são rejeitados com 400, e os erros de item trazem o índice (`items[0].quantity`).
- **Regras de negócio:** ingrediente repetido, quantidade decimal para `UNIT`, ingrediente inativo, inexistente ou de outro estabelecimento são rejeitados com 400, e o limite máximo de quantidade coincide com o do banco.
- **Produto inativo:** pode ter a receita consultada e editada.
- **Permissões:** OWNER e MANAGER editam, STOCKIST apenas consulta e ATTENDANT não acessa receitas. As permissões são verificadas no backend.
- **Isolamento:** uma cafeteria não enxerga nem altera receitas de outra, nem usa ingredientes de outra, e as respostas são idênticas às de recursos inexistentes.
- **Ingrediente desativado:** desativar um ingrediente não é bloqueado nem altera as receitas que o usam. A resposta sinaliza `ingredientActive: false`, e a receita só pode ser regravada sem esse ingrediente.

---

## Setup

Foram reaproveitadas as contas e os dados dos testes anteriores: cafeteria A (OWNER, MANAGER, STOCKIST e ATTENDANT) e cafeteria B (OWNER), os produtos dos testes de produtos e os ingredientes dos testes de ingredientes e estoque.

| # | O que foi feito | Resultado |
|---|---|---|
| S1 | `POST /auth/login` das 5 contas e armazenamento dos tokens | 200 nas cinco ✅ |
| S2 | OWNER A: `GET /ingredients/4` | 404 ✅ (o id 4, "Leite", pertence à cafeteria B, como no teste de estoque) |
| S3 | OWNER A: `POST /ingredients` com `{"name":"Leite","unit":"ML","minimumQuantity":0}` | 409 "Já existe um ingrediente com este nome." ✅ (a cafeteria A já tinha um Leite) |
| S4 | OWNER A: `GET /ingredients?size=100` | 200, 7 ingredientes, com o Leite da A no id 9 (ML, ativo) ✅ |

### Ids usados

| Variável | Valor | Descrição |
|---|---|---|
| `idBrownie` | 1 | Produto, cafeteria A, inativo |
| `idCappuccino` | 2 | Produto, cafeteria A |
| `idEspresso` | 3 | Produto, cafeteria A |
| `idLatte` | 4 | Produto "LATTE GRANDE", cafeteria A |
| `idCappuccinoB` | 5 | Produto, cafeteria B |
| `idCaro` | 6 | Produto, cafeteria A, nunca teve receita |
| `idCafe` | 2 | Ingrediente Café (G), cafeteria A |
| `idOvo` | 3 | Ingrediente Ovo (UNIT), cafeteria A |
| `idLeiteB` | 4 | Ingrediente Leite (ML), cafeteria B |
| `idAcucar` | 5 | Ingrediente Açúcar (G), cafeteria A, inativo |
| `idChocolate` | 7 | Ingrediente "chocolate EM PÓ" (G), cafeteria A |
| `idCopo` | 8 | Ingrediente "Copo descartável" (UNIT), cafeteria A |
| `idLeite` | 9 | Ingrediente Leite (ML), cafeteria A |

---

## Bloco 1: Substituição

`PUT /products/{id}/recipe`.

| # | Token | Rota | Corpo | Esperado | Resultado |
|---|---|---|---|---|---|
| P1 | OWNER A | 2 | `{"items":[{"ingredientId":9,"quantity":150.5},{"ingredientId":2,"quantity":30}]}` | 200, itens em ordem de nome, `updatedAt` avançou | 200 ✅ Café 30.000 (G) e Leite 150.500 (ML), `updatedAt` `01:15:11-03:00` |
| P2 | MANAGER | 3 | `{"items":[{"ingredientId":2,"quantity":18}]}` | 200, 1 item | 200 ✅ Café 18.000 |
| P3 | OWNER A | 1 (inativo) | `{"items":[{"ingredientId":3,"quantity":2},{"ingredientId":2,"quantity":10}]}` | 200 (produto inativo aceita edição) | 200 ✅ Café 10.000 e Ovo 2.000 |
| P4 | OWNER A | 2 | `{"items":[{"ingredientId":2,"quantity":40},{"ingredientId":3,"quantity":1}]}` | 200, receita substituída por completo | 200 ✅ Café 40.000 e Ovo 1.000, sem o Leite |
| P5 | OWNER A | 2 | `{"items":[]}` | 200, `items` vazio, `updatedAt` avançou | 200 ✅ `updatedAt` `01:22:07-03:00` |
| P6 | OWNER A | 2 | `{"items":[{"ingredientId":2,"quantity":30},{"ingredientId":9,"quantity":150}]}` | 200, estado final do Cappuccino | 200 ✅ Café 30.000 e Leite 150.000, `updatedAt` `01:22:48-03:00` |

O P4 reaproveitou o Café numa receita nova sem violar `uq_recipe_items_product_ingredient`, o que prova que o `DELETE` em massa e os `INSERT` convivem no PostgreSQL. O P5 avançou o `updatedAt` do produto mesmo sem nenhum item, o que prova que o `touch()` gera o `UPDATE`. A resposta do P1 trouxe o Café antes do Leite, embora o Leite tenha sido enviado primeiro. O `updatedAt` do Cappuccino era o da criação (`2026-11-03T12:13:44Z`) antes do P1, então a evolução (`01:15:11`, `01:20:44`, `01:22:07` e `01:22:48`) mostra um avanço a cada `PUT`.

---

## Bloco 2: Consulta

`GET /products/{id}/recipe`.

| # | Token | Rota | Esperado | Resultado |
|---|---|---|---|---|
| G1 | OWNER A | 2 | 200, Café 30.000 e Leite 150.000, `updatedAt` igual ao do P6 | 200 ✅ `04:22:48.649103Z` (o mesmo instante do P6, `01:22:48-03:00`) |
| G2 | STOCKIST | 2 | 200, mesma receita | 200 ✅ |
| G3 | MANAGER | 1 (inativo) | 200, Café 10.000 e Ovo 2.000 | 200 ✅ `updatedAt` igual ao do P3 |
| G4 | OWNER A | 4 (sem receita) | 200, `items` vazio | 200 ✅ `updatedAt` `2026-11-03T12:31:13Z` (a consulta não altera o produto) |
| G5 | OWNER A | 9999 | 404 | 404 ✅ "Produto não encontrado." |

O G1 confirma que o `updatedAt` do `PUT` persistiu no banco, e não apenas na resposta. Produto sem receita (G4) é tratado como receita vazia, e não como erro.

---

## Bloco 3: Validação do corpo

`PUT /products/2/recipe` com OWNER A.

| # | Corpo | Esperado | Resultado |
|---|---|---|---|
| V1 | `{}` | 400, campo `items` | 400 ✅ "A lista de itens é obrigatória." |
| V2 | `{"items":null}` | 400, campo `items` | 400 ✅ (mesma resposta) |
| V3 | 51 itens | 400, campo `items` | 400 ✅ "A receita deve ter no máximo 50 itens." |
| V4 | `{"items":[null]}` | 400, item inválido | 400 ✅ campo `items[0]`, "Item da receita inválido." |
| V5 | `{"items":[{"quantity":10}]}` | 400 | 400 ✅ campo `items[0].ingredientId`, "O ingrediente é obrigatório." |
| V6 | `{"items":[{"ingredientId":0,"quantity":10}]}` | 400 | 400 ✅ campo `items[0].ingredientId`, "O ingrediente informado é inválido." |
| V7 | `{"items":[{"ingredientId":2}]}` | 400 | 400 ✅ campo `items[0].quantity`, "A quantidade é obrigatória." |
| V8 | `{"items":[{"ingredientId":2,"quantity":0}]}` | 400 | 400 ✅ campo `items[0].quantity`, "A quantidade deve ser maior que zero." |
| V9 | `{"items":[{"ingredientId":2,"quantity":1.2345}]}` | 400 | 400 ✅ campo `items[0].quantity`, "até 11 dígitos inteiros e 3 decimais" |
| V10 | `GET /products/2/recipe` (conferência) | 200, receita intacta | 200 ✅ Café 30.000 e Leite 150.000, `updatedAt` `04:22:48.649103Z` |
| V11 | corpo malformado | 400, `fieldErrors` vazio | 400 ✅ "Corpo da requisição inválido ou ilegível." |

---

## Bloco 4: Regras de negócio

`PUT /products/{id}/recipe` com OWNER A. Estes casos passam pelo DTO e são barrados no service, por isso `fieldErrors` vem vazio.

| # | Rota | Corpo | Esperado | Resultado |
|---|---|---|---|---|
| N1 | 2 | Café 10 e Café 5 | 400 | 400 ✅ "Ingrediente repetido na receita." |
| N2 | 2 | `{"items":[{"ingredientId":3,"quantity":1.5}]}` (Ovo, UNIT) | 400 | 400 ✅ "A quantidade deve ser um número inteiro para ingredientes em UNIT." |
| N3 | 2 | `{"items":[{"ingredientId":5,"quantity":10}]}` (Açúcar, inativo) | 400 | 400 ✅ `O ingrediente "Açúcar" está inativo.` |
| N4 | 2 | `{"items":[{"ingredientId":4,"quantity":10}]}` (Leite da B) | 400 | 400 ✅ "Ingrediente não encontrado." |
| N5 | 2 | `{"items":[{"ingredientId":9999,"quantity":10}]}` | 400, mesma resposta do N4 | 400 ✅ "Ingrediente não encontrado." |
| N6 | 2 | Café 50 e Açúcar 10 (um válido e um inativo) | 400 | 400 ✅ `O ingrediente "Açúcar" está inativo.` |
| N6b | 2 | `GET /products/2/recipe` | 200, receita intacta | 200 ✅ Café 30.000 e Leite 150.000, `updatedAt` `04:22:48.649103Z` |
| N7 | 4 | `{"items":[{"ingredientId":7,"quantity":99999999999.999}]}` | 200 (limite máximo) | 200 ✅ chocolate em pó 99999999999.999 |
| N8 | 4 | chocolate 10 e copo descartável 1 | 200 | 200 ✅ chocolate 10.000 e copo 1.000 |

---

## Bloco 5: Permissões

Os corpos dos `PUT` são válidos de propósito, para garantir que o 403 vem da permissão e não de validação.

| # | Token | Requisição | Esperado | Resultado |
|---|---|---|---|---|
| Q1 | ATTENDANT | `GET /products/2/recipe` | 403 | 403 ✅ "Acesso negado." |
| Q2 | ATTENDANT | `PUT /products/2/recipe` com `{"items":[{"ingredientId":2,"quantity":1}]}` | 403 | 403 ✅ |
| Q3 | STOCKIST | `PUT /products/2/recipe` com o mesmo corpo | 403 | 403 ✅ |
| Q4 | Sem token | `GET /products/2/recipe` | 401 | 401 ✅ "Autenticação necessária." |
| Q5 | Sem token | `PUT /products/2/recipe` com o mesmo corpo | 401 | 401 ✅ |
| Q6 | OWNER A | `GET /products/2/recipe` (conferência) | 200, receita intacta | 200 ✅ Café 30.000 e Leite 150.000, `updatedAt` `04:22:48.649103Z` |

O STOCKIST consulta (G2) mas não edita (Q3). O ATTENDANT não acessa receitas em nenhuma operação.

---

## Bloco 6: Isolamento entre cafeterias

A cafeteria B tem o produto Cappuccino (5) e o ingrediente Leite (4).

| # | Token | Requisição | Esperado | Resultado |
|---|---|---|---|---|
| I1 | OWNER B | `GET /products/2/recipe` (produto da A) | 404 | 404 ✅ "Produto não encontrado." |
| I2 | OWNER B | `PUT /products/2/recipe` com `{"items":[{"ingredientId":4,"quantity":10}]}` | 404 | 404 ✅ "Produto não encontrado." |
| I3 | OWNER B | `PUT /products/5/recipe` com `{"items":[{"ingredientId":2,"quantity":10}]}` (Café da A) | 400 | 400 ✅ "Ingrediente não encontrado." |
| I4 | OWNER B | `PUT /products/5/recipe` com `{"items":[{"ingredientId":4,"quantity":200}]}` | 200 | 200 ✅ Leite 200.000 |
| I5 | OWNER A | `GET /products/2/recipe` (conferência) | 200, receita intacta | 200 ✅ Café 30.000 e Leite 150.000, `updatedAt` `04:22:48.649103Z` |
| I6 | OWNER A | `GET /products/5/recipe` (produto da B) | 404 | 404 ✅ "Produto não encontrado." |

O produto é verificado antes dos ingredientes (I2). As respostas são idênticas às de um produto ou ingrediente inexistente (compare com o G5, o N4 e o N5). O I4 mostra que o isolamento não impede o uso normal.

---

## Bloco 7: Receita com ingrediente desativado

Usa o chocolate em pó (7), que está na receita do LATTE GRANDE (4). OWNER A.

| # | Requisição | Corpo | Esperado | Resultado |
|---|---|---|---|---|
| R1 | `PATCH /ingredients/7/status` | `{"active":false}` | 200, `active: false` | 200 ✅ |
| R2 | `GET /products/4/recipe` | - | 200, 2 itens, chocolate com `ingredientActive: false` | 200 ✅ copo com `true`, `updatedAt` `04:59:45Z` (o do N8) |
| R3 | `PUT /products/4/recipe` | chocolate 10 e copo 1 | 400 | 400 ✅ `O ingrediente "chocolate EM PÓ" está inativo.` |
| R4 | `PUT /products/4/recipe` | `{"items":[{"ingredientId":8,"quantity":1}]}` | 200 (remover o inativo é permitido) | 200 ✅ só o copo descartável 1.000 |
| R5 | `PATCH /ingredients/7/status` | `{"active":true}` | 200, `active: true` | 200 ✅ |

O R2 mostra que desativar um ingrediente não é bloqueado e não altera as receitas que o usam, nem o `updatedAt` do produto. O sinal `ingredientActive` é o que o frontend usará para destacar a receita com problema. O R3 e o R4 documentam as duas saídas para corrigir a receita: remover o ingrediente inativo ou reativá-lo.

---

## Verificações finais no banco

**V1: estado final das receitas**

```sql
SELECT ri.coffee_shop_id, ri.product_id, p.name AS produto,
       ri.ingredient_id, i.name AS ingrediente, ri.quantity
FROM recipe_items ri
JOIN products p ON p.id = ri.product_id AND p.coffee_shop_id = ri.coffee_shop_id
JOIN ingredients i ON i.id = ri.ingredient_id AND i.coffee_shop_id = ri.coffee_shop_id
ORDER BY ri.product_id, i.name;
```

| coffee_shop_id | product_id | produto | ingredient_id | ingrediente | quantity |
|---|---|---|---|---|---|
| 1 | 1 | Brownie | 2 | Café | 10.000 |
| 1 | 1 | Brownie | 3 | Ovo | 2.000 |
| 1 | 2 | Cappuccino | 2 | Café | 30.000 |
| 1 | 2 | Cappuccino | 9 | Leite | 150.000 |
| 1 | 3 | Espresso | 2 | Café | 18.000 |
| 1 | 4 | LATTE GRANDE | 8 | Copo descartável | 1.000 |
| 2 | 5 | Cappuccino | 4 | Leite | 200.000 |

O Caro (6) não aparece porque nunca teve receita.

**V2: item cujo produto, ingrediente e estabelecimento não coincidem**

```sql
SELECT ri.id
FROM recipe_items ri
JOIN products p ON p.id = ri.product_id
JOIN ingredients i ON i.id = ri.ingredient_id
WHERE p.coffee_shop_id <> ri.coffee_shop_id
   OR i.coffee_shop_id <> ri.coffee_shop_id;
```

Resultado: 0 linhas ✅

**V3: ingrediente repetido na mesma receita ou quantidade não positiva**

```sql
SELECT product_id, ingredient_id, COUNT(*)
FROM recipe_items
GROUP BY product_id, ingredient_id
HAVING COUNT(*) > 1
UNION ALL
SELECT product_id, ingredient_id, 0
FROM recipe_items
WHERE quantity <= 0;
```

Resultado: 0 linhas ✅

**V4: produtos cuja receita foi alterada**

```sql
SELECT id, name, updated_at > created_at AS editado
FROM products ORDER BY id;
```

| id | name | editado |
|---|---|---|
| 1 | Brownie | t |
| 2 | Cappuccino | t |
| 3 | Espresso | t |
| 4 | LATTE GRANDE | t |
| 5 | Cappuccino | t |
| 6 | Caro | f |

Todos os produtos que tiveram a receita substituída foram marcados como alterados, e o Caro (6), que nunca teve receita, não.

---

## Resumo

| Bloco | Testes | Resultado |
|---|---|---|
| Setup | 4 | ✅ |
| 1. Substituição | 6 | ✅ |
| 2. Consulta | 5 | ✅ |
| 3. Validação do corpo | 11 | ✅ |
| 4. Regras de negócio | 9 | ✅ |
| 5. Permissões | 6 | ✅ |
| 6. Isolamento | 6 | ✅ |
| 7. Ingrediente desativado | 5 | ✅ |
| Verificações finais | 4 | ✅ |
| **Total** | **48 testes + 4 verificações (mais 4 de setup)** | **Nenhum defeito de comportamento encontrado** |