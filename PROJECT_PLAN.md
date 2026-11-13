# Nyx Car Launcher — Plano de Desenvolvimento

## Status das fases

| Fase | Situação | Observações |
|---|---|---|
| 1 — Diagnóstico | **Concluída** | Diagnóstico rodado na central em 09/11/2026. Resultados em [HARDWARE.md](HARDWARE.md). Decisão: Views nativas ([ARCHITECTURE.md](ARCHITECTURE.md), D2). ADB via Wi-Fi: bloqueado pelo fabricante; o trabalho segue por pendrive. |
| 2 — Design System | **Concluída** | Design System em Views e Home de referência aprovados na central em 09/11/2026 (versão 0.2.1-ref: atalho "CarPlay", barra inferior só com ícones, Navegação pelo CarPlay). Ver [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md). |
| 3 — Interface principal | **Em andamento** | 0.3.0 validada: atalhos e abas funcionam. Player USB: a limitação é do player do fabricante; decisão do usuário pendente (player próprio, de terceiros ou só abrir o player; ARCHITECTURE.md, D16). Tela de espera do ZLINK: medição dos sinais repetida na 0.3.2 (roteiro em [BUILD.md](BUILD.md), seção 6). |
| 4 — Funcionalidades | **Em andamento** | Etapa 0.4.0 (tela inicial e player próprio) **validada no carro em 10/11/2026**. Etapas 0.4.1 (deslizar entre abas) e 0.4.2 (sem o atalho do player USB, Home sem "pulo" ao voltar do CarPlay) **validadas no carro em 11/11/2026**. Etapa 0.4.3 (novo nome Nyx Car, Play Store em Aplicativos, data e hora no relatório) **validada no carro em 11/11/2026**. Etapa 0.4.4 (Aplicativos com Favoritos editáveis e lista de todos os apps) **validada no carro em 11/11/2026**. Etapa 0.4.5 (volta automática do CarPlay e medição do "CarPlay pronto") testada no carro em 12/11/2026: medição concluída. Etapa 0.4.6 (correções; volta automática) **validada no carro em 12/11/2026**. Etapa 0.4.7 (B1: tela de espera do CarPlay do Nyx Car, cabo e sem fio) testada no carro em 12/11/2026. Etapa 0.4.8 (ajustes do B1 e CarPlay sem iPhone) testada no carro em 12/11/2026. Etapa 0.4.9 (conexão interrompida, "CarPlay desconectado" por 2 s, mais rapidez) testada no carro em 12/11/2026. Etapas 0.4.10 (toque na tela de espera fecha também o ZLINK) e 0.4.11 (fim do CarPlay pela tela do ZLINK, com o serviço de acessibilidade) testadas no carro em 12/11/2026. Etapa 0.4.12 (fim pela imagem do CarPlay já na primeira vez; conexão lenta pelo cabo) implementada; **aguardando o teste no carro**. O player original não abre mais sozinho (Reprodução automática desligada pelo usuário). |
| 5 — Otimização | Não iniciada | Linha de base de desempenho vem do diagnóstico. |
| 6 — Distribuição | Em andamento | Código no GitHub (público): https://github.com/viniciussoaresbr/nyx-car-launcher (11/11/2026). Chave de assinatura: continua a deste PC, sem cópia (decisão do usuário; ARCHITECTURE.md, D11). |
| 7 — Testes na multimídia | Não iniciada | |

### Checklist da Fase 1

- [x] Verificar versões do Android SDK e ferramentas (ver [BUILD.md](BUILD.md))
- [x] Preparar o ambiente de desenvolvimento (JDK 21, Android SDK, Gradle Wrapper)
- [x] Criar o projeto
- [x] Analisar as informações de hardware disponíveis (capturas de tela) → [HARDWARE.md](HARDWARE.md)
- [x] Definir e documentar a arquitetura → [ARCHITECTURE.md](ARCHITECTURE.md)
- [x] Criar o APK de diagnóstico (somente leitura) e testá-lo (testes unitários + teste de tela na JVM)
- [ ] ~~Testar em emulador~~: o emulador 37.2.13 cai neste PC (ver [BUILD.md](BUILD.md))
- [x] Instalar o APK de diagnóstico na central (09/11/2026)
- [x] Registrar API real, densidade, área útil, GPU, pacotes (ZLINK, Bluetooth…) e linha de base de desempenho
- [x] Documentar as limitações do firmware confirmadas
- [x] Decidir Compose × Views com base nas medições → **Views nativas**
- [x] Tentar ADB via Wi-Fi → **indisponível**: opções do desenvolvedor bloqueadas pelo fabricante (ver [HARDWARE.md](HARDWARE.md), seção 5)

### Checklist da Fase 2

- [x] Paleta de cores (aprovada na calibração) → `colors.xml`, espelhada em `PcPalette.kt`
- [x] Tipografia (escala de 16 a 112 sp, fonte do sistema)
- [x] Espaçamentos, cantos e alvo mínimo de toque (64 dp)
- [x] Ícones próprios em vetor (14)
- [x] Componentes: botões, botão de mídia, atalho, cartão e barra inferior, com estados visuais
- [x] Tela de referência: Home em 1280 × 655 dp
- [x] Catálogo de componentes (aberto pelo Nyx Diagnóstico) e testes automatizados das regras
- [x] Medição embutida: a Home registra abertura e memória a cada abertura a frio
- [x] Primeira avaliação na central (0.2.0-ref): posições dos elementos aprovadas
- [x] Ajustes pedidos (0.2.1-ref): ZLINK exibido como "CarPlay" com ícone no estilo da Home, barra inferior sem rótulos, cartão de Navegação abrindo o CarPlay (Waze)
- [x] Aprovação das capturas da 0.2.1-ref (`docs/design/`)
- [x] Validação na central (09/11/2026): Home aprovada; abertura a frio de ~585 ms e ~10 MB de PSS
- [x] Teste "CarPlay pelo cabo": o iPhone não aparece no USB do Android; tela de espera própria inviável por esse caminho (ARCHITECTURE.md, D10)

### Checklist da Fase 3

- [x] Plano, decisões e riscos aprovados pelo usuário (09/11/2026); cartão de música abre o **player USB**
- [x] Navegação entre telas: barra inferior + quatro abas montadas sob demanda (ARCHITECTURE.md, D3)
- [x] Atalhos reais para os apps da central, com estado "Não encontrado" (D15)
- [x] Home: Bluetooth, CarPlay, Aplicativos, Configurações; cartões de Navegação (CarPlay) e Música (player USB)
- [x] Indicador de Wi-Fi real; Bluetooth só quando o Android informa "ligado"
- [x] Controles de mídia por teclas de mídia (D16)
- [x] Voltar: aba → Home; na Home, nada
- [x] Testes (42) e capturas de cada aba em `docs/design/`
- [x] Validação na central (0.3.0, 10/11/2026): todos os atalhos abrem o app certo
- [x] Investigação do player USB (0.3.1): o player do fabricante só toca e aceita comandos com a própria tela aberta, também no launcher original (D16)
- [x] **Botões de mídia × player USB**: o usuário escolheu um **player próprio** (10/11/2026); vai para a etapa 0.4.0 (D16)
- [ ] **Tela de espera do ZLINK**: tempos medidos (cabo 11 a 14 s, sem fio cerca de 16 s). Medição da 0.3.2: início da conexão pelo cabo identificado por sinal público; momento "CarPlay pronto" ainda sem sinal confiável (D10). Retomada na etapa 0.4.5 (D20)
- [x] Indicador de Bluetooth: o relatório da 0.3.2 mostra "ligado (indicador visível)"

### Etapa 0.4.0: tela inicial padrão e player próprio (aprovada em 10/11/2026)

**Decisões do usuário na aprovação:** ao ligar a central, o player **sempre espera um
toque** para retomar; as músicas do pendrive estão soltas (sem pastas), mas o player
**navega por pastas** (a raiz do pendrive é a primeira).

**Checklist**
- [x] Nyx Car pode ser escolhido como tela inicial (HOME), sem mudar nada sozinho
- [x] Botão Home do sistema volta à Home de qualquer aba
- [x] Configurações: "Tela inicial" (Apps padrão) e "Launcher original"
- [x] Player: aba Música por pastas, música atual com capa, progresso e controles
- [x] Cartão da Home mostra e controla a música; sem música, leva à aba Música
- [x] Foco de áudio, sessão de mídia, serviço só enquanto toca
- [x] Memória da última música; ao abrir, pausada; só toca com um toque
- [x] Pendrive removido: para e avisa; formato não suportado: pula e avisa
- [x] Diário do player e seção "Player próprio" no relatório do Nyx Diagnóstico
- [x] Gravação do ZLINK: Bluetooth por perfil, tipos de áudio e modo de áudio
- [x] Testes (73) e capturas `docs/design/*-v0.4.0.png`
- [x] Teste no carro (10/11/2026, relatório 20261110-203535): o som sai nos alto-falantes,
  o CarPlay pausa a música, ao ligar o carro a última música volta pausada e só toca com um
  toque, e o Nyx Car continua como tela inicial depois de desligar e ligar
- [x] Recuperação: **Configurações → Launcher original** abre a Tela de início 3 e o Home da
  central traz o Nyx Car de volta (testado pelo usuário em 11/11/2026)
- Achado: o player original abre sozinho ao conectar o pendrive. O usuário decidiu
  desativá-lo à mão (exceção à seção 9) e descartou contornar (etapa 0.4.1)
- [ ] Nova medição do ZLINK com os sinais extras

**Plano aprovado:**

Objetivo: numa só ida ao carro (D14), validar o Nyx Car como tela inicial e descobrir
se um player próprio consegue tocar o pendrive com a tela dele fechada.

**A. Nyx Car como tela inicial padrão** (antecipa a Fase 7, a pedido do usuário)
- O app passa a se declarar como tela inicial (`HOME`), mantendo o ícone comum. Uma única
  Home aberta por vez; o botão Home do sistema volta para a Home de qualquer aba.
- **Quem escolhe é o usuário**, pelo caminho padrão do Android: Configurações → Apps
  padrão → Tela inicial → Nyx Car (ou "Sempre" na pergunta que aparece ao pressionar
  Home). O app não altera nenhuma configuração sozinho.
- **Recuperação (seção 9):** a aba Configurações ganha "Launcher original" (abre o
  `com.android.launcher3`) e "Trocar tela inicial" (abre Apps padrão). Os caminhos do
  BUILD.md, seção 8, são testados na mesma viagem.
- **Riscos:** o firmware pode trazer o launcher original de volta ao ligar a central (há
  centrais que fazem isso). Se acontecer, não há como impedir dentro das regras do plano; o
  resultado é documentado. Se o Nyx Car falhar ao abrir, o Android mostra o aviso "parou",
  que leva às informações do app (limpar padrões ou desinstalar).

**B. Player próprio, primeira versão (viabilidade)**
- A aba **Música** vira o player: pastas do pendrive à esquerda; à direita, a música atual
  (título, artista, capa quando houver, progresso) e botões grandes. Os atalhos de BT
  Música, Rádio e do player original ficam numa linha menor.
- O **cartão da Home** mostra a música e o artista, e os botões ⏮ ▶ ⏭ controlam o player.
- Leitura do pendrive **por pastas**, sem depender da biblioteca de mídia do sistema. Exige a
  permissão de leitura do armazenamento, pedida uma vez.
- Reprodução com o `MediaPlayer` do Android, sem bibliotecas novas. Um serviço de reprodução
  existe só enquanto há música (o Android exige uma notificação durante a reprodução).
  Sessão de mídia padrão e foco de áudio: pausa quando o CarPlay, o BT Música ou uma ligação
  assumem o som, e retoma depois de interrupções curtas.
- Guarda a última pasta, música e posição. Ao ligar a central, o cartão mostra a última
  música pausada; um toque retoma (na v1, o player não toca sozinho).
- Pendrive removido: o player para e avisa.
- **Fora da v1:** aleatório e repetir, artistas e álbuns, busca, listas de reprodução.
- **Riscos:**
  - não se sabe se o som de um app comum sai nos alto-falantes como o do player do
    fabricante (é o primeiro ponto testado; se falhar, a etapa para aí);
  - formatos dependem dos decodificadores da central;
  - no Android 8.1, o caminho do pendrive vem de uma função interna do sistema, com uma
    alternativa pública caso ela falhe;
  - o player original pode abrir sozinho ao inserir o pendrive (não dá para impedir);
  - o Rádio pode não respeitar o foco de áudio.

**C. Tela de espera do ZLINK: próxima medição**
- A gravação do Nyx Diagnóstico passa a registrar também os estados das conexões Bluetooth
  (sem nome nem endereço do aparelho) e os tipos de áudio tocando, em busca de um sinal
  confiável de "CarPlay pronto". Nenhuma tela por cima do ZLINK nesta etapa.

### Etapa 0.4.1: deslizar entre abas e player original (pedida em 10/11/2026)

**Pedidos do usuário depois do teste da 0.4.0:** trocar de aba deslizando o dedo para a
direita ou para a esquerda; impedir que o player original abra sozinho ao conectar o
pendrive.

**Decisões do usuário:**
- **Player original:** quebrar a regra da seção 9 só para ele, **desativando-o à mão** nas
  Configurações do Android (exceção registrada na seção 9). O Nyx Car não consegue
  desativar outro app: isso exige permissão de sistema, root ou ADB.
- **Sem contorno:** a alternativa de o Nyx Car voltar sozinho para a frente quando o
  player original abrir foi descartada (o player apareceria e sumiria). Se o botão
  **Desativar** estiver cinza, o player original fica como está.
- Deslize na ordem da barra, **parando nas pontas** (recomendação aceita; sem volta de
  Configurações para a Home).

**Checklist**
- [x] Deslize horizontal entre abas, sem afetar toques nem a rolagem vertical (ARCHITECTURE.md, D3)
- [x] Atalhos de apps desativados: "Desativado"; na aba Música, o atalho some
- [x] Nyx Diagnóstico: situação do player original e botão para a página dele nas Configurações
- [x] Diário: erros que fecham o app, sem caminhos de arquivo (D18)
- [x] Medição de abertura separando processos reabertos em segundo plano
- [x] Testes (85)
- [x] Teste no carro (11/11/2026): deslize aprovado pelo usuário ("está bem bacana"). Desativar
  o player original deixou de ser necessário (ver a atualização abaixo)
- [x] Recuperação (validada em 11/11/2026; ver a etapa 0.4.0)

**Atualização (11/11/2026):** o usuário encontrou a causa da abertura automática: a opção
**Reprodução automática** em Configurações do Veículo → Geral (ajuste comum, não de fábrica).
Desligada, o player original não abre mais ao conectar o pendrive, e desativar o app deixou
de ser necessário. O usuário também reiniciou a central por completo: o Nyx Car abriu
como tela inicial.

### Etapa 0.4.2: sem o atalho do player USB (pedida em 11/11/2026)

- [x] Remover "Música USB" da aba Música (fica BT Música e Rádio) e de Aplicativos (11 apps
  em 4 colunas). O Vídeo do fabricante continua.
- [x] Corrigir a Home descendo do topo ao voltar do CarPlay (barra de status estável;
  relatado pelo usuário em 11/11/2026)
- [x] Teste no carro (11/11/2026): "Música USB" sumiu das duas abas e a Home não desce mais
  ao voltar do CarPlay. O relatório confirma `persist.sys.autoplay = 0` (Reprodução
  automática desligada)

### Etapa 0.4.3: Play Store em Aplicativos e novo nome (pedidos em 11/11/2026)

Pedidos do usuário depois do teste: ocupar o lugar vazio da aba Aplicativos com a **Play
Store**, com ícone no padrão do launcher, e trocar o nome "Premium Car", genérico demais,
por **Nyx Car**.

- [x] Novo nome em tudo o que aparece (app, "Nyx Diagnóstico", textos, relatório, APK). O
  identificador `com.premiumcar.launcher` continua, para atualizar por cima sem perder a tela
  inicial, as permissões e a última música (ARCHITECTURE.md, D19)

- [x] Atalho "Play Store" (`com.android.vending`) no último lugar da grade (12 apps, 3 × 4),
  com ícone próprio em traço (sacola com o símbolo de reproduzir; não é o logotipo oficial)
- [x] Nyx Diagnóstico: seção "Data e hora" (hora e fuso automáticos, tempo ligado e aviso
  quando o relógio parece estar na data do firmware). Motivo: depois de uma reinicialização
  completa, o relógio da central voltou para 16/04 (data do firmware) e ficou errado por
  cerca de 3 horas (HARDWARE.md, seção 5)
- [x] Análise da cópia da pasta `Android` da central (HARDWARE.md, seção 8): nada nela muda o
  launcher
- [x] Testes (88)
- [x] Teste no carro (11/11/2026, relatório `nyx-car-diagnostico-20261111-171950.txt`):
  instalada por cima, "está tudo certo" segundo o usuário; o Nyx Car continuou como tela
  inicial depois da troca de nome; a Play Store abre; hora e fuso automáticos ligados
- [x] Recuperação (validada em 11/11/2026; ver a etapa 0.4.0)

**Riscos da 0.4.3:** a Play Store pode pedir uma conta Google ou atualizações ao abrir; isso é
do app do Google, não do launcher. Nenhuma configuração da central é alterada.

### Etapa 0.4.4: favoritos e lista de todos os aplicativos (aprovada em 11/11/2026)

Primeiros itens dos próximos passos (o código já está no GitHub; a chave e o identificador do
app ficam como estão). Itens "Lista de aplicativos" e "Favoritos" da Fase 4 do plano original.

**Decisões do usuário:**
- Seletor **Favoritos | Todos** na aba Aplicativos; a aba abre sempre em "Favoritos".
- **Favoritos sempre 12**, para a grade 3 × 4 ficar completa. Edição pelo botão **Editar** (só o ícone de lápis)
  (só na página Favoritos), com **troca por lugar**: toque num favorito → escolha o app na
  lista; escolher um app que já é favorito troca os dois de lugar. Cada troca **vale na hora**;
  "Restaurar padrão" volta aos 12 atalhos de antes e "Concluir" sai da edição.
- **Ícones de traço** desenhados para cada app instalado; apps instalados no futuro recebem um
  ícone de traço genérico (o usuário não pretende instalar outros apps, para não pesar a
  central).
- **Música USB nunca aparece.** O **Theme** também fica escondido.

**Checklist**
- [x] Lista em ordem alfabética, com os mesmos atalhos dos Favoritos (4 colunas, 3 linhas por
  tela) e barra de rolagem sempre visível: os 20 apps ficam em 5 linhas. Primeira versão tinha
  atalhos menores em 6 colunas; o usuário pediu o mesmo tamanho dos Favoritos (11/11/2026)
- [x] Nomes e ícones do Nyx Car para os apps conhecidos ("CarPlay", "Equalizador",
  "Informações do carro"…); 4 ícones novos: Chrome (globo), Google (lupa), Instruções
  (livro), Scan de imagens (moldura); ícone genérico para apps desconhecidos
- [x] Escondidos: Nyx Car, entradas sem nome, atualizadores de firmware, MCU e CAN (seção 9),
  Theme e Música USB
- [x] Montada em segundo plano no primeiro toque em "Todos" e refeita ao voltar de outro app;
  não carrega ícones de outros apps
- [x] Favoritos: edição por lugar, sem repetição, salvos no aparelho (`SharedPreferences`);
  Voltar sai da escolha e depois da edição; qualquer app de "Todos" pode ser favorito
- [x] Relatório: os favoritos (só nomes) e o que aparece e fica escondido em "Todos"
- [x] Testes (103) e capturas `aba-apps-v0.4.4.png`, `aba-apps-todos-v0.4.4.png`,
  `aba-apps-todos-fim-v0.4.4.png`, `aba-apps-editar-v0.4.4.png` e `aba-apps-trocar-v0.4.4.png`
- [x] Teste no carro (11/11/2026, relatório `nyx-car-diagnostico-20261111-192835.txt`):
  aprovado pelo usuário, inclusive a rolagem de "Todos" e o botão de lápis; o relatório mostra
  os 20 apps esperados em "Todos", nenhum com ícone genérico, e os 7 escondidos

**Riscos da 0.4.4:** um app de fábrica pode ter um nome diferente do visto no relatório e
aparecer com o ícone genérico (o relatório mostra quais); abrir apps pela lista não muda
nenhuma configuração da central. Os favoritos ficam guardados no app: desinstalá-lo os
apaga (volta o padrão).

### Etapa 0.4.5: volta automática do CarPlay e medição do "CarPlay pronto" (aprovada em 11/11/2026)

**Pedido do usuário:** a tela de espera do ZLINK "quebra toda a imersão". Proposta dele: abrir o
ZLINK só quando o iPhone aparecer no USB (Apple `05ac`) e voltar sozinho para o launcher quando
o iPhone for desconectado. Análise com os dados (ARCHITECTURE.md, D20): o ZLINK já abre sozinho
~3 s depois de o iPhone aparecer, e a tela feia é a da conexão (~8 s), que o launcher não
acelera; a volta automática é viável.

**Decisões do usuário:** fazer a 0.4.5 (volta automática + medição) e **estudar o B1** (janela
do Nyx Car por cima do ZLINK durante a conexão), com autorização para quebrar regras iniciais do
projeto (seção 9, terceira exceção). B2 (empurrar o ZLINK para trás) descartado.

**Checklist**
- [x] Volta automática: com o ZLINK na tela, o fim do CarPlay (cabo fora por 1,5 s ou o
  Bluetooth da central religando depois de ter sido desligado pelo CarPlay) traz o Nyx Car
  para a frente, como o botão Home; com outro app na tela, nada muda
- [x] Diário: iPhone no USB, cabo conectado, Bluetooth desligado pelo CarPlay, fim da sessão e
  o que foi feito (sem nome nem endereço de aparelho)
- [x] Gravação dos testes: tráfego de rede por interface (só faixas de bytes por segundo) e as
  decisões da volta automática na linha do tempo; instruções novas (apertar o Home da central
  quando o CarPlay aparecer marca o momento real)
- [x] Testes (119)
- [x] Teste no carro (12/11/2026, relatório `nyx-car-diagnostico-20261112-164448.txt`): sinal de
  "CarPlay pronto" encontrado (tráfego de rede do vídeo, ~10 s); sinais do fim confirmados
  (Bluetooth religa, depois o USB cai). A volta com o ZLINK na tela ainda não foi exercitada:
  em todas as desconexões o Nyx Car já estava na tela
- [x] Com os dados: sinal escolhido e B1 proposto (ARCHITECTURE.md, D20)

### Etapa 0.4.6: correções do teste da 0.4.5 (12/11/2026)

- [x] Nyx Diagnóstico sem "pulo" ao voltar do ZLINK (relatado pelo usuário): barra de status
  estável também no diagnóstico e no catálogo
- [x] Relatório: "Testes no carro" (com o diário) lidos na hora de salvar
- [x] Volta automática: espera de 2 s para qualquer sinal de fim, cancelada se o iPhone
  reaparecer no USB (mau contato)
- [x] Testes (124)
- [x] Teste no carro (12/11/2026, relatório `nyx-car-diagnostico-20261112-172549.txt`): **a
  volta automática funciona**, pelo cabo e ao desligar o Bluetooth (sem fio), ~2 s depois; 5
  voltas no diário. Com outro app na tela (Configurações do Veículo) e com o Nyx Car na tela,
  nada mudou, como previsto. O diário do relatório já inclui as gravações
- [x] "Pulo" do Nyx Diagnóstico ao voltar do ZLINK: **sumiu** (confirmado pelo usuário)
- [ ] Mau contato: não testado (não é urgente, segundo o usuário; fica para depois)

### Etapa 0.4.7: tela de espera do CarPlay do Nyx Car, o B1 (pedida em 12/11/2026)

**Decisão do usuário:** seguir com o B1 **pelo cabo e também sem fio** (a proposta era só o
cabo). Commit e push da 0.4.5 e da 0.4.6 feitos antes.

**Checklist**
- [x] Janela sobreposta "Conectando ao CarPlay" no padrão do Nyx Car, sem animação; um toque
  fecha; não recebe o foco (Voltar e Home vão para o app de baixo)
- [x] Abre pelo cabo quando o iPhone aparece no USB; sem fio, quando o ZLINK cobre a Home sem
  o usuário ter aberto um app e com o Bluetooth da central ainda ligado
- [x] Fecha quando o vídeo chega (tráfego total, lido a cada 250 ms só com a tela aberta:
  ≥ 512 KB/s pelo cabo, ≥ 160 KB/s sem fio, depois de o Bluetooth da central desligar), por
  tempo máximo (20 s / 25 s), com um toque ou quando a Home volta
- [x] Na desconexão, "CarPlay desconectado" cobre a tela de espera do ZLINK até a volta
  automática; num mau contato, volta para "Conectando"
- [x] Permissão "Sobrepor a outros apps" pelo Nyx Diagnóstico (cartão 5), com prévia de 5 s;
  relatório mostra a situação
- [x] Testes (139) e capturas `docs/design/carplay-*-v0.4.7.png`
- [x] Teste no carro (12/11/2026, relatório `nyx-car-diagnostico-20261112-181412.txt`): pelo
  cabo funciona (vídeo detectado de 9,0 a 9,7 s; num teste o ZLINK apareceu ~1 s no fim); sem
  fio, a tela só entrou com a Home na tela e o ZLINK aparecia ~1 s antes dela; a desconexão
  mostrava ~2 s de espera. Pedidos do usuário: ver a etapa 0.4.8

### Etapa 0.4.8: ajustes do B1 e CarPlay sem iPhone (pedidos em 12/11/2026)

**Pedidos do usuário:** fechar a tela de espera 2 s depois do sinal de CarPlay pronto; voltar
ao Nyx Car na hora ao desconectar (sem a proteção contra mau contato); a tela de espera sem
fio também fora da Home e sem o ZLINK aparecendo antes; e, ao tocar em CarPlay (ou no cartão
de Navegação) sem nada conectado, não cair na tela parada do ZLINK.

**Checklist**
- [x] Tela de espera fecha 2 s depois do vídeo
- [x] Volta ao Nyx Car na hora; "CarPlay desconectado" cobre a espera do ZLINK até a Home
- [x] Leitura rápida do app na tela (propriedade do sistema, sem `getprop`); vigia do ZLINK a
  cada 200 ms por 2,5 s quando a Home sai da tela (`onPause`) e por 20 s quando o iPhone se
  conecta pelo Bluetooth ou o ponto de acesso Wi-Fi liga
- [x] "Aguardando o iPhone" ao tocar em CarPlay sem nada conectado (todas as entradas passam
  pelo `AppLauncher`); toque volta, toque longo abre o ZLINK, 1 min de tempo máximo; o iPhone
  conectando troca para "Conectando"
- [x] Testes (144) e captura `docs/design/carplay-aguardando-iphone-v0.4.8.png`
- [x] Teste no carro (12/11/2026, relatório `nyx-car-diagnostico-20261112-193615.txt`): pelo
  cabo, preciso (vídeo de 8,3 a 10,8 s depois de a tela abrir); sem fio com o Nyx Diagnóstico
  na tela, o ZLINK ainda aparecia ~1 s antes da tela; na desconexão, a espera do ZLINK aparecia
  por um instante antes de "CarPlay desconectado"; um cabo tirado logo depois de conectado
  deixava a tela até o tempo máximo (20 s) e depois o ZLINK parado. Pedidos do usuário: ver a
  etapa 0.4.9

### Etapa 0.4.9: conexão interrompida e fim mais rápido (pedidos em 12/11/2026)

**Pedidos do usuário:** se a conexão cair enquanto o CarPlay carrega, manter a nossa tela por
algum tempo e, se nada acontecer, fechar a tela e o ZLINK; na desconexão, mostrar "CarPlay
desconectado" na hora, antes da tela do ZLINK, por ~2 s, e então fechar a tela e o ZLINK; e
acabar com o ZLINK aparecendo ~1 s antes da nossa tela no sem fio com outro app aberto.

**Checklist**
- [x] "Conexão interrompida — Aguardando o iPhone voltar…" por 5 s quando a conexão cai antes
  do CarPlay aparecer (cabo ou Bluetooth da central religando antes do vídeo; pelo cabo, também
  sem "cabo conectado" em 6 s); o iPhone voltando segue a conexão; sem volta, o Nyx Car volta
  para a frente (o ZLINK sai) e a tela fecha quando ele aparece
- [x] "CarPlay desconectado" na hora e por 2 s, com o Nyx Car voltando por baixo
- [x] Tempo máximo sem o CarPlay começar (Bluetooth da central não desligou): a tela e o ZLINK
  saem
- [x] Thread própria do CarPlay, com prioridade de tela; leitura do app na tela sem passar pela
  fila de baixa prioridade; vigia a cada 100 ms
- [x] Janela reservada (1 px, transparente, sem toque) durante o CarPlay e por 20 s depois de o
  iPhone se conectar pelo Bluetooth
- [x] Gravação: horário em milissegundos, sinais a cada 100 ms (`FastSignals`) e avisos do
  sistema numa thread separada
- [x] Testes (156) e captura `docs/design/carplay-interrompida-v0.4.9.png`
- [x] Teste no carro (12/11/2026, relatório `nyx-car-diagnostico-20261112-205612.txt`): fim
  percebido 50 a 65 ms depois do Bluetooth religar; sem fio, a tela cobre o ZLINK até 50 ms
  depois de ele chegar; "Conexão interrompida" entrou no mau contato, mas virou "Conectando
  (sem fio)" 4 s depois; nenhum arquivo de sinal do USB ou da rede é legível. Pedido do
  usuário: ver a etapa 0.4.10

### Etapa 0.4.10: toque na tela de espera fecha o ZLINK (pedido em 12/11/2026)

**Pedido do usuário:** num mau contato ou ao conectar e desconectar rápido, tocar em "Toque
para fechar" deve fechar também o ZLINK, e não só a nossa tela, deixando a espera do ZLINK.

**Checklist**
- [x] Toque em "Conectando ao CarPlay" (e "Preparando…"): o Nyx Car volta e o ZLINK sai da
  frente; tocar e segurar fecha só a nossa tela
- [x] Vigia do sem fio armada só com o iPhone conectado pelo Bluetooth (não com as tentativas
  da central) e disparada só quando o ZLINK chega à tela
- [x] Nada substitui "Conexão interrompida" e "CarPlay desconectado"; o CarPlay entrando de
  novo durante "Conexão interrompida" segue a conexão
- [x] Leitura de arquivos do sistema retirada da gravação (não legíveis)
- [x] Testes (158) e captura `docs/design/carplay-espera-cabo-v0.4.10.png`
- [x] Teste no carro (junto com a 0.4.11): o toque em "Conectando" trouxe a Home

### Etapa 0.4.11: fim do CarPlay pela tela do ZLINK (pedida em 12/11/2026)

**Pedido do usuário:** "a tela de espera do ZLINK ainda aparece antes de CarPlay desconectado
por alguns segundos [...] eu autorizo o caminho do serviço de acessibilidade restrito ao
zlink". Quarta exceção da seção 9.

**Checklist**
- [x] Serviço de acessibilidade "Nyx Car — fim do CarPlay", só com avisos do ZLINK
  (`packageNames`), sem gestos nem ações; lê a estrutura da tela, nunca o conteúdo dos textos
- [x] Aprende as telas de espera do ZLINK com o Bluetooth da central ligado; reconhece a tela
  do CarPlay (Bluetooth desligado pelo CarPlay, ≥ 1,5 s); o ZLINK voltando para a espera
  durante o CarPlay mostra "CarPlay desconectado" na hora; na dúvida, nada muda
- [x] O fim pelo Bluetooth, segundos depois, não repete a volta e registra quanto antes a tela
  avisou
- [x] Nyx Diagnóstico: cartão 6 (abrir a Acessibilidade, situação) e linha no relatório
- [x] Testes (172)
- [x] Teste no carro (12/11/2026, relatório `nyx-car-diagnostico-20261112-222525.txt`): a
  Acessibilidade existe e o serviço ligou; o fim pela tela funcionou 1 vez (0,14 s depois da
  troca de tela do ZLINK, 1,1 s antes do cabo); nas 4 primeiras, a tela de espera do fim ainda
  não era conhecida e o fim veio pelo Bluetooth, ~0,6 s depois da troca. Numa conexão lenta
  pelo cabo, o tempo máximo tirou o ZLINK da frente com ele ainda "conectando". Ver a etapa
  0.4.12

### Etapa 0.4.12: fim pela imagem do CarPlay (12/11/2026)

**Motivo:** o teste da 0.4.11 mostrou que o fim pela tela só funcionava depois de a tela de
espera do fim ser aprendida, e que as telas do CarPlay têm um elemento próprio (a imagem do
CarPlay, `phone_content_event_delegate`) que nenhuma tela de espera tem.

**Checklist**
- [x] A imagem do CarPlay sumindo durante o CarPlay (conferida de novo 100 ms depois) é o fim,
  mesmo numa tela nunca vista; a tela fica aprendida como espera
- [x] As piscadas da tela do CarPlay sem fio (a cada ~6 s, com a imagem) não contam
- [x] A imagem do CarPlay nunca é aprendida como espera; uma tela sem ela nunca vira tela do
  CarPlay
- [x] Pelo cabo, com o ZLINK "conectando", o tempo máximo espera até 1 min no total
- [x] Testes (179)
- [ ] Teste no carro (BUILD.md, seção 6). Na primeira tentativa (12/11/2026, relatório
  `nyx-car-diagnostico-20261112-225020.txt`) o serviço de acessibilidade estava desligado,
  provavelmente pelo carro desligando (HARDWARE.md, seção 5): a regra nova não foi exercitada

**Riscos da 0.4.12:**
- depende do nome interno de um elemento do ZLINK (`phone_content…`); se o ZLINK mudar, a
  regra deixa de valer sozinha e volta o aprendizado da 0.4.11, e depois o Bluetooth;
- o tempo entre tirar o cabo e o ZLINK trocar de tela é do ZLINK e não aparece em nenhum
  aviso: nesse intervalo, a última imagem do CarPlay continua na tela.

**Riscos da 0.4.11:**
- **a Acessibilidade pode não existir** nas Configurações deste firmware; sem ela, o serviço
  não pode ser ligado (sem ADB não há outro caminho) e tudo continua como na 0.4.10;
- se a tela de espera depois do fim for diferente da de antes da conexão, o primeiro fim ainda
  vem pelo Bluetooth; a linha do tempo mostra as telas para ajustar;
- com qualquer serviço de acessibilidade ligado, os apps passam a gerar avisos de
  acessibilidade (um pouco mais de trabalho para a central); o Android filtra e só entrega ao
  Nyx Car os do ZLINK;
- se o ZLINK mostrar uma tela de espera conhecida com o CarPlay ainda conectado, o Nyx Car
  volta para a frente; tocar em CarPlay reabre o ZLINK direto (o Bluetooth ainda desligado
  indica o CarPlay conectado).

**Riscos da 0.4.10:**
- tocar em "Conectando" com o CarPlay quase pronto também tira o ZLINK da frente; o CarPlay
  continua conectando por baixo e abre direto pelo atalho CarPlay;
- o fim do CarPlay continua chegando depois de o ZLINK trocar de tela (nenhum aviso mais cedo
  é legível): se a espera do ZLINK ainda aparecer por um instante, o único caminho conhecido é
  o serviço de acessibilidade restrito ao ZLINK, que depende de autorização.

**Riscos da 0.4.9:**
- o fim do CarPlay ainda é percebido pelo Bluetooth da central religando ou pelo cabo caindo,
  que chegam **depois** de o ZLINK trocar de tela: a 0.4.9 tira o atraso do Nyx Car, mas a
  espera do ZLINK ainda pode aparecer por um instante. A gravação procura um aviso mais cedo;
  se não houver, o próximo passo seria um serviço de acessibilidade restrito ao ZLINK (depende
  de autorização: ver ARCHITECTURE.md, D20);
- o tempo máximo agora fecha o ZLINK quando o CarPlay não começou: se o iPhone estiver
  esperando uma confirmação na tela dele (primeira conexão), o Nyx Car volta antes; tocar em
  CarPlay de novo abre o ZLINK direto (o cabo está conectado);
- a janela reservada pode fazer o Android 8 mostrar a notificação "Nyx Car está sendo exibido
  sobre outros apps" enquanto o CarPlay estiver em uso.

**Riscos da 0.4.8:**
- "CarPlay conectado" é deduzido do Bluetooth da central desligado (o ZLINK o desliga durante o
  CarPlay) ou do cabo conectado; se o Bluetooth da central for desligado à mão, o CarPlay abre
  o ZLINK direto, como antes;
- sem fio fora da Home, a vigia depende de o iPhone se conectar pelo Bluetooth (ou de o ponto
  de acesso ligar) no começo; se o iPhone já estava conectado, só a Home saindo da tela arma
  a vigia;
- sem a proteção contra mau contato, um cabo frouxo traz o Nyx Car para a frente e o ZLINK
  reabre sozinho em seguida.


**Riscos da 0.4.7:**
- o tráfego usado é o total do aparelho: um download pelo Wi-Fi durante a conexão poderia
  fechar a tela cedo (a regra exige antes o Bluetooth da central desligado pelo CarPlay);
- se o vídeo vier com menos tráfego que o medido, a tela fica até o tempo máximo (20 a 25 s),
  cobrindo o CarPlay já pronto; um toque fecha;
- sem fio, a tela só entra se o Nyx Car estiver na tela quando o ZLINK abrir sozinho;
- a permissão "Sobrepor a outros apps" pode não existir neste firmware (a página das
  informações do app abre no lugar); sem ela, tudo fica como na 0.4.6.

**Riscos da 0.4.5:**
- a volta pode não acontecer se nenhum dos dois sinais chegar ao desconectar (nenhuma
  gravação pegou uma desconexão); o diário vai mostrar;
- um mau contato maior que 1,5 s traz o Nyx Car para a frente; ao reconectar, o ZLINK abre
  sozinho de novo;
- o CarPlay sem fio depende só do sinal do Bluetooth (hipótese);
- se o Android encerrar o processo do Nyx Car durante o CarPlay, não há volta automática.

**Riscos da 0.4.1:**
- deslize trocar de aba sem querer (solavanco) ou exigir arrasto longo demais: os limites
  são ajustáveis depois do teste;
- outro app da central que abre o player ao conectar o pendrive pode mostrar erro ao não
  encontrá-lo; nesse caso, o usuário reativa o player original;
- o Vídeo do fabricante é o mesmo app e também fica desativado.

---

*Plano original, mantido como referência:*

## 1. Visão geral

Desenvolver um aplicativo Android personalizado para uma central multimídia automotiva, inspirado nas interfaces premium da BMW iDrive e Mercedes-Benz MBUX.

O objetivo é criar uma experiência visual sofisticada, moderna, intuitiva e otimizada para hardware limitado.

O aplicativo deverá funcionar inicialmente como um launcher independente, sem modificar o launcher original, o firmware ou qualquer componente do aplicativo ZLINK.

## 2. Informações do hardware de destino

- Sistema operacional: Android 13.
- Resolução: 1280 × 720.
- Memória RAM: 2 GB.
- Processador: quad-core MediaTek (modelo exato a identificar).
- Aplicativo de projeção: ZLINK.
- Conectividade: Wi-Fi e Bluetooth.
- Instalação de aplicativos: disponível pelo gerenciador de arquivos via USB/pendrive.
- ADB Wireless: não localizado até o momento.

Não presumir que todos os recursos do Android 13 estejam disponíveis. Investigar as particularidades do firmware.

> Atualização da Fase 1 (medido na central): o sistema real é **Android 8.1 (API 27)**,
> apenas rotulado como 13. CPU AC8227L com 4× Cortex-A7 a 1,1 GHz (32 bits), GPU
> Mali-450, tela a 160 dpi. Detalhes em [HARDWARE.md](HARDWARE.md).

## 3. Direção visual

Criar uma identidade própria inspirada em duas referências:

### Mercedes-Benz MBUX
- Sofisticação e minimalismo.
- Fundo escuro e superfícies grafite.
- Tipografia limpa e elegante.
- Iluminação azul ou turquesa.
- Hierarquia visual bem definida.

### BMW iDrive
- Widgets e cartões informativos.
- Navegação intuitiva.
- Atalhos para funções importantes.
- Organização funcional da tela.

### Identidade visual proposta

- Background principal: grafite profundo.
- Superfícies: tons de carvão e cinza azulado.
- Cor de destaque: turquesa/ciano.
- Textos principais: branco suave.
- Textos secundários: cinza azulado.
- Bordas discretas.
- Ícones minimalistas e consistentes.
- Cantos arredondados moderados.

Evitar excesso de gradientes, efeitos de vidro, sombras pesadas e animações desnecessárias.

Não reproduzir logotipos ou elementos proprietários das montadoras.

## 4. Arquitetura e tecnologias

Avaliar e utilizar preferencialmente:

- Kotlin.
- Android SDK.
- Jetpack Compose, caso os testes comprovem desempenho adequado.
- Material Design 3 com personalização visual.
- Arquitetura simples e modular.
- Coroutines quando necessário.
- DataStore apenas se houver necessidade de persistência.

Priorizar componentes nativos e dependências reduzidas.

Não adotar bibliotecas pesadas sem justificativa.

Antes de implementar, definir a arquitetura e documentar as decisões técnicas.

## 5. Funcionalidades da primeira versão

### Tela inicial

- Relógio digital em destaque.
- Data.
- Indicadores de conectividade, quando disponíveis.
- Cartão de navegação.
- Cartão de música.
- Atalho para Bluetooth.
- Atalho para ZLINK.
- Acesso à lista de aplicativos.
- Atalho para configurações.

### Navegação

Criar uma barra inferior com acesso rápido a:

- Home.
- Música.
- Aplicativos.
- Conectividade.
- Configurações.

Os atalhos devem abrir os aplicativos instalados por meio das APIs normais do Android.

### Tela de aplicativos

- Listar aplicativos instalados e disponíveis para inicialização.
- Identificar ícone e nome.
- Permitir abrir aplicativos.
- Dar destaque aos aplicativos utilizados no carro.

### Configurações próprias

- Alterar tema, quando aplicável.
- Configurar aplicativos favoritos.
- Configurar atalhos da tela inicial.
- Escolher formato de relógio.
- Consultar informações da versão do launcher.

## 6. Integração com ZLINK

O ZLINK deverá permanecer completamente intacto.

Na primeira versão:

- Não modificar seu APK.
- Não alterar permissões.
- Não realizar engenharia reversa.
- Não interceptar sua comunicação.
- Não modificar serviços de conexão do CarPlay.
- Não substituir componentes proprietários.

Apenas disponibilizar um atalho para abrir o ZLINK.

Futuramente, investigar se é possível criar uma experiência de espera própria antes da abertura do ZLINK, respeitando as limitações do Android e do fabricante.

Não prometer substituir a tela interna de espera do ZLINK sem comprovar a viabilidade técnica.

## 7. Performance

O hardware possui somente 2 GB de RAM.

Portanto:

- Evitar serviços em segundo plano desnecessários.
- Evitar animações contínuas.
- Não utilizar vídeos como background.
- Evitar bibliotecas de terceiros desnecessárias.
- Otimizar imagens e ícones.
- Evitar atualizações frequentes de estado.
- Evitar efeitos gráficos pesados.
- Implementar carregamento eficiente dos aplicativos.
- Evitar inicialização automática até validar o funcionamento.

Medir o consumo de memória, tempo de inicialização e fluidez na central.

Não inventar metas de desempenho antes de obter uma linha de base real.

## 8. Estratégia de desenvolvimento

### Fase 1 — Diagnóstico

- Verificar versões do Android SDK e ferramentas.
- Preparar o ambiente de desenvolvimento.
- Criar o projeto.
- Identificar a arquitetura mais adequada.
- Verificar APIs disponíveis no Android 13.
- Documentar limitações do firmware.

Não executar comandos que modifiquem a central.

### Fase 2 — Design System

Criar:

- Paleta de cores.
- Tipografia.
- Componentes de cartões.
- Botões automotivos.
- Ícones.
- Barra de navegação.
- Espaçamentos.
- Estados visuais.

Produzir uma tela de referência antes de implementar funcionalidades.

### Fase 3 — Interface principal

Implementar:

- Home.
- Relógio.
- Cartões.
- Barra inferior.
- Navegação entre telas.
- Atalhos.

Garantir adaptação à resolução 1280 × 720 e à orientação horizontal.

### Fase 4 — Funcionalidades

Implementar:

- Lista de aplicativos.
- Abertura de aplicativos.
- Favoritos.
- Atalho para ZLINK.
- Configurações próprias.

Testar cada funcionalidade isoladamente.

### Fase 5 — Otimização

- Analisar consumo de RAM.
- Avaliar uso de CPU.
- Eliminar recomposições desnecessárias.
- Avaliar inicialização.
- Testar fluidez em hardware equivalente, se disponível.

### Fase 6 — Distribuição

Gerar APK de instalação manual.

Documentar:

- Como compilar.
- Onde encontrar o APK.
- Como transferir via pendrive.
- Como instalar.
- Como desinstalar.
- Como recuperar a configuração original.

### Fase 7 — Testes na multimídia

Instalar o aplicativo sem substituir o launcher original.

Validar:

- Resolução.
- Orientação.
- Escala dos componentes.
- Legibilidade.
- Toques.
- Abertura dos aplicativos.
- Abertura do ZLINK.
- Retorno à interface.
- Consumo de memória.
- Estabilidade.

## 9. Segurança e restrições obrigatórias

- Não realizar root.
- Não desbloquear bootloader.
- Não modificar firmware.
- Não alterar partições do sistema.
- Não remover aplicativos originais.
- Não desinstalar ZLINK.
- Não alterar configurações de fábrica.
- Não realizar atualização de firmware.
- Não executar comandos destrutivos na central.

Todas as operações na central deverão ser previamente explicadas e autorizadas.

O launcher original deverá continuar disponível para recuperação.

**Exceção autorizada pelo usuário (10/11/2026):** desativar o player original
(`com.hcn.AutoMediaPlayer`, apps "Música" e "Vídeo"), porque ele abre sozinho ao conectar o
pendrive e disputa a tela com o player próprio. Vale só para este app, só **desativar**
(nunca desinstalar) e só pelas Configurações do Android, à mão. É reversível no mesmo lugar
("Ativar"); passo a passo e volta em [BUILD.md](BUILD.md), seção 8. As demais regras
continuam valendo.

**Segunda exceção autorizada pelo usuário (11/11/2026): menu de fábrica.** Alterar opções do
menu de fábrica (Configurações do Veículo → Fábrica) quando isso melhorar o funcionamento do
Nyx Car. Regras desta exceção:
- cada opção é **explicada antes** (o que deve mudar e o risco) e alterada pelo usuário, à mão;
- **uma opção por vez**, com **foto do valor original** antes, teste logo depois e volta ao
  valor original se algo estranho acontecer;
- **nunca:** tipo de carro e CAN, câmera de ré e detecção de ré, som (Audio AMP), antena do
  rádio, imagem e saída AV, calibração de toque e botões, Acc Delay, Quick Boot, protocolo
  e modo das portas USB, logotipo e animação de partida, código QR e código de ativação
  (licença), restauração de fábrica e atualização de firmware;
- o código de ativação e outros identificadores não são registrados nem compartilhados.
As demais regras desta seção continuam valendo.

**Terceira exceção autorizada pelo usuário (11/11/2026): ZLINK e regras iniciais.** Ao aprovar a
etapa 0.4.5, o usuário autorizou estudar uma janela do Nyx Car **sobreposta à tela de espera
do ZLINK** (B1; seção 6: "não substituir componentes proprietários") e disse: "está autorizado
quebrar regras que foram definidas inicialmente no projeto". Interpretação adotada:
- vale para as regras de integração com o ZLINK (seção 6) e de comportamento do launcher
  (por exemplo, a volta automática para a frente), no que for preciso para a tela de espera;
- **cada quebra continua sendo explicada antes**, com risco e caminho de volta, e o usuário
  confirma;
- não são tocados sem um pedido específico: root, bootloader, firmware e suas atualizações,
  partições, comandos destrutivos, desinstalação de apps originais ou do ZLINK, e o que a
  segunda exceção proíbe no menu de fábrica;
- o APK do ZLINK continua sem modificação.

**Quarta exceção autorizada pelo usuário (12/11/2026): serviço de acessibilidade restrito ao
ZLINK.** Depois do teste da 0.4.9, o usuário escreveu: "a tela de espera do ZLINK ainda aparece
antes de CarPlay desconectado por alguns segundos e como isso me incomoda eu autorizo o caminho
do serviço de acessibilidade restrito ao zlink". Regras desta exceção (etapa 0.4.11):
- o serviço só recebe avisos do ZLINK e só lê a janela dele;
- lê só a estrutura da tela (tipos e nomes internos dos elementos, se têm texto, se são
  tocáveis); o conteúdo dos textos não é guardado, registrado nem enviado;
- não executa gestos nem ações, não toca no ZLINK nem no CarPlay;
- quem liga e desliga é o usuário, em Configurações → Acessibilidade; desligado, o Nyx Car
  funciona como na 0.4.10;
- o APK do ZLINK continua sem modificação.

## 10. Organização sugerida do projeto

Separar responsabilidades de maneira simples:

- app: inicialização e configuração Android.
- ui: telas e componentes visuais.
- design: tema, cores e tipografia.
- launcher: descoberta e abertura de aplicativos.
- settings: preferências do usuário.

Evitar abstrações e camadas desnecessárias para um projeto dessa dimensão.

## 11. Documentação

Manter atualizados:

- README.md
- PROJECT_PLAN.md
- ARCHITECTURE.md
- CHANGELOG.md
- BUILD.md

Registrar decisões técnicas e limitações identificadas durante o desenvolvimento.

## 12. Forma de trabalho do Claude Code

Trabalhar em pequenas etapas.

Antes de cada fase:
1. Analisar o estado atual do projeto.
2. Apresentar o plano da etapa.
3. Explicar as decisões técnicas.
4. Identificar riscos e limitações.

Depois de cada fase:
1. Executar testes.
2. Verificar erros.
3. Revisar o código.
4. Documentar o resultado.
5. Apresentar o que foi concluído e o que falta.

Não implementar todas as funcionalidades de uma só vez.

Não inventar informações sobre o hardware.

Priorizar código legível, simples, sustentável e eficiente.

## 13. Critério de sucesso da primeira versão

A primeira versão será considerada concluída quando:

- O aplicativo estiver instalado na multimídia.
- A interface estiver adaptada à resolução 1280 × 720.
- O relógio estiver funcionando.
- Os atalhos abrirem os aplicativos.
- O ZLINK continuar funcionando normalmente.
- O launcher original continuar disponível.
- Não houver instabilidade perceptível.
- O consumo de recursos for compatível com o hardware.

**Objetivo final:** construir uma interface automotiva premium, leve, funcional e independente, que transforme a experiência de uso da central sem comprometer os recursos originais do veículo.
