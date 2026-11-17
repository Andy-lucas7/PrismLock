# PrismLock

**Transforme sua Tela de Bloqueio no Android em uma obra de arte óptica em 120Hz.**

O **PrismLock** é um estúdio completo de **Live Wallpaper** e **Tela de Bloqueio interativa** para Android 13+, combinando refração de vidro líquido (*Optical Crystal / AGSL*), papéis de parede em vídeo com aceleração por hardware zero-copy, motor de fórmulas estilo KWGT/KLWP, tipografia elástica anatômica e pós-processamento 3D offline.

---

## Principais Recursos

- **Cristal Óptico Real (AGSL GPU Shaders)**  
  Calcula normais 3D em espaço base proporcional (máx. `1080px` de largura) para simular refração física, dispersão cromática RGB, brilho especular de Fresnel e jateamento fosco (*frost*) — funcionando mesmo com preenchimento 100% transparente.

- **Vídeos em Live Wallpaper com Refração ao Vivo e Trava Térmica**  
  Reproduza `.mp4`, `.webm`, `.mkv` e `.mov` diretamente via `MediaPlayer` + `SurfaceTexture` + `EGL14`/`GLES20` (`HardwareBuffer` GPU-to-GPU, sem cópia de pixels na CPU) com refração óptica em tempo real e pausa síncrona automática ao apagar a tela, abrir outros apps ou desbloquear.

- **Profundidade 3D Avançada (ML Kit + Bokeh & Color Pop)**  
  Segmentação de sujeito offline por foto no carrossel SAF (`3D ON / 3D OFF`) e pós-processamento fotográfico pré-renderizado no `WallpaperBaker` (**0% de custo em tempo real**):
  - **Bokeh de Fundo (Modo Retrato)** (`0%–100%`)
  - **Color Pop P&B** (fundo monocromático ou invertido)
  - **Destaque do Sujeito** (contraste/brilho/saturação) e **Vinheta & Halo 3D**

- **Motor de Fórmulas KWGT / KLWP (`KustomFormulaEngine`)**  
  Sintaxe completa `$...$` com retrocompatibilidade `{HH}:{mm}`:
  - Data/Hora (`$df(HH)$`, `$df(mm)$`, `$df(ss)$`, `$df(EEEE)$`, `$df(dd/MM/yyyy)$`, `$df(MMMM)$`)
  - Bateria/Sistema (`$bi(level)$`, `$bi(status)$`, `$bi(temp)$`, `$si(alarm)$`, `$si(model)$`)
  - Clima Open-Meteo (`$wi(temp)$`, `$wi(cond)$`, `$wi(city)$`)
  - Lógica condicional e texto (`$if(bi(level) < 20, "Bateria Baixa", "Normal")$`, `$tc(up|low|cap, ...)$`, operadores `=`, `!=`, `<`, `>`, `&`, `|`)

- **Tipografia Elástica (`StemStretch`) + Fontes Nativas & SAF**  
  Preservação anatômica com trava anti-estrangulamento (`0.38f * baseStrokeWidth`), scanner nativo `/system/fonts/` para Samsung Sharp Sans / One UI Sans (sem empacotar arquivos proprietários), **8 fontes OFL** embarcadas (`Outfit`, `Inter`, `Space Grotesk`, `Bebas Neue`, `Playfair Display`, `JetBrains Mono`, `Syne`, `Plus Jakarta Sans`), importação avulsa e **Pasta Automática de Fontes (SAF)** com leitura da tabela OpenType `name` e preview ao vivo no menu.

- **Arquitetura em 2 Telas + Pacotes Portáteis `.prism`**  
  - **Carrossel Spotlight**: gestão visual de múltiplas telas com arraste vertical (`↑ Editar · ↓ Aplicar`), criação nomeada e dock em vidro fosco.
  - **Estúdio Interativo 1:1**: arraste com *snap* magnético, pinça para escala/rotação contínua, assistente de fórmulas, seções expansíveis com presets de 1 toque e `FocusedInputDialog` flutuante acima do teclado em todos os campos.
  - **Exportação & Importação `.prism`**: empacota configuração JSON, imagens/vídeos externos e fontes `.ttf`/`.otf` usadas em um arquivo compactado `.prism` (ZIP), importável via arquivo `.prism`, `.zip` ou pasta descompactada.

---

## Arquitetura

```text
app/src/main/java/com/example/prismlock/
├── MainActivity.kt                  # Orquestração 2 telas (Hub/Studio), pickers SAF e bake modal
├── data/
│   ├── Data.kt                      # Persistência DataStore (ProjectStore, fontes, EnvProvider)
│   └── PrismPackageManager.kt       # Empacotamento/descompactação .prism (ZIP e Pasta SAF)
├── model/
│   └── Model.kt                     # LayerNode, Glass, Project (Bokeh/ColorPop) e Presets
├── engine/
│   ├── Dynamic.kt                   # Tags legadas {HH} e bridge numérica
│   ├── KustomFormulaEngine.kt       # Parser completo KWGT/KLWP ($df$, $bi$, $si$, $wi$, $if$, $tc$)
│   ├── Icons.kt                     # Ícones vetoriais e importador SVG
│   ├── Type.kt                      # FontManager (OFL, Samsung, SAF), StemStretch e GlyphAtlasBaker
│   ├── LiquidGlassRenderer.kt       # Shaders AGSL, lensHeightMap e render em base FHD+
│   └── WallpaperBaker.kt            # Asset Baking Engine (sharp, frosted, depth 3D, Bokeh, atlas)
├── ui/
│   ├── PrismTheme.kt                # Paleta monocromática OLED e especificações de movimento
│   ├── Components.kt                # FocusedInputDialog, PrecisionSliderField, ColorPicker, StudioSection
│   ├── ThemeHubScreen.kt            # Carrossel Spotlight inicial em vidro fosco e dock circular
│   ├── StudioScreen.kt              # Estúdio em abas (Fundo & 3D, Camadas, Conteúdo, Tipografia, Vidro/Cor)
│   └── InteractiveLockPreview.kt    # Preview 1:1 com vídeo ao vivo, gestos e snap magnético
└── wallpaper/
    ├── DepthAndCarousel.kt          # SubjectDepthEngine (ML Kit + Bokeh/ColorPop) e WallpaperCarousel
    ├── VideoWallpaperRenderer.kt    # Pipeline zero-copy MediaPlayer -> SurfaceTexture -> GLES -> HardwareBuffer
    └── PrismWallpaperService.kt     # WallpaperService orientado a eventos e trava térmica
```

---

## Como Compilar e Instalar

**Requisitos:** Android Studio / JDK 17+, Android SDK (`compileSdk = 36`, `minSdk = 33`, `targetSdk = 35`).

```bash
# Compilar APK Debug
./gradlew assembleDebug

# Compilar APK Release (assinado com debug keystore para testes locais)
./gradlew assembleRelease

# Instalar Release diretamente no aparelho via ADB
./gradlew installRelease
```

No Windows (PowerShell):

```powershell
.\gradlew.bat assembleDebug
```
