import { Decimal } from './GeneralResponse';

export interface Wallet {
  balance: Decimal;
  updatedAt: string;
}

export interface GoldBalance {
  grams: Decimal;
}
