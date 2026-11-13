# Design System — Nyx Car

Identidade visual própria: fundo grafite, superfícies carvão e cinza-azulado, destaque
turquesa. Inspirada na sobriedade da MBUX e na organização em cartões do iDrive, sem
reproduzir logotipos ou elementos das montadoras.

Implementação: **Views nativas** (decisão D2), com recursos XML em `app/src/main/res/`.

| Referência visual | Arquivo |
|---|---|
| Home de referência (1280 × 655 dp) | [docs/design/home-referencia-v0.2.0.png](docs/design/home-referencia-v0.2.0.png) |
| Catálogo completo de componentes | [docs/design/catalogo-v0.2.0.png](docs/design/catalogo-v0.2.0.png) |

## 1. Base: a tela da central

- 1280 × 720 px a **160 dpi**, ou seja, **1 dp = 1 px** e 1 sp = 1 px.
- **Área útil: 1280 × 655 dp.** A barra do fabricante ocupa 65 px no topo.
- Teto de ~26 quadros/s e GPU Mali-450 (ver [HARDWARE.md](HARDWARE.md)).

## 2. Regras

1. **Nada tocável menor que 64 × 64 dp.** Garantido por teste automatizado.
2. **Nenhum texto menor que 16 sp.**
3. **Sem sombras, desfoques, transparências grandes ou gradientes.** Superfícies planas
   e bordas de 1 dp.
4. **Sem animações contínuas.** O retorno ao toque é uma troca instantânea de cor
   (estado "pressionado"), sem ondulação nem elevação. Única transição: o **deslize entre
   abas** (0.4.1), em que a aba acompanha o dedo e a nova entra em 160 ms ao soltar, com
   transparência só durante o gesto.
5. **O destaque turquesa é para o que é ativo ou importante:** destino selecionado,
   ação principal, ícones de atalho e status ligado. Não usar como decoração.
6. Ícones são **brancos no arquivo**; a cor vem de quem usa (`tint`).

## 3. Cores (`values/colors.xml`)

Aprovadas na calibração feita na central em 09/11/2026. `design/PcPalette.kt` espelha
os valores para o código, e um teste garante que os dois continuam iguais.

| Token | Cor | Uso |
|---|---|---|
| `pc_background` | `#0E1116` | Fundo das telas |
| `pc_surface` | `#161B21` | Cartões, barra inferior |
| `pc_surface_high` | `#1E252D` | Atalhos, botões secundários, item selecionado |
| `pc_surface_highest` | `#27303A` | Estado pressionado, capa de álbum |
| `pc_outline` | `#2F3943` | Bordas e divisórias (1 dp) |
| `pc_text_primary` | `#E8ECEF` | Texto principal |
| `pc_text_secondary` | `#8D9AA6` | Texto de apoio, ícones inativos |
| `pc_text_disabled` | `#5A6672` | Desativado |
| `pc_accent` | `#2CC8D8` | Destaque |
| `pc_accent_dim` | `#1A7F8A` | Destaque pressionado |
| `pc_accent_container` | `#1A3E46` | Fundo de ícone em destaque (opaco, sem transparência) |
| `pc_on_accent` | `#00262B` | Texto e ícone sobre o destaque |
| `pc_warning` / `pc_error` / `pc_success` | `#E3B341` / `#E5534B` / `#3FB950` | Avisos |

## 4. Tipografia (`values/styles.xml`)

Fonte do sistema (Roboto), aprovada na calibração.

| Estilo | Tamanho | Peso | Uso |
|---|---|---|---|
| `PcText.Clock` | 112 sp | leve | Relógio da Home |
| `PcText.Headline` | 28 sp | normal | Título de tela |
| `PcText.Title` | 24 sp | médio | Título de cartão, informação principal |
| `PcText.BodyLarge` | 22 sp | normal | Leitura principal (ex.: data) |
| `PcText.Body` | 20 sp | normal, secundário | Texto de apoio (ex.: artista) |
| `PcText.Label` | 18 sp | médio | Botões, atalhos, navegação |
| `PcText.Caption` | 16 sp | normal, secundário | Legendas (o menor permitido) |

## 5. Medidas (`values/dimens.xml`)

| Grupo | Valores |
|---|---|
| Espaçamento (grade de 8) | `xs` 4 · `s` 8 · `m` 16 · `l` 24 · `xl` 32 · `xxl` 48 dp |
| Estrutura | margem da tela 24 · entre colunas 16 · interno dos cartões 24 · barra inferior 80 dp |
| Cantos | `s` 8 · `m` 12 (botões) · `l` 16 (cartões e atalhos) dp |
| Toque | mínimo 64 dp |
| Ícones | 24 · 32 · 56 dp |
| Capa | 128 dp (cartão da Home) · 160 dp (aba Música) |

## 6. Componentes

| Componente | Onde | Estados |
|---|---|---|
| Botão principal | estilo `PcButton.Primary` | normal, pressionado, desativado |
| Botão secundário | estilo `PcButton` | normal, pressionado, desativado |
| Botão redondo (mídia) | estilos `PcRoundButton` e `PcRoundButton.Primary` (64, 80 e 96 dp) | normal, pressionado, desativado |
| Atalho | `ui/components/ShortcutTile` (`app:tileIcon`, `app:tileLabel`, `caption`) | normal, pressionado, selecionado (borda turquesa), desativado, app ausente (desativado + legenda "Não encontrado"), app desativado nas Configurações (desativado + legenda "Desativado", 0.4.1) |
| Cartão | `pc_bg_card` e `pc_bg_card_clickable` | normal, pressionado |
| Cartão informativo | `layout/view_info_card` (título + linhas) | estático |
| Barra inferior | `ui/components/BottomNavBar` + `ui/Destination` | só ícones; selecionado (ícone turquesa sobre pílula `pc_accent_container`, 88 × 48 dp), pressionado |
| Atalho compacto (0.4.0) | `layout/view_source_button` (ícone 24 dp + rótulo lado a lado, 64 dp de altura) | normal, pressionado; some quando o app está ausente ou desativado (0.4.1) |
| Seletor de segmentos (0.4.4, "Favoritos \| Todos") | `layout/screen_apps` + `pc_bg_segmented` (trilho) e `pc_bg_segment` (opção de 128 × 64 dp; a selecionada ganha a pílula `pc_accent_container` e o texto turquesa, como a barra inferior) | normal, pressionado, selecionado |
| Botão de ícone (0.4.4, Editar) | `ImageButton` 64 × 64 dp com `pc_bg_button` e ícone de 24 dp em turquesa; o nome vai em `contentDescription` | normal, pressionado |
| Barra de rolagem (0.4.4, "Todos") | `pc_scrollbar_thumb` (cinza `pc_text_secondary`) sobre `pc_scrollbar_track` (`pc_surface_high`), 6 dp de largura no meio da margem direita de 24 dp; sempre visível quando há o que rolar | — |
| Atalho em edição (0.4.4) | `ShortcutTile` selecionado (borda turquesa de `pc_bg_tile`) com a legenda "Trocar" ou "Favorito" | normal, pressionado |
| Contêiner das abas (0.4.1) | `ui/components/SwipeTabsLayout` (`screen_container`) | parado; arrastando (a aba segue o dedo a 35%, até 120 dp, com até 30% de transparência; 10% nas pontas); entrando (96 dp → 0 e transparência 40% → 0 em 160 ms) |
| Linha de lista (0.4.0) | `layout/view_browser_item` + `pc_bg_list_item` (64 dp) | normal, pressionada; música atual em turquesa com ícone de alto-falante |
| Barra de progresso (0.4.0) | `pc_progress_bar` (trilho `pc_outline`, preenchimento turquesa, 4 dp, sem animação) | — |

**Ícones** (`drawable/ic_*.xml`, 24 × 24, traço de 2 dp com pontas arredondadas, ou
preenchidos para os controles de mídia), 39 no total:
- navegação e status: home, music, apps, connectivity, settings, bluetooth, wifi,
  navigation, carplay, chevron_right, chevron_left (0.4.0), restore (0.4.0, "Launcher
  original");
- mídia: play, pause, skip_previous, skip_next;
- apps e telas (Fase 3): usb, radio, video, movie, map, camera, car, gauge, equalizer,
  steering, folder, gear, volume, display, clock, diagnostics, store (0.4.3, Play Store);
- lista "Todos" (0.4.4): browser (Chrome, globo), search (Google, lupa), book (Instruções),
  image (Scan de imagens), app_generic (apps sem ícone próprio), edit (botão Editar).

São desenhos próprios, sem marcas de terceiros (o atalho do YouTube usa o ícone genérico
`video`). O ícone `carplay` (anel com o
símbolo de reproduzir) segue o estilo dos demais; o logotipo oficial da Apple não é usado,
por escolha do usuário, para manter o padrão visual. Pelo mesmo motivo, a Play Store usa o
ícone `store` (sacola com o símbolo de reproduzir), não o logotipo do Google.

**Deslize entre abas** (pedido do usuário, 10/11/2026): arrastar o dedo para a esquerda
abre a próxima aba da barra; para a direita, a anterior; nas pontas, nada troca. Para não
trocar num solavanco, vale só o arrasto claramente horizontal e de pelo menos 154 px (12% da
largura), ou um gesto rápido de pelo menos 48 dp. Rolagens verticais e toques não mudam.

**Barra inferior sem rótulos** (decisão do usuário, 09/11/2026): os ícones bastam e o
visual fica mais limpo. Como a seleção não conta mais com o texto, ela é marcada por área
(a pílula), além da cor. O nome de cada destino continua em `contentDescription`, para
leitores de tela e para os avisos.

## 7. Layout

### Home

Três colunas iguais (≈ 400 dp) sobre a barra inferior de 80 dp:

```
┌──────────────────────┬──────────────────────┬──────────────────────┐
│ 15:48                │ ▲ Navegação          │ ♪ Música             │
│ domingo, 9 de nov.   │                      │                      │
│ ◠ Wi-Fi  (ᛒ Bluet.)  │      ( ▲ )           │ [capa] Música atual  │
│                      │  Waze pelo CarPlay   │        Artista       │
│ [Bluetooth] [CarPlay]│                      │                      │
│ [Apps]   [Config.]   │ [ Abrir navegação ]  │   ⏮   ( ▶ )   ⏭      │
├──────────────────────┴──────────────────────┴──────────────────────┤
│   (⌂)        ♪           ▦            ((•))           ☰            │
└────────────────────────────────────────────────────────────────────┘
```

O indicador de Bluetooth só aparece quando o Android informa "ligado" (ver
`system/BluetoothStatus`). O cartão de música (0.4.0) mostra a música do player próprio,
o artista e a capa (quando o arquivo tem); sem música escolhida, "Músicas do pendrive".

### Abas

Título (`PcText.Headline`) e um corpo em que linhas de atalhos e cartões dividem a altura
por peso, sem rolagem (`ui/screens/ScreenBuilder`). A aba Música tem layout próprio
(`layout/screen_music`), com a única lista rolável do launcher:

| Aba | Conteúdo |
|---|---|
| Música (0.4.0) | metade esquerda: pastas e músicas do pendrive (lista com rolagem rápida) · metade direita: capa 160 dp, título, artista, "pasta · 2 de 3", progresso, ⏮ ▶ ⏭ · linha de atalhos compactos: BT Música e Rádio (o player USB do fabricante saiu na 0.4.2) |
| Aplicativos | botão **Editar** (só o ícone de lápis) e seletor **Favoritos \| Todos** ao lado do título (0.4.4). **Favoritos:** grade 3 × 4 com os 12 favoritos (padrão: Rádio, Vídeo, Bluetooth, CarPlay · Maps, YouTube, Câmera, Informações do carro · Equalizador, Comandos de volante, Arquivos, Play Store). **Todos:** os apps da central em ordem alfabética, ícones de traço, nos mesmos atalhos dos Favoritos (4 colunas, 3 linhas por tela) com barra de rolagem; 20 apps = 5 linhas. **Editar favoritos:** os 12 com borda turquesa e a legenda "Trocar", botões Restaurar padrão e Concluir (primário); tocar num lugar abre "Trocar “X” por…" (a lista de Todos com a legenda "Favorito" e o lugar tocado com borda) e Cancelar |
| Tela de espera do CarPlay (0.4.7) | janela sobreposta ao ZLINK, tela cheia `pc_background`: círculo `pc_accent_container` de 112 dp com o ícone `carplay` turquesa (56 dp), título "Conectando ao CarPlay" (Headline), situação em Body ("iPhone conectado pelo cabo" / "iPhone conectando sem fio" / "Preparando a tela do CarPlay…"), "Toque para voltar ao Nyx Car · toque e segure para ver o ZLINK" (Caption) no rodapé (0.4.10; era "Toque para fechar"); na desconexão, "CarPlay desconectado — Voltando para o Nyx Car…" (por 2 s na 0.4.9); se a conexão cai antes do CarPlay aparecer (0.4.9), "Conexão interrompida — Aguardando o iPhone voltar…", com "Toque para voltar ao Nyx Car"; ao tocar em CarPlay sem iPhone (0.4.8), "Aguardando o iPhone — Conecte o cabo ou ligue o CarPlay sem fio no iPhone", com "Toque para voltar · toque e segure para abrir o ZLINK". Sem animação |
| Conectividade | Bluetooth, Wi-Fi, CarPlay · cartão "Estado" (2/3 da altura) |
| Configurações | grade 4 × 2 (Android, Som, Tela, Data e hora, Veículo, Tela inicial, Launcher original, Nyx Diagnóstico) · cartão com a versão e o caminho de volta ao launcher original |

Capturas: `docs/design/home-v0.4.0.png`, `home-tocando-v0.4.0.png`,
`aba-music-tocando-v0.4.0.png`, `aba-music-permissao-v0.4.0.png`,
`aba-settings-v0.4.3.png` (novo nome), `aba-music-tocando-v0.4.2.png` (sem o atalho Música USB),
`aba-apps-v0.4.4.png`, `aba-apps-todos-v0.4.4.png`, `aba-apps-todos-fim-v0.4.4.png` (rolada até o fim), `aba-apps-editar-v0.4.4.png` e `aba-apps-trocar-v0.4.4.png` (Favoritos | Todos e edição), `carplay-espera-cabo-v0.4.7.png`, `carplay-espera-sem-fio-v0.4.7.png`, `carplay-espera-preparando-v0.4.7.png` `carplay-desconectado-v0.4.7.png`, `carplay-aguardando-iphone-v0.4.8.png`, `carplay-interrompida-v0.4.9.png` e `carplay-espera-cabo-v0.4.10.png` (rodapé novo) (tela de espera do CarPlay) e, das abas sem mudança, `aba-*-v0.3.0.png`. O deslize não aparece em capturas:
é verificado pelos testes de toque (BUILD.md, seção 3).

## 8. Como verificar

- `./gradlew testDebugUnitTest` gera as capturas em `app/build/outputs/roborazzi/`
  (`home_reference.png`, `tab_*.png` e `design_catalog.png`) e valida o alvo mínimo de
  toque na Home e em cada aba, além da paleta.
- Na central: **Nyx Diagnóstico → Calibração → Abrir catálogo de componentes**.
- Limitação conhecida: nas capturas da JVM (Robolectric, API 27), dias e meses saem em
  inglês e o relógio mostra um horário simulado. Na central, a data sai em português.
