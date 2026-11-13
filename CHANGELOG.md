# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).

## [0.4.12] — 2026-11-12

Ajustes depois do teste da 0.4.11 no carro (relatório `nyx-car-diagnostico-20261112-222525.txt`).

### Alterado
- **Fim pela tela do ZLINK já na primeira vez.** No teste, todas as telas do CarPlay tinham o
  elemento da imagem do CarPlay (`phone_content_event_delegate`), e nenhuma tela de espera o
  tinha. Agora, se a imagem do CarPlay some da tela do ZLINK durante o CarPlay, mesmo numa
  tela nunca vista, o Nyx Car confere de novo 100 ms depois e, se continuar sem ela, mostra
  "CarPlay desconectado" e aprende essa tela. Na 0.4.11, o fim só era visto pela tela depois de
  ela ser aprendida com o Bluetooth ligado (o que só aconteceu quando o usuário abriu o ZLINK
  à mão), então as 4 primeiras desconexões ainda vieram pelo Bluetooth.
- **Pelo cabo, conexão lenta:** se o tempo máximo (20 s) chega com o cabo conectado e o ZLINK
  ainda mostrando "conectando", a tela de espera continua, até 1 min no total, em vez de tirar
  o ZLINK da frente. No teste, o CarPlay entrou 31 s depois do cabo.
- A imagem do CarPlay nunca é aprendida como tela de espera.
- Gravação: cada tela do ZLINK diz se tem a imagem do CarPlay e se mostra "conectando".

## [0.4.11] — 2026-11-12

Pedido do usuário: acabar com a tela de espera do ZLINK aparecendo "por alguns segundos" antes
de "CarPlay desconectado". **Testada no carro em 12/11/2026** (relatório
`nyx-car-diagnostico-20261112-222525.txt`): a Acessibilidade existe neste firmware e o serviço
ligou; na última desconexão, "CarPlay desconectado" entrou 0,14 s depois de o ZLINK trocar de
tela, 1,1 s antes do aviso do cabo. Nas 4 anteriores, a tela de espera do fim ainda não tinha
sido aprendida e o fim veio pelo Bluetooth (o ZLINK troca de tela ~0,6 s antes dele). O ZLINK troca de tela antes de religar o Bluetooth da central, e
nenhum aviso público do Android chega antes disso (HARDWARE.md, seção 5). O usuário autorizou
um **serviço de acessibilidade restrito ao ZLINK** (quarta exceção do plano, seção 9).

### Adicionado
- **Fim do CarPlay pela tela do ZLINK:** o serviço "Nyx Car — fim do CarPlay" avisa quando a
  tela do ZLINK muda. O Nyx Car aprende as telas de espera do ZLINK (as vistas com o Bluetooth
  da central ligado, quando não há CarPlay) e, se durante o CarPlay o ZLINK voltar para uma
  delas, mostra "CarPlay desconectado" **na hora**, sem esperar o Bluetooth religar. O que foi
  aprendido fica guardado no app.
  - Só observa o ZLINK; lê só a estrutura da tela (tipos e nomes internos dos elementos, se
    têm texto, se são tocáveis), nunca o conteúdo dos textos; não toca em nada.
  - Na dúvida (tela nunca vista, Bluetooth desligado sem o CarPlay registrado), não faz nada:
    o aviso do Bluetooth continua valendo, como na 0.4.10. Desligado, tudo funciona como antes.
  - Ligado pelo usuário em Configurações → Acessibilidade, com o atalho no Nyx Diagnóstico →
    Testes no carro → **cartão 6**. O relatório mostra se está ligado e quantas telas aprendeu.
- **Gravação dos testes:** cada tela nova do ZLINK entra na linha do tempo (resumo da
  estrutura, quantidade de elementos e nomes internos; sem textos), e o fim pelo Bluetooth
  mostra quantos segundos depois da tela do ZLINK ele chegou.

## [0.4.10] — 2026-11-12

Pedido do usuário e correção depois do teste da 0.4.9 no carro (relatório
`nyx-car-diagnostico-20261112-205612.txt`).

### Alterado
- **Toque na tela "Conectando ao CarPlay": volta ao Nyx Car e tira o ZLINK da frente**
  (pedido do usuário), em vez de fechar só a nossa tela e mostrar a espera do ZLINK. Tocar e
  segurar fecha só a nossa tela e mostra o ZLINK. Rodapé: "Toque para voltar ao Nyx Car ·
  toque e segure para ver o ZLINK". Se o CarPlay terminar de conectar depois, tocar em CarPlay
  no Nyx Car abre o ZLINK direto.
- **A vigia do sem fio só conta o ZLINK chegando à tela.** O ZLINK que já estava na frente
  (aberto pelo usuário ou parado na espera dele) não abre mais "Conectando ao CarPlay".

### Corrigido
- **"Conexão interrompida" trocada por "Conectando (sem fio)" num mau contato pelo cabo:** sem
  o iPhone, a central tenta conectar o Bluetooth dele a cada ~10 s, e cada tentativa rearmava a
  vigia com o ZLINK parado na tela. Agora a vigia só arma com o iPhone **conectado** pelo
  Bluetooth, e nada substitui as mensagens de fim. Se o CarPlay voltar a entrar durante
  "Conexão interrompida", a conexão segue.

### Removido
- Gravação dos testes: a leitura de arquivos do sistema sobre o USB e a rede. Nenhum deles pode
  ser lido por um app nesta central, e a lista de interfaces de rede veio vazia. Continua a
  leitura das propriedades a cada 100 ms.

## [0.4.9] — 2026-11-12

Pedidos do usuário depois do teste da 0.4.8 no carro (relatório
`nyx-car-diagnostico-20261112-193615.txt`). **Testada no carro em 12/11/2026** (relatório
`nyx-car-diagnostico-20261112-205612.txt`): o Nyx Car reage ao fim do CarPlay 50 a 65 ms
depois do Bluetooth religar (antes, 0,1 a 0,7 s) e cobre o ZLINK sem fio até 50 ms depois de
ele chegar à tela. "Conexão interrompida" entrou no mau contato, mas foi trocada 4 s depois
por "Conectando (sem fio)" (corrigido na 0.4.10).

### Adicionado
- **Conexão interrompida:** se a conexão cai antes de o CarPlay aparecer (cabo tirado ou mau
  contato), a tela do Nyx Car continua e mostra "Conexão interrompida — Aguardando o iPhone
  voltar…" por 5 s. Se o iPhone voltar, segue para "Conectando ao CarPlay"; se não, a tela e o
  ZLINK saem e o Nyx Car volta. Um toque volta na hora. Pelo cabo, vale também quando o iPhone
  aparece no USB e a central não vira aparelho dele em 6 s (nas gravações, leva até 3 s).
- **Tempo máximo sem o CarPlay começar:** a tela e o ZLINK saem e o Nyx Car volta (antes, a
  tela saía e deixava o ZLINK parado na espera dele). Se o CarPlay já tinha começado
  (Bluetooth da central desligado) e só o vídeo não foi percebido, a tela sai e mostra o
  CarPlay, como antes.
- **Gravação dos testes no carro:** horário com milissegundos; o app na tela e a porta USB
  lidos a cada 100 ms (antes, 1 s); arquivos do sistema sobre o USB da central e a rede (se o
  Android deixar ler) e as interfaces de rede ligadas, em busca de um aviso do fim do CarPlay
  que chegue antes da tela do ZLINK. Os avisos do sistema são registrados numa thread separada
  da leitura lenta, com o horário real.

### Alterado
- **Desconexão:** "CarPlay desconectado" entra na hora e fica 2 s (pedido do usuário), com o
  Nyx Car voltando por baixo; depois a tela sai e mostra o Nyx Car. A Home voltando não a
  fecha antes do tempo.
- **Mais rápido no fim do CarPlay e no sem fio:** tudo o que o Nyx Car faz em volta do CarPlay
  roda numa thread própria, com prioridade de tela. Antes, a leitura do app na tela passava
  duas vezes pela fila de baixa prioridade (0,1 a 0,7 s no teste) e a tela de espera disputava
  a thread principal com o Nyx Diagnóstico. A vigia do ZLINK lê a cada 100 ms (era 200 ms).
- **Janela reservada:** durante o CarPlay (e por 20 s depois de o iPhone se conectar pelo
  Bluetooth) a janela do Nyx Car fica reservada, invisível (1 px, transparente, sem receber
  toques). O Android passa a tratar o Nyx Car como um app à vista e não o deixa sem
  processador com o ZLINK na frente; a tela também aparece mais rápido, sem criar a janela.

## [0.4.8] — 2026-11-12

Ajustes pedidos pelo usuário depois do teste da 0.4.7 no carro (relatório
`nyx-car-diagnostico-20261112-181412.txt`). **Testada no carro em 12/11/2026** (relatório
`nyx-car-diagnostico-20261112-193615.txt`): pelo cabo, a tela entra e sai com precisão; sem
fio com o Nyx Diagnóstico na tela, o ZLINK ainda aparecia ~1 s antes; na desconexão, a tela
de espera do ZLINK aparecia por um instante; um cabo tirado logo depois de conectado deixava
a tela até o tempo máximo e depois o ZLINK parado. Ajustes na 0.4.9.

### Adicionado
- **CarPlay sem iPhone:** tocar em CarPlay (atalho, cartão de Navegação, Favoritos, Todos ou
  Conectividade) sem nada conectado mostra "Aguardando o iPhone — Conecte o cabo ou ligue o
  CarPlay sem fio no iPhone" no lugar da tela parada do ZLINK. Quando o iPhone conecta, a
  mesma tela passa para "Conectando ao CarPlay". Um toque volta para o Nyx Car; tocar e
  segurar abre o ZLINK mesmo assim; sai sozinha depois de 1 min. Com o CarPlay já conectado,
  o ZLINK abre direto.
- **Sem fio fora da Home:** quando o iPhone se conecta pelo Bluetooth (ou o ponto de acesso
  Wi-Fi liga), o Nyx Car vigia por 20 s se o ZLINK aparece sozinho, com qualquer app na tela,
  e cobre a tela de espera dele.

### Alterado
- **Sem fio na Home: a tela entra mais cedo.** A Home avisa ao sair da tela (`onPause`, antes
  do `onStop`) e o app na tela é lido direto da propriedade do sistema, em microssegundos (o
  `getprop` levava ~0,4 s). A vigia lê a cada 200 ms, só por alguns segundos.
- **CarPlay pronto:** a tela de espera fica mais 2 s depois de o vídeo chegar (sugestão do
  usuário), para o CarPlay já estar desenhado quando ela sai.
- **Desconexão: o Nyx Car volta na hora**, sem os 2 s de proteção contra mau contato (pedido
  do usuário). "CarPlay desconectado" cobre a tela de espera do ZLINK até a Home aparecer. Um
  cabo posto de volta vira uma conexão nova (o ZLINK abre sozinho de novo).

## [0.4.7] — 2026-11-12

Pedido do usuário: esconder a tela de espera do ZLINK (B1), pelo cabo e sem fio.
**Testada no carro em 12/11/2026:** pelo cabo, a tela saiu com o vídeo de 9,0 a 9,7 s depois de
o iPhone aparecer (num teste, o ZLINK apareceu ~1 s no fim); sem fio, saiu com o vídeo, mas o
ZLINK aparecia ~1 s no início e a tela não entrava fora da Home. Ajustes na 0.4.8.

### Adicionado
- **Tela de espera do CarPlay do Nyx Car**, uma janela sobreposta à tela de espera do ZLINK
  enquanto o CarPlay conecta. O ZLINK continua na frente, por baixo, sem nenhuma mudança na
  conexão. A tela mostra "Conectando ao CarPlay" e muda o texto conforme a conexão avança
  ("iPhone conectado pelo cabo" / "iPhone conectando sem fio" → "Preparando a tela do
  CarPlay…"), sem animação.
  - **Abre:** pelo cabo, quando o iPhone aparece no USB (antes de o ZLINK abrir sozinho); sem
    fio (ou se o aviso do USB não chegou), quando o ZLINK cobre a Home sem o usuário ter
    aberto um app.
  - **Fecha:** quando o vídeo do CarPlay chega (tráfego de rede ≥ 512 KB/s pelo cabo, ≥
    160 KB/s sem fio, depois de a central desligar o Bluetooth), após o tempo máximo (20 s
    pelo cabo, 25 s sem fio), com um toque, ou quando a Home volta para a tela.
  - **Desconexão:** nos 2 s de confirmação do fim, mostra "CarPlay desconectado — Voltando
    para o Nyx Car…" no lugar da tela de espera do ZLINK, até o Nyx Car voltar. Num mau
    contato, volta para "Conectando".
  - Não abre quando o usuário abre o CarPlay pelo Nyx Car, nem com o CarPlay já conectado.
- Permissão **"Sobrepor a outros apps"** (pedida pelo usuário uma vez, reversível): Nyx
  Diagnóstico → Testes no carro → cartão 5, com o botão "Ver a tela (5 s)". O relatório mostra
  se ela foi dada.

## [0.4.6] — 2026-11-12

Correções depois do teste da 0.4.5 no carro (relatório `nyx-car-diagnostico-20261112-164448.txt`).
**Validada no carro em 12/11/2026:** a volta automática funciona pelo cabo e no sem fio.

### Corrigido
- **Nyx Diagnóstico "pulando" ao voltar do CarPlay** (relatado pelo usuário): o mesmo problema
  que a Home tinha antes da 0.4.2. O diagnóstico e o catálogo de componentes passam a reservar
  sempre a altura estável da barra de status (`ui/StableSystemBars`).
- **Diário desatualizado no relatório:** a parte "Testes no carro" era lida quando o Nyx
  Diagnóstico abria; o que acontecia depois (as gravações) só aparecia na linha do tempo. Agora
  é lida de novo na hora de salvar.

### Alterado
- **Volta automática mais resistente a mau contato**, com base nas gravações: ao tirar o cabo,
  o Bluetooth da central religa ~0,5 s antes de o USB cair. Os dois sinais agora esperam 2 s, e
  o iPhone reaparecendo no USB (ou a porta voltando a conectar) nesse intervalo cancela o fim.
  Antes, um cabo tirado e posto de volta rápido encerrava a sessão.

### Documentação
- Resultado da medição da 0.4.5: o tráfego de rede do vídeo começa ~10 s depois de o iPhone
  aparecer no USB, ~1,3 s depois de o Bluetooth da central desligar, logo antes de o CarPlay
  aparecer: é o candidato ao sinal de "CarPlay pronto" para o B1 (HARDWARE.md, seção 5;
  ARCHITECTURE.md, D20).

## [0.4.5] — 2026-11-11

Pedido do usuário: a tela de espera do ZLINK "quebra toda a imersão". Primeiro passo
(ARCHITECTURE.md, D20).

### Adicionado
- **Volta automática ao Nyx Car quando o CarPlay termina:** com o ZLINK na tela, tirar o cabo
  do iPhone (fora por 1,5 s, para um mau contato não contar) ou o Bluetooth da central religar
  depois do CarPlay traz o Nyx Car para a frente, como o botão Home. Com outro app na tela,
  nada muda. Só avisos públicos do Android, sem serviço em segundo plano e sem mexer no ZLINK.
- Diário do app: iPhone no USB, cabo conectado, Bluetooth desligado pelo CarPlay e o fim de
  cada sessão, com o que foi feito.
- Nyx Diagnóstico → Testes no carro: a gravação mede o **tráfego de rede por interface** (só
  bytes por segundo), em busca do momento em que o CarPlay fica pronto, e registra as decisões
  da volta automática. Instruções novas: apertar o Home da central quando o CarPlay aparecer.

### Documentação
- Recuperação pelo **launcher original validada** pelo usuário (11/11/2026).
- Análise das 5 conexões pelo cabo: o ZLINK já abre sozinho ~3 s depois de o iPhone aparecer
  no USB; abrir o ZLINK pelo launcher não encurtaria a espera (HARDWARE.md, seção 5).
- Terceira exceção às regras do plano (seção 9): estudo de uma tela do Nyx Car por cima do
  ZLINK durante a conexão (B1).

## [0.4.4] — 2026-11-11

Validada no carro em 11/11/2026 (rolagem de "Todos" e botão de lápis aprovados pelo usuário).

### Adicionado
- **Aplicativos: seletor Favoritos | Todos.** "Favoritos" mostra 12 apps em 3 linhas de 4 (a
  aba sempre abre nele); o padrão são os 12 atalhos de antes. "Todos" lista os apps da
  central em ordem alfabética, com **ícones de traço** do Nyx Car e os mesmos nomes dos atalhos
  ("CarPlay" em vez de "ZLINK5"), nos **mesmos atalhos dos Favoritos** (4 colunas, 3 linhas por
  tela; pedido do usuário depois de testar no carro) e com **barra de rolagem** sempre visível
  na margem direita: os 20 apps ficam em 5 linhas. A lista é
  montada no primeiro toque e refeita ao voltar de outro app.
- **Editar favoritos** (botão só com o ícone de lápis, no alto, só em Favoritos): toque num favorito e escolha o app
  que vai no lugar dele; um app que já é favorito troca de lugar com o tocado, então são
  sempre 12, sem repetição. Cada troca já fica salva; "Restaurar padrão" volta aos 12 de
  antes e "Concluir" sai da edição. Voltar sai da escolha e depois da edição.
- Ícones novos: `browser` (Chrome), `search` (Google), `book` (Instruções), `image` (Scan de
  imagens), `app_generic` (apps instalados no futuro) e `edit` (botão Editar).
- Componentes novos: seletor de segmentos ("Favoritos | Todos"), botão de ícone (Editar) e
  barra de rolagem (`pc_scrollbar_thumb`/`pc_scrollbar_track`).
- Nyx Diagnóstico: o relatório lista os favoritos (só nomes) e o que aparece e o que fica
  escondido em "Todos".

### Alterado
- Ficam fora de "Todos": o próprio Nyx Car, entradas sem nome, os atualizadores de firmware,
  MCU e CAN (seção 9 do plano), o **Theme** e a **Música USB** (pedidos do usuário).
- Sai o aviso "A lista completa de aplicativos chega na próxima versão".

## [0.4.3] — 2026-11-11

Pedidos do usuário depois de validar a 0.4.1 e a 0.4.2 no carro.

### Alterado
- **Novo nome: Nyx Car** (antes "Premium Car", genérico demais). Mudam o nome do app, o
  ícone de diagnóstico ("Nyx Diagnóstico"), os textos, o título e o nome do arquivo do
  relatório (`nyx-car-diagnostico-*.txt`) e o nome do APK (`nyx-car-launcher-release.apk`).
  O identificador do app continua `com.premiumcar.launcher`: a atualização entra por cima,
  mantendo a tela inicial, as permissões e a última música (ARCHITECTURE.md, D19).

### Adicionado
- **Play Store** em Aplicativos (`com.android.vending`), no lugar que ficou vazio: a grade
  passa a ter 12 apps (3 linhas de 4). Ícone próprio em traço (`ic_store`: sacola com o
  símbolo de reproduzir), no padrão dos demais; não é o logotipo do Google.
- PC Diagnóstico: seção **Data e hora** (hora e fuso automáticos, fuso atual, tempo ligado e
  aviso quando o relógio parece estar na data do firmware). O atalho da Play Store também
  aparece em "Atalhos e indicadores do launcher".

### Documentação
- **0.4.3 validada no carro** (11/11/2026): o Nyx Car continuou como tela inicial depois
  da troca de nome; a hora automática da central está ligada (HARDWARE.md, seção 5).
- **0.4.1 e 0.4.2 validadas no carro** (11/11/2026): deslize aprovado, "Música USB" fora das
  duas abas, Home sem "pulo" ao voltar do CarPlay. O relatório confirma
  `persist.sys.autoplay = 0` (Reprodução automática desligada).
- Relógio: numa partida do zero, a central voltou para 16/04/2026 (data do firmware) e
  ficou errada por ~3 horas (HARDWARE.md, seção 5).
- Análise da cópia da pasta `Android` da central (HARDWARE.md, seção 8): o `can.apk` em cache
  é a versão já instalada do app Informações do carro; nada muda o launcher. A cópia entra
  no `.gitignore` (tem um identificador da central e o cache de mapas).

## [0.4.2] — 2026-11-11

### Removido
- **Atalho "Música USB"** (player do fabricante), a pedido do usuário: o player próprio o
  substitui. A aba Música fica com BT Música e Rádio; Aplicativos fica com 11 apps em grade
  de 4 colunas (o último lugar fica vazio). O **Vídeo** do fabricante continua.

### Alterado
- PC Diagnóstico → Testes no carro → 4. Player original: explica a solução encontrada pelo
  usuário (desligar "Reprodução automática" nas Configurações do Veículo → Geral).

### Corrigido
- **Home "pulando" ao voltar do CarPlay** (relatado pelo usuário): o ZLINK roda em tela cheia
  e esconde a barra de status; ao voltar, a Home era desenhada sem a barra e, quando ela
  reaparecia, descia 65 px. Agora a janela ocupa a tela toda e o conteúdo reserva sempre a
  altura estável da barra (`ui/StableSystemBars`), que não muda quando outro app a esconde.
  Se a altura reservada mudar, o diário registra.

### Documentação
- O player original não abre mais sozinho: o usuário desligou **Reprodução automática** em
  Configurações do Veículo → Geral (ajuste comum, não de fábrica). Desativar o app deixou de
  ser necessário. Menu de fábrica e Engineer mode registrados no HARDWARE.md (sem acesso ao
  ADB); segunda exceção à seção 9 (menu de fábrica, com salvaguardas).
- A central mantém o Premium Car como tela inicial também depois de uma reinicialização
  completa.

## [0.4.1] — 2026-11-10

Pedidos do usuário depois do teste da 0.4.0 no carro: **deslizar para trocar de aba** e
lidar com o **player original**, que abre sozinho ao conectar o pendrive.

### Adicionado
- **Deslizar entre abas:** arrastar o dedo para a esquerda abre a próxima aba e, para a
  direita, a anterior, na ordem da barra (Home, Música, Aplicativos, Conectividade,
  Configurações). Nas pontas, o deslize para (não dá a volta). Só troca com um arrasto
  claramente horizontal e de pelo menos 12% da largura da tela (154 px na central), ou com
  um gesto rápido de pelo menos 48 dp. Arrastos verticais (lista de músicas) e toques não
  mudam; um deslize que começa num botão não aciona o botão. A aba acompanha o dedo com
  resistência, e a nova entra com uma transição de 0,16 s (a barra continua instantânea).
- **Apps desativados:** atalhos de apps desativados nas Configurações mostram
  "Desativado" (antes, "Não encontrado"). Na aba Música, o atalho some: com o player
  original desativado, ficam só BT Música e Rádio.
- **PC Diagnóstico → Testes no carro → 4. Player original:** mostra se ele está ativado e
  abre a página dele nas Configurações do Android, onde ficam **Desativar** e **Ativar**. O
  Premium Car não desativa nada sozinho (um app comum não tem essa permissão).
- **Relatório:** situação do player original (seção "Player USB") e atalhos de apps
  desativados marcados como "app desativado".
- **Diário: erros que fecham o app** ("Erro do app": tipo, mensagem e local no código). Os
  caminhos de arquivo saem da mensagem, para não registrar nomes de músicas. A seção passa a
  se chamar "Diário de eventos (player e erros do app)".

### Alterado
- **Medição da abertura da Home:** quando o Android reabre o processo em segundo plano (a
  central fecha os apps ao desligar o carro), o tempo conta da criação da tela, e a abertura
  aparece separada como "processo já aberto". Na 0.4.0, esses casos entravam como aberturas
  de 16 a 24 s e distorciam a mediana; os registros antigos foram descartados.
- **R8 sem ofuscação** (`-dontobfuscate`, com números de linha): os erros do diário mostram
  nomes de classes legíveis, já que sem ADB não há como desfazer a ofuscação. O código sem
  uso continua sendo removido; o APK passa de 1.220.898 para 1.254.420 bytes.

### Documentação
- **Exceção à seção 9 do plano**, autorizada pelo usuário: desativar (nunca desinstalar) o
  player original, à mão, nas Configurações. Passo a passo e volta no BUILD.md, seção 8. A
  alternativa de o Premium Car voltar sozinho para a frente foi descartada pelo usuário: se
  não for possível desativar, o player original fica como está.
- Resultados do teste da 0.4.0 no carro (HARDWARE.md, PROJECT_PLAN.md).

## [0.4.0] — 2026-11-10

Primeira etapa da Fase 4, aprovada pelo usuário: **player próprio** das músicas do pendrive e
Premium Car **disponível como tela inicial**.

### Adicionado
- **Player próprio na aba Música:** pastas e músicas do pendrive à esquerda (a raiz do
  pendrive é a primeira pasta), música atual à direita com capa (quando o arquivo tem),
  título, artista, posição na pasta, progresso e ⏮ ▶ ⏭. Embaixo, atalhos para BT Música,
  Rádio e o player original (Música USB).
- A música continua tocando com outro app aberto e **pausa quando o CarPlay, o BT Música ou
  uma ligação assumem o som**. Em interrupções curtas, volta sozinha.
- **Cartão de música da Home:** mostra a música e o artista; os botões controlam o player.
  Sem música escolhida, levam à aba Música. Tocar no cartão também leva à aba Música.
- **Memória:** guarda a última música, a pasta e a posição. Ao ligar a central, a música
  aparece **pausada** e só toca com um toque (decisão do usuário).
- Pendrive removido: a música para e o player avisa. Música em formato que a central não
  toca: o player pula para a próxima e avisa.
- **Tela inicial:** o Premium Car aparece na escolha de tela inicial do Android
  (Configurações → Apps padrão → Tela inicial, ou "Sempre" ao tocar em Home). O app não muda
  essa configuração sozinho. O botão Home volta para a Home de qualquer aba.
- **Recuperação:** a aba Configurações ganha **Tela inicial** (abre Apps padrão) e
  **Launcher original** (abre a tela de início original); o cartão "Sobre" explica a volta.
- **PC Diagnóstico:** seção "Player próprio" (permissão, pendrives encontrados, formatos na
  raiz, estado) e o diário de eventos do player, sem nomes de músicas.
- **Gravação do CarPlay:** passa a registrar conexões Bluetooth por perfil (sem nome nem
  endereço do aparelho), os tipos de áudio tocando e o modo de áudio.
- Permissões novas: leitura das músicas (pedida na aba Música) e as de serviço de
  reprodução em primeiro plano (concedidas na instalação; ver ARCHITECTURE.md, D6).

### Alterado
- Os botões ⏮ ▶ ⏭ não enviam mais teclas de mídia ao que estiver tocando: controlam o
  player próprio (o player do fabricante não aceitava comandos com a tela fechada).
- Configurações em grade 4 × 2.

### Corrigido
- PC Diagnóstico: ao parar a gravação, a leitura que estava em andamento ainda registrava
  tudo de novo como "Início" (visto no relatório da 0.3.2).

## [0.3.2] — 2026-11-10

Correção do PC Diagnóstico: os relatórios da 0.3.1 chegaram ao PC com **0 bytes**. A Home é
a mesma da 0.3.0.

### Corrigido
- **"Salvar em…"** força a gravação física do arquivo e confere o tamanho no destino antes
  de dizer "salvo". Causa mais provável dos 0 bytes: o arquivo é criado no pendrive na hora
  em que o local é confirmado, mas o conteúdo fica no cache do sistema por alguns segundos;
  se o pendrive sai antes disso, o arquivo fica vazio. A mensagem agora mostra o tamanho e
  avisa quando o pendrive pode ser removido.
- O relatório é montado **antes** de abrir o seletor e guardado num arquivo do app. Antes,
  se o Android encerrasse o app com o seletor aberto, o salvamento era ignorado em silêncio
  e o arquivo criado pelo seletor ficava vazio.
- "Salvar em Download" também força a gravação e confere o tamanho.
- Se a coleta ainda não terminou, o app avisa em vez de não fazer nada.

### Adicionado
- A linha do tempo dos testes é gravada também num arquivo do app. Se o Android encerrar o
  PC Diagnóstico durante uma gravação, o que foi registrado volta na próxima abertura, com
  o aviso "Gravação interrompida".
- Testes: gravação e conferência do relatório, relatório preparado e linha do tempo em
  arquivo (49 no total).

## [0.3.1] — 2026-11-10

Versão de investigação: a Home é a mesma da 0.3.0.

### Adicionado
- PC Diagnóstico → aba **Testes no carro**:
  - **Gravação de até 3 minutos**, com o PC Diagnóstico em segundo plano: eventos USB,
    propriedades do sistema que mudarem (uma leitura por segundo), música tocando, sessões
    de mídia e avisos de música dos players. Serve para medir a conexão do CarPlay pelo cabo
    e o comportamento do player USB.
  - **Comandos de teste para o player USB** por três caminhos: tecla de mídia do sistema,
    receptor de botões do player e sessão de mídia do player.
  - **Acesso às sessões de mídia (opcional):** atalho para "Acesso a notificações". Com o
    acesso, o diagnóstico lista quais apps publicam estado e faixa. O app não lê notificações.
  - Seções "Player USB", "Receptores de botões de mídia no sistema" e "Sessões de mídia".
- A linha do tempo vai para o relatório.
- Testes: diferenças de propriedades e formatação de tempo (44 no total).

### Removido
- O teste USB manual da aba Apps e atalhos (substituído pela gravação).

### Problemas conhecidos (vistos na 0.3.0)
- Os botões ⏮ ▶ ⏭ do cartão de música não comandam o player USB, e a música para ao sair
  do player. Em investigação (ARCHITECTURE.md, D16).
- Ao conectar o iPhone, a tela de espera do ZLINK aparece antes do CarPlay. Em investigação
  (ARCHITECTURE.md, D10).

## [0.3.0] — 2026-11-09

Fase 3 — interface principal.

### Adicionado
- **Navegação entre telas:** a barra inferior troca entre Home, Música, Aplicativos,
  Conectividade e Configurações, na mesma janela e sem animação. Cada aba é montada na
  primeira visita. **Voltar** leva das abas para a Home; na Home, não faz nada.
- **Atalhos reais** (pacotes levantados pelo diagnóstico):
  - Home: Bluetooth, CarPlay (ZLINK), Aplicativos e Configurações (abas); cartão de
    Navegação abre o CarPlay; cartão de Música abre o **player USB**.
  - Música: BT Música, Música USB, Rádio, YouTube e controles de mídia.
  - Aplicativos: Rádio, Música USB, Vídeo, Bluetooth, CarPlay, Maps, YouTube, Câmera,
    Informações do carro, Equalizador, Comandos de volante, Arquivos.
  - Conectividade: Bluetooth, Wi-Fi (tela do Android), CarPlay e um cartão de estado.
  - Configurações: Android, Som, Tela, Data e hora, Veículo, PC Diagnóstico e a versão.
- App ausente: atalho desativado com a legenda **"Não encontrado"**.
- **Controles de mídia** (⏮ ▶ ⏭) por teclas de mídia do Android, na Home e na aba Música.
  O ícone ▶/⏸ acompanha se há música tocando.
- **Indicador de Wi-Fi** real. O de Bluetooth só aparece quando o Android informa "ligado".
- 16 ícones novos no estilo do Design System (rádio, pendrive, filme, mapa, câmera,
  veículo, velocímetro, equalizador, volante, pasta, engrenagem, som, tela, relógio,
  diagnóstico, vídeo).
- PC Diagnóstico → Apps e atalhos: seção **"Atalhos e indicadores do launcher"**, com a
  tela que cada atalho abre e os estados de Wi-Fi, Bluetooth e música.
- Testes: navegação, voltar, abertura de apps, app ausente, teclas de mídia, alvos de
  64 dp e captura de cada aba (42 testes no total).

### Alterado
- Cartão de música sem dados fictícios: "Músicas do pendrive", tocar abre o player.

### Segurança
- Nova permissão `ACCESS_NETWORK_STATE` (e `BLUETOOTH` até o Android 11): normais, somente
  leitura. Continua sem internet, sem serviços e sem categoria HOME.

### Documentação
- **Fase 2 concluída:** Home 0.2.1-ref aprovada na central. Abertura a frio de ~585 ms e
  ~10 MB de PSS.
- Teste "CarPlay pelo cabo": o iPhone não aparece na lista USB do Android (9 leituras), e o
  ZLINK abre sozinho ao conectar, com a própria tela de "conectando". A tela de espera
  própria fica inviável por esse caminho (ARCHITECTURE.md, D10; HARDWARE.md, seção 5).

## [0.2.1-ref] — 2026-11-09

Ajustes da Fase 2 após a primeira avaliação na central (posições dos elementos aprovadas).

### Alterado
- O atalho do ZLINK passa a se chamar **CarPlay**, com ícone próprio no estilo da Home
  (anel com o símbolo de reproduzir). O ZLINK continua intacto: só o atalho é nosso.
- **Barra inferior só com ícones**, sem rótulos. A seleção agora é uma pílula turquesa-escura
  atrás do ícone; o nome do destino fica para leitores de tela e avisos. Altura de 88 para
  80 dp (o espaço vai para os cartões).
- Cartão de Navegação: **"Waze pelo CarPlay"**, o uso principal informado. A escolha do app
  de navegação fica para a Fase 4.

### Adicionado
- PC Diagnóstico → Apps e atalhos: **teste "CarPlay pelo cabo"**, que lista os aparelhos USB
  conectados e indica se há um iPhone. Cada toque em "Atualizar" vira uma leitura com
  horário no relatório. Somente leitura: não pede permissão, não se comunica com o iPhone e
  não interfere no ZLINK. Serve para avaliar a tela de espera própria (ARCHITECTURE.md, D10).
- Testes: barra sem rótulos com nomes acessíveis, atalho CarPlay e formatação do teste USB
  (30 testes no total).

### Removido
- Ícone `phone_link`, substituído por `carplay`.

## [0.2.0-ref] — 2026-11-09

Fase 2 — Design System (tela de referência).

### Adicionado
- **Design System em Views nativas:** paleta aprovada, escala tipográfica (16–112 sp),
  espaçamentos em grade de 8 dp, cantos, alvo mínimo de toque de 64 dp e 14 ícones
  vetoriais próprios. Documentado em DESIGN_SYSTEM.md.
- Componentes com estados (normal, pressionado, selecionado, desativado): botão principal
  e secundário, botões redondos de mídia, atalho (`ShortcutTile`), cartão e barra inferior
  (`BottomNavBar`).
- **Home de referência** (1280 × 655 dp): relógio e data reais (`TextClock`), indicadores
  de Bluetooth e Wi-Fi, atalhos (Bluetooth, ZLINK, Aplicativos, Configurações), cartões de
  navegação e de música e barra inferior com 5 destinos. Os toques ainda não abrem nada:
  informam a fase em que a função chega.
- Catálogo de componentes, aberto em PC Diagnóstico → Calibração.
- A Home registra abertura e memória a cada abertura a frio. Os registros aparecem e são
  exportados na aba Desempenho do diagnóstico.
- Testes: captura da Home e do catálogo, alvo mínimo de 64 dp em todo elemento tocável,
  paleta Kotlin igual a `colors.xml`, navegação e registro de métricas (26 testes no total).

### Alterado
- O ícone **Premium Car** abre a Home (Views). O painel de diagnóstico ganhou ícone e
  tarefa próprios: **PC Diagnóstico**.

## [0.1.1-diag] — 2026-11-09

### Adicionado
- Aba **Apps e atalhos**: cartão "ADB via Wi-Fi" com o botão **Abrir opções do
  desenvolvedor**, que abre a tela padrão do Android escondida do menu neste firmware.
  Também o botão **Atualizar status** e a seção "Depuração (ADB)", que mostra se as
  opções do desenvolvedor e a depuração estão ligadas.
- Teste automatizado do botão.

### Decisão
- Interface do launcher em **Views nativas** (ARCHITECTURE.md, D2), confirmada pelo usuário.
- ADB via Wi-Fi testado e **indisponível**: o fabricante bloqueou as opções do
  desenvolvedor. O trabalho segue por pendrive (ARCHITECTURE.md, D14).

### Documentação
- Resultados do diagnóstico na central registrados em HARDWARE.md: Android 8.1 real
  (API 27), 4× Cortex-A7 a 1,1 GHz, Mali-450, 160 dpi, área útil de 1280 × 655 dp,
  cadência máxima de ~26 quadros/s e pacotes de ZLINK, Bluetooth, música e do launcher original.
- Teste A/B na central: Views nativas superaram o Compose em abertura (285 × 561 ms),
  fluidez (26,9 × 9,6 fps) e memória (12,4 × 23,4 MB). Recomendação registrada em
  ARCHITECTURE.md (D2).

### Problema conhecido
- A métrica de "quadros atrasados" do diagnóstico usa os 60 Hz informados pelo sistema
  e superestima os atrasos nesta central, cuja cadência real é de ~38,7 ms.

## [0.1.0-diag] — 2026-11-09

Fase 1 — Diagnóstico.

### Adicionado
- Projeto Android: Kotlin, Jetpack Compose (Material 3 personalizado), AGP 9.5.1,
  Gradle 9.9.0, `minSdk 23`, `targetSdk 37`. Um único módulo `app`.
- APK de diagnóstico **somente leitura** com sete abas:
  - **Sistema:** API real × versão declarada, kernel, modo low-RAM, ABIs, CPU, frequência,
    memória, GPU (OpenGL), armazenamento.
  - **Tela e toque:** resolução, densidade, área em dp, área útil e barras do sistema,
    taxa de atualização, telas de toque.
  - **Apps e atalhos:** launcher padrão, launchers instalados, candidatos a atalho
    (ZLINK, Bluetooth, música, rádio, navegação…), disponibilidade das telas de
    Configurações, serviços de mídia.
  - **Recursos:** recursos de hardware e software declarados.
  - **Propriedades:** `getprop` sem identificadores (serial, MAC, IMEI, rede).
  - **Desempenho:** tempo de abertura, memória (PSS) e teste de fluidez Compose × Views,
    cada um em processo próprio.
  - **Calibração:** degraus de preto, tons da paleta, cores de destaque, tamanhos de
    texto, alvos e precisão do toque.
- Exportação do relatório em .txt (pasta Download ou local escolhido).
- Paleta provisória (grafite + turquesa) e ícone adaptativo em vetor.
- Testes unitários e teste de tela na JVM (Robolectric + Roborazzi) com capturas em 1280 × 720.
- Documentação: README, PROJECT_PLAN, ARCHITECTURE, HARDWARE, BUILD, CHANGELOG.

### Segurança
- Sem permissão de internet, sem serviços, sem inicialização automática e sem categoria
  HOME: o app não substitui o launcher original.
