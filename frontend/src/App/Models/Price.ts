import { Decimal } from './GeneralResponse';

export interface LivePrice {
  pricePerGram: Decimal;
  recordedAt: string;
  source: string;
}

export interface DailyPrice {
  date: string;
  closePrice: Decimal;
}

export type ConnectionStatus = 'connecting' | 'live' | 'reconnecting';
