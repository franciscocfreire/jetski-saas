import type { Browser } from '@playwright/test';
import {
  CAPITANIA_CODIGO,
  MAILPIT_SMTP_HOST,
  MAILPIT_SMTP_PORT,
  OPERADOR_PLATAFORMA_EMAIL,
  OPERADOR_PLATAFORMA_SENHA,
} from './ambiente';
import { capitaniaId, garantirPlanoSoDelegada } from './banco';
import { empresa, plataforma, publico, type ApiEmpresa, type ApiPlataforma } from './backend';
import { tokenRopc } from './keycloak';
import { entrar, type Sessao } from './login';

/**
 * Preparo do par EAMA emissora × operadora delegada. Tudo o que é PRÉ-REQUISITO vai por
 * API; o que é regra sob teste (vínculo, balcão, painel) fica para a spec, pela UI.
 *
 * Endereços de e-mail são únicos por execução e distintos entre as empresas: é assim que
 * o Mailpit prova quem remeteu (smtp_from) e para onde foi (marinha_email) cada mensagem.
 */

const DOMINIO = 'e2e-delegada.test';

export interface Empresa {
  papel: 'EAMA' | 'OPERADORA';
  tenantId: string;
  slug: string;
  razaoSocial: string;
  adminEmail: string;
  sessao: Sessao;
  api: ApiEmpresa;
  /** Capitania configurada pela empresa. Na operadora, NUNCA pode receber o ofício. */
  marinhaEmail: string;
  emailOficial: string;
  emailRemetente: string;
  smtpFrom: string;
  responsavelNome: string;
  telefone: string;
  eamaRegistro?: string;
  instrutor: { id: string; nome: string };
  modeloId?: string;
}

export interface ParDelegado {
  sufixo: string;
  eama: Empresa;
  operadora: Empresa;
  plataforma: ApiPlataforma;
  saldoInicialOperadora: number;
  encerrar: () => Promise<void>;
}

const esperar = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** Signup + ativação; devolve a senha temporária do admin. */
export async function cadastrar(razaoSocial: string, slug: string, adminEmail: string): Promise<{ tenantId: string; senha: string }> {
  const api = await publico();
  try {
    const { tenantId } = await api.signup({ razaoSocial, slug, adminEmail, adminNome: `Admin ${razaoSocial}` });
    // O last-email é global: confere que é o NOSSO e-mail antes de usar o token.
    for (let tentativa = 0; tentativa < 20; tentativa++) {
      const mail = await api.ultimoEmail();
      if (mail.success && mail.to?.toLowerCase() === adminEmail.toLowerCase() && mail.magicToken) {
        await api.ativar(mail.magicToken);
        return { tenantId, senha: mail.temporaryPassword ?? '' };
      }
      await esperar(500);
    }
    throw new Error(`Ativação de ${slug}: e-mail de ativação para ${adminEmail} não apareceu`);
  } finally {
    await api.dispose();
  }
}

function tokenDaSessao(sessao: Sessao): () => Promise<string> {
  return async () => {
    const s = await sessao.page.evaluate(async () => {
      const res = await fetch('/api/auth/session', { credentials: 'include' });
      return res.json();
    });
    if (!s?.accessToken) throw new Error('Sessão sem accessToken (expirou?)');
    return s.accessToken as string;
  };
}

export async function prepararParDelegado(browser: Browser, sufixo: string): Promise<ParDelegado> {
  const plat = await plataforma(await tokenRopc(OPERADOR_PLATAFORMA_EMAIL, OPERADOR_PLATAFORMA_SENHA));
  const planoId = Number(process.env.E2E_PLANO_SO_DELEGADA_ID) || garantirPlanoSoDelegada();
  const capitania = capitaniaId(CAPITANIA_CODIGO);

  const definicoes = [
    { papel: 'EAMA' as const, rotulo: 'eama', razao: `EAMA E2E ${sufixo} LTDA` },
    { papel: 'OPERADORA' as const, rotulo: 'operadora', razao: `Operadora E2E ${sufixo} LTDA` },
  ];

  const criadas: Array<{ papel: 'EAMA' | 'OPERADORA'; tenantId: string; slug: string }> = [];
  const empresas: Empresa[] = [];

  const encerrar = async () => {
    for (const e of empresas) await e.sessao.context.close().catch(() => undefined);
    if (process.env.E2E_MANTER_EMPRESAS === '1') {
      console.log(`[delegada] E2E_MANTER_EMPRESAS=1 — mantendo ${criadas.map((c) => c.slug).join(', ')}`);
    } else {
      for (const c of criadas) {
        await plat.excluir(c.tenantId, c.slug).catch((e) => console.warn(`[delegada] limpeza de ${c.slug}: ${e.message}`));
      }
    }
    await plat.dispose();
  };

  try {
    // 1–2. Cadastro, ativação e aprovação (a aprovação cria a Trial com todos os módulos).
    for (const d of definicoes) {
      const slug = `e2e-${d.rotulo}-${sufixo}`;
      const adminEmail = `admin.${d.rotulo}.${sufixo}@${DOMINIO}`;
      const { tenantId, senha } = await cadastrar(d.razao, slug, adminEmail);
      criadas.push({ papel: d.papel, tenantId, slug });
      await plat.aprovar(tenantId);

      // Login no navegador: troca a senha temporária e dá o token de ADMIN_TENANT.
      const sessao = await entrar(browser, adminEmail, senha);
      const api = await empresa(tenantId, tokenDaSessao(sessao));

      const r = d.rotulo;
      const cfg = {
        marinhaEmail: `capitania.${r}.${sufixo}@${DOMINIO}`,
        emailOficial: `oficial.${r}.${sufixo}@${DOMINIO}`,
        emailRemetente: `contato.${r}.${sufixo}@${DOMINIO}`,
        smtpFrom: `smtp.${r}.${sufixo}@${DOMINIO}`,
        responsavelNome: d.papel === 'EAMA' ? `Responsável EAMA ${sufixo}` : `Responsável Operadora ${sufixo}`,
        telefone: d.papel === 'EAMA' ? '(13) 3000-1000' : '(13) 3000-2000',
      };

      // 3. Mesma capitania nas duas; registro só na EAMA.
      const eamaRegistro = d.papel === 'EAMA' ? `EAMA-E2E-${sufixo}` : undefined;
      await api.salvarPerfilEmissora(capitania, eamaRegistro);

      // 5. Dados do ofício + SMTP próprio apontando para o Mailpit (From distinto por empresa).
      await api.salvarConfigGeral({
        ...cfg,
        smtpHost: MAILPIT_SMTP_HOST,
        smtpPort: MAILPIT_SMTP_PORT,
        smtpUsername: `smtp-${r}`,
        smtpPassword: 'mailpit-aceita-qualquer',
        smtpStarttls: false,
      });

      // 8. Instrutor em CADA empresa: o da operadora não pode aparecer na emissão delegada.
      const instrutor = await api.criarInstrutor({
        nome: d.papel === 'EAMA' ? `Instrutor EAMA ${sufixo}` : `Instrutor Operadora ${sufixo}`,
        cpf: d.papel === 'EAMA' ? '111.444.777-35' : '529.982.247-25',
        rg: '12.345.678-9',
        orgaoEmissor: 'SSP/SP',
        cha: d.papel === 'EAMA' ? `CHA-EAMA-${sufixo}` : `CHA-OP-${sufixo}`,
      });

      empresas.push({
        papel: d.papel, tenantId, slug, razaoSocial: d.razao, adminEmail, sessao, api,
        ...cfg, eamaRegistro, instrutor: { id: instrutor.id, nome: instrutor.nome },
      });
    }

    const [eama, operadora] = empresas;

    // 4. Habilitação da EAMA (exige capitania + registro já salvos).
    await plat.habilitarEmissora(eama.tenantId);

    // 6–7. Operadora: plano sem EMISSAO_PROPRIA (depois da aprovação!) e créditos.
    await plat.mudarPlano(operadora.tenantId, planoId);
    await plat.lancarCreditos(operadora.tenantId, 10, `e2e emissão delegada ${sufixo}`);

    // 9. Modelo para o balcão da operadora.
    operadora.modeloId = (await operadora.api.criarModelo(`SeaDoo E2E ${sufixo}`)).id;

    const saldoInicialOperadora = await operadora.api.saldo();
    return { sufixo, eama, operadora, plataforma: plat, saldoInicialOperadora, encerrar };
  } catch (e) {
    await encerrar();
    throw e;
  }
}

// ---------------------------------------------------------------------------
// Empresa única (E2E de cadastros): uma empresa nova, aprovada e logada, sem par
// ---------------------------------------------------------------------------

export interface EmpresaSimples {
  tenantId: string;
  slug: string;
  razaoSocial: string;
  adminEmail: string;
  sessao: Sessao;
  api: ApiEmpresa;
  plataforma: ApiPlataforma;
  encerrar: () => Promise<void>;
}

/**
 * Cria, ativa, aprova e loga uma empresa nova (admin ADMIN_TENANT, sem 2FA). A aprovação
 * cria a assinatura Trial. `encerrar` fecha o navegador e exclui a empresa (IMEDIATO),
 * salvo com E2E_MANTER_EMPRESAS=1.
 */
export async function prepararEmpresa(browser: Browser, sufixo: string, rotulo = 'cadastros'): Promise<EmpresaSimples> {
  const plat = await plataforma(await tokenRopc(OPERADOR_PLATAFORMA_EMAIL, OPERADOR_PLATAFORMA_SENHA));
  const slug = `e2e-${rotulo}-${sufixo}`;
  const razaoSocial = `Empresa E2E ${rotulo} ${sufixo} LTDA`;
  const adminEmail = `admin.${rotulo}.${sufixo}@${DOMINIO}`;
  let tenantId: string | undefined;
  let sessao: Sessao | undefined;

  const encerrar = async () => {
    await sessao?.context.close().catch(() => undefined);
    if (tenantId && process.env.E2E_MANTER_EMPRESAS !== '1') {
      await plat.excluir(tenantId, slug).catch((e) => console.warn(`[cadastros] limpeza de ${slug}: ${e.message}`));
    } else if (tenantId) {
      console.log(`[cadastros] E2E_MANTER_EMPRESAS=1 — mantendo ${slug}`);
    }
    await plat.dispose();
  };

  try {
    const criado = await cadastrar(razaoSocial, slug, adminEmail);
    tenantId = criado.tenantId;
    await plat.aprovar(tenantId);
    sessao = await entrar(browser, adminEmail, criado.senha);
    const api = await empresa(tenantId, tokenDaSessao(sessao));
    return { tenantId, slug, razaoSocial, adminEmail, sessao, api, plataforma: plat, encerrar };
  } catch (e) {
    await encerrar();
    throw e;
  }
}
