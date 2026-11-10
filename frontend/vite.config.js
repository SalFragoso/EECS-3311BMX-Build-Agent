import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// `npm run build` writes the GUI into Spring Boot's static folder, so the backend serves it at
// http://localhost:8080 and nobody needs Node just to run the app.
// `npm run dev` runs the GUI on :5173 with /api requests forwarded to the backend on :8080.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: "../backend/src/main/resources/static",
    emptyOutDir: true,
  },
  server: {
    proxy: { "/api": "http://localhost:8080" },
  },
});
