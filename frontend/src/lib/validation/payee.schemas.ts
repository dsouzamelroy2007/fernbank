import { z } from 'zod';
import type { components } from '@/lib/api/schema';
import { isValidFernbankAccountNumber } from '@/lib/validation/iban';

type FieldsMatchApi<Zod extends Record<string, unknown>, Api> = {
  [K in keyof Zod]: K extends keyof Api
    ? Zod[K] extends NonNullable<Api[K]>
      ? true
      : false
    : false;
};
type AllTrue<T> = T[keyof T] extends true ? true : false;
function assertSchemaMatchesApi<T extends true>(_marker?: T): void {}

export const accountNumberSchema = z
  .string()
  .min(1, 'Enter an account number')
  .refine(isValidFernbankAccountNumber, 'Not a valid fernbank account number');

export const payeeSchema = z.object({
  name: z.string().min(1, 'Enter a name for this payee'),
  targetAccountNumber: accountNumberSchema,
});
export type PayeeInput = z.infer<typeof payeeSchema>;
assertSchemaMatchesApi<AllTrue<FieldsMatchApi<PayeeInput, components['schemas']['PayeeRequest']>>>();
