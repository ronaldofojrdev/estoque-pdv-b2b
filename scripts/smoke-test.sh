#!/usr/bin/env bash
# Teste ponta a ponta contra a API rodando via docker compose.
# Uso: docker compose up --build -d && ./scripts/smoke-test.sh
set -euo pipefail

API=${API_URL:-http://localhost:8084}

# primeira ocorrencia do campo no JSON
json_field() { grep -o "\"$1\":\"\{0,1\}[^\",}]*" | head -1 | sed "s/^\"$1\":\"\{0,1\}//"; }

login() {
  curl -sf -X POST "$API/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" | json_field token
}

# UUID v4 simples, sem depender de uuidgen (nao existe no Git Bash)
new_uuid() {
  local h
  h=$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')
  echo "${h:0:8}-${h:8:4}-4${h:13:3}-a${h:17:3}-${h:20:12}"
}

ADMIN=$(login admin admin123)
SELLER=$(login vendedor vendedor123)
SUFFIX=$RANDOM

call() { # metodo token caminho [corpo] [header extra]
  curl -s -X "$1" "$API$3" -H "Authorization: Bearer $2" -H 'Content-Type: application/json' \
    ${5:+-H "$5"} ${4:+-d "$4"}
}

echo "1) admin cadastra produto com 10 unidades"
PRODUCT=$(call POST "$ADMIN" /products "{\"sku\":\"SMK-$SUFFIX\",\"name\":\"Produto smoke\",\"price\":25.50,\"initialQuantity\":10,\"minStock\":3}" | json_field id)

echo "2) vendedor cadastra cliente B2B (CNPJ validado)"
CUSTOMER=$(call POST "$SELLER" /customers "{\"name\":\"Empresa Smoke $SUFFIX\",\"cnpj\":\"11.222.333/0001-81\"}" | json_field id || true)
if [ -z "$CUSTOMER" ]; then
  CUSTOMER=$(call GET "$SELLER" "/customers?q=11222333" | json_field id)
fi

echo "3) venda de 4 unidades (Idempotency-Key gerada pelo cliente)"
KEY=$(new_uuid)
BODY="{\"customerId\":\"$CUSTOMER\",\"paymentMethod\":\"PIX\",\"items\":[{\"productId\":\"$PRODUCT\",\"quantity\":4}]}"
SALE=$(call POST "$SELLER" /sales "$BODY" "Idempotency-Key: $KEY" | json_field id)
echo "   mesma chave de novo (reenvio apos falha de rede): HTTP $(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/sales" \
  -H "Authorization: Bearer $SELLER" -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" -d "$BODY") (esperado 200)"
QTY=$(call GET "$SELLER" "/products/$PRODUCT" | json_field quantity)
[ "$QTY" = "6" ] || { echo "FALHOU: estoque deveria ser 6 e e $QTY"; exit 1; }
echo "   estoque: $QTY"

echo "4) venda acima do estoque e recusada com 409"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/sales" -H "Authorization: Bearer $SELLER" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $(new_uuid)" \
  -d "{\"customerId\":\"$CUSTOMER\",\"paymentMethod\":\"CASH\",\"items\":[{\"productId\":\"$PRODUCT\",\"quantity\":99}]}")
[ "$CODE" = "409" ] || { echo "FALHOU: esperado 409 e veio $CODE"; exit 1; }

echo "5) vendedor nao cancela venda (403), admin cancela e o estoque volta"
[ "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/sales/$SALE/cancel" -H "Authorization: Bearer $SELLER")" = "403" ] \
  || { echo "FALHOU: vendedor deveria receber 403"; exit 1; }
call POST "$ADMIN" "/sales/$SALE/cancel" > /dev/null
QTY=$(call GET "$SELLER" "/products/$PRODUCT" | json_field quantity)
[ "$QTY" = "10" ] || { echo "FALHOU: estoque deveria voltar a 10 e e $QTY"; exit 1; }
echo "   estoque: $QTY"

echo "OK"
