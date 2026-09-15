import { Injectable } from '@nestjs/common';
import { Observable, Subject } from 'rxjs';
import { config } from '../config/configuration';
import { BackendClientService } from '../backend-client/backend-client.service';
import type { SessionPayload } from '../session/session-crypto.service';
import type {
  AccountResponse,
  CursorPage,
  StatementEntryResponse,
} from '../dashboard/dashboard.types';

export interface NotificationEvent {
  accountId: string;
  entry: StatementEntryResponse;
}

interface PollerState {
  refCount: number;
  intervalId: NodeJS.Timeout;
  subject: Subject<NotificationEvent>;
  lastSeenByAccount: Map<string, string>;
}

@Injectable()
export class NotificationPollerService {
  private readonly pollers = new Map<string, PollerState>();

  constructor(private readonly backendClient: BackendClientService) {}

  subscribe(session: SessionPayload): Observable<NotificationEvent> {
    let state = this.pollers.get(session.sessionId);
    if (!state) {
      const newState: PollerState = {
        refCount: 0,
        subject: new Subject<NotificationEvent>(),
        lastSeenByAccount: new Map(),
        intervalId: null as unknown as NodeJS.Timeout,
      };
      newState.intervalId = setInterval(
        () => void this.poll(session, newState),
        config.notificationPollIntervalMs,
      ).unref();
      this.pollers.set(session.sessionId, newState);
      void this.seedLastSeen(session, newState);
      state = newState;
    }
    state.refCount++;
    const activeState = state;

    return new Observable<NotificationEvent>((subscriber) => {
      const subscription = activeState.subject.subscribe(subscriber);
      return () => {
        subscription.unsubscribe();
        activeState.refCount--;
        if (activeState.refCount <= 0) {
          clearInterval(activeState.intervalId);
          this.pollers.delete(session.sessionId);
        }
      };
    });
  }

  private async seedLastSeen(
    session: SessionPayload,
    state: PollerState,
  ): Promise<void> {
    try {
      const accounts = await this.listAccounts(session);
      await Promise.all(
        accounts.map(async (account) => {
          if (!account.id) return;
          const latest = await this.latestEntry(session, account.id);
          if (latest?.id) {
            state.lastSeenByAccount.set(account.id, latest.id);
          }
        }),
      );
    } catch {
      // empty
    }
  }

  private async poll(
    session: SessionPayload,
    state: PollerState,
  ): Promise<void> {
    try {
      const accounts = await this.listAccounts(session);
      for (const account of accounts) {
        if (!account.id) continue;
        const page = await this.backendClient.requestJson<
          CursorPage<StatementEntryResponse>
        >({
          session,
          method: 'GET',
          path: `/api/v1/accounts/${account.id}/statement`,
          query: { pageSize: 5 },
        });
        const entries = page.data ?? [];
        const lastSeenId = state.lastSeenByAccount.get(account.id);
        const newEntries = lastSeenId ? takeUntilId(entries, lastSeenId) : [];
        for (const entry of newEntries.reverse()) {
          state.subject.next({ accountId: account.id, entry });
        }
        if (entries[0]?.id) {
          state.lastSeenByAccount.set(account.id, entries[0].id);
        }
      }
    } catch {
      // empty
    }
  }

  private listAccounts(session: SessionPayload): Promise<AccountResponse[]> {
    return this.backendClient.requestJson<AccountResponse[]>({
      session,
      method: 'GET',
      path: '/api/v1/accounts',
    });
  }

  private async latestEntry(
    session: SessionPayload,
    accountId: string,
  ): Promise<StatementEntryResponse | undefined> {
    const page = await this.backendClient.requestJson<
      CursorPage<StatementEntryResponse>
    >({
      session,
      method: 'GET',
      path: `/api/v1/accounts/${accountId}/statement`,
      query: { pageSize: 1 },
    });
    return page.data?.[0];
  }
}

function takeUntilId(
  entries: StatementEntryResponse[],
  stopAtId: string,
): StatementEntryResponse[] {
  const result: StatementEntryResponse[] = [];
  for (const entry of entries) {
    if (entry.id === stopAtId) break;
    result.push(entry);
  }
  return result;
}
