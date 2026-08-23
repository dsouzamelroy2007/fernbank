#!/usr/bin/env bash
# Curl-based smoke test for the Next.js frontend itself (Phase 6 foundation, updated for
# Phase 8's BFF). The frontend no longer owns any auth logic or API proxying of its own
# — that's scripts/smoke-test-bff.sh's job now — so this script only checks what's left
# on the frontend's side: public pages render, and proxy.ts's cookie-presence gate
# redirects an unauthenticated request away from the (app) route group.
#
# Usage:
#   WEB_BASE_URL=http://localhost:3000 ./scripts/smoke-test-web.sh

set -euo pipefail

WEB_BASE_URL="${WEB_BASE_URL:-http://localhost:3000}"

log() { printf '\n== %s ==\n' "$1"; }
require() {
	if ! command -v "$1" >/dev/null 2>&1; then
		echo "This script needs '$1' on PATH." >&2
		exit 1
	fi
}
require curl

check_status() {
	local path="$1" expected="$2"
	local status
	status=$(curl -s -o /dev/null -w '%{http_code}' "$WEB_BASE_URL$path")
	if [ "$status" != "$expected" ]; then
		echo "FAILED: GET $path expected $expected, got $status" >&2
		exit 1
	fi
	echo "GET $path -> $status, as expected"
}

log "Public pages render"
check_status "/" 200
check_status "/login" 200
check_status "/register" 200

log "proxy.ts redirects an unauthenticated request away from the (app) group"
REDIRECT_HEADERS="$(mktemp)"
trap 'rm -f "$REDIRECT_HEADERS"' EXIT
STATUS=$(curl -s -o /dev/null -D "$REDIRECT_HEADERS" -w '%{http_code}' "$WEB_BASE_URL/dashboard")
if [ "$STATUS" != "307" ] && [ "$STATUS" != "308" ]; then
	echo "FAILED: expected a redirect (307/308) from /dashboard with no session cookie, got $STATUS" >&2
	exit 1
fi
if ! grep -qi 'location:.*\/login?from=%2Fdashboard' "$REDIRECT_HEADERS"; then
	echo "FAILED: expected a redirect to /login?from=%2Fdashboard" >&2
	exit 1
fi
echo "unauthenticated /dashboard correctly redirected to /login"

log "proxy.ts is presence-only - any cookie with the right name passes it through"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' --cookie "fernbank_bff_session=not-a-real-session-value" "$WEB_BASE_URL/dashboard")
if [ "$STATUS" != "200" ]; then
	echo "FAILED: expected 200 (proxy.ts doesn't validate the cookie's contents), got $STATUS" >&2
	exit 1
fi
echo "confirmed presence-only behavior, as documented (the BFF is the real authorization boundary)"

echo
echo "All web smoke tests passed."
