import { SidebarProvider, SidebarInset, SidebarTrigger } from '@/components/ui/sidebar';
import { Separator } from '@/components/ui/separator';
import { AppSidebar } from '@/components/app-shell/app-sidebar';
import { UserMenu } from '@/components/app-shell/user-menu';
import { ThemeToggle } from '@/components/app-shell/theme-toggle';
import { AuthGate } from '@/components/app-shell/auth-gate';

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <header className="flex h-12 shrink-0 items-center justify-between border-b px-4">
          <div className="flex items-center gap-2">
            <SidebarTrigger />
            <Separator orientation="vertical" className="h-4" />
          </div>
          <div className="flex items-center gap-1">
            <ThemeToggle />
            <UserMenu />
          </div>
        </header>
        <main className="flex flex-1 flex-col p-6">
          <AuthGate>{children}</AuthGate>
        </main>
        {/* Written into by lib/format/announce.ts — the transfer wizard's own receipt
            step, and Phase 8's SSE transaction notifications from any active session. */}
        <div aria-live="polite" className="sr-only" id="balance-announcer" />
      </SidebarInset>
    </SidebarProvider>
  );
}
