import { describe, expect, it } from 'vitest';
import { changePasswordSchema } from '@/lib/validation/security.schemas';

describe('changePasswordSchema', () => {
  it('accepts matching passwords of sufficient length', () => {
    expect(
      changePasswordSchema.safeParse({
        currentPassword: 'old password',
        newPassword: 'new secure password',
        confirmNewPassword: 'new secure password',
      }).success,
    ).toBe(true);
  });

  it('rejects a confirmation mismatch', () => {
    const result = changePasswordSchema.safeParse({
      currentPassword: 'old password',
      newPassword: 'new secure password',
      confirmNewPassword: 'something else',
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0]?.path).toEqual(['confirmNewPassword']);
    }
  });

  it('rejects a new password shorter than 8 characters', () => {
    expect(
      changePasswordSchema.safeParse({
        currentPassword: 'old password',
        newPassword: 'short',
        confirmNewPassword: 'short',
      }).success,
    ).toBe(false);
  });

  it('rejects a missing current password', () => {
    expect(
      changePasswordSchema.safeParse({
        currentPassword: '',
        newPassword: 'new secure password',
        confirmNewPassword: 'new secure password',
      }).success,
    ).toBe(false);
  });
});
