'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { QRCodeSVG } from 'qrcode.react';
import { Monitor, History as HistoryIcon } from 'lucide-react';
import { useSessions } from '@/hooks/queries/use-sessions';
import { useLoginHistory } from '@/hooks/queries/use-login-history';
import { useChangePassword } from '@/hooks/mutations/use-change-password';
import { useRevokeSession } from '@/hooks/mutations/use-revoke-session';
import { useMfaEnroll, useMfaEnrollConfirm } from '@/hooks/mutations/use-mfa-enroll';
import { changePasswordSchema, type ChangePasswordInput } from '@/lib/validation/security.schemas';
import { mfaEnrollConfirmSchema, type MfaEnrollConfirmInput } from '@/lib/validation/auth.schemas';
import { ApiError } from '@/lib/api/errors';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field, FieldLabel, FieldError, FieldGroup } from '@/components/ui/field';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Skeleton } from '@/components/ui/skeleton';

export default function ProfilePage() {
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <h1 className="text-3xl font-bold tracking-tight">Profile & security</h1>
      <Tabs defaultValue="password">
        <TabsList>
          <TabsTrigger value="password">Password</TabsTrigger>
          <TabsTrigger value="mfa">MFA</TabsTrigger>
          <TabsTrigger value="sessions">Sessions</TabsTrigger>
          <TabsTrigger value="history">Login history</TabsTrigger>
        </TabsList>
        <TabsContent value="password" className="pt-4">
          <PasswordTab />
        </TabsContent>
        <TabsContent value="mfa" className="pt-4">
          <MfaTab />
        </TabsContent>
        <TabsContent value="sessions" className="pt-4">
          <SessionsTab />
        </TabsContent>
        <TabsContent value="history" className="pt-4">
          <LoginHistoryTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function PasswordTab() {
  const changePassword = useChangePassword();
  const form = useForm<ChangePasswordInput>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmNewPassword: '' },
  });

  async function onSubmit(values: ChangePasswordInput) {
    try {
      await changePassword.mutateAsync({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      });
      toast.success('Password changed');
      form.reset();
    } catch (error) {
      const message =
        error instanceof ApiError ? (error.detail ?? error.title ?? error.message) : 'Failed to change password';
      toast.error(message);
    }
  }

  return (
    <Card className="rounded-3xl">
      <CardHeader>
        <CardTitle>Change password</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="max-w-sm">
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="currentPassword">Current password</FieldLabel>
              <Input
                id="currentPassword"
                type="password"
                autoComplete="current-password"
                {...form.register('currentPassword')}
              />
              <FieldError
                errors={
                  form.formState.errors.currentPassword ? [form.formState.errors.currentPassword] : undefined
                }
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="newPassword">New password</FieldLabel>
              <Input
                id="newPassword"
                type="password"
                autoComplete="new-password"
                {...form.register('newPassword')}
              />
              <FieldError
                errors={form.formState.errors.newPassword ? [form.formState.errors.newPassword] : undefined}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="confirmNewPassword">Confirm new password</FieldLabel>
              <Input
                id="confirmNewPassword"
                type="password"
                autoComplete="new-password"
                {...form.register('confirmNewPassword')}
              />
              <FieldError
                errors={
                  form.formState.errors.confirmNewPassword
                    ? [form.formState.errors.confirmNewPassword]
                    : undefined
                }
              />
            </Field>
            <Button type="submit" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting ? 'Changing…' : 'Change password'}
            </Button>
          </FieldGroup>
        </form>
      </CardContent>
    </Card>
  );
}

function MfaTab() {
  const enroll = useMfaEnroll();
  const confirm = useMfaEnrollConfirm();
  const [enrollment, setEnrollment] = useState<{ secret?: string; otpAuthUri?: string } | null>(null);
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);
  const [savedRecoveryCodes, setSavedRecoveryCodes] = useState(false);

  const form = useForm<MfaEnrollConfirmInput>({
    resolver: zodResolver(mfaEnrollConfirmSchema),
    defaultValues: { code: '' },
  });

  async function startEnrollment() {
    try {
      const result = await enroll.mutateAsync();
      setEnrollment(result);
    } catch (error) {
      const message =
        error instanceof ApiError ? (error.detail ?? error.title ?? error.message) : 'Failed to start MFA enrolment';
      toast.error(message);
    }
  }

  async function onConfirm(values: MfaEnrollConfirmInput) {
    try {
      const result = await confirm.mutateAsync(values.code);
      setRecoveryCodes(result.recoveryCodes ?? []);
    } catch (error) {
      const message =
        error instanceof ApiError ? (error.detail ?? error.title ?? error.message) : 'Invalid code';
      toast.error(message);
    }
  }

  if (recoveryCodes) {
    return (
      <Card className="rounded-3xl">
        <CardHeader>
          <CardTitle>MFA enabled</CardTitle>
          <CardDescription>
            Save these recovery codes somewhere safe — each one can be used once if you lose
            access to your authenticator app. They won&apos;t be shown again.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <ul className="grid grid-cols-2 gap-2 font-mono text-sm">
            {recoveryCodes.map((code) => (
              <li key={code} className="bg-muted rounded px-2 py-1">
                {code}
              </li>
            ))}
          </ul>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={savedRecoveryCodes}
              onChange={(e) => setSavedRecoveryCodes(e.target.checked)}
            />
            I&apos;ve saved these recovery codes
          </label>
          <Button
            disabled={!savedRecoveryCodes}
            onClick={() => {
              setRecoveryCodes(null);
              setEnrollment(null);
            }}
          >
            Done
          </Button>
        </CardContent>
      </Card>
    );
  }

  if (enrollment) {
    return (
      <Card className="rounded-3xl">
        <CardHeader>
          <CardTitle>Scan this QR code</CardTitle>
          <CardDescription>
            Scan with your authenticator app, then enter the 6-digit code it shows.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col items-center gap-4">
          {enrollment.otpAuthUri && (
            <div className="rounded-md bg-white p-4">
              <QRCodeSVG value={enrollment.otpAuthUri} size={180} />
            </div>
          )}
          <p className="text-muted-foreground max-w-xs break-all text-center font-mono text-xs">
            {enrollment.secret}
          </p>
          <form onSubmit={form.handleSubmit(onConfirm)} noValidate className="w-full max-w-xs">
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="mfa-code">Authentication code</FieldLabel>
                <Input
                  id="mfa-code"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  {...form.register('code')}
                  aria-invalid={!!form.formState.errors.code}
                />
                <FieldError errors={form.formState.errors.code ? [form.formState.errors.code] : undefined} />
              </Field>
              <Button type="submit" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? 'Confirming…' : 'Confirm'}
              </Button>
            </FieldGroup>
          </form>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="rounded-3xl">
      <CardHeader>
        <CardTitle>Multi-factor authentication</CardTitle>
        <CardDescription>
          Add an authenticator app for a second factor at login and for step-up-verified
          high-value transfers.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Button disabled={enroll.isPending} onClick={startEnrollment}>
          {enroll.isPending ? 'Starting…' : 'Enable MFA'}
        </Button>
      </CardContent>
    </Card>
  );
}

function SessionsTab() {
  const { data: sessions, isPending } = useSessions();
  const revokeSession = useRevokeSession();

  async function handleRevoke(sessionId: string) {
    try {
      await revokeSession.mutateAsync(sessionId);
      toast.success('Session revoked');
    } catch (error) {
      const message =
        error instanceof ApiError ? (error.detail ?? error.title ?? error.message) : 'Failed to revoke session';
      toast.error(message);
    }
  }

  return (
    <Card className="rounded-3xl">
      <CardHeader>
        <CardTitle>Active sessions</CardTitle>
        <CardDescription>
          Every device currently signed in. There&apos;s no way to tell which one is this
          device — revoke any you don&apos;t recognize.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {isPending ? (
          <div className="flex flex-col gap-2">
            {[1, 2].map((i) => (
              <Skeleton key={i} className="h-12 w-full" />
            ))}
          </div>
        ) : sessions && sessions.length > 0 ? (
          <ul className="flex flex-col divide-y">
            {sessions.map((session) => (
              <li key={session.id} className="flex items-center gap-3 py-3 text-sm">
                <span className="icon-badge bg-accent text-accent-foreground size-9 rounded-full">
                  <Monitor className="size-4" />
                </span>
                <div className="flex-1">
                  <p>Issued {session.issuedAt ? new Date(session.issuedAt).toLocaleString() : ''}</p>
                  <p className="text-muted-foreground text-xs">
                    Expires {session.expiresAt ? new Date(session.expiresAt).toLocaleString() : ''}
                  </p>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={revokeSession.isPending}
                  onClick={() => handleRevoke(session.id!)}
                >
                  Revoke
                </Button>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-muted-foreground py-8 text-center text-sm">No active sessions.</p>
        )}
      </CardContent>
    </Card>
  );
}

function LoginHistoryTab() {
  const { data, isPending, fetchNextPage, hasNextPage, isFetchingNextPage } = useLoginHistory();
  const events = data?.pages.flatMap((page) => page.data ?? []) ?? [];

  return (
    <Card className="rounded-3xl">
      <CardHeader>
        <CardTitle>Login history</CardTitle>
        <CardDescription>Recent sign-in and security events for your account.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {isPending ? (
          <div className="flex flex-col gap-2">
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </div>
        ) : events.length === 0 ? (
          <p className="text-muted-foreground py-8 text-center text-sm">No login history yet.</p>
        ) : (
          <>
            <ul className="flex flex-col divide-y">
              {events.map((event) => (
                <li key={event.id} className="flex items-center gap-3 py-2 text-sm">
                  <span className="icon-badge bg-muted text-muted-foreground size-8 rounded-full">
                    <HistoryIcon className="size-3.5" />
                  </span>
                  <span className="flex-1">{event.eventType}</span>
                  <span className="text-muted-foreground text-xs">
                    {event.createdAt ? new Date(event.createdAt).toLocaleString() : ''}
                  </span>
                </li>
              ))}
            </ul>
            {hasNextPage && (
              <Button variant="outline" disabled={isFetchingNextPage} onClick={() => fetchNextPage()}>
                {isFetchingNextPage ? 'Loading…' : 'Load more'}
              </Button>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
