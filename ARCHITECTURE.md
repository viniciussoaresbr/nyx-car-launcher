# Arquitetura e decisões técnicas

Princípio geral: **o mais simples que funcione bem em hardware fraco.** Um módulo, poucas
dependências, nenhuma camada sem necessidade concreta.

## 1. Estrutura

Um único módulo Gradle (`app`), organizado por pacotes:

```
app/src/main/java/com/premiumcar/launcher/
├── app/           Application e MainActivity (janela única: Home + abas, em Views)
├── launcher/      Abertura de apps e telas do sistema (AppLauncher), apps da central (CarApps)
├── player/        (0.4.0) Player do pendrive: MusicPlayer, PlaybackService, leitura por
│                  pastas (MusicStorage), etiquetas (TrackReader), memória e diário
├── system/        Leituras do sistema para a interface: Wi-Fi, Bluetooth, thread de
│                  segundo plano
├── design/        PcPalette (espelho de colors.xml) e tema Compose do diagnóstico
├── ui/            Destinos da navegação
│   ├── components/  ShortcutTile, BottomNavBar (componentes do Design System)
│   ├── screens/     Conteúdo e montagem das abas (TabContent, ScreenBuilder)
│   ├── apps/        Aba Aplicativos: Favoritos | Todos e edição dos favoritos (AppsScreen, 0.4.4)
│   ├── music/       Aba Música: navegação por pastas e música atual (MusicScreen)
│   └── catalog/     Catálogo de componentes (ferramenta de desenvolvimento)
├── diagnostics/   Coleta de informações do sistema e relatório
│   └── ui/        Painel de diagnóstico (Compose, ícone "Nyx Diagnóstico")
├── perf/          Medições: abertura, quadros, memória, registro da Home, testes de fluidez
└── settings/      Preferências do usuário: favoritos da aba Aplicativos (FavoritesStore, 0.4.4)

app/src/main/res/  Design System em recursos: colors, dimens, styles (tipografia e
                   componentes), color/ (cores por estado), drawable/ (fundos e ícones),
                   layout/ (Home e componentes)
```

Os tokens, componentes e regras visuais estão em [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md).
O pacote `settings` é criado na Fase 4.
O diagnóstico será portado para Views como a tela "Sobre / Diagnóstico" das
Configurações; aí o Compose sai do APK.

## 2. Decisões

### D1 — Kotlin + Android SDK
Linguagem oficial do Android, sem custo extra de execução.

### D2 — Tecnologia de interface: **Views nativas** (decidido em 09/11/2026)
Confirmado pelo usuário, que também percebeu as Views bem mais fluidas na central.

- O plano condiciona o Compose a "testes que comprovem desempenho adequado". O APK de
  diagnóstico fez um **teste A/B** na central: a mesma grade de ícones rolada
  automaticamente, em Compose e em Views, cada uma em processo próprio e a frio.
- **Resultado** (medianas de 3 execuções; detalhes em [HARDWARE.md](HARDWARE.md#4-desempenho--linha-de-base)):

  | | Compose | Views |
  |---|---|---|
  | Abertura (processo → 1º quadro) | 561 ms | 285 ms |
  | Fluidez na rolagem | 9,6 fps, travadas de até 1,6 s | 26,9 fps (o teto do aparelho), sem travadas |
  | Memória (PSS) | 23,4 MB | 12,4 MB |

- **Conclusão:** neste hardware (Cortex-A7 a 1,1 GHz, Mali-450, apps sem compilação
  antecipada), o Compose **não** atingiu desempenho adequado. As Views nativas abriram
  2× mais rápido, usaram metade da memória e alcançaram o teto de fluidez.
- **Decisão:** interface do launcher em **Views nativas**, com widgets da
  plataforma e estilos próprios. Sem AppCompat e sem Material Components, salvo
  necessidade justificada. Os princípios do Material 3 (escala de tipos, formas,
  estados) continuam como referência visual.
- O painel de diagnóstico atual (em Compose) segue como ferramenta. Quando virar a tela
  "Sobre / Diagnóstico" do launcher, será portado para Views, e o Compose sairá do APK.
- Ícones: vetores próprios (`VectorDrawable`), sem bibliotecas de ícones.

### D3 — Single-Activity, navegação por estado (implementado na Fase 3)
Cinco destinos fixos (Home, Música, Apps, Conectividade, Configurações) não justificam
uma biblioteca de navegação. Um estado `Destino atual` + `when` resolve, sem dependência
extra.

- A Home faz parte do layout principal. Cada aba é montada na **primeira visita** e depois
  só mostrada ou escondida (`GONE`): troca instantânea, sem animação, sem recriar telas.
- **Deslize (0.4.1, pedido do usuário):** o contêiner das abas (`ui/components/SwipeTabsLayout`)
  reconhece o arrasto horizontal e pede a aba vizinha na ordem da barra; nas pontas, nada
  troca. Segue o padrão do Android para gestos em grupos de Views: só assume o toque quando o
  movimento é claramente horizontal (mais de 2× o vertical e acima de 2× a distância mínima de
  toque do sistema), e aí o filho recebe `ACTION_CANCEL` (o botão sob o dedo não é acionado).
  A lista de músicas, ao rolar, pede para não ser interrompida, então rolagens verticais nunca
  trocam de aba. Troca ao soltar com 12% da largura (154 px na central, mínimo 96 dp) ou com um
  gesto rápido (≥ 600 dp/s e ≥ 48 dp). Retorno visual barato: a aba atual acompanha o dedo a 35%
  (até 120 dp) e a nova entra em 160 ms (deslocamento e transparência, com camada de hardware).
  Sem animação contínua; a barra inferior continua trocando na hora.
- **Barra de status estável (0.4.2):** a janela ocupa a tela toda e a raiz (`home_root`)
  recebe como margem a altura **estável** das barras do sistema (`ui/StableSystemBars`;
  `SYSTEM_UI_FLAG_LAYOUT_STABLE` no 8.1, `getInsetsIgnoringVisibility` no 11+), ou a visível,
  se for maior. Antes, ao voltar do CarPlay (tela cheia, sem barra de status), a Home descia
  65 px quando a barra reaparecia. Mudanças nessa margem vão para o diário do app.
  **Confirmado no carro em 11/11/2026.**
- **Voltar:** numa aba, volta para a Home; na Home, não faz nada (comportamento de
  launcher). No Android 13+ via `OnBackInvokedCallback`; até o 12 (a central), via
  `onBackPressed`.
- As abas dividem a altura por peso (linhas de atalhos e cartões), sem rolagem.

### D4 — `minSdk 23`, `compileSdk`/`targetSdk 37`
- `minSdk 23` porque a versão real do Android da central é incerta (possivelmente 8.x).
  É o mínimo exigido pelas bibliotecas AndroidX atuais.
- `targetSdk 37` (o mais recente) só altera comportamento em aparelhos com Android 17.
  Na central não muda nada.
- Recursos de APIs mais novas são usados só com verificação de versão (`SDK_INT`).

### D5 — Nada de launcher padrão, serviços ou inicialização automática (por enquanto)
- **Fase 1:** o app **não** declara `CATEGORY_HOME`. Abre como um app comum e não disputa
  o botão Home com o launcher original.
- Sem `Service`, sem `BOOT_COMPLETED`, sem alarmes, sem trabalho em segundo plano.
- O Wi-Fi é escutado só entre `onStart` e `onStop` (tela visível). As consultas ao
  `PackageManager` rodam numa única thread de baixa prioridade, sob demanda (depois do
  primeiro quadro e ao voltar de outro app), nunca periodicamente.
- A categoria HOME só será adicionada na Fase 7, com o roteiro de recuperação testado e
  com a sua autorização.
- **10/11/2026:** o usuário pediu o Nyx Car como tela inicial padrão. A categoria HOME foi
  antecipada para a etapa 0.4.0 (PROJECT_PLAN.md), com o roteiro de recuperação testado na
  mesma viagem. O serviço de reprodução do player próprio será a primeira exceção à regra
  "sem `Service`", e só existirá enquanto houver música.
- **Implementado na 0.4.0:**
  - `MainActivity` declara MAIN/HOME/DEFAULT e mantém MAIN/LAUNCHER (ícone comum).
    `singleTask`, `clearTaskOnLaunch`, `stateNotNeeded`: uma só Home; o botão Home chega
    por `onNewIntent` e volta à aba Home.
  - **O app não define nada sozinho:** só passa a aparecer na escolha de tela inicial do
    Android. Quem escolhe é o usuário. A volta está a um toque (Configurações → "Tela
    inicial" e "Launcher original") e no BUILD.md, seção 8.
  - Rede de segurança do Android (comportamento do AOSP, não conferido neste firmware): se
    uma tela inicial de terceiros fecha com erro repetidamente, o sistema desfaz a escolha
    dela como padrão.
  - Ainda sem `BOOT_COMPLETED` e sem alarmes: ao ligar, o próprio Android abre a tela
    inicial escolhida.
  - `PlaybackService` (D17) existe só enquanto a música toca.

### D6 — Sem permissão de Internet
O launcher não precisa de rede. Isso elimina telemetria acidental e reduz a superfície
de risco.

Permissões declaradas:
- concedidas na instalação, somente leitura: `ACCESS_NETWORK_STATE` (indicador de Wi-Fi),
  `BLUETOOTH` até o Android 11 (indicador de Bluetooth);
- pedida ao usuário: `WRITE_EXTERNAL_STORAGE` até o Android 9 (salvar o relatório do
  diagnóstico);
- **0.4.0, player próprio:** `READ_EXTERNAL_STORAGE` até o Android 12 / `READ_MEDIA_AUDIO`
  no 13+ (ler as músicas do pendrive; pedida na aba Música, uma vez);
  `FOREGROUND_SERVICE` e `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (exigidas só a partir do
  Android 9 e 14; concedidas na instalação); `POST_NOTIFICATIONS` (só existe no 13+; não é
  pedida, e sem ela a música toca do mesmo jeito).

### D7 — Persistência (Fase 4)
Fase 1: nenhuma. Fase 4 (favoritos, atalhos, formato do relógio): **DataStore
Preferences**, como sugere o plano. Ele grava de forma assíncrona e evita leituras de
disco na thread principal, relevantes em eMMC lento. O peso no APK será conferido. Se
for desproporcional, a alternativa é `SharedPreferences` (nativo).

- **0.4.0:** o player guarda só cinco valores (música, pasta, nome da pasta, posição,
  título/artista) em **`SharedPreferences`** (`player/PlayerMemory`), com `apply()`
  assíncrono. Para isso o DataStore (mais coroutines e protobuf) seria desproporcional. A
  decisão do DataStore fica para quando houver configurações de verdade.
- **0.4.4:** os 12 favoritos da aba Aplicativos também ficam em `SharedPreferences`
  (`settings/FavoritesStore`, 12 linhas curtas, gravadas na hora com `apply()`). Cada favorito
  é o **id estável** de um app conhecido (`TabContent.knownApps`: "radio", "carplay"…) ou, para
  um app que o Nyx Car não conhece, pacote, classe e nome. Nada de ids de recursos (mudam entre
  versões). Ao ler, a lista é validada (`launcher/Favorites.normalize`): ids desconhecidos e
  repetidos saem, e o padrão completa até 12. Com isso a grade é montada na hora, sem consultar
  o `PackageManager`.

### D8 — Relógio eficiente
`TextClock` da plataforma: ele mesmo escuta `ACTION_TIME_TICK` (uma vez por minuto),
`TIME_SET` e `TIMEZONE_CHANGED`, e para de escutar quando a tela sai de vista. Sem
atualização por segundo, sem loops, sem código próprio.

### D9 — Lista de apps eficiente (implementada na 0.4.4)
- Consulta ao `PackageManager` (MAIN/LAUNCHER) fora da thread principal, só no primeiro toque
  em "Todos"; a lista fica na memória.
- **Nenhum ícone de outro app é carregado:** por decisão do usuário, cada app conhecido usa o
  ícone de traço do Nyx Car e os desconhecidos, um ícone genérico. Por isso o cache de ícones
  previsto (`LruCache`) não foi necessário.
- Atualização ao voltar de outro app (`onRestart`), sem varreduras periódicas nem callbacks:
  o usuário não pretende instalar apps.
- Regras (`launcher/AllApps`, lógica pura): ordem alfabética sem diferenciar acentos; nomes e
  ícones dos atalhos para os apps conhecidos; escondidos o próprio Nyx Car, entradas sem nome,
  os atualizadores de firmware, MCU e CAN (um toque errado levaria a uma atualização, proibida
  pela seção 9), o Theme e a Música USB (pedidos do usuário).

### D10 — ZLINK: apenas atalho, exibido como "CarPlay"
Abertura pela Intent de lançamento padrão, com o nome de pacote descoberto pelo
diagnóstico. Nada de interceptar, modificar ou depender de componentes internos do ZLINK.

- **Nome e ícone (09/11/2026):** no launcher, o atalho se chama **CarPlay** e usa um ícone
  próprio no estilo da Home. Só o atalho é nosso; o ZLINK continua intacto.
- **Uso informado pelo usuário:** navega pelo **Waze no CarPlay** e conecta o iPhone
  **mais pelo cabo** do que sem fio. Por isso o cartão de Navegação abre o CarPlay por
  padrão; a escolha do app (Google Maps, Waze, CarPlay) fica para as configurações da Fase 4.
- **Modificar o APK do ZLINK para trocar a tela de espera: descartado.** Além de proibido
  pelo plano (seção 6), exigiria assinar o app com outra chave, o que obriga a desinstalar o
  original, e o CarPlay depende de um serviço do sistema (`z-link`) e de uma licença em
  `/apd/zlink/` cuja validação não conhecemos: o risco é perder o CarPlay.
- **Tela de espera própria: inviável pelo USB** (teste da 0.2.1-ref, 09/11/2026; detalhes
  em [HARDWARE.md](HARDWARE.md), seção 5). A ideia era o launcher verificar se há um iPhone
  no USB antes de abrir o ZLINK. Resultado:
  - o iPhone nunca aparece na lista USB do Android (a porta parece inverter os papéis
    durante o CarPlay);
  - ao conectar o cabo, o próprio ZLINK vem para a frente e mostra a tela dele com
    "conectando" antes do CarPlay. O launcher não participa desse momento, e trocar essa
    tela exigiria modificar o ZLINK (proibido).
  - Uma tela nossa só cobriria um caso: tocar em CarPlay **sem** o iPhone conectado. A única
    pista para saber se o CarPlay está conectado é a propriedade `rw.cp.link.state`, um
    detalhe interno e não documentado do serviço do fabricante, o que contraria esta
    decisão ("não depender de componentes internos do ZLINK").
  - **Recomendação anterior:** não seguir com a tela de espera própria.
- **Reaberto a pedido do usuário (10/11/2026):** ele quer contornar a tela de espera do
  ZLINK. A versão 0.3.1 grava uma **linha do tempo** da conexão (eventos USB, propriedades
  como `sys.top.activity`, `sys.usb.config` e `rw.cp.link.state`) para descobrir dois
  momentos: quando a conexão começa e quando o CarPlay fica pronto.
  - **Candidata, se os sinais existirem:** uma janela própria **sobreposta** à tela de espera
    do ZLINK durante a conexão, retirada quando o CarPlay estiver pronto. O ZLINK continua
    intacto e na frente, por baixo, então a conexão não é perturbada. Exige a permissão
    "Sobrepor a outros apps", concedida pelo usuário (reversível), e o launcher em execução.
  - **Descartado:** trazer uma tela do launcher para a frente do ZLINK durante a conexão
    (poderia interromper a conexão do CarPlay) e qualquer alteração no ZLINK.
  - **Riscos:** depender de um sinal não documentado do fabricante (conflita com esta
    decisão; exige aprovação do usuário); se o sinal falhar, a sobreposição poderia cobrir
    o CarPlay, por isso teria tempo máximo e sairia com um toque. Nada será prometido antes
    dos dados.
  - **Tempos medidos pelo usuário (10/11/2026):** pelo cabo, 11 a 14 s de tela de espera
    (a primeira conexão é a mais lenta, cerca de 14 s); sem fio, cerca de 16 s. Esse tempo é
    da conexão entre o iPhone e o ZLINK: uma sobreposição só mudaria o que aparece na tela,
    não encurtaria a espera. A linha do tempo da 0.3.1 se perdeu (relatórios com 0 bytes);
    a medição dos sinais será repetida com a 0.3.2, pelo cabo e sem fio.
  - **Medição da 0.3.2 (10/11/2026; detalhes em HARDWARE.md, seção 5):**
    - **Início da conexão pelo cabo: encontrado, e por um sinal público.** O Android avisa
      "aparelho USB conectado" (Apple) ~3 s antes de o ZLINK vir para a frente. Sem fio, o
      primeiro sinal visto foi o ponto de acesso Wi-Fi ligando (propriedade interna); os
      eventos públicos do Bluetooth ainda não foram gravados.
    - **CarPlay pronto: não encontrado.** O ZLINK usa a mesma tela para a espera e para o
      CarPlay, e `rw.cp.link.state` não muda. O único candidato é a sessão de música
      Bluetooth do iPhone sumir (~8,3 s depois do ZLINK, duas amostras), o que depende do
      Bluetooth conectado e do "Acesso a notificações".
    - **Consequência:** hoje, uma sobreposição só poderia sair por tempo fixo ou por esse
      candidato, com risco de cobrir o CarPlay por alguns segundos ou de mostrar a tela do
      ZLINK no fim. Próximo passo: gravar mais candidatos (estados das conexões Bluetooth,
      sem nome nem endereço, e tipos de áudio tocando) antes de qualquer protótipo.
  - **0.4.0:** a gravação passa a registrar as conexões Bluetooth por perfil (A2DP sink,
    AVRCP, HFP do lado carro, enlace ACL; nunca nome nem endereço), os tipos de áudio tocando
    (`AudioPlaybackCallback`: mídia, navegação, chamada…) e o modo de áudio. Nenhuma tela por
    cima do ZLINK nesta etapa.

### D15 — Atalhos para os apps da central (Fase 3)
- Os pacotes vêm do diagnóstico (`launcher/CarApps`). A abertura usa a entrada padrão
  (MAIN/LAUNCHER) com `FLAG_ACTIVITY_NEW_TASK | RESET_TASK_IF_NEEDED`, como um launcher:
  um app já aberto (ex.: CarPlay conectado) volta para a frente no estado em que estava.
- **Nomes de classe nunca são presumidos.** Pacotes com mais de uma entrada
  (`com.autochips.bluetooth`: Bluetooth e BT Música; `com.hcn.AutoMediaPlayer`: Música e
  Vídeo) têm a entrada escolhida pelo nome exibido (`EntrySelection`, com testes).
- Telas do Android (Wi-Fi, Som, Tela, Data e hora) abrem pelas ações públicas de `Settings`.
- Se um app ou tela não existir, o atalho fica **desativado** com a legenda
  "Não encontrado"; os cartões mostram um aviso.
- **App desativado pelo usuário (0.4.1):** `AppLauncher.availability` distingue app ausente de
  app desativado (`getApplicationEnabledSetting`, só leitura). O atalho mostra "Desativado"; os
  atalhos compactos da aba Música somem. É o caso do player original, se o usuário o desativar
  (exceção à seção 9 do plano). O Nyx Car **não** desativa nem ativa apps: isso exige uma
  permissão de sistema (`CHANGE_COMPONENT_ENABLED_STATE`), ADB ou root.

### D16 — Controles de mídia (Fase 3)
Os botões ⏮ ▶ ⏭ enviam teclas de mídia (`AudioManager.dispatchMediaKeyEvent`), como os
botões do volante: o Android entrega o comando à sessão de mídia ativa, sem permissão. O
ícone ▶/⏸ segue `AudioManager.isMusicActive()`, conferido 0,7 s e 2 s depois do toque
(sem polling). Funciona só com players que publicam sessão de mídia. Título e artista reais
dependem de "acesso a notificações" (Fase 4).

- **Resultado na central (0.3.0): não funciona com o player USB**, e a música do player
  para ao sair dele. Hipóteses, não confirmadas: o player não publica sessão de mídia nem
  receptor de botões, e/ou só toca com a própria tela aberta.
- **Investigação (0.3.1, Nyx Diagnóstico → Testes no carro):** o que o player declara ao
  Android (receptor de botões, serviços, sessão), comandos de teste por três caminhos
  padrão (tecla de mídia do sistema, receptor do player, sessão do player) e, se o usuário
  autorizar, a lista de sessões de mídia via "Acesso a notificações".
- **Se o player só toca com a tela dele aberta**, nenhum launcher consegue controlá-lo em
  segundo plano: os botões do cartão passariam a abrir o player, e o controle remoto
  ficaria para o BT Música e o CarPlay (se publicarem sessão).
- **Confirmado pelo usuário (10/11/2026):** com o launcher original acontece o mesmo: a
  música para ao sair do player, e os botões de mídia do launcher original só abrem o
  player. A limitação é do player do fabricante; nenhum launcher contorna isso. O carro não
  tem botões de mídia no volante.
- **Caminhos possíveis (decisão do usuário pendente):**
  - **Player próprio no launcher (recomendado):** toca as músicas do pendrive em segundo
    plano, publica sessão de mídia padrão (o cartão mostra a faixa e os botões funcionam),
    usa o `MediaPlayer` do Android, sem bibliotecas pesadas, e um serviço de reprodução que
    só existe enquanto há música tocando. O player do fabricante continua instalado.
    Riscos: a central pode tratar o áudio de apps comuns de forma diferente do player do
    fabricante (a confirmar primeiro, numa versão mínima); exige a permissão de leitura do
    armazenamento; formatos dependem dos decodificadores da central. Funcionalidade nova,
    fora do plano original: precisa de plano próprio aprovado.
  - **Player de terceiros (ex.: VLC):** imediato, e os botões do cartão devem funcionar se
    ele publicar sessão de mídia (a confirmar na central). Visual diferente da Home, e a
    faixa no cartão exigiria "Acesso a notificações".
  - **Manter o player do fabricante:** os botões do cartão passariam a só abrir o player.
- **Decisão do usuário (10/11/2026): player próprio.** Plano aprovado (etapa 0.4.0); ver D17.
- **0.4.0:** os botões do cartão da Home controlam **o player próprio** diretamente (sem
  teclas de mídia); `system/MediaKeys` foi removido. O player publica uma sessão de mídia
  padrão, então o Android e outros apps também o enxergam.

### D17 — Player próprio das músicas do pendrive (Fase 4, etapa 0.4.0)
Aprovado pelo usuário em 10/11/2026, com duas decisões dele: **ao ligar a central, o player
sempre espera um toque** para retomar; e a navegação é **por pastas** (as músicas dele estão
soltas na raiz do pendrive, que vira a primeira "pasta").

- **Leitura do pendrive** (`player/MusicStorage`): direto no sistema de arquivos, somente
  leitura, sem a biblioteca de mídia do Android (que pode demorar a indexar ou não indexar).
  Pendrives pela lista de volumes do Android (`StorageManager`; no 8.1 o caminho vem da
  função interna `getPath()`, permitida nessa versão) e, se ela não trouxer nenhum, pelas
  pastas de `/storage`. Pastas de sistema e arquivos ocultos (inclusive os `._*` do macOS)
  ficam de fora. Ordem "natural" ("2" antes de "10"), sem peso para maiúsculas e acentos.
- **Lista** (`ui/music`): `ListView` nativa com reciclagem de linhas (só as visíveis existem),
  rolagem rápida e linhas de 64 dp. A lista mostra os nomes dos arquivos; título, artista e
  capa (`MediaMetadataRetriever`) são lidos **só da música atual**.
- **Reprodução** (`player/MusicPlayer`): `MediaPlayer` do Android, fila = músicas da pasta.
  Próxima/anterior mantêm o estado (tocando ou pausado); "anterior" volta ao início da música
  se ela já passou de 3 s. No fim da pasta, volta à primeira e pausa (sem "repetir" na v1).
  Música que não abre (formato sem decodificador na central) é pulada, com aviso.
- **Foco de áudio:** pede o som para música. Se outro app assume (CarPlay, BT Música,
  ligação), pausa e **não** retoma sozinho; em interrupções curtas, retoma; com aviso
  sonoro curto, abaixa o volume.
- **Sessão de mídia padrão** (`MediaSession`), com título, artista, capa e comandos.
- **Primeiro plano só enquanto toca** (`player/PlaybackService`): o Android 8 exige uma
  notificação; ao pausar, o serviço termina. Sem *wake lock*: se a central dormir, a música
  para junto (evita manter a central acordada com o carro desligado).
- **Memória e partida:** posição salva a cada 10 s enquanto toca, ao pausar e ao desligar
  (`ACTION_SHUTDOWN`). Ao abrir o processo, só as preferências são lidas: o pendrive só é
  acessado quando o usuário toca em ▶ (a pasta é relida e a música volta ao ponto salvo).
- **Pendrive removido:** a música para, a posição fica guardada e o player avisa.
- **Diário** (`player/PlayerLog`, arquivo privado, 300 linhas): início do processo (com o
  tempo desde que o sistema ligou, para saber se a central reinicia ou só dorme ao desligar
  o carro), comandos, foco de áudio, erros por formato, pendrive e tela; desde a 0.4.1,
  também os erros que fecham o app (D18). Sem nomes de músicas. Vai para o relatório do PC
  Diagnóstico.
- **Resultado na central (0.4.0, 10/11/2026):** o som sai nos alto-falantes; o CarPlay pausa
  a música; ao ligar o carro, a última música volta pausada. A central não reinicia ao
  desligar o carro: ela dorme e fecha os apps, e o Android reabre o Nyx Car (tela
  inicial) do zero. O player original abre sozinho ao conectar o pendrive (HARDWARE.md).
- **Fora da v1:** aleatório, repetir, artistas/álbuns, busca, listas de reprodução.
- **Riscos (conferidos no teste do carro):** o som de um app comum pode não sair nos
  alto-falantes como o do player do fabricante (a central pode rotear áudio por fonte); formatos
  dependem dos decodificadores da central; o caminho do pendrive no 8.1; o player original
  pode abrir sozinho ao inserir o pendrive; o Rádio pode ignorar o foco de áudio; se a central
  apenas "dormir" ao desligar o carro, a música pode voltar sozinha ao acordar (o diário
  mostrará isso).

### D18 — Erros do app no diário e R8 sem ofuscação (0.4.1)
O relatório da 0.4.0 mostrou o Nyx Car sendo aberto do zero várias vezes, inclusive logo
depois de reconectar o pendrive, sem como saber se foi a central fechando o app ou um erro
nosso (sem ADB não há logcat).
- `system/CrashRecorder`, instalado no `Application.onCreate` (só registra o tratador
  padrão de erros do Java, sem custo na abertura): grava na hora, no diário, o tipo do erro, a
  mensagem e até 3 pontos do nosso código, e depois deixa o Android seguir como sempre.
  **Privacidade:** caminhos de armazenamento (`/storage`, `/mnt`, `/sdcard`, também em URI) são
  trocados por `<arquivo>` até o próximo ":" ou aspas, porque nomes de músicas podem ter espaços.
- No relatório, "Player iniciado" sem um "Erro do app" logo antes indica que quem fechou o
  Nyx Car foi a central.
- **R8 com `-dontobfuscate` e números de linha:** sem ADB, um erro ofuscado não poderia ser
  traduzido de volta. Remoção de código e otimizações continuam; o APK cresce ~33 KB.

### D19 — Nome: Nyx Car (11/11/2026)
O usuário trocou o nome "Premium Car", genérico demais, por **Nyx Car**. Muda tudo o que
aparece: nome do app e do ícone de diagnóstico ("Nyx Diagnóstico"), textos, título e nome do
arquivo do relatório (`nyx-car-diagnostico-*.txt`) e nome do APK
(`nyx-car-launcher-release.apk`).
- **O identificador do app continua `com.premiumcar.launcher`** (pacote e `applicationId`).
  O Android reconhece um app pelo pacote: trocá-lo faria do Nyx Car um **app novo**, instalado
  ao lado do antigo, sem a escolha de tela inicial, sem as permissões (pendrive, acesso a
  notificações) e sem a última música salva. Mantendo o pacote, a 0.4.3 instala por cima e
  tudo continua. O pacote não aparece para quem usa a central.
- Também ficam os prefixos internos `pc_` (recursos) e `Pc` (classes), usados em centenas de
  lugares e invisíveis na tela. Os nomes internos com o nome antigo por extenso
  (`NyxCarApplication`, `NyxCarTheme`, `Theme.NyxCar`, sessão de mídia) foram trocados.
- O ícone do app é uma marca abstrata (mostrador), sem letras: não muda.
- O CHANGELOG mantém o nome antigo nas versões até a 0.4.2, como registro; os relatórios já
  salvos mantêm o nome de arquivo antigo.

### D11 — Assinatura
- Fase 1: o APK release é assinado com a chave de **debug** desta máquina
  (`~/.android/debug.keystore`), apenas para testes.
- **Decisão do usuário (11/11/2026):** continuar com essa chave, sem chave própria e sem
  cópias de segurança, para não reinstalar o launcher. Trocar a chave obrigaria a desinstalar
  a versão anterior (o Android não aceita atualizar um app assinado com outra chave) e a
  escolher de novo a tela inicial e as permissões.
- **Consequência aceita:** o código está no GitHub, mas a chave fica só neste PC. Um APK
  compilado em outro PC não atualiza o instalado: seria preciso desinstalar e instalar de novo.

### D12 — Medir sempre com build release
Builds de debug do Compose são muito mais lentos (sem R8, sem otimizações do ART) e
distorcem as medições. Todo teste na central usa o build release (R8 ligado).

### D13 — Textos em pt-BR
O painel de diagnóstico usa textos fixos no código, por ser uma ferramenta interna. A
interface do launcher (Fase 3 em diante) usará `strings.xml`.

### D14 — Fluxo de trabalho sem ADB e sem emulador
A central não oferece ADB (opções do desenvolvedor bloqueadas pelo fabricante), e o
emulador não funciona no PC de desenvolvimento. Para manter cada ida ao carro produtiva:
- **Aprovação visual antes da viagem:** capturas de tela geradas na JVM
  (Robolectric/Roborazzi) na resolução exata da central (1280 × 720, 160 dpi).
- **Medição de dentro do app:** o próprio APK mede (abertura, memória, quadros) e grava
  relatórios em arquivo, que voltam pelo pendrive.
- **Agrupar mudanças:** cada APK levado ao carro reúne o máximo de itens a validar,
  com um roteiro de teste curto.

## 3. Dependências

| Biblioteca | Motivo |
|---|---|
| `androidx.activity:activity-compose` | Ponte Activity ↔ Compose, resultados de Activity |
| `androidx.core:core-ktx` | Compatibilidade de insets e leitura de atributos de componentes |
| `androidx.compose` (BOM): `ui`, `foundation`, `material3` | **Somente o painel de diagnóstico** (temporário). A Home e o Design System usam Views nativas, sem bibliotecas de interface |
| `junit` (somente testes) | Testes unitários |
| `robolectric`, `roborazzi`, `compose ui-test-junit4` (somente testes) | Abrir a interface real na JVM e salvar capturas de tela. Necessárias porque o emulador não funciona neste PC e a central não tem ADB: são o único jeito de ver a interface antes de levar ao carro. Não entram no APK. |

Nada além disso. Qualquer nova dependência precisa de justificativa registrada aqui.

## 4. Ferramentas

| Ferramenta | Versão |
|---|---|
| Android Gradle Plugin | 9.5.1 (Kotlin embutido no AGP 9; o plugin `kotlin-android` não é mais usado) |
| Gradle | 9.9.0 (wrapper com SHA-256 fixado) |
| Kotlin / plugin do compilador Compose | 2.4.21 |
| Compose BOM | 2026.09.00 |
| JDK do build | 21 (Temurin), selecionado por `gradle/gradle-daemon-jvm.properties` |
| Android SDK | Platform 37, Build-Tools 36.0.0 |

## 5. Riscos conhecidos

| Risco | Impacto | Mitigação |
|---|---|---|
| ~~Versão real do Android bem menor que 13~~ | — | **Confirmado: API 27 (8.1).** `minSdk 23`, verificações de versão |
| CPU/GPU fracas (confirmado) | Interface travando | Views nativas; animações mínimas; nada de efeitos de GPU |
| Teto de ~26 quadros/s (confirmado) | Rolagens e animações nunca ficam "lisas" como num celular | Interface majoritariamente estática; poucas rolagens; transições curtas ou nenhuma |
| ~~Densidade de tela desconhecida~~ | — | **Medida: 160 dpi, área útil 1280 × 655 dp** |
| ~~Modo low-RAM bloquear acesso a notificações~~ | — | **Não é low-RAM.** Viabilidade do "tocando agora" será testada na Fase 4 |
| ~~Firmware forçar o launcher original no botão Home ou ao ligar~~ | — | **Não acontece (0.4.0):** o Nyx Car continua como tela inicial depois de desligar e ligar o carro e depois de uma reinicialização completa (11/11/2026). Recuperação a um toque e no BUILD.md, seção 8 |
| ~~Som de app comum não sair nos alto-falantes~~ | — | **Sai normalmente (0.4.0)** |
| ~~Central só "dormir" ao desligar o carro~~ | — | **Ela dorme, mas fecha os apps:** a música volta pausada, como decidido (0.4.0) |
| ~~Player original abre sozinho ao conectar o pendrive~~ | — | **Resolvido pelo usuário (11/11/2026):** "Reprodução automática" desligada em Configurações do Veículo → Geral. O atalho dele saiu do launcher na 0.4.2 |
| Deslize trocar de aba sem querer (solavanco) | Aba errada com o carro em movimento | Só arrasto claramente horizontal e de 154 px (ou gesto rápido). Aprovado pelo usuário no teste da 0.4.1 (11/11/2026); limites ajustáveis se aparecer problema |
| Relógio voltar para a data do firmware numa partida do zero (visto em 11/11/2026) | Hora e data erradas na Home (e no diário) até a central acertar o relógio (~3 h nesse dia) | O launcher só mostra a hora do sistema. A 0.4.3 informa no relatório se a hora automática está ligada; a correção, se precisar, é um ajuste comum de data e hora |
| Perda da chave de assinatura (só neste PC) | Atualizar exige desinstalar e configurar de novo | Aceito pelo usuário (11/11/2026, D11): sem cópia da chave; o código está no GitHub |
| ~~Toque com conversão 1085×600 → 1280×720~~ | — | **Teste de precisão aprovado na central** |

## 6. Pendências técnicas

- **Métrica de "quadros atrasados":** hoje é calculada contra os 60 Hz informados pelo
  sistema. Nesta central a cadência real é de ~38,7 ms, o que faz a métrica superestimar
  os atrasos. Corrigir para usar a cadência observada antes das medições da Fase 5.
