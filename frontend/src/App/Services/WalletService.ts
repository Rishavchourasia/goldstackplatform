import { goldApiUrl, walletApiUrl } from '../Constants/urlConstants';
import { GoldBalance, Wallet } from '../Models/Wallet';
import { httpGet, httpPost } from './httpServices';

export class WalletService {
  static async getWallet(): Promise<Wallet> {
    return await httpGet<Wallet>(walletApiUrl, true);
  }

  static async getGold(): Promise<GoldBalance> {
    return await httpGet<GoldBalance>(goldApiUrl, true);
  }

  static async topUp(amount: string): Promise<Wallet> {
    return await httpPost<Wallet>(`${walletApiUrl}/topup`, true, { amount });
  }
}
