import { defineConfig } from 'vite';
import { resolve } from 'path';
import fs from 'fs';

// Kotlin/JS webpack output directory
const kotlinJsOutput = resolve(__dirname, 'build/kotlin-webpack/js/developmentExecutable');

export default defineConfig({
  root: '.',
  publicDir: 'src/jsMain/resources',
  build: {
    outDir: 'dist',
    rollupOptions: {
      input: resolve(__dirname, 'index.html')
    }
  },
  server: {
    port: 3000,
    fs: {
      // Allow serving files from Kotlin/JS build output
      allow: ['.', resolve(__dirname, 'build')]
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/health': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  resolve: {
    alias: {
      '@kotlin': resolve(__dirname, 'build/js/packages/frontend/kotlin')
    }
  },
  plugins: [
    {
      name: 'serve-kotlin-js',
      configureServer(server) {
        // Serve Kotlin/JS webpack output files at root path
        // e.g. /frontend.js → build/kotlin-webpack/js/developmentExecutable/frontend.js
        server.middlewares.use((req, res, next) => {
          const url = req.url?.split('?')[0];
          if (url && url.endsWith('.js')) {
            const filePath = resolve(kotlinJsOutput, url.substring(1));
            if (fs.existsSync(filePath)) {
              res.setHeader('Content-Type', 'application/javascript');
              fs.createReadStream(filePath).pipe(res);
              return;
            }
          }
          next();
        });
      }
    }
  ]
});
