import { BrowserRouter } from 'react-router-dom';
import { ErrorBoundary } from './App/Components/ErrorBoundary/ErrorBoundary';
import { ExceptionModal } from './App/Components/ExceptionModal/ExceptionModal';
import { Toaster } from './App/Components/Toaster/Toaster';
import AppRouting from './AppRouting';

export default function App() {
  return (
    <BrowserRouter>
      <ErrorBoundary>
        <AppRouting />
      </ErrorBoundary>
      <ExceptionModal />
      <Toaster />
    </BrowserRouter>
  );
}
