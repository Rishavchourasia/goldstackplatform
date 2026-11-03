import { AppError } from '../Models/HttpModels';

export function toAppError(e: unknown): AppError {
  if (isAppError(e)) return e;
  return { status: 0, code: 'UNKNOWN', message: e instanceof Error ? e.message : String(e), fieldErrors: [] };
}

function isAppError(e: unknown): e is AppError {
  return typeof e === 'object' && e !== null && 'code' in e && 'status' in e && 'message' in e;
}

const SYSTEM_CODES = new Set([
  'NETWORK',
  'UNKNOWN',
  'INTERNAL_ERROR',
  'SERVICE_UNAVAILABLE',
  'DEPENDENCY_UNAVAILABLE',
  'RATE_LIMITED',
]);

export function isUnexpected(e: AppError): boolean {
  return e.status === 0 || e.status >= 500 || SYSTEM_CODES.has(e.code);
}

export function formError(e: AppError | null | undefined): AppError | null {
  return e && !isUnexpected(e) ? e : null;
}

export function isOutcomeUnknown(e: AppError): boolean {
  return e.code === 'NETWORK' || e.code === 'REQUEST_IN_PROGRESS' || e.code === 'ORDER_STILL_PROCESSING';
}

const FRIENDLY: Record<string, string> = {
  INSUFFICIENT_BALANCE: "You don't have enough coins for this. Top up your wallet and try again.",
  INSUFFICIENT_GOLD: "You don't hold that much gold.",
  QUOTE_EXPIRED: 'This price quote expired or was already used. Get a fresh quote to continue.',
  QUOTE_ALREADY_USED: 'This quote was already used. Get a fresh quote to continue.',
  INVALID_CREDENTIALS: 'Email or password is incorrect.',
  EMAIL_TAKEN: 'An account with this email already exists. Try logging in.',
  AMOUNT_TOO_SMALL: 'That amount is too small to trade at the current price.',
  TOPUP_LIMIT_EXCEEDED: 'A single top-up can be at most ₹1,00,000.',
  IDEMPOTENCY_KEY_REUSED: 'Something went wrong with this order attempt. Please get a new quote.',
  REQUEST_IN_PROGRESS:
    'This order is still being processed. Wait a moment, then retry (you will not be charged twice).',
  ORDER_STILL_PROCESSING: 'Your order is still being processed. Check your portfolio or notifications in a moment.',
  GOLD_CREDIT_FAILED: "We couldn't deliver the gold, so the order was cancelled and your coins are being refunded.",
  RATE_LIMITED: "You're going a bit fast. Wait a few seconds and try again.",
  NETWORK: "Can't reach GoldStack. Check your internet connection and try again.",
  SERVICE_UNAVAILABLE: 'Part of GoldStack is temporarily unavailable. Please try again in a moment.',
  DEPENDENCY_UNAVAILABLE: 'Part of GoldStack is temporarily unavailable. Please try again in a moment.',
  INTERNAL_ERROR: 'Something went wrong on our side. It has been logged.',
  SESSION_EXPIRED: 'For your security you were logged out. Please log in again.',
};

export function friendlyMessage(e: AppError): string {
  if (e.code === 'VALIDATION_FAILED' && e.fieldErrors.length) {
    return e.fieldErrors.map((f) => `${f.field}: ${f.message}`).join(' · ');
  }
  if (FRIENDLY[e.code]) return FRIENDLY[e.code];

  if (e.code === 'UNKNOWN' || (e.status >= 500 && ![502, 503, 504].includes(e.status))) return FRIENDLY.INTERNAL_ERROR;
  if ([502, 503, 504].includes(e.status)) return FRIENDLY.SERVICE_UNAVAILABLE;
  if (e.status === 0) return FRIENDLY.NETWORK;
  return e.message;
}
