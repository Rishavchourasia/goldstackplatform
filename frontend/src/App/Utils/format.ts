import { Decimal } from '../Models/GeneralResponse';

const coinFmt = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 });
const gramFmt = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 4, maximumFractionDigits: 4 });
const pctFmt = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const compactFmt = new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 });

type NumericString = `${number}`;

export function coins(v: Decimal): string {
  return coinFmt.format(v as NumericString);
}

export function grams(v: Decimal): string {
  return `${gramFmt.format(v as NumericString)} g`;
}

export function percent(v: Decimal): string {
  const sign = isNegative(v) || isZero(v) ? '' : '+';
  return `${sign}${pctFmt.format(v as NumericString)}%`;
}

export function signedCoins(v: Decimal): string {
  return isNegative(v) || isZero(v) ? coins(v) : `+${coins(v)}`;
}

export function isNegative(v: Decimal): boolean {
  return v.trim().startsWith('-');
}

export function isZero(v: Decimal): boolean {
  return /^-?0*(\.0*)?$/.test(v.trim());
}

export function compact(n: number): string {
  return compactFmt.format(n);
}

export function date(iso: string): string {
  return new Date(iso.length === 10 ? iso + 'T00:00:00' : iso).toLocaleDateString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

export function dateTime(iso: string): string {
  return new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
}

export function time(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

export function toChartNumber(v: Decimal): number {
  return Number(v);
}

export function todayIso(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function yearsAgoIso(years: number): string {
  const d = new Date();
  d.setFullYear(d.getFullYear() - years);
  return d.toISOString().slice(0, 10);
}
