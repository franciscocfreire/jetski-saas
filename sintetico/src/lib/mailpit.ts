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

export interface Cabecalho { ID: string; Subject: string; Created: string; From: { Address: string; Name: string }; Attachments: number }

/** Cabeçalhos das mensagens para `email` posteriores a `desde`, da mais recente para a mais antiga. */
export async function mensagensPara(mailpit: string, email: string, desde: Date): Promise<Cabecalho[]> {
  const busca = await pedir(`${mailpit}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`);
  if (busca.status !== 200) throw new ErroHttp('busca no Mailpit', busca);
  return (JSON.parse(busca.corpo) as { messages: Cabecalho[] }).messages.filter((m) => new Date(m.Created) >= desde);
}

/** Extrai link mágico e senha temporária do e-mail de convite. Exportada para teste. */
export function lerConvite(texto: string, html: string): Convite | undefined {
  const link = /magic-activate\?token=([A-Za-z0-9._-]+)/.exec(`${texto}\n${html}`);
  // A senha é aleatória e PODE começar ou terminar com '*' (já aconteceu: "*x1Z%Yn35bgG").
  // No HTML ela vem sozinha num parágrafo, sem ambiguidade; o texto puro, onde '*' também é
  // marca de negrito, fica só como reserva — e sem aparar asteriscos da linha da senha.
  const noHtml = /senha tempor[áa]ria:\s*<\/strong>\s*<\/p>\s*<p[^>]*>\s*([^<]+?)\s*<\/p>/i.exec(html);
  let senha = noHtml?.[1].replace(/&(amp|lt|gt|quot|#39);/g, (_m, e: string) => ({ amp: '&', lt: '<', gt: '>', quot: '"', '#39': "'" })[e] as string);
  if (!senha) {
    const linhas = texto.split(/\r?\n/).map((l) => l.trim());
    const i = linhas.findIndex((l) => /senha tempor[áa]ria:/i.test(l));
    if (i >= 0) senha = linhas[i].replace(/^.*senha tempor[áa]ria:\*{0,2}\s*/i, '') || linhas.slice(i + 1).find((l) => l !== '');
  }
  if (!link || !senha) return undefined;
  return { magicToken: link[1], senhaTemporaria: senha };
}

/**
 * Espera chegar, para `email`, uma mensagem posterior a `desde` da qual `extrair` tire
 * algo. É o "olhar o celular" das personas: convite, link de verificação, código de login.
 */
export async function esperarNoEmail<T>(
  mailpit: string,
  email: string,
  desde: Date,
  extrair: (texto: string, html: string) => T | undefined,
  oQue: string,
  timeoutMs = 60_000,
): Promise<T> {
  const limite = Date.now() + timeoutMs;
  for (;;) {
    const msg = await ultimaMensagemPara(mailpit, email, desde);
    const achado = msg && extrair(msg.Text, msg.HTML);
    if (achado !== undefined) return achado;
    if (Date.now() > limite) {
      throw new Error(`${oQue} para ${email} não chegou ao Mailpit em ${timeoutMs / 1000}s` + (msg ? ' (chegou e-mail, mas sem o conteúdo esperado)' : ''));
    }
    await new Promise((ok) => setTimeout(ok, 2_000));
  }
}

/** Código de 6 dígitos do login por e-mail (SPI meujet-email-code). Exportada para teste. */
export function lerCodigo(texto: string): string | undefined {
  return /(?<!\d)(\d{6})(?!\d)/.exec(texto)?.[1];
}

/** Link de verificação de e-mail enviado pelo Keycloak (action token). Exportada para teste. */
export function lerLinkDeVerificacao(texto: string, html: string): string | undefined {
  const m = /https?:\/\/[^\s"<>]+\/login-actions\/action-token\?[^\s"<>]+/.exec(`${texto}\n${html}`);
  return m?.[0].replace(/&amp;/g, '&');
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
