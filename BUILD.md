# Compilação, instalação e recuperação

## 1. Ambiente de desenvolvimento

Tudo foi instalado no espaço do usuário, sem `sudo` e sem alterar o sistema:

| Componente | Versão | Local |
|---|---|---|
| JDK (Eclipse Temurin) | 21.0.13 | `~/.jdks/jdk-21.0.13+9` |
| Android SDK Command-line Tools | latest | `~/Android/Sdk/cmdline-tools/latest` |
| Android SDK Platform | 37 (`android-37.0`) | `~/Android/Sdk/platforms` |
| Android SDK Build-Tools | 36.0.0 | `~/Android/Sdk/build-tools` |
| Platform-Tools (adb) | 37.0.1 | `~/Android/Sdk/platform-tools` |
| Gradle | 9.9.0 (via wrapper, SHA-256 verificado) | `~/.gradle/wrapper/dists` |
| Emulador + imagens Android 8.1 e 13 | 37.2.13 | `~/Android/Sdk/emulator`, `~/Android/Sdk/system-images` |

As licenças padrão do Android SDK foram aceitas durante a instalação (`sdkmanager --licenses`).

- O Gradle escolhe o JDK 21 sozinho, por causa de `gradle/gradle-daemon-jvm.properties`
  (ele encontra o JDK em `~/.jdks`). Não é preciso exportar `JAVA_HOME`.
- `local.properties` (fora do controle de versão) aponta o SDK:
  `sdk.dir=/home/<usuário>/Android/Sdk`.
- Para abrir no Android Studio: *Open* → pasta do projeto. Ele usa o mesmo SDK.

### Limitação conhecida: emulador

O emulador oficial (37.2.13) **cai com falha de segmentação** neste PC (Fedora 44,
kernel 7.2), com qualquer imagem e configuração: com e sem KVM, com GPU no host ou no
convidado, com o binário headless ou com interface. Por isso, a verificação visual é feita
por **testes de tela na JVM** (Robolectric + Roborazzi), que abrem a Activity real e
salvam capturas PNG (seção 3).

Se o espaço fizer falta, o emulador e as imagens (~3 GB) podem ser removidos:

```bash
~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --uninstall "emulator" \
  "system-images;android-27;default;x86" "system-images;android-33;default;x86_64"
```

## 2. Compilar

```bash
git clone https://github.com/viniciussoaresbr/nyx-car-launcher.git
cd nyx-car-launcher
./gradlew assembleRelease
```

APK gerado: **`app/build/outputs/apk/release/nyx-car-launcher-release.apk`**

- Use sempre o build **release** para instalar na central: ele é otimizado (R8). O de
  debug é bem mais lento e distorce as medições.
- A **primeira** compilação demora bastante: cerca de 30 min neste PC, por causa do
  download de dependências e da primeira execução do R8 e do lint. As seguintes levam
  cerca de 1 min.
- Fase 1: o APK é assinado com a chave de debug desta máquina (`~/.android/debug.keystore`).
  **Faça backup desse arquivo.** Sem ele, uma versão nova não consegue atualizar a
  instalada: seria preciso desinstalar antes. A Fase 6 criará uma chave própria do projeto.

## 3. Testes

```bash
./gradlew testDebugUnitTest
```

- Testes de lógica: estatísticas de quadros, leitura de `/proc/cpuinfo` e `getprop`,
  filtro de dados sensíveis, versões do Android, classificação de apps.
- Testes de tela (simulando Android 8.1), com capturas em **`app/build/outputs/roborazzi/`**:
  - **Home** na área útil real (1280 × 655 dp a 160 dpi): `home_reference.png` e, com
    música tocando, `home_playing.png`;
  - **abas** Música, Aplicativos, Conectividade e Configurações: `tab_*.png` (com os apps
    da central simulados como instalados) e a aba Música com um pendrive simulado:
    `tab_music_playing.png`;
  - **catálogo de componentes** completo: `design_catalog.png`;
  - **painel de diagnóstico**, todas as abas, a 160 e 240 dpi.
- Regras do Design System verificadas automaticamente: todo elemento tocável com pelo
  menos 64 dp (Home e cada aba) e `PcPalette.kt` igual a `colors.xml`.
- Comportamento: navegação entre abas, Voltar, botão Home do sistema, declaração como tela
  inicial, recuperação (Tela inicial e Launcher original), abertura de apps (Intent e
  flags), app ausente ("Não encontrado") e escolha da entrada certa em pacotes com mais de
  um ícone.
- Player (pendrive em pasta temporária, `MediaPlayer` simulado): tocar, foco de áudio
  (perda definitiva e curta), "ao ligar, só toca com um toque", pendrive removido, fim da
  pasta, música que não abre, próxima/anterior, notificação do serviço, ordem natural e
  filtros de arquivos.
- Deslize entre abas (0.4.1), com toques simulados: ordem da barra, parada nas pontas,
  arrasto vertical, curto ou lento que não troca, gesto rápido que troca, deslize sobre um
  botão que não o aciona (e o toque que continua funcionando), rolagem da lista de músicas
  e a transição terminando no lugar.
- App desativado ("Desativado"), aba Música só com BT Música e Rádio e Aplicativos sem
  Música USB (0.4.2), erros do diário sem caminhos de arquivo e medição de abertura com o
  processo já aberto.
- Barra de status (0.4.2): a Home mantém a margem de cima quando outro app esconde a barra
  (volta do CarPlay) e usa a altura visível se o firmware não informar a estável.
- Aplicativos com a grade completa, Play Store no último lugar (0.4.3), e o aviso de relógio
  na data do firmware no relatório.
- Aplicativos → Todos (0.4.4): com os apps reais da central (nomes do relatório), a lista
  sai em ordem, sem Música USB, Theme, atualizadores e o próprio Nyx Car, com atalhos do
  mesmo tamanho dos Favoritos, rola até o fim com a barra sempre visível, respeita 64 dp e abre o app certo; a aba volta para "Favoritos" ao entrar de novo. Regras da
  lista testadas também na JVM (`AllAppsTest`).
- Favoritos (0.4.4): editar troca um lugar, salva na hora e continua depois de reabrir o app;
  escolher um favorito troca os dois de lugar; "Restaurar padrão"; Voltar na escolha e na
  edição; nome longo com a legenda "Favorito" em uma linha. Regras na JVM (`FavoritesTest`):
  sempre 12, sem repetição, lista guardada inválida completada com o padrão.
- CarPlay (0.4.5), com avisos USB e Bluetooth simulados: cabo fora com o ZLINK na tela traz o
  Nyx Car de volta (como o Home); mau contato curto não faz nada; com outro app na tela nada
  muda; sem fio pelo Bluetooth; fim pelo cabo e pelo Bluetooth volta uma vez só. Regras na
  JVM (`CarPlaySessionTest`), inclusive o Bluetooth atrasado da sessão anterior, e o tráfego
  de rede da gravação (`NetTrafficTest`).
- 0.4.6: a sequência real do cabo tirado (Bluetooth religa, depois o USB cai) volta uma vez
  só; mau contato com o iPhone reaparecendo no USB não encerra; o Nyx Diagnóstico mantém a
  margem da barra de status quando o CarPlay a esconde; o diário entra no relatório com o que
  aconteceu depois de abrir o diagnóstico.
- Tela de espera do CarPlay (0.4.7), com tráfego e avisos simulados: pelo cabo abre no iPhone
  no USB e fecha com o vídeo (o controle de 294 KB/s não fecha); tempo máximo; toque; sem
  permissão nada aparece; sem fio abre quando o ZLINK cobre a Home e fecha com 247 KB/s; não
  abre com o CarPlay aberto pelo usuário, já conectado ou com outro app; "desconectado" até a
  volta automática; mau contato volta para "Conectando". Regra do vídeo na JVM
  (`VideoStartDetectorTest`) e capturas de cada estado (`CarPlayWaitScreenTest`).
- 0.4.8: a tela fica 2 s depois do vídeo; volta na hora ao desconectar; vigia do ZLINK depois
  de a Home sair da tela e depois de o iPhone se conectar pelo Bluetooth (com outro app na
  tela), terminando sozinha; "Aguardando o iPhone" ao tocar em CarPlay sem nada conectado
  (toque, tempo máximo, toque longo abre o ZLINK, o iPhone conectando troca a tela) e o ZLINK
  direto com o CarPlay conectado ou sem a permissão.
- 0.4.9: "CarPlay desconectado" por 2 s com a Home por baixo (a Home voltando não fecha antes;
  um toque fecha); "Conexão interrompida" pelo cabo sem "cabo conectado" em 6 s e com o fim
  antes do vídeo; o iPhone voltando segue a conexão; sem volta, a Home e a tela fecha quando
  ela aparece; tempo máximo sem o CarPlay começar traz a Home e, com o CarPlay já entrando, só
  fecha a tela; janela reservada durante o CarPlay e pela vigia do Bluetooth, saindo no fim.
- 0.4.10: o toque em "Conectando" traz o Nyx Car (com outro app na frente, só fecha a tela);
  tocar e segurar fecha só a tela; "Conexão interrompida" continua com as tentativas de
  Bluetooth da central; o CarPlay entrando de novo durante ela segue a conexão; o Bluetooth só
  "conectando" não arma a vigia; o ZLINK que já estava na tela não abre a tela de espera.
- 0.4.11: fim pela tela do ZLINK (com a tela de espera aprendida antes, "CarPlay desconectado"
  na hora e o fim pelo Bluetooth depois não repete a volta); tela nunca vista não muda nada e o
  Bluetooth continua valendo; sem CarPlay, as telas só são aprendidas e guardadas. Regras na JVM
  (`ZlinkScreensTest`): aprendizado, tela do CarPlay estável, telas intermediárias, um fim por
  sessão, a imagem do CarPlay nunca vira espera, Bluetooth desligado sem sessão.
- 0.4.12: a imagem do CarPlay sumindo é fim já na primeira vez (conferida 100 ms depois e
  aprendida); voltando na conferência, não é; pelo cabo, o ZLINK "conectando" segura a tela até
  1 min. Na JVM: fim provável e confirmação, piscadas com a imagem, a imagem nunca vira espera,
  tela sem a imagem nunca vira tela do CarPlay.
- As capturas aprovadas de cada versão ficam em `docs/design/`.
- Relatório HTML: `app/build/reports/tests/testDebugUnitTest/index.html`.

## 4. Transferir pelo pendrive

1. Use um pendrive formatado em **FAT32** (o formato mais compatível com centrais).
2. Copie `nyx-car-launcher-release.apk` para a raiz do pendrive.
3. Ejete o pendrive com segurança antes de removê-lo.

## 5. Instalar na central

> Operação na central: só faça depois de ler esta seção e concordar.
> O que o app **faz**: mostra a Home e as abas; abre apps e telas de configuração quando
> você toca nos atalhos; **toca as músicas do pendrive** (somente leitura, depois que você
> permitir o acesso), com uma notificação enquanto toca; lê o estado do Wi-Fi e do
> Bluetooth; **pode ser escolhido como tela inicial** (só se você escolher); grava um
> relatório .txt **somente quando você toca em "Salvar"** no Nyx Diagnóstico; guarda num
> arquivo privado a linha do tempo dos testes, a última música e posição e um diário do
> player e dos erros do app, sem nomes de músicas (tudo apagado ao desinstalar).
> O que ele **não** faz: não altera configurações (nem a tela inicial: quem escolhe é você),
> não toca música sozinho ao ligar, não apaga nem altera arquivos do pendrive, não remove o
> launcher original, não desativa apps (o player original só é desativado por você, nas
> Configurações; seção 8), não acessa a internet, não mexe no ZLINK. Só roda em segundo plano
> enquanto uma música toca, durante uma gravação de teste que você inicia (até 3 minutos)
> ou, se você ativar o acesso opcional a notificações, enquanto esse acesso estiver ativo.

1. Conecte o pendrive na central e abra o **gerenciador de arquivos**.
2. Navegue até o pendrive e toque em `nyx-car-launcher-release.apk`.
3. Se o Android pedir, permita **"Instalar apps desconhecidos"** para o gerenciador de
   arquivos. É uma permissão do gerenciador, não do sistema todo.
4. Toque em **Instalar** e depois em **Abrir** (ou procure **Nyx Car** na lista de apps).

Se aparecer **"App não instalado"** ou **"Erro ao analisar o pacote"**, anote a mensagem
exata. Isso indica que o Android real é anterior ao 6.0, um dado importante para o diagnóstico.

## 6. Roteiro de teste na central (versão 0.4.12)

Valida o fim do CarPlay pela imagem do CarPlay sumindo da tela do ZLINK, já na primeira vez. O
serviço "Nyx Car — fim do CarPlay" continua ligado (confira no cartão 6: "Situação: ligado").
Instale **por cima** (toque em "Atualizar"). Faça com o **carro parado**. Grave cada teste no
Nyx Diagnóstico → Testes no carro. Anote as respostas das perguntas em **negrito**.

**A. Desconexão**

1. Pelo cabo: conecte, espere o CarPlay e tire o cabo. **A tela de espera do ZLINK ainda
   aparece antes de "CarPlay desconectado"? Por quanto tempo, mais ou menos?** Repita 3
   vezes.
2. Sem fio: com o CarPlay na tela, desligue o Bluetooth do iPhone. Mesmas perguntas, 2 vezes.
3. **Durante o CarPlay (pelo cabo e sem fio, alguns minutos), o Nyx Car voltou para a frente
   sozinho alguma vez sem você desconectar?** (não deveria)

**B. Conexão lenta (se acontecer)**

4. Se o CarPlay demorar para entrar pelo cabo, a tela "Conectando ao CarPlay" deve continuar
   enquanto o ZLINK estiver conectando, até 1 min.

**C. Relatório (atenção aos 0 bytes)**

5. Salve o relatório no pendrive pelo Nyx Diagnóstico (**Salvar em…**) e espere a mensagem
   **"Relatório salvo e conferido"** antes de remover o pendrive.

**Para desligar o serviço:** cartão 6 → Abrir acessibilidade → desative "Nyx Car — fim do
CarPlay". O Nyx Car volta a perceber o fim pelo Bluetooth, como na 0.4.10.

O relatório omite identificadores (número de série, MAC, IMEI, dados de rede). O diário do
app não tem nomes de músicas; avisos de outros players podem ter: revise o arquivo antes
de compartilhar.

## 7. Desinstalar

**Configurações → Apps → Nyx Car → Desinstalar** (ou segure o ícone e arraste para
"Desinstalar", se o launcher original permitir). Nada mais fica no sistema, exceto os
relatórios salvos em **Download**, que podem ser apagados pelo gerenciador de arquivos.
Se o Nyx Car for a tela inicial, o Android volta sozinho ao launcher original.

## 8. Recuperar a configuração original

A partir da 0.4.0, o Nyx Car **pode** ser a tela inicial, mas só se você escolher. O
launcher original (`com.android.launcher3`, "Tela de início 3") continua instalado e
intacto. Caminhos de volta, do mais simples ao mais drástico:

1. **No Nyx Car: Configurações → Launcher original.** Abre o launcher original na hora
   (o Home da central continua levando ao Nyx Car).
2. **No Nyx Car: Configurações → Tela inicial** → **Tela inicial** → **Tela de início 3**.
   O launcher original volta a ser o padrão.
3. Sem abrir o Nyx Car: **Configurações do Android → Apps → Apps padrão → Tela
   inicial** → **Tela de início 3** (a tela **Apps padrão** existe neste firmware).
4. Se o Nyx Car fechar com erro ao abrir, o Android mostra "Nyx Car parou": toque em
   **Informações do app** → **Limpar padrões** (ou desinstale). Pelo comportamento padrão do
   Android, se uma tela inicial de terceiros fechar com erro várias vezes seguidas, o sistema
   desfaz a escolha dela sozinho (não conferido neste firmware).
5. Desinstalar o Nyx Car (seção 7): o Android volta ao launcher original.

### Player original desativado (exceção autorizada em 10/11/2026)

> **Solução adotada em 11/11/2026, sem desativar nada:** Configurações do Veículo →
> **Geral** → desligar **Reprodução automática**. Com isso, o player original não abre mais
> ao conectar o pendrive. Para voltar ao comportamento antigo, ligue a opção de novo. O
> roteiro abaixo fica registrado como alternativa.

O player original (`com.hcn.AutoMediaPlayer`) abria sozinho ao conectar o pendrive. O
usuário autorizou desativá-lo (PROJECT_PLAN.md, seção 9). **Desativar não apaga nada** e se
desfaz no mesmo lugar. O mesmo app é o **Vídeo**: ele também fica desativado.

**Desativar (na central, com o carro parado):**
1. Configurações do Android → **Apps e notificações** → **Informações do app** (ou "Ver
   todos os apps"). Os nomes podem variar um pouco neste firmware.
2. Toque em **Música**. Confira no fim da página a versão **1.3.20260202-ac8227l**: é o
   player do pendrive.
3. Toque em **Desativar** e confirme o aviso do Android ("outros apps podem não funcionar
   como deveriam"; é um aviso padrão).
   - Se **Desativar** estiver cinza, o fabricante bloqueou: **pare**. Não há outro caminho
     permitido (o ADB está bloqueado e o menu de fábrica é proibido).
   - **Nunca** use "Desinstalar atualizações" nem outra opção da página.

**Conferir depois:** conecte o pendrive (nada deve abrir sozinho), desligue e ligue o carro
com o pendrive, e teste Rádio, BT Música, CarPlay e câmera de ré. Se aparecer "… parou" ou
algo estranho, ative de novo.

**Ativar de novo:** mesmo caminho → **Música** (aparece como "Desativado" na lista) →
**Ativar**. Tudo volta como era.

## 9. ADB via Wi-Fi (indisponível nesta central)

> **Resultado (09/11/2026):** não foi possível ativar. O fabricante bloqueou as opções do
> desenvolvedor: os 7 toques não funcionam, e abrir a tela diretamente é recusado pelo
> Android ("Ative as opções de desenvolvedor"). O único caminho restante seria o menu de
> fábrica, proibido pelo plano. Detalhes em [HARDWARE.md](HARDWARE.md), seção 5. O
> roteiro abaixo fica registrado caso uma atualização de firmware libere o acesso.

O diagnóstico encontrou `service.adb.tcp.port=8555`: a central tem uma porta de ADB pela
rede já configurada, mas o serviço fica desligado enquanto a depuração estiver desativada.
Com o ADB, instalar, ver logs, capturar a tela e medir o desempenho passam a ser feitos do
PC, sem pendrive.

**O que muda na central:** só a chave "Depuração USB", nas opções do desenvolvedor. Isso é
reversível e não toca no firmware, nas partições, no ZLINK nem nas configurações de fábrica.

**Segurança:** com a depuração ligada, qualquer computador na mesma rede pode *pedir*
conexão. Cada computador novo, porém, precisa ser **aprovado na tela da central**
(`ro.adb.secure=1`). Recuse qualquer pedido que você não tenha iniciado.

### Ativar (na central)

1. **Rede:** conecte a central ao Wi-Fi do mesmo roteador do PC (rede `192.168.1.x`).
   Rede de convidados não serve, porque isola os aparelhos. O sinal precisa chegar ao carro.
2. **Opções do desenvolvedor:** **nesta central, o atalho padrão (tocar 7 vezes em
   "Número da versão") foi desativado pelo fabricante** (testado em 09/11/2026). Use o
   Nyx Car (versão 0.1.1-diag ou superior): aba **Apps e atalhos** → **Abrir opções
   do desenvolvedor**. Isso abre a tela padrão do Android, escondida do menu.
   - Se a chave principal no topo estiver desligada, ligue-a e confirme o aviso do Android.
   - Se o sistema pedir **senha**, pare: é provável que seja o menu de fábrica do
     fabricante, que está fora do escopo.
3. **Depuração:** nessa mesma tela, ligue **"Depuração USB"** e confirme. **Não altere
   nenhuma outra opção.** De volta ao Nyx Car, **Atualizar status** deve mostrar
   "Depuração USB (ADB): LIGADA".
4. **IP da central:** Configurações → Wi-Fi → toque na rede conectada, ou Sobre o
   dispositivo → Status → **Endereço IP**. Anote (ex.: `192.168.1.45`).

### Conectar (no PC)

```bash
~/Android/Sdk/platform-tools/adb connect <IP-da-central>:8555
```

- Na central, aparece **"Permitir depuração USB?"** com a impressão digital do PC: marque
  **"Sempre permitir deste computador"** e toque em **OK**.
- Se a porta 8555 não responder, tente a padrão: `adb connect <IP>:5555`.
- Depois de desligar e ligar o carro, o IP pode mudar e a depuração pode ser desativada.
  Basta repetir os passos 3 e 4.

### Desativar

Opções do desenvolvedor → desligue **"Depuração USB"**. Para esconder o menu, desligue a
chave no topo de "Opções do desenvolvedor".

### O que será feito via ADB (sempre explicado antes)

- **Somente leitura:** `getprop`, `dumpsys` (memória, quadros, atividade atual), `logcat`,
  captura de tela (`screencap`).
- **Instalar e atualizar o Nyx Car** (`adb install -r`), equivalente ao pendrive.
- **Compilar o próprio Nyx Car** (`cmd package compile`) para medições realistas.
  Afeta apenas o nosso app.
- **Nunca:** desinstalar ou desativar apps do sistema (única exceção: o player original, à mão, seção 8), `settings put` em configurações do
  sistema, root, remount, flash ou reboot para bootloader/recovery.
