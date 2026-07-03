import type { Metadata } from "next";
import { Geist, Playfair_Display } from "next/font/google";
import "./globals.css";
import { ChromeHeader, ChromeFooter } from "@/components/Chrome";
import { Providers } from "@/components/Providers";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const playfair = Playfair_Display({
  variable: "--font-playfair",
  subsets: ["latin"],
  weight: ["400", "600", "700"],
});

export const metadata: Metadata = {
  title: "Meu Jet — Portal do Cliente",
  description:
    "Portal do cliente Meu Jet: conta, reservas e documentação náutica.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="pt-BR">
      <body className={`${geistSans.variable} ${playfair.variable}`}>
        <div className="prototype-banner no-print py-1 text-center text-[11px] font-semibold text-amber-900">
          EM CONSTRUÇÃO — conta, reservas, pagamento, termos e habilitação já
          são reais; histórico e emissão CHA-MTA-E em breve
        </div>
        <Providers>
          <ChromeHeader />
          <main className="mx-auto max-w-6xl px-4 py-8">{children}</main>
          <ChromeFooter />
        </Providers>
      </body>
    </html>
  );
}
