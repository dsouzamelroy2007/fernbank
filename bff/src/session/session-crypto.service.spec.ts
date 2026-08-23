import { SessionCryptoService } from './session-crypto.service';

describe('SessionCryptoService', () => {
  const service = new SessionCryptoService();

  it('round-trips a payload through encrypt/decrypt', () => {
    const payload = {
      sessionId: 'abc-123',
      refreshToken: 'refresh-token-value',
    };
    const encrypted = service.encrypt(payload);
    expect(encrypted).not.toContain(payload.refreshToken);
    expect(service.decrypt(encrypted)).toEqual(payload);
  });

  it('returns null for tampered ciphertext', () => {
    const encrypted = service.encrypt({ sessionId: 'a', refreshToken: 'b' });
    const tampered =
      encrypted.slice(0, -4) +
      (encrypted.at(-4) === 'A' ? 'B' : 'A') +
      encrypted.slice(-3);
    expect(service.decrypt(tampered)).toBeNull();
  });

  it('returns null for garbage input', () => {
    expect(service.decrypt('not-a-valid-cookie-value')).toBeNull();
    expect(service.decrypt('')).toBeNull();
  });

  it('produces a different ciphertext each time (random IV)', () => {
    const payload = { sessionId: 'a', refreshToken: 'b' };
    expect(service.encrypt(payload)).not.toEqual(service.encrypt(payload));
  });
});
