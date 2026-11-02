import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone", // imagen Docker mínima (docker-compose.yml)
  cacheComponents: true,
  // Solo en desarrollo: abrir la web desde el móvil en la red de casa. La IP va en .env.local (DEV_ORIGIN), nunca en el repo
  allowedDevOrigins: process.env.DEV_ORIGIN ? [process.env.DEV_ORIGIN] : [],
  // El service worker siempre se revalida, para que una versión nueva llegue sin esperar a la caché del navegador
  async headers() {
    return [{ source: "/sw.js", headers: [{ key: "Cache-Control", value: "no-cache, no-store, must-revalidate" }] }];
  },
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
