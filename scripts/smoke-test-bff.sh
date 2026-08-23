#!/usr/bin/env bash
# Curl-based smoke test for the NestJS BFF (Phase 8). Walks the golden path against a
# running bff + backend: register -> login (asserts the response body never contains an
# accessToken or refreshToken, and that both the session and CSRF cookies are set with
# the right attributes) -> a proxied GET through the BFF -> a mutation rejected without
# the CSRF header and accepted with it -> logout -> a proxied call after logout 401s.
#
# Usage:
#   BFF_BASE_URL=http://localhost:4000 ./scripts/smoke-test-bff.sh

set -euo pipefail

BFF_BASE_URL="${BFF_BASE_URL:-http://localhost:4000}"
RUN_ID="$(date +%s)-$$"
EMAIL="smoke-bff-${RUN_ID}@example.com"
PASSWORD="correct horse battery staple"
JAR="$(mktemp)"
trap 'rm -f "$JAR"' EXIT

log() { printf '\n== %s ==\n' "$1"; }
require() {
	if ! command -v "$1" >/dev/null 2>&1; then
		echo "This script needs '$1' on PATH." >&2
		exit 1
	fi
}
require curl
require jq

log "Health check"
curl -sf "$BFF_BASE_URL/health" | jq -e '.status == "UP"' >/dev/null
echo "bff is up"

log "Register"
REGISTER=$(curl -sf -X POST "$BFF_BASE_URL/api/v1/auth/register" \
	-H 'Content-Type: application/json' \
	-d "{\"fullName\":\"Smoke BFF\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
echo "$REGISTER" | jq '{userId, email}'

log "Login"
LOGIN_HEADERS="$(mktemp)"
LOGIN=$(curl -sf -c "$JAR" -D "$LOGIN_HEADERS" -X POST "$BFF_BASE_URL/api/v1/auth/login" \
	-H 'Content-Type: application/json' \
	-d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
echo "$LOGIN" | jq '{status}'

if echo "$LOGIN" | jq -e 'has("accessToken") or has("refreshToken")' >/dev/null; then
	echo "FAILED: login response body must never contain an accessToken or refreshToken key" >&2
	exit 1
fi
echo "response body has no accessToken/refreshToken key, as expected"

if ! grep -qi 'set-cookie:.*fernbank_bff_session.*httponly' "$LOGIN_HEADERS"; then
	echo "FAILED: login did not set an HttpOnly session cookie" >&2
	exit 1
fi
if grep -qi 'set-cookie:.*fernbank_bff_csrf.*httponly' "$LOGIN_HEADERS"; then
	echo "FAILED: the CSRF cookie must NOT be HttpOnly - the frontend has to read it" >&2
	exit 1
fi
echo "session cookie is HttpOnly, CSRF cookie is not, as expected"
rm -f "$LOGIN_HEADERS"

CSRF_TOKEN=$(grep fernbank_bff_csrf "$JAR" | awk '{print $NF}')
if [ -z "$CSRF_TOKEN" ]; then
	echo "FAILED: no CSRF token found in the cookie jar" >&2
	exit 1
fi

log "Proxied GET through the BFF (no bearer token involved)"
ME=$(curl -sf -b "$JAR" "$BFF_BASE_URL/api/v1/me")
echo "$ME" | jq '{email, fullName}'
if [ "$(echo "$ME" | jq -r '.email')" != "$EMAIL" ]; then
	echo "FAILED: /api/v1/me did not return the registered user" >&2
	exit 1
fi
echo "proxy + session-based auth confirmed working end to end"

log "Dashboard aggregation"
DASHBOARD=$(curl -sf -b "$JAR" "$BFF_BASE_URL/bff/dashboard")
echo "$DASHBOARD" | jq '{me: .me.email, accountCount: (.accounts | length)}'

log "Mutation without the CSRF header is rejected"
NO_CSRF_STATUS=$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR" -X POST "$BFF_BASE_URL/api/v1/payees" \
	-H 'Content-Type: application/json' \
	-d '{"name":"Dana","targetAccountNumber":"FB000000000000000000"}')
if [ "$NO_CSRF_STATUS" != "403" ]; then
	echo "FAILED: expected 403 without a CSRF header, got $NO_CSRF_STATUS" >&2
	exit 1
fi
echo "correctly rejected with 403"

log "The same mutation with the CSRF header reaches the real backend"
WITH_CSRF_STATUS=$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR" -X POST "$BFF_BASE_URL/api/v1/payees" \
	-H 'Content-Type: application/json' -H "X-CSRF-Token: $CSRF_TOKEN" -H "Idempotency-Key: $(uuidgen)" \
	-d '{"name":"Dana","targetAccountNumber":"FB000000000000000000"}')
if [ "$WITH_CSRF_STATUS" = "403" ]; then
	echo "FAILED: still rejected as 403 even with a matching CSRF header" >&2
	exit 1
fi
echo "not rejected on CSRF grounds (got $WITH_CSRF_STATUS - the request reached the backend's own validation)"

log "Logout"
LOGOUT_STATUS=$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR" -c "$JAR" -X POST "$BFF_BASE_URL/api/v1/auth/logout" \
	-H "X-CSRF-Token: $CSRF_TOKEN")
if [ "$LOGOUT_STATUS" != "204" ]; then
	echo "FAILED: expected 204 from logout, got $LOGOUT_STATUS" >&2
	exit 1
fi
echo "logged out"

log "A proxied call after logout is rejected"
STALE_STATUS=$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR" "$BFF_BASE_URL/api/v1/me")
if [ "$STALE_STATUS" != "401" ]; then
	echo "FAILED: expected 401 for a proxied call after logout, got $STALE_STATUS" >&2
	exit 1
fi
echo "post-logout call correctly rejected"

echo
echo "All BFF smoke tests passed."
