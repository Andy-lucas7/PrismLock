package com.example.prismlock.ui

import android.graphics.RuntimeShader
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons as MIcons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.example.prismlock.engine.FontManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

private const val HERO_BEAM_AGSL = """
    uniform float2 uResolution;
    uniform float2 uConvergeTarget;
    uniform float2 uTitleCenter;
    uniform float uTime;
    uniform float uAmbientAngle;

    // Holofote volumetrico desfocado saindo da origem em direcao ao alvo
    float spotlightCone(float2 uv, float2 origin, float2 aim, float startSigma, float endSigma) {
        float2 seg = aim - origin;
        float lenSq = max(dot(seg, seg), 0.0001);
        float len = sqrt(lenSq);
        float2 dir = seg / len;
        float along = dot(uv - origin, dir);
        float h = clamp(along / len, 0.0, 1.18);
        float2 proj = origin + dir * (h * len);
        float dPerp = length(uv - proj);
        float sigma = mix(startSigma, endSigma, clamp(h, 0.0, 1.0));
        float radialCone = exp(-(dPerp * dPerp) / max(2.0 * sigma * sigma, 0.0005));
        float axialFade = smoothstep(-0.08, 0.22, h) * (0.28 + 0.72 * smoothstep(0.0, 1.0, h));
        float focusPool = exp(-dot(uv - aim, uv - aim) / max(2.2 * endSigma * endSigma, 0.002));
        return radialCone * axialFade * 0.75 + focusPool * 0.55;
    }

    // Holofote orbital passando suavemente atras do titulo apos a revelacao
    float revolvingSpotlight(float2 uvTitle, float angle, float2 axisScale, float sigmaLong, float sigmaCross) {
        float c = cos(angle);
        float s = sin(angle);
        float2 orbPos = float2(c * axisScale.x, s * axisScale.y);
        float2 d = uvTitle - orbPos;
        float2 beamDir = normalize(-orbPos + float2(s * 0.14, -c * 0.14));
        float projAlong = dot(d, beamDir);
        float projCross = d.x * (-beamDir.y) + d.y * beamDir.x;
        float longFalloff = exp(-(projAlong * projAlong) / max(2.0 * sigmaLong * sigmaLong, 0.002));
        float crossFalloff = exp(-(projCross * projCross) / max(2.0 * sigmaCross * sigmaCross, 0.002));
        float halo = exp(-dot(d, d) / max(2.0 * sigmaCross * sigmaCross * 1.8, 0.002));
        return longFalloff * crossFalloff * 0.85 + halo * 0.40;
    }

    half4 main(float2 fragCoord) {
        float minDim = max(min(uResolution.x, uResolution.y), 1.0);
        float2 uv = (fragCoord - uConvergeTarget) / minDim;

        // 1. Quatro holofotes prismáticos desfocados convergindo dos cantos ate (w/2, h*0.42) sem demora excessiva no foco central
        float conv = clamp(uTime / 0.22, 0.0, 1.0);
        float easeConv = conv * conv * (3.0 - 2.0 * conv);
        float uniteGlow = smoothstep(0.15, 0.24, uTime);
        float introFadeOut = 1.0 - smoothstep(0.20, 0.36, uTime);

        float2 c1 = (float2(0.0, 0.0) - uConvergeTarget) / minDim * 1.05;
        float2 c2 = (float2(uResolution.x, 0.0) - uConvergeTarget) / minDim * 1.05;
        float2 c3 = (float2(0.0, uResolution.y) - uConvergeTarget) / minDim * 1.05;
        float2 c4 = (uResolution - uConvergeTarget) / minDim * 1.05;

        float2 aim1 = mix(c1 * 0.42, float2(-0.02, -0.02), easeConv);
        float2 aim2 = mix(c2 * 0.42, float2( 0.03, -0.02), easeConv);
        float2 aim3 = mix(c3 * 0.42, float2(-0.02,  0.03), easeConv);
        float2 aim4 = mix(c4 * 0.42, float2( 0.03,  0.02), easeConv);

        float startSig = mix(0.22, 0.16, easeConv);
        float endSig = mix(0.18, 0.105, easeConv);

        float s1 = spotlightCone(uv, c1, aim1, startSig, endSig);
        float s2 = spotlightCone(uv, c2, aim2, startSig, endSig);
        float s3 = spotlightCone(uv, c3, aim3, startSig, endSig);
        float s4 = spotlightCone(uv, c4, aim4, startSig, endSig);

        float3 introCol = float3(0.0);
        introCol += float3(0.00, 0.88, 1.00) * s1 * 0.56;
        introCol += float3(0.55, 0.36, 0.96) * s2 * 0.56;
        introCol += float3(0.23, 0.51, 0.96) * s3 * 0.56;
        introCol += float3(0.95, 0.25, 0.48) * s4 * 0.56;

        // Brilho central difuso na chegada imediata do P
        float pool = exp(-dot(uv, uv) / 0.045);
        introCol += float3(0.65, 0.82, 1.00) * pool * uniteGlow * 0.40;
        introCol *= introFadeOut;

        // 2. Holofotes desfocados girando continuamente atras do titulo
        float ambientFade = smoothstep(0.30, 0.65, uTime);
        float2 uvTitle = (fragCoord - uTitleCenter) / minDim;

        float r1 = revolvingSpotlight(uvTitle,  uAmbientAngle,               float2(0.32, 0.13), 0.36, 0.16);
        float r2 = revolvingSpotlight(uvTitle, -uAmbientAngle * 0.85 + 2.1,  float2(0.36, 0.15), 0.40, 0.18);
        float r3 = revolvingSpotlight(uvTitle,  uAmbientAngle * 1.10 + 4.2,  float2(0.28, 0.11), 0.34, 0.15);
        float r4 = revolvingSpotlight(uvTitle, -uAmbientAngle * 0.70 + 5.2,  float2(0.38, 0.16), 0.42, 0.19);

        float3 ambientCol = float3(0.0);
        ambientCol += float3(0.00, 0.84, 1.00) * r1 * 0.30;
        ambientCol += float3(0.56, 0.34, 0.96) * r2 * 0.30;
        ambientCol += float3(0.92, 0.24, 0.50) * r3 * 0.26;
        ambientCol += float3(0.22, 0.48, 0.98) * r4 * 0.26;
        ambientCol *= ambientFade;

        float3 total = clamp(introCol + ambientCol, 0.0, 1.0);
        return half4(half3(total), 1.0);
    }
"""

private const val PORTAL_MASK_AGSL = """
    uniform float2 uCenter;
    uniform float uRadius;
    uniform float uFeather;

    half4 main(float2 fragCoord) {
        float dist = distance(fragCoord, uCenter);
        float keepAlpha = smoothstep(uRadius - uFeather, uRadius + uFeather, dist);
        return half4(0.0, 0.0, 0.0, half(keepAlpha));
    }
"""

private fun phaseProgress(t: Float, start: Float, end: Float): Float =
    ((t - start) / (end - start).coerceAtLeast(1e-5f)).coerceIn(0f, 1f)

private fun smoothPhase(t: Float, start: Float, end: Float): Float {
    val x = phaseProgress(t, start, end)
    return x * x * (3f - 2f * x)
}

@Composable
private fun HeroBeamBackground(
    beamShader: RuntimeShader,
    t: () -> Float,
    sweepProg: () -> Float,
    reducedMotion: Boolean
) {
    // State objects only: values are read in the draw phase, so the spin redraws without recomposing.
    val ambientAngle: State<Float>? = if (reducedMotion) {
        null
    } else {
        val ambientTransition = rememberInfiniteTransition(label = "HeroAmbientSpotlights")
        ambientTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6.2831855f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 9000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "AmbientSpotlightAngle"
        )
    }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        beamShader.setFloatUniform("uResolution", w, h)
        beamShader.setFloatUniform("uConvergeTarget", w * 0.5f, h * 0.42f)
        beamShader.setFloatUniform("uTitleCenter", w * 0.5f, lerp(h * 0.42f, h * 0.28f, sweepProg()))
        beamShader.setFloatUniform("uTime", t())
        beamShader.setFloatUniform("uAmbientAngle", ambientAngle?.value ?: 0f)
        drawRect(brush = ShaderBrush(beamShader))
    }
}

@Composable
fun PrismHeroScreen(
    allowReducedMotionSkip: Boolean = true,
    onIntroSettled: () -> Unit = {},
    onEnterComplete: () -> Unit
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    val reducedMotion = remember(ctx) { PrismMotion.isReducedMotionEnabled(ctx) }

    val timeline = remember { Animatable(0f) }
    val revealProgress = remember { Animatable(0f) }
    var isEntering by remember { mutableStateOf(false) }
    var introSettledNotified by remember { mutableStateOf(false) }
    var heroOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var buttonCenterInRoot by remember { mutableStateOf(Offset.Unspecified) }

    val phrases = remember { listOf("LIQUID CRYSTAL", "ELASTIC ENGINE", "WIDGET STUDIO") }
    var phraseIndex by remember { mutableIntStateOf(0) }

    val beamShader = remember { RuntimeShader(HERO_BEAM_AGSL) }
    val portalShader = remember { RuntimeShader(PORTAL_MASK_AGSL) }

    val brandFamily = remember {
        FontManager.init(ctx)
        FontManager.composeFontFamily("res:outfit", 700)
    }

    val baseTextStyle = remember(brandFamily) {
        TextStyle(
            fontFamily = brandFamily,
            fontSize = 54.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp
        )
    }

    val fullLayout = remember(textMeasurer, baseTextStyle) {
        textMeasurer.measure(AnnotatedString("Prismlock"), style = baseTextStyle)
    }
    val pLayout = remember(textMeasurer, baseTextStyle) {
        textMeasurer.measure(AnnotatedString("P"), style = baseTextStyle)
    }

    val fullW = fullLayout.size.width.toFloat().coerceAtLeast(1f)
    val fullH = fullLayout.size.height.toFloat().coerceAtLeast(1f)
    val pW = pLayout.size.width.toFloat().coerceAtLeast(1f)
    val restStartOffsetInFull = remember(fullLayout, pW) {
        if (fullLayout.layoutInput.text.length > 1) {
            fullLayout.getHorizontalPosition(1, usePrimaryDirection = true)
        } else pW
    }

    // Linha do tempo linear de 5.2s (PrismMotion.Duration.INTRO); pula sob reduzir movimento
    LaunchedEffect(Unit) {
        if (allowReducedMotionSkip && reducedMotion) {
            onIntroSettled()
            onEnterComplete()
            return@LaunchedEffect
        }
        timeline.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = PrismMotion.Duration.INTRO, easing = LinearEasing)
        ) {
            if (!introSettledNotified && value >= 0.82f) {
                introSettledNotified = true
                onIntroSettled()
            }
        }
        if (!introSettledNotified) {
            introSettledNotified = true
            onIntroSettled()
        }
    }

    val t = timeline.value
    val pIgnite = smoothPhase(t, 0.13f, 0.23f)
    val slideProg = smoothPhase(t, 0.22f, 0.57f)
    val sweepProg = smoothPhase(t, 0.38f, 0.80f)
    val btnProg = smoothPhase(t, 0.74f, 0.98f)

    val taglineVisible = btnProg > 0.05f
    LaunchedEffect(taglineVisible) {
        if (!taglineVisible) return@LaunchedEffect
        while (true) {
            delay(1850L)
            phraseIndex = (phraseIndex + 1) % phrases.size
        }
    }

    fun launchPortal() {
        if (isEntering) return
        isEntering = true
        if (!introSettledNotified) {
            introSettledNotified = true
            onIntroSettled()
        }
        if (reducedMotion) {
            onEnterComplete()
            return
        }
        var completed = false
        scope.launch {
            revealProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
            ) {
                if (!completed && value >= 0.99f) {
                    completed = true
                    onEnterComplete()
                }
            }
            if (!completed) {
                completed = true
                onEnterComplete()
            }
        }
    }

    val needsPortalOffscreen = isEntering || revealProgress.value > 0.001f
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords -> heroOriginInRoot = coords.positionInRoot() }
            .graphicsLayer {
                compositingStrategy = if (needsPortalOffscreen) {
                    CompositingStrategy.Offscreen
                } else {
                    CompositingStrategy.Auto
                }
            }
            .drawWithContent {
                drawContent()
                val rp = revealProgress.value
                if (rp > 0.001f) {
                    val center = if (buttonCenterInRoot != Offset.Unspecified) {
                        buttonCenterInRoot - heroOriginInRoot
                    } else {
                        Offset(size.width * 0.5f, size.height * 0.80f)
                    }
                    val featherWidth = 120f
                    val maxRadius = hypot(size.width, size.height) * 1.25f
                    val currentRadius = lerp(-featherWidth, maxRadius, rp)
                    portalShader.setFloatUniform("uCenter", center.x, center.y)
                    portalShader.setFloatUniform("uRadius", currentRadius)
                    portalShader.setFloatUniform("uFeather", featherWidth)
                    drawRect(brush = ShaderBrush(portalShader), blendMode = BlendMode.DstIn)
                }
            }
    ) {
        val screenW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val screenH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        val wordScale = lerp(0.92f, 1.0f, sweepProg)
        val titleCenterY = lerp(screenH * 0.42f, screenH * 0.28f, sweepProg)
        val brandTopY = titleCenterY - fullH * 0.5f

        // Pure #000000 backdrop + holofotes desfocados em composable isolado (ambientAngle não recompõe textos)
        HeroBeamBackground(
            beamShader = beamShader,
            t = { timeline.value },
            sweepProg = { smoothPhase(timeline.value, 0.38f, 0.80f) },
            reducedMotion = reducedMotion
        )

        // Exact centered coordinates calculated from TextMeasurer width
        val finalWordLeft = (screenW - fullW) * 0.5f
        val initialPLeft = (screenW - pW) * 0.5f
        val pLeft = lerp(initialPLeft, finalWordLeft, slideProg)

        val slideRightPx = with(density) { 42.dp.toPx() }
        val restTargetLeft = finalWordLeft + restStartOffsetInFull
        val restLeft = lerp(restTargetLeft + slideRightPx, restTargetLeft, slideProg)
        val restBlurDp = (16f * (1f - slideProg)).dp

        val restW = (fullW - restStartOffsetInFull).coerceAtLeast(1f)
        val featherW = restW * 0.65f
        val revealEndX = lerp(0f, restW + featherW * 1.15f, slideProg)
        val revealStartX = revealEndX - featherW

        val restColorToWhiteBrush = remember(revealStartX, revealEndX) {
            Brush.horizontalGradient(
                0.00f to Color.White,
                0.28f to Color(0xFF00E5FF),
                0.56f to Color(0xFF8B5CF6),
                0.80f to Color(0xFFF43F5E),
                1.00f to Color.Transparent,
                startX = revealStartX,
                endX = revealEndX
            )
        }
        val pWhiteShadow = remember(slideProg) {
            Shadow(
                color = Color.White.copy(alpha = (0.85f - 0.45f * slideProg).coerceIn(0.35f, 0.90f)),
                offset = Offset.Zero,
                blurRadius = lerp(38f, 16f, slideProg)
            )
        }
        val restWhiteShadow = remember(slideProg) {
            Shadow(
                color = Color.White.copy(alpha = 0.35f * slideProg),
                offset = Offset.Zero,
                blurRadius = 14f
            )
        }

        val wordCenterYFrac = ((brandTopY + fullH * 0.5f) / screenH).coerceIn(0.05f, 0.95f)
        // Fullscreen unclipped Box holding "P" and "rismlock" with unified scale around word center
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = wordScale
                    scaleY = wordScale
                    transformOrigin = TransformOrigin(0.5f, wordCenterYFrac)
                },
            contentAlignment = Alignment.TopStart
        ) {
            // "P" ignites and shines in Pure White from start -> slides left to finalWordLeft
            Box(
                modifier = Modifier
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .graphicsLayer {
                        translationX = pLeft
                        translationY = brandTopY
                        alpha = pIgnite
                    }
            ) {
                Text(
                    text = "P",
                    style = baseTextStyle.copy(
                        color = Color.White,
                        shadow = pWhiteShadow
                    ),
                    softWrap = false,
                    maxLines = 1
                )
            }

            // "rismlock" enters from right with unbounded Gaussian blur decay + progressive white letter fade
            if (slideProg > 0.005f) {
                val blurMod = if (restBlurDp > 0.25.dp) {
                    Modifier.blur(radius = restBlurDp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                } else {
                    Modifier
                }
                Box(
                    modifier = Modifier
                        .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                        .graphicsLayer {
                            translationX = restLeft
                            translationY = brandTopY
                            alpha = slideProg
                        }
                        .then(blurMod)
                ) {
                    Text(
                        text = "rismlock",
                        style = baseTextStyle.copy(
                            brush = restColorToWhiteBrush,
                            shadow = restWhiteShadow
                        ),
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }
        }

        // Frases centrais alternando uma de cada vez com fade suave no centro da tela
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
                .graphicsLayer { alpha = btnProg },
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = phraseIndex,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                label = "HeroTaglineCrossfade"
            ) { idx ->
                Text(
                    text = phrases[idx % phrases.size],
                    color = Color(0xCCFFFFFF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3.6.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Modern pure white CTA button appearing below (0.75f .. 1.00f)
        val ctaInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 64.dp)
                .graphicsLayer {
                    alpha = btnProg
                    translationY = (1f - btnProg) * 28.dp.toPx()
                }
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    buttonCenterInRoot = Offset(
                        x = pos.x + coords.size.width * 0.5f,
                        y = pos.y + coords.size.height * 0.5f
                    )
                }
                .shadow(22.dp, CircleShape, ambientColor = Color.White, spotColor = Color(0xFF8B5CF6))
                .clip(CircleShape)
                .background(Color.White)
                .clickable(
                    enabled = btnProg > 0.35f && !isEntering,
                    interactionSource = ctaInteraction,
                    indication = null,
                    onClick = ::launchPortal
                )
                .padding(horizontal = 28.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Entrar no PrismLock",
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                )
                Icon(
                    imageVector = MIcons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
