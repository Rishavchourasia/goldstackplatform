import { AuthResponse, User } from '../Models/User';

const TOKEN_KEY = 'goldstack.token';
const USER_KEY = 'goldstack.user';

export const infoStore = {
  getAuthToken(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },

  getUser(): User | null {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? (JSON.parse(raw) as User) : null;
    } catch {
      return null;
    }
  },

  saveSession(res: AuthResponse): void {
    try {
      localStorage.setItem(TOKEN_KEY, res.token);
      localStorage.setItem(USER_KEY, JSON.stringify(res.user));
    } catch {
      return;
    }
  },

  deleteSession(): void {
    try {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    } catch {
      return;
    }
  },
};
