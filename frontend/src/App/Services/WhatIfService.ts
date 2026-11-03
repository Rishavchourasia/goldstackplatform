import { whatIfApiUrl } from '../Constants/urlConstants';
import { Frequency } from '../Models/GeneralResponse';
import { LumpSumResult, SipWhatIfResult } from '../Models/WhatIf';
import { httpGet, withQuery } from './httpServices';

export class WhatIfService {
  static async getLumpSum(amount: string, date: string): Promise<LumpSumResult> {
    return await httpGet<LumpSumResult>(withQuery(`${whatIfApiUrl}/lump-sum`, { amount, date }));
  }

  static async getSip(amount: string, frequency: Frequency, startDate: string): Promise<SipWhatIfResult> {
    return await httpGet<SipWhatIfResult>(withQuery(`${whatIfApiUrl}/sip`, { amount, frequency, startDate }));
  }
}
