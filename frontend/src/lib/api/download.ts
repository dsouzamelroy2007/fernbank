import { resolveBffBase } from '@/lib/env';
import { ApiError } from '@/lib/api/errors';

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
