import type { SessionPayload } from './session-crypto.service';

declare global {
  namespace Express {
    interface Request {
      /** Decrypted session cookie payload for this request, or null if absent/invalid/tampered. */
      fernbankSession?: SessionPayload | null;
    }
  }
}

export {};
