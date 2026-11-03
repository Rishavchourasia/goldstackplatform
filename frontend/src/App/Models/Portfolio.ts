import { Decimal } from './GeneralResponse';

export interface Portfolio {
  coinBalance: Decimal;
  totalGrams: Decimal;
  totalInvested: Decimal;
  currentPricePerGram: Decimal;
  currentValue: Decimal;
  profitLoss: Decimal;
  returnPercent: Decimal;
  priceAt: string;
}

export interface HistoryPoint {
  date: string;
  grams: Decimal;
  invested: Decimal;
  value: Decimal;
}
