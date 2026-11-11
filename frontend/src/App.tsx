import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './lib/authContext';
import { HeroBlogProvider } from './lib/heroBlogContext';
import { AppRoutes } from './routes/router';
import { NotificationProvider } from './features/notifications/NotificationProvider';
import { AdminAccessProvider } from './features/admin/AdminAccessProvider';

export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AdminAccessProvider>
          <NotificationProvider>
            <HeroBlogProvider>
              <AppRoutes />
            </HeroBlogProvider>
          </NotificationProvider>
        </AdminAccessProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
