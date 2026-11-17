# PrismLock

**Transforme sua Tela de Bloqueio no Android em uma obra de arte óptica em 120Hz.**

O **PrismLock** é um estúdio completo de **Live Wallpaper** e **Tela de Bloqueio interativa** para Android 13+, combinando refração de vidro líquido (*Optical Crystal / AGSL*), papéis de parede em vídeo com aceleração por hardware zero-copy, motor de fórmulas estilo KWGT/KLWP, tipografia elástica anatômica e pós-processamento 3D offline.

---

## Principais Recursos

- **Cristal Óptico Real (AGSL GPU Shaders)**  
  Calcula normais 3D em espaço base proporcional (máx. `1080px` de largura) para simular refração física, dispersão cromática RGB, brilho especular de Fresnel e jateamento fosco (*frost*) — funcionando mesmo com preenchimento 100% transparente.

- **Vídeos em Live Wallpaper com Refração ao Vivo e Trava Térmica**  
  Reproduza `.mp4`, `.webm`, `.mkv` e `.mov` diretamente via `MediaPlayer` + `SurfaceTexture` + `EGL14`/`GLES20` (`HardwareBuffer` GPU-to-GPU, sem cópia de pixels na CPU) com refração óptica em tempo real e pausa síncrona automática ao apagar a tela, abrir outros apps ou desbloquear.

- **Profundidade 3D Seletiva (ML Kit + Bokeh & Color Pop)**  
  Ativação direta por foto via selo **`3D ON / 3D OFF`** sobre as miniaturas do carrossel SAF (com criação automática da camada `Sujeito 3D` quando necessário e exibição contextual dos controles avançados) e pós-processamento fotográfico pré-renderizado no `WallpaperBaker` (**0% de custo em tempo real**):
  - **Bokeh de Fundo (Modo Retrato)** (`0%–100%`)
  - **Color Pop P&B** (fundo monocromático ou invertido)
  - **Destaque do Sujeito** (contraste/brilho/saturação) e **Vinheta & Halo 3D**

- **Motor Único Estilo KWGT / KLWP (`KustomFormulaEngine` + `Dynamic` + FX)**  
  Sintaxe completa `$...$`, globais `$gv(...)$` e retrocompatibilidade `{HH}:{mm}` avaliadas de forma unificada:
  - **Globais do Projeto (`Globais`)**: variáveis de **Cor**, **Interruptor (0/1)**, **Número**, **Texto** e **Lista**, com vínculo direto em qualquer camada (`Aplicar em:` / `Todas`) e herança em pilhas `STACK`.
  - **Propriedades Dinâmicas FX por Camada**: `visibilityExpr`, `fxColor` (compatível com Sólido, Gradiente, Vazado, Adaptativo, Blur e Cristal Óptico), `fxOpacity`, `fxRotation`, `fxScale`, `fxOffsetX`, `fxOffsetY` e ícone vetorial por fórmula.
  - **Funções**: Data/Hora (`df`), contagem regressiva (`tf`), Bateria (`bi`), Sistema & Áudio sem permissões extras (`si(alarm|model|volm|ringer|mplaying|locked|steps)`), Clima Open-Meteo (`wi`), Mídia (`mi`), manipulação de cores (`ce`), loop (`fl`), condicionais (`if`), texto (`tc`) e matemática (`mu`).

- **Ações de Toque por Item (`Toque` + `TouchActionExecutor`)**  
  Configuração individual por camada (e suporte a `STACK` pai) para **Abrir App** (seletor via `<queries>` compatível com a Play Store, sem `QUERY_ALL_PACKAGES`), **Abrir Link/Deep Link**, **Alternar/Definir/Ciclar Global**, **Próximo Wallpaper**, **Controles de Mídia**, **Alarme/Câmera/Configurações** e **Modo Teste no Editor**. Execução funcional na **Tela Inicial (Launcher)** respeitando o limite nativo do Android/Keyguard na tela de bloqueio.

- **Tipografia Elástica (`StemStretch`) + Fontes Nativas & SAF**  
  Alongamento vertical ponderado ($C^\infty$) que prioriza hastes verticais, classificação uniforme por lóbulo horizontal com proteção de pontuação (`:`/`.`) e deslocamento vertical puro (`xFinal = x`, trava anti-estrangulamento `0.38f * baseStrokeWidth`), scanner nativo `/system/fonts/` para Samsung Sharp Sans / One UI Sans, **8 fontes OFL** embarcadas (`Outfit`, `Inter`, `Space Grotesk`, `Bebas Neue`, `Playfair Display`, `JetBrains Mono`, `Syne`, `Plus Jakarta Sans`), importação avulsa e **Pasta Automática de Fontes (SAF)** com leitura da tabela OpenType `name` e preview ao vivo no menu.

- **Fluidez 120fps, Trava Térmica e Pacotes Portáteis `.prism`**  
  - **Carrossel Spotlight a 120Hz**: cards com dados estáticos de prévia (`18:45` + telemetria mock), cache persistente `HubPreviewCache`, brilho ambiente pré-desfocado em baixa resolução (`72×156`) e animações isoladas em `graphicsLayer` na taxa nativa de Hz da tela.
  - **Estúdio Interativo 1:1**: cache de cena via `RenderNode`, pausa automática do recálculo pesado de lente durante gestos (usando delta de `Matrix` em tempo real), arraste com *snap* magnético, pinça para escala/rotação, `FocusedInputDialog` flutuante acima do teclado e **7 abas** (*Fundo & 3D*, *Camadas*, *Conteúdo*, *Tipografia*, *Vidro / Cor*, *Toque*, *Globais*).
  - **Exportação & Importação `.prism`**: empacota configuração JSON, globais, ações de toque, mídias externas e fontes `.ttf`/`.otf` usadas em `.prism` (ZIP), importável via `.prism`, `.zip` ou pasta descompactada.

---

## Arquitetura

```text
app/src/main/java/com/example/prismlock/
├── MainActivity.kt                  # Orquestração 2 telas (Hub/Studio), pickers SAF e bake modal
├── data/
│   ├── Data.kt                      # Persistência DataStore (ProjectStore, fontes, EnvProvider/áudio)
│   └── PrismPackageManager.kt       # Empacotamento/descompactação .prism (ZIP e Pasta SAF)
├── model/
│   └── Model.kt                     # LayerNode (FX + TouchAction), GlobalVar, Glass, Project e Presets
├── engine/
│   ├── Dynamic.kt                   # Tags legadas {HH}, globais numéricas/texto e bridge
│   ├── KustomFormulaEngine.kt       # Motor unificado ($df$, $tf$, $bi$, $si$, $wi$, $mi$, $gv$, $ce$, $fl$, $if$, $tc$, $mu$)
│   ├── TouchActionExecutor.kt       # Hit-test preciso por matriz inversa e executor de ações de toque
│   ├── Icons.kt                     # Ícones vetoriais e importador SVG
│   ├── Type.kt                      # FontManager (OFL, Samsung, SAF), StemStretch (C∞ + lóbulos) e GlyphAtlasBaker
│   ├── LiquidGlassRenderer.kt       # Shaders AGSL, fxColor/FX, lensHeightMap e render em base FHD+
│   └── WallpaperBaker.kt            # Asset Baking Engine (sharp, frosted, depth 3D, Bokeh, atlas)
├── ui/
│   ├── PrismTheme.kt                # Paleta monocromática OLED e especificações de movimento
│   ├── Components.kt                # FocusedInputDialog, PrecisionSliderField, ColorPickerPanel, StudioSection
│   ├── ThemeHubScreen.kt            # Carrossel Spotlight 120fps em cache com brilho ambiente pré-renderizado
│   ├── StudioScreen.kt              # Estúdio em 7 abas (Fundo & 3D, Camadas, Conteúdo, Tipografia, Vidro/Cor, Toque, Globais)
│   └── InteractiveLockPreview.kt    # Preview 1:1 com vídeo ao vivo, gestos, snap e modo teste de toque
└── wallpaper/
    ├── DepthAndCarousel.kt          # SubjectDepthEngine (ML Kit + Bokeh/ColorPop) e WallpaperCarousel
    ├── VideoWallpaperRenderer.kt    # Pipeline zero-copy MediaPlayer -> SurfaceTexture -> GLES -> HardwareBuffer
    └── PrismWallpaperService.kt     # WallpaperService orientado a eventos, trava térmica e despacho de toque

app/src/test/java/com/example/prismlock/
└── KustomFormulaTest.kt             # Testes unitários do motor unificado, globais e serialização JSON
```

---

## Como Compilar e Testar

**Requisitos:** Android Studio / JDK 17+, Android SDK (`compileSdk = 36`, `minSdk = 33`, `targetSdk = 35`).

```bash
# Compilar APK Debug e executar testes unitários
./gradlew assembleDebug testDebugUnitTest

# Compilar APK Release (assinado com debug keystore para testes locais)
./gradlew assembleRelease

# Instalar Release diretamente no aparelho via ADB
./gradlew installRelease
```

No Windows (PowerShell):

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
```
