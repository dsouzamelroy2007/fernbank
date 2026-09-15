import type { SessionPayload } from './session-crypto.service';

declare global {
  namespace Express {
    interface Request {
      fernbankSession?: SessionPayload | null;
    }
  }
}

export {};
