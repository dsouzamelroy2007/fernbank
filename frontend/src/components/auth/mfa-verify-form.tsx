'use client';

import { useRouter } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { mfaVerifySchema, type MfaVerifyInput } from '@/lib/validation/auth.schemas';
import { useAuth } from '@/hooks/use-auth';
import { ApiError } from '@/lib/api/errors';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field, FieldLabel, FieldError, FieldGroup, FieldDescription } from '@/components/ui/field';

export function MfaVerifyForm({ mfaToken, onBack }: { mfaToken: string; onBack: () => void }) {
  const { verifyMfa } = useAuth();
  const router = useRouter();

  const form = useForm<MfaVerifyInput>({
    resolver: zodResolver(mfaVerifySchema),
    defaultValues: { mfaToken, code: '' },
  });

  async function onSubmit(values: MfaVerifyInput) {
    try {
      await verifyMfa(values.mfaToken, values.code);
      router.push('/dashboard');
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
          <FieldLabel htmlFor="code">Authentication code</FieldLabel>
          <FieldDescription>Enter the 6-digit code from your authenticator app.</FieldDescription>
          <Input
            id="code"
            inputMode="numeric"
            autoComplete="one-time-code"
            autoFocus
            {...form.register('code')}
            aria-invalid={!!form.formState.errors.code}
          />
          <FieldError
            errors={form.formState.errors.code ? [form.formState.errors.code] : undefined}
          />
        </Field>
        <Button type="submit" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting ? 'Verifying…' : 'Verify'}
        </Button>
        <Button type="button" variant="ghost" onClick={onBack}>
          Back to sign in
        </Button>
      </FieldGroup>
    </form>
  );
}
