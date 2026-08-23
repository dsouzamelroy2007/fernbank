import { DashboardService } from './dashboard.service';
import type { BackendClientService } from '../backend-client/backend-client.service';
import type { SessionPayload } from '../session/session-crypto.service';

const session: SessionPayload = { sessionId: 's1', refreshToken: 'r1' };

describe('DashboardService', () => {
  it('merges me + accounts + per-account statements into one response', async () => {
    const requestJson = jest
      .fn()
      .mockResolvedValueOnce({ userId: 'u1', email: 'a@example.com' })
      .mockResolvedValueOnce([{ id: 'acc-1' }, { id: 'acc-2' }])
      .mockResolvedValueOnce({ data: [{ id: 'entry-1' }], hasNext: false })
      .mockResolvedValueOnce({ data: [{ id: 'entry-2' }], hasNext: false });
    const backendClient = { requestJson } as unknown as BackendClientService;
    const service = new DashboardService(backendClient);

    const result = await service.getDashboard(session);

    expect(result.me).toEqual({ userId: 'u1', email: 'a@example.com' });
    expect(result.accounts).toHaveLength(2);
    expect(result.accounts[0].recentStatement).toEqual({
      entries: [{ id: 'entry-1' }],
      degraded: false,
    });
    expect(result.accounts[1].recentStatement.entries).toEqual([
      { id: 'entry-2' },
    ]);
  });

  it('degrades gracefully when one account statement fetch fails, rather than failing the whole request', async () => {
    const requestJson = jest
      .fn()
      .mockResolvedValueOnce({ userId: 'u1' })
      .mockResolvedValueOnce([{ id: 'acc-1' }, { id: 'acc-2' }])
      .mockResolvedValueOnce({ data: [{ id: 'entry-1' }], hasNext: false })
      .mockRejectedValueOnce(new Error('backend blip'));
    const backendClient = { requestJson } as unknown as BackendClientService;
    const service = new DashboardService(backendClient);

    const result = await service.getDashboard(session);

    expect(result.accounts).toHaveLength(2);
    expect(result.accounts[0].recentStatement).toEqual({
      entries: [{ id: 'entry-1' }],
      degraded: false,
    });
    expect(result.accounts[1].recentStatement).toEqual({
      entries: [],
      degraded: true,
    });
  });

  it('returns an empty accounts array with no per-account calls when the customer has none', async () => {
    const requestJson = jest
      .fn()
      .mockResolvedValueOnce({ userId: 'u1' })
      .mockResolvedValueOnce([]);
    const backendClient = { requestJson } as unknown as BackendClientService;
    const service = new DashboardService(backendClient);

    const result = await service.getDashboard(session);

    expect(result.accounts).toEqual([]);
    expect(requestJson).toHaveBeenCalledTimes(2);
  });
});
