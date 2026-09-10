#!/usr/bin/env python3
"""
Monta a apresentação à Capitania a partir de UMA fonte de verdade (SLIDES).

Gera:
  deck.html          slides 16:9, setas para navegar, "n" mostra a narração
  narracao/NN-*.txt  um arquivo por slide, texto pronto para a voz sintética
  ORDEM.txt          a ordem de montagem, com durações e o arquivo de captura

Para mexer no texto, edite SLIDES aqui e rode de novo:  python3 montar.py

As capturas NÃO são copiadas: o deck aponta para ../capturas-apresentacao/,
que é onde o harness (frontend/jetski-backoffice/e2e/capturas/capturar.mjs)
salva os PNGs. Enquanto um arquivo não existir, o slide mostra o lugar dele
reservado, com o nome esperado — assim dá para revisar o deck antes de ter
uma única tela capturada.
"""

import pathlib
import re
import unicodedata

AQUI = pathlib.Path(__file__).parent

# ---------------------------------------------------------------------------
# Fonte de verdade. `narracao` é o que vai para a voz sintética: siglas já
# substituídas conforme a tabela de pronúncia do roteiro ("guia" em vez de
# GRU, "carteira de motonauta" em vez de CHA-MTA-E, "cinco-bê-um" em vez de
# 5-B-1) — voz sintética lê sigla mal, e a sigla já está escrita no slide.
# ---------------------------------------------------------------------------

SLIDES = [
    dict(
        n="01", dur="0:10", arquivo=None,
        titulo="Emissão digital da CHA-MTA-E",
        sub="Apresentação à Capitania dos Portos · setembro de 2026",
        tipo="capa",
        narracao="Comandante, senhores.",
    ),
    dict(
        n="02", dur="0:30", arquivo=None,
        titulo="Hoje, o processo é de papel",
        bullets=["Preencher, assinar, conferir",
                 "Fotografar e enviar por aplicativo",
                 "Vento, água, pressa — erro e ilegibilidade"],
        tipo="cartela",
        narracao="Hoje, o processo de documentação para a utilização de motos aquáticas "
                 "ainda depende, em grande parte, do preenchimento manual de documentos em papel.\n\n"
                 "Isso gera uma série de dificuldades. O cliente precisa preencher, assinar, "
                 "aguardar a conferência e, muitas vezes, o documento ainda precisa ser fotografado "
                 "e encaminhado por aplicativo. Em um ambiente de praia, com vento, água, movimento "
                 "e clientes com pressa, esse processo pode gerar erros, documentos ilegíveis e "
                 "perda de tempo.\n\n"
                 "Mas o principal ponto é que essa dificuldade não está apenas na emissão do "
                 "documento. Ela acompanha toda a operação.",
    ),
    dict(
        n="03", dur="0:22", arquivo=None, eixo="1",
        titulo="Emissão",
        sub="Preenchimento digital, assinatura eletrônica, menos etapas manuais.",
        tipo="eixo",
        narracao="Primeiro, temos a emissão: o processo poderia ser muito mais rápido e "
                 "organizado, com o preenchimento digital das informações e assinatura eletrônica, "
                 "reduzindo erros e eliminando etapas manuais desnecessárias.",
    ),
    dict(
        n="04", dur="0:35", arquivo=None, eixo="2",
        titulo="Fiscalização na água",
        sub="Água e papel não combinam. O documento molha, rasga, perde a legibilidade.",
        tipo="eixo",
        narracao="Segundo, temos a fiscalização dentro da água. O cliente atualmente pode sair "
                 "para navegar levando um documento em papel. E água e papel não combinam. O "
                 "documento pode molhar, rasgar ou perder a legibilidade. Em uma abordagem, isso "
                 "pode dificultar a conferência da documentação pelo agente fiscalizador.\n\n"
                 "Com um sistema digital, a fiscalização poderia ter acesso rápido às informações "
                 "necessárias, permitindo verificar a situação do condutor e da moto aquática de "
                 "maneira muito mais eficiente.",
    ),
    dict(
        n="05", dur="0:30", arquivo=None, eixo="3",
        titulo="Análise interna",
        sub="Informação organizada e padronizada — conferência, pesquisa, armazenamento e análise.",
        tipo="eixo",
        narracao="E terceiro, temos a análise interna da própria Marinha. Em vez de receber "
                 "documentos fotografados, separados ou com diferentes padrões de preenchimento, "
                 "a Marinha poderia receber as informações de forma organizada e padronizada, "
                 "facilitando a conferência, a pesquisa, o armazenamento e a análise dos documentos.",
    ),
    dict(
        n="06", dur="0:28", arquivo=None,
        titulo="Menos papel, menos erro, menos tempo perdido",
        bullets=["Mais agilidade, rastreabilidade e segurança",
                 "Tecnologia a favor da fiscalização e da Marinha",
                 "Mantendo o controle e a confiabilidade do processo"],
        tipo="cartela",
        narracao="Ou seja, a proposta não busca apenas digitalizar um formulário. Busca reduzir "
                 "o tempo e aumentar a eficiência em todo o ciclo: desde o momento em que o "
                 "documento é emitido, passando pela fiscalização na água, até a análise e o "
                 "controle interno da Marinha.\n\n"
                 "O objetivo é simples: menos papel, menos erro, menos tempo perdido — e mais "
                 "agilidade, rastreabilidade e segurança para todos os envolvidos. E, "
                 "principalmente, utilizar a tecnologia para facilitar o trabalho da fiscalização "
                 "e da própria Marinha, mantendo o controle e a confiabilidade que o processo exige.\n\n"
                 "O que vamos mostrar a seguir é como esse mesmo atendimento acontece no nosso "
                 "sistema — e, principalmente, o que a Capitania passa a receber no fim dele.",
    ),
    dict(
        n="07", dur="0:20", arquivo="07-balcao-oito-passos.png",
        titulo="O atendimento em oito passos",
        sub="Cliente · Passeio · Habilitação · Documentos · Orientações · Termos · Pagamento · Emissão",
        narracao="O sistema cuida de toda a operação da locadora. Mas hoje vamos entrar em um só "
                 "ponto, o primeiro eixo: o atendimento de balcão de um cliente que não tem "
                 "habilitação e precisa da carteira de motonauta para pilotar.\n\n"
                 "É um roteiro fixo, em oito passos. O operador não escolhe a ordem, e não "
                 "consegue pular nenhum deles.",
    ),
    dict(
        n="08", dur="0:30", arquivo="08a-passo-cliente.png",
        titulo="Passos 1 e 2 — quem é, e o que vai fazer",
        sub="Cadastro único da pessoa. A loja só enxerga a ficha quando existe um aluguel.",
        narracao="Primeiro identificamos a pessoa. O cadastro é feito uma única vez: se este "
                 "cliente voltar amanhã, em qualquer loja que use o sistema, os dados dele já "
                 "estão aqui — mas a loja só enxerga a ficha depois que existe um aluguel de fato.\n\n"
                 "Em seguida, o passeio: qual moto, que horário, por quanto tempo. Esta parte é "
                 "comercial. O que interessa à Marinha começa no passo seguinte.",
    ),
    dict(
        n="09", dur="0:30", arquivo="09-habilitacao-bifurcacao.png",
        titulo="Passo 3 — a pergunta que define tudo",
        bullets=["Já habilitado → registra o número e segue",
                 "Não habilitado → entra o rito da NORMAM-212"],
        narracao="Aqui o sistema faz a pergunta que define todo o resto: este cliente já é "
                 "habilitado, ou vai pilotar sob a autorização especial de motonauta?\n\n"
                 "Se ele já tem carteira, o número é registrado e o atendimento segue, sem "
                 "documentação a encaminhar à Marinha. Se não tem, entramos no caminho da norma "
                 "— e daqui em diante nenhum passo é opcional.",
    ),
    dict(
        n="10", dur="0:40", arquivo="10b-gru-paga.png",
        titulo="A GRU é recolhida antes",
        bullets=["Guia gerada em nome do locatário, paga no balcão",
                 "Pagamento conferido na origem, comprovante anexado",
                 "Sem guia paga, a emissão fica bloqueada"],
        narracao="A taxa da União é recolhida antes, no próprio balcão. O sistema gera a guia em "
                 "nome do locatário, apresenta o pagamento por pix, confere a quitação na origem "
                 "e guarda o comprovante junto do processo.\n\n"
                 "Enquanto a guia não estiver paga e confirmada, o botão de emissão fica "
                 "bloqueado. Não é uma recomendação ao operador: é uma trava. Não existe "
                 "documentação encaminhada à Capitania sem guia quitada.",
    ),
    dict(
        n="11", dur="0:20", arquivo="11-documento-identidade.png",
        titulo="Passo 4 — identidade capturada na hora",
        sub="Pela câmera, direto no processo. Sem foto de aplicativo, sem arquivo de origem desconhecida.",
        narracao="O documento de identidade é capturado ali mesmo, pela câmera, já anexado ao "
                 "processo — sem foto de aplicativo, sem arquivo que ninguém sabe de onde veio.",
    ),
    dict(
        n="12", dur="0:25", arquivo="12a-videoaula-bloqueada.png",
        titulo="Passo 5 — orientação obrigatória",
        sub="O avanço só libera quando o vídeo termina. O sistema registra que aquele locatário recebeu a orientação.",
        narracao="Antes de assinar qualquer coisa, o cliente assiste à orientação de segurança. "
                 "Repare no botão: ele só habilita quando o vídeo chega ao fim. E o sistema "
                 "registra que aquele locatário, naquele atendimento, recebeu a orientação.",
    ),
    dict(
        n="13", dur="0:45",
        arquivo=["13a-anexo-1c.png", "13b-anexo-5c.png",
                 "13c-anexo-5b1.png", "13d-anexo-5b2.png"],
        titulo="Passo 6 — o dossiê da norma, montado sozinho",
        bullets=["1-C · declaração de residência",
                 "5-C · autodeclaração de saúde",
                 "5-B-1 · atestado do instrutor credenciado",
                 "5-B-2 · declaração do locatário"],
        narracao="Este é o dossiê que a norma exige, montado pelo sistema com os dados que já "
                 "foram informados. Ninguém redigita nada.\n\n"
                 "Declaração de residência, anexo um-cê. Autodeclaração de saúde, anexo cinco-cê. "
                 "Atestado de demonstração assinado pelo instrutor credenciado, anexo cinco-bê-um. "
                 "E a declaração do próprio locatário, cinco-bê-dois. Ao final, a identidade "
                 "anexada, com carimbo identificando cada página.\n\n"
                 "Sempre no mesmo formato, sempre na mesma ordem, sempre legível — é o terceiro "
                 "eixo, o da padronização, aparecendo na prática. E, se o locatário for "
                 "estrangeiro, os anexos cinco-bê saem automaticamente em inglês, com o "
                 "passaporte como documento de referência.",
    ),
    dict(
        n="14", dur="0:25", arquivo="14a-assinatura.png",
        titulo="Assinatura com confirmação por código",
        sub="O código vai ao e-mail ou ao WhatsApp do próprio cliente. A assinatura fica amarrada a uma identidade verificada.",
        narracao="O cliente assina na tela. Antes de fechar, ele confirma um código enviado ao "
                 "e-mail ou ao WhatsApp dele. Isso amarra a assinatura a uma identidade "
                 "verificada, e não a um rabisco anônimo.",
    ),
    dict(
        n="15", dur="0:35", arquivo="15-pagina-auditoria.png",
        titulo="O documento carrega a própria prova",
        bullets=["Data, hora, endereço de rede e dispositivo",
                 "Carimbo de tempo de autoridade externa",
                 "Impressão digital do arquivo (SHA-256)"],
        narracao="E o documento carrega a própria prova: uma página de auditoria com data, hora, "
                 "endereço de rede e dispositivo; um carimbo de tempo emitido por autoridade "
                 "externa; e a impressão digital do arquivo.\n\n"
                 "Se um único caractere do documento mudar, essa impressão digital muda junto. "
                 "Qualquer pessoa pode conferir — inclusive a Capitania, anos depois.",
    ),
    dict(
        n="16", dur="0:15", arquivo="16-emissao-resultado.png",
        titulo="Passo 8 — um clique",
        sub="Gera o documento único, arquiva, registra e encaminha.",
        narracao="Um clique. O sistema gera o documento único, arquiva, registra e encaminha.",
    ),
    dict(
        n="17", dur="0:55", arquivo="17-oficio-recebido.png", destaque=True,
        titulo="O que chega à Capitania",
        bullets=["Ofício com o credenciamento da escola emissora",
                 "Itens da NORMAM-212, um a um",
                 "Anexo único, nomeado como a norma pede",
                 "Resposta ao e-mail oficial do Anexo 5-A"],
        narracao="E é isto que chega à Capitania. O assunto identifica o pedido, o nome, o cê-pê-efe "
                 "e a referência do atendimento.\n\n"
                 "O corpo é um ofício: a escola credenciada, com o número do seu credenciamento, "
                 "encaminha a documentação para análise e emissão da carteira, e lista item por "
                 "item o que exige o item cinco ponto quatro ponto dois da NORMAM duzentos e doze "
                 "— a guia paga com o número, o cinco-cê, o cinco-bê, o um-cê e a identidade.\n\n"
                 "A resposta vai para o e-mail oficial declarado no anexo cinco-á. O arquivo anexo "
                 "é um só, nomeado exatamente como a norma pede: nome completo e cê-pê-efe, ou "
                 "passaporte no caso de estrangeiro. E no rodapé, a impressão digital do arquivo.",
    ),
    dict(
        n="18", dur="0:30", arquivo="18-documento-no-celular.png",
        titulo="O documento vai para a água — no celular",
        sub="Abre por link, sem aplicativo e sem login. Não molha, não rasga, não fica ilegível.",
        narracao="Encerrado o atendimento, o cliente recebe o documento no próprio celular, por um "
                 "link que abre sem aplicativo e sem login. Ele não precisa levar papel para "
                 "dentro da água.\n\n"
                 "Este é o segundo eixo. Hoje, entregamos o documento ao cliente. O passo "
                 "seguinte, que queremos construir junto com a Marinha, é a consulta pelo lado da "
                 "fiscalização: o agente conferindo, na abordagem, a situação do condutor e da "
                 "embarcação.",
    ),
    dict(
        n="19", dur="0:25", arquivo="19-lista-emissoes.png",
        titulo="Tudo o que foi encaminhado fica registrado",
        bullets=["Duplo clique não gera dois processos",
                 "Reenvio gera novo arquivo, com nova impressão digital",
                 "Loja sem credenciamento só emite por escola parceira da mesma Capitania"],
        narracao="Tudo o que foi encaminhado fica registrado. Um duplo clique não gera dois "
                 "processos. Um reenvio não sobrescreve o anterior: gera um novo arquivo, com "
                 "nova impressão digital, e os dois ficam na trilha.\n\n"
                 "E a loja que ainda não é credenciada só consegue emitir através de uma escola "
                 "credenciada parceira, da mesma Capitania, com o vínculo registrado nos dois lados.",
    ),
    dict(
        n="20", dur="0:20", arquivo=None,
        titulo="Os três eixos",
        bullets=["1 · Emissão — funcionando",
                 "2 · Fiscalização — primeiro passo dado",
                 "3 · Análise interna — com a orientação da Marinha"],
        tipo="cartela",
        narracao="Voltando aos três eixos: a emissão, os senhores acabaram de ver funcionando. "
                 "A fiscalização na água, demos o primeiro passo, tirando o papel da mão do "
                 "cliente. E a análise interna é justamente onde precisamos da orientação da Marinha.",
    ),
]

# ---------------------------------------------------------------------------

CSS = """
:root{
  --fundo:#08131A; --fundo-2:#0D1F2A; --tinta:#EAF2F6; --tinta-fraca:#93A9B5;
  --regua:#1E3540; --mar:#4FA8CC; --trama:#D2A550;
}
*{box-sizing:border-box}
html,body{margin:0;background:#04090C;color:var(--tinta);
  font-family:"Source Serif 4",Georgia,serif;-webkit-font-smoothing:antialiased}

/* Um slide = 1920x1080 escalado por --z, para caber na janela sem reflow:
   assim o que você vê é exatamente o que sai no PDF. */
#palco{position:fixed;inset:0;display:grid;place-items:center;overflow:hidden}
.slide{position:absolute;width:1920px;height:1080px;background:var(--fundo);
  transform-origin:center center;opacity:0;pointer-events:none;
  display:grid;padding:88px 104px;gap:44px}
.slide.ativo{opacity:1;pointer-events:auto}

.olho{font-family:"IBM Plex Mono",monospace;font-size:22px;letter-spacing:.28em;
  text-transform:uppercase;color:var(--mar);margin:0}
h1{font-family:"Barlow Condensed","Arial Narrow",sans-serif;font-weight:700;
  text-transform:uppercase;letter-spacing:.005em;line-height:.94;margin:0;
  text-wrap:balance}
.sub{font-size:34px;line-height:1.4;color:var(--tinta-fraca);margin:0;max-width:44ch}
ul{margin:0;padding:0;list-style:none;display:grid;gap:18px}
li{font-size:33px;line-height:1.35;color:var(--tinta);padding-left:34px;position:relative}
li::before{content:"";position:absolute;left:0;top:.62em;width:16px;height:2px;
  background:var(--mar)}

/* capa */
.slide.capa{place-content:center;text-align:left}
.slide.capa h1{font-size:150px}
.slide.capa .sub{font-size:38px;margin-top:26px}
.slide.capa .fio{width:180px;height:5px;background:var(--trama);margin-bottom:52px}

/* cartela e eixo */
.slide.cartela{align-content:center;grid-template-columns:1fr}
.slide.cartela h1{font-size:104px;max-width:26ch}
.slide.cartela ul{margin-top:34px}
.slide.eixo{align-content:center;grid-template-columns:280px 1fr;align-items:center}
.slide.eixo .num{font-family:"Barlow Condensed",sans-serif;font-size:300px;font-weight:700;
  line-height:.8;color:var(--trama)}
.slide.eixo h1{font-size:110px}

/* captura */
.slide.tela{grid-template-rows:auto 1fr;gap:38px}
.slide.tela.com-pe{grid-template-rows:auto 1fr auto}
.slide.tela .pe-legenda{font-size:30px;line-height:1.35;color:var(--tinta-fraca);margin:0;max-width:none}
.slide.tela h1{font-size:62px}
.corpo{display:grid;gap:52px;min-height:0}
.corpo.com-lista{grid-template-columns:1fr 460px}
.moldura{border:1px solid var(--regua);background:var(--fundo-2);overflow:hidden;
  display:grid;place-items:center;min-height:0}
/* max-* em vez de width/height 100%: com 100% a imagem era esticada até a
   largura da moldura e o excedente ficava cortado pelo overflow — uma foto de
   celular (retrato, 355px) virava um recorte ampliado do topo. Assim a
   proporção manda e a peça inteira aparece, centrada. */
.moldura img{max-width:100%;max-height:100%;width:auto;height:auto;object-fit:contain;display:block;margin:auto}
.grade{display:grid;grid-template-columns:1fr 1fr;gap:18px;min-height:0}
.grade .moldura{padding:10px}
.grade figcaption,.grade .rot{display:none}
.vazio{font-family:"IBM Plex Mono",monospace;font-size:24px;color:var(--tinta-fraca);
  text-align:center;line-height:1.8;padding:40px}
.vazio b{color:var(--mar);display:block;font-size:28px;margin-bottom:14px}
.corpo .lista{align-content:center}
.slide.destaque{background:linear-gradient(160deg,#0B2030 0%,#08131A 60%)}
.slide.destaque h1{color:#fff}

/* rodapé */
.pe{position:absolute;left:104px;right:104px;bottom:44px;display:flex;
  justify-content:space-between;align-items:baseline;
  font-family:"IBM Plex Mono",monospace;font-size:19px;color:#546B78;
  border-top:1px solid var(--regua);padding-top:16px}

/* notas de narração (tecla n) */
#notas{position:fixed;left:0;right:0;bottom:0;max-height:42vh;overflow:auto;
  background:#02070A;border-top:2px solid var(--mar);padding:26px 34px;
  font-size:19px;line-height:1.65;color:#C9D8E0;display:none}
#notas.on{display:block}
#notas h4{font-family:"IBM Plex Mono",monospace;font-size:13px;letter-spacing:.2em;
  text-transform:uppercase;color:var(--mar);margin:0 0 12px}
#notas p{margin:0 0 12px;max-width:120ch}
#ajuda{position:fixed;right:16px;top:16px;font-family:"IBM Plex Mono",monospace;
  font-size:13px;color:#3E5461;letter-spacing:.08em}

@media print{
  @page{size:1920px 1080px;margin:0}
  html,body{background:#fff}
  #palco{position:static;display:block;overflow:visible}
  .slide{position:static;opacity:1;transform:none!important;page-break-after:always}
  #notas,#ajuda{display:none!important}
}
"""

JS = """
const slides = [...document.querySelectorAll('.slide')];
let i = 0;

function escalar(){
  const z = Math.min(innerWidth/1920, innerHeight/1080);
  slides.forEach(s => s.style.transform = `scale(${z})`);
}
function mostrar(n){
  i = Math.max(0, Math.min(slides.length - 1, n));
  slides.forEach((s, k) => s.classList.toggle('ativo', k === i));
  const s = slides[i];
  notas.innerHTML = '<h4>slide ' + s.dataset.n + ' · ' + s.dataset.dur + '</h4>'
    + (s.dataset.narracao || '').split('\\n\\n').map(p => '<p>' + p + '</p>').join('');
  location.hash = s.dataset.n;
}
addEventListener('resize', escalar);
addEventListener('keydown', e => {
  if (['ArrowRight',' ','PageDown','Enter'].includes(e.key)) { mostrar(i+1); e.preventDefault(); }
  else if (['ArrowLeft','PageUp','Backspace'].includes(e.key)) { mostrar(i-1); e.preventDefault(); }
  else if (e.key === 'Home') mostrar(0);
  else if (e.key === 'End') mostrar(slides.length-1);
  else if (e.key === 'n') notas.classList.toggle('on');
  else if (e.key === 'f') document.documentElement.requestFullscreen?.();
});
addEventListener('click', e => { if (!e.target.closest('#notas')) mostrar(i+1); });

// Abrir/compartilhar direto num slide (deck.html#13) e navegar pelo histórico.
function doHash(){
  const k = slides.findIndex(s => s.dataset.n === location.hash.slice(1));
  if (k >= 0 && k !== i) mostrar(k);
}
addEventListener('hashchange', doHash);
escalar();
const inicial = slides.findIndex(s => s.dataset.n === location.hash.slice(1));
mostrar(inicial >= 0 ? inicial : 0);
"""


def slug(t):
    """Nome de arquivo sem acento: o gerador de voz e o editor de vídeo vão
    engolir estes nomes, e acento em nome de arquivo dá dor de cabeça neles."""
    sem = unicodedata.normalize("NFKD", t).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", "-", sem.lower()).strip("-")[:38]


def esc(t):
    return (t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
             .replace('"', "&quot;"))


def render_slide(s, total):
    tipo = s.get("tipo", "tela")
    dados = (f' data-n="{s["n"]}" data-dur="{s["dur"]}"'
             f' data-narracao="{esc(s["narracao"])}"')
    classes = ["slide", tipo] + (["destaque"] if s.get("destaque") else [])
    if tipo == "tela" and s.get("sub") and not s.get("bullets"):
        classes.append("com-pe")
    partes = [f'<section class="{" ".join(classes)}"{dados}>']

    lista = ""
    if s.get("bullets"):
        lista = "<ul>" + "".join(f"<li>{esc(b)}</li>" for b in s["bullets"]) + "</ul>"

    if tipo == "capa":
        partes += [
            '<div><div class="fio"></div>',
            f'<h1>{esc(s["titulo"])}</h1>',
            f'<p class="sub">{esc(s.get("sub",""))}</p></div>',
        ]
    elif tipo == "eixo":
        partes += [
            f'<div class="num">{s["eixo"]}</div>',
            f'<div><h1>{esc(s["titulo"])}</h1>'
            f'<p class="sub" style="margin-top:28px">{esc(s.get("sub",""))}</p></div>',
        ]
    elif tipo == "cartela":
        partes += [f'<div><h1>{esc(s["titulo"])}</h1>',
                   f'<p class="sub" style="margin-top:28px">{esc(s["sub"])}</p>' if s.get("sub") else "",
                   lista, "</div>"]
    else:
        arquivos = s["arquivo"] if isinstance(s["arquivo"], list) else [s["arquivo"]]
        faltando = [a for a in arquivos
                    if not (AQUI.parent / "capturas-apresentacao" / a).exists()]
        if faltando:
            miolo = ('<div class="vazio"><b>captura pendente</b>'
                     + "<br>".join(f"capturas-apresentacao/{a}" for a in faltando)
                     + f'<br>node e2e/capturas/capturar.mjs {s["n"]}</div>')
        elif len(arquivos) == 1:
            miolo = f'<img src="../capturas-apresentacao/{arquivos[0]}" alt="">'
        else:
            # Grade: quatro páginas juntas dizem "dossiê"; uma página só não diz.
            miolo = ('<div class="grade">' + "".join(
                f'<div class="moldura"><img src="../capturas-apresentacao/{a}" alt=""></div>'
                for a in arquivos) + '</div>')
        corpo_cls = "corpo com-lista" if lista else "corpo"
        partes += [
            f'<div><p class="olho">slide {s["n"]}</p><h1>{esc(s["titulo"])}</h1></div>',
            # A grade já traz as próprias molduras; a imagem única precisa de uma.
            # Em nenhum dos casos fechamos o corpo aqui: a coluna dos bullets
            # ainda vai entrar dentro dele (fechar cedo jogava a lista para
            # fora do grid e o último item saía por baixo do rodapé).
            (f'<div class="{corpo_cls}">{miolo}' if miolo.startswith('<div class="grade"')
             else f'<div class="{corpo_cls}"><div class="moldura">{miolo}</div>'),
            f'<div class="lista">{lista}</div>' if lista else "",
            "</div>",
        ]
        # Sem bullets, a legenda vira a terceira faixa do grid — nunca por cima
        # da captura (posição absoluta cobria o rodapé da tela fotografada).
        if s.get("sub") and not lista:
            partes.append(f'<p class="pe-legenda">{esc(s["sub"])}</p>')

    partes.append(f'<div class="pe"><span>Ilha Sul Náutica · Emissão CHA-MTA-E</span>'
                  f'<span>{s["n"]} / {total}</span></div>')
    partes.append("</section>")
    return "\n".join(p for p in partes if p)


def main():
    total = f"{len(SLIDES):02d}"
    corpo = "\n".join(render_slide(s, total) for s in SLIDES)

    html = f"""<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Emissão da CHA — Capitania dos Portos</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Barlow+Condensed:wght@600;700&family=Source+Serif+4:opsz,wght@8..60,400;8..60,600&family=IBM+Plex+Mono:wght@400;500&display=swap">
<style>{CSS}</style></head><body>
<div id="ajuda">← → navega · n narração · f tela cheia</div>
<div id="palco">
{corpo}
</div>
<div id="notas"></div>
<script>{JS}</script>
</body></html>
"""
    (AQUI / "deck.html").write_text(html, encoding="utf-8")

    narr = AQUI / "narracao"
    narr.mkdir(exist_ok=True)
    for f in narr.glob("*.txt"):
        f.unlink()

    ordem = ["# Ordem de montagem — apresentação à Capitania", ""]
    for s in SLIDES:
        nome = slug(s["titulo"])
        arq = f"{s['n']}-{nome}.txt"
        (narr / arq).write_text(s["narracao"].strip() + "\n", encoding="utf-8")
        ordem.append(f"{s['n']}  {s['dur']}  narracao/{arq}")
        ordem.append(f"          captura: {s['arquivo'] or '— (cartela, sem captura)'}")
    (AQUI / "ORDEM.txt").write_text("\n".join(ordem) + "\n", encoding="utf-8")

    faltando = []
    for s in SLIDES:
        if not s["arquivo"]:
            continue
        for a in (s["arquivo"] if isinstance(s["arquivo"], list) else [s["arquivo"]]):
            if not (AQUI.parent / "capturas-apresentacao" / a).exists():
                faltando.append(a)
    print(f"deck.html          {len(SLIDES)} slides")
    print(f"narracao/          {len(SLIDES)} arquivos de texto")
    print("ORDEM.txt          ordem de montagem")
    if faltando:
        print(f"\ncapturas pendentes ({len(faltando)}):")
        for f in faltando:
            print(f"   {f}")


if __name__ == "__main__":
    main()
