# Testes manuais: endpoints de produtos

Registro dos testes manuais feitos no Postman contra os endpoints de produtos do CoffeePilot: cadastro, listagem com filtros e busca, consulta, atualização parcial e ativação/desativação. O documento cobre regras de negócio, validação, permissões, visibilidade por perfil, isolamento entre estabelecimentos e o estado do banco.

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
| POST | `/products` | OWNER, MANAGER |
| GET | `/products` | OWNER, MANAGER, STOCKIST, ATTENDANT (ATTENDANT vê só produtos ativos) |
| GET | `/products/{productId}` | OWNER, MANAGER, STOCKIST, ATTENDANT (ATTENDANT recebe 404 para produto inativo) |
| PATCH | `/products/{productId}` | OWNER, MANAGER |
| PATCH | `/products/{productId}/status` | OWNER, MANAGER |

Todos os resultados abaixo são os observados na execução. Onde a execução foi diferente do plano original, está anotado.

## O que os testes provam

- **Cadastro:** o nome é aparado, a descrição em branco vira `null`, o preço é gravado com 2 casas decimais e o mesmo nome é permitido em estabelecimentos diferentes.
- **Validação:** nome em branco ou acima de 150 caracteres, descrição acima de 500, preço ausente, zero, com mais de 2 casas decimais ou com mais de 8 dígitos inteiros são rejeitados com 400 e o campo correto. O preço máximo (99999999.99) é aceito.
- **Unicidade:** o nome é único por estabelecimento, sem diferenciar maiúsculas de minúsculas nem espaços nas pontas. Produto inativo continua reservando o nome.
- **Listagem:** busca parcial por nome sem diferenciar caixa, com `%` e `_` tratados como texto literal; filtro por status; tamanho de página limitado a 100; ordenação fixa por nome e id, ignorando o `sort` do cliente.
- **Atualização parcial:** campo ausente não altera, `description: ""` remove a descrição, corpo vazio é rejeitado, e o próprio produto pode mudar a caixa do nome sem conflitar consigo mesmo.
- **Permissões:** só OWNER e MANAGER criam e editam. ATTENDANT e STOCKIST apenas consultam. As permissões são verificadas no backend.
- **Visibilidade:** o ATTENDANT nunca vê produto inativo (nem filtrando por `active=false`), enquanto o STOCKIST vê todos.
- **Isolamento:** um estabelecimento não enxerga nem altera produtos de outro, e a resposta (404) é idêntica à de um recurso inexistente.
- **Banco:** nomes aparados, descrições em branco gravadas como `NULL`, sem duplicidade de nome por estabelecimento e `updated_at` alterado apenas nos produtos editados.

---

## Setup

Foram reaproveitadas as contas dos testes de estoque: estabelecimento A (OWNER, MANAGER, STOCKIST e ATTENDANT) e estabelecimento B (OWNER).

| # | O que foi feito | Resultado |
|---|---|---|
| S1 | `POST /auth/login` das 5 contas e armazenamento dos tokens | 200 nas cinco ✅ |
| S2 | OWNER A: `POST /products` com `{"name":"Brownie","price":9}` | 201, id 1, `price` 9.00, `description` nulo ✅ |
| S3 | OWNER A: `PATCH /products/1/status` com `{"active":false}` | 200, `active: false` ✅ |

O Brownie fica inativo para os testes de visibilidade.

### Ids usados

| Variável | Valor | Descrição |
|---|---|---|
| `idBrownie` | 1 | Brownie, estabelecimento A, inativo |
| `idCappuccino` | 2 | Cappuccino, estabelecimento A |
| `idEspresso` | 3 | Espresso, estabelecimento A |
| `idLatte` | 4 | Latte, estabelecimento A (renomeado para "LATTE GRANDE" no Bloco 6) |
| `idCappuccinoB` | 5 | Cappuccino, estabelecimento B |
| `idCaro` | 6 | Caro, estabelecimento A |

Os produtos rejeitados por validação ou por duplicidade não chegaram ao `INSERT`, então não consumiram id da identity.

---

## Bloco 1: Criação

`POST /products`.

| # | Token | Corpo | Esperado | Resultado |
|---|---|---|---|---|
| C1 | OWNER A | `{"name":"Cappuccino","description":"Com chocolate","price":12.50}` | 201, `price` 12.50, `active: true` | 201 ✅ (id 2) |
| C2 | MANAGER | `{"name":"Espresso","price":8}` | 201, `price` 8.00, `description` nulo | 201 ✅ (id 3, o inteiro `8` virou `8.00`) |
| C3 | OWNER A | `{"name":"  Latte  ","description":"   ","price":15.9}` | 201, nome "Latte", `description` nulo, `price` 15.90 | 201 ✅ (id 4) |
| C4 | OWNER B | `{"name":"Cappuccino","price":13}` | 201 (mesmo nome em outro estabelecimento) | 201 ✅ (id 5) |

O C3 confirma o `trim()` do nome e a conversão de descrição em branco para `null`. O C4 confirma que a unicidade é por estabelecimento.

---

## Bloco 2: Validação

`POST /products` com OWNER A.

| # | Corpo | Esperado | Resultado |
|---|---|---|---|
| V1 | `{"name":"   ","price":10}` | 400, campo `name` | 400 ✅ "O nome é obrigatório." (uma única violação) |
| V2 | `name` com 151 letras "a", `price` 10 | 400, campo `name` | 400 ✅ "O nome deve ter no máximo 150 caracteres." |
| V3 | `{"name":"Teste","description":<501 letras "a">,"price":10}` | 400, campo `description` | 400 ✅ "A descrição deve ter no máximo 500 caracteres." |
| V4 | `{"name":"Teste"}` | 400, campo `price` | 400 ✅ "O preço é obrigatório." |
| V5 | `{"name":"Teste","price":0}` | 400, campo `price` | 400 ✅ "O preço deve ser maior que zero." |
| V6 | `{"name":"Teste","price":12.345}` | 400, campo `price` | 400 ✅ "O preço deve ter até 8 dígitos inteiros e 2 decimais." |
| V7 | `{"name":"Teste","price":100000000}` | 400, campo `price` | 400 ✅ (mesma mensagem do V6, 9 dígitos inteiros) |
| V8 | `{"name":"Caro","price":99999999.99}` | 201 (limite máximo aceito) | 201 ✅ (id 6, `price` 99999999.99) |

Conferência: V1 a V7 não deixaram rastro no banco. O V5 retornou uma única violação para `price`, sem duplicar com o `@Digits`.

---

## Bloco 3: Duplicidade

`POST /products` com OWNER A.

| # | Corpo | Esperado | Resultado |
|---|---|---|---|
| D1 | `{"name":"cappuccino","price":10}` | 409, `fieldErrors` vazio | 409 ✅ "Já existe um produto com este nome." |
| D2 | `{"name":"  CAPPUCCINO  ","price":10}` | 409, mesma resposta | 409 ✅ |

A checagem `existsByName` normaliza com `LOWER(TRIM(...))` e o service barra antes do `INSERT`. Por isso a resposta vem com `fieldErrors` vazio e nenhum id é consumido. O 409 com o campo `name` só ocorre na corrida entre duas requisições simultâneas, quando o índice `uq_products_establishment_name` barra a segunda, caso coberto por teste automatizado no `GlobalExceptionHandlerTest`.

---

## Bloco 4: Listagem

`GET /products` com OWNER A. O estabelecimento A tinha 5 produtos: Brownie (inativo), Cappuccino, Caro, Espresso e Latte.

| # | Requisição | Esperado | Resultado |
|---|---|---|---|
| L1 | `GET /products` | 200, `totalElements` 5, ordem por nome | 200 ✅ Brownie, Cappuccino, Caro, Espresso, Latte; `pageSize` 20 |
| L2 | `GET /products?search=CAPP` | 200, só o Cappuccino | 200 ✅ `totalElements` 1 |
| L3 | `GET /products?search=%25` | 200, lista vazia (`%` literal) | 200 ✅ `totalElements` 0 |
| L4 | `GET /products?active=false` | 200, só o Brownie | 200 ✅ `totalElements` 1 |
| L5 | `GET /products?size=500` | 200, tamanho limitado a 100 | 200 ✅ `pageSize` 100 |
| L6 | `GET /products?sort=price,desc` | 200, ordem continua por nome | 200 ✅ mesma ordem do L1 |
| L7 | `GET /products?page=1&size=2` | 200, Caro e Espresso, `totalPages` 3 | 200 ✅ `first` e `last` falsos |
| L8 | `GET /products?search=_` (extra) | 200, lista vazia (`_` literal) | 200 ✅ `totalElements` 0 |

O L2, o L3 e o L8 foram a primeira execução do HQL com `ESCAPE '!'` no PostgreSQL real e rodaram sem erro. O L1 e o L4 confirmam o `CAST(:active AS Boolean)` com `active` nulo e com `false`. A ordenação por nome não trouxe surpresa de collation.

No L6, `sort.sorted` aparece como `true` porque o service força `name, id`; a resposta não reflete o `price` pedido pelo cliente.

---

## Bloco 5: Consulta

| # | Token | Requisição | Esperado | Resultado |
|---|---|---|---|---|
| G1 | OWNER A | `GET /products/2` | 200, dados completos | 200 ✅ Cappuccino, "Com chocolate", `price` 12.50 |
| G2 | OWNER A | `GET /products/9999` | 404 | 404 ✅ "Produto não encontrado.", `fieldErrors` vazio |

---

## Bloco 6: Atualização

`PATCH /products/4` (Latte). Os testes foram executados em sequência, cada um partindo do resultado do anterior.

| # | Token | Corpo | Esperado | Resultado |
|---|---|---|---|---|
| U1 | MANAGER | `{"name":"Latte Grande","description":"Maior","price":18.9}` | 200, `price` 18.90 | 200 ✅ |
| U2 | OWNER A | `{"price":20}` | 200, nome e descrição inalterados, `price` 20.00 | 200 ✅ |
| U3 | OWNER A | `{"description":""}` | 200, `description` nulo | 200 ✅ |
| U4 | OWNER A | `{}` | 400, `fieldErrors` vazio | 400 ✅ "Informe ao menos um campo para atualizar." (barrado pelo service) |
| U5 | OWNER A | `{"name":"   "}` | 400, campo `name` | 400 ✅ "O nome não pode ficar em branco." (barrado pelo DTO) |
| U6 | OWNER A | `{"name":"espresso"}` | 409 | 409 ✅ "Já existe um produto com este nome." |
| U7 | OWNER A | `{"name":"LATTE GRANDE"}` | 200 (o próprio nome com outra caixa) | 200 ✅ `name` "LATTE GRANDE" |

O `updatedAt` avançou em todas as edições bem-sucedidas (U1, U2, U3 e U7). U4, U5 e U6 não alteraram nada. O Latte termina com nome "LATTE GRANDE", `description` nulo e `price` 20.00.

---

## Bloco 7: Status

| # | Token | Requisição | Corpo | Esperado | Resultado |
|---|---|---|---|---|---|
| T1 | OWNER A | `PATCH /products/4/status` | `{"active":false}` | 200, `active: false` | 200 ✅ |
| T2 | OWNER A | `PATCH /products/4/status` | `{"active":true}` | 200, `active: true` | 200 ✅ |
| T3 | OWNER A | `POST /products` | `{"name":"Brownie","price":9}` | 409 | 409 ✅ "Já existe um produto com este nome." |

O T3 confirma que desativar um produto não libera o nome: o Brownie (id 1, inativo) continua reservando o nome.

---

## Bloco 8: Permissões e visibilidade

Os corpos dos P1 a P3 são válidos de propósito, para garantir que o 403 vem da permissão e não de validação.

| # | Token | Requisição | Esperado | Resultado |
|---|---|---|---|---|
| P1 | ATTENDANT | `POST /products` com `{"name":"Teste","price":5}` | 403 | 403 ✅ "Acesso negado." |
| P2 | ATTENDANT | `PATCH /products/2/status` com `{"active":false}` | 403 | 403 ✅ (Cappuccino intacto) |
| P3 | STOCKIST | `PATCH /products/2` com `{"price":1}` | 403 | 403 ✅ (preço intacto) |
| P4 | Sem token | `GET /products` | 401 | 401 ✅ "Autenticação necessária." |
| P5 | ATTENDANT | `GET /products?active=false` | 200, só os 4 ativos | 200 ✅ `totalElements` 4 (Cappuccino, Caro, Espresso, LATTE GRANDE); sem o Brownie |
| P6 | ATTENDANT | `GET /products/1` (Brownie, inativo) | 404 | 404 ✅ "Produto não encontrado." |
| P7 | STOCKIST | `GET /products/1` (Brownie, inativo) | 200, `active: false` | 200 ✅ |

O P5 mostra que o filtro `active=false` enviado pelo cliente é sobrescrito para `true` no service quando o perfil é ATTENDANT. O P6 e o P7 mostram a diferença entre os dois perfis no mesmo recurso.

---

## Bloco 9: Isolamento entre estabelecimentos

O estabelecimento B tem só o Cappuccino (id 5).

| # | Token | Requisição | Esperado | Resultado |
|---|---|---|---|---|
| I1 | OWNER B | `GET /products/2` (Cappuccino da A) | 404 | 404 ✅ "Produto não encontrado." |
| I2 | OWNER B | `PATCH /products/2` com `{"price":1}` | 404, produto da A intacto | 404 ✅ |
| I3 | OWNER B | `GET /products` | 200, só o Cappuccino da B | 200 ✅ id 5, `price` 13.00, `totalElements` 1 |

**Conferência com OWNER A:** `GET /products/2` retornou `active: true`, `price` 12.50 e `updatedAt` igual ao `createdAt`. Isso comprova que o P2, o P3 e o I2 não alteraram o produto.

A resposta 404 do isolamento é idêntica à de um produto inexistente (compare com o G2), então o cliente não distingue "existe em outro estabelecimento" de "não existe".

---

## Verificações finais no banco

**V1: estado final da tabela**

```sql
SELECT id, establishment_id, name, description, price, is_active
FROM products ORDER BY id;
```

| id | establishment_id | name | description | price | is_active |
|---|---|---|---|---|---|
| 1 | 1 | Brownie | (nulo) | 9.00 | f |
| 2 | 1 | Cappuccino | Com chocolate | 12.50 | t |
| 3 | 1 | Espresso | (nulo) | 8.00 | t |
| 4 | 1 | LATTE GRANDE | (nulo) | 20.00 | t |
| 5 | 2 | Cappuccino | (nulo) | 13.00 | t |
| 6 | 1 | Caro | (nulo) | 99999999.99 | t |

Descrições em branco foram gravadas como `NULL`, os nomes foram aparados e os preços têm 2 casas decimais.

**V2: nenhuma duplicidade de nome por estabelecimento**

```sql
SELECT establishment_id, LOWER(BTRIM(name)), COUNT(*)
FROM products
GROUP BY establishment_id, LOWER(BTRIM(name))
HAVING COUNT(*) > 1;
```

Resultado: 0 linhas ✅

**V3: `updated_at` alterado só nos produtos editados**

```sql
SELECT id, name, updated_at > created_at AS editado
FROM products ORDER BY id;
```

| id | name | editado |
|---|---|---|
| 1 | Brownie | t |
| 2 | Cappuccino | f |
| 3 | Espresso | f |
| 4 | LATTE GRANDE | t |
| 5 | Cappuccino | f |
| 6 | Caro | f |

Foram editados apenas o Brownie (desativado no S3) e o LATTE GRANDE (blocos 6 e 7). O Cappuccino do estabelecimento A (id 2) não foi alterado pelos testes de permissão (P2, P3) nem de isolamento (I2).

---

## Resumo

| Bloco | Testes | Resultado |
|---|---|---|
| Setup | 3 | ✅ |
| 1. Criação | 4 | ✅ |
| 2. Validação | 8 | ✅ |
| 3. Duplicidade | 2 | ✅ |
| 4. Listagem | 8 (7 planejados + 1 extra) | ✅ |
| 5. Consulta | 2 | ✅ |
| 6. Atualização | 7 | ✅ |
| 7. Status | 3 | ✅ |
| 8. Permissões e visibilidade | 7 | ✅ |
| 9. Isolamento | 3 | ✅ |
| Verificações finais | 3 | ✅ |
| **Total** | **44 testes + 3 verificações (mais 3 de setup)** | **Nenhum defeito de comportamento encontrado** |