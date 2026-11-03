export type Decimal = string;

export type Frequency = 'DAILY' | 'WEEKLY' | 'MONTHLY';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
