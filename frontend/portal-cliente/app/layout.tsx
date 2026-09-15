import type { Metadata } from "next";
import { Geist, Playfair_Display } from "next/font/google";
import "./globals.css";
import { ChromeHeader, ChromeFooter } from "@/components/Chrome";
import { Providers } from "@/components/Providers";
import { BottomNav } from "@/components/BottomNav";

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
    // Variáveis das fontes no <html> (= :root): o @theme resolve --font-sans no
    // :root; definidas só no <body>, a var fica inválida e tudo cai em serif.
    <html lang="pt-BR" className={`${geistSans.variable} ${playfair.variable}`}>
      <body>
        <Providers>
          <ChromeHeader />
          {/* pb extra no mobile: espaço p/ a bottom nav fixa */}
          <main className="mx-auto max-w-6xl px-4 py-8 pb-24 md:pb-8">{children}</main>
          <ChromeFooter />
          <BottomNav />
        </Providers>
      </body>
    </html>
  );
}
