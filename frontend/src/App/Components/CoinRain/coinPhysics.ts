export interface Coin {
  x: number;
  y: number;
  vx: number;
  vy: number;
  r: number;
  spin: number;
  spinSpeed: number;
  tilt: number;
  tiltSpeed: number;
}

export const GRAVITY = 900;

export const MAX_FALL_SPEED = 650;

export const MAX_STEP = 0.05;

export function createCoins(count: number, width: number, height: number, rand: () => number = Math.random): Coin[] {
  return Array.from({ length: count }, () => {
    const r = 9 + rand() * 9;
    return {
      x: rand() * width,
      y: -r - rand() * height * 0.7,
      vx: (rand() - 0.5) * 70,
      vy: 80 + rand() * 140,
      r,
      spin: rand() * Math.PI * 2,
      spinSpeed: (6 + rand() * 8) * (rand() < 0.5 ? -1 : 1),
      tilt: rand() * Math.PI,
      tiltSpeed: (rand() - 0.5) * 2.5,
    };
  });
}

export function stepCoins(coins: Coin[], dt: number): void {
  const t = Math.min(Math.max(dt, 0), MAX_STEP);
  for (const c of coins) {
    c.vy = Math.min(c.vy + GRAVITY * t, MAX_FALL_SPEED);
    c.x += c.vx * t;
    c.y += c.vy * t;
    c.spin += c.spinSpeed * t;
    c.tilt += c.tiltSpeed * t;
  }
}

export function allLanded(coins: Coin[], height: number): boolean {
  return coins.every((c) => c.y - c.r > height);
}
