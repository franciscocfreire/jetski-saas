"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Header } from "./Header";
import { StaffHeader } from "./StaffHeader";
import { cn } from "@/lib/cn";

export function ChromeHeader() {
  const path = usePathname();
  return path?.startsWith("/staff") ? <StaffHeader /> : <Header />;
}

export function ChromeFooter() {
  const path = usePathname();
  const staff = path?.startsWith("/staff");
  return (
    <footer className="no-print mx-auto flex max-w-6xl flex-col items-center gap-2 px-4 py-10 text-center text-xs text-slate-400">
      <div>powered by Meu Jet · NORMAM-212/DPC compliant flow</div>
      <div className="flex items-center gap-2">
        <Link
          href="/"
          className={cn(
            "rounded-full px-3 py-1",
            !staff ? "bg-brand-100 text-brand-800" : "hover:bg-slate-100"
          )}
        >
          Portal do cliente
        </Link>
        <Link
          href="/staff"
          className={cn(
            "rounded-full px-3 py-1",
            staff ? "bg-slate-200 text-ink-900" : "hover:bg-slate-100"
          )}
        >
          Backoffice (staff)
        </Link>
      </div>
    </footer>
  );
}
