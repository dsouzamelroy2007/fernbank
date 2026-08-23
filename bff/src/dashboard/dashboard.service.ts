import { Injectable } from '@nestjs/common';
import { BackendClientService } from '../backend-client/backend-client.service';
import type { SessionPayload } from '../session/session-crypto.service';
import type {
  AccountResponse,
  CursorPage,
  DashboardAccount,
  DashboardResponse,
  MeResponse,
  StatementEntryResponse,
} from './dashboard.types';

const STATEMENT_WINDOW_DAYS = 30;
const STATEMENT_PAGE_SIZE = 50;

/** Replaces the frontend's old /me + /accounts + N×/accounts/{id}/statement waterfall
 * with one round trip. Promise.allSettled on the per-account fan-out (not Promise.all)
 * — one slow/failing account degrades gracefully instead of blanking the whole
 * dashboard, matching what the old per-query useQueries fan-out already did. */
@Injectable()
export class DashboardService {
  constructor(private readonly backendClient: BackendClientService) {}

  async getDashboard(
    session: SessionPayload,
    correlationId: string,
  ): Promise<DashboardResponse> {
    const me = await this.backendClient.requestJson<MeResponse>({
      session,
      method: 'GET',
      path: '/api/v1/me',
      correlationId,
    });
    const accounts = await this.backendClient.requestJson<AccountResponse[]>({
      session,
      method: 'GET',
      path: '/api/v1/accounts',
      correlationId,
    });

    const from = new Date();
    from.setDate(from.getDate() - STATEMENT_WINDOW_DAYS);

    const statementResults = await Promise.allSettled(
      accounts.map((account) =>
        this.backendClient.requestJson<CursorPage<StatementEntryResponse>>({
          session,
          method: 'GET',
          path: `/api/v1/accounts/${account.id}/statement`,
          query: { from: from.toISOString(), pageSize: STATEMENT_PAGE_SIZE },
          correlationId,
        }),
      ),
    );

    const dashboardAccounts: DashboardAccount[] = accounts.map(
      (account, index) => {
        const result = statementResults[index];
        if (result.status === 'fulfilled') {
          return {
            ...account,
            recentStatement: {
              entries: result.value.data ?? [],
              degraded: false,
            },
          };
        }
        return { ...account, recentStatement: { entries: [], degraded: true } };
      },
    );

    return { me, accounts: dashboardAccounts };
  }
}
