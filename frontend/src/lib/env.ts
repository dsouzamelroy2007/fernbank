function required(name: string, value: string | undefined): string {
  if (value === undefined) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

export const BFF_BASE_URL = process.env.VERCEL
  ? (process.env.NEXT_PUBLIC_BFF_BASE_URL ?? '')
  : required('NEXT_PUBLIC_BFF_BASE_URL', process.env.NEXT_PUBLIC_BFF_BASE_URL);

export function resolveBffBase(): string {
  return BFF_BASE_URL || window.location.origin;
}

export const BACKEND_HEALTH_URL = process.env.NEXT_PUBLIC_BACKEND_HEALTH_URL || undefined;
