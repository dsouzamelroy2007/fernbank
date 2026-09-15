export function announceBalanceUpdate(message: string) {
  const el = document.getElementById('balance-announcer');
  if (el) el.textContent = message;
}
