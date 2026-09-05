import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './lib/authContext';
import { HeroBlogProvider } from './lib/heroBlogContext';
import { AppRoutes } from './routes/router';

export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <HeroBlogProvider>
          <AppRoutes />
        </HeroBlogProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
