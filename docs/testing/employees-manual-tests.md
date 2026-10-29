# Testes manuais: `POST /employees`

Registro dos testes manuais feitos no Postman contra o endpoint de cadastro de funcionários do CoffeePilot. O documento reúne duas sessões: a primeira rodada (hierarquia, autenticação, validação, e-mail, isolamento e estado no banco) e a rodada complementar (valores inválidos, campos extras e limites de tamanho).

## Ambiente

| Item | Valor |
|---|---|
| API | `http://localhost:8080` |
| Backend | Spring Boot 4.1.1, Java 25 |
| Banco | PostgreSQL 17 (Docker, porta 5433) |
| Ferramenta | Postman |
| Autenticação | JWT (HS256), enviado como `Authorization: Bearer <token>` |

Todos os resultados abaixo são os que foram observados na execução. Onde a execução foi diferente do plano original, está anotado.

## O que os testes provam

- **Hierarquia:** o dono cria todos os cargos, o gerente só atendente e estoquista, e o atendente nenhum.
- **Autenticação:** token ausente, falso ou adulterado é sempre 401, com a assinatura validada.
- **Validação:** os 400 apontam o campo certo, inclusive o limite de 72 bytes da senha, mapeado para `password`.
- **E-mail:** a normalização acontece antes da checagem de duplicidade.
- **Isolamento:** a cafeteria vem sempre do usuário autenticado, nunca do corpo da requisição.
- **Sessão:** usuário ou cafeteria inativos e cargo rebaixado não dão acesso com token antigo, porque o estado vale o do banco.
- **Campos protegidos:** o cliente não consegue definir `id`, `active` nem cafeteria.
- **Limites:** nome com 150 caracteres e e-mail com 254 são aceitos, e acima disso são rejeitados com 400.

---

## Sessão 1

### Setup

| # | O que foi feito                                                         | Resultado |
|---|-------------------------------------------------------------------------|---|
| S1 | `POST /auth/register` (registro de uma conta `Owner`)                   | Conta criada ✅ |
| S2 | `POST /auth/login` do dono                                              | 200 com `accessToken` ✅ |
| S3 | `POST /auth/login` com `manager@teste.com`                              | 200 ✅ |
| T27 | `POST /auth/login` com `attendant@teste.com` (funcionário criado no T1) | 200 ✅ (prova que o hash foi gravado certo) |

### Hierarquia de cargos

| # | O que foi feito | Esperado | Resultado |
|---|---|---|---|
| T1 | OWNER cria ATTENDANT | 201 | 201 ✅  |
| T2 | OWNER cria STOCKIST | 201 | 201 ✅ |
| T3 | OWNER cria MANAGER | 201 | 201 ✅ |
| T4 | OWNER cria OWNER | 201 | 201 ✅ |
| T5 | MANAGER cria ATTENDANT | 201 | 201 ✅ |
| T6 | MANAGER cria STOCKIST | 201 | 201 ✅ |
| T7 | MANAGER cria OWNER | 403 | 403 ✅ |
| T8 | MANAGER cria MANAGER | 403 | 403 ✅ |
| T9 | ATTENDANT cria qualquer cargo | 403 | 403 ✅ |

Conferência no banco: os cadastros negados (`owner3@`, `manager2@`, `negado@`) não foram criados (0 linhas).

### Autenticação

| # | O que foi feito | Esperado | Resultado |
|---|---|---|---|
| T10 | Sem token | 401/403 | 401 ✅ |
| T11 | Token inventado (`abc123`) | 401/403 | 401 ✅ |
| T12 | Token real com a assinatura alterada | 401/403 | 401 ✅ |

### Validação (todos com token de dono)

| # | O que foi feito | Esperado | Resultado |
|---|---|---|---|
| T13 | Nome em branco | 400, campo `name` | 400 ✅ |
| T14 | E-mail `a@b` | 400, campo `email` | 400 ✅ |
| T15 | E-mail `semarroba.com` | 400, campo `email` | 400 ✅ |
| T16 | Senha com 7 caracteres | 400, campo `password` | 400 ✅ |
| T17 | Senha com 40 `ç` (80 bytes) | 400, campo `password` | 400 ✅ (mensagem "no máximo 72 bytes") |
| T18 | `"role": "CHEFE"` | 400, corpo ilegível | 400 ✅ |
| T19 | Sem o campo `role` | 400, campo `role` | 400 ✅ |
| T20 | Corpo `{}` | 400, quatro campos | 400 ✅ |
| T21 | Texto puro, sem JSON | 400, corpo ilegível | 400 ✅ |

### Conflito de e-mail e normalização

| # | O que foi feito | Esperado | Resultado |
|---|---|---|---|
| T22 | Repetir `attendant@teste.com` | 409, campo `email` | 409 ✅ |
| T23 | `ATTENDANT@TESTE.COM` (maiúsculas) | 409 | 409 ✅ |
| T24 | `"  novo@teste.com  "` (com espaços) | 201, sem espaços | 201 ✅ (o banco confirmou `novo@teste.com`) |

### Isolamento por cafeteria

| # | O que foi feito | Esperado | Resultado |
|---|---|---|---|
| T25 | Enviar `"coffeeShopId": 1` no corpo | 201, campo ignorado, cafeteria 6 | 201 ✅ (banco: `coffee_shop_id = 6`) |
| T26 | Conferir a resposta sem `password`, `passwordHash` nem `coffeeShop` | Sem campos sensíveis | ✅ (em todos os 201) |

### Estado no banco (token antigo)

| # | O que foi feito | Esperado | Resultado |
|---|---|---|---|
| D1 | Dono (id 6) desativado, uso do token antigo do dono | 401/403 | 401 ✅ (o filtro JWT barrou antes do serviço) |
| D1 (controle) | Login do dono inativo | 401 | 401 ✅ ("Credenciais inválidas.") |
| D3 | Gerente rebaixado para ATTENDANT no banco, token antigo de MANAGER | 403 | 403 ✅ (vale o cargo do banco) |
| D2 | Cafeteria 6 desativada, token do dono | 401/403 | 401 ✅ (o filtro JWT barrou) |

---

## Sessão 2: valores inválidos, campos extras e limites

Todos com token de dono, em `POST /employees`.

| # | O que foi feito | Esperado | Resultado |
|---|---|---|---|
| T28 | `"role": "attendant"` (minúsculo) | 400, corpo ilegível | 400 ✅ ("Corpo da requisição inválido ou ilegível."); nada criado no banco |
| T29 | `"role": null` | 400, campo `role` | 400 ✅ ("Dados inválidos.", `role`: "O cargo é obrigatório.") |
| T30 | Corpo válido com `"id": 1`, `"active": false` e `"coffeeShopId": 1` | 201, campos ignorados | 201 ✅ (`id` 18 gerado pelo banco, `active: true`, nenhum dado de cafeteria na resposta) |
| T31 | Nome com 151 caracteres | 400, campo `name` | 400 ✅ ("O nome deve ter no máximo 150 caracteres.") |
| T32 | Nome com 150 caracteres | 201 | 201 ✅ (id 20) |
| T33 | E-mail com 255 caracteres e formato válido | 400, campo `email` | 400 ✅ ("O e-mail deve ter no máximo 254 caracteres.") |
| T34 | E-mail com 254 caracteres e formato válido | 201 | 201 ✅ (id 19); banco: `length(email) = 254`, `is_active = t` |
