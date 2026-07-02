import type { Metadata } from "next";
import { Geist, Geist_Mono, Playfair_Display } from "next/font/google";
import "./globals.css";
import { Providers } from "@/components/providers";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

// Premium serif font for headings
const playfair = Playfair_Display({
  variable: "--font-playfair",
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
});

export const metadata: Metadata = {
  metadataBase: new URL("https://meujet.com.br"),
  title: "Meu Jet | Aluguel de Jetskis e Lanchas",
  description: "Marketplace de aluguel de jetskis e lanchas. Compare preços, reserve online e viva experiências náuticas exclusivas.",
  openGraph: {
    title: "Meu Jet | Aluguel de Jetskis e Lanchas",
    description: "Marketplace de aluguel de jetskis e lanchas. Compare preços, reserve online e viva experiências náuticas exclusivas.",
    url: "https://meujet.com.br",
    siteName: "Meu Jet",
    locale: "pt_BR",
    type: "website",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="pt-BR" suppressHydrationWarning>
      <body
        className={`${geistSans.variable} ${geistMono.variable} ${playfair.variable} antialiased`}
      >
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
