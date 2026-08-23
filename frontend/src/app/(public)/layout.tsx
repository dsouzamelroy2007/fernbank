import Link from 'next/link';
import { Button } from '@/components/ui/button';

export default function PublicLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-full flex-1 flex-col">
      <header className="flex items-center justify-between border-b px-6 py-4">
        <Link href="/" className="text-lg font-semibold tracking-tight">
          fernbank
        </Link>
        <nav className="flex items-center gap-2">
          <Button variant="ghost" nativeButton={false} render={<Link href="/login" />}>
            Sign in
          </Button>
          <Button nativeButton={false} render={<Link href="/register" />}>
            Register
          </Button>
        </nav>
      </header>
      <main className="flex flex-1 flex-col">{children}</main>
      <footer className="text-muted-foreground border-t px-6 py-4 text-center text-xs">
        fernbank is an educational portfolio project — not a real financial service, not PCI-DSS
        certified, no real KYC/AML. Play money only.
      </footer>
    </div>
  );
}
