# Nyx Car Launcher

Launcher Android próprio para a central multimídia do carro (tela de 1280 × 720, 2 GB de
RAM), com visual inspirado nas interfaces premium: fundo grafite, superfícies escuras e
destaque turquesa. É **leve** e **independente**: não altera firmware, ZLINK nem o
launcher original.

## Situação: versão final 1.0.0 (15/11/2026)

O Nyx Car está pronto e em uso na central, como tela inicial. A `1.0.0` é o mesmo código da
`0.4.25`, testada no carro em 15/11/2026. O histórico das versões está no
[CHANGELOG.md](CHANGELOG.md), e o das fases, no [PROJECT_PLAN.md](PROJECT_PLAN.md).

**O que ele faz**

- **Home:** relógio, data, indicadores de Wi-Fi e Bluetooth, cartão de música e atalhos
  (CarPlay, Bluetooth, Aplicativos, Configurações). A barra inferior troca entre Home, Música,
  Aplicativos, Conectividade e Configurações, também deslizando o dedo.
- **Música:** player próprio das músicas do pendrive, por pastas, com player grande, aleatório
  (da pasta, ou da pasta com as subpastas) e a última música de volta, pausada, ao ligar o
  carro.
- **Aplicativos:** favoritos editáveis e a lista de todos os apps da central.
- **CarPlay:** a tela de espera do ZLINK fica coberta por "Conectando" na conexão e por
  "CarPlay desconectado" no fim. Quando o CarPlay termina, pelo cabo ou sem fio, o Nyx Car
  volta sozinho para a frente.
- **Nyx Diagnóstico:** relatório da central e gravação dos testes no carro, sem
  identificadores nem nomes de músicas.

**Na central** (Android 8.1 real, rotulado como 13; 4× Cortex-A7 a 1,1 GHz; GPU Mali-450): a
interface é feita em Views nativas, que venceram o Compose no teste A/B. A Home abre a frio em
~0,7 s, com ~15 MB de memória. Capturas em [`docs/design/`](docs/design/).

**Limitações conhecidas**

- Sem ADB (bloqueado pelo fabricante): a instalação e os relatórios vão pelo pendrive.
- Sem internet, a central volta à data do firmware quando parte do zero: acerte em
  Configurações → Data e hora.
- Na troca do CarPlay sem fio para o cabo, a tela passa ~12 s pela Home, com "CarPlay
  desconectado", até o iPhone conectar pelo cabo.
- A chave de assinatura fica só neste PC: um APK compilado em outro PC não atualiza o
  instalado (ARCHITECTURE.md, D11).

| Documento                            | Conteúdo                                                                           |
| ------------------------------------ | ---------------------------------------------------------------------------------- |
| [HARDWARE.md](HARDWARE.md)           | O que se sabe da central: fatos, hipóteses e resultados medidos                    |
| [ARCHITECTURE.md](ARCHITECTURE.md)   | Decisões técnicas, dependências e riscos                                           |
| [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) | Cores, tipografia, medidas, componentes e regras visuais                           |
| [BUILD.md](BUILD.md)                 | Ambiente, compilação, testes, instalação via pendrive, desinstalação e recuperação |
| [CHANGELOG.md](CHANGELOG.md)         | Histórico de versões                                                               |

## Comandos rápidos

```bash
./gradlew assembleRelease     # APK em app/build/outputs/apk/release/
./gradlew testDebugUnitTest   # testes + capturas de tela em app/build/outputs/roborazzi/
```

## Restrições do projeto

Sem root, sem desbloqueio de bootloader e sem alterações de firmware, partições ou apps
originais. Única exceção, autorizada pelo usuário: desativar à mão o player original
(reversível; ver PROJECT_PLAN.md, seção 9). O ZLINK nunca é modificado: o Nyx Car só o
abre, o traz para a frente e cobre a tela de espera dele com uma janela própria. Toda
operação na central é explicada e autorizada antes.
