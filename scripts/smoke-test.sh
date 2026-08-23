#!/usr/bin/env bash
# Curl-based smoke test for the fernbank API (Phase 4 API surface).
# Walks the golden path against a running backend: register -> login -> open two
# accounts -> deposit -> transfer -> statement -> add/list/delete a payee.
#
# Usage:
#   BASE_URL=http://localhost:8080 ./scripts/smoke-test.sh
#
# The admin section (freeze + audit-events) needs a user with ROLE_ADMIN, which has no
# self-service API in Phase 4 (see docs/postman/fernbank.postman_collection.json's Admin
# folder). This script grants it directly via `docker compose exec postgres psql` -
# dev-only, never do this against a real database.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../infra" && pwd)"
RUN_ID="$(date +%s)-$$"
EMAIL="smoke-${RUN_ID}@example.com"
PASSWORD="correct horse battery staple"

log() { printf '\n== %s ==\n' "$1"; }
require() {
	if ! command -v "$1" >/dev/null 2>&1; then
		echo "This script needs '$1' on PATH." >&2
		exit 1
	fi
}
require curl
require jq

log "Register"
curl -sf -X POST "$BASE_URL/api/v1/auth/register" \
	-H 'Content-Type: application/json' \
	-d "{\"fullName\":\"Smoke Test\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" >/dev/null
echo "registered $EMAIL"

log "Login"
LOGIN=$(curl -sf -X POST "$BASE_URL/api/v1/auth/login" \
	-H 'Content-Type: application/json' \
	-d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
ACCESS_TOKEN=$(echo "$LOGIN" | jq -r '.accessToken')
echo "got access token"

auth() { curl -sf -H "Authorization: Bearer $ACCESS_TOKEN" "$@"; }
idem() { echo "Idempotency-Key: $(uuidgen)"; }

log "Open two accounts"
SOURCE=$(auth -X POST "$BASE_URL/api/v1/accounts" -H 'Content-Type: application/json' -H "$(idem)" \
	-d '{"type":"CHECKING","currency":"USD"}')
DESTINATION=$(auth -X POST "$BASE_URL/api/v1/accounts" -H 'Content-Type: application/json' -H "$(idem)" \
	-d '{"type":"SAVINGS","currency":"USD"}')
SOURCE_ID=$(echo "$SOURCE" | jq -r '.id')
DESTINATION_ID=$(echo "$DESTINATION" | jq -r '.id')
echo "source=$SOURCE_ID destination=$DESTINATION_ID"

log "Deposit 250.00 into the source account"
auth -X POST "$BASE_URL/api/v1/accounts/$SOURCE_ID/deposits" -H 'Content-Type: application/json' -H "$(idem)" \
	-d '{"amount":{"amount":"250.00","currency":"USD"},"description":"seed"}' | jq '.newBalance'

log "Transfer 40.00 to the destination account"
auth -X POST "$BASE_URL/api/v1/transfers" -H 'Content-Type: application/json' -H "$(idem)" \
	-d "{\"sourceAccountId\":\"$SOURCE_ID\",\"destinationAccountId\":\"$DESTINATION_ID\",\"amount\":{\"amount\":\"40.00\",\"currency\":\"USD\"},\"description\":\"rent\"}" \
	| jq '{sourceNewBalance, destinationNewBalance}'

log "Statement for the source account"
auth "$BASE_URL/api/v1/accounts/$SOURCE_ID/statement?pageSize=10" | jq '.data | length'

log "List accounts"
auth "$BASE_URL/api/v1/accounts" | jq '. | length'

log "Add, list, delete a payee"
PAYEE_TARGET=$(echo "$DESTINATION" | jq -r '.accountNumber')
PAYEE=$(auth -X POST "$BASE_URL/api/v1/payees" -H 'Content-Type: application/json' -H "$(idem)" \
	-d "{\"name\":\"Landlord\",\"targetAccountNumber\":\"$PAYEE_TARGET\"}")
PAYEE_ID=$(echo "$PAYEE" | jq -r '.id')
auth "$BASE_URL/api/v1/payees" | jq '. | length'
auth -X DELETE "$BASE_URL/api/v1/payees/$PAYEE_ID" -o /dev/null -w 'delete status: %{http_code}\n'

log "IDOR check: a second customer must not see the first customer's account"
OTHER_EMAIL="smoke-other-${RUN_ID}@example.com"
curl -sf -X POST "$BASE_URL/api/v1/auth/register" -H 'Content-Type: application/json' \
	-d "{\"fullName\":\"Other Customer\",\"email\":\"$OTHER_EMAIL\",\"password\":\"$PASSWORD\"}" >/dev/null
OTHER_LOGIN=$(curl -sf -X POST "$BASE_URL/api/v1/auth/login" -H 'Content-Type: application/json' \
	-d "{\"email\":\"$OTHER_EMAIL\",\"password\":\"$PASSWORD\"}")
OTHER_TOKEN=$(echo "$OTHER_LOGIN" | jq -r '.accessToken')
STATUS=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $OTHER_TOKEN" "$BASE_URL/api/v1/accounts/$SOURCE_ID")
if [ "$STATUS" != "404" ]; then
	echo "IDOR check FAILED: expected 404, got $STATUS" >&2
	exit 1
fi
echo "cross-customer access correctly returned 404"

if docker compose -f "$INFRA_DIR/docker-compose.yml" exec -T postgres pg_isready >/dev/null 2>&1; then
	log "Admin section (granting ROLE_ADMIN directly in the dev DB)"
	USER_ID=$(docker compose -f "$INFRA_DIR/docker-compose.yml" exec -T postgres \
		psql -U "${DB_USER:-fernbank}" -d "${DB_NAME:-fernbank}" -t -A \
		-c "SELECT id FROM app_user WHERE email = '$EMAIL';")
	docker compose -f "$INFRA_DIR/docker-compose.yml" exec -T postgres \
		psql -U "${DB_USER:-fernbank}" -d "${DB_NAME:-fernbank}" \
		-c "INSERT INTO user_role (user_id, role) VALUES ('$USER_ID', 'ADMIN') ON CONFLICT DO NOTHING;" >/dev/null

	ADMIN_LOGIN=$(curl -sf -X POST "$BASE_URL/api/v1/auth/login" -H 'Content-Type: application/json' \
		-d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
	ADMIN_TOKEN=$(echo "$ADMIN_LOGIN" | jq -r '.accessToken')
	adminauth() { curl -sf -H "Authorization: Bearer $ADMIN_TOKEN" "$@"; }

	adminauth "$BASE_URL/api/v1/admin/ping" | jq '.'
	adminauth "$BASE_URL/api/v1/admin/reconciliation" | jq '.discrepancies | length'
	adminauth -X PATCH "$BASE_URL/api/v1/admin/accounts/$SOURCE_ID/freeze" \
		-H 'Content-Type: application/json' -H "$(idem)" -d '{"reason":"smoke test"}' | jq '.status'
	adminauth "$BASE_URL/api/v1/admin/audit-events?pageSize=5" | jq '.data | length'
else
	echo
	echo "Skipping admin section: infra/docker-compose.yml postgres service is not reachable via 'docker compose exec'."
fi

log "Done"
