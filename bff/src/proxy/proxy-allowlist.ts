/**
 * Explicit allowlist, not a true catch-all — the proxy can only ever reach these
 * backend resource roots, keeping the BFF from being usable to reach arbitrary backend
 * paths (e.g. /api/v1/admin/**, which stays unreachable through this process).
 */
export const ALLOWED_PROXY_RESOURCES = new Set([
  'accounts',
  'payees',
  'transfers',
  'me',
]);
