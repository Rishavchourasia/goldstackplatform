import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button, Icon, Text } from '../CustomUI';
import styles from './ErrorBoundary.module.scss';

export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Render error', error, info.componentStack);
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <div className={styles.fallback} role="alert">
        <span className={styles.icon}>
          <Icon name="alert" size={24} />
        </span>
        <Text variant="h1">This page hit a problem</Text>
        <Text tone="secondary" align="center">
          Something unexpected happened while showing this page. Your money is safe; reloading usually fixes it.
        </Text>
        <Button variant="primary" onClick={() => window.location.reload()}>
          <Icon name="refresh" size={16} />
          Reload
        </Button>
      </div>
    );
  }
}
