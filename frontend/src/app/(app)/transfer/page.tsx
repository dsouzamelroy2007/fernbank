'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { toast } from 'sonner';
import { Check } from 'lucide-react';
import { useAccounts } from '@/hooks/queries/use-accounts';
import { usePayees } from '@/hooks/queries/use-payees';
import { useTransfer } from '@/hooks/mutations/use-transfer';
import { useStepUp } from '@/hooks/mutations/use-step-up';
import { transferSchema, type TransferInput } from '@/lib/validation/transfer.schemas';
import { isValidFernbankAccountNumber } from '@/lib/validation/iban';
import { formatMoney } from '@/lib/format/money';
import { announceBalanceUpdate } from '@/lib/format/announce';
import { ApiError, PROBLEM_TYPE } from '@/lib/api/errors';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field, FieldLabel, FieldDescription } from '@/components/ui/field';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Skeleton } from '@/components/ui/skeleton';
import type { components } from '@/lib/api/schema';

type Step = 'source' | 'recipient' | 'amount' | 'review' | 'step-up' | 'receipt';
type TransferResponse = components['schemas']['TransferResponse'];

const WIZARD_STEPS: Step[] = ['source', 'recipient', 'amount', 'review'];

function isStepUpRequired(error: unknown): boolean {
  return error instanceof ApiError && !!error.type?.endsWith(PROBLEM_TYPE.stepUpRequired);
}

export default function TransferPage() {
  const router = useRouter();
  const { data: accounts, isPending: isAccountsPending } = useAccounts();
  const { data: payees, isPending: isPayeesPending } = usePayees();
  const transferMutation = useTransfer();
  const stepUpMutation = useStepUp();

  const [step, setStep] = useState<Step>('source');
  const [sourceAccountId, setSourceAccountId] = useState('');
  const [recipientMode, setRecipientMode] = useState<'payee' | 'new'>('payee');
  const [selectedPayeeId, setSelectedPayeeId] = useState('');
  const [newRecipientNumber, setNewRecipientNumber] = useState('');
  const [amount, setAmount] = useState('');
  const [currency, setCurrency] = useState('USD');
  const [description, setDescription] = useState('');
  const [stepUpCode, setStepUpCode] = useState('');
  const [receipt, setReceipt] = useState<TransferResponse | null>(null);

  const sourceAccount = accounts?.find((a) => a.id === sourceAccountId);
  const destinationAccountNumber =
    recipientMode === 'payee'
      ? (payees?.find((p) => p.id === selectedPayeeId)?.targetAccountNumber ?? '')
      : newRecipientNumber;

  function buildTransferBody(): TransferInput | null {
    const parsed = transferSchema.safeParse({
      sourceAccountId,
      destinationAccountNumber,
      amount: { amount, currency },
      description: description || undefined,
    });
    return parsed.success ? parsed.data : null;
  }

  async function submitTransfer(body: TransferInput) {
    try {
      const result = await transferMutation.mutateAsync(body);
      setReceipt(result);
      setStep('receipt');
      announceBalanceUpdate(
        `Transfer complete. New balance ${formatMoney(result.sourceNewBalance)}.`,
      );
    } catch (error) {
      if (isStepUpRequired(error)) {
        setStep('step-up');
        return;
      }
      const message =
        error instanceof ApiError ? (error.detail ?? error.title ?? error.message) : 'Transfer failed';
      toast.error(message);
    }
  }

  async function handleConfirm() {
    const body = buildTransferBody();
    if (!body) {
      toast.error('Check the transfer details and try again');
      return;
    }
    await submitTransfer(body);
  }

  async function handleStepUp() {
    try {
      // The BFF elevates the session's access token server-side and never returns it
      // to the browser (Phase 8) - nothing to store here, just retry the transfer.
      await stepUpMutation.mutateAsync(stepUpCode);
      const body = buildTransferBody();
      if (body) await submitTransfer(body);
    } catch (error) {
      const message =
        error instanceof ApiError
          ? (error.detail ?? error.title ?? error.message)
          : 'Verification failed';
      toast.error(message);
    }
  }

  if (step === 'receipt' && receipt) {
    return (
      <div className="mx-auto flex max-w-md flex-col gap-6">
        <Card className="rounded-3xl">
          <CardHeader className="items-center text-center">
            <div className="icon-badge mb-2 size-12 rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
              <Check className="size-6" />
            </div>
            <CardTitle>Transfer complete</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3 text-sm">
            <div className="flex justify-between">
              <span className="text-muted-foreground">Transaction ID</span>
              <span className="font-mono text-xs">{receipt.transactionId}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">New source balance</span>
              <span>{formatMoney(receipt.sourceNewBalance)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">Executed at</span>
              <span>{receipt.executedAt ? new Date(receipt.executedAt).toLocaleString() : ''}</span>
            </div>
            <Button className="mt-2" onClick={() => router.push('/dashboard')}>
              Back to dashboard
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  const currentStepIndex = WIZARD_STEPS.indexOf(step);

  return (
    <div className="mx-auto flex max-w-md flex-col gap-6">
      <h1 className="text-3xl font-bold tracking-tight">Transfer money</h1>

      {currentStepIndex >= 0 && (
        <div className="flex gap-1.5">
          {WIZARD_STEPS.map((s, i) => (
            <span
              key={s}
              className={
                i <= currentStepIndex
                  ? 'bg-primary h-1.5 flex-1 rounded-full transition-colors'
                  : 'bg-muted h-1.5 flex-1 rounded-full transition-colors'
              }
            />
          ))}
        </div>
      )}

      <Card className="rounded-3xl">
        <CardContent className="flex flex-col gap-4 pt-6">
          {step === 'source' && (
            <>
              <Field>
                <FieldLabel htmlFor="source">From account</FieldLabel>
                {isAccountsPending ? (
                  <Skeleton className="h-9 w-full" />
                ) : (
                  <select
                    id="source"
                    className="border-input h-9 w-full rounded-md border bg-transparent px-3 text-sm"
                    value={sourceAccountId}
                    onChange={(e) => setSourceAccountId(e.target.value)}
                  >
                    <option value="">Select an account</option>
                    {accounts?.map((account) => (
                      <option key={account.id} value={account.id}>
                        {account.type} · {account.accountNumber} · {formatMoney(account.balance)}
                      </option>
                    ))}
                  </select>
                )}
              </Field>
              <Button disabled={!sourceAccountId} onClick={() => setStep('recipient')}>
                Continue
              </Button>
            </>
          )}

          {step === 'recipient' && (
            <>
              <Tabs value={recipientMode} onValueChange={(v) => setRecipientMode(v as 'payee' | 'new')}>
                <TabsList className="w-full">
                  <TabsTrigger value="payee" className="flex-1">
                    Saved payee
                  </TabsTrigger>
                  <TabsTrigger value="new" className="flex-1">
                    New recipient
                  </TabsTrigger>
                </TabsList>
                <TabsContent value="payee" className="pt-4">
                  {isPayeesPending ? (
                    <Skeleton className="h-9 w-full" />
                  ) : payees && payees.length > 0 ? (
                    <select
                      className="border-input h-9 w-full rounded-md border bg-transparent px-3 text-sm"
                      value={selectedPayeeId}
                      onChange={(e) => setSelectedPayeeId(e.target.value)}
                    >
                      <option value="">Select a payee</option>
                      {payees.map((payee) => (
                        <option key={payee.id} value={payee.id}>
                          {payee.name} · {payee.targetAccountNumber}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <p className="text-muted-foreground text-sm">
                      No saved payees yet — add one from the Payees page, or use a new recipient.
                    </p>
                  )}
                </TabsContent>
                <TabsContent value="new" className="pt-4">
                  <Field>
                    <FieldLabel htmlFor="new-recipient">Recipient account number</FieldLabel>
                    <Input
                      id="new-recipient"
                      value={newRecipientNumber}
                      onChange={(e) => setNewRecipientNumber(e.target.value.toUpperCase())}
                      placeholder="FB..."
                    />
                  </Field>
                </TabsContent>
              </Tabs>
              <div className="flex gap-2">
                <Button variant="outline" onClick={() => setStep('source')}>
                  Back
                </Button>
                <Button
                  className="flex-1"
                  disabled={!isValidFernbankAccountNumber(destinationAccountNumber)}
                  onClick={() => setStep('amount')}
                >
                  Continue
                </Button>
              </div>
            </>
          )}

          {step === 'amount' && (
            <>
              <Field>
                <FieldLabel htmlFor="amount">Amount</FieldLabel>
                <div className="flex gap-2">
                  <Input
                    id="amount"
                    inputMode="decimal"
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                    placeholder="0.00"
                    className="flex-1"
                  />
                  <Input
                    aria-label="Currency"
                    value={currency}
                    onChange={(e) => setCurrency(e.target.value.toUpperCase())}
                    className="w-20"
                  />
                </div>
              </Field>
              <Field>
                <FieldLabel htmlFor="description">Reference (optional)</FieldLabel>
                <Input id="description" value={description} onChange={(e) => setDescription(e.target.value)} />
              </Field>
              <div className="flex gap-2">
                <Button variant="outline" onClick={() => setStep('recipient')}>
                  Back
                </Button>
                <Button className="flex-1" disabled={!buildTransferBody()} onClick={() => setStep('review')}>
                  Review
                </Button>
              </div>
            </>
          )}

          {step === 'review' && (
            <>
              <div className="flex flex-col gap-3 text-sm">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">From</span>
                  <span>
                    {sourceAccount?.type} · {sourceAccount?.accountNumber}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">To</span>
                  <span className="font-mono text-xs">{destinationAccountNumber}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Debit</span>
                  <span className="text-destructive">- {formatMoney({ amount, currency })}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Credit</span>
                  <span className="text-emerald-600 dark:text-emerald-400">
                    + {formatMoney({ amount, currency })}
                  </span>
                </div>
                {description && (
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">Reference</span>
                    <span>{description}</span>
                  </div>
                )}
              </div>
              <div className="flex gap-2">
                <Button variant="outline" onClick={() => setStep('amount')}>
                  Back
                </Button>
                <Button className="flex-1" disabled={transferMutation.isPending} onClick={handleConfirm}>
                  {transferMutation.isPending ? 'Sending…' : 'Confirm transfer'}
                </Button>
              </div>
            </>
          )}

          {step === 'step-up' && (
            <>
              <Field>
                <FieldLabel htmlFor="step-up-code">Authentication code</FieldLabel>
                <FieldDescription>
                  This transfer is over the step-up threshold — enter a fresh code from your
                  authenticator app to continue.
                </FieldDescription>
                <Input
                  id="step-up-code"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  autoFocus
                  value={stepUpCode}
                  onChange={(e) => setStepUpCode(e.target.value)}
                />
              </Field>
              <Button
                disabled={!stepUpCode || stepUpMutation.isPending || transferMutation.isPending}
                onClick={handleStepUp}
              >
                {stepUpMutation.isPending || transferMutation.isPending ? 'Verifying…' : 'Verify and send'}
              </Button>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
