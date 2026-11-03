import { describe, expect, it } from 'vitest';
import * as fmt from './format';

describe('format', () => {
  it('formats decimal strings exactly, without going through a double', () => {
    expect(fmt.grams('0.0666')).toBe('0.0666 g');
    expect(fmt.coins('1234.5')).toBe('₹1,234.50');
  });

  it('signs non-zero amounts and leaves zero unsigned', () => {
    expect(fmt.signedCoins('12.50')).toBe('+₹12.50');
    expect(fmt.signedCoins('-3.00')).toBe('-₹3.00');
    expect(fmt.signedCoins('0.00')).toBe('₹0.00');
    expect(fmt.percent('0.00')).toBe('0.00%');
  });

  it('detects zero in any scale', () => {
    expect(fmt.isZero('0')).toBe(true);
    expect(fmt.isZero('0.0000')).toBe(true);
    expect(fmt.isZero('0.0001')).toBe(false);
  });
});
