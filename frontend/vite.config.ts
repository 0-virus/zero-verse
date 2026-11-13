/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { assertProductionApiBaseUrl } from './src/lib/apiBase';
import { assertProductionUploadTransport } from './src/features/upload/uploadTransport';

export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  if (command === 'build') {
    assertProductionApiBaseUrl(env.VITE_API_BASE_URL);
    assertProductionUploadTransport(env.VITE_UPLOAD_TRANSPORT);
  }

  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 5173,
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      css: false,
    },
  };
});
