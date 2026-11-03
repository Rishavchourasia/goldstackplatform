import { LOGOUT } from '../../Redux/Actions/AuthConstants';
import type { RootState } from '../../Redux/Reducers/reducers';
import { Link, NavLink, Navigate, Outlet, useLocation } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { LivePriceChip } from '../LivePrice/LivePrice';
import { Icon } from '../CustomUI';
import { cx } from '../../Utils/cx';
import styles from './Layout.module.scss';

const NAV = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/trade', label: 'Buy / Sell' },
  { to: '/portfolio', label: 'Portfolio' },
  { to: '/what-if', label: 'What if?' },
  { to: '/sips', label: 'SIPs' },
];

export function AppShell() {
  const dispatch = useDispatch();
  const user = useSelector((state: RootState) => state.auth.user);
  const unread = useSelector((state: RootState) => state.notifications.unread);
  const location = useLocation();

  return (
    <div className={styles.app}>
      <header className={styles.topbar}>
        <Link to="/" className={styles.brand}>
          <img src="/favicon.svg" alt="" width={24} height={24} />
          GoldStack
        </Link>
        <nav className={styles.nav} aria-label="Main">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => cx(styles.navLink, isActive && styles.navActive)}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className={styles.topbarRight}>
          <LivePriceChip />
          <NavLink to="/notifications" className={styles.iconButton} aria-label={`Notifications, ${unread} unread`}>
            <Icon name="bell" />
            {unread > 0 && (
              <span key={unread} className={styles.badge}>
                {unread > 99 ? '99+' : unread}
              </span>
            )}
          </NavLink>
          <button
            type="button"
            className={styles.iconButton}
            onClick={() => dispatch({ type: LOGOUT })}
            title={user ? `Log out ${user.email}` : 'Log out'}
            aria-label="Log out"
          >
            <Icon name="logout" />
          </button>
        </div>
      </header>
      {}
      <main key={location.pathname} className={styles.content}>
        <Outlet />
      </main>
    </div>
  );
}

export function RequireAuth() {
  const user = useSelector((state: RootState) => state.auth.user);
  const location = useLocation();
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <AppShell />;
}
