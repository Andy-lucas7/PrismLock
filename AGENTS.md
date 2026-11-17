AGENTS.md — PRISMLOCK ENGINE (SYSTEM DIRECTIVES & ARCHITECTURE)


1. PROTOCOLO DE ECONOMIA DE TOKENS E AUTONOMIA

• Zero Conversa Fiada: Proibido escrever saudações, explicações ou resumos. Execute apenas ferramentas (criação/edição de arquivos e terminal).
• Edições Cirúrgicas: Aplique diffs pontuais sem reescrever arquivos saudáveis. Omita comentários óbvios.
• Proibido Placeholders: Nunca escreva // TODO ou funções vazias.
• Autocorreção Silenciosa: Valide sempre com .\gradlew.bat assembleDebug e corrija erros sozinho até compilar.

2. REGRAS DE PERFORMANCE E FLUIDEZ (120FPS SEM TRAVAMENTOS)

• Zero Sensores Físicos: Proibido usar SensorManager, giroscópio ou acelerômetro em qualquer classe.
• Event-Driven WallpaperService & Trava Térmica: 0% de uso de CPU/GPU em repouso. Renderize apenas em ACTION_TIME_TICK, ACTION_BATTERY_CHANGED, onVisibilityChanged(true), ACTION_USER_PRESENT e frames de vídeo quando visível/interativo na lockscreen (pausa síncrona imediata em onVisibilityChanged(false), ACTION_SCREEN_OFF e Home unlock quando lock-only).
• Resolução Base Proporcional (FHD+): Todos os buffers internos (sharp, frosted, depthMask, lensHeightMap, VideoWallpaperRenderer e preview) operam com largura máxima de 1080px preservando a proporção exata da tela física.
• Editor Sem Engasgos & Teclado Livre: No Jetpack Compose, atualize estados visuais apenas em memória (remember) durante arraste de sliders ou gestos, salvando no DataStore somente em onValueChangeFinished ou debounce. Entradas de texto/número usam FocusedInputDialog flutuante na metade superior.
• Cache de Geometria e Shaders: Recalcule o Path esticado (LruCache) e o lensHeightMap APENAS quando o texto, fonte, tamanho ou elasticidade mudar — nunca dentro do loop de desenho. Vídeo usa pipeline hardware zero-copy (MediaPlayer -> OES -> EGL14/GLES20 -> ImageReader HardwareBuffer).

3. MATEMÁTICA INTOCÁVEL DOS MOTORES GRÁFICOS

• Fonte Elástica (StemStretchEngine):
	• Usa PathMeasure com passo fino (1.0f) sobre Paint.getTextPath + compensação trigonométrica pela normal externa: offset = (baseStrokeWidth * 0.5f) * strokeCompensation * (1.0f - 1.0f / sqrt(nx² + (ny * S)²)).
	• Trava Anti-Estrangulamento: offset NUNCA pode ultrapassar baseStrokeWidth * 0.38f, impedindo que traços finos se cruzem ou afinem demais (como visto no 6 e 3 com 1.50x). O padrão strokeCompensation deve ser 1.0f (compensação natural exata).
	• Fontes Samsung (/system/fonts/SamsungSharpSans*, OneUISans*, SEC*) são detectadas em runtime sem empacotar binários proprietários no APK; o app inclui 8 fontes OFL em res/font/ e sincronização SAF via custom_fonts/ com parser OpenType name.
• Vidro e Cristal Óptico (LiquidGlassRenderer):
	• 6 modos de preenchimento: Sólido, Gradiente, Adaptativo, Blur Clássico, Cristal Óptico (AGSL) e Vazado.
	• No Cristal Óptico: todos os BitmapShader (sharpBg e frostedBg) usam Shader.TileMode.CLAMP e bitmaps opacos/HardwareBuffer SRGB. O jateamento (uFrostAmount) DEVE misturar diretamente sharpBg e frostedBg na leitura refratada (mix(sharpRefracted.rgb, frostedRefracted.rgb, uFrostAmount)), funcionando mesmo com preenchimento 0% opaco.
• Profundidade 3D Seletiva & Pós-Processamento (SubjectDepthEngine + WallpaperBaker):
	• ML Kit Subject Segmentation offline por imagem (depthOverrides) + Bokeh de fundo, Color Pop (normal/invertido), Destaque do Sujeito e Vinheta/Halo 3D pré-renderizados no WallpaperBaker para manter 0% de custo em tempo real.
• Motor de Fórmulas (KustomFormulaEngine + Dynamic):
	• Suporte completo a $df(...)$, $bi(...)$, $si(...)$, $wi(...)$, $if(...)$, $tc(...)$ e retrocompatibilidade com {HH}, {bat}, {=expr}.
• Pacotes Portáteis (PrismPackageManager):
	• Exporta e importa pacotes .prism (ZIP contendo theme.json, bundle.json, assets/ e fonts/) e pastas descompactadas no mesmo padrão.
