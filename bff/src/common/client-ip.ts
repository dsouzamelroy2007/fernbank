import type { Request } from 'express';

export function extractClientIp(req: Request): string {
  const header = req.headers['x-forwarded-for'];
  const value = Array.isArray(header) ? header[0] : header;
  const firstHop = value?.split(',')[0]?.trim();
  return firstHop && firstHop.length > 0 ? firstHop : (req.ip ?? '');
}
