import { ordersApiUrl } from '../Constants/urlConstants';
import { Order, Quote, Side } from '../Models/Trade';
import { httpGet, httpPost } from './httpServices';

export class TradeService {
  static async createQuote(side: Side, amount: string): Promise<Quote> {
    const body = side === 'BUY' ? { side, coins: amount } : { side, grams: amount };
    return await httpPost<Quote>(`${ordersApiUrl}/quotes`, true, body);
  }

  static async executeOrder(side: Side, quoteId: string, idempotencyKey: string): Promise<Order> {
    const path = side === 'BUY' ? `${ordersApiUrl}/buy` : `${ordersApiUrl}/sell`;
    return await httpPost<Order>(path, true, { quoteId }, { 'Idempotency-Key': idempotencyKey });
  }

  static async getOrder(id: string): Promise<Order> {
    return await httpGet<Order>(`${ordersApiUrl}/${id}`, true);
  }
}
