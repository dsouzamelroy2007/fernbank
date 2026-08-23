import Link from 'next/link';
import { BookOpenCheck, RefreshCcw, ShieldCheck } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';

const FEATURES = [
  {
    title: 'Double-entry ledger',
    description: 'Every balance change is an append-only transaction that nets to zero.',
    icon: BookOpenCheck,
  },
  {
    title: 'MFA & step-up auth',
    description:
      'TOTP-based multi-factor authentication, with step-up verification on large transfers.',
    icon: ShieldCheck,
  },
  {
    title: 'Idempotent by design',
    description:
      'Every mutating request carries an idempotency key — safe to retry, never double-applied.',
    icon: RefreshCcw,
  },
];

export default function LandingPage() {
  return (
    <div className="relative flex flex-1 flex-col items-center justify-center gap-12 overflow-hidden px-6 py-16">
      <div className="brand-glow pointer-events-none absolute top-[-10rem] left-1/2 h-96 w-96 -translate-x-1/2 rounded-full blur-3xl" />
      <div className="relative flex max-w-xl flex-col items-center gap-4 text-center">
        <h1 className="brand-gradient-text text-5xl font-bold tracking-tight">fernbank</h1>
        <p className="text-muted-foreground text-lg">
          A portfolio-quality double-entry ledger banking application — built to show the security
          and correctness practices a real financial system needs.
        </p>
        <div className="flex gap-3">
          <Button size="lg" nativeButton={false} render={<Link href="/register" />}>
            Get started
          </Button>
          <Button size="lg" variant="outline" nativeButton={false} render={<Link href="/login" />}>
            Sign in
          </Button>
        </div>
      </div>
      <div className="relative grid w-full max-w-3xl gap-4 sm:grid-cols-3">
        {FEATURES.map((feature) => (
          <Card key={feature.title} className="rounded-3xl">
            <CardHeader>
              <div className="icon-badge bg-accent text-accent-foreground mb-2 size-10 rounded-full">
                <feature.icon className="size-5" />
              </div>
              <CardTitle className="text-base">{feature.title}</CardTitle>
              <CardDescription>{feature.description}</CardDescription>
            </CardHeader>
          </Card>
        ))}
      </div>
    </div>
  );
}
