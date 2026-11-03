import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAuth } from './App/Components/AppShell/AppShell';
import * as route from './App/Constants/RouteConstants';
import { AuthPage } from './App/Views/Auth/AuthPage';
import { DashboardPage } from './App/Views/Dashboard/DashboardPage';
import { NotificationsPage } from './App/Views/Notifications/NotificationsPage';
import { PortfolioPage } from './App/Views/Portfolio/PortfolioPage';
import { SipsPage } from './App/Views/Sips/SipsPage';
import { TradePage } from './App/Views/Trade/TradePage';
import { WhatIfPage } from './App/Views/WhatIf/WhatIfPage';

export default function AppRouting() {
  return (
    <Routes>
      <Route path={route.ROUTE_LOGIN} element={<AuthPage mode="login" />} />
      <Route path={route.ROUTE_SIGNUP} element={<AuthPage mode="signup" />} />
      <Route element={<RequireAuth />}>
        <Route index element={<DashboardPage />} />
        <Route path={route.ROUTE_TRADE} element={<TradePage />} />
        <Route path={route.ROUTE_PORTFOLIO} element={<PortfolioPage />} />
        <Route path={route.ROUTE_WHAT_IF} element={<WhatIfPage />} />
        <Route path={route.ROUTE_SIPS} element={<SipsPage />} />
        <Route path={route.ROUTE_NOTIFICATIONS} element={<NotificationsPage />} />
      </Route>
      <Route path="*" element={<Navigate to={route.ROUTE_HOME} replace />} />
    </Routes>
  );
}
