import type { NextConfig } from 'next';

// Vercel-only: frontend (vercel.app) and bff (fly.dev) are different registrable
// domains in production, so the bff's SameSite=Lax session cookie would never be sent
// on a genuinely cross-site fetch. Proxying these two path prefixes at the edge makes
// the bff appear same-origin to the browser instead - zero changes to the cookie/CSRF
// design in bff/ or backend/. Unset (Docker Compose's default) means no rewrite at
// all; NEXT_PUBLIC_BFF_BASE_URL is then an absolute URL and the browser calls the bff
// directly, cross-origin-but-same-site, exactly as it always has.
const BFF_ORIGIN = process.env.BFF_ORIGIN;

// Every page in the (app) route group renders real account/balance/transaction data.
// Without this, a browser's back-forward cache can restore one of these pages byte-
// for-byte after logout with no network request at all - proxy.ts's cookie check never
// runs, because bfcache restoration isn't a navigation the middleware sees. Cache-
// Control: no-store on the document response is the standard, spec-documented way to
// opt a page out of bfcache entirely (see web.dev's bfcache guidance on pages "with
// sensitive data, e.g. after signing out").
const NO_STORE = { key: 'Cache-Control', value: 'no-store, must-revalidate' };

const nextConfig: NextConfig = {
  // 'standalone' is only for the Docker Compose path - frontend/Dockerfile literally
  // copies .next/standalone into the final image. Vercel has its own serverless
  // packaging and doesn't support this output mode: forcing it broke Vercel's build
  // with a missing .next/next-server.js.nft.json (a Node file-tracing manifest whose
  // location/shape standalone mode changes). VERCEL=1 is auto-set by Vercel on every
  // build, so this needs no configuration on either side.
  output: process.env.VERCEL ? undefined : 'standalone',
  async headers() {
    return [
      { source: '/dashboard', headers: [NO_STORE] },
      { source: '/transfer', headers: [NO_STORE] },
      { source: '/payees', headers: [NO_STORE] },
      { source: '/profile', headers: [NO_STORE] },
      { source: '/accounts/:path*', headers: [NO_STORE] },
    ];
  },
  async rewrites() {
    if (!BFF_ORIGIN) return [];
    return [
      { source: '/api/v1/:path*', destination: `${BFF_ORIGIN}/api/v1/:path*` },
      { source: '/bff/:path*', destination: `${BFF_ORIGIN}/bff/:path*` },
    ];
  },
};

export default nextConfig;
