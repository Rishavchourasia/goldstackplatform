import { livePriceSocketPath, pricesApiUrl } from '../Constants/urlConstants';
import { DailyPrice, LivePrice } from '../Models/Price';
import { httpGet, withQuery } from './httpServices';

export class PriceService {
  static async getLatest(): Promise<LivePrice> {
    return await httpGet<LivePrice>(`${pricesApiUrl}/latest`);
  }

  static async getRecentTicks(limit: number): Promise<LivePrice[]> {
    return await httpGet<LivePrice[]>(withQuery(`${pricesApiUrl}/ticks`, { limit }));
  }

  static async getDailyPrices(from?: string, to?: string): Promise<DailyPrice[]> {
    return await httpGet<DailyPrice[]>(withQuery(`${pricesApiUrl}/daily`, { from, to }));
  }

  static connectLive(handlers: {
    onOpen: () => void;
    onTick: (price: LivePrice) => void;
    onClose: () => void;
  }): () => void {
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const socket = new WebSocket(`${scheme}://${window.location.host}${livePriceSocketPath}`);
    socket.onopen = handlers.onOpen;
    socket.onmessage = (event) => handlers.onTick(JSON.parse(event.data) as LivePrice);
    socket.onclose = handlers.onClose;
    return () => socket.close();
  }
}
