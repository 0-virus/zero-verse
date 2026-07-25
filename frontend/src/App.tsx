import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './lib/authContext';
import { AppRoutes } from './routes/router';

export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}
