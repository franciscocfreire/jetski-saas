import type { Metadata } from "next";
import { Geist, Playfair_Display } from "next/font/google";
import "./globals.css";

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
  title: "Meu Jet — Console da Plataforma",
  description: "Operação da plataforma Meu Jet: empresas, créditos, faturamento e saúde.",
  robots: { index: false, follow: false },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="pt-BR">
      <head>
        {/*
          Reaplica o tamanho de texto escolhido ANTES da primeira pintura. Sem
          isto a página nasce no padrão e salta para o tamanho do operador —
          um flash que, justamente para quem precisa de fonte maior, é pior que
          não ter o recurso. Inline e síncrono de propósito.
        */}
        <script
          dangerouslySetInnerHTML={{
            __html:
              "try{var f=localStorage.getItem('mj-console-fonte');" +
              "if(f&&[14,16,18,20,22,24].indexOf(+f)>-1)" +
              "document.documentElement.style.fontSize=f+'px'}catch(e){}",
          }}
        />
      </head>
      <body className={`${geistSans.variable} ${playfair.variable}`}>
        {children}
      </body>
    </html>
  );
}
