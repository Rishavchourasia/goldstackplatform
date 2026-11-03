import { describe, expect, it } from 'vitest';
import { AppError } from '../Models/HttpModels';
import { formError, friendlyMessage, isOutcomeUnknown, isUnexpected, toAppError } from './errors';

const err = (status: number, code: string): AppError => ({ status, code, message: 'msg', fieldErrors: [] });

describe('error policy', () => {
  it('sends system failures to the ExceptionModal', () => {
    expect(isUnexpected(err(0, 'NETWORK'))).toBe(true);
    expect(isUnexpected(err(500, 'INTERNAL_ERROR'))).toBe(true);
    expect(isUnexpected(err(503, 'DEPENDENCY_UNAVAILABLE'))).toBe(true);
    expect(isUnexpected(err(429, 'RATE_LIMITED'))).toBe(true);
  });

  it('keeps business errors inline', () => {
    expect(isUnexpected(err(422, 'INSUFFICIENT_BALANCE'))).toBe(false);
    expect(isUnexpected(err(410, 'QUOTE_EXPIRED'))).toBe(false);
    expect(formError(err(400, 'VALIDATION_FAILED'))).not.toBeNull();
    expect(formError(err(0, 'NETWORK'))).toBeNull();
  });

  it('knows when a retry must reuse the idempotency key', () => {
    expect(isOutcomeUnknown(err(0, 'NETWORK'))).toBe(true);
    expect(isOutcomeUnknown(err(422, 'INSUFFICIENT_BALANCE'))).toBe(false);
  });

  it('translates codes and lists field errors', () => {
    expect(friendlyMessage(err(422, 'INSUFFICIENT_BALANCE'))).toMatch(/enough coins/);
    const validation: AppError = {
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'x',
      fieldErrors: [{ field: 'amount', message: 'too small' }],
    };
    expect(friendlyMessage(validation)).toBe('amount: too small');
  });

  it('turns anything thrown into a plain, serialisable AppError', () => {
    expect(toAppError(new Error('boom'))).toEqual({ status: 0, code: 'UNKNOWN', message: 'boom', fieldErrors: [] });
  });
});

describe('friendly fallbacks', () => {
  it('never shows raw technical text for unknown server failures', () => {
    expect(friendlyMessage(err(502, 'HTTP_502'))).toMatch(/temporarily unavailable/);
    expect(friendlyMessage(err(500, 'Internal Server Error'))).toMatch(/on our side/);
    expect(friendlyMessage(toAppError(new TypeError("Failed to execute 'json'")))).toMatch(/on our side/);
  });
});
