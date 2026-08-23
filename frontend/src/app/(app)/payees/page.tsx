'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { usePayees } from '@/hooks/queries/use-payees';
import { useAddPayee } from '@/hooks/mutations/use-add-payee';
import { useDeletePayee } from '@/hooks/mutations/use-delete-payee';
import { payeeSchema, type PayeeInput } from '@/lib/validation/payee.schemas';
import { ApiError } from '@/lib/api/errors';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field, FieldLabel, FieldError, FieldGroup } from '@/components/ui/field';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';

export default function PayeesPage() {
  const { data: payees, isPending } = usePayees();
  const addPayee = useAddPayee();
  const deletePayee = useDeletePayee();

  const [addOpen, setAddOpen] = useState(false);
  const [pendingPayee, setPendingPayee] = useState<PayeeInput | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<{ id: string; name: string } | null>(null);

  const form = useForm<PayeeInput>({
    resolver: zodResolver(payeeSchema),
    defaultValues: { name: '', targetAccountNumber: '' },
  });

  function onSubmitDetails(values: PayeeInput) {
    setPendingPayee(values);
  }

  async function confirmAddPayee() {
    if (!pendingPayee) return;
    try {
      await addPayee.mutateAsync(pendingPayee);
      toast.success('Payee added');
      setAddOpen(false);
      setPendingPayee(null);
      form.reset();
    } catch (error) {
      const message =
        error instanceof ApiError ? (error.detail ?? error.title ?? error.message) : 'Failed to add payee';
      toast.error(message);
    }
  }

  async function confirmDeletePayee() {
    if (!deleteTarget) return;
    try {
      await deletePayee.mutateAsync(deleteTarget.id);
      toast.success('Payee deleted');
    } catch (error) {
      const message =
        error instanceof ApiError ? (error.detail ?? error.title ?? error.message) : 'Failed to delete payee';
      toast.error(message);
    } finally {
      setDeleteTarget(null);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold tracking-tight">Payees</h1>
        <Dialog
          open={addOpen}
          onOpenChange={(open) => {
            setAddOpen(open);
            if (!open) {
              setPendingPayee(null);
              form.reset();
            }
          }}
        >
          <Button onClick={() => setAddOpen(true)}>Add payee</Button>
          <DialogContent>
            {!pendingPayee ? (
              <>
                <DialogHeader>
                  <DialogTitle>Add a payee</DialogTitle>
                  <DialogDescription>
                    Save a recipient&apos;s account number so you can transfer to them later
                    without retyping it.
                  </DialogDescription>
                </DialogHeader>
                <form onSubmit={form.handleSubmit(onSubmitDetails)} noValidate>
                  <FieldGroup>
                    <Field>
                      <FieldLabel htmlFor="name">Name</FieldLabel>
                      <Input id="name" {...form.register('name')} autoFocus />
                      <FieldError
                        errors={form.formState.errors.name ? [form.formState.errors.name] : undefined}
                      />
                    </Field>
                    <Field>
                      <FieldLabel htmlFor="targetAccountNumber">Account number</FieldLabel>
                      <Input
                        id="targetAccountNumber"
                        placeholder="FB..."
                        {...form.register('targetAccountNumber')}
                        onChange={(e) => form.setValue('targetAccountNumber', e.target.value.toUpperCase())}
                      />
                      <FieldError
                        errors={
                          form.formState.errors.targetAccountNumber
                            ? [form.formState.errors.targetAccountNumber]
                            : undefined
                        }
                      />
                    </Field>
                    <Button type="submit">Continue</Button>
                  </FieldGroup>
                </form>
              </>
            ) : (
              <>
                <DialogHeader>
                  <DialogTitle>Confirm payee</DialogTitle>
                  <DialogDescription>Double-check this before saving.</DialogDescription>
                </DialogHeader>
                <div className="flex flex-col gap-2 text-sm">
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">Name</span>
                    <span>{pendingPayee.name}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">Account number</span>
                    <span className="font-mono text-xs">{pendingPayee.targetAccountNumber}</span>
                  </div>
                </div>
                <DialogFooter>
                  <Button variant="outline" onClick={() => setPendingPayee(null)}>
                    Back
                  </Button>
                  <Button disabled={addPayee.isPending} onClick={confirmAddPayee}>
                    {addPayee.isPending ? 'Saving…' : 'Save payee'}
                  </Button>
                </DialogFooter>
              </>
            )}
          </DialogContent>
        </Dialog>
      </div>

      <Card className="rounded-3xl">
        <CardHeader>
          <CardTitle>Saved payees</CardTitle>
        </CardHeader>
        <CardContent>
          {isPending ? (
            <div className="flex flex-col gap-2">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : payees && payees.length > 0 ? (
            <ul className="flex flex-col divide-y">
              {payees.map((payee) => (
                <li key={payee.id} className="flex items-center gap-3 py-3 text-sm">
                  <span className="icon-badge bg-accent text-accent-foreground size-9 rounded-full text-xs font-semibold">
                    {(payee.name ?? '?').slice(0, 2).toUpperCase()}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">{payee.name}</p>
                    <p className="text-muted-foreground truncate font-mono text-xs">
                      {payee.targetAccountNumber}
                    </p>
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setDeleteTarget({ id: payee.id!, name: payee.name ?? '' })}
                  >
                    Delete
                  </Button>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-muted-foreground py-8 text-center text-sm">
              No saved payees yet — add one to make transfers faster.
            </p>
          )}
        </CardContent>
      </Card>

      <Dialog open={!!deleteTarget} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete payee?</DialogTitle>
            <DialogDescription>
              This removes {deleteTarget?.name} from your saved payees. You can add them
              again later.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteTarget(null)}>
              Cancel
            </Button>
            <Button variant="destructive" disabled={deletePayee.isPending} onClick={confirmDeletePayee}>
              {deletePayee.isPending ? 'Deleting…' : 'Delete'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
