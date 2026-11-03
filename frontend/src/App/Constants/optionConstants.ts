import { Frequency } from '../Models/GeneralResponse';

export const FREQUENCY_OPTIONS: ReadonlyArray<{ value: Frequency; label: string }> = [
  { value: 'DAILY', label: 'Day' },
  { value: 'WEEKLY', label: 'Week' },
  { value: 'MONTHLY', label: 'Month' },
];
