/** Writes into the (app)/layout.tsx aria-live region so screen readers announce balance changes. */
export function announceBalanceUpdate(message: string) {
  const el = document.getElementById('balance-announcer');
  if (el) el.textContent = message;
}
