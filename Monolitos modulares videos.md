0:00
É praticamente impossível hoje ler um
0:02
artigo de programação e não se deparar
0:04
com o conceito de microsserviços. Eles
0:07
estão em todos os lugares, conferências,
0:09
artigos, vagas de emprego. E hoje eu vou
0:13
defender um ponto polêmico.
0:14
Provavelmente você não precisa de mais
0:16
um microsserviço. Olá pessoal, tudo bem?
0:19
Mais um assunto polêmico aqui pra nossa
0:21
pauta, mas ainda focado em system, que
0:23
foi o tópico que vocês mais pediram na
0:25
minha última pergunta.
0:26
E hoje eu quero trazer alguns prós e
0:29
contras da arquitetura de microsserviço.
0:31
Eu acho que ela é uma arquitetura muito
0:33
válida dentro do nosso system design,
0:35
mas nós temos algumas alternativas que
0:37
às vezes fazem mais sentido quando o
0:39
nosso escopo ou o nosso domínio ele é
0:42
bem reduzido. Nesse vídeo eu não vou
0:45
entrar muito no detalhe do conceito de
0:47
microsserviços, né? Eu acho que nós
0:49
temos ali uma evolução que aconteceu nos
0:51
anos 2010 paraa frente, né? dos
0:54
monolitos, perdendo o espaço para
0:57
arquiteturas mais distribuídas ou
0:59
pedaços menores dentro da arquitetura.
1:01
Tem o artigo do Martin Faller que
1:04
explica bem o conceito de
1:05
microsserviços, como que surgiu. É um
1:07
artigo que foi escrito exatamente
1:09
naquela época, né, no quando estava no
1:12
topo da hype ali dos microsserviços. Eu
1:14
acho que vale a pena, quem não conhece o
1:17
conceito de microsserviços, esse artigo
1:19
ele explica bem as características, a
1:21
componentização, os principais
1:23
diferenças. Eu acho que ele é um bom
1:25
ponto de partida pro que eu vou falar.
1:27
Então, depois que vocês terminarem o
1:28
vídeo, né, quem não conhece esse artigo,
1:29
vem aqui e dá uma olhada. E o que eu
1:31
quero trazer hoje é uma percepção que eu
1:34
tenho tido ao longo dos últimos anos,
1:36
construindo e projetando o software e
1:39
principalmente os desafios que eu tenho
1:41
encontrado quando a gente parte logo de
1:43
início para uma arquitetura de
1:44
microsserviços. Mas antes, né, de irmos
1:47
pros prós e contras, vamos entender o
1:49
cenário que nós temos hoje. Segundo
1:51
estimativas que nós temos, né, de
1:52
venture capitals, blogs, etc.,
1:55
Estima-se que de 35 a 40% dos
1:59
funcionários das empresas de tecnologia
2:02
sejam efetivamente programadores ou
2:04
engenheiros de software. O restante se
2:06
divide em pesquisa, desenvolvimento,
2:10
eh vendas, gestão, administração.
2:13
Então, numa empresa, por exemplo, de
2:15
1000 funcionários, mais ou menos uns
2:17
300, 350 serão programadores. Se nós
2:21
formos pra última pesquisa
2:24
do Stack Overflow,
2:27
nós temos que 60,4%
2:32
das pessoas trabalham em empresas com
2:36
menos de 500 funcionários. Se nós
2:38
usarmos nessa proporção de que de 500
2:40
funcionários 30% seriam programadores,
2:43
então mais da metade das pessoas
2:45
trabalham em empresas que t menos do que
2:48
150 programadores, ou seja, vão estar
2:50
ali na faixa de entre 50, 100, 20.
2:54
Então, a maioria das pessoas hoje
2:55
trabalham em empresas que tem uma
2:57
quantidade de programadores menor do que
3:00
100, o que já mostra uma realidade que é
3:02
até parecido com os bordões que eu tenho
3:04
visto em conferências, que sempre os
3:06
palestrantes começam dizendo: "Você não
3:08
é Netflix", né? né? Então não pense em
3:09
montar uma arquitetura gigante como
3:11
Netflix, porque provavelmente o seu caso
3:13
de uso ele é um pouco mais reduzido,
3:15
tanto em volume quanto em complexidade.
3:18
Pois bem, supondo que você trabalha numa
3:20
empresa pequena e você construiu uma
3:22
arquitetura de microsserviços, nós temos
3:24
aqui o API Gatorway. Esse IPI Gatorway,
3:26
com base no PEF, ele redireciona para um
3:28
serviço ou para outro. Esses serviços
3:31
fazem uma série de chamadas para outros
3:33
serviços, né, nos seus end points, cada
3:34
um com sua base de dados, cada um com
3:36
sua estrutura e a cada chamada nós temos
3:39
aqui um payload, né, tanto de ida quanto
3:41
de volta. Quais são os problemas dessa
3:45
arquitetura? Na minha opinião primeiro
3:47
nós temos complexidade na estrutura.
3:49
Começa já no IPI Gator.
3:52
Com base na informação do IPI Gator, eu
3:54
vou ter
3:56
um redirecionamento para um serviço ou
3:58
para outro. Então, quando nós vamos
4:00
investigar um problema em produção, eu
4:01
já preciso saber qual foi a rota. Com
4:03
base na rota, eu vou ter que ver qual é
4:05
o serviço. Então, já tem uma
4:06
complexidade, ela é pequena, mas ela
4:08
existe.
4:10
O Pagate faz uma chamada pro serviço e o
4:12
serviço cascateia essas chamadas. Então,
4:14
se nós contarmos aqui, ó, eu posso ter,
4:17
por exemplo, numa cascata de chamadas
4:22
pelo menos quatro ou cinco chamadas
4:25
encadeadas de um serviço para outro.
4:28
Isso tem dois pontos muito importantes
4:30
na minha visão que impactam bastante,
4:32
que é exatamente hoje o meu principal
4:33
ponto de desconforto com arquitetura de
4:36
microsserviços que que é construída de
4:38
forma prematura. Nós temos aqui encoding
4:42
e latência. Então, sempre que eu faço
4:45
uma chamada de um microsserviço para
4:48
outro, provavelmente sendo uma chamada
4:49
restful, mesmo que seja GRPC, né, GRPC,
4:53
eu ainda vou ter o encoding, pagar o
4:55
preço do encoding e pagar o preço da
4:57
latência. Se eu considerar que eu tenho
4:59
uma latência, mesmo seja dentro do mesmo
5:02
cluster, né, sei usando cubernets, por
5:04
exemplo, de 5, 10 msundos, só aqui eu já
5:08
consegui um incremento significativo de
5:10
latência,
5:12
somente por ficar fazendo essa passagem,
5:14
né, de pegar um uma classe ou um byte
5:17
code, converto para Jason,
5:20
passo via networking, recebo do outro
5:23
lado, faço decoding, volto para um
5:26
cenário de bitecode.
5:28
faço a lógica, encoding de novo. Então
5:31
esse vai e volta das chamadas que
5:33
acontece entre os serviços, ele vai
5:35
acumulando latência dentro do fluxo.
5:38
Obviamente é uma latência pequena, mas
5:40
ela não é negligenciável quando você tem
5:42
uma cascata de 10, 20 serviços sendo
5:45
chamados ao mesmo tempo. Meu segundo
5:47
ponto de desconforto, aconteceu um bug
5:50
no meu front end. Eu capturei no front
5:53
end o comportamento inesperado. Para
5:55
investigar isso daqui, eu preciso
5:57
conhecer todos os serviços ou
5:59
praticamente todos os serviços que estão
6:00
na minha estrutura. E aí eu posso usar
6:03
um APM, né? posar um monitoramento ou
6:05
uma telemetria aberta ali do openetry,
6:07
mas ainda assim eu vou ter que entender
6:09
toda a cascata de chamadas para saber
6:11
exatamente qual foi o ponto. Eu começo a
6:13
ter que adicionar complexidade de
6:15
monitoramento. Eu preciso de spen ID,
6:18
trace ID, preciso que todos os meus
6:20
serviços estejam no mesmo APM para
6:22
conseguir concatenar essa informação. A
6:24
investigação, ela passa a depender muito
6:26
do sistema de monitoramento, enquanto
6:28
que no monolito, por exemplo, eh, eu
6:31
tenho o stack trace, que faz todas as
6:33
chamadas, né? E ali eu já tenho a
6:34
sequência. Quando eu distribuo minha
6:37
transação ou distribuo minha chamada, eu
6:40
preciso confiar no meu sistema de
6:42
monitoramento para conseguir transformar
6:45
uma chamada distribuída no único fluxo e
6:48
aí sim compor minha estrutura de mutação
6:51
de dados.
6:52
Então, não é que seja impossível, mas é
6:55
mais complexo, né? É uma complexidade
6:57
que você acaba tendo que pagar quando
6:59
você distribui seu processamento em
7:01
serviços diferentes. Então, no segundo
7:03
ponto aqui é o monitoramento, ele se
7:07
torna um pouco mais complexo. E por fim,
7:09
nós temos aqui segundo ponto que é a
7:12
parte de devops ou principalmente a
7:14
operação disso, né? Quando tem muitos
7:16
serviços, cada um vai ter o seu próprio
7:18
deployment, cada um vai ter sua própria
7:20
infraestrutura. Então, obviamente que
7:22
isso tem um ponto positivo que nós vamos
7:23
ver depois, né? Você consegue escalar de
7:25
forma independente, consegue ter mais
7:26
resiliência, etc. Só que você paga um
7:29
preço de ter que fazer o deployment de
7:31
todos, monitorar todos, acompanhar
7:33
todos. E se uma mudança, por exemplo,
7:36
numa feature, vamos supor aqui que nós
7:37
estamos implementando uma nova feature
7:39
no front end. Se essa feature nova ela
7:42
cascatear em vários serviços,
7:46
eu vou ter que fazer o deployment de
7:47
todos esses serviços para daí sim
7:49
conseguir visualizar o resultado. E o
7:51
que acaba acontecendo também, na maioria
7:53
das vezes, é que quase sempre cada
7:55
serviço é de um time diferente. E eu
7:58
preciso cascatear essa coordenação ao
8:00
longo de todos os times, porque se o
8:03
time que cuida do serviço três faz o
8:05
deployment do seu serviço, mas a feature
8:07
que tá no serviço N, por exemplo, ainda
8:09
não foi, eu posso ter um problema. Então
8:11
eu preciso também coordenar os
8:13
deployments. Isso traz um custo de
8:16
devops. Mas aí você pode estar se
8:17
perguntando, pô, mas por que que todo
8:19
mundo foi para microsserviço, né? Quais
8:21
foram os principais problemas? Eu acho
8:23
que o que justificou a migração do
8:25
monolito pro microsserviço foram pontos
8:28
principalmente de coordenação e
8:29
escalabilidade. O microerviço ele
8:31
permite que você consiga escalar de
8:33
forma independente cada pedaço da
8:35
aplicação. Isso é muito vantajoso. Nós
8:37
vimos, né, no saga que ter, por exemplo,
8:40
um CQRS permite essa escalabilidade
8:42
independente. Isso é sensacional. E além
8:45
disso, quando eu tenho um time grande,
8:47
né, passa de 30, 40 desenvolvedores no
8:50
mesmo software, no mesmo codebase, eu
8:54
passo a ter problemas de coordenação. Eu
8:56
posso ter, começo a ter muito conflito
8:58
de merge, o deployment fica truncado.
9:00
Então, quando o meu serviço escala,
9:03
tanto em volume de acessos quanto em
9:06
número de pessoas, invariavelmente fica
9:09
muito difícil de manter. E aí que surge,
9:11
na minha visão, o meio termo entre o
9:14
monolito e o microsserviço, que é o
9:16
conceito de monolitos modulares ou
9:20
modular monolite. Nesse caso, nós temos
9:24
o deployment único, ou seja, nós temos
9:27
aqui um único deployment, esse monolito.
9:30
Todas as chamadas entre a os
9:33
componentes, elas acontecem dentro da do
9:35
mesmo binário, o que faz com que nós não
9:39
temos que nos preocupar tanto com o
9:40
monitoramento, porque é tudo dentro do
9:41
mesmo stack trace. Eu não tenho que me
9:44
preocupar tanto com spen ID, Turce ID e
9:46
ficar olhando as chamadas entre um
9:47
serviço. Eu não pago latência, só que eu
9:50
ainda tenho módulos independentes, o que
9:53
facilita tanto o desenvolvimento
9:55
independente dos times, quanto a
9:58
garantia de que eu vou ter uma redução
10:01
nos conflitos, tanto em PQU quanto em
10:03
merges. Novamente, eu não vou entrar no
10:06
detalhe do que é o monolito modular, tem
10:08
muitos artigos bons, né? Dei uma
10:10
procurada. Eh, até tem uns um vários
10:13
artigos legais do próprio pessoal do
10:15
Google, né? Então, High Tower aqui ele é
10:19
um um dev advocate do Google, grande
10:23
precursor do conceito de microsserviços,
10:26
né? defende bastante deployment de cinem
10:28
cubernets, mas ele traz um ponto bem
10:30
interessante aqui, principalmente
10:32
contrapondo, né, essa necessidade de
10:34
logo de cara você ir para microsserviço.
10:37
Às vezes o seu problema ele é muito mais
10:39
focado em coordenação e conflitos entre
10:42
times que podem ser facilmente
10:44
resolvidos ajustando simplesmente os
10:47
módulos dentro do monolito. Você ainda
10:49
pode usar todos os benefícios de um
10:51
Cubernets, né? Você pode ter blue green
10:54
deployments, pode ter rolling releases
10:56
dentro de monolitos. Você consegue fazer
10:58
um deployment faseado, sequenciado
11:00
dentro do monolito, sem precisar pagar,
11:03
né, as penalidades de latência,
11:05
coordenação, encoding, decoding e
11:08
observabilidade que você tem numa
11:10
arquitetura de microsserviços.
11:13
Então, quando eu vou para um
11:15
monolito modular, o que acaba
11:17
acontecendo é o conceito que a gente
11:19
chama de vertical slices, né? Então são
11:22
dois conceitos diferentes. Acho que é
11:24
importante deixar claro isso, né? O
11:26
monolito modular, ele simplesmente diz
11:28
que eu vou ter um o único monolito, um
11:30
único binário, e dentro dele eu vou ter
11:32
módulos muito bem definidos. A as
11:35
interfaces entre esses módulos são
11:36
interfaces públicas. É uma mudança de
11:39
paradigma bem grande, principalmente
11:40
para quem codifica, por exemplo, com o
11:42
Spring, que tá acostumado a colocar uma
11:44
série de interfaces, tudo com métodos
11:45
métodos públicos. Agora, há uma
11:48
preocupação maior entre as interfaces
11:51
dos módulos. Então, se nós fôssemos
11:53
reescrever, né,
11:56
essa arquitetura, nós teríamos que ter
11:58
uma preocupação maior em definir nossos
12:02
módulos. Podemos pensar
12:05
nas nossas APIs
12:07
como sendo, né, o que antes no
12:10
microsserviço era um controller, uma API
12:13
Restf, agora passa a ser uma interface
12:16
mesmo de minha linguagem de programação.
12:18
Seja, por exemplo, o interface em Go ou
12:19
a interface em Java.net, Net, eu vou ter
12:22
a interface como sendo minha API
12:25
pública, que eu estou expondo pros
12:27
outros módulos e tudo o resto fica como
12:30
métodos ou package protected ou privados
12:34
mesmo, né? Então, acho que no na
12:36
arquitetura de monolitos modulares a
12:38
gente passa a utilizar muito mais os
12:41
nossos eh controles de acesso de pacote,
12:44
né? meu package, que é aquele, por
12:46
exemplo, em Java, quando eu não coloco
12:47
nenhum, ou coloco protected. Nesse caso,
12:50
somente o que tá dentro do meu pacote,
12:52
do meu módulo, vai ter acesso àela
12:54
funcionalidade. E o que eu quero expor
12:56
para fora, o que antes era minha API
12:59
Restf, passa a ser minha interface. A
13:02
grande vantagem disso é que eu vou ter
13:04
agora em tempo de compilação problemas
13:06
de contrato, o que antes eu precisava
13:08
ter um teste de contrato para o
13:10
versionamento de API. Agora fica muito
13:12
mais simples porque eu tenho minha
13:13
interface mesmo definida. dentro do meu
13:15
código. Então, qualquer problema de
13:17
versionamento eu vou pegar em tempo de
13:18
compilação, na hora de fazer o build e
13:21
eu consigo definir esses contratos intra
13:24
serviços usando interfaces de
13:27
programação. Então essa é a arquitetura,
13:29
né, de modo resumido, é a arquitetura de
13:32
monolitos modulares. Pesquisem depois,
13:35
eu acho que vale muito a pena. Se vocês
13:37
quiserem, eu posso fazer um vídeo
13:38
dedicado a isso de monolitos modulares e
13:40
vertical slices. Mas o que eu queria
13:42
trazer é só mostrar o conceito
13:44
contraponto e trazer os prós e contras.
13:46
Então os PR que eu vejo do monolito
13:48
modular são, eu tenho menor latência no
13:52
geral, porque eu não tenho encoding de
13:54
coding não pago latência de networking
13:56
ali, mesmo que seja entra cluster. Eu
13:58
tenho maior resiliência nos contratos,
14:00
como eu comentei agora, todos os meus
14:02
contratos eles estão em tempo de
14:04
compilação porque são chamadas de
14:06
métodos, né, métodos de linguagem de
14:08
programação, não são mais chamadas eh
14:12
via API. Eu tenho uma menor complexidade
14:14
gerencial.
14:16
Eu sei exatamente que eu tenho um único
14:18
deployment, um único serviço.
14:21
E meu monitoramento ele passa a ser mais
14:23
simples, porque eu não preciso me
14:24
confiar em trace ID, spin ID e no meu
14:27
APM para conseguir olhar a cascata de
14:29
chamadas. Agora com stack trace eu já
14:31
tenho toda a minha chamada, todos os
14:33
meus problemas. Obviamente, né, que nós
14:35
temos alguns contras. Eh, quando eu
14:37
tenho um monolito, eu perco a minha
14:39
independência no calendário de
14:41
deployment. até trocar a cor aqui da
14:43
setinha. Então, quando eu estou num
14:45
microsserviço, eu consigo fazer o
14:47
deployment independente. Então, isso é
14:49
muito bom quando você tem muitos times.
14:52
Eh, no monolito, você não consegue fazer
14:54
o deployment só de um pedaço, né? Você
14:56
perde o o sentido de seu monolito. Por
14:59
isso até que eu disse que logo no começo
15:01
que faz sentido para times pequenos,
15:03
então times de menos de 50 pessoas, é o
15:07
cenário perfeito pro monolito modular.
15:09
Eu perco a possibilidade de escalar de
15:13
forma independente. Como eu disse, eh,
15:16
um dos grandes trunfos dos
15:18
microsserviços é você conseguir fazer
15:20
escalabilidade de fluxos mais intensos
15:22
de forma independente. No monolito
15:24
modular, você ainda consegue escalar de
15:26
forma independente, porque você pode
15:27
ativar um pedaço ou outro, mas aí eu
15:29
acho que você começa a trazer
15:31
complexidade, né? E e o que nós queremos
15:33
que o monolito modular exatamente fugir
15:35
disso. E você tem um esforço maior para
15:38
identificar gargalos de performance,
15:40
porque no APM, né, no New Relic,
15:43
Dynamics, Opeletry, Grafana, etc., o
15:46
gargalo de performance fica muito
15:48
evidente, porque eu vou ver exatamente
15:49
qual chamada demorou mais para retornar.
15:52
Aqui, como tudo acontece dentro do mesmo
15:54
binário, eu não vou conseguir olhar a
15:55
latência para identificar o gargala. Eu
15:57
vou precisar colocar provavelmente um
15:59
profiler na minha aplicação para ver
16:01
qual foi o método que demorou mais. É
16:03
possível, mas é um pouquinho mais
16:05
complexo de se fazer. Eu vou ter que
16:06
fazer um profile na minha aplicação, o
16:08
que antes era dado por por padrão ali no
16:11
APM. Então, nós temos prós e contras nos
16:14
monolitos modulares comparativamente aos
16:16
microsserviços, mas eu acho que é mais
16:18
uma ferramenta dentro ali da caixa de
16:20
ferramentas do desenvolvedor. Se você tá
16:23
fazendo algo que é relativamente
16:24
simples, o domínio é simples, a
16:26
quantidade de pessoas que vão encostar
16:28
ali naquele software é uma quantidade
16:29
reduzida, às vezes acaba fazendo sentido
16:32
você ir para um contexto de monolitos
16:34
modulares e no futuro, se você precisar,
16:37
por exemplo, escalar um módulo
16:39
específico, você consegue fatiar aquele
16:42
módulo e exportar ele no serviço
16:44
independente e gerar, por exemplo, um
16:46
microsserviço com base naqueles módulos,
16:49
né? Então, acho que a mensagem desse
16:51
vídeo é, você não precisa partir de um
16:54
monolito diretamente pro microsserviço.
16:56
Você consegue pegar o seu monolito,
16:58
modularizar aquele monolito, ou seja, já
17:01
vai ter os ganhos de orquestração e
17:03
gerenciamento dos times, sem precisar
17:06
pagar as penalidades de latência,
17:08
complicidade e observabilidade. E se no
17:11
futuro você precisar, você consegue
17:13
ainda extrair aquele módulo para o
17:14
microsserviço. Então, o monolito modular
17:17
ele não é oposto ao microsserviço, ele é
17:19
o meio do caminho. Você pode caminhar um
17:21
pouco, talvez essa caminhada de um
17:24
monolito para um monolito modular já
17:26
resolva o seu problema e com muito menos
17:28
complexidade. Às vezes, né, ele vai ser
17:31
um um só uma etapa, um camp e no futuro
17:35
você efetivamente vá para
17:36
microsserviços, mas você está indo para
17:39
microsserviços porque você realmente
17:40
precisa, não porque você tá indo pela
17:42
hype. Então é isso, boa sorte na sua
17:44
arquitetura. E até mais.

Você disse:
0:00
Não é novidade que microsserviços estão
0:01
em queda e que monolitos modulares estão
0:03
extremamente em alta. Mas tem uma coisa
0:05
curiosa que eu sempre vejo. Sempre que
0:07
alguém fala de monolito modular, aparece
0:09
aquela mesma imagem de um monte de bloco
0:11
conectado ao banco de dados dentro de um
0:13
único código. Só que isso não é verdade.
0:14
A verdade é que monolitos modulares
0:16
podem escalar tanto quanto
0:18
microsserviços se forem bem
0:19
estruturados. E nesse vídeo vou te
0:21
mostrar os 10 princípios para escalar
0:23
arquiteturas modulares e como estruturar
0:25
um monolito modular que escala
0:26
praticamente de forma infinita. Bom, eu
0:28
sou Valdemar Neto, cofundador da Techs
0:30
Clube. Já trabalhei em empresas como
0:32
Atlácia e Totorks e há mais de 6 anos
0:34
venho pesquisando e aplicando
0:35
arquiteturas modulares e sistemas de
0:36
grande porte. Então se tu gostar desse
0:39
vídeo aqui, comenta e curte, porque me
0:41
ajuda muito a crescer, conteúdo mais
0:43
avançado e mais nichado, o YouTube não
0:45
entrega tanto. E muito desse conteúdo
0:46
faz parte do livro que eu venho
0:48
trabalhando, que é os 10 princípios da
0:49
arquitetura modular, que eu vou falar um
0:51
pouco para vocês aqui. Bom, pra gente
O que é um módulo
0:52
começar, a gente tem que entender o que
0:53
que é um módulo. Um módulo, pensem no
0:54
módulo como uma forma de agrupar e
0:56
escolher o que que tu quer expor. Então,
0:58
a gente pode pensar no módulo como uma
0:59
classe, a gente pode pensar em um módulo
1:02
como namespace, algo que agrupe em
1:04
outras classes ou uma forma lógica de
1:06
agrupamento que tu escolhe o que que vai
1:08
ser exposto para fora. Outra forma de
1:10
pensar também é uma parte do sistema que
1:12
a gente vai falar muito aqui, uma parte
1:14
do sistema pode ser feito deploy ou pode
1:16
ser compilada sozinho. Então, pensem no
1:18
módulo dessa forma. E o que que é o
1:20
monolito? Vamos pensar de forma bem
1:21
simples. Faz deploy junto, roda no mesmo
1:23
processo. É um monolito. Código é
1:25
desenvolvido, faz deploy, roda junto no
O que é um monólito
1:28
mesmo processo. A gente vai tratar isso
1:30
como monolito aqui. Bom, mas que a gente
1:32
entendeu o que que são monolitos e
Por que os monólitos modulares voltaram
1:33
módulos, por que que monolitos modulares
1:35
voltaram com tanta força agora se isso
1:37
não é uma novidade? A gente tem
1:38
monolitos desde sempre. Bom, tem dois
1:40
motivos principais que eu descobri.
1:42
Primeiro é a virtualização. Hoje a gente
1:44
consegue rodar qualquer parte do código
1:46
isolada em contêiners. E segundo,
1:48
aprendizado que a gente teve com
1:49
microsserviços nas últimas décadas. A
1:50
gente aprendeu que separar o código traz
1:53
benefícios como um design e coesão
1:55
melhor, código mais modular, é mais
1:57
fácil de manter, mas a separação física
2:00
tem custos altíssimos, operação mais
2:02
cara, carga cognitiva e também a gestão
2:05
que é muito mais complexa. Ou seja, a
2:07
modularização sempre foi boa. O que
2:09
mudou é que agora a gente consegue
2:11
colher os benefícios sem o custo dos
2:13
microsserviços, que é o que eu vou
O que mudou: virtualização e aprendizado com microservices
2:14
mostrar aqui na prática. Isso não é
2:16
novidade, a gente tem várias empresas
2:17
grandes como Shopify, Google, Ty Seven
2:19
Signals e o Basic Camp do DH que é o
2:21
criador do Ruben Rails, são famosos por
2:23
rodar em monolitos. Só que antes ficavam
2:25
nessas empresas maiores porque não tinha
2:26
tanto ferramental. Hoje em dia a gente
2:28
tem muito ferramental, qualquer um pode
2:30
construir uma arquitetura modular que
2:32
escala bastante. Bom, para exemplificar,
2:34
eu vou usar uma aplicação minha que eu
2:35
desenvolvi pro meu curso na Tech Leads
2:37
Club. Ele é um sistema de streaming
2:39
similar a Netflix. A gente vai ver como
2:41
mesmo como o monolito dá para escalar de
2:43
forma simples e também o que fazer
2:45
quando chegar no limite de um monolito.
Empresas que escalam com monólitos (Shopify, Google, Basecamp)
2:47
Bom, o que eu vou mostrar para vocês é o
2:48
monolito similar a essa esse desenho
2:50
aqui. Então eu tenho o monolito modular,
2:52
dentro dele vai ter os meus contextos
2:55
delimitados, que é muito importante na
2:56
arquitetura modular, tu separar ele por
2:58
contextos delimitados. Então tem
2:59
billing, streaming, identity e a minha
3:02
infra compartilhada. Então são partes
3:04
separadas do meu domínio, que são
3:05
módulos dentro do meu monolito modular.
3:07
Então vou mostrar aqui, vocês vão
3:08
entender melhor. Bom, aqui no código eu
3:09
tenho a mesma estrutura que eu mostrei
3:10
no Scroll Draw para vocês. Então eu
3:12
tenho meus módulos aqui. É uma aplicação
3:14
Nest no Node JS, mas é similar em
3:16
qualquer linguagem. Se vocês usarem Java
3:18
com com modulit ou qualquer outra
3:20
aplicação é bem similar. Tem meus
3:21
módulos, eles estão separados aqui.
3:24
Então tem o billing quity identity e
3:25
isso é um monolito modular. Por quê? No
3:27
meu main eu carrego meu todos os meus
3:29
módulos aqui, ó. Vocês estão vendo
3:31
contentity billing. Tô carregando todos
Exemplo prático – Arquitetura modular de streaming
3:32
os meus módulos aqui. Então o meu, a
3:35
minha aplicação, ela é monolítica. Quer
3:37
dizer que eu não consigo escalar uma
3:38
aplicação assim de forma independente?
3:41
Consigo, na verdade. Não, não quer dizer
3:43
que eu não consiga. Ó, tá vendo que eu
3:44
tenho vídeo processor worker? Aqui eu
3:46
tenho outra outro main na minha
3:49
aplicação, outro arquivo de bootstrap
3:51
que eu chamo, que chama só o content
3:54
processor module. Isso aqui é um
3:56
submódulo de content, ó. Então, conta de
3:58
toda a minha parte de conteúdo na minha
4:00
a minha aplicação modular aqui no meu
4:02
monumento modular. Então, eu tenho o meu
4:03
módulo de biling, parte mais de
4:05
cobrança, content. Eu quebrei ele em
4:06
três partes, em três submódulos e um
4:08
deles é o vídeo processor, que trata só
4:10
de processar vídeo. Então, esse daqui,
4:12
se eu for ver aqui, eu tenho meu vídeo
4:14
processor main, que eu carrego só ele de
4:16
forma independente. Então, é possível,
4:17
eu faço build de uma imagem docker só
4:19
para ele. Então, quer dizer que o
4:21
monolito modular não escala? Não, o
4:23
modulento modular puramente dessa forma
4:25
ele já escala, mas ele tem um limite,
4:27
obviamente vocês estão vendo e vocês vão
4:28
pensar, pô, mas esa aí se tiver várias
4:30
pessoas trabalhando nesse time aqui,
4:32
mesmo de forma modular, eles como como
4:34
que eu faço só pro biling escalar ou
4:36
como que eu boto 100, 200 pessoas
4:38
trabalhando nesse code base de uma forma
4:40
que um não impacte o outro? Agora a
Como escalar partes do monólito de forma independente
4:42
gente vai entrar nos limites do monolito
4:44
modular e por que ninguém fala sobre a
4:46
arquitetura modular, que é realmente
4:47
onde tá a grande escala. Então, a grande
4:49
diferença de arquitetura modular para
4:50
monolitos modulares é que numa
4:52
arquitetura modular tu pode ter vários
4:53
monolitos, várias maneiras de agrupar
4:55
módulos. Se antes aqui a gente tinha o
4:58
monolito com todos os módulos dentro,
5:00
agora a gente tem infinitos números de
5:03
possibilidades de combinação de apps,
5:05
coisa que não é possível fazer com
5:06
microsserviços. Por quê? Porque eles
5:08
estão em codases diferentes. Eu vou
5:09
explicar um pouco disso. Então, imagina
5:11
que a app ela é uma maneira de agrupar
5:13
módulos. Então eu tenho o meu módulo
5:15
identity, tem o meu módulo streaming
5:17
numa app, ela ele usa shared infra aqui,
5:20
ou seja, loging e eu tenho meu billing
5:23
em outra app. Vou mostrar na prática
5:24
para vocês entenderem. Bom, aqui eu
5:26
tenho outra versão do meu código agora
5:28
com o mono usando o NX. Não se preocupa
5:30
em entender muito isso, eu vou explicar,
5:31
mas a grande diferença aqui é que agora
5:33
eu tenho apps e eu tenho packages, tá?
5:36
Apps são somente a maneira de agrupar
5:39
módulos. Pensa aqui, eu tenho minha app
5:40
monolito. Se eu entrar em monolito
5:42
module, eu só tô carregando o meu módulo
Limites do monólito modular e transição para arquitetura modular
5:44
de content e o meu módulo de identing.
5:51
Então pensem que se dois times aqui, um
5:53
time mantém um monolito, outro mantém
5:54
sua parte de billing, cada um vai est
5:56
trabalhando de forma independente. Mas
5:57
os módulos eles ficam isolados aqui, ó.
6:00
Ou seja, tu pode compor eles em
6:02
infinitos tipos de app. Ah, eu quero
6:04
rodar o content junto com a bilinha. Tu
6:07
vai vir aqui no biling AP modul e vai
6:10
importar o content aqui, né? Content
6:12
module e ele vai funcionar. Então isso
6:15
que é a grande diferença de arquiteturas
6:16
modulares. E agora a gente vai ver os
6:18
princípios para vocês entenderem como
Apps vs Packages no Nx e composição de módulos
6:20
construir essas aplicações aqui. E eu
6:22
vou mostrar exemplo prático. Bom, se tu
6:23
vê até aqui, provavelmente tu te
6:24
interessa por monolitos modulares,
6:26
arquiteturas modulares e também
6:27
arquiteturas evolutivas, que é a maneira
6:29
saudável de evoluir arquitetura. Se tu
6:31
quer aprender mais sobre isso, eu tenho
6:32
um curso dentro da Techlads Club que eu
6:33
vou deixar o link aqui, que é o curso
6:35
aplicações Enterprise. Eu falo tudo
6:36
disso na prática, esse código que eu tô
6:38
mostrando de lá. Dá uma olhada aqui e
6:39
vamos voltar paraa prática. Bom, os 10
6:41
princípios que fazem parte do meu livro
6:43
são esses aqui. Primeiro são limites bem
6:45
definidos, ou seja, cada módulo deve ter
6:47
um limite claro. Ele deve isolar o que
6:50
pertence a ele e não expor coisas
6:53
internas. Como que a gente faz isso na
6:55
prática? Vou te mostrar. Bom, se a gente
6:57
vier aqui, por exemplo, no modo de
Princípio 1 – Limites bem definidos
6:59
identity e o identity mod, vocês vão ver
7:01
que ele, mesmo vocês não entendem no
7:03
Nest, vocês é só olhem para isso aqui,
7:05
ó. É um módulo que ele importa outros
7:07
módulos. Ele tem providers, que são
7:09
classicas que são usadas internamente e
7:11
ele não expõe nada, ou seja, quem
7:12
importar ele não vai pegar nada. Ou
7:14
seja, ele encapsula tudo que pertence a
7:17
ele. Isso que é ter bordas bem
7:19
definidas. Ele encapsula tudo que
7:20
pertence a ele e ele pertence a um
7:22
domínio. Então, do main Dream and Design
7:24
estratégico, saber modelar bem o negócio
7:27
para módulos. E assim, pessoal, isso
7:29
aqui não são módulos de feature, são
7:30
módulos de domínio. Aqui dentro já vai
7:33
ter autorização de dedicação, vai ter
7:34
usuários. Eles são módulos maiores.
7:37
Sempre que tiver começando o sistema
7:38
modular, na dúvida, façam módulos bem
7:40
grandes, deixem aparecer os agrupamentos
7:43
internos, a coesão começar a aparecer,
Princípio 2 – Componibilidade
7:45
aí sim começa a criar módulos menores. O
7:48
segundo princípio é componibilidade, é
7:50
habilidade de módulos poderem ser
7:52
compostos emoss. Lembra que eu mostrei
7:53
para vocês aqui, ó? Aqui eu tenho meu
7:55
módulo de billing, eu tenho ident
7:58
streaming, que eles estão compostos em
7:59
uma app. Para o módulo poder ser
8:00
composto, ele não pode depender de
8:02
outras coisas. Ele pode ter que ser
8:03
isolado, não depender diretamente de
8:05
outros módulos e ele pode ser facilmente
8:07
composto com outros módulos. Um exemplo
8:09
prático disso é o nosso monolito, né? Se
8:11
a gente olhar o nosso monolito, monolito
8:12
módul, ele tá carregando contentó, ou
8:15
seja, são módulos que estão sendo
8:16
compostos. Se a gente abrir esses
8:17
módulos, eles importam outros módulos
8:20
só, mas eles não têm nenhuma dependência
Princípio 3 – Independência
8:22
direta de outros módulos, o que torna
8:23
eles fáceis para compor. O terceiro
8:25
princípio é independência. Os módulos
8:27
devem ser totalmente independentes em
8:28
infraestrutura, em testes, em tudo que
8:31
precisar. Ele deve ser rodado
8:32
completamente isolado. Pense assim, o
8:34
módulo deve ser uma coisa que eu só pego
8:37
ele e movo para outro lugar. Se eu
8:39
quiser mover ele para outro repositório,
8:41
é só pegar e mover. Para isso, ele tem
8:42
que ter um design específico que
8:44
contenha todas as coisas dele, incluindo
8:46
testes. Aqui nesse exemplo, a gente tem
8:49
o módulo de identity, por exemplo, ó.
8:51
Dentro de teste, eu tenho meus testes
8:52
end to end na raiz. E dentro dele, se eu
8:54
entrar dentro de service, eu também
8:55
tenho meus testes de unidade, ou seja,
8:56
ele tem tudo que precisa aqui. Além
8:58
disso, ele tem dentro da persistência
9:01
dele, tem as migrations dele, a própria
9:03
conexão com o banco, ou seja, ele lida
9:05
com tudo que ele precisa para rodar
9:07
totalmente de forma isolada. Ou seja,
9:09
ele pode ser só composto em uma app e
9:11
ele vai levar as coisas dele pr aquela
9:13
app lá e vai rodar de forma isolada. O
Princípio 4 – Isolamento de estado
9:15
quarto princípio é o isolamento de
9:17
estado. Aqui é sobre cada módulo tem a
9:20
sua própria conexão com o banco, roda
9:22
suas próprias migrations, só vê as
9:24
próprias tabelas. Idealmente em sistemas
9:26
maiores tem o próprio banco de dados e
9:29
também se tiver conexão com reds,
9:31
conexão com filas, tudo é gerenciado,
Princípio 5 – Comunicação explícita
9:34
isolado a nível de módulo. Quinto é
9:36
extremamente importante também, que é a
9:38
comunicação explícita. O quinto é
9:40
extremamente importante, que é
9:41
comunicação explícita. Um módulo não
9:43
deve chamar diretamente o outro, que
9:45
diretamente o que eu tô dizendo é tu
9:46
expor o service de um módulo que o outro
9:48
chama. Não, ele deve chamar através de
9:51
uma API. Ou tu chama através de uma PI
9:52
rest, por exemplo, pro local host, ou tu
9:55
expõe uma façade em um módulo e no outro
9:58
tu chama através de uma interface. Vou
10:00
te mostrar um exemplo aqui. Nesse
10:01
exemplo, o meu módulo de identity, antes
10:04
de fazer, antes do usuário logar, ele
10:06
checa se o usuário tá com uma
10:07
subscription ativa. Para ele fazer isso,
10:09
ele tem que bater no módulo de billing.
10:11
Em vez de fazer uma chamada direta, aqui
10:12
eu tenho uma interface, tá vendo? E
10:14
nessa interface, o que que eu faço? Eu
10:16
injeto nela o que eu quero usar. No meu
10:19
caso aqui, eu tô injetando uns HTTP
10:21
client, ou seja, faz uma chamada HTTP
10:23
para outro módulo, mas eu também poderia
10:25
injetar uma classe façade de outro
10:28
módulo. Pesquise sobre o o pattern de
10:31
façade, né? Então, o que que eu faço? Se
10:33
eu quisesse injetar uma classe, em vez
10:34
de expor um serviço direto, eu exponho
10:37
uma fachada, que é basicamente um método
10:41
que chama internamente um serviço do meu
10:44
módulo de billing. Dessa maneira eu
10:45
também poderia fazer uma chamada direta.
10:48
que são as duas formas mais comuns de
10:49
ter baixo acoplamento entre chamada de
10:51
módulos e assim a gente tá dentro do
Princípio 6 – Substituibilidade
10:54
cinco que é comunicação explícita. E
10:56
agora a gente vai pro seis que é
10:57
substituibilidade. É um nome em
10:59
português é horrível. Em inglês é
11:01
existe, mas em português é horrível.
11:03
Desculpem, foi o nome que eu dei. Mas é
11:05
um módulo que pode ser substituído
11:07
dentro de uma app sem afetar o resto da
11:09
app. Ou seja, o módulo ele pode ser
11:11
removido daquela app sem impactar em
11:13
nada. Basta uma mudança de configuração.
11:15
Ele tem que ser configurado dessa
Princípio 7 – Deploy independente
11:16
maneira. O sétimo é deploy independente.
11:19
O módulo em si, ele não faz deploy, ele
11:21
pertence a uma app. Mas o que que eu
11:22
quero dizer é que a app ela pode ser
11:26
feita deploy de forma independente. Os
11:27
módulos que estão dentro dela são
11:29
configurados para serem feitos deploy de
11:32
forma independente. Eles estão
11:33
preparados para isso. Eles não sabem do
11:35
ambiente que eles estão. Eles não sabem
11:36
da app. Ele só tem a configuração deles
11:39
que tá pronta para rodar de forma
11:40
independente. E o oito é escala
Princípio 8 – Escala independente
11:42
independente. Ou seja, um módulo ele tem
11:44
que ser feito de forma que ele escale
11:46
totalmente independente, que ele não
11:48
dependa de outros módulos, que ele não
11:49
depende do ambiente que ele tá, ele tem
11:51
que tá, ele tem que ter a configuração
11:53
dele para conseguir escalar. Isso inclui
11:55
o banco de dados, inclui os serviços que
11:57
ele depende, toda a configuração fica
Princípio 9 – Monitoramento e observabilidade
11:59
nele, nada fica externo. O nove,
12:01
monitoramento e observabilidade. Cada
12:03
módulo deve ter seu próprio setup de
12:05
monitoramento observabilidade. Isso é
12:07
importante porque quando tem vários
12:09
módulos numa mesma app e times
12:11
diferentes são donos desses módulos e a
12:13
gente tem um call, esse tipo de coisa, é
12:15
muito importante que os times certos
12:17
recebam alerta e também possam ver as
Princípio 10 – Falhas isoladas
12:19
métricas dos próprios módulos. E o 10,
12:21
extremamente importante, são falhas
12:23
isoladas. Cada módulo deve isolar suas
12:25
falhas, ter circuit breakers, ter boas
12:28
práticas de shutdown para não impactar
12:30
outros módulos dentro da mesma app.
12:32
Agora, pra gente finalizar, olhem aqui.
12:34
Eu coloquei exemplos aqui para vocês
12:37
entenderem os limites de cada um. Qual
12:39
que é o limite de microsserviços?
12:40
Microsserviços não compõe. Tu não
12:42
consegue botar vários microsserviços
12:43
dentro de uma mesma app. Então essa é
12:44
uma limitação de microsserviços. Se a
12:46
gente parar parar para pensar sobre
12:48
esses 10 princípios. Qual que são as
12:49
limitações de manitos modulares? Agora
12:52
para e pense deploy independente é
12:53
difícil, né? Vai ter que fazer um
12:55
ferramental para isso. Escala
12:56
independente também é difícil. Pensa que
12:58
tá tudo no mesmo code base, tá tudo no
13:00
mesmo processo rodando. Mesmo que tu
13:02
separe alguma coisa, é difícil. Tu vai
13:04
ter que escrever muito script para
13:05
aquilo. E falhas isoladas também é
Limites e trade-offs de cada abordagem
13:08
difícil porque eles estão no mesmo
13:09
processo. Como que a gente resolve isso?
13:11
A gente resolve com isso aqui que eu
13:12
mostrei para vocês, que é o monoepo com
13:14
arquiteturas modulares. Aqui eu tenho
13:16
monorrepo com Enex, que é uma
13:17
ferramenta, mas aí vocês podem usar
13:18
Basel, que é bem famoso, que usa Java
13:20
até eh Maven Package, o pessoal usa
13:23
bastante. Eu tenho monorpoleo com apps e
13:25
pacotes. E aqui dentro eu posso fazer a
13:27
combinação infinita que eu quiser de
13:30
apps. Só vou precisar botar uma um
13:32
módulo para rodar sozinho, como esse
13:34
exemplo aqui, quando ele precisar
13:35
escalar. Senão eu posso deixar ele
13:36
rodando junto com outros módulos. Eu
13:37
economizo muito recurso e dessa forma eu
13:40
tenho ferramental, por exemplo, o NEX me
13:43
permite rodar somente o que mudou. Se só
13:45
o módulo de billing mudou, eu só rodo os
13:47
testes para ele, só rodo o deploy para
13:48
ele, eu só rodo o pipeline dele. E assim
13:50
eu tenho o quê? Deploy independente,
13:52
lembra? Tenho escala independente.
13:54
Depois eu tenho todas essas coisas que
13:56
uma ferramenta de monore nos dá. Por
13:58
isso que hoje a gente tem isso. A gente
13:59
tem boas ferramentas de monoporrepo e a
14:01
gente tem boas ferramentas de
Como o Nx e o monorepo viabilizam deploys e escalabilidade
14:02
virtualização também para rodar em
14:03
produção. Bom, eu espero que esse vídeo
14:05
tenha ajudado vocês a entender o momento
14:07
que a gente tá com arquiteturas
14:08
modulares. A gente tá muito além de
14:10
monolito, a gente consegue escalar muito
14:11
de uma forma muito simples. Aqui eu usei
14:14
Nest, mas todas as linguagens tem
14:16
maneiras similares de fazer. Vocês estão
14:18
trabalhando com esse tipo de
14:19
arquitetura, comenta aqui os desafios,
14:21
como que tá sendo, que eu te ajudo com
14:23
certeza. E também me segue aí porque meu
14:25
livro vai sair logo e também o meu novo
14:27
curso que é Os 10 princípios da
14:28
arquitetura modular. Vai ser bem bacana.

Você disse:
0:00
Olá pessoal eu sou a Juliana Bezerra e
0:02
nesse vídeo eu vou falar sobre o Spring
0:04
modulit é um módulo Spring modulit é um
0:07
módulo do Spring faz parte do
0:10
ecossistema do Framework e é um assunto
0:13
bem interessante porque esse módulo ele
0:15
foi proposto relativamente há pouco
0:19
tempo e por isso muita gente não conhece
0:21
então eu resolvi trazer aqui para vocês
0:23
e mostrar qual é a utilidade desse
0:25
módulo porque ele pode ser tão
0:27
interessante para nós que trabalhamos
0:29
com temas e grandes críticos e eu espero
0:34
que vocês gostem aliás se vocês curtem
0:36
esse tipo de conteúdo já se inscrevam no
0:38
canal e ativem as notificações para não
0:41
perder os próximos vídeos que eu vou
0:43
continuar falando bastante aqui de
0:44
alguns módulos do Spring pouc pouco
0:47
explorados e pouco conhecidos Tudo bem
0:49
então a ideia do Spring modul é
0:51
trabalhar com a ideia de monólitos
0:55
modulares a gente que já estuda
0:58
arquitetura que já conhece esses termos
1:01
mais técnicos aqui sabe que os sistemas
1:04
eles costumavam ser desenvolvidos como
1:07
monólitos sempre E aí surgiu a ideia de
1:10
dividi-los para utilizar arquitetura de
1:12
microsserviços que permiti uma maior
1:14
Independência e escala entre os times só
1:17
que quando a gente divide um sistema a
1:20
gente encara muitos outros desafios por
1:22
eles estarem divididos fisicamente não
1:25
só logicamente então foi estudado um
1:28
meio termo aqui para essa a divisão
1:30
porque ao invés de a gente dividir
1:31
fisicamente a gente não divide de forma
1:34
lógica e aí cria monólitos mais
1:37
modulares e a gente consegue ter ganhos
1:39
dos dois lados os ganhos das vantagens
1:41
de ter um monólito um único sistema um
1:43
único processo ali rodando e os ganhos
1:46
de escala de microsserviços pelo menos
1:48
em partes a gente também consegue então
1:51
a ideia basicamente desse Spring modulit
1:53
é ajudar a gente a criar um monólito
1:56
modular e aqui para vocês terem uma
1:58
ideia aqui da arquitetura do desenho de
2:00
como seria isso Imaginem que a gente tem
2:03
uma aplicação monolítica que ela tá numa
2:05
infra só mas ela é uma coisa só uma
2:07
implantável só ela não tem fronteiras
2:09
lógicas lá dentro dela E aí quando a
2:12
gente transforma no monólito modular a
2:14
gente pega essa base de código e divide
2:16
em n módulos e nessa divisão aqui a
2:19
gente vai ter a ajuda do Spring modules
2:21
para garantir que a gente esteja
2:24
realmente obedecendo as fronteiras de
2:26
módulos que criamos porque uma coisa é
2:28
garantir que tá tudo
2:30
funcionando outra coisa é dividir em
2:32
módulos aplicação para isso a gente pode
2:34
usar alternativas como DDD domain driven
2:38
design Eu já falei sobre esse assunto
2:39
inclusive no outro vídeo Vou deixar o
2:41
card para vocês mas uma vez que você tem
2:44
a lógica para encontrar as fronteiras e
2:46
você define as você pode usar o Spring
2:48
modulite para garantir que as fronteiras
2:50
serão respeitadas Essa é a ideia Beleza
2:54
então Vamos explorar alguns conceitos
2:55
dessa desse módulo do Spring para que a
2:58
gente possa ter mais facilidade na hora
3:01
de usar então a primeiro o primeiro
3:03
conceito que surge é a ideia de módulo
3:05
de aplicação a gente quer criar um
3:07
módulo para expor funcionalidades pros
3:10
outros módulos Tá bom então a gente tem
3:12
um pacote de aplicação aqui e a gente
3:14
vai criando um módulo para cada domínio
3:16
ou subdomínio que a gente tiver
3:19
identificado e esses módulos vão
3:21
funcionar como apis Então se o módulo B
3:24
quiser chamar o módulo a ele vai usar
3:26
api do módulo a E aí o módulo ele é essa
3:31
porta de entrada para suas
3:32
funcionalidades mas ele esconde
3:34
submódulos então se eu não quiser expor
3:37
informações operações de submódulos é só
3:40
não colocar nessa api eu deixo o módulo
3:43
o submódulo ali dentro ele já tá
3:44
protegido por padrão E aí eu não exponho
3:47
nessa api nessa espécie de api do módulo
3:50
então A ideia é muito simples é
3:52
basicamente isso a gente vai ver agora
3:54
como a gente consegue criar um código
3:56
utilizando essa ideia de módulos e como
3:58
a gente garante que os essa estrutura de
4:00
módulos que a gente identificou vai ser
4:03
obedecida Então a primeira coisa que eu
4:05
vou fazer é criar um projeto Spring
4:07
Nessa versão aqui Java vou chamar aqui o
4:12
nome do pacote eu vou chamar de demo
4:15
Spring
4:16
modules vai ser um jar vou usar o Java
4:21
21 Vamos colocar aqui como dependência
4:24
apenas o modul o Spring modul developer
4:28
Tools ok Ok é isso vou dar enter vou
4:31
gerar o projeto nessa pasta e aí a gente
4:34
pode abrir
4:35
aqui e visualizar Tá eu vou diminuir um
4:38
pouquinho o zoom porque acho que tá um
4:40
pouco exagerado certeza que todo mundo
4:43
tem um monitor pelo menos de 21
4:45
polegadas né ou quem tá assistindo do
4:48
celular tem uma resolução boa e aí vai
4:50
conseguir ver bem aqui esse código então
4:53
qual a ideia depois que eu adiciono essa
4:55
dependência a gente vai criar um projeto
4:57
aqui que vai ter um módulo de
4:58
notificação e um módulo de produtos
5:01
então toda vez que eu criar um produto
5:03
eu vou enviar uma notificação é
5:05
basicamente essa a ideia só para vocês
5:07
verem como funciona essa esse Spring
5:09
modul na prática Então vamos lá aqui
5:12
dentro desse pacote a gente vai criar
5:14
dois pacotes um chamado
5:18
notification e um
5:21
chamado produto vou chamar em português
5:24
tá
5:25
produto e
5:27
aqui renomear
5:32
Esse é produto e esse aqui vai ser
5:35
notificação notificação Ok então produto
5:39
e notificação esses dois carinhas vão
5:42
ser módulos quando a gente cria um
5:44
pacote aqui a gente já tá criando um
5:46
módulo dentro do projeto então é
5:48
importante a gente criar sub pacotes por
5:50
domínio para ter essa modularização aqui
5:53
do sistema monólito aqui dentro de
5:56
produto Então a gente vai criar uma
5:57
classe nova chamada produto
6:00
Java eu vou criar essa classe aqui com
6:04
alguns vou criar como Record na
6:07
verdade
6:09
Record E aí ela vai ter nome descrição e
6:14
preço eu vou tirar esse ID aqui assim já
6:17
tá suficiente vou criar também um
6:21
produto
6:26
service nossa deixa eu renomear aqui
6:30
esse produto service ele vai ser ele não
6:34
identificou aqui deixa
6:37
excluir Vamos criar de novo
6:40
agora produtos
6:43
service Java Agora sim criou aqui a
6:47
classe esse cara aqui vai ser um Service
6:49
do Spring boot
6:51
service e ele vai ter um método chamado
6:55
criar produto então vou ter um void aqui
6:58
criar
7:00
produto tá bom que ele vai fazer a
7:03
criação do produto e aqui a gente só vai
7:04
colocar uma mensagem mesmo para ilustrar
7:06
e depois que ele vai criar o produto ele
7:09
tem que notificar que essa produto foi
7:12
criado fazer uma notificação via sms via
7:14
e-mail enfim criar uma notificação então
7:17
aqui a gente vai criar aqui um
7:19
notificação notificação service Java Ok
7:25
esse cara aqui vai ser um
7:27
service esse
7:30
notificação serve vai ter um método aqui
7:35
criar notificação que ele vai receber
7:38
aqui um objeto notificação que a gente
7:41
ainda vai criar nessa esse Record e ele
7:44
vai mostrar aqui aqui a informação de
7:46
criando notificação e a gente pode até
7:49
mostrar mais informações né a gente pode
7:52
colocar
7:53
aqui vamos usar um loger
8:00
static
8:04
loger ortar aqui do
8:08
sl4j ok E aí esse loger a gente vai usar
8:13
para informar
8:15
[Aplausos]
8:16
aqui criando notificação E aí vou ter os
8:20
dados dessa notificação sendo impressos
8:24
e aí agora só falta a gente criar esse
8:25
Record Ok então vou colocar aqui criar
8:28
uma classe a gente entra aqui vamos
8:30
colocar um Record e aqui na notificação
8:33
a gente vai colocar as informações que a
8:35
gente quer que a notificação possua
8:37
então a notificação vai ter
8:40
um nome do
8:43
produto vai ter
8:47
também uma
8:49
data e vai ter também um formato ok que
8:54
vai me dizer aqui o que que é a
8:56
notificação se é um SMS se é um e-mail
8:58
eu vou em cortar essa data aqui do Java
9:01
útil e agora eu tenho a notificação
9:03
Então já tenho aqui meus módulos para
9:05
vocês verem como é que ficou a gente tem
9:07
um módulo notificação com duas classes e
9:09
um módulo produto com duas classes agora
9:12
o que que eu vou fazer aqui eu quero eu
9:15
preciso notificar quando o produto for
9:18
criado certo então eu vou injetar aqui
9:23
ó notificação
9:26
service vou criar um Construtor
9:30
para fazer injeção via Construtor vou
9:32
dar um Import aqui nessa
9:39
classe
9:42
notificação agora sim deixa eu
9:44
substituir
9:45
aqui agora se importou E aí eu vou
9:49
chamar o notificação
9:52
service
9:56
criar notificação e aqui dentro eu vou
9:58
criar ificação com o nome do produto
10:01
então vou ter aqui
10:03
produto nome com a data vou passar um
10:07
New date aqui e com o formato que a
10:10
gente vai colocar aqui
10:13
e-mail vamos colocar dessa
10:16
forma ok então beleza eu já tenho aqui
10:21
a configuração do meu do meu projeto e
10:25
perceba que o módulo de produto Tá
10:27
acessando o módulo de
10:30
PR fazer essa notificação Então é isso
10:32
que tá acontecendo os módulos estão se
10:35
comunicando Então se a gente rodar esse
10:37
programinha aqui a gente pode colocar um
10:39
um um Bin
10:43
aqui
10:45
application
10:48
Runner e a gente Pode injetar aqui
10:52
o product produto service e a gente pode
10:56
chamar o
10:59
produto service criar
11:03
produto tá bom E aí a gente
11:06
pode verificar aqui a gente tem um
11:09
código funcional porque é um código
11:10
simples que só faz a simulação aqui de
11:13
uma criação de produto e imprime a
11:15
notificação que é gerada então ele tá
11:18
chamando aqui uma funcionalidade que
11:20
passa por dois módulos do sistema Tá
11:24
certo então isso aqui a ideia de
11:27
application module ou de aplicação a
11:30
gente cria pacotes para representar cada
11:33
um desses módulos e além de tudo isso a
11:36
gente pode vir aqui criar um teste para
11:38
verificar se Como é que está a estrutura
11:40
de módulos da minha aplicação Então
11:42
posso vir aqui criar aqui um um teste
11:47
para verificar os módulos da
11:51
aplicação
11:53
posso utilizar aqui a classe application
11:58
modules aqui tem um método estático
12:01
of E aí eu passo a classe principal do
12:04
pacote principal da minha aplicação para
12:06
saber que módulos é que eu quero dar uma
12:08
olhada então quero dar uma olhada dos
12:10
módulos de toda a minha aplicação ou
12:12
seja de onde está a classe demo Spring
12:15
modul application com isso a gente pode
12:18
vir aqui imprimir os módulos quando a
12:21
gente roda aqui o teste a gente pode
12:23
observar na aba console de depuração
12:26
nessa opção aqui ó laune Java tests que
12:29
aparecem informações dos módulos que
12:31
temos na aplicação Então temos o módulo
12:33
produto e o módulo notificação temos o
12:37
pacote que cada um pertence e também as
12:40
dependências que esse módulo possui
12:42
Então esse módulo depende diretamente do
12:44
módulo notificação isso é interessante
12:47
pra gente identificar os pontos de
12:48
acoplamento entre os módulos e saber se
12:51
essas dependências são válidas ou não se
12:53
a gente permite elas ou não então com
12:56
essa com esse teste aqui a gente
12:58
consegue ver não um teste né só para
13:00
mostrar que a gente tem informações de
13:02
módulos a gente consegue depois aferir
13:05
se as dependências entre módulos estão
13:07
sendo respeitadas agora a gente vai
13:09
fazer uma coisinha diferente vamos fazer
13:11
esse módulo esse teste falhar da forma
13:14
que tá se a gente fizer uma verificação
13:18
dos módulos a gente não vai ter nenhum
13:21
problema aqui por quê Porque o que tá
13:24
acontecendo é que eu tô dependendo
13:27
apenas das classes públicas que estão
13:29
aqui nesses pacotes nesses módulos então
13:32
não vou ter problema nenhum eu tô
13:34
fazendo um módulo depender de outro e
13:36
tudo que tá no nível do módulo é visível
13:38
pro mundo exterior então para eu
13:40
proteger uma informação que eu não quero
13:42
que esteja visível pro mundo exterior
13:44
por exemplo se eu quiser proteger aqui o
13:47
notificação o que que eu posso fazer eu
13:50
posso criar um um sub pacote ou seja um
13:53
submódulo que eu vou chamar aqui de
13:55
interno e vou colocar esse notificação
13:58
aqui dentro
14:00
certo quando eu faço isso quando eu movo
14:03
notificação aqui para dentro já tô vendo
14:05
aqui na ide um errinho Olha só por quê
14:11
Porque o vs code já mostra para mim que
14:13
eu tenho uma referência a um módulo não
14:16
exposto então isso aqui não é um erro de
14:18
compilação é só que a gente tem uma
14:20
extensão aqui do Spring e ela nos ajuda
14:23
a encontrar esses problemas como se
14:25
fossem erros de compilação a gente
14:27
consegue rodar ainda o
14:30
programa tá perceba que eu não tenho
14:32
problema para rodar o programa ele roda
14:34
de fato e faz tudo funcionar bonitinho
14:37
só que eu tenho um problema aqui visível
14:42
no código e também meu teste agora vai
14:45
falhar Olha só o teste que antes passava
14:49
agora tá falhando diz que o módulo
14:51
depende o módulo produto Depende de um
14:54
tipo não exposto de um módulo interno
14:57
submódulo do notificação então não posso
15:00
acessar a classe notificação diretamente
15:03
aqui do meu produto service certo e é
15:06
isso que me faz ter garantia de que os
15:08
desenvolvedores vão obedecer as
15:11
restrições de módulo que eu colocar
15:12
então Spring modet vai me ajudar com
15:14
isso ele vai ajudar a fazer que testes
15:17
falhem para que os desenvolvedores não
15:19
desobedeçam as fronteiras que a gente
15:21
estabeleceu então o que que a gente
15:23
poderia fazer para corrigir esse
15:25
problema para não ter essa referência
15:28
inválida aqui o que a gente costuma
15:30
fazer para fazer essa comunicação entre
15:32
módulos é criar um famoso dto então
15:36
poderia ter um notificação dto P jaava
15:42
criar um Record Vamos colocar aqui
15:46
um Record com as mesmas informações
15:49
nesse caso não muda considere esse
15:52
notificação aqui Como Se Fosse
15:54
A Entidade de banco e esse aqui como se
15:58
fosse o objeto de transferência de dados
16:01
entre módulos então objeto que é visível
16:04
E aí o produto service criaria um
16:06
notificação dto ao invés de notificação
16:09
então a gente teria
16:11
aqui a operação dessa forma e aqui no
16:14
service eu usaria notificação
16:18
dto faria a transformação né Se fosse o
16:22
caso poderia ter um método
16:24
aqui Public notificação
16:30
from dto e lá no notificação a gente tem
16:34
nome e produto data e formato então a
16:36
data Na verdade é um
16:40
date Agora sim a gente vai ter um cara
16:43
compatível E aí a gente vai ter o código
16:45
Funcionando aqui a gente vai aceitar o
16:51
notificação convertido do
16:54
dto e basicamente é isso agora eu
16:57
consegui fazer aqui um esquema onde eu
16:59
tô expondo apenas o que eu quero da
17:01
notificação que é o dto nesse caso Por
17:04
simplicidade são os mesmos Campos mas em
17:06
em uma
17:08
implementação normalmente a gente teria
17:10
menos Campos do que a persistência aqui
17:13
a entidade de persistência e até nomes
17:15
diferentes tá certo é importante ter
17:18
esse dos acoplamento E aí o produto
17:20
service agora só depende de objetos
17:23
expostos pelo módulo notificação então
17:26
agora o meu teste que falhava vai
17:31
passar Tá bom então Perceba como é
17:34
interessante né a forma que o Spring
17:36
modulite permite que a gente proteja o
17:39
que é interno dos módulos e só exponha
17:42
mesmo o que a gente permite na nossa
17:44
configuração aqui do Java e a gente só
17:47
faz isso utilizando o mesmo um controle
17:49
de pacotes criando pacotes e sub pacotes
17:51
a gente não precisou colocar nenhum
17:52
anotação especial do Spring modulite
17:55
para ter esse comportamento a gente só
17:57
Precisou se preocupar em fazer os testes
17:59
para fazer a verificação aí de
18:01
referências válidas entre os módulos e
18:03
finalmente um conceito que eu quero
18:05
trazer agora para vocês que permite
18:07
ainda mais desacoplar esses módulos
18:09
entre si criar esse monólito modular
18:12
mais desacoplado possível é o conceito
18:15
de eventos então ao invés de a gente
18:17
fazer uma comunicação direta como a
18:18
gente tá fazendo aqui injetando
18:20
notificação service a gente vai utilizar
18:23
Um publicador de eventos a gente vai
18:26
fazer tudo isso via eventos e para isso
18:29
a gente vai ter que usar mais uma
18:30
dependência no projeto eu vou copiar e
18:33
colar ela aqui porque ela não está lá
18:35
nos starters do Spring Então vou colocar
18:38
aqui essa dependência aqui a versão pode
18:41
estar um pouquinho diferente não tem
18:42
problema eu não observei nenhuma quebra
18:45
de
18:45
compatibilidade entre essas versões
18:49
eh menores aqui então você pode usar uma
18:52
versão mais autorizada E aí com essa API
18:54
de eventos a gente consegue vir aqui
18:57
tirar essa dependência do notificação
18:59
service então o que que eu vou fazer ao
19:01
invés de depender do notificação service
19:03
e chamá-lo diretamente eu vou criar um
19:06
evento de notificação Então vou criar
19:09
aqui um evento de notificação do tipo
19:12
application event publisher e esse cara
19:16
aqui é que eu vou injetar no meu
19:18
Construtor certo
19:21
Opa deixa eu abrir mais espaço aqui pra
19:24
gente Esse cara aqui que eu vou injetar
19:26
no meu Construtor
19:28
agora a invés de fazer a chamada do
19:30
jeito que a gente estava fazendo a gente
19:32
vai criar a gente vai usar o publicador
19:35
de
19:36
eventos a gente vai publicar o evento E
19:40
aí a gente vai passar o dto aqui dentro
19:43
o evento vai ser nesse caso o próprio
19:44
dto Então vou tirar essa chamada aqui e
19:47
agora eu tô fazendo a publicação do
19:49
evento de
19:51
notificação tá bom não tenho dependência
19:54
acoplamento direto aqui com o módulo de
19:57
notificação e isso é legal porque eu não
20:00
vou ter problemas caso o módulo de
20:02
notificação mude eu tô só mandando o
20:05
evento Então se eu precisar mudar ali a
20:07
lógica assinatura dos métodos do
20:08
notificação service eu posso fazer isso
20:10
tranquilamente sem ter que refatorar os
20:12
códigos que dependem desse módulo porque
20:15
agora eles só dependem do evento de
20:16
notificação e não Da Lógica de
20:19
notificação em si Mas beleza eu
20:21
publiquei o evento agora como é que eu
20:22
reajo a esse evento como é que o meu
20:25
notificação vai saber que receber um
20:27
evento é aí que a gente vai utilizar o
20:30
conceito de application module listener
20:32
Então vou colocar uma uma anotação aqui
20:35
application mod listener e agora esse
20:38
método vai ficar ouvindo eventos desse
20:41
tipo Então sempre que chegar um evento
20:43
desse tipo ele vai ser chamado de forma
20:47
assíncrona muito legal isso aqui e uma
20:50
vez que eu tô trabalhando com esse
20:51
conceito de ass sincronicidade eu vou
20:54
precisar só
20:56
habilitar o comportamento assim síncrono
20:59
pra gente conseguir fazer isso aqui
21:00
funcionar de forma assíncrona de fato
21:03
assíncrona Ok então é isso essa é a
21:06
mudança a gente continua é claro com o
21:09
nosso teste passando porque agora a
21:11
gente tá dependendo de eventos e não
21:13
estamos dependendo de nenhuma classe
21:17
interna e se a gente rodar aqui a
21:20
aplicação ela tem que continuar
21:22
funcionando Olha só criou o produto e
21:25
criou a notificação através do evento
21:29
que foi publicado ali no nosso
21:30
application event publisher é isso
21:33
Pessoal esse foi o vídeo sobre o Spring
21:35
modul eu quis trazer pelo menos uma demo
21:38
mostrar os conceitos introdutórios para
21:41
vocês entenderem Qual é a motivação de
21:43
usar esse cara muita gente pode estar se
21:45
perguntando Ah mas por eu não uso os
21:47
modificadores do próprio Java bem o a
21:50
questão de usar os modificadores do
21:51
próprio Java é que você depende dos
21:52
desenvolvedores a ideia do Spring modul
21:55
é trazer testes para garantir que as
21:57
pessoas estão sendo a arquitetura que
22:00
foi elaborada que foi prescrita para
22:02
esse sistema se esse conteúdo te ajudou
22:04
de alguma forma por favor curte comenta
22:07
compartilha se inscreve no canal
22:09
considera ser membro também se quiser me
22:12
apoiar nos meus outros trabalhos eu vou
22:13
agradecer demais eu vou deixar os links
22:15
aqui de alguns cursos que eu tenho que
22:17
me ajudam a adquirir recursos e
22:20
ferramentas para produzir conteúdos
22:22
melhores para vocês então quem puder me
22:23
dar essa força eu vou ser muito muito
22:25
grata é isso ficamos por aqui e eu
22:28
espero ver vocês no próximo vídeo

