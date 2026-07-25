import { redirect } from "next/navigation";
import { ShieldCheck } from "lucide-react";
import { auth, signIn } from "@/lib/auth";

export default async function Login({
  searchParams,
}: {
  searchParams: Promise<{ callbackUrl?: string; error?: string }>;
}) {
  const { callbackUrl, error } = await searchParams;
  const session = await auth();
  if (session?.accessToken && !session.error) {
    redirect(callbackUrl ?? "/");
  }

  async function entrar() {
    "use server";
    await signIn("keycloak", { redirectTo: callbackUrl ?? "/" });
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-brand-900 px-4">
      <div className="w-full max-w-sm rounded-xl bg-white p-8 shadow-xl">
        <div className="flex items-center gap-2">
          <ShieldCheck className="h-7 w-7 text-brand-600" />
          <div>
            <div className="font-display text-xl leading-tight text-ink-900">Meu Jet</div>
            <div className="text-xs tracking-wide text-ink-300">CONSOLE DA PLATAFORMA</div>
          </div>
        </div>

        <p className="mt-6 text-sm text-ink-500">
          Acesso restrito a operadores da plataforma. Empresas usam o backoffice.
        </p>

        {error && (
          <div className="mt-4 rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800">
            Não foi possível entrar. Tente novamente.
          </div>
        )}

        <form action={entrar} className="mt-6">
          <button
            type="submit"
            className="w-full rounded-md bg-brand-700 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-brand-600"
          >
            Entrar
          </button>
        </form>
      </div>
    </div>
  );
}
