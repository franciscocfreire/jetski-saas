"use client";

import { Header } from "./Header";

export function ChromeHeader() {
  return <Header />;
}

export function ChromeFooter() {
  return (
    <footer className="no-print mx-auto flex max-w-6xl flex-col items-center gap-2 px-4 py-10 text-center text-xs text-slate-400">
      <div>powered by Meu Jet · NORMAM-212/DPC compliant flow</div>
    </footer>
  );
}
