# PrismLock — Plano de Correção (instruções para o agente)

Repo: `Andy-lucas7/PrismLock` · Base: `app/src/main/java/com/example/prismlock/` (abreviado `P/`).
Números de linha são do commit lido em 23/11/2026; **podem deslocar após cada etapa** → sempre confirme com `grep -n` (comando dado em cada passo) antes de editar.

## Regras globais (valem para TODAS as etapas)

- Não leia arquivos inteiros. Use `grep -n` + `sed -n 'A,Bp'` nos intervalos indicados.
- Nunca leia/abra: `node_modules`, `build/`, `.gradle/`, `.git/`, lockfiles, binários, `res/font/*`.
- Só diffs pontuais (Edit). Não reescreva arquivos. Sem `// TODO`, sem placeholders, sem mocks.
- Sem sensores físicos, **exceto** o leitor de movimento da Etapa 8 (`MotionSource`), único ponto autorizado a usar `SensorManager`. Wallpaper continua event-driven e em `:wallpaper`.
- Buffers internos ≤ 1080px de largura, proporção da tela preservada.
- Ao fim de CADA etapa: `.\gradlew.bat assembleDebug testDebugUnitTest` (Windows) ou `./gradlew assembleDebug testDebugUnitTest`. Corrija até compilar. Um commit por etapa.
- Ordem: **1 → 2 → 6A → 7A → 4 → 3 → 6B → 7B → 8 → 6C → 7C → 5**. A Etapa 8 (Paralaxe) depende da 4 (planos separados em volta do Sujeito 3D), da 7A (renderer sem corrida) e da 7B (RAM sob controle, porque o paralaxe adiciona buffers). (6A = bugs críticos de perda de dados/travamento; 6B = fluxo de widget; 6C = consistência de opções.) Não comece a Etapa 5 sem os números de medição (5.1).
- Legenda de confiança nas etapas 6: **[Confirmado]** = li o código e o defeito é determinístico; **[Verificar]** = suspeita forte, confirmar em execução antes de alterar.

---

## ETAPA 1 — Seletor de foto/vídeo com galeria do sistema

**Problema:** `OpenDocument()` abre Documentos/Arquivos, sem galeria.
**Solução:** `ActivityResultContracts.PickVisualMedia` (Photo Picker) + copiar a mídia para armazenamento próprio (URIs do Photo Picker não são persistentes).

### 1.1 Localizar
```
grep -n "imagePicker\|layerImagePicker\|onPickImage\|onPickLayerImage" P/MainActivity.kt
```
Esperado: `:423` (imagePicker, `OpenDocument`), `:528` (layerImagePicker), `:722` (launch `arrayOf("image/*","video/*")`), `:750` (launch `arrayOf("image/*")`).
**Não mexer** em `folderPicker`(:415), `fontPicker`(:432), `fontFolderPicker`(:442), `svgPicker`(:456), `importThemePicker`(:467), `importFolderThemePicker`(:490), `customAssetPicker`(:536): continuam `OpenDocument*`.

### 1.2 Criar helper de cópia
Arquivo: `P/data/Data.kt` (ou novo `P/data/MediaImporter.kt`, preferível).
```kotlin
object MediaImporter {
    fun importToAppStorage(ctx: Context, uri: Uri): Uri? = runCatching {
        val mime = ctx.contentResolver.getType(uri).orEmpty()
        val ext = when {
            mime.startsWith("video/") -> mime.substringAfter('/').ifBlank { "mp4" }
            mime == "image/png" -> "png"
            mime == "image/webp" -> "webp"
            else -> "jpg"
        }
        val dir = File(ctx.filesDir, "media").apply { mkdirs() }
        val out = File(dir, "${System.currentTimeMillis()}.$ext")
        ctx.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { o -> i.copyTo(o, 64 * 1024) } }
        Uri.fromFile(out)
    }.getOrNull()
}
```
Executar a cópia em `Dispatchers.IO` (não na main thread). Apagar o arquivo antigo em `filesDir/media` quando o `imageUri` for substituído (evita acumular disco).

### 1.3 Trocar os launchers
Em `P/MainActivity.kt`:
- `:423` `imagePicker` → `rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia())`. Callback recebe `Uri?`; **remover** `takePersistableUriPermission`; chamar `MediaImporter.importToAppStorage` e usar o URI retornado em `previewUri`/`commit(copy(imageUri = ..., folderUri = null))`.
- `:528` `layerImagePicker` → mesmo contrato com `PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)`; importar e gravar em `updateSelected { it.copy(imageUri = s) }`.
- `:722` → `imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))`.
- `:750` → `layerImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))`.
- Import necessário: `androidx.activity.result.PickVisualMediaRequest`.

### 1.4 Botão "Arquivos" (fallback SAF)
No ponto de chamada `onPickImage` (UI em `P/ui/StudioScreen.kt`, buscar com `grep -n "onPickImage" P/ui/*.kt`) manter o botão atual como "Galeria" e adicionar um secundário "Arquivos" que chama um novo callback `onPickImageFile` → launcher `OpenDocument` com `arrayOf("image/*","video/*")` (comportamento antigo, mantém `takePersistableUriPermission`).

### 1.5 Verificar leitores
```
grep -n "contentResolver.open\|openFileDescriptor\|setDataSource\|ImageDecoder.createSource" P/wallpaper/*.kt P/engine/WallpaperBaker.kt P/data/*.kt
```
Confirmar que cada leitor aceita URI `file://` (`openInputStream` e `MediaMetadataRetriever/MediaPlayer.setDataSource(ctx, uri)` aceitam). Ajustar se algum usa só `content://`. Verificar também `PrismPackageManager.kt` (export de `assets/`) para não quebrar ao exportar `.prism` com URI `file://`.

> `minSdk = 33` (app/build.gradle.kts): o Photo Picker é nativo, sem dependência extra de Play Services.

**Aceite:** tocar em "escolher foto/vídeo" abre o Photo Picker com a galeria; o fundo persiste após reiniciar o app e o wallpaper roda no processo `:wallpaper`.

---

## ETAPA 2 — Remover o eixo `wght` duplicado

**Causa:** `P/engine/Type.kt:437` → `node.axes[it.tag] ?: if (tag=="wght") node.weight ...`. Depois de mexer no slider "Eixo wght", `axes["wght"]` vence e "Peso" não faz mais nada.

### 2.1 UI
```
grep -n "FontManager.axes(node.font).forEach" P/ui/StudioScreen.kt
```
Esperado `:2796`. Mudar para `FontManager.axes(node.font).filter { it.tag != "wght" }.forEach { ... }`. Manter "Peso" (`:2775`).

### 2.2 Engine
`P/engine/Type.kt:431-438` (`fun variation`): trocar a linha do `m[it.tag] = ...` por:
```kotlin
m[it.tag] = if (it.tag == "wght") node.weight.toFloat().coerceIn(it.min, it.max)
            else node.axes[it.tag] ?: it.def
```
Conferir `:319` (`typeface`): lógica `if (axes(font).any { it.tag == "wght" }) base else Typeface.create(base, weight...)` está correta; não alterar.
Conferir o cache de texto (`grep -n "cacheKey\|\"\\$font|" P/engine/Type.kt`): a chave de `TextShape`/atlas deve conter `weight` (e não `axes["wght"]`).

### 2.3 Migração (temas e `.prism` antigos)
`P/data/Data.kt` — todos os pontos de decode: `:78, :84, :88, :161, :166, :179`. Criar uma função única em `P/model/Model.kt`:
```kotlin
fun LayerNode.migrated(): LayerNode {
    val w = axes["wght"]
    val base = if (w != null) copy(weight = w.roundToInt().coerceIn(1, 1000), axes = axes - "wght") else this
    return base.copy(children = base.children.map { it.migrated() })
}
fun Project.migrated(): Project = copy(layers = layers.map { it.migrated() })
```
Aplicar `.migrated()` logo após cada `decodeFromString(Project...)` em `Data.kt` e no import em `P/data/PrismPackageManager.kt` (`grep -n "decodeFromString" P/data/PrismPackageManager.kt`). Conferir se os campos de `Project` são `layers` e `LayerNode.children` (`Model.kt:73+`).

**Aceite:** com fonte variável, só aparece "Peso"; mexer em qualquer eixo (wdth etc.) não trava o Peso; tema antigo com `axes.wght` abre com o peso correto.

---

## ETAPA 4 — Escolher o que fica à frente/atrás do sujeito 3D

**Causa raiz (revisada na 3ª varredura) [Confirmado]:** existem **dois caminhos de render com regras diferentes**.
- Preview do Estúdio: `InteractiveLockPreview.kt:486` → `renderScaledBaseFrame` (`LiquidGlassRenderer.kt:614`) → `render()` (`:468`). O loop em `:515-535` desenha a camada DEPTH **na posição dela na lista**: o que está acima de "Sujeito 3D" na lista já aparece à frente no preview.
- Tela de bloqueio e Hub: `PrismWallpaperService.kt:712` e `renderPreviewBitmap` (`:675`) → `bakeMinuteScene` (`:833`). Ele junta **todas** as camadas em `clockLayerBmp` e desenha o sujeito **sempre por cima** (`:944-954`, `:1080-1086`).
O usuário vê no Estúdio uma coisa e no bloqueio outra (quebra de WYSIWYG).

**Abordagem preferida (menor e sem campo novo):** a ordem da lista manda nos dois caminhos. Em `bakeMinuteScene`, dividir as camadas raiz em "antes do DEPTH" (`clockLayerBmp`) e "depois do DEPTH" (`overlayLayerBmp`), seguindo a mesma regra do loop `:515-535`. Na UI, a linha "Sujeito 3D" em `LayersTab` (`StudioScreen.kt:965`) ganha a legenda "Camadas acima ficam à frente do recorte", e as setas de mover já existentes resolvem a escolha. O campo `aboveSubject` (4.1) só é necessário se você quiser um botão explícito por camada. Nesse caso ele vira atalho que **move** a camada para cima/baixo do DEPTH na lista, sem criar uma segunda fonte de verdade.
Também tratar (ver 7.11): limitar a **um** DEPTH por projeto e só na raiz.

**Causa original anotada:** o sujeito recortado é desenhado **depois** de todo o `clockLayerBmp` em 3 lugares, então tudo fica atrás dele; `LayerNode` não tem flag.

### 4.1 Modelo
`P/model/Model.kt`, em `LayerNode` (declara em `:73`; adicionar perto de `hidden/locked`, `:85-86`):
```kotlin
val aboveSubject: Boolean = false
```
`ignoreUnknownKeys` já está ligado em `Data.kt:60`, então temas antigos continuam carregando (padrão = atrás, comportamento atual).
Presets que devem sair com data/hora à frente: `Model.kt:325-328`, `:338-341`, `:351-354` (camadas "Data", "Hora..."): setar `aboveSubject = true` onde fizer sentido. Subcamadas de STACK herdam do pai (usar o valor do nó raiz).

### 4.2 Renderer — separar duas passagens
```
grep -n "LayerType.DEPTH\|clockLayerBmp\|showDepth\|hasDepthLayer" P/engine/LiquidGlassRenderer.kt
```
Pontos (lidos):
- `:515-535` loop de camadas raiz (desenho em ordem) — o ramo `DEPTH` em `:523` já existe; filtrar nós por `aboveSubject` conforme a passagem.
- `:775` e `:884` — loops com `node.type == LayerType.DEPTH` (preparação/medição); manter.
- `:911` criação do `clockLayerBmp` (só com `needsHardwareShader`); `:928-930` `hasDepthLayer/showDepth`; `:944-954` desenho do sujeito dentro de `completeBmp`.
- `:1080-1086` desenho do sujeito no frame em tempo de execução (`scene.showDepth`).

Implementar:
1. Adicionar `overlayLayerBitmap: Bitmap?` ao objeto de cena (mesmo tipo que carrega `clockLayerBitmap`/`showDepth`, ver `grep -n "class .*Scene\|clockLayerBitmap" P/engine/LiquidGlassRenderer.kt`).
2. Ao compor camadas: nós com `aboveSubject == false` → `clockLayerBmp` (atrás); com `true` → `overlayLayerBmp`.
3. **Só alocar `overlayLayerBmp` se `showDepth && project.layers.any { it.aboveSubject && !it.hidden }`** (evita +10MB de RAM para quem não usa). Reusar o mesmo mecanismo de `renderNodeToBitmap` (`:1116`).
4. Ordem de desenho: fundo → dim → `clockLayerBmp` → sujeito (`:1080`) → `overlayLayerBmp`. Replicar em `completeBmp` (`:944-954`: após `drawBitmapCenterCrop(... depthMask ...)` desenhar `overlayLayerBmp`) com mesma alpha/`condAlpha`.
5. Se `showDepth` for falso, camadas `aboveSubject` caem em `clockLayerBmp` (sem sujeito não há diferença).
6. Liberar/reciclar `overlayLayerBmp` onde `clockLayerBmp` é liberado (`grep -n "recycle()" P/engine/LiquidGlassRenderer.kt`).

### 4.3 Wallpaper service e preview
- `P/wallpaper/PrismWallpaperService.kt:705-718, 818, 877, 890`: chamadas que passam `depthMask` ao renderer — nada a mudar se o renderer encapsula; confirmar que o frame pré-composto RGB_565 (ACTION_TIME_TICK) usa `completeBmp` (já inclui a passagem 4.2.4).
- `P/ui/InteractiveLockPreview.kt:100`: pula DEPTH no hit-test; manter.
- `P/engine/WallpaperBaker.kt:447-458`: bake do sujeito; sem alteração.

### 4.4 UI no Estúdio
`P/ui/StudioScreen.kt`, `PositionAndTouchTab` (`:3154`). Adicionar abaixo dos controles de posição, visível quando `project.depth` e existe camada DEPTH (`project.layers.any { it.type == LayerType.DEPTH }`) e `node.type != DEPTH`:
- Texto "Em relação ao sujeito 3D" + dois `FilterChip` ("Atrás" / "À frente"), padrão do arquivo (`prismFilterChipColors()`, `prismFilterChipBorder(sel)`), `update { it.copy(aboveSubject = ...) }; onSaveFinished()`.
- Opcional: em `LayersTab` (`:965`) um ícone pequeno na linha da camada indicando "à frente".

**Aceite:** data/hora com "À frente" aparece sobre o sujeito recortado no preview, na tela de bloqueio e no frame pré-composto; com "Atrás" o comportamento é igual ao atual.

---

## ETAPA 3 — Blur suave ao arrastar o slider

**Causa:** `P/engine/LiquidGlassRenderer.kt:1193` `getFrostedShader` chama `renderBlurredBitmap` (`:1213`, `HardwareRenderer`+`ImageReader`+`latch.await(200ms)`) em resolução cheia para cada raio inteiro, com `frostedShaderCache` de 2 (`:212`) e snap ±2px do prebake (`:1197`).

### 3.1 Localizar
```
grep -n "frostedShaderCache\|prebakedFrostedBlurPx\|renderBlurredBitmap\|getFrostedShader\|bakeBlurredBitmap" P/engine/*.kt P/wallpaper/*.kt P/ui/*.kt
```
Usos de blur: `:759`, `:763` (preparação), `:2020` (`drawClassicBlur`), `:2101` (Cristal). Sliders: `P/ui/StudioScreen.kt:3007` ("Blur Gauss") e `:3054` ("Blur Jato"); slider genérico `P/ui/Components.kt:512-560`.

### 3.2 Blur em baixa resolução durante o arraste
1. Adicionar a `LiquidGlassRenderer` um flag `@Volatile var interactiveBlur = false`.
2. `getFrostedShader` (`:1193`): se `interactiveBlur`, gerar com `w/4 × h/4` e raio `radius/4` (o `BitmapShader` escala via matriz local do shader; usar `shader.setLocalMatrix(Matrix().apply { setScale(4f, 4f) })`) e **não** gravar no `frostedShaderCache`.
3. Cache de resolução cheia só quando `!interactiveBlur`; chave inclui raio exato (sem snap). **Remover** a tolerância `abs(prebakedFrostedBlurPx - blurPx) <= 2f` em `:1197`: usar o pré-bake apenas se `abs(...) < 0.5f`.

### 3.3 Último valor vence (sem fila, sem bloquear a UI)
- Mover a geração do blur para o thread `PrismBlurGen` com um único job pendente (`AtomicReference<Float>` com o último raio pedido). Novo pedido substitui o pendente; ao concluir, publicar o shader e invalidar o preview. Mostrar o último shader pronto enquanto o novo é gerado (sem piscar).
- `renderBlurredBitmap` (`:1213`): trocar `latch.await(200, MILLISECONDS)` por espera com timeout curto só no caminho de resolução cheia; o caminho interativo não deve bloquear a thread de desenho.

### 3.4 Ligar ao slider
- Em `P/ui/Components.kt` `PrecisionSliderField` (`:527-554`) já existe `dragging`. Expor `onDragStateChange: ((Boolean) -> Unit)? = null` (opcional) e chamar em `onValueChange` (`dragging=true`) e `onValueChangeFinished`.
- Nos dois sliders de blur (`StudioScreen.kt:3007`, `:3054`) passar `onDragStateChange = { LiquidGlassRenderer.interactiveBlur = it }` (confirmar o nome do objeto/instância com `grep -n "LiquidGlassRenderer" P/ui/StudioScreen.kt | head`).
- Ao soltar (`onValueChangeFinished`): `interactiveBlur=false` e disparar **uma** geração em resolução cheia.
- Mesmo tratamento para "Bokeh Fundo" (`StudioScreen.kt:3691`, `depthBgBlur`; `P/wallpaper/DepthAndCarousel.kt:162` usa `blurPx` em `composeProcessedWallpaper`): usar resolução reduzida durante o arraste.

### 3.5 Remover duplicação
`:1116` (`renderNodeToBitmap`) e `:1213` (`renderBlurredBitmap`) repetem criação de `HandlerThread`/`ImageReader`/`HardwareRenderer`. Extrair uma classe privada `OffscreenRenderer(w,h)` com `render(node): Bitmap` e usar nas duas. Um único `PrismBlurGen`.

**Aceite:** arrastar "Blur Gauss"/"Blur Jato" de 0 a 80 atualiza de forma contínua (sem saltos), sem travar a UI; ao soltar, a imagem final fica nítida em resolução cheia.

---

## ETAPA 5 — RAM e limpeza de código

### 5.1 Medir ANTES (obrigatório; o agente não consegue rodar o app sozinho — peça os números ao usuário)
No aparelho, com o app em cada estado (Home, Estúdio aberto com wallpaper estático, Estúdio com 3D ligado, Estúdio com vídeo, wallpaper ativo):
```
adb shell dumpsys meminfo com.example.prismlock
adb shell dumpsys meminfo com.example.prismlock:wallpaper
```
Registrar "Graphics", "Native Heap", "Java Heap", "TOTAL PSS" por processo. Rodar de novo depois de cada item abaixo.

### 5.2 Maiores consumidores esperados (1080×2400 ARGB_8888 ≈ 10,4 MB por bitmap)
Auditar com `grep -n "createBitmap\|ARGB_8888\|RGB_565\|ALPHA_8" P/engine/*.kt P/wallpaper/*.kt P/ui/*.kt` (17 ocorrências de ARGB_8888):
1. `lensHeightCache` (`LiquidGlassRenderer.kt:213`, LRU 3; geração em `:1854-1975`): só precisa de alpha → criar com `Bitmap.Config.ALPHA_8` (¼ do tamanho) e `LruCache(1)` fora de arraste. Atenção: `extractAlpha`/shader AGSL (`:99-121`) lê `.a`; ALPHA_8 funciona com `BitmapShader` (conferir visual).
2. `sharpBg`/`frosted`/`prebakedFrosted*`/`exactSharp` (`:1166-1212`, `:789`): evitar manter 3 cópias; liberar `frosted` quando nenhuma camada usa blur/vidro.
3. `depthMask` (`DepthAndCarousel.kt:39`, `memCache(1)`; `WallpaperBaker.kt:64`; `PrismWallpaperService.kt:93`): liberar quando `project.depth == false` ou ao sair do Estúdio; manter `SubjectDepthEngine.releaseMemory()` (`WallpaperBaker.kt:320, 490`; `PrismWallpaperService.kt:75`) chamado em `onTrimMemory`.
4. `clockLayerBmp` + `completeBmp` (`LiquidGlassRenderer.kt:911-960`): `completeBmp` já usa RGB_565 quando `useRgb565ForComplete`; garantir `true` no caminho do service.
5. Decodificação: toda carga de imagem com `inSampleSize`/`ImageDecoder.setTargetSize` para a largura de tela ≤1080 (`grep -n "decodeStream\|decodeFile\|ImageDecoder" P/**/*.kt`).
6. Preview do Estúdio e Hub (`ThemeHubScreen.kt`, `HubPreviewCache`, `thumbCache(12)` em `DepthAndCarousel.kt:220`): limitar por bytes (`LruCache.sizeOf`), não por contagem.
7. ML Kit (`SubjectDepthEngine`, `DepthAndCarousel.kt:36-60`): fechar o `segmenter` após uso (`close()`) e só no processo principal.
8. `onTrimMemory`/`onLowMemory` em `MainActivity` e `PrismWallpaperService`: `evictAll()` em todos os caches (`LiquidGlassRenderer.kt:305-336` já tem rotinas de limpeza; reaproveitar).

### 5.3 Limpeza de código (sem mudar comportamento)
- Dividir `P/ui/StudioScreen.kt` (3806 linhas) por aba, em arquivos novos no mesmo pacote `ui`: `LayersTab` (`:965`), `ContentTab` (`:1387`), `GlobalsTab` (`:2128`), `ShapeAndFontTab` (`:2599`), `GlassAndColorTab` (`:2883`), `PositionAndTouchTab` (`:3154`), seção 3D/Projeto (`:3445`, `:3735`). Mover como estão (cortar/colar por intervalo com `sed`), sem reformatar; apenas ajustar `private` → `internal` e imports.
- Unificar a duplicação de `drawBitmapCenterCrop` (`LiquidGlassRenderer.kt:1320`)/`checkCoverage` (`:1329`) e o desenho do sujeito repetido em 3 lugares (já tratado na 4.2).
- Procurar redundâncias: `grep -n "private fun \(draw\|render\|bake\)" P/engine/*.kt` e compare assinaturas parecidas; remover código morto (`grep -n "@Suppress\|unused"`; verifique referências com `grep -rn "nomeDaFuncao" P` antes de apagar).
- Remover as 3 cópias de decode JSON em `Data.kt` (`:78,:84,:88,:161,:166,:179`) para uma função `decodeProject(String)` que já aplica `.migrated()` (Etapa 2.3).

### 5.4 Meta
Não prometer "<50MB" para o app inteiro. Depois da medição, fixar metas por processo (ex.: `:wallpaper` ≤ 80 MB PSS; principal sem Estúdio aberto ≤ 120 MB) e atualizar `README.md`/`DOCUMENTATION.md` com os valores medidos.

**Aceite:** PSS por processo reduzido e documentado; app compila; nenhum efeito visual regrediu (Cristal Óptico, Blur Clássico, Fonte Elástica, 3D).

---

## ETAPA 6 — Bugs, inconsistências e lógica sem nexo (achados adicionais)

Caminhos abreviados: `P/` = `app/src/main/java/com/example/prismlock/`. Uma subetapa = um commit.

### 6A — Críticos: perda de dados e travamento

**6A.1 [Confirmado] Apply do wallpaper trava o app se o bake falhar**
`P/MainActivity.kt:363-401` (`applyAndBake`): `isBaking = true` (`:377`) e `isBaking = false` (`:387`) sem `try/finally`. Qualquer exceção em `WallpaperBaker.bakeAll` (`:382`) deixa `isBaking=true` para sempre: overlay de progresso preso e `if (isBaking) return` bloqueia novas tentativas.
Correção: envolver `bakeAll` + `sendBroadcast` em `try { ... } catch (e: Exception) { snackbar.showSnackbar("Falha ao aplicar: ${e.message}") } finally { isBaking = false }`; relançar `CancellationException`.

**6A.2 [Confirmado] Salvamento com corrida: estado antigo sobrescreve o novo**
`P/MainActivity.kt:205-213` (`saveFlow.debounce(400)`) vs `:308-311` (`stage` emite no flow) vs `:327` (`commit` grava direto via `persistProject`, sem emitir).
Cenário: `stage(A)` (arrasto de slider) → `commit(B)` (B ⊇ A) em <400ms → o debounce dispara 400ms depois e grava **A** por cima de **B**; a mudança do `commit` se perde no disco.
Correção: um único escritor. `commit` deve fazer `editingProject = p; saveFlow.tryEmit(p); flushNow()` onde `flushNow` cancela o debounce pendente e grava o último valor; ou `persistProject` passa a gravar só `currentProject` mais recente (comparando um contador de versão incremental `rev`, descartando gravações com `rev` menor que o último gravado).

**6A.3 [Confirmado] Falha de decode apaga a lista de temas/widgets do usuário**
`P/data/Data.kt:189-201` (`upsertTheme`), `:204-221` (`deleteTheme`), `:366-392` (`upsertWidget`), `:393-` (`deleteWidget`), `:78` e `:84-88`: o padrão `json.decodeFromString(...)` dentro de `runCatching{}.getOrNull() ?: default...` trata "JSON inválido" igual a "chave ausente" e depois **regrava** a lista com padrão/um item. Uma mudança de schema ou escrita corrompida zera todos os temas.
Correção: separar os casos. Chave ausente → defaults. Decode falhou → **não gravar**; copiar o JSON bruto para `themes_backup_<timestamp>` (arquivo em `filesDir`), logar (`Log.e`) e avisar o usuário. Aplicar também o `.migrated()` da Etapa 2.3.

**6A.4 [Confirmado] Fonte da verdade duplicada (DataStore + arquivo) e globais do widget perdidas**
`P/data/Data.kt:172-186` (`save`), `:333-364` (`getActiveWidget/saveActiveWidget`): o projeto ativo existe em **dois** lugares (DataStore `KEY`/`ACTIVE_WIDGET_KEY` e arquivo atômico). `saveActiveWidget` só atualiza o DataStore e a lista `WIDGETS_KEY` quando `!isWallpaperProcess()` (`:352`). O toque no widget roda em `:wallpaper` (`PrismWidgetProvider.kt:47-80`): globais alteradas (`TOGGLE_GLOBAL`, etc.) gravam só o arquivo; a lista no DataStore fica com o valor antigo e o próximo "Aplicar" no Estúdio desfaz o toggle.
Correção: eleger o arquivo como fonte para o estado de runtime (globais) e tratar a lista de temas só como "biblioteca". Criar `ProjectStore.patchActiveWidgetGlobals(ctx, map)` que grava só `globals` num arquivo `widget_state.json` lido por `getActiveWidget()` e aplicado por cima do projeto; o processo principal funde na lista ao abrir o Estúdio.

**6A.5 [Confirmado] Excluir o widget ativo não limpa `ACTIVE_WIDGET_KEY`**
`P/data/Data.kt:393-400`: `deleteWidget` remove da lista e, se esvaziar, recria um starter, mas não atualiza `ACTIVE_WIDGET_KEY`/arquivo ativo. Widgets já fixados continuam renderizando o projeto "excluído" até o próximo "Aplicar". Mesmo padrão em `deleteTheme` (`:211`): excluir o último tema ressuscita um starter sem avisar.
Correção: se `widgetId == id do ativo` → `saveActiveWidget(ctx, remaining.first())` e `PrismWidgetProvider.updateAll`. Para temas, se a lista ficar vazia, mostrar estado vazio no Hub ("Nenhum tema, criar novo") em vez de ressuscitar. A confirmação de exclusão já existe (`pendingDelete`, `ThemeHubScreen.kt:~262/806`); manter.

**6A.6 [Confirmado] Cidade padrão fantasma: clima sempre de São Paulo**
`P/model/Model.kt:167-169`: `city = ""`, `lat = -23.55`, `lon = -46.63`. `PrismWallpaperService.kt:598-604` (`refreshWeatherIfNeeded`) usa `lastLocation` e cai em `project.lat/lon`. Como `ACCESS_COARSE_LOCATION` nunca é solicitada em tempo de execução (nenhum `requestPermissions`/`RequestPermission` no repo), `lastLocation` é sempre `null` e todo usuário sem cidade vê o clima de São Paulo, com `{city}` vazio.
Correção: `lat/lon` anuláveis (`Double?`); se `null` e sem permissão → `temp = null` e `{temp}` mostra "--"; na aba "Clima & Automação" exibir botão "Usar minha localização" que solicita `ACCESS_COARSE_LOCATION` via `rememberLauncherForActivityResult(RequestPermission())` e salva lat/lon + reverse geocode. Migração: `lat == -23.55 && lon == -46.63 && city.isBlank()` → tratar como não definido.

**6A.7 [Confirmado] `geocode` falha em silêncio**
`P/ui/StudioScreen.kt:3799`: `if (g != null) onCommit(...)`; se a busca falhar (offline/cidade inexistente) nada acontece e o diálogo já fechou. Mostrar `Toast`/snackbar "Cidade não encontrada ou sem internet" e reabrir o diálogo com o texto digitado.

### 6B — Fluxo de widget (construção, aplicação, execução)

**6B.1 [Confirmado] Toque no widget ignora onde você tocou**
`P/widget/PrismWidgetProvider.kt:62`: `widget.layers.flatten()...firstOrNull { !it.hidden && it.touch.isActive }` executa **sempre a primeira** camada com ação. Widget com 2 botões (ex.: Play e Próxima) só aciona o primeiro. A tela de bloqueio já tem hit-test por matriz inversa (`TouchActionExecutor`/`PrismWallpaperService.kt:571`).
Correção: o `RemoteViews` não informa o ponto tocado, então são necessárias áreas de clique separadas. Em `R.layout.prism_widget` usar um `FrameLayout` com a `ImageView` do bitmap e uma `View` transparente por camada acionável (pool fixo, ex.: 6 views, as não usadas ficam `GONE`). Posicionar cada uma com `setViewLayoutMargin` (API 31+, ok com `minSdk 33`) e tamanho por `setViewLayoutWidth/Height`, calculados a partir do retângulo `Measured` da camada em relação ao tamanho real do widget. Cada view recebe `setOnClickPendingIntent` com `requestCode = id.hashCode()` e extra `layerId`. Em `ACTION_WIDGET_TAP`, ler `layerId` e executar só essa camada.

**6B.2 [Confirmado] "Aplicar & Fixar Widget" abre o diálogo de fixar toda vez**
`P/MainActivity.kt:363-375`: chama `PrismWidgetProvider.requestPin(ctx)` sempre, mesmo com widget já fixado (`hasPinnedWidgets` existe e não é usado aqui). Mensagem "pronto para fixar!" aparece mesmo quando já havia widget. Correção: se `hasPinnedWidgets` → só `updateAll` + "Widget atualizado"; senão `requestPin`; se `requestPin` falhar (launcher sem suporte) → instrução "Adicione manualmente pela lista de widgets".

**6B.3 [Confirmado] Todos os widgets fixados mostram o mesmo projeto; não há escolha por instância**
`P/data/Data.kt:333` `getActiveWidget` é único e `PrismWidgetProvider.kt:~150` renderiza o mesmo para todos os `appWidgetIds`. "Aplicar" um novo widget troca **todos** os já fixados.
Correção: guardar `Map<appWidgetId, projectId>` (arquivo `widget_bindings.json`); criar `WidgetConfigActivity` (`android:configure` em `res/xml/prism_widget_info.xml`) que lista os widgets salvos e grava o vínculo; "Aplicar" no Estúdio atualiza apenas o vínculo escolhido (ou cria novo). `renderAndPushWidgets` resolve o projeto por `id`. Limpar vínculo em `onDeleted`.

**6B.4 [Confirmado] Relógio do widget só atualiza a cada 30 min se o wallpaper PrismLock não for o ativo**
`res/xml/prism_widget_info.xml`: `updatePeriodMillis="1800000"`. A atualização por minuto só ocorre em `PrismWallpaperService.kt:130-133` (`ACTION_TIME_TICK` do engine do wallpaper). Quem usa só o widget vê hora defasada até 30 min.
Correção: alarme leve por minuto apenas enquanto houver widget com camada dependente de tempo (`Dynamic`/`KustomFormulaEngine` tokens de hora) e tela ligada: `AlarmManager.setWindow(RTC, próximoMinuto, 5_000, pi)` reagendado em cada update; parar em `onDisabled`. Reagir também a `ACTION_USER_PRESENT`/`SCREEN_ON` (registrar dinamicamente num `Service` curto) para atualizar ao acordar. Documentar o custo de bateria no README.

**6B.5 [Confirmado] Presets de wallpaper aparecem no editor de widget e resultam em widget vazio**
`P/ui/StudioScreen.kt:872`: `(if (project.isWidget) Presets.allWidgets + Presets.all else Presets.all)`. Os presets de tela (`Model.kt:325-355`) têm camadas `cond = LOCK_ONLY` e `LayerType.DEPTH`; no widget `env.locked = false` (`PrismWidgetProvider.kt:~135, ~158`), então `LOCK_ONLY` fica invisível e DEPTH não existe (`rawDepthBitmap = null`, `MainActivity.kt:~262`). Resultado: widget em branco.
Correção: widget mostra só `Presets.allWidgets`. Na aba de camadas do widget, esconder "Sujeito 3D" e as condições `LOCK_ONLY/HOME_ONLY` (`grep -n "Cond.entries\|Cond.LOCK_ONLY" P/ui/StudioScreen.kt`) ou renomeá-las para o contexto do widget.

**6B.6 [Confirmado] Proporção do widget: três faixas diferentes e preview não bate com o real**
- Preview do Estúdio: `StudioScreen.kt:353` → `coerceIn(0.75f, 3.8f)`.
- Hub/Provider/pacote: `ThemeHubScreen.kt:300,319`, `PrismPackageManager.kt:476`, `MainActivity.kt:259` → `coerceIn(0.6f, 4.5f)`.
- `PrismWidgetProvider.kt:170` ignora `widgetAspect` e usa `minW/maxH` do launcher (mistura largura mínima com altura máxima, proporção que não existe de fato). Camadas posicionadas por fração distorcem frente ao que foi desenhado.
Correção: constantes únicas em `Model.kt` (`WIDGET_ASPECT_MIN = 0.6f`, `WIDGET_ASPECT_MAX = 4.5f`) usadas em todos os pontos. No provider, renderizar com a proporção do **slot real** (`OPTION_APPWIDGET_MIN_WIDTH/MIN_HEIGHT` em retrato, `MAX_WIDTH/MAX_HEIGHT` em paisagem; usar `AppWidgetManager.OPTION_APPWIDGET_SIZES` na API 31+) e posicionar por `anchor` com layout adaptativo, ou avisar no Estúdio "este widget foi desenhado para 2:1". Presets de formato em `StudioScreen.kt:3487-3492` (2×2 = 1.05, 4×2 = 2.0, 4×1.8 = 2.2, 4×1 = 3.4) devem derivar de células reais (`(70*n-30)dp`).

**6B.7 [Confirmado] Ações inúteis ou sem feedback no widget**
`P/engine/TouchActionExecutor.kt:133`: `NEXT_WALLPAPER` chama `onNextWallpaper()` (padrão `{}`) e retorna `true`; no widget nada acontece. `onFeedback` também é no-op no widget (`PrismWidgetProvider.kt:~64`), então "Aplicativo não encontrado" nunca aparece. Correção: no widget, `NEXT_WALLPAPER` envia broadcast para o wallpaper service; passar `onFeedback = { Toast }` (via `Handler(Looper.getMainLooper())`). No Estúdio, esconder ações incompatíveis com widget (ver `grep -n "TouchActionType.entries" P/ui/StudioScreen.kt`).

**6B.8 [Confirmado] Pré-visualização do widget da lista do Android sem imagem**
`res/xml/prism_widget_info.xml`: `android:description="@string/app_name"` e nenhum `previewImage`/`previewLayout`. Adicionar `android:previewLayout="@layout/prism_widget_preview"` (API 31+), descrição própria em `strings.xml` e `android:configure` (6B.3). `widgetCategory="home_screen|keyguard"`: `keyguard` não tem efeito relevante com `minSdk 33`; manter só `home_screen`.

**6B.9 [Confirmado, segurança] Receiver do widget aceita intents de outros apps**
`P/AndroidManifest.xml:57-70`: receiver `exported="true"` com `ACTION_WIDGET_TAP`/`ACTION_WIDGET_REFRESH` no `intent-filter`. Qualquer app pode disparar o toque e, portanto, ações do usuário (abrir apps/URLs, alternar globais). Os `PendingIntent`s do app usam intent **explícito** e não precisam de filtro.
Correção: remover as duas ações do `intent-filter` (deixar só `APPWIDGET_UPDATE`); mover TAP/REFRESH para um segundo `<receiver android:exported="false">` (`PrismWidgetActionReceiver`) usado só pelos `PendingIntent`s.

**6B.10 [Confirmado, segurança] Pacote `.prism` importado pode carregar ações perigosas**
`P/engine/TouchActionExecutor.kt:153-160` (`OPEN_URL`): aceita qualquer esquema (`Uri.parse`, `Dynamic.eval` do campo). Um tema compartilhado pode abrir `intent:`, `file:`, `content:`. `OPEN_APP` abre qualquer pacote. Correção: lista de esquemas permitidos (`http`, `https`, `mailto`, `tel`), rejeitar os demais; ao importar `.prism` (`PrismPackageManager.kt`, rotina de import ~`:232-531`) listar ações de toque encontradas e pedir confirmação ("Este tema contém ações que abrem apps/links").

### 6C — Opções sem nexo, mortas ou inconsistentes

**6C.1 [Confirmado] "Passos" é uma opção morta**
`env.steps` nunca é atualizado (`Data.kt:429` default `0`; único valor é o mock `ThemeHubScreen.kt:192`). A UI oferece o preset "Passos" (`StudioScreen.kt:1729`), `{steps}` (`Dynamic.kt:27,40,125`), ícone (`Icons.kt:70,133`) e `KustomFormulaEngine.kt:631`. O manifest declara `ACTIVITY_RECOGNITION` mas nunca pede em tempo de execução, e `AGENTS.md` proíbe `SensorManager`.
Decisão (escolher uma): **(a)** remover `steps` do preset, do `{steps}`, do `Env`, do ícone e a permissão do manifest; ou **(b)** implementar via Health Connect/`HealthServicesClient` sem sensor direto, com pedido de permissão e atualização no `updateAll`. Recomendado (a) agora.

**6C.2 [Confirmado] Permissões declaradas que nunca são pedidas**
`AndroidManifest.xml:6-10`: `ACCESS_COARSE_LOCATION` (ver 6A.6) e `ACTIVITY_RECOGNITION` (6C.1). Remover o que não for usado. `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (`MainActivity.kt:641`) é restrita pela política da Play Store; considerar abrir `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (não exige a permissão) ou explicar no README.

**6C.3 [Confirmado] `allowBackup="true"` com regras de exemplo vazias**
`AndroidManifest.xml:20`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` são os templates padrão comentados: o backup do Google leva o DataStore inteiro (temas com URIs `content://` que não existem em outro aparelho). Correção: definir regras explícitas, incluindo `datastore/` e `files/media/` (da Etapa 1) e excluindo caches (`depth_masks`, `cache/`), ou `allowBackup="false"`.

**6C.4 [Verificar] Slider "Blur Gauss" vs "Blur Jato" gravam o mesmo campo**
`StudioScreen.kt:3007` e `:3054` alteram `glass.blur`, e `LiquidGlassRenderer.kt:759-763` interpreta de formas diferentes (`coerceAtLeast(18f * frostAmount)` quando `frostAmount > 0.01`): no Cristal, o valor exibido pode não ser o aplicado. Medir em execução; se confirmado, mostrar o valor efetivo ou separar `frostBlur` do `blur`.

**6C.5 [Verificar] Camadas aninhadas (STACK)**
`LiquidGlassRenderer.kt:515-520` ignora filhos na raiz via `nestedChildIds`, e `PrismWallpaperService.kt:571` só considera `HOME_ONLY/touch` no primeiro nível. Verificar se filhos de STACK com `cond`/`touch` próprios funcionam (herança descrita em `AGENTS.md`); se não, aplicar `flatten()` com herança do pai.

**6C.6 [Confirmado] Erros engolidos em ~150 pontos**
`grep -rc runCatching` soma ~148 ocorrências (`Data.kt` 27, `VideoWallpaperRenderer.kt` 20, `LiquidGlassRenderer.kt` 19, `DepthAndCarousel.kt` 14, `PrismPackageManager.kt` 13…), e `CoroutineScope.launchSafe` (`Data.kt` final) descarta qualquer exceção. Isso mascara os defeitos acima (6A.3, 6A.1). Correção: helper `inline fun <T> runLogging(tag: String, block: () -> T): T?` com `Log.w(tag, e)`, usá-lo nos caminhos de I/O, decode, bake e vídeo; `launchSafe` deve logar e relançar `CancellationException`.

**6C.7 [Confirmado] Desempenho/RAM: lista inteira de temas num único valor do DataStore**
`Data.kt:189-201`, `:366-392`: cada `upsert` decodifica e recodifica **todos** os temas (`THEMES_KEY`) em uma única string; durante arraste de slider, o debounce de 400ms refaz isso continuamente, gerando cópias grandes de String/JSON na heap. Também `getActiveWidget` faz `prismStore.data.first()`.
Correção: um arquivo JSON por projeto (`filesDir/projects/<id>.json`) + índice leve (`index.json` com id, título, tipo, mtime); `upsert` grava só o arquivo alterado; DataStore guarda apenas preferências pequenas. Migrar na primeira execução a partir de `THEMES_KEY`/`WIDGETS_KEY`.

**6C.8 [Confirmado] Mock fixo no Hub**
`ThemeHubScreen.kt:182`: `HUB_MOCK_TIME = 2026-11-17 18:45` e `steps = 4280` (`:192`). Data fixa envelhece e o dado falso reforça a opção morta de 6C.1. Usar só `LocalTime` 18:45 com a data atual.

**Aceite da Etapa 6:** (6A) forçar exceção em `bakeAll` não trava o botão; `stage`+`commit` rápidos persistem o último estado; JSON corrompido não apaga temas; (6B) dois botões no widget acionam ações distintas; widget já fixado não reabre o diálogo; relógio do widget atualiza sem o wallpaper ativo; apps externos não conseguem disparar o toque; (6C) sem "Passos" ou com permissão real; clima não mostra São Paulo sem configuração.

---

## ETAPA 7 — 3ª varredura: robustez sob uso agressivo, RAM e redundâncias

Cenário mental usado: tocar um botão, voltar no meio, tocar outro ao mesmo tempo, girar a tela, sair para a Home durante o "Aplicar", importar pacote malicioso. `P/` = `app/src/main/java/com/example/prismlock/`.

### 7A — Estado e concorrência (perda de edições, crash)

**7A.1 [Confirmado] Leitura de estado velho: edições seguidas na mesma callback se perdem**
`P/MainActivity.kt:240` `val currentProject by rememberUpdatedState(editingProject)`. Esse valor **só atualiza na recomposição**. `updateLayer` (`:402-404`), `updateSelected`, `flushSave` (`:323`) e os pickers leem `currentProject` logo depois de `stage/commit` na mesma callback e recebem o projeto **anterior**.
Casos reais:
- `customAssetPicker` (`:536-563`): `commit(...customAssets + alias)` e em seguida `updateSelected {...}` → `stage` parte do projeto antigo e **apaga o ativo recém-salvo**.
- Pinça no preview (`InteractiveLockPreview.kt:316-330`): `it.scale * zoom` aplicado várias vezes por frame sobre o mesmo valor velho → zoom/rotação "engasgam" e perdem incrementos com toque de alta taxa.
- Slider: `onValueChangeFinished → flushSave()` grava o penúltimo valor; o último só chega pelo debounce (perdido se o app morrer em <400ms).
Correção: trocar `currentProject` por leitura direta de `editingProject` (é `mutableStateOf`, atualiza na hora) em todas as funções de `MainActivity`; ou manter um `AtomicReference<Project>` atualizado dentro de `stage/commit`. Remover `rememberUpdatedState` de `currentProject` e `currentSelectedId` (`:240-241`).

**7A.2 [Confirmado] Girar a tela, trocar tema escuro ou o sistema matar o processo = volta ao Hub e perde a edição**
`P/MainActivity.kt:198-202`: `screen`, `editingProject`, `selectedLayerId`, `focusThemeId` usam `remember`, não `rememberSaveable`. Com o debounce de 400ms (`:205-213`), o que estava pendente também some.
Correção: `rememberSaveable` para `screen`, `selectedLayerId` e o **id** do projeto em edição (reabrir do store ao recriar, sem colocar o JSON todo no Bundle). Gravar o pendente em `onStop` (`:114-119`) antes de `releaseUiMemory`.

**7A.3 [Confirmado] Sair para a Home durante "Aplicar" corrompe ou cancela o bake e deixa o wallpaper sem nada**
- O bake roda em `scope` da composição (`MainActivity.kt:379`). Activity destruída → cancelado no meio.
- `WallpaperBaker.bakeAll` apaga **todo** `baked_cache` antes de começar (`WallpaperBaker.kt:405-407`). Se falhar ou for cancelado, o wallpaper fica sem bake.
- `onStop` → `releaseUiMemory` (`MainActivity.kt:114-135`) chama `WallpaperBaker.clearMemoryCache`, `SubjectDepthEngine.releaseMemory` (fecha o segmenter **durante** a segmentação) e `GlyphAtlasBaker.clearCache` com o bake ainda rodando.
Correção: bake num `CoroutineScope` de aplicação (ou `WorkManager` expedited com notificação curta); gravar em `baked_cache_tmp/` e trocar com `renameTo` só no sucesso; `releaseUiMemory` não roda enquanto `isBaking`; remover `System.gc()` (`:134`).

**7A.4 [Confirmado] Renderer compartilhado sem trava entre bake e preview**
`LiquidGlassRenderer.clearShaderCachesForHome()` (`:324-340`) zera `sharpBgBmp/sharpBgShader` **sem `bgLock`**, e `getFrostedShader` (`:1193-1205`) usa `sharpBgShader!!` / `sharpBgBmp!!`. O mesmo `renderer` (`MainActivity.kt:159`) é usado pelo bake em `Dispatchers.Default` e pela UI na main. Abrir o Estúdio (`openStudio`, `:331`) ou voltar ao Hub logo depois de "Aplicar" pelo Hub (o diálogo de bake não bloqueia o primeiro frame) → `NullPointerException` ou bake com fundo errado.
Correção: o bake usa **instância própria** de `LiquidGlassRenderer` (criada e liberada dentro de `bakeAll`); `clearShaderCachesForHome` dentro de `synchronized(bgLock)`; trocar `!!` por retorno seguro.

**7A.5 [Confirmado] Cache global de texto não é thread-safe e mistura ambientes**
`P/engine/Dynamic.kt:23-24, 86-87`: `textCache` é `HashMap` + `cacheStamp` num `object`, usado ao mesmo tempo pela UI (preview na main), pelos cards do Hub (`Dispatchers.Default`, horário fixo 18:45) e pelo bake. O check-then-get não é atômico. Resultado: `ConcurrentModificationException`/estado corrompido, e um card do Hub pode mostrar o texto calculado com o `env`/hora do Estúdio (e vice-versa). Além disso, `"$env"` (`toString` do data class) é montado em **toda** chamada de `eval` → alocação por camada por frame.
Correção: cache por instância de renderer (passado como parâmetro) ou `ThreadLocal`; chave do stamp por `env.hashCode()` + minuto, sem `toString`.

**7A.6 [Verificar] Toque no wallpaper pode executar a ação duas vezes**
`P/wallpaper/PrismWallpaperService.kt:368-427`: `onTouchEvent(ACTION_UP)` e `onCommand(COMMAND_TAP)` chamam `handleLayerTap`. A deduplicação (`lastActionTapMs > 180ms`) só existe no `onCommand`. Se o launcher entregar `COMMAND_TAP` **antes** do `ACTION_UP`, a ação roda duas vezes: `TOGGLE_GLOBAL` liga e desliga (parece que "não funciona"), `OPEN_APP` abre duas vezes. Correção: checar `lastActionTapMs` também no `ACTION_UP`, ou tratar só um dos dois caminhos.

**7A.7 [Confirmado] Toque duplo rápido no wallpaper avança vários fundos e enfileira trabalho**
`PrismWallpaperService.kt:385-395` e `onNextWallpaper` (`:457-462`): cada gatilho lança `loadWallpaperAndMask(advance=true)` sem cancelar o anterior. O `sceneMutex` serializa, então 3 toques duplos = 3 avanços + 3 bakes completos de cena. Correção: `nextJob?.cancel(); nextJob = scope.launch {...}` e ignorar gatilho se um avanço estiver em andamento.

**7A.8 [Confirmado] Globais alteradas por toque na tela de bloqueio são desfeitas pelo Estúdio**
`PrismWallpaperService.kt:449-455` → `ProjectStore.save` no processo `:wallpaper` grava só o arquivo (`Data.kt:175`, `!isWallpaperProcess()`). O DataStore da biblioteca mantém o valor antigo; abrir o tema e "Aplicar" sobrescreve. Mesmo defeito do widget (6A.4); a correção de 6A.4 deve cobrir wallpaper **e** widget.

### 7B — RAM: por que passa de 300 MB (estimativas por leitura, confirmar com 5.1)

Referência: 1080×2400 ARGB_8888 ≈ 10,4 MB.

**7B.1 [Confirmado] Dois `LiquidGlassRenderer` vivos ao mesmo tempo, cada um com buffers de GPU próprios**
`MainActivity.kt:159` (principal, tela cheia) e `ThemeHubScreen.kt:248` (cards). Cada renderer pode ter `blurImageReader` **e** `sceneImageReader` (`LiquidGlassRenderer.kt:1116-1135`, `:1213-1240`) com `maxImages = 2` em RGBA_8888, ou seja, até **~42 MB** de buffers no renderer de tela cheia, mais `HardwareRenderer`s e `RenderNode`s. Unificar com a Etapa 3.5 (`OffscreenRenderer` único, `maxImages = 1`, liberado ao sair do Estúdio).

**7B.2 [Confirmado] Estúdio segura 4 bitmaps de fundo em tela cheia + cópias internas do renderer**
`MainActivity.kt:252-300`: `rawBgBitmap`, `rawDepthBitmap`, `bgBitmap`, `depthBitmap` (com pós-processamento 3D são 4 objetos distintos) **+** `sharpBgBmp`/`exactSharp`, `frosted`, `prebakedFrosted` no renderer (`:1166-1212`) **+** `clockLayerBmp` e `lensHeightCache` (3). Facilmente 80–120 MB só de bitmaps. `releaseUiMemory` (`:127-135`) limpa caches mas **não** solta esses `mutableStateOf`. Correção: ao aplicar pós-processamento, soltar `raw*` (recarregar do disco se o slider mudar); em `onStop`, anular os 4 estados e `renderer.release()`.

**7B.3 [Confirmado] O Hub renderiza e guarda prévia de TODOS os temas e widgets**
`ThemeHubScreen.kt:289-370`: loop em `themes + widgets`, sem limite à página visível, cache global `HubPreviewCache.map` (`ConcurrentHashMap` **sem limite**, `:125-127`), mais `previewImages`/`ambientImages`. Para cada tema ainda decodifica o fundo e roda **ML Kit** (`getOrCreateMaskIfEnabled`, `:345`), carregando a biblioteca nativa de segmentação (dezenas de MB de heap nativo) só para mostrar cards. Correção: renderizar apenas `settledPage ± 1` (casa com `beyondViewportPageCount = 1`), `LruCache` por bytes, e **não** segmentar no Hub (usar só máscara já em disco; se não existir, card sem 3D).

**7B.4 [Confirmado] Máscara 3D em baixa resolução reaproveitada no wallpaper**
`DepthAndCarousel.kt:57-92`: o cache de máscara (`memCache` e PNG em `cacheDir/depth_masks`) usa **só o URI** como chave. O Hub gera a máscara a partir de um bitmap de 420 px (`ThemeHubScreen.kt:344-345`); depois o Estúdio e o bake reutilizam essa máscara de 420 px numa tela de 1080 px → recorte serrilhado/borrado. Correção: chave = `uri + largura do source` (ou sempre segmentar na resolução base 1080 e só reduzir na leitura).

**7B.5 [Confirmado] Máscaras 3D no `cacheDir` somem e o wallpaper perde o 3D em silêncio**
`DepthAndCarousel.kt:61` grava em `cacheDir` (o Android limpa sob pouca memória de armazenamento); no processo `:wallpaper` não há segmentação (`:81-83` retorna `null`). Correção: guardar as máscaras usadas por temas aplicados em `filesDir/depth_masks/` (o bake já tem `baked_cache` em `filesDir`, `WallpaperBaker.kt:254`).

**7B.6 [Confirmado] Segmenter do ML Kit é criado e fechado a cada imagem**
`DepthAndCarousel.kt:84-90`: `finally { segmenter.close() }` em toda máscara. No bake de pasta (`WallpaperBaker.kt:421-431`, sem limite de quantidade) o modelo é carregado N vezes. Correção: abrir uma vez por lote, fechar no fim do `bakeAll`; limitar a pasta (ex.: 50 imagens) com aviso.

**7B.7 [Confirmado] Atualização do widget a cada minuto limpa o cache do wallpaper**
`PrismWidgetProvider.kt:~150-200`: a cada `ACTION_TIME_TICK` (`PrismWallpaperService.kt:130-133`) cria um `LiquidGlassRenderer` novo (thread, `HardwareRenderer`), decodifica o wallpaper de referência inteiro (`resolveReferenceBg`) e no `finally` chama `WallpaperCarousel.clearMemoryCache()`. Widget e wallpaper estão no **mesmo processo** `:wallpaper` e no mesmo `object`, então isso apaga o `bmpCache` do wallpaper ativo todo minuto (re-decode ao acordar). Correção: renderer de widget reaproveitado (`object`/`lazy` no processo), cache próprio do fundo do widget, nada de `clearMemoryCache` global; e só re-renderizar se o texto avaliado mudou (comparar hash das strings de `Dynamic.eval`).

**7B.8 [Verificar] Vídeo decodificado na resolução original**
`VideoWallpaperRenderer.kt:316-391`: `MediaPlayer` decodifica na resolução do arquivo (4K/HEVC aloca buffers grandes de decodificador e `SurfaceTexture`) e só depois reduz para 1080. Correção: no import (Etapa 1), se o vídeo for maior que a tela, transcodificar para ≤1080p com `MediaCodec`/`Transformer` (Media3) uma vez, ou avisar.

**7B.9 [Confirmado] Código morto que ainda pesa**
- `WallpaperBaker.kt:252` `memAssetsCache` (mapa de `BakedWallAssets` com 3 bitmaps cheios) nunca recebe `put` → só `clear()` em `:318`. Remover.
- `Dynamic.kt` (`Expr`, `tag`, `{=...}`) e `KustomFormulaEngine.kt` são **dois avaliadores** de expressão com regras diferentes (ver 7C.2). O `AGENTS.md` fala em "avaliador único". Unificar no `KustomFormulaEngine`, mantendo `{TAG}` como açúcar sintático convertido para `$...$`.
- `LayerNode.parentId` (`Model.kt:124`) duplica a árvore `children`. Nos caminhos de adicionar/duplicar (`StudioScreen.kt:1019-1023`, `Model.kt:248-256`) os filhos ficam com `parentId = null`, e o render depende de `nestedChildIds` (`LiquidGlassRenderer.kt:516`). Remover `parentId` (derivar da árvore) ou mantê-lo sempre correto.

### 7C — Lógica de opções, import e fluxos

**7C.1 [Confirmado] "Profundidade 3D" pode ser adicionada várias vezes, dentro de pilha e no widget**
`StudioScreen.kt:1040` + `add()` (`:1019-1027`): sem checagem. Com STACK selecionada, o DEPTH vira filho e **nunca** é desenhado (o loop de pilha filtra DEPTH, `LiquidGlassRenderer.kt:1563`). Dois DEPTH na raiz → o sujeito é desenhado duas vezes no preview, mas `bakeMinuteScene` usa `firstOrNull` (`:949`) para a opacidade. Correção: esconder o chip se já existir DEPTH, se `project.isWidget` ou se a seleção for STACK (adicionar na raiz). Também ao excluir o DEPTH (`:1105-1108`), desligar `project.depth` ou mostrar aviso.

**7C.2 [Confirmado] Globais: `{nome}` avalia fórmulas, `$gv(nome)$` não**
`Dynamic.kt` `tag()` avalia `$...$` dentro do valor da global; `KustomFormulaEngine.kt:671-676` (`evalGv`) devolve o texto cru. A mesma global mostra resultados diferentes conforme a sintaxe. Correção: `evalGv` avalia o valor com limite de profundidade (ex.: 4) para evitar ciclo `a → b → a`.

**7C.3 [Confirmado] Import `.prism`: lixo em disco, fontes sobrescritas, JSON errado escolhido**
`P/data/PrismPackageManager.kt:240-315`:
- Cria `imported_themes/<uuid>/...` **antes** de validar; se o JSON falhar (`:313-315` `?: return null`), a pasta fica órfã com toda a mídia extraída. Apagar `themeDir` em qualquer saída sem sucesso.
- Fontes vão para a pasta **compartilhada** `custom_fonts` com o nome do arquivo (`:277-286`): importar um pacote com `Inter.ttf` **substitui** a fonte que o usuário já tinha, e o cache `tfCache` (`Type.kt:310`, chave `font|weight`) continua servindo a antiga até reiniciar. Usar nome com hash do conteúdo; invalidar `tfCache`/`composeFamilyCache` ao gravar.
- `themeJsonText == null && rel.endsWith(".json")` (`:269-271`) aceita **qualquer** `.json` do zip como tema (ex.: `meta.json`). Restringir a `theme.json`/`widget.json`.
- Sem limite de tamanho: `zis.readBytes()` e `copyTo` sem teto → zip-bomb enche disco/memória. Limitar por entrada (ex.: JSON ≤ 2 MB, mídia ≤ 200 MB) e total.
- Zip-slip: **ok** (usa só o nome base sanitizado). O filtro `".." !in rel` rejeita nomes legítimos como `foto..final.jpg`; trocar por checagem de segmento (`rel.split('/').any { it == ".." }`).

**7C.4 [Confirmado] Import de fonte avulsa deixa arquivo inválido e sobrescreve homônimo**
`Type.kt:265-275` (`FontManager.import`): grava em `fonts/<nome>` e só depois valida (`error("invalid")`) sem apagar o arquivo; mesmo nome substitui fonte existente sem invalidar `tfCache`. Validar num temporário e mover; nome único.

**7C.5 [Confirmado] Vídeo com erro vira wallpaper preto sem aviso**
`VideoWallpaperRenderer.kt:390` `setOnErrorListener { _, _, _ -> true }` engole o erro. Correção: ao erro, cair para o primeiro frame estático (ou cor do tema) e gravar um flag lido pelo Estúdio ("vídeo não suportado neste aparelho").

**7C.6 [Confirmado] Receiver do wallpaper aceita broadcasts de qualquer app**
`PrismWallpaperService.kt:258`: `registerReceiver(receiver, filter, RECEIVER_EXPORTED)` inclui `ACTION_BAKED_READY` e `ACTION_RELOAD_PROJECT`. Qualquer app pode forçar recarga/rebake em loop (bateria). Os broadcasts do sistema (`TIME_TICK`, `SCREEN_*`, `BATTERY_CHANGED`) chegam também com `RECEIVER_NOT_EXPORTED`, e os do próprio app (mesmo UID, outro processo) também. Trocar para `RECEIVER_NOT_EXPORTED`.

**7C.7 [Confirmado] Engine de preview e engine real atualizam o widget em dobro**
`PrismWallpaperService.kt:130-133`: o seletor de wallpaper do sistema cria um engine de preview que também é "current" no seu slot (`:225-230`) e também chama `PrismWidgetProvider.updateAll` a cada minuto. Restringir `updateAll` ao engine `!isPreview`.

**7C.8 [Confirmado] Ordem inconsistente da biblioteca**
`Data.kt:183` (`save`, usado no "Aplicar") insere tema novo no **início** (`add(0, p)`); `upsertTheme` (`:199`) insere no **fim**. Um tema criado e aplicado logo muda de posição no Hub conforme o caminho. Escolher uma regra (ex.: ordem de criação) e usar nas duas.

**7C.9 [Confirmado] Seleção órfã ao excluir pilha**
`StudioScreen.kt:1105-1108`: só limpa a seleção se o id excluído for o selecionado. Excluir uma STACK com um **filho** selecionado deixa `selectedId` apontando para um nó que não existe; as abas ficam sem alvo. Limpar se `project.layers.find(selectedId) == null` após a exclusão.

**7C.10 [Confirmado] Sem desfazer**
Nenhum undo no app (`grep -ri "undo\|desfazer"` vazio). "Substituir por modelo" (`StudioScreen.kt:905-930`), excluir camada e arrastos não têm volta. Adicionar pilha de histórico leve no `MainActivity` (últimos 30 `Project` imutáveis, empilhados em `commit` e no fim de cada arraste), com botão Desfazer/Refazer no topo do Estúdio. Como `Project` é imutável, o custo é só de referência.

**7C.11 [Confirmado] Snap usa tamanho velho após pinça**
`InteractiveLockPreview.kt:290-296`: `boxW/boxH` são lidos no `down`. Pinçar e depois arrastar com um dedo no mesmo gesto usa o tamanho antigo → as linhas-guia de centro/margem ficam fora do lugar. Recalcular a partir de `hitBoxes` quando `pressedCount` voltar a 1.

**Aceite da Etapa 7:** girar a tela no Estúdio mantém a edição; `customAsset` salvo não some; ir para a Home durante "Aplicar" não deixa o wallpaper vazio; abrir o Estúdio logo após aplicar não trava; Hub com 20 temas não carrega ML Kit nem 20 prévias; recorte 3D nítido em 1080p; widget por minuto não esvazia o cache do wallpaper; o Estúdio e a tela de bloqueio mostram a mesma ordem de camadas em relação ao sujeito 3D.

---

## ETAPA 8 — Paralaxe por movimento ("Cena 3D")

> **Pré-requisito de regra:** o `AGENTS.md` (§2) proíbe `SensorManager`. Antes de codar, editar essa linha para: "Sensores só via `MotionSource` (Etapa 8), registrado apenas com o wallpaper visível e o paralaxe ligado; nenhum outro uso de sensores". Sem isso, um agente que segue o `AGENTS.md` vai recusar ou desfazer a etapa.

### 8.0 Conceito de UX (o que o usuário vê)

Em vez de pedir "liga o paralaxe nesta camada e escolha o comportamento" item por item, o app trata a tela como **uma cena com profundidade**. O usuário mexe em **um** controle principal e só desce ao detalhe se quiser.

1. **Um interruptor:** "Movimento 3D" no painel **Fundo & 3D** (`StudioScreen.kt`, seção do projeto em `:3445+`). Ligado, a cena já funciona bem sem mais nenhum ajuste.
2. **Três planos automáticos**, derivados do que já existe (nada a configurar):
   - **Fundo:** a foto. Fica "atrás do vidro" e é o que mais se move.
   - **Itens:** relógio, data, widgets etc. Ficam no plano da tela, **parados por padrão** para continuar legíveis.
   - **Destaque:** o "Sujeito 3D" recortado. Fica "à frente do vidro" e se move um pouco **no sentido contrário** ao fundo. É isso que dá a sensação de que ele salta para fora.
   A ordem de empilhamento continua vindo da lista de camadas (Etapa 4): itens abaixo do "Sujeito 3D" na lista ficam atrás dele, itens acima ficam à frente. **Profundidade (quanto se move) e empilhamento (quem cobre quem) são ideias separadas**, e o usuário só precisa entender a primeira.
3. **"Vista lateral"** (o diferencial de UX): ao ligar o Movimento 3D, aparece um mini-diagrama de perfil com três cartões (Fundo · Itens · Destaque) sobre um eixo "longe ↔ perto", com uma linha marcando "tela". O usuário **arrasta cada cartão** para frente/trás, com snap no "tela" (= parado). Isso substitui sliders abstratos com nomes técnicos ("fator", "multiplicador").
4. **Intensidade única** com 3 chips: *Sutil · Padrão · Intenso* (+ slider fino em "Personalizado"). Ela escala a cena inteira mantendo as proporções entre os planos.
5. **Por camada (opcional, aba Posição & Toque):** uma linha "Movimento" com chips **Seguir plano** (padrão) · **Parado** · **Personalizado**. *Personalizado* adiciona o cartão dessa camada na vista lateral, para ser arrastado como os outros. Não há outra tela de configuração.
6. **Pré-visualização sem precisar entortar o celular:** no Estúdio, com o painel aberto, **arrastar o dedo sobre o preview inclina a cena** (solta → volta ao centro com mola). Um botão pequeno "Usar movimento do celular" liga o sensor real no preview. Assim dá para testar sentado e sem enjoo.
7. **Avançado (recolhido):** "Eixos: ambos / só horizontal / só vertical", "Inverter direção", "Desligar na Tela Inicial".
8. **Acessibilidade e bateria:** se "Remover animações" estiver ativo no sistema (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`) ou com Economia de bateria (`PowerManager.isPowerSaveMode`), o efeito fica pausado. O painel mostra uma legenda com o motivo, em vez de parecer quebrado. Aparelho sem sensor de rotação → o interruptor aparece desativado com "Este aparelho não tem sensor de movimento".

### 8.1 Modelo de dados
`P/model/Model.kt`:
```kotlin
@Serializable enum class MotionAxes { BOTH, HORIZONTAL, VERTICAL }
@Serializable enum class LayerMotion { FOLLOW_PLANE, STILL, CUSTOM }

@Serializable data class MotionScene(
    val enabled: Boolean = false,
    val intensity: Float = 1f,          // 0.4 Sutil · 1.0 Padrão · 1.6 Intenso
    val backgroundDepth: Float = 1f,    // +1 = mais longe (move mais)
    val itemsDepth: Float = 0f,         // 0 = plano da tela (parado)
    val subjectDepth: Float = -0.35f,   // negativo = à frente da tela
    val axes: MotionAxes = MotionAxes.BOTH,
    val invert: Boolean = false,
    val homeScreen: Boolean = true
)
```
- `Project` (perto de `depth*`, `:158-166`): `val motion: MotionScene = MotionScene()`.
- `LayerNode` (`:73+`): `val motion: LayerMotion = LayerMotion.FOLLOW_PLANE`, `val motionDepth: Float = 0f` (usado só em `CUSTOM`).
- Filhos de STACK herdam do nó raiz (o deslocamento é aplicado no nó raiz, nunca em filhos isolados).
- `ignoreUnknownKeys` (`Data.kt:60`) mantém compatibilidade; exportar/importar em `PrismPackageManager` sem mudança extra, porque o campo vai no `theme.json`.

### 8.2 Leitura do movimento: `MotionSource` (novo arquivo `P/wallpaper/MotionSource.kt`)
Robustez e economia vêm daqui.
- **Sensor:** `Sensor.TYPE_GAME_ROTATION_VECTOR`, que funde giroscópio e acelerômetro, não usa magnetômetro (não "puxa" perto de ímã/capinha) e custa pouco. Fallback: `TYPE_ROTATION_VECTOR`; sem os dois → `isSupported = false`. **Não** usar acelerômetro puro (ruidoso) nem giroscópio cru (drift).
- **Taxa:** `registerListener(..., SensorManager.SENSOR_DELAY_GAME, maxReportLatencyUs = 0)` em um `HandlerThread("PrismMotion")` próprio, nunca na main.
- **Ângulos relativos:** em cada `onSensorChanged`, converter o quaternion em matriz (`getRotationMatrixFromVector`) e calcular a rotação **relativa a uma referência** (`getAngleChange`). Saída: `pitch`/`roll` em radianos. Isso é imune a "gimbal lock" com o celular de pé e funciona deitado na cama.
- **Recentrar sozinho (anti-deriva e "qualquer posição é neutra"):** a referência anda lentamente em direção à orientação atual (filtro passa-alta, τ ≈ 1,5 s). O usuário pode deitar de lado e em 1–2 s a cena volta ao centro; só **inclinações** recentes geram movimento. Ao acordar a tela (`onVisibilityChanged(true)`), a referência = orientação atual (sem "pulo" inicial).
- **Anti-cortes / anti-tremor:**
  1. Descartar amostras com `timestamp` fora de ordem ou com intervalo > 100 ms (depois de uma pausa, reiniciar o filtro em vez de saltar).
  2. **One Euro Filter** (`minCutoff ≈ 1.0 Hz`, `beta ≈ 0.02`): suave parado, rápido em movimento rápido, sem atraso perceptível.
  3. **Zona morta** de 0,6° (mão parada não treme a imagem).
  4. **Limite de velocidade** por frame (ex.: 25 % do deslocamento máximo) para engolir picos isolados do sensor.
  5. **Saturação suave**: `offset = max · tanh(ângulo / ângulo_max)` com `ângulo_max ≈ 12°`. Nunca bate no limite de forma seca.
- **Saída:** `StateFlow<Offset>` normalizado em [-1, 1] por eixo, já com `axes`/`invert` aplicados.
- **Ciclo de vida (econômico):** `start()` só quando *wallpaper visível* **e** `motion.enabled` **e** não em economia de bateria **e** animações do sistema ligadas **e** (bloqueio **ou** `homeScreen`). `stop()` em `onVisibilityChanged(false)`, `ACTION_SCREEN_OFF`, `onDestroy` e quando o projeto desliga o efeito. Com o processo de `:wallpaper` em segundo plano, nenhum listener fica registrado.
- **Ocioso = 0 fps:** se o offset filtrado variar < 0,25 px por 500 ms, parar de pedir frames (o listener continua, mas nada é desenhado). Ao voltar a mexer, retomar.

### 8.3 Composição por planos (custo por frame = poucos `drawBitmap`)
Nada é re-renderizado durante o movimento: os planos são **bitmaps prontos** que só são deslocados.
- Usar a separação da Etapa 4 (`bakeMinuteScene`, `LiquidGlassRenderer.kt:833+`):
  - **P0 Fundo:** `sharpBg` com **sobra de borda** (overscan) = `maxShiftPx` de cada lado, para nunca aparecer borda preta. `maxShiftPx = 18dp × intensity × |backgroundDepth|`, recortado na proporção da tela já no bake (`WallpaperBaker`).
  - **P1 Itens atrás do destaque:** `clockLayerBmp` (camadas abaixo do DEPTH na lista).
  - **P2 Destaque:** `depthMask` (o recorte).
  - **P3 Itens à frente:** `overlayLayerBmp` (camadas acima do DEPTH).
  - Camadas `STILL`/`CUSTOM` com profundidade diferente do plano → um bitmap extra por **valor distinto** de profundidade (agrupar camadas com o mesmo valor; limite de 2 grupos extras; acima disso, arredondar para o grupo mais próximo e avisar na vista lateral). Assim o custo não cresce por camada.
- **Desenho por frame** (novo `drawMotionFrame`, ao lado de `drawWakeAnimationFrame`, `:1003`): para cada plano, `canvas.translate(dx · depth, dy · depth)` e `drawBitmap`. Canvas de hardware (`lockHardwareCanvas`, já usado em `PrismWallpaperService.kt:808, 831`). Sem `Path`, sem shader novo, sem alocação no frame.
- **Vidro / Cristal Óptico:** a refração está "assada" no bitmap do plano Itens. Se o fundo se mover e o vidro não, o reflexo fica descolado. Regra: camadas `Fill.GLASS/CRYSTAL` usam como profundidade a do **Fundo** por padrão (movem junto com o que refratam). Se o usuário pedir outra profundidade para uma camada de vidro, mostrar a nota "O vidro pode parecer descolado do fundo" (sem bloquear). Não recalcular refração por frame.
- **Animação de acordar** (`drawWakeAnimationFrame`): somar o offset do movimento ao `wakeOffset` existente (`:1070-1076`) para não brigar uma com a outra.
- **Vídeo:** o fundo de vídeo usa o mesmo deslocamento via matriz do quad (`VideoWallpaperRenderer.updateCenterCropVertices`, `:498`), com overscan no recorte. Como o 3D é desativado em vídeo, só os planos Fundo/Itens existem.
- **Toque:** o hit-test (`findActionableAt`, `PrismWallpaperService.kt:429`) subtrai o deslocamento atual de cada plano antes de testar (senão o botão "foge" do dedo).
- **Widget:** não se aplica. Esconder o painel quando `project.isWidget`.

### 8.4 Loop de frames
Em `PrismWallpaperService`:
- Coletar `MotionSource.offset` e, a cada mudança relevante (> 0,25 px), pedir um frame via `Choreographer.postFrameCallback` (no máximo um pendente). Desenhar no callback com o último offset. Isso segue a taxa nativa da tela sem forçar Hz (regra do AGENTS.md mantida).
- Nada de `ValueAnimator` contínuo. Com o celular parado, o loop para (8.2, ocioso).
- `ACTION_TIME_TICK` continua refazendo só a cena do minuto. O movimento reaproveita a cena pronta.

### 8.5 Estúdio
- Painel "Movimento 3D" dentro de **Fundo & 3D** (`StudioScreen.kt:3445+`, perto dos controles de Bokeh `:3691`): interruptor, chips de intensidade, **vista lateral** (`Canvas` Compose com 3–5 cartões arrastáveis no eixo X; snap em 0 = "tela"; cada cartão com o nome do plano e uma miniatura), seção Avançado recolhida.
- Aba **Posição & Toque** (`:3154`): linha "Movimento" com os chips do item 8.0.5.
- **Preview interativo** (`InteractiveLockPreview.kt`): com o painel aberto, gesto de arrastar fora de camadas = inclinação simulada (mola de volta ao soltar, `Animatable` com `spring`). O desenho do preview reutiliza `drawMotionFrame`, para garantir WYSIWYG com a tela de bloqueio. Botão "Usar movimento do celular" instancia o mesmo `MotionSource` no processo principal, parado ao sair do painel.
- Ao ligar o Movimento 3D sem "Sujeito 3D" na cena, os cartões mostram só Fundo · Itens, com a dica "Ative o recorte 3D em uma foto para separar o destaque".
- Salvar com a regra da Etapa 2 do AGENTS (stage durante arraste, commit ao soltar).

### 8.6 RAM e bateria (orçamento)
- Overscan do fundo: com 18 dp, a área extra é de ~5 %. **Não** guardar um segundo fundo: o bake já gera o fundo maior.
- Planos extras: P3 (já previsto na Etapa 4) + no máximo 2 grupos `CUSTOM`, em `ARGB_8888` só se houver transparência (Itens/Destaque), e o Fundo em `RGB_565` (como o frame pré-composto atual). Liberar tudo quando `motion.enabled = false`.
- Meta: com o celular parado, CPU igual ao estado atual. Em movimento, o custo é 3–5 `drawBitmap` por frame, sem decodificar, desfocar ou segmentar.

### 8.7 Testes (unitários, sem aparelho)
Em `app/src/test/.../MotionFilterTest.kt`, testar a matemática isolada (classe pura `MotionFilter`, sem Android):
- amostra com salto de 40° em 1 frame → saída limitada pelo limite de velocidade;
- 3 s parado em 70° → saída volta a ~0 (recentrar);
- ruído de ±0,4° → saída 0 (zona morta);
- lacuna de 300 ms nos timestamps → filtro reinicia sem pulo;
- `tanh` nunca passa de 1.

**Aceite da Etapa 8:** com o efeito ligado e nenhum outro ajuste, inclinar o celular mostra o fundo se movendo, o relógio parado e o recorte "saltando" para frente. Deitar de lado ou trocar de mão não deixa a cena torta (recentra em ~2 s). Tremor de mão não aparece. Parado, nenhum frame é desenhado. Com a tela apagada, o wallpaper invisível, economia de bateria ligada ou "Remover animações" ativo, nenhum listener de sensor fica registrado. Botões tocáveis continuam acertando com a cena deslocada. O preview do Estúdio se move igual à tela de bloqueio.

---

## Checklist final
- [ ] 1: galeria do sistema abre; mídia persiste após reiniciar
- [ ] 2: só "Peso"; temas antigos migram
- [ ] 4: seletor "Atrás/À frente"; data sobre o sujeito OK
- [ ] 3: blur contínuo no slider
- [ ] 6A: apply com try/finally, save sem corrida, decode seguro, delete do ativo, clima sem São Paulo fantasma
- [ ] 6B: toque por camada no widget, pin só uma vez, binding por instância, relógio do widget, presets só de widget, aspect único, receiver seguro, import `.prism` validado
- [ ] 6C: remover/implementar "Passos", permissões e backup limpos, logs no lugar de `runCatching` mudo, projetos em arquivos individuais
- [ ] 7A: estado sem leitura velha, `rememberSaveable`, bake atômico e fora da UI, renderer do bake isolado, cache de texto thread-safe, toque sem duplicar
- [ ] 7B: um renderer offscreen, Estúdio solta bitmaps, Hub só ±1 página e sem ML Kit, máscara 3D por resolução e em `filesDir`, widget sem limpar cache do wallpaper, código morto removido
- [ ] 7C: um DEPTH na raiz, `$gv$` consistente, import `.prism`/fonte seguro, vídeo com fallback, receivers não exportados, undo
- [ ] 8: AGENTS.md ajustado; `MotionSource` com filtro e recentro; planos Fundo/Itens/Destaque com overscan; vista lateral + intensidade; preview por arraste; 0 fps parado; sensores desligados fora de vista; testes do filtro
- [ ] 5: medições antes/depois + arquivos divididos
- [ ] `gradlew assembleDebug testDebugUnitTest` verde; 1 commit por etapa
