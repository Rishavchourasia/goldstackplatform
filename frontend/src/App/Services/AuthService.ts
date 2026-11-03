import { authApiBaseUrl, usersApiBaseUrl } from '../Constants/urlConstants';
import { AuthResponse, User } from '../Models/User';
import { httpGet, httpPost } from './httpServices';

export class AuthService {
  static async login(email: string, password: string): Promise<AuthResponse> {
    return await httpPost<AuthResponse>(`${authApiBaseUrl}/login`, false, { email, password });
  }

  static async signup(email: string, password: string, fullName: string): Promise<AuthResponse> {
    return await httpPost<AuthResponse>(`${authApiBaseUrl}/signup`, false, { email, password, fullName });
  }

  static async me(): Promise<User> {
    return await httpGet<User>(`${usersApiBaseUrl}/me`, true);
  }
}
