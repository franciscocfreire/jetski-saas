import Link from "next/link";
import { cn } from "@/lib/cn";
import type { ChecklistEstado } from "@/lib/mock";

export function Card({
  className,
  children,
}: {
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <div
      className={cn(
        "rounded-2xl border border-slate-200 bg-white shadow-sm",
        className
      )}
    >
      {children}
    </div>
  );
}

type BtnProps = {
  children: React.ReactNode;
  variant?: "primary" | "outline" | "ghost" | "subtle";
  size?: "sm" | "md" | "lg";
  className?: string;
  href?: string;
  onClick?: () => void;
  disabled?: boolean;
  type?: "button" | "submit";
};

export function Button({
  children,
  variant = "primary",
  size = "md",
  className,
  href,
  onClick,
  disabled,
  type = "button",
}: BtnProps) {
  const base =
    "inline-flex items-center justify-center gap-2 rounded-xl font-medium transition-colors disabled:opacity-50 disabled:pointer-events-none";
  const sizes = {
    sm: "h-9 px-3 text-sm",
    md: "h-11 px-5 text-sm",
    lg: "h-12 px-6 text-base",
  };
  const variants = {
    primary: "bg-brand-600 text-white hover:bg-brand-700",
    outline:
      "border border-slate-300 bg-white text-slate-800 hover:bg-slate-50",
    ghost: "text-slate-700 hover:bg-slate-100",
    subtle: "bg-brand-50 text-brand-800 hover:bg-brand-100",
  };
  const cls = cn(base, sizes[size], variants[variant], className);
  if (href)
    return (
      <Link href={href} className={cls}>
        {children}
      </Link>
    );
  return (
    <button type={type} onClick={onClick} disabled={disabled} className={cls}>
      {children}
    </button>
  );
}

export function Badge({
  children,
  tone = "slate",
  className,
}: {
  children: React.ReactNode;
  tone?: "slate" | "green" | "amber" | "red" | "brand";
  className?: string;
}) {
  const tones = {
    slate: "bg-slate-100 text-slate-700",
    green: "bg-emerald-100 text-emerald-700",
    amber: "bg-amber-100 text-amber-800",
    red: "bg-rose-100 text-rose-700",
    brand: "bg-brand-100 text-brand-800",
  };
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold",
        tones[tone],
        className
      )}
    >
      {children}
    </span>
  );
}

export function SectionTitle({
  children,
  sub,
}: {
  children: React.ReactNode;
  sub?: string;
}) {
  return (
    <div className="mb-5">
      <h2 className="text-xl font-bold tracking-tight text-ink-900">
        {children}
      </h2>
      {sub && <p className="mt-1 text-sm text-slate-500">{sub}</p>}
    </div>
  );
}

export function estadoBadge(e: ChecklistEstado) {
  switch (e) {
    case "ok":
      return <Badge tone="green">Concluído</Badge>;
    case "em_validacao":
      return <Badge tone="amber">Em validação</Badge>;
    case "expirada":
      return <Badge tone="red">Expirada</Badge>;
    default:
      return <Badge tone="slate">Pendente</Badge>;
  }
}

export function Field({
  label,
  children,
  hint,
}: {
  label: string;
  children: React.ReactNode;
  hint?: string;
}) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-medium text-slate-700">
        {label}
      </span>
      {children}
      {hint && <span className="mt-1 block text-xs text-slate-400">{hint}</span>}
    </label>
  );
}

export const inputCls =
  "h-11 w-full rounded-xl border border-slate-300 bg-white px-3.5 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100";
