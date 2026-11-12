import { useEffect, useRef, useState } from 'react';
import { usePrefersReducedMotion } from '../CustomHooks/usePrefersReducedMotion';
import { allLanded, createCoins, stepCoins, type Coin } from './coinPhysics';
import styles from './CoinRain.module.scss';

const MAX_DURATION_MS = 4500;

export function CoinRain({ count = 70 }: { count?: number }) {
  const reduced = usePrefersReducedMotion();
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [finished, setFinished] = useState(false);

  useEffect(() => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (reduced || !canvas || !ctx) return;

    const resize = () => {
      const dpr = window.devicePixelRatio || 1;
      canvas.width = window.innerWidth * dpr;
      canvas.height = window.innerHeight * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };
    resize();
    window.addEventListener('resize', resize);

    const coins = createCoins(count, window.innerWidth, window.innerHeight);
    const started = performance.now();
    let last = started;
    let frame = requestAnimationFrame(function tick(now) {
      stepCoins(coins, (now - last) / 1000);
      last = now;
      ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);
      for (const coin of coins) drawCoin(ctx, coin);
      if (allLanded(coins, window.innerHeight) || now - started > MAX_DURATION_MS) {
        setFinished(true);
        return;
      }
      frame = requestAnimationFrame(tick);
    });

    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener('resize', resize);
    };
  }, [count, reduced]);

  if (reduced || finished) return null;
  return <canvas ref={canvasRef} className={styles.canvas} aria-hidden="true" />;
}

function drawCoin(ctx: CanvasRenderingContext2D, c: Coin) {
  const face = Math.max(0.12, Math.abs(Math.cos(c.spin)));
  ctx.save();
  ctx.translate(c.x, c.y);
  ctx.rotate(c.tilt);
  ctx.scale(face, 1);

  const shine = ctx.createRadialGradient(-c.r * 0.35, -c.r * 0.35, c.r * 0.1, 0, 0, c.r);
  shine.addColorStop(0, '#fff4b8');
  shine.addColorStop(0.45, '#f2c94c');
  shine.addColorStop(0.85, '#c8961e');
  shine.addColorStop(1, '#8a6212');
  ctx.beginPath();
  ctx.arc(0, 0, c.r, 0, Math.PI * 2);
  ctx.fillStyle = shine;
  ctx.fill();

  ctx.lineWidth = c.r * 0.12;
  ctx.strokeStyle = '#9c7414';
  ctx.stroke();

  ctx.beginPath();
  ctx.arc(0, 0, c.r * 0.7, 0, Math.PI * 2);
  ctx.lineWidth = c.r * 0.07;
  ctx.strokeStyle = 'rgba(255, 244, 184, 0.75)';
  ctx.stroke();

  if (face > 0.55) {
    ctx.fillStyle = '#8a6212';
    ctx.font = `700 ${c.r * 1.05}px Inter, system-ui, sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('₹', 0, c.r * 0.06);
  }
  ctx.restore();
}
