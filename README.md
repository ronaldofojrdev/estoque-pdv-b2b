# Estoque / PDV B2B (API)

API REST de estoque e ponto de venda para vendas B2B: produtos, clientes (empresas com CNPJ), vendas com baixa automatica de estoque, cancelamento e relatorios. Autenticacao com JWT e dois perfis.

Stack: Java 17, Spring Boot 3.5, Spring Security (JWT), Spring Data JPA, PostgreSQL, Flyway, Testcontainers, Docker Compose.

## O que a API faz

- **Produtos**: cadastro, edicao, busca paginada por nome/SKU, lista de estoque baixo (`quantidade <= estoque minimo`).
- **Estoque**: entrada e saida manual com motivo, historico de todas as movimentacoes (vendas e cancelamentos entram no historico).
- **Clientes B2B**: cadastro com validacao dos digitos verificadores do CNPJ, CNPJ unico, busca por nome ou CNPJ.
- **Vendas (PDV)**: varios itens por venda, preco e nome do produto copiados para a venda (alterar o produto depois nao muda vendas antigas), formas de pagamento `CASH`, `PIX`, `CARD` e `INVOICE` (faturado).
- **Cancelamento** de venda (so admin) devolve os itens ao estoque.
- **Relatorios** (so admin): total vendido por dia e produtos mais vendidos, ignorando vendas canceladas.

### Perfis

| Perfil | Pode |
|---|---|
| `SELLER` (vendedor) | Consultar produtos e clientes, cadastrar cliente, registrar e consultar vendas. |
| `ADMIN` | Tudo do vendedor + cadastrar/editar produtos, ajustar estoque, cancelar vendas, ver relatorios. |

Na primeira execucao sao criados `admin` / `admin123` e `vendedor` / `vendedor123`. Troque com as variaveis `ADMIN_PASSWORD` e `SELLER_PASSWORD` e defina um `JWT_SECRET` proprio (minimo 32 caracteres).

## Decisoes que valem explicar

### Estoque nunca fica negativo, nem com vendas simultaneas

A baixa e feita com um unico `UPDATE`:

```sql
UPDATE products SET quantity = quantity - :qty
WHERE id = :id AND active = TRUE AND quantity >= :qty
```

Se duas vendas disputam a ultima unidade, o banco serializa o `UPDATE` na linha: a segunda enxerga o saldo novo, nao atualiza nenhuma linha e a venda e recusada com `409 INSUFFICIENT_STOCK`. Ler o saldo, conferir e gravar de volta teria uma corrida. Alem disso:

- a venda inteira roda em uma transacao: se o terceiro item nao tem saldo, a baixa dos dois primeiros e desfeita;
- itens sao processados em ordem de id, para duas vendas com itens em comum nao travarem uma a outra (deadlock);
- a coluna `quantity` e `updatable = false` na entidade: editar nome/preco de um produto nunca sobrescreve um saldo que acabou de mudar;
- `CHECK (quantity >= 0)` no banco como ultima defesa.

### Venda idempotente

`POST /sales` exige o header `Idempotency-Key` com um UUID gerado pelo cliente para cada venda. Se a resposta se perde e o cliente reenvia, a API devolve a venda ja registrada (`200`) em vez de baixar o estoque duas vezes (`201` so na primeira). Duas requisicoes simultaneas com a mesma chave tambem geram uma venda so (a `UNIQUE` do banco barra a segunda).

### Cancelamento sem estoque duplicado

O cancelamento trava a venda (`SELECT ... FOR UPDATE`). Cancelar duas vezes, ou dois admins ao mesmo tempo, devolve os itens uma vez so.

### Erros

Respostas de erro seguem o formato `ProblemDetail` (RFC 9457). Falta de estoque inclui `code`, `productId` e `available` para o cliente mostrar qual item faltou e quanto ha.

| Status | Quando |
|---|---|
| 400 | Validacao do corpo, header obrigatorio ausente |
| 401 | Sem token, token invalido ou login errado |
| 403 | Perfil sem permissao |
| 404 | Produto, cliente ou venda inexistente |
| 409 | SKU/CNPJ duplicado, estoque insuficiente |
| 422 | Regra de negocio (CNPJ invalido, produto inativo, periodo de relatorio invalido) |

## Rodando

Precisa de Java 17, Maven e Docker.

```bash
docker compose up --build -d
./scripts/smoke-test.sh
```

### Exemplo

```bash
# login
TOKEN=$(curl -s -X POST localhost:8084/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')

# produto com 10 unidades
curl -X POST localhost:8084/products -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"sku":"PAR-10","name":"Parafuso 10mm","price":1.50,"initialQuantity":10,"minStock":3}'

# cliente
curl -X POST localhost:8084/customers -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Mercado Central Ltda","cnpj":"11.222.333/0001-81"}'

# venda (UUID novo a cada venda)
curl -X POST localhost:8084/sales -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: 5f0c3a52-8f6e-4a39-9b1f-0c3f3a1d7c11" \
  -d '{"customerId":"<id-cliente>","paymentMethod":"PIX","items":[{"productId":"<id-produto>","quantity":4}]}'
```

### Endpoints

| Metodo | Rota | Perfil |
|---|---|---|
| POST | `/auth/login` | publico |
| GET | `/products?q=&page=&size=&onlyActive=` | todos |
| GET | `/products/{id}`, `/products/low-stock` | todos |
| POST / PUT | `/products`, `/products/{id}` | admin |
| POST / GET | `/products/{id}/movements` | admin |
| GET / POST | `/customers`, `/customers/{id}` | todos |
| POST | `/sales` (header `Idempotency-Key`) | todos |
| GET | `/sales`, `/sales/{id}` | todos |
| POST | `/sales/{id}/cancel` | admin |
| GET | `/reports/daily?from=&to=` | admin |
| GET | `/reports/summary?from=&to=` | admin (total, quantidade de vendas e ticket medio) |
| GET | `/reports/top-products?from=&to=&limit=` | admin |
| GET | `/actuator/health` | publico |

Listas paginadas devolvem `{ "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }`, com `size` limitado a 100.

## Variaveis de ambiente

| Variavel | Padrao | Para que serve |
|---|---|---|
| `DB_URL` | monta a partir das tres abaixo | URL JDBC completa (se definida, ignora `DB_HOST`, `DB_PORT` e `DB_NAME`) |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5436` / `pdv` | Onde esta o Postgres |
| `DB_USER` / `DB_PASSWORD` | `pdv` / `pdv` | Credenciais do banco |
| `JWT_SECRET` | valor de desenvolvimento | Segredo do token. **Troque em producao** (minimo 32 caracteres) |
| `ADMIN_PASSWORD` / `SELLER_PASSWORD` | `admin123` / `vendedor123` | Senhas dos usuarios criados na primeira execucao |
| `CORS_ORIGINS` | `http://localhost:5173` | Origens permitidas para chamadas de navegador, separadas por virgula |

## Deploy

Existe um `render.yaml` (Blueprint do Render) com a API em Docker e um Postgres gratuito, mas **ainda nao publiquei nem testei no Render**, entao nao ha link de demo. Para publicar: criar a conta, escolher "New Blueprint Instance" apontando para este repositorio e preencher `ADMIN_PASSWORD` e `SELLER_PASSWORD`. O banco gratuito do Render expira depois de um tempo, por isso serve para demonstracao e nao para dados reais.

## Testes

```bash
mvn verify
```

83 testes. Os de integracao usam Testcontainers (Postgres real), entao o Docker precisa estar rodando.

- **CNPJ e venda (unitarios)**: digitos verificadores, formatos com e sem pontuacao, copia do preco no item.
- **Concorrencia**: 12 compradores disputando 5 unidades vendem exatamente 5 e o estoque termina em 0; 8 envios simultaneos da mesma `Idempotency-Key` geram uma venda e uma baixa; 6 cancelamentos simultaneos devolvem o estoque uma vez.
- **Venda**: total correto, itens repetidos juntados, falta de estoque desfaz tudo, produto inativo, venda sem itens.
- **Seguranca**: login, token invalido, rotas protegidas, vendedor barrado nas rotas de admin.
- **API**: produtos (busca, paginacao, edicao sem tocar no saldo, estoque baixo), clientes (CNPJ invalido, duplicado com outra formatacao), vendas e relatorios.

## Limitacoes conhecidas

- **Datas dos relatorios em UTC**: um relatorio "do dia" no Brasil corta as vendas depois das 21h no dia seguinte.
- **Sem cadastro de usuarios pela API**: os usuarios vem do seed inicial.
- **Sem troca de senha nem refresh token**: o token dura 8 horas.
- **Cancelamento parcial e devolucao de item** nao existem, so o cancelamento da venda inteira.
- **Sem impostos, desconto, limite de credito do cliente nem contas a receber** do pagamento `INVOICE`.

## Proximos passos

Em ordem do que eu faria primeiro:

1. **Fuso horario nos relatorios**: receber o fuso do usuario (ou usar America/Sao_Paulo) em vez de UTC.
2. **Cadastro de usuarios pela API**, com troca de senha, no lugar do seed inicial.
3. **Cancelamento parcial e devolucao de item**, hoje so existe cancelar a venda inteira.
4. **Contas a receber** para vendas `INVOICE`, com vencimento e baixa do pagamento.
5. **Desconto por item ou por venda**, com limite por perfil.
6. **Refresh token**, para o vendedor nao precisar logar de novo a cada 8 horas.
7. **Publicar a demo** (o `render.yaml` ja esta pronto).
