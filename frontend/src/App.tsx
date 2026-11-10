import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './lib/authContext';
import { HeroBlogProvider } from './lib/heroBlogContext';
import { AppRoutes } from './routes/router';
import { NotificationProvider } from './features/notifications/NotificationProvider';

export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <NotificationProvider>
          <HeroBlogProvider>
            <AppRoutes />
          </HeroBlogProvider>
        </NotificationProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
