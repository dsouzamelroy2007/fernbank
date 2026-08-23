import {
  Body,
  Controller,
  HttpCode,
  HttpStatus,
  Post,
  Req,
  Res,
} from '@nestjs/common';
import type { Request, Response } from 'express';
import { AuthBackendService } from './auth-backend.service';
import { BackendClientService } from '../backend-client/backend-client.service';
import { AccessTokenCacheService } from '../session/access-token-cache.service';
import { SessionService } from '../session/session.service';
import { requireSession } from '../session/require-session';
import { extractCorrelationId } from '../common/correlation';
import { extractClientIp } from '../common/client-ip';
import { decodeJwtExpiryMs } from '../common/jwt';
import { RegisterDto } from './dto/register.dto';
import { LoginDto } from './dto/login.dto';
import { MfaVerifyDto } from './dto/mfa-verify.dto';
import { MfaEnrollConfirmDto } from './dto/mfa-enroll-confirm.dto';
import { StepUpDto } from './dto/step-up.dto';

interface BackendAuthResult {
  status: 'AUTHENTICATED' | 'MFA_REQUIRED';
  accessToken?: string;
  refreshToken?: string;
  mfaToken?: string;
}

/** What the browser is allowed to see — never accessToken, never refreshToken. */
interface BrowserAuthResult {
  status: 'AUTHENTICATED' | 'MFA_REQUIRED';
  mfaToken?: string;
}

const STEP_UP_FALLBACK_TTL_MS = 5 * 60 * 1000;

/**
 * Owns the session+CSRF cookies. Register/login/mfa-verify/logout are raw calls (no
 * cached access token exists yet, or logout only needs the refreshToken as a body
 * param). mfa/enroll, mfa/enroll/confirm, and step-up need a Bearer token attached, so
 * they go through the same BackendClientService the proxy uses.
 */
@Controller('api/v1/auth')
export class AuthController {
  constructor(
    private readonly authBackend: AuthBackendService,
    private readonly backendClient: BackendClientService,
    private readonly tokenCache: AccessTokenCacheService,
    private readonly sessionService: SessionService,
  ) {}

  @Post('register')
  @HttpCode(HttpStatus.CREATED)
  register(@Body() dto: RegisterDto, @Req() req: Request) {
    return this.authBackend.post(
      '/api/v1/auth/register',
      dto,
      extractCorrelationId(req),
    );
  }

  @Post('login')
  @HttpCode(HttpStatus.OK)
  async login(
    @Body() dto: LoginDto,
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
  ): Promise<BrowserAuthResult> {
    const result = await this.authBackend.post<BackendAuthResult>(
      '/api/v1/auth/login',
      dto,
      extractCorrelationId(req),
      extractClientIp(req),
    );
    return this.startSessionIfAuthenticated(result, res);
  }

  @Post('mfa/verify')
  @HttpCode(HttpStatus.OK)
  async mfaVerify(
    @Body() dto: MfaVerifyDto,
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
  ): Promise<BrowserAuthResult> {
    const result = await this.authBackend.post<BackendAuthResult>(
      '/api/v1/auth/mfa/verify',
      dto,
      extractCorrelationId(req),
    );
    return this.startSessionIfAuthenticated(result, res);
  }

  @Post('logout')
  @HttpCode(HttpStatus.NO_CONTENT)
  async logout(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
  ): Promise<void> {
    const session = req.fernbankSession;
    if (session) {
      // Logout must always succeed from the user's perspective, even if the backend
      // call fails — the cookies are cleared regardless (matches the old Route
      // Handler's same reasoning).
      await this.authBackend
        .post(
          '/api/v1/auth/logout',
          { refreshToken: session.refreshToken },
          extractCorrelationId(req),
        )
        .catch(() => undefined);
      this.tokenCache.invalidate(session.sessionId);
    }
    this.sessionService.clearSession(res);
  }

  @Post('mfa/enroll')
  @HttpCode(HttpStatus.OK)
  mfaEnroll(@Req() req: Request) {
    const session = requireSession(req);
    return this.backendClient.requestJson({
      session,
      method: 'POST',
      path: '/api/v1/auth/mfa/enroll',
      correlationId: extractCorrelationId(req),
    });
  }

  @Post('mfa/enroll/confirm')
  @HttpCode(HttpStatus.OK)
  mfaEnrollConfirm(@Body() dto: MfaEnrollConfirmDto, @Req() req: Request) {
    const session = requireSession(req);
    return this.backendClient.requestJson({
      session,
      method: 'POST',
      path: '/api/v1/auth/mfa/enroll/confirm',
      body: dto,
      correlationId: extractCorrelationId(req),
    });
  }

  /**
   * The elevated access token is overwritten into the session cache and NEVER returned
   * to the browser — every subsequent proxied call for this session (including the
   * transfer retry that triggered step-up) automatically rides it until it expires or
   * a normal refresh overwrites it, same as today's client-side tokenStore.set()
   * behavior, just relocated server-side.
   */
  @Post('step-up')
  @HttpCode(HttpStatus.OK)
  async stepUp(
    @Body() dto: StepUpDto,
    @Req() req: Request,
  ): Promise<{ elevated: true }> {
    const session = requireSession(req);
    const result = await this.backendClient.requestJson<{
      accessToken: string;
    }>({
      session,
      method: 'POST',
      path: '/api/v1/auth/step-up',
      body: dto,
      correlationId: extractCorrelationId(req),
    });
    const expiresAt =
      decodeJwtExpiryMs(result.accessToken) ??
      Date.now() + STEP_UP_FALLBACK_TTL_MS;
    this.tokenCache.overwriteElevated(
      session.sessionId,
      result.accessToken,
      expiresAt,
    );
    return { elevated: true };
  }

  private startSessionIfAuthenticated(
    result: BackendAuthResult,
    res: Response,
  ): BrowserAuthResult {
    if (
      result.status === 'AUTHENTICATED' &&
      result.accessToken &&
      result.refreshToken
    ) {
      const sessionId = this.sessionService.startSession(
        res,
        result.refreshToken,
      );
      this.tokenCache.seed(sessionId, result.accessToken, result.refreshToken);
      return { status: 'AUTHENTICATED' };
    }
    return { status: 'MFA_REQUIRED', mfaToken: result.mfaToken };
  }
}
