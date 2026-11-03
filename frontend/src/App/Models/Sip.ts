import { Decimal, Frequency } from './GeneralResponse';

export interface Sip {
  id: string;
  amount: Decimal;
  frequency: Frequency;
  startDate: string;
  nextRunDate: string;
  status: 'ACTIVE' | 'PAUSED' | 'CANCELLED';
  createdAt: string;
}

export interface SipExecution {
  id: string;
  runDate: string;
  status: 'PENDING' | 'SUCCESS' | 'FAILED';
  orderId: string | null;
  failureReason: string | null;
  attemptedAt: string;
}

export interface SipRunSummary {
  runDate: string;
  due: number;
  requested: number;
  skipped: number;
}

export type SipAction = 'pause' | 'resume' | 'cancel';
