import type { Metadata, Viewport } from "next";
import "./globals.css";

const site = "https://pocketmoney.xanderglobalacademy.com";

export const metadata: Metadata = {
  metadataBase: new URL(site),
  title: "Xander Pocket Money",
  description: "Send pocket money onto a student card.",
  applicationName: "Xander Pocket Money",
  icons: {
    icon: [{ url: "/xander-icon.jpg", type: "image/jpeg" }],
    apple: [{ url: "/xander-icon.jpg", type: "image/jpeg" }],
  },
  openGraph: {
    title: "Xander Pocket Money",
    description: "Send pocket money onto a student card.",
    url: site,
    siteName: "Xander Pocket Money",
    type: "website",
    images: [
      {
        url: `${site}/xander-link.jpg`,
        width: 1200,
        height: 1200,
        alt: "Xander",
        type: "image/jpeg",
      },
    ],
  },
  twitter: {
    card: "summary",
    title: "Xander Pocket Money",
    description: "Send pocket money onto a student card.",
    images: [`${site}/xander-link.jpg`],
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  themeColor: "#1565D8",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="antialiased">{children}</body>
    </html>
  );
}
