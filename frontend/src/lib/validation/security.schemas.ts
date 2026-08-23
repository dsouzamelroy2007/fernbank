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

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Enter your current password'),
    newPassword: z.string().min(8, 'Password must be at least 8 characters'),
    confirmNewPassword: z.string().min(1, 'Confirm your new password'),
  })
  .refine((data) => data.newPassword === data.confirmNewPassword, {
    message: "Passwords don't match",
    path: ['confirmNewPassword'],
  });
export type ChangePasswordInput = z.infer<typeof changePasswordSchema>;
// confirmNewPassword is a client-only field (never sent to the API), so only the two
// fields the backend actually declares are checked against ChangePasswordRequest.
assertSchemaMatchesApi<
  AllTrue<
    FieldsMatchApi<
      Pick<ChangePasswordInput, 'currentPassword' | 'newPassword'>,
      components['schemas']['ChangePasswordRequest']
    >
  >
>();
