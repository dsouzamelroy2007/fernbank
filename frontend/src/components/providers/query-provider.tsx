'use client';

import { useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

export function QueryProvider({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { staleTime: 30_000, retry: 1 },
          // Money moves must never be auto-retried by the query cache — a retry here
          // doesn't know about Idempotency-Key semantics, which is exactly the class of
          // bug that header exists to prevent. Optimistic updates are simply never used
          // for mutations either, so there's no "cancelled query" cleanup to opt out of.
          mutations: { retry: false },
        },
      }),
  );

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
