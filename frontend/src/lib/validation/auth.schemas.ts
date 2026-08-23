import { z } from 'zod';
import type { components } from '@/lib/api/schema';

/**
 * Compile-time cross-check between a hand-written Zod schema and the OpenAPI-generated
 * request type it's meant to match. openapi-typescript only emits types, not runtime
 * validators, so the schemas below are hand-written — but every field is checked against
 * the generated `components["schemas"][...]` type here, so `tsc --noEmit` (already
 * CI-gating via `npm run typecheck`) fails the moment a field is renamed, removed, or
 * given an incompatible type on either side. The API schema is optional-fields-only
 * (springdoc doesn't declare required-ness), so the check compares each Zod-declared
 * field's value type against `NonNullable<Api[K]>`, not presence/optionality.
 */
type FieldsMatchApi<Zod extends Record<string, unknown>, Api> = {
  [K in keyof Zod]: K extends keyof Api
    ? Zod[K] extends NonNullable<Api[K]>
      ? true
      : false
    : false;
};
type AllTrue<T> = T[keyof T] extends true ? true : false;

/** No-op at runtime — exists purely so an unsatisfied type argument fails the build. */
function assertSchemaMatchesApi<T extends true>(_marker?: T): void {}

export const registerSchema = z.object({
  fullName: z.string().min(1, 'Full name is required'),
  email: z.string().email('Enter a valid email address'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
});
export type RegisterInput = z.infer<typeof registerSchema>;
assertSchemaMatchesApi<
  AllTrue<FieldsMatchApi<RegisterInput, components['schemas']['RegisterRequest']>>
>();

// Backend only enforces @NotBlank on login — no .email()/length constraint beyond that,
// so this schema doesn't invent stricter client-side rules than the API actually enforces.
export const loginSchema = z.object({
  email: z.string().min(1, 'Email is required'),
  password: z.string().min(1, 'Password is required'),
});
export type LoginInput = z.infer<typeof loginSchema>;
assertSchemaMatchesApi<
  AllTrue<FieldsMatchApi<LoginInput, components['schemas']['LoginRequest']>>
>();

// No format pattern on `code` — the backend's own API-verification pass deliberately left
// MFA code format unconstrained to avoid rejecting valid recovery codes; kept consistent here.
export const mfaVerifySchema = z.object({
  mfaToken: z.string().min(1, 'Missing MFA challenge'),
  code: z.string().min(1, 'Enter your authentication code'),
});
export type MfaVerifyInput = z.infer<typeof mfaVerifySchema>;
assertSchemaMatchesApi<
  AllTrue<FieldsMatchApi<MfaVerifyInput, components['schemas']['MfaVerifyRequest']>>
>();

export const mfaEnrollConfirmSchema = z.object({
  code: z.string().min(1, 'Enter your authentication code'),
});
export type MfaEnrollConfirmInput = z.infer<typeof mfaEnrollConfirmSchema>;
assertSchemaMatchesApi<
  AllTrue<FieldsMatchApi<MfaEnrollConfirmInput, components['schemas']['MfaEnrollConfirmRequest']>>
>();
