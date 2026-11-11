# Nyx Car Launcher

Launcher Android próprio para a central multimídia do carro (tela de 1280 × 720, 2 GB de
RAM), com visual inspirado nas interfaces premium: fundo grafite, superfícies escuras e
destaque turquesa. É **leve** e **independente**: não altera firmware, ZLINK nem o
launcher original.

Chamava-se **Premium Car** até a versão 0.4.2. O nome mudou em 11/11/2026; o identificador
interno do app (`com.premiumcar.launcher`) continua o mesmo, para a atualização entrar por
cima ([ARCHITECTURE.md](ARCHITECTURE.md), D19).

## Situação atual: Fase 4 — funcionalidades (etapa 0.4.3 validada no carro; próximos passos em decisão)

- **Fase 1 (concluída):** o diagnóstico na central mostrou Android 8.1 real (rotulado
  como 13), 4× Cortex-A7 a 1,1 GHz, GPU Mali-450 e tela a 160 dpi com área útil de
  1280 × 655. No teste A/B, as **Views nativas** venceram o Compose com folga, e a
  interface passou a ser feita em Views.
- **Fase 2 (concluída):** o Design System e a **Home de referência** (versão `0.2.1-ref`:
  atalho "CarPlay", barra inferior só com ícones, Navegação pelo CarPlay) foram aprovados
  na central. A Home abre em ~0,6 s e usa ~10 MB.
- **Fase 3 (em andamento):** na `0.3.0`, os atalhos abrem os apps da central e a barra
  inferior troca entre Home, Música, Aplicativos, Conectividade e Configurações. Capturas
  em [`docs/design/`](docs/design/) (`home-v0.3.0.png` e `aba-*-v0.3.0.png`). A `0.3.1`
  mostrou que o player USB do fabricante só funciona com a própria tela aberta (decisão do
  próximo passo pendente). A `0.3.2` corrige os relatórios que chegavam com 0 bytes e repete
  a medição da tela de espera do ZLINK.
- **Fase 4 (em andamento):** a `0.4.0` traz o **player próprio** das músicas do pendrive
  (aba Música por pastas; o cartão da Home mostra e controla a música; ao ligar, a última
  música volta pausada e só toca com um toque) e permite escolher o Nyx Car como
  **tela inicial**, com o launcher original a um toque em Configurações. Capturas em
  `docs/design/*-v0.4.0.png`. **Validada no carro em 10/11/2026.** A `0.4.1` permite
  **trocar de aba deslizando o dedo**. O player original deixou de abrir sozinho ao
  conectar o pendrive (o usuário desligou "Reprodução automática" nas Configurações do
  Veículo), e a `0.4.2` remove o atalho dele e acaba com o "pulo" da Home ao voltar do
  CarPlay. **0.4.1 e 0.4.2 validadas no carro em 11/11/2026.** A `0.4.3` põe a **Play Store**
  no lugar vazio de Aplicativos e traz o novo nome, **Nyx Car** (validada em 11/11/2026). Roteiro de teste no [BUILD.md](BUILD.md), seção 6.

| Documento | Conteúdo |
|---|---|
| [PROJECT_PLAN.md](PROJECT_PLAN.md) | Plano completo e status das fases |
| [HARDWARE.md](HARDWARE.md) | O que se sabe da central: fatos, hipóteses e resultados medidos |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Decisões técnicas, dependências e riscos |
| [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) | Cores, tipografia, medidas, componentes e regras visuais |
| [BUILD.md](BUILD.md) | Ambiente, compilação, testes, instalação via pendrive, desinstalação e recuperação |
| [CHANGELOG.md](CHANGELOG.md) | Histórico de versões |

## Comandos rápidos

```bash
./gradlew assembleRelease     # APK em app/build/outputs/apk/release/
./gradlew testDebugUnitTest   # testes + capturas de tela em app/build/outputs/roborazzi/
```

## Restrições do projeto

Sem root, sem desbloqueio de bootloader e sem alterações de firmware, partições ou apps
originais. Única exceção, autorizada pelo usuário: desativar à mão o player original
(reversível; ver PROJECT_PLAN.md, seção 9). O ZLINK nunca é modificado, só aberto por
atalho. Toda operação na central é explicada e autorizada antes.
