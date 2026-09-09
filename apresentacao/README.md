# Apresentação à Capitania — emissão da CHA-MTA-E

Vinte slides narrados por voz sintética, sobre o processo de emissão no balcão.
Roteiro completo (o que capturar, o que aparece escrito, o que a voz diz, tabela
de pronúncia das siglas) está no artifact do roteiro; aqui fica o que é gerado.

## Como montar, na ordem

```bash
# 1. persona fictícia no ambiente dev + rearme da demonstração
./seed-apresentacao.sh                      # na raiz do repo

# 2. capturar as telas (abre o navegador já logado, 1920×1080 @2x)
cd frontend/jetski-backoffice
node e2e/capturas/capturar.mjs              # tudo, ou "capturar.mjs 13 17"
#    → PNGs em capturas-apresentacao/

# 3. gerar o deck e os textos da narração
cd ../../apresentacao
python3 montar.py
```

`montar.py` é a única fonte de verdade do texto: edite a lista `SLIDES` nele e
rode de novo. Ele escreve:

| arquivo | o que é |
|---|---|
| `deck.html` | os 20 slides, 16:9. `←` `→` navega, `n` mostra a narração do slide, `f` tela cheia, `deck.html#13` abre direto num slide |
| `narracao/NN-*.txt` | um arquivo por slide, com as siglas já trocadas para a voz sintética ("guia" em vez de GRU, "cinco-bê-um" em vez de 5-B-1) |
| `ORDEM.txt` | ordem de montagem: slide, duração, arquivo de narração, arquivo de captura |

## Sobre o deck

Os slides apontam para `../capturas-apresentacao/*.png` — não há cópia. Enquanto
um PNG não existir, o slide mostra o lugar reservado com o nome do arquivo que
falta e o comando que o gera, então dá para revisar o deck inteiro antes de ter
uma única tela capturada.

Sete slides não têm captura nenhuma (capa, os três eixos, o objetivo e o fecho):
são desenhados no próprio deck.

**PDF para o pendrive:** abra `deck.html`, imprima para PDF em paisagem, sem
margens e sem cabeçalho/rodapé do navegador. Cada slide sai como uma página de
1920×1080. Sala de reunião de Capitania nem sempre tem internet.

## Narração

Um arquivo `.txt` = um áudio. Gere em português do Brasil, ritmo levemente abaixo
do padrão, e **teste primeiro um trecho com siglas** — é onde toda voz sintética
falha. Os parágrafos separados por linha em branco pedem meio segundo de silêncio
entre eles: sem essa pausa, os três eixos da abertura viram um bloco só.

O slide 20 encerra a apresentação. O pedido à Marinha — formato do ofício, canal
de recebimento, consulta pela fiscalização — é conversa ao vivo na reunião, não
texto de voz sintética.
