import type { Metadata } from "next";
import "./globals.css";
import { ChromeHeader, ChromeFooter } from "@/components/Chrome";

export const metadata: Metadata = {
  title: "JetRiders — Protótipo (Portal + Backoffice)",
  description: "Protótipo clicável: portal do cliente e backoffice (staff).",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="pt-BR">
      <body>
        <div className="prototype-banner no-print py-1 text-center text-[11px] font-semibold text-amber-900">
          PROTÓTIPO — dados fictícios, sem backend · para validação com
          stakeholders
        </div>
        <ChromeHeader />
        <main className="mx-auto max-w-6xl px-4 py-8">{children}</main>
        <ChromeFooter />
      </body>
    </html>
  );
}
