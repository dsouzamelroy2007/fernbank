export interface MoneyDto {
  amount?: string;
  currency?: string;
}

export interface MeResponse {
  userId?: string;
  customerId?: string;
  email?: string;
  fullName?: string;
}

export interface AccountResponse {
  id?: string;
  accountNumber?: string;
  type?: string;
  status?: string;
  balance?: MoneyDto;
  createdAt?: string;
}

export interface StatementEntryResponse {
  id?: string;
  transactionId?: string;
  createdAt?: string;
  amount?: MoneyDto;
  description?: string;
}

export interface CursorPage<T> {
  data?: T[];
  nextCursor?: string;
  hasNext?: boolean;
}

export interface DashboardAccount extends AccountResponse {
  recentStatement: {
    entries: StatementEntryResponse[];
    degraded: boolean;
  };
}

export interface DashboardResponse {
  me: MeResponse;
  accounts: DashboardAccount[];
}
