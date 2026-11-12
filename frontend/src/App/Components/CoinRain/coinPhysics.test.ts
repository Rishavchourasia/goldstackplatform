import { describe, expect, it } from 'vitest';
import { allLanded, createCoins, GRAVITY, MAX_FALL_SPEED, MAX_STEP, stepCoins } from './coinPhysics';

function seeded(seed = 42) {
  let s = seed;
  return () => {
    s = (s * 16807) % 2147483647;
    return (s - 1) / 2147483646;
  };
}

describe('coin rain physics', () => {
  it('creates the requested coins, spread across the width and all above the screen', () => {
    const coins = createCoins(50, 1000, 800, seeded());
    expect(coins).toHaveLength(50);
    for (const c of coins) {
      expect(c.x).toBeGreaterThanOrEqual(0);
      expect(c.x).toBeLessThanOrEqual(1000);
      expect(c.y + c.r).toBeLessThanOrEqual(0);
    }
  });

  it('gravity speeds a coin up, but never past the top falling speed', () => {
    const [coin] = createCoins(1, 500, 500, seeded());
    const before = coin.vy;
    stepCoins([coin], 0.01);
    expect(coin.vy).toBeCloseTo(Math.min(before + GRAVITY * 0.01, MAX_FALL_SPEED));
    for (let i = 0; i < 200; i++) stepCoins([coin], 0.02);
    expect(coin.vy).toBe(MAX_FALL_SPEED);
  });

  it('a long pause moves coins by at most one capped step', () => {
    const [coin] = createCoins(1, 500, 500, seeded());
    const y = coin.y;
    const vy = coin.vy;
    stepCoins([coin], 5);
    const maxMove = Math.min(vy + GRAVITY * MAX_STEP, MAX_FALL_SPEED) * MAX_STEP;
    expect(coin.y - y).toBeCloseTo(maxMove);
  });

  it('is finished only when every coin is below the bottom edge', () => {
    const coins = createCoins(20, 400, 600, seeded());
    expect(allLanded(coins, 600)).toBe(false);
    for (let i = 0; i < 400; i++) stepCoins(coins, 0.02);
    expect(allLanded(coins, 600)).toBe(true);
  });
});
