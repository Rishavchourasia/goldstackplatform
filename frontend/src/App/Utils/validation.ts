export function isAmount(value: string, decimals: number): boolean {
  const re = new RegExp(`^\\d{1,12}(\\.\\d{1,${decimals}})?$`);
  return re.test(value.trim()) && !/^0*(\.0*)?$/.test(value.trim());
}

export function coinError(value: string, min = 1): string | null {
  if (!value.trim()) return 'Enter an amount';
  if (!isAmount(value, 2)) return 'Use a positive number with up to 2 decimals';
  if (Number(value) < min) return `Minimum is ${min} coins`;
  return null;
}

export function gramError(value: string): string | null {
  if (!value.trim()) return 'Enter grams';
  if (!isAmount(value, 4)) return 'Use a positive number with up to 4 decimals';
  return null;
}
