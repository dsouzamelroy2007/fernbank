import { z } from 'zod';
import type { components } from '@/lib/api/schema';
import { isValidFernbankAccountNumber } from '@/lib/validation/iban';

// Two changes from auth.schemas.ts's copy, both needed because transferSchema has
// genuinely optional fields (destinationAccountId/Number) where none of the existing
// schemas did: (1) NonNullable<Zod[K]> strips `| undefined` so optionality doesn't block
// the value-type comparison, and (2) the `-?` modifier stops this homomorphic mapped
// type from re-inheriting Zod's own optional modifier onto the *result* - without it,
// `AllTrue` would index into a partly-optional `{ ...: true }` type, produce
// `true | undefined`, and always fail even when every field genuinely matches.
type FieldsMatchApi<Zod extends Record<string, unknown>, Api> = {
  [K in keyof Zod]-?: K extends keyof Api
    ? NonNullable<Zod[K]> extends NonNullable<Api[K]>
      ? true
      : false
    : false;
};
type AllTrue<T> = T[keyof T] extends true ? true : false;
function assertSchemaMatchesApi<T extends true>(_marker?: T): void {}

export const moneyDtoSchema = z.object({
  amount: z
    .string()
    .min(1, 'Enter an amount')
    .refine((value) => Number.isFinite(Number(value)) && Number(value) > 0, 'Amount must be greater than zero'),
  currency: z
    .string()
    .regex(/^[A-Z]{3}$/, 'Currency must be a 3-letter ISO code'),
});
export type MoneyDtoInput = z.infer<typeof moneyDtoSchema>;
assertSchemaMatchesApi<AllTrue<FieldsMatchApi<MoneyDtoInput, components['schemas']['MoneyDto']>>>();

// destinationAccountId XOR destinationAccountNumber is enforced imperatively server-side
// (api.DestinationAccountResolver) rather than declared on the schema - mirrored here via
// .refine() so the wizard can't submit an impossible combination.
export const transferSchema = z
  .object({
    sourceAccountId: z.string().uuid(),
    destinationAccountId: z.string().uuid().optional(),
    destinationAccountNumber: z
      .string()
      .refine(isValidFernbankAccountNumber, 'Not a valid fernbank account number')
      .optional(),
    amount: moneyDtoSchema,
    description: z.string().optional(),
  })
  .refine((data) => !!data.destinationAccountId !== !!data.destinationAccountNumber, {
    message: 'Choose a saved payee or enter a new recipient, not both',
    path: ['destinationAccountNumber'],
  });
export type TransferInput = z.infer<typeof transferSchema>;
assertSchemaMatchesApi<AllTrue<FieldsMatchApi<TransferInput, components['schemas']['TransferRequest']>>>();
