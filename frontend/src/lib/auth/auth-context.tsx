'use client';

import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { apiFetch } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

type MeResponse = components['schemas']['MeResponse'];

export type LoginResult =
  { status: 'AUTHENTICATED' } | { status: 'MFA_REQUIRED'; mfaToken: string };

interface AuthContextValue {
  user: MeResponse | null;
  isAuthenticated: boolean;
  /** True until the initial bootstrap load-user attempt has resolved one way or the
   * other. The BFF's session cookie makes the session durable across a hard reload —
   * there's no client-side token to restore, just an httpOnly cookie the browser
   * already sends automatically, so bootstrap is simply "try loading /me". */
  isBootstrapping: boolean;
  login: (email: string, password: string) => Promise<LoginResult>;
  verifyMfa: (mfaToken: string, code: string) => Promise<void>;
  register: (fullName: string, email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  loadUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<MeResponse | null>(null);
  const [isBootstrapping, setIsBootstrapping] = useState(true);
  const bootstrapped = useRef(false);

  const loadUser = useCallback(async () => {
    try {
      const me = await apiFetch('get', '/api/v1/me');
      setUser(me);
    } catch {
      setUser(null);
    }
  }, []);

  useEffect(() => {
    if (bootstrapped.current) return;
    bootstrapped.current = true;

    void loadUser().finally(() => setIsBootstrapping(false));
  }, [loadUser]);

  const login = useCallback(
    async (email: string, password: string): Promise<LoginResult> => {
      const body = await apiFetch('post', '/api/v1/auth/login', { body: { email, password } });
      if (body.status === 'AUTHENTICATED') {
        await loadUser();
        return { status: 'AUTHENTICATED' };
      }
      return { status: 'MFA_REQUIRED', mfaToken: body.mfaToken! };
    },
    [loadUser],
  );

  const verifyMfa = useCallback(
    async (mfaToken: string, code: string) => {
      const body = await apiFetch('post', '/api/v1/auth/mfa/verify', {
        body: { mfaToken, code },
      });
      if (body.status === 'AUTHENTICATED') {
        await loadUser();
      }
    },
    [loadUser],
  );

  const register = useCallback(async (fullName: string, email: string, password: string) => {
    await apiFetch('post', '/api/v1/auth/register', { body: { fullName, email, password } });
  }, []);

  const logout = useCallback(async () => {
    // The BFF reads the refresh token from the session cookie itself - no body needed.
    // Logout must always succeed from the user's perspective even if this call fails.
    await apiFetch('post', '/api/v1/auth/logout').catch(() => undefined);
    setUser(null);
  }, []);

  const value: AuthContextValue = {
    user,
    isAuthenticated: user !== null,
    isBootstrapping,
    login,
    verifyMfa,
    register,
    logout,
    loadUser,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuthContext(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuthContext must be used within an AuthProvider');
  }
  return context;
}
