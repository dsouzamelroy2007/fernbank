import type { Metadata } from 'next';
import { Suspense } from 'react';
import { LoginForm } from '@/components/auth/login-form';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';

export const metadata: Metadata = { title: 'Sign in — fernbank' };

export default function LoginPage() {
  return (
    <div className="relative flex flex-1 items-center justify-center overflow-hidden px-6 py-16">
      <div className="brand-glow pointer-events-none absolute top-[-8rem] left-1/2 h-80 w-80 -translate-x-1/2 rounded-full blur-3xl" />
      <Card className="relative w-full max-w-sm rounded-3xl shadow-xl shadow-primary/10">
        <CardHeader>
          <CardTitle className="text-xl">Sign in</CardTitle>
          <CardDescription>Welcome back to fernbank.</CardDescription>
        </CardHeader>
        <CardContent>
          {/* useSearchParams() (reading ?reason=idle) requires a Suspense boundary to
              statically prerender this otherwise-static page. */}
          <Suspense fallback={<Skeleton className="h-64 w-full" />}>
            <LoginForm />
          </Suspense>
        </CardContent>
      </Card>
    </div>
  );
}
