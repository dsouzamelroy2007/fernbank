import { z } from 'zod';
import type { components } from '@/lib/api/schema';

type FieldsMatchApi<Zod extends Record<string, unknown>, Api> = {
  [K in keyof Zod]: K extends keyof Api
    ? Zod[K] extends NonNullable<Api[K]>
      ? true
      : false
    : false;
};
type AllTrue<T> = T[keyof T] extends true ? true : false;

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

export const loginSchema = z.object({
  email: z.string().min(1, 'Email is required'),
  password: z.string().min(1, 'Password is required'),
});
export type LoginInput = z.infer<typeof loginSchema>;
assertSchemaMatchesApi<
  AllTrue<FieldsMatchApi<LoginInput, components['schemas']['LoginRequest']>>
>();

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
