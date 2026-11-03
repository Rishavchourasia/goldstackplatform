import { portfolioApiUrl } from '../Constants/urlConstants';
import { Page } from '../Models/GeneralResponse';
import { HistoryPoint, Portfolio } from '../Models/Portfolio';
import { Order } from '../Models/Trade';
import { httpGet, withQuery } from './httpServices';

export class PortfolioService {
  static async getSummary(): Promise<Portfolio> {
    return await httpGet<Portfolio>(portfolioApiUrl, true);
  }

  static async getHistory(): Promise<HistoryPoint[]> {
    return await httpGet<HistoryPoint[]>(`${portfolioApiUrl}/history`, true);
  }

  static async getTransactions(page: number, size = 10): Promise<Page<Order>> {
    return await httpGet<Page<Order>>(withQuery(`${portfolioApiUrl}/transactions`, { page, size }), true);
  }
}
