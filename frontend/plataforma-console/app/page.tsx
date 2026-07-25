import { redirect } from "next/navigation";
import { auth } from "@/lib/auth";
import { Shell } from "@/components/Shell";
import { platformFetch, PlatformApiError } from "@/lib/api";

type TenantResumo = { id: string; slug: string; razaoSocial: string; status: string };

/**
 * Visão geral. Na F0 ela existe para provar a fundação de ponta a ponta:
 * login pelo client próprio → chamada a /v1/platform/tenants SEM X-Tenant-Id →
 * barreira do PlatformScopeInterceptor quando quem entrou não é operador.
 *
 * Os indicadores de negócio chegam na F4, quando existir o read model
 * (plataforma_metrica_diaria) — hoje não há como agregar cross-tenant sem
 * varrer empresa a empresa.
 */
export default async function Home() {
  const session = await auth();
  if (!session?.accessToken) {
    redirect("/login");
  }

  let tenants: TenantResumo[] | null = null;
  let erro: { status: number; mensagem: string } | null = null;

  try {
    tenants = await platformFetch<TenantResumo[]>("/v1/platform/tenants");
  } catch (e) {
    const apiErro = e as PlatformApiError;
    erro = {
      status: apiErro.status ?? 0,
      mensagem: apiErro.message ?? "Falha ao consultar a plataforma",
    };
  }

  const porStatus = (tenants ?? []).reduce<Record<string, number>>((acc, t) => {
    acc[t.status] = (acc[t.status] ?? 0) + 1;
    return acc;
  }, {});

  return (
    <Shell email={session.user?.email}>
      <h1 className="font-display text-2xl text-ink-900">Visão geral</h1>
      <p className="mt-1 text-sm text-ink-500">
        Console da plataforma — separado do backoffice onde as empresas operam.
      </p>

      {erro?.status === 403 && (
        <div className="mt-6 rounded-lg border border-amber-300 bg-amber-50 p-4">
          <div className="font-medium text-amber-900">Acesso restrito</div>
          <p className="mt-1 text-sm text-amber-800">
            Sua conta autenticou, mas não é operador de plataforma. Concessão de acesso
            ainda é feita por <code>PLATFORM_ADMIN_EMAILS</code> ou SQL (ver SUPERADMIN.md);
            a tela de operadores chega na F2.
          </p>
        </div>
      )}

      {erro && erro.status !== 403 && (
        <div className="mt-6 rounded-lg border border-red-300 bg-red-50 p-4 text-sm text-red-800">
          Falha ao consultar a plataforma ({erro.status}): {erro.mensagem}
        </div>
      )}

      {tenants && (
        <>
          <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Card titulo="Empresas" valor={tenants.length} />
            {Object.entries(porStatus).map(([status, total]) => (
              <Card key={status} titulo={status} valor={total} />
            ))}
          </div>

          <div className="mt-8 rounded-lg border border-slate-200 bg-white p-4 text-sm text-ink-500">
            Indicadores de negócio (MRR, emissões, créditos consumidos, funil de signup)
            chegam na F4, com o read model <code>plataforma_metrica_diaria</code>.
          </div>
        </>
      )}
    </Shell>
  );
}

function Card({ titulo, valor }: { titulo: string; valor: number }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="text-xs uppercase tracking-wide text-ink-300">{titulo}</div>
      <div className="mt-1 font-display text-3xl text-brand-800">{valor}</div>
    </div>
  );
}
