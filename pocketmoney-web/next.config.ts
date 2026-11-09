import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  images: { remotePatterns: [{ protocol: "https", hostname: "schoolmis.xanderglobalacademy.com" }] },
  async redirects() {
    return [
      { source: "/parent-guide", destination: "/parent-guide/index.html", permanent: false },
    ];
  },
  async headers() {
    return [
      {
        source: "/parent-guide/:path*",
        headers: [{ key: "Cache-Control", value: "no-cache, must-revalidate" }],
      },
    ];
  },
};

export default nextConfig;
