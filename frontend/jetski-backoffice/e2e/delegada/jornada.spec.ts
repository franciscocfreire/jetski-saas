import { expect, test, type Page } from '@playwright/test';
import { CAPITANIA_CODIGO, sufixoExecucao } from './helpers/ambiente';
import { atenderNoBalcao, escolher, marcar, type ClienteBalcao } from './helpers/balcao';
import { capitaniaId } from './helpers/banco';
import { gerarCpf } from './helpers/cpf';
import { prepararParDelegado, type Empresa, type ParDelegado } from './helpers/empresas';
import { buscar, esperarMensagem, esperarMensagens, nomesDosAnexos } from './helpers/mailpit';

/**
 * Fase 3 do plano (e2e/EMISSAO_DELEGADA_E2E_PLANO.md): a jornada da emissão delegada
 * entre duas empresas criadas na hora — uma EAMA emissora e uma operadora.
 *
 * O que ela garante (R1–R12 do plano): vínculo bilateral; instrutor só da EAMA no balcão;
 * crédito debitado da operadora; ofício à Capitania com destino, remetente (SMTP/From),
 * Reply-To e assinatura da EAMA, identificando a operadora só como "operado por"; via do
 * cliente e aviso à EAMA pela operadora; painel e reenvio da EAMA; kill switch — tudo com
 * a RLS real do backend de dev (jetski_app).
 *
 * Requer o backend com os PRs #9 (ofício pela EAMA) e #12 (azp) — ver o plano, §6.
 */
test.describe.serial('emissão delegada · jornada EAMA × operadora', () => {
  const sufixo = sufixoExecucao();
  const cliente: ClienteBalcao = {
    nome: `Condutor E2E ${sufixo}`,
    cpf: gerarCpf(),
    email: `condutor.${sufixo}@e2e-delegada.test`,
  };
  const gruNumero = `E2E-GRU-${sufixo}`;
  const OFICIO = 'Solicitação de Emissão de CHA-MTA-E';

  let par: ParDelegado;
  let eama: Empresa;
  let operadora: Empresa;
  let reservaId: string;
  let saldoAposVinculo: number;

  const parceria = (page: Page, parceiro: Empresa) =>
    page.locator(`[data-testid="delegada-parceria"][data-parceiro="${parceiro.razaoSocial}"]`);

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(5 * 60 * 1000);
    par = await prepararParDelegado(browser, sufixo);
    ({ eama, operadora } = par);
    // Videoaula: sem o player do YouTube a tela oferece a confirmação manual.
    await operadora.sessao.context.route('**://*.youtube.com/**', (r) => r.abort());
    await operadora.sessao.context.route('**://*.ytimg.com/**', (r) => r.abort());
  });

  test.afterAll(async () => {
    await par?.encerrar();
  });

  // --------------------------------------------------------------------------------
  test('negativo · empresas de capitanias diferentes não formam vínculo', async () => {
    await operadora.api.salvarPerfilEmissora(capitaniaId('CPSC'));
    try {
      const r = await operadora.api.convidarCru(eama.slug, 'OPERADORA');
      expect(r.status).toBe(400);
      expect(JSON.stringify(r.corpo).toLowerCase()).toContain('capitania');
    } finally {
      await operadora.api.salvarPerfilEmissora(capitaniaId(CAPITANIA_CODIGO));
    }
    expect(await operadora.api.listarVinculos()).toEqual([]);
  });

  // --------------------------------------------------------------------------------
  test('A · operadora convida a EAMA e a EAMA aceita o termo (R1)', async () => {
    const op = operadora.sessao.page;
    await op.goto('/dashboard/emissao-delegada');
    await op.getByTestId('delegada-convite-slug').fill(eama.slug);
    await escolher(op, op.getByTestId('delegada-convite-papel'), 'Sou a operadora');
    await op.getByTestId('delegada-convite-enviar').click();
    await expect(parceria(op, eama)).toHaveAttribute('data-status', 'CONVIDADO');
    await expect(parceria(op, eama).getByTestId('delegada-parceria-aguardando')).toBeVisible();

    const em = eama.sessao.page;
    await em.goto('/dashboard/emissao-delegada');
    await expect(parceria(em, operadora)).toHaveAttribute('data-status', 'CONVIDADO');
    await parceria(em, operadora).getByTestId('delegada-parceria-aceitar').click();
    await marcar(em.getByTestId('delegada-termo-aceito'));
    await em.getByTestId('delegada-termo-confirmar').click();
    await expect(parceria(em, operadora)).toHaveAttribute('data-status', 'ATIVO');

    await op.reload();
    await expect(parceria(op, eama)).toHaveAttribute('data-status', 'ATIVO');

    // Papel exclusivo (§8.M): a operadora segue na Trial (todos os módulos), mas a
    // parceria em vigor a torna delegada — e ela não é emissora.
    expect((await operadora.api.modoEmissao()).modo).toBe('DELEGADA');
    expect((await operadora.api.perfilEmissora()).emissoraHabilitada).toBe(false);
    await expect(op.getByTestId('delegada-perfil-operadora')).toBeVisible();
    await expect(op.getByTestId('delegada-convite-bloqueado')).toBeVisible();

    // Avisos das transições, cada um ao outro lado da parceria.
    await esperarMensagem({ para: eama.emailRemetente, assunto: 'Convite de parceria de emissão delegada' });
    await esperarMensagem({ para: operadora.emailRemetente, assunto: 'Parceria de emissão delegada ativada' });

    // Ativar a parceria zera o bônus de adesão da operadora (anti-fraude); comprados ficam.
    saldoAposVinculo = await operadora.api.saldo();
    expect(saldoAposVinculo).toBeLessThanOrEqual(par.saldoInicialOperadora);
    expect(saldoAposVinculo).toBeGreaterThanOrEqual(10);
  });

  // --------------------------------------------------------------------------------
  test('B · EAMA designa o próprio instrutor para a parceria', async () => {
    const em = eama.sessao.page;
    await parceria(em, operadora).getByTestId('delegada-parceria-designar').click();
    await marcar(em.locator(`[data-testid="delegada-designacao-instrutor"][data-instrutor-id="${eama.instrutor.id}"]`));
    await em.getByTestId('delegada-designacao-salvar').click();
    await expect(em.getByTestId('delegada-designacao-salvar')).toHaveCount(0);
  });

  // --------------------------------------------------------------------------------
  test('C · balcão da operadora emite com o instrutor da EAMA e debita a operadora (R2, R3)', async () => {
    test.setTimeout(8 * 60 * 1000);
    const op = operadora.sessao.page;
    reservaId = await atenderNoBalcao(op, operadora.api, cliente, gruNumero);

    // R2: o dropdown lista o instrutor da EAMA e nunca o da própria operadora.
    await op.getByTestId('balcao-emissao-instrutor').click();
    const opcoes = op.getByTestId('balcao-emissao-instrutor-opcao');
    await expect(opcoes.first()).toBeVisible();
    const nomes = await opcoes.allTextContents();
    expect(nomes.some((n) => n.includes(eama.instrutor.nome))).toBeTruthy();
    expect(nomes.some((n) => n.includes(operadora.instrutor.nome))).toBeFalsy();
    await opcoes.filter({ hasText: eama.instrutor.nome }).first().click();

    await expect(op.getByTestId('balcao-emissao-emitir')).toBeEnabled();
    await op.getByTestId('balcao-emissao-emitir').click();
    await expect(op.getByTestId('balcao-emissao-resultado')).toBeVisible({ timeout: 120_000 });

    // Documentação completa → a Marinha recebe. Se faltou algo, falha dizendo o quê.
    const pendente = op.getByTestId('balcao-emissao-marinha-pendente');
    if (await pendente.isVisible().catch(() => false)) {
      throw new Error(`Marinha não notificada — pendências: ${await pendente.innerText()}`);
    }
    await expect(op.getByTestId('balcao-emissao-envio-marinha')).toHaveAttribute('data-status', 'ENVIADO', {
      timeout: 120_000,
    });
    await expect(op.getByTestId('balcao-emissao-envio-cliente')).toHaveAttribute('data-status', 'ENVIADO', {
      timeout: 120_000,
    });

    // R3: crédito debitado da OPERADORA.
    expect(await operadora.api.saldo()).toBe(saldoAposVinculo - 1);
  });

  // --------------------------------------------------------------------------------
  test('D · ofício sai pela EAMA; via do cliente e aviso à EAMA saem pela operadora (R4–R9)', async () => {
    const oficio = await esperarMensagem({ para: eama.marinhaEmail, assunto: OFICIO });

    // R5: remetente = SMTP da EAMA. R6: Reply-To = e-mail oficial da EAMA.
    expect(oficio.From.Address).toBe(eama.smtpFrom);
    expect(oficio.ReplyTo.map((r) => r.Address)).toContain(eama.emailOficial);
    // A operadora delegada acompanha o ofício em cópia (e-mail oficial dela).
    expect((oficio.Cc ?? []).map((c) => c.Address)).toContain(operadora.emailOficial);

    // R7: assinatura da EAMA; a operadora só aparece como "operado por", sem contatos.
    expect(oficio.HTML).toContain(`O EAMA <b>${eama.razaoSocial}</b>`);
    expect(oficio.HTML).toContain(`credenciamento nº ${eama.eamaRegistro}`);
    expect(oficio.HTML).toContain(eama.responsavelNome);
    expect(oficio.HTML).toContain(`operado por <b>${operadora.razaoSocial}</b>`);
    for (const contatoDaOperadora of [operadora.emailOficial, operadora.telefone, operadora.smtpFrom, operadora.responsavelNome]) {
      expect(oficio.HTML).not.toContain(contatoDaOperadora);
    }
    expect(oficio.HTML).toContain(gruNumero);
    expect(nomesDosAnexos(oficio).some((n) => n.startsWith(cliente.nome) && n.endsWith('.pdf'))).toBeTruthy();

    // R4: nada vai para a Capitania configurada pela operadora.
    expect(await buscar({ para: operadora.marinhaEmail })).toHaveLength(0);

    // R8: via do cliente pela operadora.
    const viaCliente = await esperarMensagem({ para: cliente.email, assunto: 'Seus documentos' });
    expect(viaCliente.From.Address).toBe(operadora.smtpFrom);
    expect(nomesDosAnexos(viaCliente).some((n) => n.endsWith('.pdf'))).toBeTruthy();

    // R9: a EAMA é avisada de que emitiram em nome dela.
    const aviso = await esperarMensagem({ para: eama.emailRemetente, assunto: 'Documento emitido em seu nome' });
    expect(aviso.From.Address).toBe(operadora.smtpFrom);
    expect(aviso.HTML).toContain(operadora.razaoSocial);
  });

  // --------------------------------------------------------------------------------
  test('E · painel da EAMA mostra a emissão e o reenvio sai pela EAMA sem novo crédito (R9, R10)', async () => {
    const em = eama.sessao.page;
    await em.goto('/dashboard/emissao-delegada');
    const painel = em.getByTestId('delegada-painel');
    await expect(painel).toBeVisible();

    const linha = painel.getByTestId('delegada-painel-emissao').first();
    await expect(linha).toContainText(cliente.nome);
    await expect(linha).toContainText(operadora.razaoSocial);
    await expect(linha).toContainText(eama.instrutor.nome);
    await expect(linha).toContainText(gruNumero);
    await expect(
      painel.locator(`[data-testid="delegada-painel-contagem"][data-operadora-id="${operadora.tenantId}"]`),
    ).toHaveAttribute('data-total', '1');

    // Reenviar pede o destino num window.prompt: vazio = Capitania configurada pela EAMA.
    em.once('dialog', (d) => d.accept(''));
    await linha.getByTestId('delegada-painel-reenviar').click();
    await expect(linha).toContainText(eama.marinhaEmail, { timeout: 30_000 });

    const reenvio = await esperarMensagem({ para: eama.marinhaEmail, assunto: '(reenvio)' });
    expect(reenvio.From.Address).toBe(eama.smtpFrom);
    expect(reenvio.HTML).toContain(`operado por <b>${operadora.razaoSocial}</b>`);

    expect(await operadora.api.saldo()).toBe(saldoAposVinculo - 1);
  });

  // --------------------------------------------------------------------------------
  test('F · kill switch: EAMA bloqueia e a operadora não emite; liberar volta a emitir (R11)', async () => {
    const em = eama.sessao.page;
    await em.goto('/dashboard/emissao-delegada');
    await parceria(em, operadora).getByTestId('delegada-parceria-bloquear').click();
    await expect(parceria(em, operadora)).toHaveAttribute('data-status', 'BLOQUEADO');

    // reemitir=true: sem ele, a idempotência devolveria o documento existente antes de
    // checar o vínculo.
    const bloqueada = await operadora.api.emitirCru(reservaId, true);
    expect(bloqueada.status).toBe(400);
    expect(JSON.stringify(bloqueada.corpo)).toContain('suspensa pelo parceiro');
    expect(await operadora.api.saldo()).toBe(saldoAposVinculo - 1);

    await parceria(em, operadora).getByTestId('delegada-parceria-liberar').click();
    await expect(parceria(em, operadora)).toHaveAttribute('data-status', 'ATIVO');

    const liberada = await operadora.api.emitirCru(reservaId, true);
    expect(liberada.status, JSON.stringify(liberada.corpo)).toBe(200);
    expect(await operadora.api.saldo()).toBe(saldoAposVinculo - 2);

    // Três ofícios à Capitania da EAMA: emissão, reenvio e reemissão — todos pela EAMA.
    const oficios = await esperarMensagens({ para: eama.marinhaEmail, assunto: OFICIO, quantidade: 3 });
    for (const m of oficios) expect(m.From.Address).toBe(eama.smtpFrom);
  });

  // --------------------------------------------------------------------------------
  test('H · instrutor da operadora só assina depois de aprovado pela EAMA, que pode removê-lo (NORMAM-212)', async () => {
    const idProprio = operadora.instrutor.id;

    // Sem aprovação: fora da lista e recusado na emissão.
    expect((await operadora.api.instrutoresParceiro()).map((i) => i.id)).not.toContain(idProprio);
    await operadora.api.definirInstrutor(reservaId, idProprio);
    const semAprovacao = await operadora.api.emitirCru(reservaId, true);
    expect(semAprovacao.status).toBe(400);
    expect(JSON.stringify(semAprovacao.corpo)).toContain('aprovado');

    // A operadora pede a aprovação na tela de instrutores (cadastro anterior à parceria).
    const op = operadora.sessao.page;
    await op.goto('/dashboard/instrutores');
    const linhaPropria = op.locator(`[data-testid="instrutores-proprio"][data-instrutor-id="${idProprio}"]`);
    await expect(linhaPropria).toHaveAttribute('data-aprovacao', 'NAO_ENVIADO');
    await linhaPropria.getByTestId('instrutores-proprio-solicitar').click();
    await expect(linhaPropria).toHaveAttribute('data-aprovacao', 'PENDENTE');

    // A EAMA aprova na parceria.
    const em = eama.sessao.page;
    await em.goto('/dashboard/emissao-delegada');
    await parceria(em, operadora).getByTestId('delegada-parceria-instrutores-operadora').click();
    const pedido = em.locator(`[data-testid="delegada-instrutor-operadora"][data-instrutor-id="${idProprio}"]`);
    await expect(pedido).toHaveAttribute('data-status', 'PENDENTE');
    await expect(pedido).toContainText(operadora.instrutor.nome);
    await pedido.getByTestId('delegada-instrutor-operadora-aprovar').click();
    await expect(pedido).toHaveAttribute('data-status', 'APROVADO');

    // Aprovado: disponível com origem OPERADORA, e a emissão sai com ele.
    expect(await operadora.api.instrutoresParceiro()).toContainEqual(
      expect.objectContaining({ id: idProprio, origem: 'OPERADORA' }),
    );
    const aprovada = await operadora.api.emitirCru(reservaId, true);
    expect(aprovada.status, JSON.stringify(aprovada.corpo)).toBe(200);
    expect(await operadora.api.saldo()).toBe(saldoAposVinculo - 3);

    // A EAMA remove a aprovação: o instrutor sai da lista da operadora.
    em.once('dialog', (d) => d.accept('instrutor desligado'));
    await pedido.getByTestId('delegada-instrutor-operadora-remover').click();
    await expect(pedido).toHaveAttribute('data-status', 'REMOVIDO');
    expect((await operadora.api.instrutoresParceiro()).map((i) => i.id)).not.toContain(idProprio);

    // A habilitação volta para o instrutor da EAMA: nada fica apontando para um removido.
    await operadora.api.definirInstrutor(reservaId, eama.instrutor.id);
  });

  // --------------------------------------------------------------------------------
  test('G · operadora revoga a parceria', async () => {
    const op = operadora.sessao.page;
    await op.goto('/dashboard/emissao-delegada');
    op.once('dialog', (d) => d.accept());
    await parceria(op, eama).getByTestId('delegada-parceria-revogar').click();
    await expect(parceria(op, eama)).toHaveAttribute('data-status', 'REVOGADO');
  });
});
