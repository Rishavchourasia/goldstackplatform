import { sipsApiUrl, devApiUrl } from '../Constants/urlConstants';
import { Frequency, Page } from '../Models/GeneralResponse';
import { Sip, SipExecution, SipRunSummary } from '../Models/Sip';
import { httpGet, httpPost, withQuery } from './httpServices';

export class SipService {
  static async getList(): Promise<Sip[]> {
    return await httpGet<Sip[]>(sipsApiUrl, true);
  }

  static async create(amount: string, frequency: Frequency, startDate: string): Promise<Sip> {
    return await httpPost<Sip>(sipsApiUrl, true, { amount, frequency, startDate });
  }

  static async changeStatus(id: string, action: 'pause' | 'resume' | 'cancel'): Promise<Sip> {
    return await httpPost<Sip>(`${sipsApiUrl}/${id}/${action}`, true);
  }

  static async getExecutions(id: string): Promise<Page<SipExecution>> {
    return await httpGet<Page<SipExecution>>(withQuery(`${sipsApiUrl}/${id}/executions`, { size: 10 }), true);
  }

  static async runDueNow(): Promise<SipRunSummary> {
    return await httpPost<SipRunSummary>(`${devApiUrl}/sips/run`, true);
  }
}
