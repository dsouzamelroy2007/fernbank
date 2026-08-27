'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { Loader2 } from 'lucide-react';
import { registerSchema, type RegisterInput } from '@/lib/validation/auth.schemas';
import { useAuth } from '@/hooks/use-auth';
import { useBackendWarmup } from '@/hooks/use-backend-warmup';
import { ApiError } from '@/lib/api/errors';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field, FieldLabel, FieldError, FieldGroup } from '@/components/ui/field';

export function RegisterForm() {
  const { register: registerUser } = useAuth();
  const router = useRouter();
  const backendReady = useBackendWarmup();

  const form = useForm<RegisterInput>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: '', email: '', password: '' },
  });

  async function onSubmit(values: RegisterInput) {
    try {
      await registerUser(values.fullName, values.email, values.password);
      toast.success('Account created — sign in to continue.');
      router.push('/login');
    } catch (error) {
      const message =
        error instanceof ApiError
          ? (error.detail ?? error.title ?? error.message)
          : 'Something went wrong';
      toast.error(message);
    }
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
      <FieldGroup>
        <Field>
          <FieldLabel htmlFor="fullName">Full name</FieldLabel>
          <Input
            id="fullName"
            autoComplete="name"
            autoFocus
            {...form.register('fullName')}
            aria-invalid={!!form.formState.errors.fullName}
          />
          <FieldError
            errors={form.formState.errors.fullName ? [form.formState.errors.fullName] : undefined}
          />
        </Field>
        <Field>
          <FieldLabel htmlFor="email">Email</FieldLabel>
          <Input
            id="email"
            type="email"
            autoComplete="email"
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
            autoComplete="new-password"
            {...form.register('password')}
            aria-invalid={!!form.formState.errors.password}
          />
          <FieldError
            errors={form.formState.errors.password ? [form.formState.errors.password] : undefined}
          />
        </Field>
        {!backendReady && (
          <p className="text-muted-foreground flex items-center gap-2 text-sm">
            <Loader2 className="size-4 animate-spin" aria-hidden />
            Waking up the demo servers — this can take a few minutes on the free tier.
          </p>
        )}
        <Button type="submit" disabled={!backendReady || form.formState.isSubmitting}>
          {!backendReady
            ? 'Waking up…'
            : form.formState.isSubmitting
              ? 'Creating account…'
              : 'Create account'}
        </Button>
        <p className="text-muted-foreground text-center text-sm">
          Already have an account?{' '}
          <Link href="/login" className="text-primary underline underline-offset-4">
            Sign in
          </Link>
        </p>
      </FieldGroup>
    </form>
  );
}
