# PrismLock

**Transforme sua Tela de Bloqueio e Tela Inicial no Android em uma experiência visual óptica em 120Hz.**

O **PrismLock** é um estúdio completo de **Live Wallpaper** e **personalização interativa** para Android 13+, combinando refração de vidro líquido (*Optical Crystal / AGSL*), papéis de parede em vídeo com aceleração por hardware, motor de fórmulas e variáveis globais estilo KWGT/KLWP, ações de toque por camada, tipografia elástica e pós-processamento 3D offline.

---

## Principais Recursos

- **Cristal Óptico Real (AGSL GPU Shaders)**  
  Simulação física de refração, dispersão cromática RGB, brilho especular de Fresnel e vidro jateado (*frost*) em tempo real sobre a imagem ou vídeo de fundo.

- **Vídeos em Live Wallpaper com Refração ao Vivo**  
  Reproduza `.mp4`, `.webm`, `.mkv` e `.mov` diretamente via aceleração por hardware com refração óptica em tempo real e pausa automática quando fora de exibição.

- **Profundidade 3D Seletiva (ML Kit + Bokeh & Color Pop)**  
  Ative o recorte 3D diretamente nas miniaturas de cada papel de parede e aplique pós-processamento fotográfico offline:
  - **Bokeh de Fundo (Modo Retrato)** (`0%–100%`)
  - **Color Pop P&B** (fundo monocromático ou invertido)
  - **Destaque do Sujeito** e **Vinheta & Halo 3D**

- **Motor de Fórmulas, Globais & FX Estilo KWGT / KLWP**  
  Sintaxe completa `$...$`, variáveis globais `$gv(...)$` e tags `{HH}:{mm}`:
  - **Aba Globais**: crie variáveis de **Cor**, **Interruptor (0/1)**, **Número**, **Texto** e **Lista**, vinculando-as diretamente a qualquer camada ou pilha.
  - **Propriedades Dinâmicas (FX)**: controle visibilidade, cor, opacidade, rotação, escala, deslocamento X/Y e ícones por fórmulas.
  - **Funções Integradas**: Data/Hora (`df`), contagem regressiva (`tf`), Bateria (`bi`), Sistema e Áudio (`si`), Clima Open-Meteo (`wi`), Mídia (`mi`), manipulação de cores (`ce`), repetições (`fl`), condicionais (`if`), texto (`tc`) e matemática (`mu`).

- **Ações de Toque por Item**  
  Configure cliques individuais por camada ou pilha na aba **Toque** para abrir aplicativos instalados, links e deep links, alternar ou ciclar variáveis globais, avançar o papel de parede do carrossel, controlar mídia ou abrir atalhos do sistema na Tela Inicial.

- **Tipografia Elástica & Fontes Personalizadas**  
  Alongamento vertical contínuo de fontes com controle de compensação de traço, detecção automática de fontes do sistema, **8 fontes OFL** embarcadas (`Outfit`, `Inter`, `Space Grotesk`, `Bebas Neue`, `Playfair Display`, `JetBrains Mono`, `Syne`, `Plus Jakarta Sans`), importação avulsa e sincronização automática com pasta de fontes (`.ttf` / `.otf`) com preview ao vivo.

- **Estúdio em 2 Telas + Pacotes Portáteis `.prism`**  
  - **Carrossel Spotlight**: navegação fluida entre temas salvos com gestos verticais (`↑ Editar · ↓ Aplicar`), criação nomeada e dock em vidro fosco.
  - **Estúdio Interativo 1:1**: arraste com *snap* magnético, pinça para escala/rotação, entrada de texto/números em diálogo flutuante e **7 abas** (*Fundo & 3D*, *Camadas*, *Conteúdo*, *Tipografia*, *Vidro / Cor*, *Toque*, *Globais*).
  - **Exportação & Importação `.prism`**: empacote configurações, globais, ações de toque, mídias e fontes em um arquivo `.prism` (ZIP), com suporte também a `.zip` e pastas descompactadas.

---

## Arquitetura

```text
app/src/main/java/com/example/prismlock/
├── MainActivity.kt                  # Orquestração Hub/Studio, pickers SAF e bake de recursos
├── data/
│   ├── Data.kt                      # Persistência DataStore (ProjectStore, fontes, EnvProvider)
│   └── PrismPackageManager.kt       # Empacotamento e importação .prism (ZIP e Pasta SAF)
├── model/
│   └── Model.kt                     # LayerNode, TouchAction, GlobalVar, Glass, Project e Presets
├── engine/
│   ├── Dynamic.kt                   # Tags {HH} e bridge numérica
│   ├── KustomFormulaEngine.kt       # Motor unificado de fórmulas, globais e FX
│   ├── TouchActionExecutor.kt       # Hit-test e execução das ações de toque
│   ├── Icons.kt                     # Ícones vetoriais e importador SVG
│   ├── Type.kt                      # FontManager, StemStretch e GlyphAtlasBaker
│   ├── LiquidGlassRenderer.kt       # Shaders AGSL, cristal óptico e composição
│   └── WallpaperBaker.kt            # Pré-renderização de imagens, máscaras 3D e efeitos
├── ui/
│   ├── PrismTheme.kt                # Paleta monocromática OLED e animações
│   ├── Components.kt                # Componentes reutilizáveis do estúdio
│   ├── ThemeHubScreen.kt            # Carrossel inicial de temas salvos
│   ├── StudioScreen.kt              # Estúdio em 7 abas
│   └── InteractiveLockPreview.kt    # Preview 1:1 interativo com gestos e vídeo ao vivo
└── wallpaper/
    ├── DepthAndCarousel.kt          # Segmentação 3D (ML Kit) e carrossel de mídias
    ├── VideoWallpaperRenderer.kt    # Renderização de vídeo por hardware
    └── PrismWallpaperService.kt     # WallpaperService com suporte a toques e eventos

app/src/test/java/com/example/prismlock/
└── KustomFormulaTest.kt             # Testes unitários do motor de fórmulas e globais
```

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
