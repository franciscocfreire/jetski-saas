import type { NextConfig } from "next";

// No docker/túnel o portal vive sob /portal do mesmo host do backoffice
// (nginx roteia); em dev standalone (npm run dev na 3003) fica sem basePath.
const basePath = process.env.NEXT_PUBLIC_BASE_PATH || "";

const nextConfig: NextConfig = {
  basePath,
  output: "standalone",
  images: {
    remotePatterns: [
      { protocol: "https", hostname: "images.unsplash.com" },
      { protocol: "https", hostname: "picsum.photos" },
    ],
  },
};

export default nextConfig;
