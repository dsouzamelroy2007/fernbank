'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { loginSchema, type LoginInput } from '@/lib/validation/auth.schemas';
import { useAuth } from '@/hooks/use-auth';
import { ApiError } from '@/lib/api/errors';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field, FieldLabel, FieldError, FieldGroup } from '@/components/ui/field';
import { MfaVerifyForm } from '@/components/auth/mfa-verify-form';

export function LoginForm() {
  const { login } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  // Kept in component state, never the URL — an MFA challenge token has no business
  // showing up in browser history or a referrer header.
  const [mfaToken, setMfaToken] = useState<string | null>(null);

  useEffect(() => {
    if (searchParams.get('reason') === 'idle') {
      toast.info("You were signed out after 5 minutes of inactivity.");
    }
  }, [searchParams]);

  const form = useForm<LoginInput>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  });

  async function onSubmit(values: LoginInput) {
    try {
      const result = await login(values.email, values.password);
      if (result.status === 'AUTHENTICATED') {
        router.push('/dashboard');
      } else {
        setMfaToken(result.mfaToken);
      }
    } catch (error) {
      const message =
        error instanceof ApiError
          ? (error.detail ?? error.title ?? error.message)
          : 'Something went wrong';
      toast.error(message);
    }
  }

  if (mfaToken) {
    return <MfaVerifyForm mfaToken={mfaToken} onBack={() => setMfaToken(null)} />;
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
      <FieldGroup>
        <Field>
          <FieldLabel htmlFor="email">Email</FieldLabel>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            autoFocus
            {...form.register('email')}
            aria-invalid={!!form.formState.errors.email}
          />
          <FieldError
            errors={form.formState.errors.email ? [form.formState.errors.email] : undefined}
          />
        </Field>
        <Field>
          <FieldLabel htmlFor="password">Password</FieldLabel>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            {...form.register('password')}
            aria-invalid={!!form.formState.errors.password}
          />
          <FieldError
            errors={form.formState.errors.password ? [form.formState.errors.password] : undefined}
          />
        </Field>
        <Button type="submit" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting ? 'Signing in…' : 'Sign in'}
        </Button>
        <p className="text-muted-foreground text-center text-sm">
          Don&apos;t have an account?{' '}
          <Link href="/register" className="text-primary underline underline-offset-4">
            Register
          </Link>
        </p>
      </FieldGroup>
    </form>
  );
}
