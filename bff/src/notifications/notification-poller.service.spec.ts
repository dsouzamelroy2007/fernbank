import { NotificationPollerService } from './notification-poller.service';
import type { BackendClientService } from '../backend-client/backend-client.service';
import type { SessionPayload } from '../session/session-crypto.service';

const session: SessionPayload = { sessionId: 's1', refreshToken: 'r1' };

function makeBackendClient() {
  const requestJson = jest
    .fn()
    .mockImplementation((opts: { path: string }) =>
      opts.path === '/api/v1/accounts'
        ? Promise.resolve([])
        : Promise.resolve({ data: [] }),
    );
  const backendClient = { requestJson } as unknown as BackendClientService;
  return { backendClient, requestJson };
}

describe('NotificationPollerService', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('starts one poll interval on first subscribe and stops it after the last unsubscribe', async () => {
    const { backendClient, requestJson } = makeBackendClient();
    const poller = new NotificationPollerService(backendClient);

    const subscription = poller.subscribe(session).subscribe();
    await jest.advanceTimersByTimeAsync(0); // flush the seed call
    const callsAfterSeed = requestJson.mock.calls.length;
    expect(callsAfterSeed).toBeGreaterThan(0);

    await jest.advanceTimersByTimeAsync(5000);
    expect(requestJson.mock.calls.length).toBeGreaterThan(callsAfterSeed);

    subscription.unsubscribe();
    const callsAfterUnsubscribe = requestJson.mock.calls.length;
    await jest.advanceTimersByTimeAsync(15_000);
    expect(requestJson.mock.calls.length).toBe(callsAfterUnsubscribe);
  });

  it('does not start a second interval for a second concurrent subscriber on the same session', async () => {
    const { backendClient, requestJson } = makeBackendClient();
    const poller = new NotificationPollerService(backendClient);

    const sub1 = poller.subscribe(session).subscribe();
    await jest.advanceTimersByTimeAsync(0);
    const sub2 = poller.subscribe(session).subscribe();
    await jest.advanceTimersByTimeAsync(0);

    const callsBeforeTick = requestJson.mock.calls.length;
    await jest.advanceTimersByTimeAsync(5000);
    const callsAfterOneTick = requestJson.mock.calls.length;

    // Exactly one poll cycle's worth of accounts-listing calls, not two.
    expect(callsAfterOneTick - callsBeforeTick).toBe(1);

    sub1.unsubscribe();
    sub2.unsubscribe();
  });

  it('keeps polling for a remaining subscriber after only one of two unsubscribes', async () => {
    const { backendClient, requestJson } = makeBackendClient();
    const poller = new NotificationPollerService(backendClient);

    const sub1 = poller.subscribe(session).subscribe();
    const sub2 = poller.subscribe(session).subscribe();
    await jest.advanceTimersByTimeAsync(0);

    sub1.unsubscribe();
    const callsBeforeTick = requestJson.mock.calls.length;
    await jest.advanceTimersByTimeAsync(5000);
    expect(requestJson.mock.calls.length).toBeGreaterThan(callsBeforeTick);

    sub2.unsubscribe();
  });
});
