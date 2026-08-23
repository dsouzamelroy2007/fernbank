import { NextResponse } from 'next/server';

/** Container/orchestrator healthcheck target only - proves the Next.js server is
 * accepting requests. Not part of the app's real API surface (which all lives on the
 * bff), so it stays outside src/lib/api and carries no auth. */
export function GET() {
  return NextResponse.json({ status: 'ok' });
}
