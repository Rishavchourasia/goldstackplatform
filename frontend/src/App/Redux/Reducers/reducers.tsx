import { combineReducers } from 'redux';
import { authReducer } from './AuthReducer';
import { balanceReducer } from './BalanceReducer';
import { errorReducer } from './ErrorReducer';
import { notificationReducer } from './NotificationReducer';
import { portfolioReducer } from './PortfolioReducer';
import { priceReducer } from './PriceReducer';
import { sipReducer } from './SipReducer';
import { toastReducer } from './ToastReducer';
import { tradeReducer } from './TradeReducer';
import { whatIfReducer } from './WhatIfReducer';

const rootReducer = combineReducers({
  auth: authReducer,
  price: priceReducer,
  balances: balanceReducer,
  trade: tradeReducer,
  portfolio: portfolioReducer,
  whatIf: whatIfReducer,
  sips: sipReducer,
  notifications: notificationReducer,
  toasts: toastReducer,
  errors: errorReducer,
});

export type RootState = ReturnType<typeof rootReducer>;

export default rootReducer;
