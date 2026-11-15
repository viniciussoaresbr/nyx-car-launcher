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

- **Teste da 0.4.25 no carro (15/11/2026, último teste antes da 1.0.0)**, relatório
  `diagnostico/nyx-car-diagnostico-20260417-153802.txt` (8 gravações; o nome traz de novo a
  data errada: 18 h depois de partir do zero, a central continuava com o relógio na data do
  firmware). Horários da central:
  - **cabo durante o CarPlay sem fio (correção da 0.4.25):** o iPhone apareceu no USB 4,8 s
    depois de o vídeo do CarPlay sem fio chegar, e o Nyx Car não abriu a espera do cabo nem
    fechou o ZLINK. O próprio iPhone encerrou a sessão sem fio 13,7 s depois (o Bluetooth da
    central religou), e o Nyx Car mostrou "CarPlay desconectado" e voltou para a frente. O cabo
    completou a conexão 5,5 s depois, o ZLINK abriu sozinho, coberto por "Conectando", e o
    CarPlay entrou pelo cabo 6,5 s depois. Na troca do sem fio para o cabo, ficam ~12 s sem o
    CarPlay: é o tempo da troca no iPhone, e o Nyx Car só acompanha o fim real;
  - **Nyx Car aberto do zero (correção da 0.4.25):** às 14:59:34, a central fechou o Nyx Car
    (e, 18 s depois, o Nyx Diagnóstico) com o CarPlay entrando pelo cabo. A sessão continuou, e
    o fim (14:59:47) foi coberto. O registro "aberto do zero com o Bluetooth da central
    desligado" apareceu 2 vezes por outro motivo: ao desligar o carro, a central desliga o
    próprio Bluetooth 7 a 13 s depois do fim do CarPlay e fecha os apps ao dormir (a tela
    apagou por ~6 e ~5 min). Essa sessão terminou ao ligar o carro, com o Bluetooth religando e
    o Nyx Car na frente: "nada mudou", como previsto;
  - **fim pelo som nas 7 desconexões com música** (6 pelo cabo, 1 sem fio): janela no lugar 15
    a 21 ms e desenhada 24 a 48 ms depois do aviso do som (3 gravadas). O cabo ou o Bluetooth
    confirmaram 0,6 a 1,1 s depois. Sem música (14:56 sem fio, 14:58 pelo cabo), o fim veio
    pelo Bluetooth;
  - **cabo que não conectou** (15:03): a central virou aparelho USB do iPhone, mas o CarPlay
    não começou em 20 s, e o Nyx Car fechou "Conectando" e o ZLINK. Quando o cabo saiu
    (15:03:36), apareceu "CarPlay desconectado" por 2 s, embora o CarPlay não tivesse entrado. Na tentativa
    seguinte, o vídeo chegou em 9,7 s;
  - **CarPlay depois do tempo máximo (0.4.23):** não aparece no relatório; continua sem
    validação no carro;
  - **música:** o modo "pasta e subpastas" (⇄ da lista) ficou guardado nas três aberturas do
    zero. O tempo de leitura e a ordem depois da partida não aparecem no relatório, que traz só
    os últimos 80 registros do diário;
  - **desempenho:** Home aberta a frio em 0,69 s (mediana de 8 aberturas), com 14,7 MB; 23,7 MB
    em uso na hora do relatório. Nenhum erro do app nos registros do diário.

- **Teste da 0.4.24 no carro (14/11/2026)**, relatório
  `diagnostico/nyx-car-diagnostico-20260417-035525.txt` (6 gravações; o nome traz a data errada
  da central, ver abaixo):
  - **relógio da central em 17/04/2026:** o relatório marca "Relógio na data do firmware". A
    central partiu do zero (sistema ligado havia ~6,8 h) e não acertou a hora, mesmo com a data
    automática ligada: sem internet, ela não tem de onde tirá-la. O Nyx Car só lê o relógio e
    não pode mudá-lo. Os horários abaixo são os da central;
  - **aleatório:** ligado às 03:31 na fila de 29 músicas; as posições seguiram 2, 3, 4, 5 da
    ordem sorteada, e tocar numa música de outra pasta (36 músicas) começou um sorteio novo
    dela. O ⇄ da lista (pasta e subpastas) e a continuidade depois de ligar o carro não
    aparecem no diário (o pendrive foi retirado às 03:45, antes da partida seguinte);
  - **player grande:** visto pelo usuário na foto do teste, com a capa e o ⇄ ligado;
  - **fim pelo som nas 5 desconexões com música** (2 pelo cabo, 3 sem fio): janela no lugar 11
    a 26 ms depois do aviso do som, e o Bluetooth confirmou 0,6 a 0,8 s depois;
  - **0.4.23, CarPlay de novo logo depois do fim** (03:04:23): o ZLINK voltou para a frente,
    coberto por "Conectando", como previsto. O caso "depois do tempo máximo" não ocorreu: nas
    duas vezes em que o tempo máximo estourou sem fio (03:46 e 03:49), o iPhone ainda estava
    ligando e desligando o Bluetooth, e o usuário tentou de novo em seguida;
  - **defeito 1, cabo durante o CarPlay sem fio** (02:27): o vídeo do CarPlay sem fio chegou e,
    0,1 s depois, o iPhone apareceu no USB. A espera do cabo tomou o lugar, o cabo não
    completou a conexão em 6 s, e o Nyx Car fechou o ZLINK com o CarPlay na tela. O CarPlay só
    entrou 50 s depois, pelo cabo. Corrigido na 0.4.25;
  - **defeito 2, Nyx Car aberto do zero durante o CarPlay** (03:04:33 e 03:48:12, sem "Erro do
    app": quem fechou foi a central, 10 a 20 s depois de o CarPlay entrar): a sessão estava só
    na memória do processo fechado. Às 03:48:46, o Bluetooth religou no fim do CarPlay, e nada
    cobriu a espera do ZLINK, que ficou 14 s na tela até o usuário apertar Home. Corrigido na
    0.4.25: ao abrir, o Bluetooth da central desligado volta a contar como CarPlay em uso.

- **Teste da 0.4.22 no carro (14/11/2026)**, relatório
  `diagnostico/nyx-car-diagnostico-20261114-134950.txt` (sem acessibilidade e sem captura; 8
  conexões pelo cabo e 6 tentativas sem fio, 10 gravações):
  - **conexão:** "Conectando" fechou 2,3 a 2,6 s depois de o Bluetooth da central desligar, e
    o usuário não viu mais a espera do ZLINK antes do CarPlay. Sem a medição da thread
    principal e sem o redesenho do Nyx Diagnóstico atrás do ZLINK, nenhuma disputa apareceu;
  - **fim pelo som nas 9 desconexões com música** (5 gravadas): janela no lugar 14 a 39 ms e
    desenhada 20 a 50 ms depois do aviso do som. O Bluetooth confirmou 0,6 a 1,0 s depois e o
    cabo, 1,1 s. Nenhuma cobertura falsa;
  - **sem fio, a música para de verdade ~7 s antes do aviso do som:** "música tocando" e o
    tráfego caíram a zero 7 a 8 s antes de o ZLINK soltar o áudio (12:12:17 → 12:12:25 e
    12:15:09 → 12:15:16). É o tempo que o ZLINK leva para perceber a conexão perdida; o aviso
    do som continua vindo junto com a troca da tela dele. Pelo cabo, não há esse intervalo;
  - **sem música** (12:11, cabo; 13:20, sem fio): o fim veio pelo Bluetooth, como esperado;
  - **sem fio, 3 tentativas estouraram o tempo máximo (25 s)** com o Bluetooth do iPhone
    conectando e caindo. Em duas, o CarPlay entrou depois, com o ZLINK atrás do Nyx Car: às
    13:19:35, 5,4 s depois do tempo máximo, com o Nyx Diagnóstico na frente; e entre 12:14:39 e
    12:14:55, 13 a 29 s depois. Às 12:14:59, o usuário abriu o CarPlay pelo Nyx Car. Como o
    Bluetooth tinha desligado sem o ZLINK na tela, a sessão não foi registrada. No fim (12:15:16),
    nada cobriu a espera do ZLINK, que ficou à vista até o usuário apertar Home (12:15:22).
    Corrigido na 0.4.23;
  - **barras do sistema** (medição da 0.4.22): só às 12:10:59, com o ZLINK abrindo do zero
    depois de o carro ligar, a barra de status apareceu e sumiu 0,7 s depois, 6 s antes do
    CarPlay. Nas outras conexões e nos fins, nada mudou: o ZLINK, o Nyx Car e o Nyx Diagnóstico
    ficam sempre em tela cheia. Não serve como sinal (medição retirada na 0.4.23).

- **Teste da 0.4.21 no carro (14/11/2026)**, relatório
  `diagnostico/nyx-car-diagnostico-20261114-085752.txt` (sem acessibilidade e sem captura da
  tela; 6 sessões gravadas, 5 pelo cabo e 1 sem fio, e 3 fora da gravação):
  - **fim pelo som nas 7 desconexões com música** (5 gravadas): a janela ficou no lugar 11 a
    16 ms e desenhada 16 a 43 ms depois do aviso do som, na hora, sem esperar o quadro. O
    Bluetooth confirmou 0,7 a 1,0 s depois. Nenhuma cobertura falsa, nenhum fim falso;
  - **desconexão durante uma ligação** (08:57:05): a fala e a música pararam juntas, o que a
    regra trata como fim de ligação, e o fim veio pelo Bluetooth 0,67 s depois. No teste de
    13/11, o fim de uma ligação com o CarPlay continuando deu o mesmo padrão (a música voltou
    1,4 s depois), por isso esse caso continua pelo Bluetooth;
  - **thread principal ocupada por ~11 s depois de o Bluetooth desligar**, em todas as 6
    gravações: 40 registros (o limite) de 100 a 516 ms cada, todos quadros da thread
    principal. Era a própria gravação: cada linha nova fazia o Nyx Diagnóstico, atrás do ZLINK,
    redesenhar a lista inteira, e cada quadro demorado virava mais uma linha (medição da
    0.4.20). Desde a 0.4.19, essa thread ficava com prioridade de tela urgente (−8) durante o
    CarPlay. Tudo isso disputava o processador com o ZLINK justo quando o CarPlay começa;
  - **relato do usuário:** a desconexão está precisa, melhor que com a acessibilidade; a
    espera do ZLINK só apareceu ao desconectar durante uma ligação. Numa conexão, a espera do
    ZLINK apareceu rapidamente antes do CarPlay, o que não acontecia havia várias versões
    (corrigido na 0.4.22, ver ARCHITECTURE.md, D20).
  - **imagem do CarPlay na conexão** (dados da acessibilidade de 12 e 13/11): a tela do ZLINK
    com a imagem do CarPlay e sem "conectando" apareceu 1,3 a 2,4 s depois de o Bluetooth
    desligar (2,9, 3,8 e 4,3 s uma vez cada); "Conectando" fecha ~2,35 s depois (vídeo em
    ~0,35 s mais 2 s). A margem é pequena, mas o usuário não via a espera do ZLINK nessas
    versões: a estrutura lida pela acessibilidade pode demorar a mudar depois da imagem.

- **Teste da 0.4.19 no carro (13/11/2026)**, relatório
  `diagnostico/nyx-car-diagnostico-20261113-193943.txt` (serviço religado pelo usuário às
  19:31; 3 fins pelo cabo, 3 sem fio, 5 medidos):
  - **fim pela tela do ZLINK nas 6 desconexões**, nenhum fim falso. Duas vezes, sem fio, a
    central religou o Bluetooth 10 a 13 s depois de o CarPlay entrar e o desligou de novo 1 s
    depois; com a imagem do CarPlay na tela, o Nyx Car não deu o fim. Às 19:38:44, logo antes
    de uma dessas vezes, `com.hcn.autoeq/.BlankActivity` (app do equalizador da central) ficou
    100 ms na frente do ZLINK;
  - **aviso do ZLINK atrasado:** chegou à thread principal do Nyx Car **165 a 219 ms** depois
    em 4 fins (3 pelo cabo, 1 sem fio) e em 8 ms no outro, com as duas threads a −8. Leitura da
    tela: 14 a 37 ms;
  - a colocação na hora deu erro no meio (corrigido na 0.4.20). A janela já estava no lugar,
    mas a medição mostrou "janela no lugar ?";
  - **"Conectando" até o tempo máximo** (19:31, cabo, sem música): o tráfego ficou em 105 KB/s,
    abaixo do limite de 512 KB/s, com o CarPlay parado na tela inicial dele. A tela ficou 10 s
    por cima do CarPlay. A imagem do CarPlay já estava na tela do ZLINK 2,3 s depois de o
    Bluetooth desligar (corrigido na 0.4.20);
  - **avisos depois de 1 s sem avisos**, com o CarPlay na tela: `ImageView` (6 a 12 por
    sessão), `ViewGroup` (1 ou 2, contando o do fim), `ViewGroup` com texto (3, numa sessão),
    `janela/MainActivity` (2, numa sessão);
  - **relato do usuário:** ainda há uma piscada quase imperceptível.

- **Teste da 0.4.18 no carro (13/11/2026)**, relatório
  `diagnostico/nyx-car-diagnostico-20261113-190957.txt` (serviço religado pelo usuário às
  19:00; 3 fins pelo cabo, 2 sem fio):
  - **fim pela tela do ZLINK nas 5 desconexões**, todas pela tela `14073307`; o cabo caiu 0,8
    a 1,1 s depois, e o Bluetooth religou 1,4 s depois sem fio. Nenhum fim falso;
  - **etapas** (ms depois do aviso do ZLINK): aviso recebido pelo Nyx Car em 2 a 9 (**277**
    uma vez, pelo cabo); leitura da tela de 22 a 34 ms; da janela pedida ao próximo quadro, 11
    a 37 ms; do quadro à janela no lugar, 9 a 27 ms. Janela no lugar em **53 a 101 ms** (357
    uma vez); desenhada em 77 a 101 ms. A thread do CarPlay ficou com prioridade −8;
  - **avisos do ZLINK durante o CarPlay:** `conteúdo/ViewGroup/subárvore` (9 a 23 por
    sessão) e `conteúdo/ImageView/subárvore` (5 a 17); numa sessão, também
    `conteúdo/FrameLayout/subárvore` ×1 e `janela/MainActivity` ×2 (o usuário foi à Home e
    voltou). A cada ~6 s vem um `ImageView` (telas `aff3f9d9` ou `2218598f`) e, 0,1 a 0,4 s
    depois, um `ViewGroup` (volta à `310e8c29`). O aviso do fim é `conteúdo/ViewGroup/subárvore`;
  - **relato do usuário:** a espera do ZLINK aparece por menos tempo que antes, mas ainda
    pisca.

- **Teste da 0.4.17 no carro (13/11/2026)**, relatório
  `diagnostico/nyx-car-diagnostico-20261113-180907.txt` (serviço de acessibilidade religado
  pelo usuário às 17:56; só pelo cabo):
  - **fim pela tela do ZLINK nas 6 desconexões**, todas pela tela `14073307`. O cabo caiu 0,8 a
    1,1 s depois. **Nenhum fim falso**, inclusive logo depois de o CarPlay entrar;
  - **tempo até cobrir** (5 fins medidos, em ms depois do aviso do ZLINK): tela lida em 33 a
    38 ms (125 e 268 ms em dois fins), janela desenhada em 77 a 111 ms (192 e 313 ms). Entre a
    leitura e o desenho, 44 a 73 ms; numa das vezes, 31 ms disso foram os registros do diário
    (corrigido na 0.4.18);
  - **som do CarPlay:** parou só **8 a 17 ms** antes do aviso da tela do ZLINK. Pausado por
    1 min 49 s (17:57:32 a 17:59:21) e por 42 s (até o fim às 18:05), o som de mídia continuou
    aberto. Na Siri ou ligação das 18:07:18, a música continuou junto com o som de chamada e,
    quando ele acabou (18:07:25,4), **o som de mídia sumiu por 1,4 s com o CarPlay
    funcionando**. Às 18:03:18, um som de toque ou aviso tocou junto com a música até o fim;
  - na ligação ou Siri, `sys.nonsystem.callstate` vai de 0 a 1 e volta;
  - duas telas novas do ZLINK depois do fim, já com a janela do Nyx Car na frente: `6e642b59`
    (6 elementos) e `f2eabb5d` (1 elemento);
  - leitor de notificações desligado em todas as gravações.

- **Teste da 0.4.16 no carro (13/11/2026)**, relatório
  `diagnostico/nyx-car-diagnostico-20261113-132236.txt` (serviço de acessibilidade religado
  pelo usuário às 13:16):
  - **fim pela tela do ZLINK em 4 das 5 desconexões gravadas** (2 pelo cabo, 2 sem fio): a tela
    `14073307`, já aprendida como espera, vale como fim na hora. "CarPlay desconectado" entrou
    no diário **87 a 145 ms** depois de a tela ser lida. O cabo caiu ou o Bluetooth religou 1,0
    a 1,4 s depois. Desse tempo, uma parte era do Nyx Car: a thread do CarPlay pedia a Home ao
    Android antes de a janela poder ser desenhada (corrigido na 0.4.17);
  - **fim falso pelo Bluetooth** (cabo, 13:18): a central desligou o Bluetooth com o CarPlay
    entrando, o vídeo chegou, e 2,3 s depois **o Bluetooth religou e desligou de novo 1,4 s
    depois**, com a imagem do CarPlay na tela do ZLINK. O Nyx Car deu o fim e trouxe a Home. O
    ZLINK voltou sozinho com o CarPlay 4 s depois. Às 13:18:26, o cabo saiu de verdade: a
    sessão já estava encerrada, então a espera do ZLINK ficou **7 s** na tela, até a Home ser
    chamada (corrigido na 0.4.17);
  - **leitor de notificações:** o pedido para religar (0.4.14) saiu duas vezes (11:19 e 11:25)
    e o leitor continuou desligado em todas as gravações;
  - **som do CarPlay:** com música, o som do tipo mídia sumiu **57 a 146 ms** antes da troca de
    tela pelo cabo e 69 a 654 ms antes sem fio. Ele não sumiu nenhuma vez sem o CarPlay
    terminar, mas a Siri, ligações e avisos de navegação não foram gravados;
  - uma segunda tela de espera (`37e4b3c0`, 20 elementos) apareceu depois de um fim;
  - **relato do usuário:** a espera do ZLINK ainda aparece "por alguns milésimos de segundos",
    pelo cabo e sem fio; o toque em "Conexão interrompida" ficou certo.

- **Teste da 0.4.13 no carro (13/11/2026)**, relatório
  `diagnostico/nyx-car-diagnostico-20261113-083619.txt` (serviço de acessibilidade religado
  pelo usuário depois de ligar o carro):
  - **fim pela tela do ZLINK nas 9 desconexões** (4 pelo cabo, 5 sem fio): a tela `14073307`
    aparece e "CarPlay desconectado" entra 0,05 a 0,2 s depois, **1,0 a 1,3 s antes** do cabo
    cair ou do Bluetooth religar;
  - **leitor de notificações desligado:** o "Acesso a notificações" continua dado, mas o leitor
    não estava ligado ao Android em nenhuma gravação. As sessões de mídia funcionam mesmo assim
    (só pedem o acesso). Hipótese: o Android não religa o leitor depois de a central forçar o
    fechamento do app ao desligar o carro;
  - **som do CarPlay:** com música, um som do tipo mídia aparece 0,5 a 1,1 s depois de o
    Bluetooth da central desligar e **some 0,05 a 0,35 s antes** de a tela do ZLINK mudar no
    fim (5 sessões). Pausar a música não o faz sumir. Sem música (testes de 12/11), não há som;
  - sem fio, 3 conexões não completaram (o ZLINK veio, o Bluetooth da central não desligou): o
    tempo máximo trouxe a Home;
  - **relato do usuário:** mesmo com o fim pela tela, a espera do ZLINK ainda aparece "por
    alguns milésimos de segundos ou 1 segundo" antes de "CarPlay desconectado". **Hipótese:** os
    elementos da espera do ZLINK continuam na tela durante o CarPlay, por baixo da imagem
    (aparecem na estrutura das telas do CarPlay), e ficam à vista quando o vídeo some, antes de
    o ZLINK trocar a estrutura da tela; numa "Conexão interrompida", um toque fechou a nossa
    tela antes de a Home ser desenhada e o ZLINK apareceu rápido (corrigido na 0.4.15).

- **Teste da 0.4.12 no carro (12/11/2026)**, relatório
  `diagnostico/nyx-car-diagnostico-20261112-225020.txt`:
  - **o serviço de acessibilidade estava desligado** ("Fim do CarPlay pela tela do ZLINK:
    desligado"), sem o usuário ter desligado. Nenhuma tela do ZLINK foi registrada, e todos os
    fins vieram pelo Bluetooth, como na 0.4.10;
  - **quando ele caiu:** o serviço ligou às 22:17:35 (0.4.11). Às ~22:26 o carro foi desligado:
    o Nyx Car foi aberto do zero às 22:26:44 e a central dormiu ~15 min ("acordado há" passou de
    228 para 230 min em 16,7 min). Nesse processo, e no da 0.4.12, o serviço não voltou;
  - **hipótese:** ao desligar o carro, o firmware **força a parada** dos apps (o mesmo que "fecha
    os apps" visto desde a 0.4.0; há propriedades do IPO da MediaTek, `sys.ipo.disable` = 1 e
    `sys.ipowin.done` = 1), e o Android 8.1 tira da lista de serviços de acessibilidade ligados
    os de um app parado à força. Se for isso, o serviço desliga a cada vez que o carro desliga.
    **Confirmado pelo usuário em 12 e 13/11/2026:** com o serviço ligado, depois de desligar e
    ligar o carro, o cartão 6 mostra "desligado". O "Acesso a notificações" e a permissão de
    sobrepor, ao contrário, continuam dados desde 10/11, com o carro desligado várias vezes.
    Escolher o Nyx Car em "Definir Navegador" (Configurações do Veículo → Geral) não evitou
    isso (13/11). Nas Configurações do Veículo e no menu de fábrica (fotos do usuário, 13/11)
    não há lista de apps protegidos; o Quick Boot está em "Cold start after 3 days";
  - "Conexão interrompida" (cabo tirado logo depois de conectado) entrou e trouxe a Home 5 s
    depois, sem virar "Conectando"; os toques em "Conectando" trouxeram a Home.

- **Teste da 0.4.11 no carro (12/11/2026)**, relatório
  `diagnostico/nyx-car-diagnostico-20261112-222525.txt` (serviço de acessibilidade restrito ao
  ZLINK; só a estrutura das telas, sem textos):
  - **a Acessibilidade existe** nas Configurações deste firmware, e o serviço do Nyx Car ligou;
  - **telas do ZLINK** (resumo da estrutura e nomes internos que as distinguem):
    - conectando: `72cf7b5d` (24 elementos, `state_connecting`); a cada ~6 s alterna com
      `85a96c05`/`06a8aa21`, um carrossel de prévias (`link_aa_op_preview`, Android Auto, e
      `link_cp_op_preview`, CarPlay);
    - espera parada: `14073307` (21 elementos, `title_link`), **a mesma depois do fim do
      CarPlay**; sem iPhone pelo cabo: `17480eaf` (`wired_cl`); ajustes do ZLINK: `52142ed6`
      (outra tela, `features.settings.SettingsActivity`, visível em `sys.top.activity`);
    - **CarPlay:** `7228d6ff` (27 elementos), junto com o Bluetooth da central desligando (uma
      vez 16 ms antes do aviso), e `310e8c29` (24 elementos) 1,4 a 2,2 s depois. As duas têm
      **`phone_content_event_delegate`**, que nenhuma tela de espera tem. Sem fio, a cada ~6 s
      a tela pisca por ~0,3 s (`2218598f`/`aff3f9d9`, com `phone_content_presenter`) sem perder
      esse elemento; pelo cabo, não pisca;
  - **fim do CarPlay:** o ZLINK troca para `14073307` **~0,6 s antes** de o Bluetooth religar (4
    vezes) e 1,1 s antes de o cabo cair (1 vez). Com a tela já aprendida, "CarPlay
    desconectado" entrou 0,14 s depois da troca;
  - **conexão lenta pelo cabo:** a inversão de papéis do USB levou 7,6 s (normal: ~1 s); o ZLINK
    ficou em "conectando" e o CarPlay entrou 31 s depois do cabo; o tempo máximo (20 s) tirou o
    ZLINK da frente antes (corrigido na 0.4.12);
  - o toque em "Conectando ao CarPlay" (0.4.10) trouxe a Home.

- **Teste da 0.4.9 no carro (12/11/2026)**, relatório
  `diagnostico/nyx-car-diagnostico-20261112-205612.txt` (horários em milissegundos):
  - **fim do CarPlay:** o Nyx Car reage **50 a 65 ms** depois do aviso "Bluetooth desligado →
    ligando" (3 vezes), e `sys.top.activity` já mostra a Home 10 a 50 ms depois. Pelo cabo,
    `sys.usb.state` e `sys.usb.config` voltam para `mtp` ~0,7 s depois do Bluetooth, e o estado
    USB "connected" cai ~0,5 s depois: o Bluetooth continua sendo o **primeiro aviso**;
  - **sem fio:** a tela do Nyx Car entra até 50 ms depois de `sys.top.activity` virar o ZLINK.
    O iPhone conecta pelo Bluetooth (A2DP e HFP "conectado") 2 a 3 s antes de o ZLINK chegar;
  - **sem o iPhone conectado, a central tenta o HFP dele a cada ~10 s** (avisos "desconectado
    → conectando" e de volta), mesmo com o cabo do CarPlay;
  - **arquivos do sistema:** nenhum dos 6 candidatos do USB pode ser lido por um app
    (`/sys/class/android_usb/android0/state`, `/sys/class/udc/musb-hdrc.0.auto/state` e
    `current_speed`, `mode` e `vbus` do `musb-hdrc.0.auto`, `/sys/class/power_supply/usb/online`);
    a lista de interfaces de rede veio vazia (exige a permissão de internet);
  - **mau contato pelo cabo:** o iPhone apareceu e sumiu do USB, "Conexão interrompida" entrou
    6 s depois, mas 4 s depois uma tentativa de HFP da central rearmou a vigia e a tela virou
    "Conectando (sem fio)" até o tempo máximo (corrigido na 0.4.10);
  - **sem fio sem completar (3 vezes):** o ZLINK chegou à tela, mas o Bluetooth da central não
    desligou; o tempo máximo (25 s) trouxe a Home.
  - **relato do usuário:** a tela de espera do ZLINK ainda aparece **por alguns segundos**
    antes de "CarPlay desconectado". Como o Nyx Car reage em 50 ms ao Bluetooth, o ZLINK troca
    de tela segundos antes de religá-lo. Na 0.4.11, o serviço de acessibilidade restrito ao
    ZLINK passa a ver essa troca.

- **Teste da 0.4.8 no carro (12/11/2026)**, relatório
  `diagnostico/nyx-car-diagnostico-20261112-193615.txt`:
  - **pelo cabo, 6 conexões:** "cabo conectado" (a central vira aparelho do iPhone) de 1,7 a
    3 s depois de o iPhone aparecer no USB; o Bluetooth da central desliga de 6 a 8 s depois
    disso; o vídeo foi detectado de 8,3 a 10,8 s depois de a tela abrir. O usuário achou
    preciso;
  - **cabo tirado logo depois de conectar (2 vezes):** o iPhone aparece e some do USB, a
    central nunca vira aparelho dele, e mesmo assim o **ZLINK vem para a frente sozinho** (3,6 s
    depois) e fica na espera. A tela saiu no tempo máximo e deixou o ZLINK parado;
  - **sem fio, 8 conexões:** o Bluetooth da central desliga de 4 a 8,7 s depois de a tela
    abrir; o vídeo foi detectado de 4,8 a 9 s depois. Uma conexão não completou (tempo
    máximo, sem o Bluetooth da central desligar). Com o Nyx Diagnóstico na tela, o usuário viu
    o ZLINK ~1 s antes da tela;
  - **desconexão:** o Nyx Car voltou em todas (10). O primeiro aviso continua sendo o
    Bluetooth da central religando; o "terminou" saiu 0,1 a 0,7 s depois dele (atraso do Nyx
    Car, corrigido na 0.4.9). O usuário viu a tela de espera do ZLINK por um instante antes
    de "CarPlay desconectado": **o ZLINK troca de tela antes de religar o Bluetooth**;
  - `rw.cp.link.state` continuou `1` e nenhuma outra propriedade mudou no fim do CarPlay;
  - durante o CarPlay, o tráfego total cai para ~0 a 1 KB/s depois da primeira subida (tela
    parada manda pouco vídeo): o tráfego **não** serve para saber se o CarPlay terminou.

- **Teste da 0.4.7 no carro (12/11/2026)**, relatório
  `diagnostico/nyx-car-diagnostico-20261112-181412.txt` (tela de espera do Nyx Car, B1):
  - a permissão "Sobrepor a outros apps" existe neste firmware e foi dada pelo usuário;
  - **pelo cabo:** 5 conexões; o vídeo foi detectado de 9,0 a 9,7 s depois de o iPhone
    aparecer no USB, ~0,5 a 1 s depois de a central desligar o Bluetooth. Numa conexão o
    CarPlay não ficou pronto e a tela saiu no tempo máximo (20 s);
  - **sem fio:** com a Home na tela, o vídeo foi detectado 4,4 e 7,3 s depois de a tela abrir
    (o limite de 160 KB/s funcionou); o ZLINK aparecia ~1 s antes da tela (atraso do `onStop`
    e do `getprop`). Fora da Home, 2 sessões sem a tela;
  - a volta automática funcionou em todas as desconexões.

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

