
AGENTS.md — PRISMLOCK ENGINE (SYSTEM DIRECTIVES & ARCHITECTURE)


1. PROTOCOLO DE ECONOMIA DE TOKENS E AUTONOMIA

• Zero Conversa Fiada: Proibido escrever saudações, explicações ou resumos. Execute apenas ferramentas (criação/edição de arquivos e terminal).
• Edições Cirúrgicas: Aplique diffs pontuais sem reescrever arquivos saudáveis. Omita comentários óbvios.
• Proibido Placeholders: Nunca escreva // TODO ou funções vazias.
• Autocorreção Silenciosa: Valide sempre com .\gradlew.bat assembleDebug e corrija erros sozinho até compilar.

2. REGRAS DE PERFORMANCE E FLUIDEZ (120FPS SEM TRAVAMENTOS)

• Zero Sensores Físicos: Proibido usar SensorManager, giroscópio ou acelerômetro em qualquer classe.
• Event-Driven WallpaperService: 0% de uso de CPU/GPU em repouso. Renderize apenas em ACTION_TIME_TICK, onVisibilityChanged(true) (animações puras por tempo de 550ms) e ACTION_USER_PRESENT.
• Editor Sem Engasgos (UI Debounce): No Jetpack Compose, atualize estados visuais apenas em memória (remember) durante o arraste de sliders ou gestos, salvando no DataStore somente em onValueChangeFinished ou debounce(300).
• Cache de Geometria e Shaders: Recalcule o Path esticado (LruCache) e o lensHeightMap APENAS quando o texto, fonte, tamanho ou elasticidade mudar — nunca dentro do loop de desenho (drawFrame). Durante arraste rápido de slider no editor, use throttle de 32ms no recálculo vetorial.

3. MATEMÁTICA INTOCÁVEL DOS MOTORES GRÁFICOS

• Fonte Elástica (StemStretchEngine):
	• Usa PathMeasure com passo fino (1.0f) sobre Paint.getTextPath + compensação trigonométrica pela normal externa: offset = (baseStrokeWidth * 0.5f) * strokeCompensation * (1.0f - 1.0f / sqrt(nx² + (ny * S)²)).
	• Trava Anti-Estrangulamento: offset NUNCA pode ultrapassar baseStrokeWidth * 0.38f, impedindo que traços finos se cruzem ou afinem demais (como visto no 6 e 3 com 1.50x). O padrão strokeCompensation deve ser 1.0f (compensação natural exata).
• Vidro e Cristal Óptico (LiquidGlassRenderer):
	• 5 modos independentes: Sólido, Gradiente, Blur Clássico, Cristal Óptico (AGSL) e Vazado.
	• No Cristal Óptico: todos os BitmapShader (sharpBg e frostedBg) usam Shader.TileMode.CLAMP e bitmaps ARGB_8888 100% opacos (alpha = 1.0). O jateamento (uFrostAmount) DEVE misturar diretamente sharpBg e frostedBg na leitura refratada (mix(sharpRefracted.rgb, frostedRefracted.rgb, uFrostAmount)), funcionando mesmo quando a cor/tint de preenchimento estiver com opacidade 0%.
• Profundidade 3D Seletiva (SubjectDepthEngine):
	• ML Kit Subject Segmentation offline com cache em disco (1080px max), ativado individualmente por imagem via depthEnabledUris no Carrossel SAF.
