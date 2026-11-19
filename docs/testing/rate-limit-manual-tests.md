# Testes manuais: rate limit

Registro dos testes manuais feitos contra o rate limit do CoffeePilot: limite por IP (login, cadastro, geral e regras por endpoint), limite de falhas de login por e-mail e CORS. O documento comprova as mensagens de cada bloqueio, o cabeçalho `Retry-After`, a expiração das janelas, o isolamento entre contadores e os valores de produção.

## Ambiente

| Item | Valor |
|---|---|
| API | `http://localhost:8080/v1` |
| Backend | Spring Boot 4.1.1, Java 25 |
| Banco | PostgreSQL 17 (Docker, porta 5433) |
| Ferramenta | Postman e scripts PowerShell (`Invoke-WebRequest`) |
| Autenticação | JWT (HS256), enviado como `Authorization: Bearer <token>` |

## Endpoints e limites testados

| Método | Rota | Limite padrão (produção) | Chave |
|---|---|---|---|
| POST | `/v1/auth/login` | 5 por minuto | IP |
| POST | `/v1/auth/register` | 3 por hora | IP |
| POST | `/v1/auth/login` (falhas) | 10 falhas em 15 minutos | e-mail normalizado |
| POST | `/v1/orders` | 60 por minuto | IP (regra) |
| qualquer | `/v1/products` e `/v1/products/**` | 300 por minuto | IP (regra) |
| demais | todas as outras rotas | 120 por minuto | IP |
| OPTIONS | qualquer | fora da contagem | n/a |

Todos os resultados abaixo são os observados na execução.

## Como distinguir os limites

| Mensagem do corpo | Limite | `Content-Type` |
|---|---|---|
| "Muitas requisições. Tente novamente em instantes." | Por IP (filtro) | `application/json;charset=UTF-8` |
| "Muitas tentativas de login. Tente novamente em instantes." | Por e-mail | `application/json` |

Os dois respondem 429 no formato padrão (`timestamp`, `status`, `error`, `message`, `path`, `fieldErrors`) com `Retry-After`.

## O que os testes provam

- **Limite por IP:** bloqueio na 6ª tentativa de login (limite 5) e no 4º cadastro (limite 3).
- **Limite por e-mail:** a 11ª falha para o mesmo e-mail recebe 429, igual para e-mail existente e inexistente, então o bloqueio não revela se a conta existe.
- **Normalização:** o e-mail em maiúsculas cai no mesmo contador.
- **Isolamento:** o bloqueio de um e-mail não afeta outro, e os contadores de login, geral e regras são independentes.
- **Ordem das verificações:** a senha correta não fura o bloqueio por e-mail, e o rate limit roda antes da autenticação.
- **Sucesso zera o contador:** após um login correto, as falhas anteriores deixam de contar.
- **Expiração:** passada a janela, o acesso volta.
- **Regras por endpoint:** a regra que casa substitui o limite geral, o curinga `/v1/products/**` compartilha um contador entre subrotas e um caminho parecido (`/productsx`) não casa.
- **CORS:** o preflight de origem autorizada responde 200 com os cabeçalhos corretos, a origem não autorizada recebe 403 e o preflight não consome cota.
- **Padrões de produção:** os valores do `application.properties`, sem variáveis de ambiente, são os que valem.

---

## Setup

Conta do estabelecimento 1: OWNER `dono@teste.com`. Ingrediente 1 (Café) com receita no produto 1 (Cappuccino, 30 g) e produto 2 (Brownie, sem receita). Login e register são públicos. Nos demais testes, token do dono obtido por `POST /v1/auth/login`.

| Bloco | Variáveis de ambiente (IntelliJ) |
|---|---|
| 1 | nenhuma (padrões) |
| 2 | `RATE_LIMIT_LOGIN_PER_MINUTE=20` |
| 3 | `RATE_LIMIT_LOGIN_PER_MINUTE=20`, `RATE_LIMIT_LOGIN_FAILURES_WINDOW=1m` |
| 4 | `RATE_LIMIT_GENERAL_PER_MINUTE=10`, `RATE_LIMIT_LOGIN_PER_MINUTE=20`, `RATE_LIMIT_ORDERS_POST_PER_MINUTE=5`, `RATE_LIMIT_PRODUCTS_PER_MINUTE=8` |
| 5 | nenhuma (padrões de produção) |

---

## Bloco 1: Limite por IP

Sem token. Aplicação reiniciada antes de cada teste.

| # | Rota | Corpo | Esperado | Resultado |
|---|---|---|---|---|
| I1 | `POST /auth/login` | `{"email":"naoexiste1@teste.com","password":"Errada@123"}`, 6 vezes em 1 minuto | 401 nas 5 primeiras, 429 na 6ª | ✅ 429 na 6ª, mensagem do filtro de IP |
| I2 | `POST /auth/register` | e-mails e nomes diferentes, 4 vezes | 201 nos 3 primeiros, 429 no 4º | ✅ 201 ×3 e 429 no 4º, `path` `/v1/auth/register` |
| I3 | `POST /auth/login` | 3 falhas e 1 acerto | 200 na 4ª | ✅ 200 |
| I4 | `POST /auth/login` | 6ª tentativa, cabeçalho | `Retry-After` até 60 | ✅ 55, `Content-Type` com `;charset=UTF-8` |

---

## Bloco 2: Falhas de login por e-mail

Sem token.

| # | Cenário | Corpo | Esperado | Resultado |
|---|---|---|---|---|
| E1 | 11 falhas, e-mail existente | `{"email":"dono@teste.com","password":"SenhaErrada@1"}` | 401 ×10, 429 na 11ª | ✅ 429 na 11ª, "Muitas tentativas de login..." |
| E2 | 11 falhas, e-mail inexistente | `{"email":"naoexiste@teste.com","password":"Qualquer@123"}` | Igual ao E1 | ✅ 429 na 11ª |
| E3 | E-mail em maiúsculas durante o bloqueio | `{"email":"DONO@TESTE.COM","password":"SenhaErrada@1"}` | 429 | ✅ 429, `Retry-After` diminuindo |
| E4 | Outro e-mail durante o bloqueio | conta existente, senha correta | Nunca 429 | ✅ 200 |
| E5 | Bloquear conta inexistente e logar com conta existente | dois e-mails | Login passa | ✅ 200 |

---

## Bloco 3: Janela, ordem das verificações e sucesso

| # | Cenário | Esperado | Resultado |
|---|---|---|---|
| W1 | 11ª falha, `Retry-After` | Até 60 | ✅ 49, `Content-Type` `application/json` |
| W2 | Esperar a janela e errar a senha | 401 | ✅ 401 |
| W3 | 10 falhas e 11ª com a senha **correta** | 429 | ✅ 429 |
| W4 | Esperar a janela e logar com a senha correta | 200 | ✅ 200 |
| W5 | 3 falhas, 1 login correto, 10 falhas e a 11ª falha (script PowerShell) | 401 ×3, 200, 401 ×10 e 429 na 11ª | ✅ 401 ×3, 200, 401 ×10 e 429 na 11ª, `Retry-After: 58` |

---

## Bloco 4: Limite geral, regras por endpoint e CORS

Geral 10, pedidos 5 e produtos 8 por minuto. Token do dono, salvo indicação.

| # | Token | Rota e corpo | Esperado | Resultado |
|---|---|---|---|---|
| G1 | dono | `GET /ingredients`, 11 chamadas | 200 ×10, 429 na 11ª | ✅ `path` `/v1/ingredients` |
| G2 | dono | `GET /ingredients` e `/ingredients/1` logo depois | 429 nas duas | ✅ |
| G3 | dono | `GET /ingredients` após 60 s | 200 | ✅ |
| G4 | nenhum | `GET /ingredients`, 11 chamadas | 401 ×10, 429 na 11ª | ✅ rate limit antes da autenticação |
| G5 | dono | `POST /orders` (`productId` 1, `quantity` 1), 6 chamadas | 201 ×5, 429 na 6ª | ✅ `path` `/v1/orders` |
| G6 | dono | `GET /orders` logo depois | 200 | ✅ a regra é só para POST |
| G7 | dono | `GET /products` e `GET /products/1/recipe` alternados, 9 chamadas | 200 ×8, 429 na 9ª | ✅ contador compartilhado |
| G8 | dono | `GET /ingredients` logo depois | 200 | ✅ a regra não consome o geral |
| G9 | dono | `GET /productsx`, 11 chamadas | 404 ×10, 429 na 11ª (geral) | ✅ |
| G10 | nenhum | `OPTIONS /products` com `Origin: http://localhost:5173`, `Access-Control-Request-Method: GET` e `Access-Control-Request-Headers: authorization` | 200 com cabeçalhos CORS | ✅ 200, `Allow-Origin: http://localhost:5173`, `Allow-Methods: GET,POST,PUT,PATCH,DELETE,OPTIONS`, `Allow-Headers: authorization`, `Expose-Headers: Retry-After`, `Max-Age: 3600` |
| G10b | nenhum | `OPTIONS /products` com `Origin: http://site-malicioso.com` | 403 | ✅ 403 |
| G10c | nenhum | 15 `OPTIONS /products` e depois 11 `GET /ingredients` | OPTIONS sem consumir cota, 429 só na 11ª | ✅ 429 na 11ª chamada, os 15 OPTIONS não contaram |
| G11 | dono | Esgotar o geral e `POST /auth/login` correto | 200 | ✅ login independente do geral |

---

## Bloco 5: Padrões de produção

Sem variáveis de rate limit. Aplicação reiniciada antes de cada teste.

| # | Token | Rota e corpo | Esperado | Resultado |
|---|---|---|---|---|
| R1 | nenhum | `POST /auth/login`, e-mail inexistente, 6 vezes em 1 minuto | 401 ×5, 429 na 6ª | ✅ "Muitas requisições..." |
| R2 | nenhum | `POST /auth/register`, 4 cadastros | 201 ×3, 429 no 4º | ✅ |
| R3 | nenhum | `POST /auth/login` com `naoexiste2@teste.com`: 5 tentativas, 61 s, 5 tentativas, 61 s e a 11ª (script PowerShell) | 401 ×10, 429 na 11ª com `Retry-After` perto de 780 | ✅ `Retry-After: 777` |
| R5 | dono | `GET /ingredients`, 121 chamadas em 1 minuto | 200 ×120, 429 na 121ª | ✅ |
| R6 | dono | `POST /orders` (`productId` 2, `quantity` 1), 61 chamadas | 201 ×60, 429 na 61ª | ✅ |

---

## Resumo

| Bloco | Testes | Resultado |
|---|---|---|
| 1. Limite por IP | 4 | ✅ |
| 2. Falhas por e-mail | 5 | ✅ |
| 3. Janela, ordem e sucesso | 5 | ✅ |
| 4. Limite geral, regras e CORS | 13 | ✅ |
| 5. Padrões de produção | 5 | ✅ |
| **Total** | **32 testes** | **Nenhum defeito de comportamento encontrado** |
