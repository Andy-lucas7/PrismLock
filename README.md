# PrismLock

**Transforme sua Tela de Bloqueio no Android em uma obra de arte óptica em 120Hz.**

O **PrismLock** é um estúdio completo de personalização de **Live Wallpaper** e **Tela de Bloqueio interativa** para Android 13+, projetado para unir a estética de vidro líquido refrativo (*Liquid Glass / Optical Crystal*), tipografia elástica sob medida e profundidade 3D com desempenho instantâneo no dia a dia.

---

## Por que o PrismLock é diferente?

- **Cristal Óptico Real (AGSL GPU Shaders)**  
  Diferente de papéis de parede estáticos ou filtros simples, o PrismLock calcula normais 3D em resolução 1:1 sobre os contornos do relógio para simular refração física da luz, dispersão cromática RGB, brilho especular de Fresnel e jateamento fosco (*frost*) — reagindo perfeitamente ao seu papel de parede mesmo com preenchimento transparente.

- **Tipografia Elástica com Preservação Anatômica (`StemStretch`)**  
  Estique os dígitos do relógio verticalmente em tempo real sem distorcer arcos ou transformar barras horizontais em blocos pesados. Suporta fontes do sistema, fontes variáveis (eixos OpenType) e importação de qualquer `.ttf` ou `.otf`.

- **Profundidade 3D Seletiva Offline (ML Kit)**  
  O sujeito da foto passa por cima do relógio com recorte inteligente executado 100% offline no aparelho. No carrossel de pastas, você liga ou desliga o **3D ON / 3D OFF** individualmente em cada imagem.

- **Sincronia Perfeita entre Always On Display (AOD) e Tela de Bloqueio**  
  A troca de imagem do carrossel acontece no momento em que a tela apaga (`ACTION_SCREEN_OFF`), garantindo que o AOD e o despertar exibam exatamente a mesma foto, sem trocas bruscas.

- **Despertar Instantâneo (< 2ms) com Asset Baking Engine**  
  Ao tocar em **Aplicar**, o app pré-compila wallpapers nítidos, camadas jateadas, máscaras 3D e um atlas vetorial para os dígitos do relógio. Durante o uso normal, a animação de despertar roda em 120fps puros sem recalcular shaders pesados ou vetores a cada frame, preservando bateria e evitando aquecimento.

---

## Recursos do Estúdio

- **Canvas 1:1 Interativo**: toque, arraste com *snap* magnético e feedback háptico, use pinça para escala/rotação e puxe a alça inferior para ajustar a elasticidade diretamente na tela.
- **Presets Prontos**: *iOS Vidro 3D*, *Samsung Esticado*, *Xiaomi Canto Clássico* e *Huawei Editorial*.
- **5 Modos de Material**: Sólido, Gradiente (Linear, Radial, Sweep), Adaptativo (cores extraídas automaticamente do wallpaper), Blur Clássico e Cristal Óptico, além de contorno Vazado.
- **Camadas & Placeholders Dinâmicos**: hora, data, bateria, alarme, passos e clima (Open-Meteo), pilhas (`STACK`), formas vetoriais, ícones e importação de SVG.
- **Carrossel via SAF**: escolha uma foto única ou uma pasta inteira do aparelho, com troca ao apagar a tela, por intervalo ou por toque duplo.

---

## Arquitetura

```text
app/src/main/java/com/example/prismlock/
├── MainActivity.kt                  # Estúdio Compose, inspetor em abas e modal de compilação
├── data/Data.kt                     # Persistência DataStore (ProjectStore) e EnvProvider
├── model/Model.kt                   # Camadas, Glass, Project (depthOverrides) e Presets
├── engine/
│   ├── Dynamic.kt                   # Placeholders dinâmicos ({HH}, {mm}, {temp}, {alarm}, etc.)
│   ├── Icons.kt                     # Biblioteca vetorial e importador de SVG
│   ├── Type.kt                      # FontManager, StemStretch e GlyphAtlasBaker
│   ├── LiquidGlassRenderer.kt       # Shaders AGSL, lensHeightMap e composição por minuto
│   └── WallpaperBaker.kt            # Pré-compilação em disco (sharp, frosted, depth, atlas)
├── ui/
│   ├── Components.kt                # Sliders de precisão e seletor de cores
│   └── InteractiveLockPreview.kt    # Preview 1:1 com gestos, snap e preparo assíncrono
└── wallpaper/
    ├── DepthAndCarousel.kt          # SubjectDepthEngine (ML Kit) e WallpaperCarousel (SAF)
    └── PrismWallpaperService.kt     # WallpaperService orientado a eventos
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
.\gradlew.bat installRelease
```
