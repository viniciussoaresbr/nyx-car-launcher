# Hardware de destino — diagnóstico

Medições feitas na central pelo APK de diagnóstico `0.1.0-diag` em 09/11/2026.
Relatório completo: [`diagnostico/premium-car-diagnostico-20261109-185052.txt`](diagnostico/premium-car-diagnostico-20261109-185052.txt).

Itens marcados como **hipótese** ainda não foram comprovados. Todo o resto foi medido.

## 1. Resumo

| Item | Medido | Consequência no projeto |
|---|---|---|
| Android real | **8.1.0 (API 27)**, patch de segurança 2018-07-05. Para os apps, o sistema se apresenta como "13" | `minSdk 23` está correto; APIs do Android 9+ só com verificação de versão |
| Kernel | 3.18.79+ | — |
| CPU | AutoChips AC8227L: **4× Cortex-A7, máx. 1118 MHz, somente 32 bits** (`armeabi-v7a`) | CPU muito modesta: menos trabalho na thread principal |
| GPU | **ARM Mali-450 MP**, OpenGL ES 2.0, sem Vulkan | Evitar desfoque, sombras e transparências grandes |
| RAM | 1,90 GB + 1 GB de zRAM; heap por app de 128 MB (até 256 MB). **Não** é Android Go | Memória confortável para um launcher |
| Compilação de apps | `pm.dexopt.install=quicken`: sem compilação antecipada na instalação; isso só acontece numa otimização em segundo plano, sem data prevista | O código do app começa interpretado/JIT; o código do sistema (Views) já vem compilado |
| Tela | 1280 × 720 px a **160 dpi (mdpi): 1 dp = 1 px** | Medidas em dp equivalem a pixels |
| Área útil | **1280 × 655 dp**. Barra do sistema de 65 px no topo, sem barra inferior | Tela de trabalho do Design System |
| Cadência real de quadros | **~25,8 quadros/s** (intervalo constante de 38,7 ms), embora o sistema informe 60 Hz | Teto de fluidez: nenhuma animação ou rolagem passa de ~26 fps |
| Toque | GT9xx mapeado para 0–1279 × 0–719, 5+ pontos; teste de precisão aprovado na central | Alvos podem seguir o padrão de 48–64 dp |
| Armazenamento interno | 25,3 GB (22,9 GB livres) | — |

## 2. Hipóteses da Fase 1 → resultado

| Hipótese | Resultado |
|---|---|
| "Android 13" é só um rótulo | **Confirmado.** A API real é 27 (Android 8.1). As propriedades internas dizem `8.1.0`, mas o fabricante alterou o que os apps leem em `Build.VERSION.RELEASE` para "13" |
| Processador AC8227L quad-core Cortex-A7 | **Confirmado.** Frequência máxima de 1118 MHz, abaixo dos ~1,3 GHz citados no mercado |
| Densidade de tela desconhecida | **160 dpi.** Área útil de 1280 × 655 dp |
| Modo low-RAM poderia bloquear o acesso a notificações | **Não é low-RAM**, e a tela "Acesso a notificações" existe. O cartão de música com a faixa tocando é viável em princípio (Fase 4) |

## 3. Apps e pacotes

| Função | App | Pacote | Observação |
|---|---|---|---|
| **Launcher original** | Tela de início 3 | `com.android.launcher3` (`.Launcher`) | Visual aplicado pelo tema `com.hcn.auto.theme.xt88`. **Referência para recuperação** |
| Trocar a tela inicial | Apps padrão | `com.android.settings` | Disponível: o caminho de recuperação 2 do BUILD.md existe |
| **ZLINK** | ZLINK5 v5.4.23 | `com.zjinnova.zlink` | App comum (não de sistema). Há também um serviço de sistema `z-link` rodando e uma licença em `/apd/zlink/` (`persist.sys.lic.dir`): fora do escopo, não tocar. No launcher, aparece como **CarPlay**. Abre sozinho ao conectar o iPhone (ver seção 5) |
| **Bluetooth** | Bluetooth, BT Música | `com.autochips.bluetooth` | A tela de Bluetooth padrão do Android **não existe** (`ro.atc.disable_android_bt=1`): o atalho deve abrir este app |
| **Música** | Música | `com.hcn.AutoMediaPlayer` | Não há "player padrão" registrado: o atalho abre este app direto |
| Rádio | Rádio | `com.hcn.autoradio` | — |
| Vídeo | Vídeo | `com.hcn.AutoMediaPlayer` | — |
| Navegação | Maps | `com.google.android.apps.maps` | Também atende links `geo:` |
| Configurações do Android | Configurar | `com.android.settings` | Wi-Fi, Som, Tela e Data/hora abrem direto |
| Configurações do veículo | Configurações Do Veículo | `com.hcn.autosetting` | — |
| Informações do carro | Informaçãodocarro | `com.hcn.autocan` | — |
| Câmera / AV | Av In | `com.auto.hcamera` | — |
| Comandos de volante | Comandos de volante | `com.hcn.swc` | — |
| Equalizador | DSP | `com.hcn.autoeq` | — |
| Arquivos | Gerenciador de arquivos | `com.mediatek.filemanager` | — |
| Discador | — | indisponível | As chamadas ficam no app Bluetooth |

Serviços de mídia (`MediaBrowserService`) disponíveis para o cartão de música: áudio
Bluetooth do celular (`com.android.bluetooth`, A2DP), YouTube e Podcasts. O player
`com.hcn.AutoMediaPlayer` **não** expõe esse serviço; se ele publica uma sessão de mídia,
será verificado na Fase 4.

## 4. Desempenho — linha de base

Teste de fluidez: grade de 300 ícones rolada automaticamente, 3 execuções de cada motor,
todas a frio. Medianas:

| | Compose | Views nativas |
|---|---|---|
| Início do processo → primeiro quadro | 561 ms | **285 ms** |
| Quadros por segundo durante a rolagem | 9,6 | **26,9** (o teto do aparelho) |
| Intervalo entre quadros (p90) | 166 ms | **38,8 ms** |
| Pior travada (máx.) | 843–1617 ms | **38,9 ms** |
| Memória do processo (PSS) | 23,4 MB | **12,4 MB** |

**Como ler:** todos os intervalos das Views ficaram em 38,7 ms, ou seja, elas entregaram
um quadro a cada ciclo possível do aparelho (~25,8/s). O Compose ficou em ~37% desse teto,
com travadas de quase 1 s.

> **Correção da métrica de "quadros atrasados":** o relatório mostra 93% para as Views
> porque o cálculo considerou os 60 Hz informados pelo sistema. Medida contra a cadência
> real (38,7 ms), a taxa das Views é de praticamente **0%**. A métrica será corrigida para
> usar a cadência observada (ver ARCHITECTURE.md, pendências).

**Fatores que pesam contra o Compose aqui** (explicação provável, não medida
isoladamente): o código do Compose fica dentro do app e, com `dexopt=quicken`, roda
interpretado/JIT. Já o código das Views faz parte do sistema e vem pré-compilado. A
otimização em segundo plano poderia melhorar o Compose com o tempo, mas não há como
forçá-la nem confirmá-la sem ADB. E a diferença é grande demais (2,8× na fluidez, travadas
de centenas de ms) para depender disso.

Outros números:

- Painel de diagnóstico (Compose), primeira abertura após instalar: 1038 ms até o
  primeiro quadro, 17,1 MB de PSS.
- **Home de referência em Views** (0.2.0-ref e 0.2.1-ref, aberturas a frio): 579 e
  590 ms até o primeiro quadro, **10,0–10,1 MB de PSS**. É a referência para a Fase 3.
- O driver gráfico não informa memória de gráficos (sempre 0,0 MB). Essa parcela não é
  mensurável neste aparelho.

## 5. Outras descobertas

- **Teste da versão 0.4.0 na central** (10/11/2026; relato do usuário e relatório
  `diagnostico/premium-car-diagnostico-20261110-203535.txt`):
  - O som do **player próprio** sai nos alto-falantes; o **CarPlay pausa** a música; ao
    ligar o carro, a última música volta **pausada** e só toca com um toque.
  - O Nyx Car **continua como tela inicial** depois de desligar e ligar o carro: o
    firmware não traz o launcher original de volta. **Confirmado também numa reinicialização
    completa** feita pelo usuário em 11/11/2026: a central abriu direto no Nyx Car.
  - **Ao desligar o carro, a central não reinicia: ela dorme.** No diário, o sistema estava
    ligado havia ~28 h, ~25 h delas dormindo. Mas ela **fecha os apps**: o Nyx Car é
    aberto do zero segundos depois (o Android reabre a tela inicial), já com a música
    pausada. O pendrive é desmontado ao desligar (`media_eject`, `media_bad_removal`) e
    montado ~3 s depois de a tela acender.
  - O Nyx Car também foi aberto do zero duas vezes logo depois de o pendrive ser
    reconectado com o carro ligado (20:11 e 20:30). **Causa desconhecida:** a central
    fechando apps quando o player original abre, ou um erro do app. A 0.4.1 registra os
    erros do app no diário para separar os dois casos.
  - O **player original abre sozinho ao conectar o pendrive**. Não existe "player de música
    padrão" para escolher (`Player de música padrão: indisponível`).
    **Resolvido em 11/11/2026:** quem controla isso é a opção **Reprodução automática** em
    Configurações do Veículo → **Geral** (ajuste comum, não de fábrica). O usuário a desligou
    e o player não abre mais. **Hipótese:** é a mesma `persist.sys.autoplay` (era `1`); o
    próximo relatório deve mostrar `0`.
  - **Home "pulando" ao voltar do ZLINK** (relato do usuário, 11/11/2026): ao tocar em Home
    saindo do ZLINK, a Home descia do topo; com outros apps, não. **Hipótese** (coerente com
    o sintoma): o ZLINK roda em tela cheia e esconde a barra de status de 65 px; a Home era
    desenhada sem ela e descia quando a barra voltava. Corrigido na 0.4.2 reservando a
    altura estável da barra. **Confirmado no carro em 11/11/2026:** a Home não desce mais.
  - **BT Música e Música USB são apps diferentes**, embora pareçam iguais na tela:
    `com.autochips.bluetooth` (BtMusicActivity) e `com.hcn.AutoMediaPlayer`. A tela de
    "Startup application" também os lista separados.
  - As aberturas da Home de 16 a 24 s do relatório não eram reais: eram processos reabertos
    pelo Android em segundo plano, com a tela criada bem depois. A 0.4.1 mede esses casos à
    parte.

- **Teste da 0.4.1 e da 0.4.2 no carro (11/11/2026)**, relatório
  `diagnostico/premium-car-diagnostico-20261111-161151.txt`:
  - Deslize entre abas aprovado pelo usuário; "Música USB" sumiu das duas abas; a Home não
    desce mais ao voltar do CarPlay.
  - `persist.sys.autoplay` passou de `1` para **`0`**: confirma que a opção **Reprodução
    automática** (Configurações do Veículo → Geral) é essa propriedade.
  - Firmware igual em todos os relatórios desde 09/11 (`ro.build.date` 17/04/2026,
    `ro.fota.version` `WJ_2026.04.17.10`): nenhuma atualização aconteceu.
  - Nenhum "Erro do app" no diário.
  - **O relógio volta para a data do firmware numa partida do zero.** Depois de 11/11 12:45,
    o diário mostra a central partindo do zero (`sistema ligado há 0 min`,
    `ro.boot.bootreason` = `power_key`) com o relógio em **16/04/2026 21:01**, perto da data
    de compilação do firmware (17/04/2026, horário da China). Ficou assim por cerca de 3
    horas, até ser acertado (o relatório saiu às 16:11 de 11/11, com o Wi-Fi conectado).
    Três aberturas do Nyx Car aparecem no mesmo segundo (21:01:22): como o relógio volta
    sempre para o mesmo ponto, são provavelmente **três partidas seguidas**. Nas sonecas
    normais (desligar o carro) o relógio não se perde. **Hipótese:** a hora só é acertada
    pela rede; a 0.4.3 mostra no relatório se "Data e hora automáticas" está ligada.
    `persist.sys.gps.time` = `0` sugere uma opção do fabricante para acertar a hora pelo
    GPS, desligada; não se sabe onde ela fica.

- **Teste da 0.4.3 no carro (11/11/2026)**, relatório
  `diagnostico/nyx-car-diagnostico-20261111-171950.txt`:
  - Instalada por cima com o novo nome: o **Nyx Car continuou como tela inicial**
    (`Nyx Car como tela inicial: SIM (padrão)`), como previsto por manter o pacote.
  - Play Store: `com.android.vending/.AssetBrowserActivity`.
  - **Data e hora automáticas: ligadas** (e o fuso também). Conclusão: a central não guarda
    a hora numa partida do zero e só a acerta pela rede (Wi-Fi). Sem internet, o relógio da
    Home fica na data do firmware até a central se conectar. Não há o que o launcher faça
    sem alterar o sistema; o relatório avisa quando isso acontecer.
  - Home: mediana de **790 ms** na abertura a frio e ~14 MB de PSS. Nenhum erro do app.

- **Teste da 0.4.4 no carro (11/11/2026)**, relatório
  `diagnostico/nyx-car-diagnostico-20261111-192835.txt`:
  - "Todos" lista os **20 apps** previstos, todos reconhecidos (nenhum com ícone genérico). Os
    nomes de sistema batem com os do relatório de 11/11 (ex.: "Configurar", "DSP",
    "Informaçãodocarro", "ZLINK5").
  - Escondidos de "Todos": Música (player original), atualização, Atualização de Rede Can,
    Atualização Mcu, a proteção de tela sem nome (`com.hcn.screensaver`), Theme e o Nyx Car.
  - Home: mediana de **623 ms** na abertura a frio (~12,6 MB de PSS). Nenhum erro do app.

- **Teste da 0.4.6 no carro (12/11/2026)**, relatório
  `diagnostico/nyx-car-diagnostico-20261112-172549.txt`: **a volta automática funciona.**
  - 5 voltas ao Nyx Car com o ZLINK na tela de espera depois do fim do CarPlay: 3 tirando o
    cabo e 2 no sem fio, desligando o Bluetooth do iPhone. Nos dois casos o primeiro sinal é o
    Bluetooth da central religando; pelo cabo, o estado USB cai junto ou logo depois.
  - Tempo: o Nyx Car volta ~2,5 s depois de o Bluetooth religar (2 s de espera contra mau
    contato + leitura da tela). O usuário percebeu ~2 s.
  - Sem volta, como previsto: com as Configurações do Veículo na tela
    (`com.hcn.autosetting`) e com o cabo tirado antes de o CarPlay ficar pronto, com o Nyx Car
    na tela.
  - O tráfego do vídeo voltou a subir para ~1,1 a 1,2 MB/s cerca de 1,2 s depois de o
    Bluetooth desligar e caiu em 1 a 2 s, mesmo com o ZLINK na tela (a tela parada do CarPlay
    manda pouco vídeo): para o B1, vale a **primeira subida**.

- **Teste da 0.4.5 no carro (12/11/2026)**, relatório
  `diagnostico/nyx-car-diagnostico-20261112-164448.txt`: 5 gravações pelo cabo e 2 sem fio.
  Tempos contados do momento em que o iPhone aparece no USB:

  | Cabo | ZLINK na frente | Bluetooth da central desligando | Tráfego ≥ 1 MB/s (vídeo) | Usuário sai do ZLINK |
  |---|---|---|---|---|
  | 1 | +2,9 s | +8,6 s | +10,1 s | +12,2 s |
  | 2 | +2,9 s | +8,5 s | +9,8 s | +12,0 s |
  | 3 | +3,0 s | +8,6 s | +10,2 s (294 KB/s em +8,9 s) | +11,8 s |
  | 4 | +3,4 s | +8,7 s | +10,3 s | +13,2 s |
  | 5 | +2,9 s | +8,4 s | +9,7 s | +12,9 s |

  - **Tráfego de rede:** os contadores por interface (`/sys/class/net`) não são legíveis por
    apps nesta central; só o total do Android (`TrafficStats`). Mesmo assim o vídeo aparece
    claramente: de ~0 para **1,2 a 1,4 MB/s recebidos** (e 0,6 a 0,9 MB/s enviados), sempre
    ~1,3 s depois de o Bluetooth desligar e ~2 s antes de o usuário sair do ZLINK. Com o ZLINK
    fora da tela, cai para ~250 KB/s (o vídeo para). É o melhor candidato a "CarPlay pronto".
    Leitura a cada ~1 s: precisão de ~1 s.
  - **Sem fio:** o tráfego subiu bem menos (247 a 428 KB/s); o sinal do vídeo não ficou claro.
  - **Fim do CarPlay (cabo tirado):** chegam os dois sinais, primeiro o **Bluetooth da central
    religando** e ~0,5 s depois o **estado USB caindo** (`connected` some). Em todas as
    gravações o usuário já tinha voltado ao Nyx Diagnóstico antes de tirar o cabo, então a
    volta automática corretamente não agiu ("na tela estava com.premiumcar.launcher").
  - **Mau contato:** no diário, um cabo posto de volta logo fez o iPhone reaparecer no USB no
    mesmo segundo em que a sessão foi dada por encerrada. Corrigido na 0.4.6 (espera de 2 s,
    cancelada quando o iPhone reaparece).
  - A marca do botão Home não foi usada (o usuário voltou ao diagnóstico pelo Voltar).
  - **Comportamento do ZLINK ao tirar o cabo** (relato do usuário, 12/11/2026): com o CarPlay
    pronto na tela, tirar o cabo faz o ZLINK **voltar para a tela de espera e ficar nela**; só
    sai tocando no botão de voltar, no canto superior esquerdo da tela dele. É o caso que a
    volta automática (0.4.5) resolve: o ZLINK continua na tela, então o Nyx Car volta para a
    frente.
  - **"Pulo" no Nyx Diagnóstico** ao voltar do ZLINK direto para ele (relato do usuário):
    corrigido na 0.4.6.

- **Conexão do CarPlay pelo cabo: as 5 gravações juntas (análise de 11/11/2026, 0.3.2 e
  0.4.0)**, contando do momento em que o iPhone aparece no USB:
  - o iPhone some da lista USB em 0,6 a 1,2 s e a central vira "aparelho" (estado USB
    `connected` e `configured`) em 1,8 a 2,3 s;
  - **o ZLINK abre sozinho em 2,7 a 3,4 s** (todas as vezes, com o Nyx Diagnóstico na tela);
  - a central desliga o próprio Bluetooth em **8,3 a 8,6 s** (as duas gravações da 0.4.0, que
    registram o Bluetooth) e também no CarPlay sem fio;
  - o usuário voltou ao app em **11,4 a 11,8 s**, logo depois de o CarPlay aparecer (pedido da
    gravação); coerente com o cronômetro dele (~11 s);
  - **nenhuma propriedade do sistema** muda quando o CarPlay aparece (a gravação compara todas
    a cada segundo), nem os tipos de áudio tocando;
  - **nenhuma gravação pegou uma desconexão** (todas terminaram com o iPhone conectado): os
    sinais do fim do CarPlay ainda são hipóteses (estado USB caindo, Bluetooth religando).
- **Medição da conexão do CarPlay (0.3.2, 10/11/2026)**, relatório
  `diagnostico/premium-car-diagnostico-20261110-165729.txt`: 6 gravações, 3 pelo cabo e 3
  sem fio (numa delas nada mudou em 17 s). Tempos contados a partir do primeiro sinal de
  cada conexão:

  | Conexão | Primeiro sinal | ZLINK vem para a frente | Sessão de música Bluetooth do iPhone some |
  |---|---|---|---|
  | Cabo 1 | iPhone na lista USB (t+6,9 s) | +3,4 s | +11,7 s |
  | Cabo 2 | iPhone na lista USB (t+0,3 s) | +2,7 s | até +11,4 s (junto com a volta ao app) |
  | Cabo 3 | iPhone na lista USB (t+1,9 s) | +3,3 s | até +11,8 s (junto com a volta ao app) |
  | Sem fio 1 | ponto de acesso Wi-Fi ligado (`init.svc.hostapd`, t+2,7 s) | +3,7 s | +12,0 s |
  | Sem fio 2 | sessão Bluetooth do iPhone aparece (t+10,1 s) | +1,5 s | até +6,6 s (junto com a volta ao app) |

  - **Pelo cabo, o iPhone aparece, sim, na lista USB do Android, mas só por ~1 s**
    (`ACTION_USB_DEVICE_ATTACHED`, fabricante Apple `05ac`, produto `12a8`, 12 interfaces).
    Depois some, e a porta passa a funcionar como aparelho (`sys.usb.config` `mtp → none`;
    estado USB "connected, configured"), o que confirma a inversão de papéis. O teste da
    0.2.1 não o viu porque lia a lista só de vez em quando.
  - O aviso de USB chega **~3 s antes** de o ZLINK vir para a frente: é um sinal público e
    documentado do Android para o **início** da conexão pelo cabo.
  - O ZLINK usa a **mesma tela** (`features.main.MainActivity`) para a espera e para o
    CarPlay: `sys.top.activity` não marca o momento em que o CarPlay fica pronto.
  - `rw.cp.link.state` já valia `1` antes de conectar, em todas as gravações: **não**
    indica uma conexão ativa (hipótese da 0.2.1 descartada). `sys.usb.configfs` ficou em `2`.
  - Candidato a sinal de **CarPlay pronto**: a sessão de música Bluetooth do iPhone
    (`com.android.bluetooth`) some cerca de 8,3 s depois de o ZLINK vir para a frente (nas
    duas medições limpas). Coerente com o tempo contado pelo usuário, mas são só duas
    amostras, e o sinal depende do Bluetooth do iPhone conectado. Não confirmado.
- **Indicador de Bluetooth da Home:** o relatório mostra "ligado (indicador visível)", o
  mesmo dado que a Home usa.
- **Sessões de mídia** (acesso concedido pelo usuário): o BT Música publica sessão com
  título, artista e comandos (`com.android.bluetooth`). O player USB não publica sessão,
  embora declare um receptor de botões de mídia (`MusicIntentReceiver`).
- **Teste da versão 0.3.1 na central** (10/11/2026, relatado pelo usuário; os relatórios
  chegaram com 0 bytes, ver CHANGELOG 0.3.2):
  - **Player USB:** com o **launcher original** acontece o mesmo que com o nosso: ao sair do
    player, a música para, e os botões ⏮ ▶ ⏭ do launcher original só **abrem o player**.
    Conclusão: a limitação é do player do fabricante, que não toca nem aceita comandos fora
    da própria tela.
  - O carro **não tem botões de mídia no volante**.
  - **CarPlay, tempo da tela de espera do ZLINK até o CarPlay aparecer:** pelo cabo, de 11 a
    14 s; a primeira conexão é a mais lenta (cerca de 14 s). Sem fio, cerca de 16 s.
- **Teste da versão 0.3.0 na central** (10/11/2026, relatado pelo usuário):
  - Todos os atalhos abrem o app certo, inclusive nos pacotes com dois ícones
    (Bluetooth × BT Música; Música USB × Vídeo).
  - **Player USB** (`com.hcn.AutoMediaPlayer`): tocar no cartão abre o player, mas os botões
    ⏮ ▶ ⏭ do launcher (teclas de mídia do Android) **não têm efeito**. Ao sair do player,
    **a música para**. Ainda não se sabe se o player para sozinho ao perder a tela ou se
    isso depende de como se sai dele (Voltar × Home); a versão 0.3.1 investiga.
  - **CarPlay:** o atalho abre o ZLINK. Ao conectar o iPhone pelo cabo, a tela de espera do
    ZLINK aparece por alguns segundos antes do CarPlay.
  - Indicador de Bluetooth: verificação pendente.
- **ADB via Wi-Fi: indisponível dentro das regras do projeto** (testado em 09/11/2026).
  O sistema tem `service.adb.tcp.port=8555`, mas o adbd só liga com a "Depuração USB"
  ativada, e o fabricante bloqueou o acesso a ela:
  - A central está na rede de casa (`192.168.1.26`) e responde a ping a partir do PC.
    As portas 8555 e 5555 estão fechadas.
  - Tocar 7 vezes em "Número da versão" não ativa as opções do desenvolvedor.
  - Abrir a tela de opções do desenvolvedor diretamente (Nyx Car 0.1.1-diag) é
    recusado pelo próprio Android com o aviso **"Ative as opções de desenvolvedor"**.
  - Ativar exigiria gravar uma configuração protegida do sistema, o que um app comum não
    pode fazer sem ADB. Em centrais desse tipo, a depuração às vezes fica no **menu de
    fábrica** do fabricante (protegido por senha), mas o plano proíbe alterar
    configurações de fábrica (seção 9). Esse caminho não foi seguido.
  - **Conclusão:** o fluxo de trabalho continua por pendrive (ver ARCHITECTURE.md, D14).
- **CarPlay pelo cabo** (teste de 09/11/2026, versão 0.2.1-ref):
  - Ao conectar o iPhone pela primeira vez, o **ZLINK abre sozinho**, mostra a própria tela
    de espera com "conectando" por alguns segundos e depois entra no CarPlay. Quem traz o
    ZLINK para a frente é o próprio sistema do fabricante, não o launcher.
  - O iPhone **nunca apareceu** na lista de aparelhos USB do Android: 9 leituras, todas
    com 0 aparelhos, antes e depois de conectar e com o CarPlay aberto, embora a central
    declare suporte a modo host USB.
    **Corrigido em 10/11/2026:** ele aparece por ~1 s logo ao conectar (ver a medição da 0.3.2).
  - Indício do motivo: com o CarPlay ativo, `sys.usb.config` mudou de `mtp` para `none` e
    `sys.usb.configfs` de `0` para `2`. **Hipótese:** durante o CarPlay, a porta USB da
    central funciona como *aparelho* e o iPhone como *host* (inversão de papéis exigida
    pelo CarPlay); por isso o Android não o lista.
  - Com o CarPlay ativo, surgiu a propriedade `rw.cp.link.state: 1`, ausente no relatório
    anterior. Provavelmente é o estado da conexão do CarPlay, gravado pelo serviço do
    fabricante. O valor depois de desconectar não foi medido.
    **Descartado em 10/11/2026:** o valor fica em `1` mesmo sem o iPhone conectado.
  - `init.svc.hostapd` passou a `running`: o ponto de acesso Wi-Fi da central foi ligado.
    **Hipótese:** preparação para o CarPlay sem fio.
  - Não se sabe se um pendrive apareceria na lista USB, porque nenhum estava conectado
    durante o teste.
- `ro.sf.hwrotation=90`: o painel é fisicamente vertical e o sistema gira a imagem.
  **Hipótese:** pode contribuir para o teto de ~26 quadros/s.
- O sistema declara suporte a tela dividida e picture-in-picture.

## 6. Calibração (avaliação na central, 09/11/2026)

- Tons provisórios (fundo, superfícies, borda): **aprovados**.
- Tamanhos de fonte: **aprovados**.
- Cor de destaque: **aprovada**. Considera-se a proposta `#2CC8D8`, usada em todo o painel.
- Toque: **preciso** no teste de alvos.

## 7. Dados de origem (capturas de tela das configurações)

Antes do APK, foram estas informações das telas de configuração que levantaram as
hipóteses acima:

| Item | Valor exibido |
|---|---|
| Plataforma | `ac8227l` |
| CAN / modelo | `Raise->Chevrolet/BUICK->ONIX_R01`, versão `146.0.1.20251110` |
| Compilação / MCU | `WJ_2025.10.28_12_2224` / `CVD1810-WJ_24.02.20_339` |
| Painel / touch | `1280*720_YT090IBNK12018-A12` / `GT9xx_1085x600` |
| Kernel | `3.18.79+ (publisher28@ubuntu128 #2) Tue Oct 28 12:59:23 CST 2025` |

O decodificador CAN e o MCU são geridos por apps do fabricante e ficam **fora do escopo**.

### Menu de fábrica (fotos do usuário, 11/11/2026; só visto, nada alterado)

Em Configurações do Veículo → **Fábrica**. Alterar esse menu é proibido pela seção 9 do plano;
o registro serve para entender o comportamento da central. O código de ativação exibido é
um identificador da central e **não** é registrado aqui.

| Opção | Valor | O que indica para o projeto |
|---|---|---|
| **Quick Boot** | "Cold start after 3 days" | Confirma o diário da 0.4.0: ao desligar o carro, a central **dorme** (e fecha os apps); depois de 3 dias desligada, faz uma partida completa |
| **The navigation bar slides out sideways** | OFF | Barra lateral por gesto. Ligada, competiria com o deslize entre abas (0.4.1) |
| Startup navigator / Startup Navigation | Desligar / submenu | Abrir um app de navegação ao ligar |
| Startup application | **Empty** (opções: Empty, Rádio, Música `com.hcn.AutoMediaPlayer`, BT Música) | App aberto ao ligar a central. "Empty" é o certo: nada abre por cima do Nyx Car. **Não** controla a abertura do player ao conectar o pendrive |
| USB Setting | usb port0/port1 protocol: `usb2_0` · usb mode: **host** · Engineer mode · USB0/USB1 DVR Enhancement: marcados | Nada sobre abrir o player ao conectar o pendrive. "host" é o que permite pendrive e CarPlay pelo cabo |
| USB Setting → **Engineer mode** | abre o **EngineerMode** da MediaTek (abas Telephony, Connectivity…) com o aviso "EM is an advanced debug mode… please entry the Developer options at settings!" | Exige as opções do desenvolvedor, que o fabricante bloqueou (seção 5): **não dá acesso ao ADB**. Nada foi alterado |
| Acc Delay | 300 ms | Atraso ao detectar a chave (ACC) |
| Split Screen | On | Tela dividida (o sistema declara suporte) |
| Status bar panel mode · Button mirrored to the right · Power supply voltage value display · Real-time network speed | desligados | Barra de status e botões do sistema |
| Front Switch | OFF | — |
| No eq is displayed on the radio | On | — |
| Reverse detection · Audio AMP (100) · Rádio Ant (Padrão) · Av Out (On) · AV Output Range · Ajuste de tela · PWM regulation · LED adjust entrance enable (On) | — | Integração com o carro, som e imagem |
| Cartype Setting · Configurações relacionadas ao CAN · Calibração de toque · Botão físico de toque · Remote Control Learning · External Devices (None) · IR Devices (Encoder) | — | Integração com o carro e comandos |
| Voice Assistant (TXZ) · BT Pairing Mode (Numeric Comparison) · BT Sync Volume Mode (sync volume) · reverse contact name (desligado) | — | Bluetooth e voz |
| Logo de abertura · Configurações de logotipo dedicadas · regain the default boot animation · Gerar código QR · Activation code | — | Logotipo de partida e licença |

## 8. Cópia da pasta `Android` da central (11/11/2026)

O usuário copiou a pasta `Android` do armazenamento interno para
`diagnostico/Android-copia/` (40 MB, 13 arquivos). Analisada sem executar nada.

**Por que quase tudo está vazio:** no Android 8.1, essa pasta guarda só os dados que cada app
escolhe deixar à vista (`Android/data/<pacote>`, `Android/media/<pacote>`). As configurações de
verdade ficam na área privada (`/data/data`), que exige root. As pastas do ZLINK, do player
original, do Rádio, do DSP e do launcher original estão **vazias**. A licença do ZLINK fica em
`/apd/zlink/` (`persist.sys.lic.dir`), fora dessa pasta.

| Conteúdo | O que é | Serve para o launcher? |
|---|---|---|
| `data/com.hcn.appupdate/cache/can.apk` (35 MB) + `Changelog.txt` + `output.json` | Sobra de download do atualizador do fabricante: o app **Informações do carro** (`com.hcn.autocan`) versão `146.0.1.20251110`. O changelog, em chinês, diz "importa novos modelos de carro; corrige problemas conhecidos" | Não. É a **mesma versão já instalada** (todos os relatórios desde 09/11): não há atualização pendente |
| Manifesto do `can.apk` (lido com `aapt2`, sem instalar) | Roda como **usuário do sistema** (`android.uid.system`). Expõe telas de ajustes do CAN (`CanRelatedSetActivity`, `CansetActivity`, `KeyMapActivity`, `DoorMapActivity`, `CanInfoActivity`) e um serviço `CanService` com interface própria. **Não** tem provedor de dados nem avisos públicos (broadcasts) | Não. Mostrar dados do carro (portas, temperatura) na Home exigiria engenharia reversa do serviço, e o CAN está fora do escopo (seção 9) |
| `data/com.adups.fota/` e `.srcMid`/`.dog_watch` na raiz | Cliente de **atualização de firmware pela internet** (Adups FOTA; `ro.fota.*`). `.srcMid` tem 20 caracteres e é um **identificador da central**: não é registrado aqui. `.dog_watch` é um marcador ("uu") | Não. Atualizar o firmware é proibido (seção 9) |
| `data/com.google.android.apps.maps/cache/diskcache/map_cache.db` (5 MB) | Cache de mapas do Google Maps | Não. Pode revelar lugares por onde o carro passou: **não foi aberto** |
| `data/com.google.android.youtube/…/*.uid`, `data/com.google.android.gms/files/gmsnet2.jpg`, `data/.nomedia` | Marcadores vazios ou de 1 pixel | Não |

**Conclusão:** nada na cópia muda o launcher. Ela não é necessária ao projeto e tem um
identificador da central e o cache de mapas; a pasta foi incluída no `.gitignore`.

