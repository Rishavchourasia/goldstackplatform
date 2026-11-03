import { LOGIN_REQUEST, SIGNUP_REQUEST } from '../../Redux/Actions/AuthConstants';
import type { RootState } from '../../Redux/Reducers/reducers';
import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { FormError } from '../../Components/FormError/FormError';
import { Button, Card, Stack, Text, TextField } from '../../Components/CustomUI';
import { useDocumentTitle } from '../../Components/CustomHooks/useDocumentTitle';
import styles from './AuthPage.module.scss';

export function AuthPage({ mode }: { mode: 'login' | 'signup' }) {
  const isSignup = mode === 'signup';
  useDocumentTitle(isSignup ? 'Sign up' : 'Log in');
  const dispatch = useDispatch();
  const { user, loading, err: error } = useSelector((state: RootState) => state.auth);
  const location = useLocation();

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const passwordError = isSignup && password.length > 0 && password.length < 8 ? 'At least 8 characters' : null;

  const from = (location.state as { from?: string } | null)?.from ?? '/';
  if (user) return <Navigate to={from} replace />;

  function submit(e: FormEvent) {
    e.preventDefault();
    if (passwordError) return;
    dispatch(
      isSignup
        ? { type: SIGNUP_REQUEST, payload: { email, password, fullName } }
        : { type: LOGIN_REQUEST, payload: { email, password } },
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.glow} aria-hidden="true" />
      <Card as="form" padding="lg" className={styles.card} onSubmit={submit}>
        <Stack gap={5}>
          <Stack gap={2} align="center">
            <img src="/favicon.svg" alt="" width={40} height={40} className={styles.logo} />
            <Text variant="h1" align="center">
              {isSignup ? 'Create your account' : 'Welcome back'}
            </Text>
            <Text tone="secondary" align="center">
              {isSignup ? 'Get 10,000 virtual coins to start investing in gold.' : 'Log in to see your gold.'}
            </Text>
          </Stack>

          {isSignup && (
            <TextField
              label="Full name"
              required
              maxLength={100}
              autoComplete="name"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
            />
          )}
          <TextField
            label="Email"
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <TextField
            label="Password"
            type="password"
            required
            minLength={isSignup ? 8 : undefined}
            maxLength={72}
            autoComplete={isSignup ? 'new-password' : 'current-password'}
            error={passwordError}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />

          <FormError error={error} />

          <Button type="submit" variant="primary" size="lg" fullWidth loading={loading}>
            {isSignup ? 'Create account' : 'Log in'}
          </Button>
          <Text variant="small" tone="muted" align="center">
            {isSignup ? (
              <>
                Already have an account? <Link to="/login">Log in</Link>
              </>
            ) : (
              <>
                New here? <Link to="/signup">Create an account</Link>
              </>
            )}
          </Text>
        </Stack>
      </Card>
    </div>
  );
}
