AGENTS.md — PRISMLOCK ENGINE (SYSTEM DIRECTIVES & ARCHITECTURE)


1. PROTOCOLO DE ECONOMIA DE TOKENS E AUTONOMIA

• Zero Conversa Fiada: Proibido escrever saudações, explicações ou resumos. Execute apenas ferramentas (criação/edição de arquivos e terminal).
• Edições Cirúrgicas: Aplique diffs pontuais sem reescrever arquivos saudáveis. Omita comentários óbvios.
• Proibido Placeholders: Nunca escreva // TODO ou funções vazias.
• Autocorreção Silenciosa: Valide sempre com .\gradlew.bat assembleDebug testDebugUnitTest e corrija erros sozinho até compilar.

2. REGRAS DE PERFORMANCE E FLUIDEZ (120FPS SEM TRAVAMENTOS)

• Sensores Controlados: Sensores só via MotionSource (Etapa 8), registrado apenas com o wallpaper visível e o paralaxe ligado; nenhum outro uso de sensores.
• Event-Driven WallpaperService, Processo Isolado :wallpaper & Trava Térmica: 0% de uso de CPU/GPU em repouso. PrismWallpaperService e PrismWidgetProvider rodam isolados em android:process=":wallpaper" (sem carregar Compose ou bibliotecas nativas do ML Kit) mantendo o frame final completo pré-composto em RGB_565 em ACTION_TIME_TICK e ACTION_SCREEN_OFF para despertar em 0ms. Renderize apenas em ACTION_TIME_TICK, ACTION_BATTERY_CHANGED, onVisibilityChanged(true), ACTION_USER_PRESENT, toques na Tela Inicial quando houver camadas HOME_ONLY/ALWAYS e frames de vídeo quando visível (pausa síncrona imediata em onVisibilityChanged(false), ACTION_SCREEN_OFF e Home unlock quando lock-only).
• Resolução Base Proporcional (FHD+): Todos os buffers internos (sharp, frosted, depthMask, lensHeightMap, VideoWallpaperRenderer e preview) operam com largura máxima de 1080px preservando a proporção exata da tela física. Não alterar artificialmente a taxa de atualização (Hz) nativa da tela.
• Editor & Carrossel Hub Sem Engasgos (120Hz):
	• No StudioScreen (Estúdio): barra fixa superior do painel com 3 Pílulas de Contexto do Projeto (Fundo & 3D / Formato & Fundo no modo Widget, Globais ($gv) e Clima & Automação) + 5 Abas da Camada Selecionada (Camadas, Conteúdo, Forma & Fonte, Vidro & Cor, Posição & Toque) e botões calculadora ƒx (FxCalculatorButton / FxExpressionDialog) nos controles principais. Mantenha estados visuais em memória (remember/onStageProject) durante arraste de sliders ou gestos, salvando no DataStore somente em onValueChangeFinished ou debounce. Reuse display list (RenderNode cacheKey), pause prepareFrameAssets durante arraste e só gere lensHeightMap quando houver camada Fill.CRYSTAL/Fill.GLASS (usando delta de Matrix durante drag). Entradas de texto/número usam FocusedInputDialog flutuante na metade superior.
	• No ThemeHubScreen: alterna Telas (Lock) e Widgets (KWGT) com transição monocromática fade + blur (420ms), horário fixo (18:45) e telemetria mock estática (HUB_MOCK_ENV), cache persistente HubPreviewCache (420px + ambient pré-desfocado 72×156 sem blur em tempo real), derivedStateOf para settledPage e animações exclusivamente em graphicsLayer dentro do HorizontalPager (beyondViewportPageCount = 1).
• Cache de Geometria e Shaders: Recalcule o Path esticado (LruCache) e o lensHeightMap APENAS quando o texto, fonte, tamanho ou elasticidade mudar — nunca dentro do loop de desenho. Vídeo usa pipeline hardware zero-copy (MediaPlayer -> OES -> EGL14/GLES20 -> ImageReader HardwareBuffer).

3. MATEMÁTICA INTOCÁVEL DOS MOTORES GRÁFICOS

• Fonte Elástica (StemStretch em Type.kt):
	• Curva vertical C∞ ponderada (alpha = 0.34f) que alivia arcos/barras de topo e base para priorizar o alongamento das hastes verticais.
	• PathMeasure com passo fino (1.0f) sobre Paint.getTextPath + compensação trigonométrica pela normal externa (padrão strokeCompensation = 1.0f, trava anti-estrangulamento máx. baseStrokeWidth * 0.38f).
	• Proteção uniforme por lóbulo horizontal (lobeFactor) + proteção de pontuação pequena (: / .) e deslocamento puro no eixo vertical (xFinal = x), impedindo pontas/dentes côncavos nos terminais.
	• Fontes Samsung (/system/fonts/SamsungSharpSans*, OneUISans*, SEC*) são detectadas em runtime sem empacotar binários proprietários no APK; o app inclui 8 fontes OFL em res/font/ e sincronização SAF via custom_fonts/ com parser OpenType name.
• Vidro e Cristal Óptico (LiquidGlassRenderer):
	• 6 modos de preenchimento: Sólido, Gradiente, Adaptativo, Blur Clássico, Cristal Óptico (AGSL) e Vazado, incluindo camadas LayerType.IMAGE em Blur Clássico e Cristal Óptico usando o canal Alpha da imagem (extractAlpha + DST_IN) como lensHeightMap.
	• No Cristal Óptico: todos os BitmapShader (sharpBg e frostedBg) usam Shader.TileMode.CLAMP e bitmaps opacos/HardwareBuffer SRGB. O jateamento (uFrostAmount) DEVE misturar diretamente sharpBg e frostedBg na leitura refratada (mix(sharpRefracted.rgb, frostedRefracted.rgb, uFrostAmount)), funcionando mesmo com preenchimento 0% opaco.
• Profundidade 3D Seletiva & Pós-Processamento (SubjectDepthEngine + WallpaperBaker):
	• Ativação exclusiva via selo 3D ON/OFF nas miniaturas SAF (ocultando opções de Bokeh/Color Pop quando OFF); ML Kit offline por imagem (depthOverrides) + pré-render no WallpaperBaker com 0% de custo em tempo real.
• Motor Único de Fórmulas, Globais, Ícones & FX (KustomFormulaEngine + Dynamic + Icons):
	• Avaliador único para $df()$, $tf()$, $bi()$, $si()$, $wi()$, $mi()$, $ai()$, $gv()$, $ce()$, $fl()$, $if()$, $tc()$, $mu()$ e tags legadas {HH}/{bat}/{nome}/{=expr}, incluindo Project.globals (COLOR, SWITCH, NUMBER, TEXT, LIST) com herança em STACK e aplicação universal de fxColor/fxOpacity em todos os Fill (inclusive glass.tint e ADAPTIVE).
	• Biblioteca vetorial com 58 ícones nativos em Icons.builtIn + suporte a SVG e PNG/WebP em Project.customAssets conectados via fxAsset em ICON, SVG e IMAGE.
• Ações de Toque por Item (TouchActionExecutor + PositionAndTouchTab):
	• Hit-test preciso por matriz inversa (inclusive subindo para STACK pai) com execução na Tela Inicial via onTouchEvent / COMMAND_TAP + PendingIntent BAL-safe e <queries> compatível com a Play Store; controles e modo teste ficam integrados na aba Posição & Toque.
• Pacotes Portáteis & Diferenciação Widget/Wallpaper (PrismPackageManager):
	• Exporta e importa pacotes .prism (Wallpapers, packageType = "WALLPAPER") e .widget.prism (Widgets, packageType = "WIDGET", isWidget = true, widgetAspect e widget.json opcional), empacotando theme.json/widget.json, bundle.json (v2), assets/, custom_assets/, layer_assets/ e fonts/, além de pastas descompactadas no mesmo padrão, roteando para ProjectStore.upsertWidget ou ProjectStore.upsertTheme conforme isWidget.

4. MOVIMENTO E IDENTIDADE VISUAL

• Referência normativa: PRISMLOCK_IDENTIDADE_MOVIMENTO.md e fonte única de tokens em docs/motion.tokens.json espelhada em ui/PrismMotion.kt.
• Toda nova animação de UI deve consumir PrismMotion (Duration, Easing, Springs, Blur, Scale, Gesture, rubberBand e isReducedMotionEnabled), mantendo 100% das transformações em graphicsLayer/drawWithCache sem alocações por frame.
