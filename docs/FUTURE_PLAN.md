# Future Plan

> **Status: Draft — proposed ideas, not delivery commitments.**
>
> Fernbank is an educational portfolio project that handles play money only. This plan
> describes possible future work; it does not make Fernbank a real banking, payment,
> card-issuing, or foreign-exchange service. Features will be prioritized based on
> learning value, security, and available time.

## Guiding principles

- Correctness and security come before adding features.
- Keep the ledger double-entry, append-only, and reconcilable.
- Make financial behavior explicit: show fees, exchange rates, dates, and status before
  a user confirms an operation.
- Never store real payment-card credentials or handle real funds.
- Prefer a small, well-tested feature over a broad but incomplete implementation.

## Proposed roadmap

### 1. Scheduled and recurring transfers

**Goal:** Make scheduled money movement more complete and predictable.

Scheduled transfers are part of the project’s direction; this phase would focus on
strengthening their lifecycle and adding recurring schedules.

Possible work:

- Add daily, weekly, and monthly recurrence options, with an end date or maximum count.
- Show upcoming, completed, failed, and cancelled transfers.
- Let users edit or cancel a transfer before its execution cutoff.
- Define and document the funding policy:
  - Check available funds when the transfer is created, when it executes, or both.
  - If funds are insufficient at execution, fail safely and explain why.
- Make job execution retry-safe and prevent duplicate postings.
- Add tests for concurrent execution, retries, cancellation, and insufficient funds.

**Done when:** A scheduled or recurring transfer can’t be posted twice, its status is
visible to the user, and its behavior under insufficient funds is documented and tested.

### 2. Monthly statement emails

**Goal:** Give customers a useful monthly summary of account activity.

Possible work:

- Generate a monthly statement for each account.
- Include opening and closing balances, transactions, and the statement period.
- Email a notification when the statement is ready; initially use the local email
  service in development.
- Provide an authenticated in-app download rather than placing sensitive statement
  data or account details in email.
- Make generation safe to retry without sending duplicate statements.
- Add tests for month boundaries, empty periods, and multiple time zones.

**Done when:** A user can access a monthly statement for the correct account and period,
and email notifications don’t expose sensitive financial details.

### 3. Simulated card management

**Goal:** Demonstrate a card-management experience without issuing or processing real
payment cards.

Possible work:

- Create virtual **simulated** cards linked to a Fernbank account.
- Show card status, a masked card number, and a fictional merchant transaction history.
- Support controls such as freeze/unfreeze and spending limits.
- Record simulated authorizations, reversals, and settlements as ledger activity.
- Add audit events for card creation and security-setting changes.

**Safety boundary:** Use generated test data only. Do not store real card numbers, CVVs,
PINs, or other payment credentials. Do not connect to a card network or payment
processor.

**Done when:** The demo clearly labels cards and activity as simulated, and every
balance-impacting event reconciles to ledger entries.

### 4. Multi-currency accounts and simulated FX transfers

**Goal:** Explore how currency conversion affects ledger design and user experience.

Possible work:

- Allow accounts in more than one currency.
- Show an explicit exchange-rate quote, quote expiry, and any simulated fee before
  confirmation.
- Record the rate and original amounts used for each conversion so transactions
  remain auditable.
- Define currency-specific minor-unit handling and rounding rules.
- Post a conversion as balanced entries in each currency using a documented ledger
  model.
- Add tests for rounding, stale quotes, repeated requests, and reconciliation.

**Safety boundary:** Use a clearly identified demo rate source or fixed test rates.
Do not describe simulated rates as executable market quotes or facilitate real FX.

**Done when:** A conversion is understandable to the user, repeatable from its recorded
rate and inputs, and covered by reconciliation tests.

### 5. Notifications and account activity

**Goal:** Help users keep track of important account events.

Possible work:

- Add in-app notifications for transfers, scheduled-transfer failures, and security
  events.
- Let users choose which non-security notifications they receive.
- Keep security notifications enabled where appropriate.
- Avoid including full account numbers, tokens, or unnecessary financial details in
  notification messages.
- Make delivery retry-safe and record delivery status.

**Done when:** Notifications are useful, don’t disclose unnecessary sensitive data, and
can’t cause a financial operation to be repeated.

### 6. Better statements and data export

**Goal:** Make account history easier to inspect and use.

Possible work:

- Add CSV export with a documented format.
- Improve statement filtering by date, transaction type, and status.
- Make timezone and currency presentation consistent across screens and exports.
- Add accessibility and usability checks for transaction tables and downloads.

**Done when:** Exported data matches the account’s recorded activity and is protected
by the same ownership checks as the account view.

## Suggested order

1. Strengthen scheduled-transfer behavior.
2. Add monthly statements and email notifications.
3. Add simulated card management.
4. Improve account activity and exports.
5. Add simulated multi-currency support after the ledger model is designed and reviewed.

This order prioritizes reliability and useful portfolio demonstrations before more
complex ledger changes.

## Out of scope unless the project is deliberately re-scoped

- Real deposits, withdrawals, payment processing, or card issuance.
- Real cardholder data, payment-network connections, or storage of CVV/PIN data.
- Real currency conversion, executable exchange-rate quotes, or custody of funds.
- Claims that Fernbank is a licensed, compliant, or production-ready financial service.

Any change to these boundaries would require a separate design, threat model, and
review; it should not be treated as a routine roadmap feature.