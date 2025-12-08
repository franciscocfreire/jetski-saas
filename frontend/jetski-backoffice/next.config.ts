import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: 'standalone',
  images: {
    remotePatterns: [
      {
        protocol: 'https',
        hostname: 'images.unsplash.com',
        pathname: '/**',
      },
      {
        protocol: 'https',
        hostname: 'sea-doo.brp.com',
        pathname: '/**',
      },
      {
        protocol: 'https',
        hostname: 'www.yamahawaverunners.com',
        pathname: '/**',
      },
    ],
  },
};

export default nextConfig;
