import type { Metadata } from 'next';
import { RegisterForm } from '@/components/auth/register-form';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';

export const metadata: Metadata = { title: 'Register — fernbank' };

export default function RegisterPage() {
  return (
    <div className="relative flex flex-1 items-center justify-center overflow-hidden px-6 py-16">
      <div className="brand-glow pointer-events-none absolute top-[-8rem] left-1/2 h-80 w-80 -translate-x-1/2 rounded-full blur-3xl" />
      <Card className="relative w-full max-w-sm rounded-3xl shadow-xl shadow-primary/10">
        <CardHeader>
          <CardTitle className="text-xl">Create your account</CardTitle>
          <CardDescription>Play money only — no real KYC/AML.</CardDescription>
        </CardHeader>
        <CardContent>
          <RegisterForm />
        </CardContent>
      </Card>
    </div>
  );
}
