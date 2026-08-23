# 1. Record architecture decisions

## Status

Accepted

## Context

fernbank will accumulate decisions — data model, auth strategy, deployment topology —
that are expensive to reverse and not obvious from reading the code alone. Without a
record, later sessions (or reviewers) have to reconstruct the reasoning from scratch.

## Decision

We will use Architecture Decision Records (ADRs), one Markdown file per decision, stored
under `docs/adr/`, numbered sequentially. Each ADR follows the format: Status, Context,
Decision, Consequences.

## Consequences

- Every non-obvious structural choice (JWT vs. sessions, minor-units money, double-entry
  ledger, BFF or not) gets its own ADR before or shortly after it's implemented.
- ADRs are not updated after acceptance; a changed decision gets a new ADR that
  supersedes the old one.
