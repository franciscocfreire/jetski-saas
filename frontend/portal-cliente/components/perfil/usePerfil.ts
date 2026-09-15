"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useSession } from "next-auth/react";
import { getSelf, ApiError, type CustomerSelf } from "@/lib/api";

/**
 * Sessão + identidade global (/v1/customers/self) compartilhadas pelo hub do
 * Perfil e pelas subpáginas. Deslogado → /login.
 */
export function usePerfil() {
  const { data: session, status } = useSession();
  const router = useRouter();
  const token = session?.accessToken;
  const [self, setSelf] = useState<CustomerSelf | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [buscando, setBuscando] = useState(true);

  const recarregar = useCallback(async () => {
    if (!token) return;
    setErro(null);
    try {
      setSelf(await getSelf(token));
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível carregar o perfil.");
    } finally {
      setBuscando(false);
    }
  }, [token]);

  useEffect(() => {
    if (status === "unauthenticated") {
      router.replace("/login");
      return;
    }
    if (status === "authenticated" && token) recarregar();
  }, [status, token, recarregar, router]);

  return {
    session,
    token,
    self,
    setSelf,
    erro,
    recarregar,
    carregando: status === "loading" || (buscando && !erro),
  };
}
