import { Decimal } from './GeneralResponse';

export type Side = 'BUY' | 'SELL';

export interface Quote {
  quoteId: string;
  side: Side;
  pricePerGram: Decimal;
  coins: Decimal;
  grams: Decimal;
  expiresAt: string;
}

export interface Order {
  id: string;
  side: Side;
  source: 'MANUAL' | 'SIP';
  coins: Decimal;
  grams: Decimal;
  pricePerGram: Decimal;
  status: 'PENDING' | 'COMPLETED' | 'FAILED';
  failureCode?: string | null;
  failureReason?: string | null;
  createdAt: string;
}
