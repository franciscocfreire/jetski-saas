// A caixa de entrada das personas: a API do Mailpit do espelho.
//
// Só é acessível de dentro da VM (127.0.0.1:8025) ou por túnel SSH — é onde caem
// TODOS os e-mails do espelho, inclusive links de ativação de conta.

import { ErroHttp, pedir } from './http.ts';

export interface Convite {
  /** JWT do link mágico de ativação. */
  magicToken: string;
  /** Senha temporária — vira a senha do primeiro login, que obriga a trocá-la. */
  senhaTemporaria: string;
}

interface Resumo { ID: string; Subject: string; Created: string }

async function ultimaMensagemPara(mailpit: string, email: string, desde: Date): Promise<{ Text: string; HTML: string } | undefined> {
  const busca = await pedir(`${mailpit}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`);
  if (busca.status !== 200) throw new ErroHttp('busca no Mailpit', busca);
  // Só mensagens POSTERIORES ao cadastro: a caixa pode guardar um convite antigo para o
  // mesmo e-mail (espelho reaproveitado, cadastro refeito), cujo token já não vale.
  const msgs = (JSON.parse(busca.corpo) as { messages: Resumo[] }).messages.filter((m) => new Date(m.Created) >= desde);
  if (msgs.length === 0) return undefined;
  const r = await pedir(`${mailpit}/api/v1/message/${msgs[0].ID}`); // a API devolve a mais recente primeiro
  if (r.status !== 200) throw new ErroHttp('leitura de mensagem no Mailpit', r);
  return JSON.parse(r.corpo) as { Text: string; HTML: string };
}

/** Extrai link mágico e senha temporária do e-mail de convite. Exportada para teste. */
export function lerConvite(texto: string, html: string): Convite | undefined {
  const link = /magic-activate\?token=([A-Za-z0-9._-]+)/.exec(`${texto}\n${html}`);
  // No texto: uma linha "*Senha temporária:*" e a senha na primeira linha não vazia depois dela.
  const linhas = texto.split(/\r?\n/).map((l) => l.trim());
  const i = linhas.findIndex((l) => /senha tempor[áa]ria:/i.test(l));
  let senha: string | undefined;
  if (i >= 0) {
    const naMesmaLinha = linhas[i].replace(/^.*senha tempor[áa]ria:\**\s*/i, '').replace(/\*+$/, '');
    senha = naMesmaLinha || linhas.slice(i + 1).find((l) => l !== '');
  }
  if (!link || !senha) return undefined;
  return { magicToken: link[1], senhaTemporaria: senha.replace(/^\*+|\*+$/g, '') };
}

/** Espera o e-mail de convite chegar (o envio é assíncrono). */
export async function esperarConvite(mailpit: string, email: string, desde: Date, timeoutMs = 60_000): Promise<Convite> {
  const limite = Date.now() + timeoutMs;
  for (;;) {
    const msg = await ultimaMensagemPara(mailpit, email, desde);
    const convite = msg && lerConvite(msg.Text, msg.HTML);
    if (convite) return convite;
    if (Date.now() > limite) {
      throw new Error(`Nenhum convite para ${email} no Mailpit em ${timeoutMs / 1000}s` + (msg ? ' (chegou e-mail, mas sem link/senha reconhecíveis)' : ''));
    }
    await new Promise((ok) => setTimeout(ok, 2_000));
  }
}
