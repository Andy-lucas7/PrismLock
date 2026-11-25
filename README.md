# PrismLock

**Transforme sua Tela de Bloqueio e Tela Inicial no Android em uma experiência visual óptica em 120Hz.**

O **PrismLock** é um estúdio completo de **Live Wallpaper (KLWP)** e **Widgets Ópticos (KWGT)** para Android 13+, combinando refração de vidro líquido (*Optical Crystal / AGSL*), papéis de parede em vídeo com aceleração por hardware, motor de fórmulas com botões calculadora `ƒx`, biblioteca visual de ativos (58 ícones vetoriais, SVGs e PNGs), ações de toque por camada, tipografia elástica e pós-processamento 3D offline em processo isolado `:wallpaper`.

---

## Principais Recursos

- **Cristal Óptico Real (AGSL GPU Shaders) & Imagens Refratadas**  
  Simulação física de refração, dispersão cromática RGB, brilho especular de Fresnel e vidro jateado (*frost*) em textos, formas, ícones, SVGs e camadas de imagem (`LayerType.IMAGE` com canal alpha usado como mapa de lente 3D).

- **Estúdio de Widgets Ópticos (KWGT) & Live Wallpaper (KLWP)**  
  Crie e fixe widgets em múltiplas proporções (`2×2`, `4×2`, `4×1.8`, `4×1`) com uma imagem de referência própria do widget para refração realista (sem imagem, o vidro refrata um fundo neutro grafite — nunca a foto da tela de bloqueio, que não é o que fica atrás do widget) e consumo zero em repouso.

- **Vídeos em Live Wallpaper com Refração ao Vivo**  
  Reproduza `.mp4`, `.webm`, `.mkv` e `.mov` diretamente via aceleração por hardware (`ImageReader` zero-copy) com pausa automática quando fora de exibição.

- **Profundidade 3D Seletiva (ML Kit + Bokeh & Color Pop)**  
  Ative o recorte 3D diretamente nas miniaturas de cada papel de parede e aplique pós-processamento fotográfico offline:
  - **Bokeh de Fundo (Modo Retrato)** (`0%–100%`)
  - **Color Pop P&B** (fundo monocromático ou invertido)
  - **Destaque do Sujeito** e **Vinheta & Halo 3D**

- **Motor de Fórmulas, Botões Calculadora `ƒx`, Globais & Ativos Dinâmicos**  
  Sintaxe completa `$...$`, variáveis globais `$gv(...)$` e tags `{HH}:{mm}`:
  - **Botão Calculadora `ƒx` Direto nos Controles**: alterne Cor, Tamanho/Largura, Opacidade, Posição X/Y, Rotação, Escala, Visibilidade e Ativo/Ícone para expressões com preview ao vivo e atalhos (`gv()`, `wi()`, `ai()`, `df()`, `bi()`, `if()`, `ce()`).
  - **Globais do Projeto (`$gv`)**: crie variáveis de **Cor**, **Interruptor (0/1)**, **Número**, **Texto** e **Lista**, vinculando-as diretamente a qualquer camada ou pilha.
  - **Biblioteca Visual de 58 Ícones + Ativos Nomeados (`SVG` / `PNG` / `WebP`)**: alterne automaticamente vetores ou fotos personalizadas via `$if(...)$` em `fxAsset`.
  - **Funções Integradas**: Data/Hora (`df`), contagem regressiva (`tf`), Bateria (`bi`), Sistema e Áudio (`si`), Clima Open-Meteo (`wi`), Astronomia (`ai`), Mídia (`mi`), manipulação de cores (`ce`), repetições (`fl`), condicionais (`if`), texto (`tc`) e matemática (`mu`).

- **Ações de Toque por Item**  
  Configure cliques individuais por camada ou pilha na aba **Posição & Toque** para abrir aplicativos instalados, links e deep links, alternar ou ciclar variáveis globais, avançar o papel de parede do carrossel, controlar mídia ou abrir atalhos do sistema na Tela Inicial.

- **Tipografia Elástica & Fontes Personalizadas**  
  Alongamento vertical contínuo de fontes com controle de compensação de traço, detecção automática de fontes do sistema, **8 fontes OFL** embarcadas (`Outfit`, `Inter`, `Space Grotesk`, `Bebas Neue`, `Playfair Display`, `JetBrains Mono`, `Syne`, `Plus Jakarta Sans`), importação avulsa e sincronização automática com pasta de fontes (`.ttf` / `.otf`) com preview visual.

- **Hero Page, Hub Spotlight + Pacotes Portáteis `.prism` / `.widget.prism`**  
  - **Abertura Holográfica & Processo Isolado `:wallpaper`**: tela de boas-vindas AGSL com transição radial e serviço de papel de parede/widget isolado para liberação imediata de memória nativa e despertar instantâneo (`RGB_565`).
  - **Carrossel Spotlight**: alternância suave *fade + blur* entre **Telas (Lock)** e **Widgets (KWGT)** com gestos verticais (`↑ Editar · ↓ Aplicar`).
  - **Estúdio em 5 Abas + 3 Pílulas de Contexto**: pílulas globais no topo (`Fundo & 3D` / `Formato & Fundo`, `Globais ($gv)`, `Clima & Automação`) e **5 abas** dedicadas ao item selecionado (*Camadas*, *Conteúdo*, *Forma & Fonte*, *Vidro & Cor*, *Posição & Toque*).
  - **Exportação & Importação Diferenciada**: empacote temas (`.prism`) e widgets (`.widget.prism`) com `theme.json`/`widget.json`, `bundle.json`, globais, ações de toque, ativos nomeados, mídias e fontes (compatível também com `.zip` e pastas SAF).

- **Modelos Prontos por Estilo (sem marcas)**  
  Cada modelo tem um foco e um público, usando as 8 fontes embarcadas:
  - **Telas:** `Moderno · Profundidade` (Bebas Neue gigante atrás do sujeito 3D), `Clean · Névoa` (Inter fina, muito respiro), `Fofo · Pêssego` (pastel arredondado com chips), `Vidro · Aurora` (cristal óptico elástico + 3D), `Elegante · Editorial` (Playfair em creme), `Avançado · Terminal` (painel mono com barras de bateria/dia), `Simples · Canto` (relógio discreto no canto) e `Ousado · Ácido` (Syne em degradê lima↔violeta).
  - **Widgets:** `Clean · Relógio`, `Moderno · Cristal Elástico`, `Fofo · Energia`, `Avançado · Painel`, `Música · Cápsula` e `Elegante · Calendário`.
  - Cópias salvas de modelos antigos que mantêm o título original recebem o novo visual automaticamente (id, fotos e ajustes preservados); temas criados ou renomeados pelo usuário nunca são alterados.

---

## Desempenho e Consumo

| Cenário (Galaxy, QHD+) | Antes | Agora |
| --- | --- | --- |
| Processo `:wallpaper`, tela apagada / desbloqueado em outro app | 373 MB PSS | ~56 MB PSS (≈20 MB sem cena carregada) |
| App aberto no Hub (memória) | 353 MB PSS | 97 MB PSS |
| CPU do app no Hub / em segundo plano | ~185% contínuo | 0% em repouso (0 quadros desenhados parado) |

- **Apresentação própria do wallpaper:** `PrismWallpaperService` desenha com um `HardwareRenderer` próprio (em vez de `lockHardwareCanvas`), parado 1,5 s após o último quadro; o HWUI pode então descartar o contexto de GPU, o cache Skia e os render targets.
- **Hibernação:** 3 s depois de ficar invisível ou de apagar a tela, o processo libera fotos, máscara 3D, fundo fosco, caches e renderizadores. Com o aparelho bloqueado mantém **apenas o quadro final pronto** (`RGB_565`, ~5 MB) para acender sem atraso; desbloqueado em outro app não mantém nada. Qualquer remontagem recarrega a foto do bake em disco automaticamente.
- **Zero trabalho com tela apagada:** ticks, sensores, vídeo e animações só rodam com o wallpaper visível.
- **Hub sem loop de recomposição:** os `Flow` do DataStore são criados uma vez (`remember`) e coletados com `collectAsStateWithLifecycle`; os modelos padrão são memoizados. Prévias dos cards ficam em cache em disco (**WebP**), com chave que inclui o projeto, a imagem exibida (uri + data/tamanho) e o dia.
- **Release com R8** (`optimization { enable = true }` + `proguard-rules.pro` com keep rules do ML Kit) — APK release ~6 MB.
- **Sem telemetria em segundo plano:** o `TransportBackendDiscovery` do ML Kit (datatransport) é removido no manifest; os eventos são descartados no próprio processo e o app nunca é acordado para enviar logs.
- **Widgets leves:** cada atualização carrega a imagem de referência sem cache, recicla após o render e libera o renderizador.

---

## Arquitetura

```text
app/src/main/java/com/example/prismlock/
├── MainActivity.kt                  # Orquestração Hero/Hub/Studio, pickers SAF e bake de recursos
├── data/
│   ├── Data.kt                      # Persistência DataStore (ProjectStore, fontes, EnvProvider)
│   └── PrismPackageManager.kt       # Exportação/importação .prism e .widget.prism (ZIP e Pasta SAF)
├── model/
│   └── Model.kt                     # LayerNode, TouchAction, GlobalVar, Glass, Project e Presets
├── engine/
│   ├── Dynamic.kt                   # Tags {HH} e bridge numérica
│   ├── KustomFormulaEngine.kt       # Motor unificado de fórmulas, ai(), globais e FX
│   ├── TouchActionExecutor.kt       # Hit-test e execução das ações de toque
│   ├── Icons.kt                     # 58 ícones vetoriais e codificador/importador SVG
│   ├── Type.kt                      # FontManager, StemStretch e GlyphAtlasBaker
│   ├── LiquidGlassRenderer.kt       # Shaders AGSL, cristal óptico em formas/textos/imagens e composição
│   └── WallpaperBaker.kt            # Pré-renderização de imagens, máscaras 3D e frames RGB_565
├── ui/
│   ├── PrismTheme.kt                # Paleta monocromática OLED e animações
│   ├── PrismHeroScreen.kt           # Abertura cinematográfica AGSL com revelação radial
│   ├── Components.kt                # FocusedInputDialog, FxCalculatorButton, FxExpressionDialog e controles
│   ├── ThemeHubScreen.kt            # Carrossel inicial com alternância Telas / Widgets
│   ├── StudioScreen.kt              # Estúdio em 5 abas + 3 pílulas de contexto + seletor visual de ativos
│   └── InteractiveLockPreview.kt    # Preview 1:1 interativo com gestos e vídeo ao vivo
├── wallpaper/
│   ├── DepthAndCarousel.kt          # Segmentação 3D (ML Kit) e carrossel de mídias
│   ├── VideoWallpaperRenderer.kt    # Renderização de vídeo por hardware
│   └── PrismWallpaperService.kt     # WallpaperService isolado em :wallpaper com despertar em 0ms
└── widget/
    └── PrismWidgetProvider.kt       # AppWidgetProvider óptico (KWGT) isolado em :wallpaper

app/src/test/java/com/example/prismlock/
└── KustomFormulaTest.kt             # Testes unitários do motor de fórmulas e globais
```

---

## Documentação Completa

Para um tutorial detalhado tela por tela, guia das **3 Pílulas de Contexto**, das **5 Abas do Estúdio**, botões calculadora **`ƒx`**, referência completa da **linguagem de fórmulas (`$df$`, `$tf$`, `$bi$`, `$si$`, `$wi$`, `$ai$`, `$mi$`, `$gv$`, `$if$`, `$ce$`, `$fl$`, `$tc$`, `$mu$`)**, lista dos **58 ícones vetoriais** e receitas passo a passo, consulte o manual em [DOCUMENTATION.md](DOCUMENTATION.md).

---

## Como Compilar e Testar

**Requisitos:** Android Studio / JDK 17+, Android SDK (`compileSdk = 36`, `minSdk = 33`, `targetSdk = 35`).

```bash
# Compilar APK Debug e executar testes unitários
./gradlew assembleDebug testDebugUnitTest

# Compilar APK Release
./gradlew assembleRelease

# Instalar Release diretamente no aparelho via ADB
./gradlew installRelease
```

No Windows (PowerShell):

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
```
