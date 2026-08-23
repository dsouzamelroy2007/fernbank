import { Controller, Get, Req } from '@nestjs/common';
import type { Request } from 'express';
import { DashboardService } from './dashboard.service';
import { requireSession } from '../session/require-session';
import { extractCorrelationId } from '../common/correlation';
import type { DashboardResponse } from './dashboard.types';

@Controller('bff/dashboard')
export class DashboardController {
  constructor(private readonly dashboardService: DashboardService) {}

  @Get()
  getDashboard(@Req() req: Request): Promise<DashboardResponse> {
    const session = requireSession(req);
    return this.dashboardService.getDashboard(
      session,
      extractCorrelationId(req),
    );
  }
}
