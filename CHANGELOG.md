# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).

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
