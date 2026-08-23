import { Injectable } from '@nestjs/common';
import { createCipheriv, createDecipheriv, randomBytes } from 'node:crypto';
import { config } from '../config/configuration';

const ALGORITHM = 'aes-256-gcm';
const IV_LENGTH = 12;
const AUTH_TAG_LENGTH = 16;

export interface SessionPayload {
  sessionId: string;
  refreshToken: string;
}

/** AES-256-GCM encrypt/decrypt of the session cookie payload — same pattern as the
 * backend's own MfaSecretConverter (pure JDK/Node crypto, no new dependency). */
@Injectable()
export class SessionCryptoService {
  private readonly key: Buffer;

  constructor() {
    this.key = Buffer.from(config.sessionEncryptionKey, 'base64');
    if (this.key.length !== 32) {
      throw new Error(
        `BFF_SESSION_ENCRYPTION_KEY must decode to exactly 32 bytes, got ${this.key.length}`,
      );
    }
  }

  encrypt(payload: SessionPayload): string {
    const iv = randomBytes(IV_LENGTH);
    const cipher = createCipheriv(ALGORITHM, this.key, iv);
    const ciphertext = Buffer.concat([
      cipher.update(Buffer.from(JSON.stringify(payload), 'utf8')),
      cipher.final(),
    ]);
    const authTag = cipher.getAuthTag();
    return Buffer.concat([iv, authTag, ciphertext]).toString('base64url');
  }

  /** Returns null on any decryption/tamper/parse failure — callers treat this as "no session". */
  decrypt(cookieValue: string): SessionPayload | null {
    try {
      const raw = Buffer.from(cookieValue, 'base64url');
      const iv = raw.subarray(0, IV_LENGTH);
      const authTag = raw.subarray(IV_LENGTH, IV_LENGTH + AUTH_TAG_LENGTH);
      const ciphertext = raw.subarray(IV_LENGTH + AUTH_TAG_LENGTH);
      const decipher = createDecipheriv(ALGORITHM, this.key, iv);
      decipher.setAuthTag(authTag);
      const plaintext = Buffer.concat([
        decipher.update(ciphertext),
        decipher.final(),
      ]);
      const parsed = JSON.parse(plaintext.toString('utf8')) as SessionPayload;
      if (
        typeof parsed.sessionId !== 'string' ||
        typeof parsed.refreshToken !== 'string'
      ) {
        return null;
      }
      return parsed;
    } catch {
      return null;
    }
  }
}
