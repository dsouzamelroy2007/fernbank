/** Reads the `exp` claim off an already-trusted JWT (issued by our own call to Spring
 * Boot) without verifying its signature — used only as a caching hint, never a trust
 * decision, so signature verification would be unnecessary complexity here. */
export function decodeJwtExpiryMs(token: string): number | null {
  try {
    const payloadSegment = token.split('.')[1];
    const payload = JSON.parse(
      Buffer.from(payloadSegment, 'base64url').toString('utf8'),
    ) as {
      exp?: number;
    };
    return typeof payload.exp === 'number' ? payload.exp * 1000 : null;
  } catch {
    return null;
  }
}
