import { DailyPrice } from './Price';
import { Decimal, Frequency } from './GeneralResponse';

export interface LumpSumResult {
  amount: Decimal;
  investedOn: string;
  priceThen: Decimal;
  priceNow: Decimal;
  grams: Decimal;
  valueNow: Decimal;
  profit: Decimal;
  returnPercent: Decimal;
}

export interface SeriesPoint {
  date: string;
  invested: Decimal;
  value: Decimal;
}

export interface SipWhatIfResult {
  amountPerInstallment: Decimal;
  frequency: Frequency;
  startDate: string;
  installments: number;
  totalInvested: Decimal;
  totalGrams: Decimal;
  valueNow: Decimal;
  profit: Decimal;
  returnPercent: Decimal;
  series: SeriesPoint[];
}

export interface LumpSumResponse {
  result: LumpSumResult;
  prices: DailyPrice[];
}
