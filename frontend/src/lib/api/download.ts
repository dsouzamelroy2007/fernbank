import { resolveBffBase } from '@/lib/env';
import { ApiError } from '@/lib/api/errors';

/** Downloads a file from the BFF (e.g. statement export) — browsers ignore the `download`
 * attribute on a cross-origin <a href> (the BFF is a different origin from the
 * frontend), so a plain link can't force a save here; this fetches the bytes
 * (credentialed — the BFF's session cookie, no CSRF header needed since GET is a safe
 * method) and triggers the save manually instead. */
export async function downloadFile(path: string, filename: string): Promise<void> {
  const response = await fetch(new URL(path, resolveBffBase()), {
    credentials: 'include',
  });
  if (!response.ok) {
    throw await ApiError.fromResponse(response);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
