package com.example.prismlock.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.media.MediaPlayer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * Hardware zero-copy video wallpaper decoder & pre-processor:
 * MediaPlayer -> SurfaceTexture (OES) -> EGL/GLES (CENTER_CROP + dual-pass Gaussian blur)
 * -> GPU HardwareBuffer ImageReaders -> Bitmap.wrapHardwareBuffer -> BitmapShader for AGSL.
 */
class VideoWallpaperRenderer(
    private val context: Context,
    private val onFrameAvailableCallback: () -> Unit
) {
    data class VideoFrame(
        val sharpBitmap: Bitmap,
        val sharpShader: BitmapShader,
        val frostedBitmap: Bitmap,
        val frostedShader: BitmapShader,
        val width: Int,
        val height: Int
    )

    private class BufferSlot(
        val image: Image,
        val hwBuffer: HardwareBuffer,
        val bitmap: Bitmap,
        val shader: BitmapShader
    ) {
        fun close() {
            runCatching { bitmap.recycle() }
            runCatching { hwBuffer.close() }
            runCatching { image.close() }
        }
    }

    private val lock = Any()
    private val frameLock = Any()

    private var glThread: HandlerThread? = null
    private var glHandler: Handler? = null

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var pbufferSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var sharpEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var frostedEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var oesTexId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var decoderSurface: Surface? = null
    private var mediaPlayer: MediaPlayer? = null

    private var sharpReader: ImageReader? = null
    private var frostedReader: ImageReader? = null

    private var fboA = 0
    private var texA = 0
    private var fboB = 0
    private var texB = 0

    private var oesProgram = 0
    private var oesPosLoc = 0
    private var oesTexLoc = 0
    private var oesMatrixLoc = 0

    private var blurProgram = 0
    private var blurPosLoc = 0
    private var blurTexLoc = 0
    private var blurDirLoc = 0
    private var blurTexelLoc = 0

    private val quadVertices: FloatBuffer = ByteBuffer.allocateDirect(16 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(
                floatArrayOf(
                    -1f, -1f, 0f, 0f,
                     1f, -1f, 1f, 0f,
                    -1f,  1f, 0f, 1f,
                     1f,  1f, 1f, 1f
                )
            )
            position(0)
        }
    private val scaledQuad: FloatBuffer = ByteBuffer.allocateDirect(16 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private val stMatrix = FloatArray(16)

    @Volatile private var released = false
    @Volatile private var shouldPlay = false
    @Volatile private var prepared = false
    @Volatile private var currentUri: String? = null
    @Volatile private var targetW = 1080
    @Volatile private var targetH = 2340
    @Volatile private var blurW = 360
    @Volatile private var blurH = 780
    @Volatile private var videoW = 0
    @Volatile private var videoH = 0
    @Volatile private var blurStrength = 1.0f

    private var activeSharp: BufferSlot? = null
    private var staleSharp: BufferSlot? = null
    private var activeFrosted: BufferSlot? = null
    private var staleFrosted: BufferSlot? = null

    private val srgbSpace = ColorSpace.get(ColorSpace.Named.SRGB)

    fun setSource(uriStr: String, baseW: Int, baseH: Int, frostedRadiusPx: Float = 24f) {
        val w = baseW.coerceAtLeast(64)
        val h = baseH.coerceAtLeast(64)
        val normBlur = (frostedRadiusPx / 24f).coerceIn(0.35f, 2.2f)
        synchronized(lock) {
            if (released) return
            blurStrength = normBlur
            if (currentUri == uriStr && targetW == w && targetH == h && mediaPlayer != null) {
                return
            }
            currentUri = uriStr
            targetW = w
            targetH = h
            blurW = (w / 3).coerceIn(120, 420)
            blurH = (h / 3).coerceIn(120, 900)
            ensureThreadLocked()
            glHandler?.post { setupSourceOnGl(uriStr, w, h, blurW, blurH) }
        }
    }

    fun play() {
        synchronized(lock) {
            if (released) return
            shouldPlay = true
            glHandler?.post {
                val mp = mediaPlayer ?: return@post
                if (prepared && !mp.isPlaying) {
                    runCatching {
                        mp.isLooping = true
                        mp.setVolume(0f, 0f)
                        mp.start()
                    }
                }
            }
        }
    }

    fun pause() {
        synchronized(lock) {
            shouldPlay = false
            glHandler?.post {
                val mp = mediaPlayer ?: return@post
                if (prepared && mp.isPlaying) {
                    runCatching { mp.pause() }
                }
            }
        }
    }

    fun pauseAtFirstFrame() {
        synchronized(lock) {
            shouldPlay = false
            glHandler?.post {
                val mp = mediaPlayer ?: return@post
                if (prepared) {
                    runCatching {
                        if (mp.isPlaying) mp.pause()
                        mp.seekTo(0, MediaPlayer.SEEK_CLOSEST_SYNC)
                    }
                }
            }
        }
    }

    fun <T> withCurrentFrame(block: (VideoFrame) -> T): T? {
        synchronized(frameLock) {
            val s = activeSharp ?: return null
            val f = activeFrosted ?: s
            return block(
                VideoFrame(
                    sharpBitmap = s.bitmap,
                    sharpShader = s.shader,
                    frostedBitmap = f.bitmap,
                    frostedShader = f.shader,
                    width = targetW,
                    height = targetH
                )
            )
        }
    }

    fun hasFrame(): Boolean = synchronized(frameLock) { activeSharp != null }

    fun release() {
        val thread: HandlerThread?
        synchronized(lock) {
            if (released) return
            released = true
            shouldPlay = false
            currentUri = null
            thread = glThread
            glHandler?.post {
                teardownSourceOnGl()
                teardownEglOnGl()
            }
        }
        synchronized(frameLock) {
            activeSharp?.close(); activeSharp = null
            staleSharp?.close(); staleSharp = null
            activeFrosted?.close(); activeFrosted = null
            staleFrosted?.close(); staleFrosted = null
        }
        thread?.quitSafely()
        synchronized(lock) {
            glThread = null
            glHandler = null
        }
    }

    private fun ensureThreadLocked() {
        if (glThread != null) return
        val t = HandlerThread("PrismVideoGL").apply { start() }
        glThread = t
        glHandler = Handler(t.looper)
    }

    private fun ensureEglOnGl(): Boolean {
        if (eglDisplay !== EGL14.EGL_NO_DISPLAY && eglContext !== EGL14.EGL_NO_CONTEXT) return true
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display === EGL14.EGL_NO_DISPLAY) return false
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return false

        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT or EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
            return false
        }
        val config = configs[0] ?: return false
        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        val ctx = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        if (ctx === EGL14.EGL_NO_CONTEXT) return false

        val pbufAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        val pbuf = EGL14.eglCreatePbufferSurface(display, config, pbufAttribs, 0)
        if (pbuf === EGL14.EGL_NO_SURFACE) return false
        if (!EGL14.eglMakeCurrent(display, pbuf, pbuf, ctx)) return false

        eglDisplay = display
        eglConfig = config
        eglContext = ctx
        pbufferSurface = pbuf

        initProgramsOnGl()
        return true
    }

    private fun initProgramsOnGl() {
        oesProgram = buildProgram(VS_QUAD, FS_OES)
        oesPosLoc = GLES20.glGetAttribLocation(oesProgram, "aPosition")
        oesTexLoc = GLES20.glGetAttribLocation(oesProgram, "aTexCoord")
        oesMatrixLoc = GLES20.glGetUniformLocation(oesProgram, "uSTMatrix")

        blurProgram = buildProgram(VS_QUAD, FS_BLUR)
        blurPosLoc = GLES20.glGetAttribLocation(blurProgram, "aPosition")
        blurTexLoc = GLES20.glGetAttribLocation(blurProgram, "aTexCoord")
        blurDirLoc = GLES20.glGetUniformLocation(blurProgram, "uDirection")
        blurTexelLoc = GLES20.glGetUniformLocation(blurProgram, "uTexelSize")

        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        oesTexId = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun setupSourceOnGl(uriStr: String, w: Int, h: Int, bw: Int, bh: Int) {
        if (released) return
        if (!ensureEglOnGl()) return
        teardownSourceOnGl()
        EGL14.eglMakeCurrent(eglDisplay, pbufferSurface, pbufferSurface, eglContext)

        val config = eglConfig ?: return
        val winAttribs = intArrayOf(EGL14.EGL_NONE)

        val sReader = ImageReader.newInstance(
            w, h, PixelFormat.RGBA_8888, 5,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        )
        val fReader = ImageReader.newInstance(
            bw, bh, PixelFormat.RGBA_8888, 5,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        )
        sharpReader = sReader
        frostedReader = fReader
        sharpEglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, sReader.surface, winAttribs, 0)
        frostedEglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, fReader.surface, winAttribs, 0)

        createBlurFbosOnGl(bw, bh)

        val st = SurfaceTexture(oesTexId).apply {
            setDefaultBufferSize(w, h)
            setOnFrameAvailableListener({ renderFrameOnGl() }, glHandler)
        }
        val surf = Surface(st)
        surfaceTexture = st
        decoderSurface = surf

        prepared = false
        videoW = 0
        videoH = 0

        val mp = MediaPlayer()
        mediaPlayer = mp
        runCatching {
            mp.setDataSource(context, Uri.parse(uriStr))
            mp.setSurface(surf)
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            mp.setOnVideoSizeChangedListener { _, vw, vh ->
                if (vw > 0 && vh > 0) {
                    videoW = vw
                    videoH = vh
                }
            }
            mp.setOnPreparedListener { player ->
                glHandler?.post {
                    if (released || mediaPlayer !== player) return@post
                    prepared = true
                    videoW = player.videoWidth.coerceAtLeast(1)
                    videoH = player.videoHeight.coerceAtLeast(1)
                    player.setVolume(0f, 0f)
                    player.isLooping = true
                    runCatching {
                        if (shouldPlay) {
                            player.start()
                        } else {
                            // Seek frame 0 and pause cleanly so keyframe renders immediately without playback
                            player.seekTo(0)
                            player.start()
                            glHandler?.postDelayed({
                                if (!shouldPlay && !released && mediaPlayer === player) {
                                    runCatching { if (player.isPlaying) player.pause() }
                                }
                            }, 80L)
                        }
                    }
                }
            }
            mp.setOnErrorListener { _, _, _ -> true }
            mp.prepareAsync()
        }.onFailure {
            teardownSourceOnGl()
        }
    }

    private fun renderFrameOnGl() {
        val st = surfaceTexture ?: return
        val sReader = sharpReader ?: return
        val fReader = frostedReader ?: return
        if (released || eglDisplay === EGL14.EGL_NO_DISPLAY) return
        if (sharpEglSurface === EGL14.EGL_NO_SURFACE || frostedEglSurface === EGL14.EGL_NO_SURFACE) return

        runCatching {
            EGL14.eglMakeCurrent(eglDisplay, pbufferSurface, pbufferSurface, eglContext)
            st.updateTexImage()
            st.getTransformMatrix(stMatrix)
        }.onFailure { return }

        updateCenterCropVertices(targetW, targetH, videoW, videoH)

        // Pass 1: sharp OES -> sharpEglSurface (full baseW x baseH)
        EGL14.eglMakeCurrent(eglDisplay, sharpEglSurface, sharpEglSurface, eglContext)
        GLES20.glViewport(0, 0, targetW, targetH)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        drawQuadOes(scaledQuad, stMatrix)
        EGLExt.eglPresentationTimeANDROID(eglDisplay, sharpEglSurface, System.nanoTime())
        EGL14.eglSwapBuffers(eglDisplay, sharpEglSurface)

        // Pass 2: downscale OES -> FBO_A -> H-Blur FBO_B -> V-Blur frostedEglSurface
        EGL14.eglMakeCurrent(eglDisplay, pbufferSurface, pbufferSurface, eglContext)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboA)
        GLES20.glViewport(0, 0, blurW, blurH)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        drawQuadOes(scaledQuad, stMatrix)

        val step = blurStrength * 1.65f
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboB)
        GLES20.glViewport(0, 0, blurW, blurH)
        drawQuadBlur(texA, step / blurW.toFloat(), 0f)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        EGL14.eglMakeCurrent(eglDisplay, frostedEglSurface, frostedEglSurface, eglContext)
        GLES20.glViewport(0, 0, blurW, blurH)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        drawQuadBlur(texB, 0f, step / blurH.toFloat())
        EGLExt.eglPresentationTimeANDROID(eglDisplay, frostedEglSurface, System.nanoTime())
        EGL14.eglSwapBuffers(eglDisplay, frostedEglSurface)

        val newSharpImg = sReader.acquireLatestImage()
        val newFrostedImg = fReader.acquireLatestImage()
        if (newSharpImg == null) {
            newFrostedImg?.close()
            return
        }

        val sharpSlot = wrapSlot(newSharpImg, scaleX = 1f, scaleY = 1f)
        val frostedSlot = newFrostedImg?.let {
            wrapSlot(
                it,
                scaleX = targetW.toFloat() / blurW.toFloat(),
                scaleY = targetH.toFloat() / blurH.toFloat()
            )
        }

        if (sharpSlot == null) {
            newSharpImg.close()
            frostedSlot?.close()
            return
        }

        var dropSharp: BufferSlot? = null
        var dropFrosted: BufferSlot? = null
        synchronized(frameLock) {
            dropSharp = staleSharp
            staleSharp = activeSharp
            activeSharp = sharpSlot

            if (frostedSlot != null) {
                dropFrosted = staleFrosted
                staleFrosted = activeFrosted
                activeFrosted = frostedSlot
            }
        }
        dropSharp?.close()
        dropFrosted?.close()

        onFrameAvailableCallback()
    }

    private fun wrapSlot(image: Image, scaleX: Float, scaleY: Float): BufferSlot? {
        val buf = image.hardwareBuffer ?: return null
        val bmp = Bitmap.wrapHardwareBuffer(buf, srgbSpace) ?: run {
            buf.close()
            return null
        }
        val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        if (scaleX != 1f || scaleY != 1f) {
            val m = Matrix().apply { setScale(scaleX, scaleY) }
            shader.setLocalMatrix(m)
        }
        return BufferSlot(image, buf, bmp, shader)
    }

    private fun updateCenterCropVertices(outW: Int, outH: Int, vidW: Int, vidH: Int) {
        val vw = vidW.coerceAtLeast(1).toFloat()
        val vh = vidH.coerceAtLeast(1).toFloat()
        val outAspect = outW.coerceAtLeast(1).toFloat() / outH.coerceAtLeast(1).toFloat()
        val vidAspect = vw / vh
        val sx: Float
        val sy: Float
        if (vidW <= 0 || vidH <= 0) {
            sx = 1f; sy = 1f
        } else if (vidAspect > outAspect) {
            sx = (vidAspect / outAspect).coerceAtLeast(1f)
            sy = 1f
        } else {
            sx = 1f
            sy = (outAspect / vidAspect).coerceAtLeast(1f)
        }
        scaledQuad.clear()
        scaledQuad.put(
            floatArrayOf(
                -sx, -sy, 0f, 0f,
                 sx, -sy, 1f, 0f,
                -sx,  sy, 0f, 1f,
                 sx,  sy, 1f, 1f
            )
        )
        scaledQuad.position(0)
    }

    private fun drawQuadOes(vertices: FloatBuffer, matrix: FloatArray) {
        GLES20.glUseProgram(oesProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
        GLES20.glUniformMatrix4fv(oesMatrixLoc, 1, false, matrix, 0)

        vertices.position(0)
        GLES20.glEnableVertexAttribArray(oesPosLoc)
        GLES20.glVertexAttribPointer(oesPosLoc, 2, GLES20.GL_FLOAT, false, 16, vertices)
        vertices.position(2)
        GLES20.glEnableVertexAttribArray(oesTexLoc)
        GLES20.glVertexAttribPointer(oesTexLoc, 2, GLES20.GL_FLOAT, false, 16, vertices)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(oesPosLoc)
        GLES20.glDisableVertexAttribArray(oesTexLoc)
    }

    private fun drawQuadBlur(texId: Int, dx: Float, dy: Float) {
        GLES20.glUseProgram(blurProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform2f(blurDirLoc, if (dx != 0f) 1f else 0f, if (dy != 0f) 1f else 0f)
        GLES20.glUniform2f(blurTexelLoc, maxOf(dx, dy), 0f)

        quadVertices.position(0)
        GLES20.glEnableVertexAttribArray(blurPosLoc)
        GLES20.glVertexAttribPointer(blurPosLoc, 2, GLES20.GL_FLOAT, false, 16, quadVertices)
        quadVertices.position(2)
        GLES20.glEnableVertexAttribArray(blurTexLoc)
        GLES20.glVertexAttribPointer(blurTexLoc, 2, GLES20.GL_FLOAT, false, 16, quadVertices)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(blurPosLoc)
        GLES20.glDisableVertexAttribArray(blurTexLoc)
    }

    private fun createBlurFbosOnGl(w: Int, h: Int) {
        deleteBlurFbosOnGl()
        val fbos = IntArray(2)
        val texs = IntArray(2)
        GLES20.glGenFramebuffers(2, fbos, 0)
        GLES20.glGenTextures(2, texs, 0)
        fboA = fbos[0]; texA = texs[0]
        fboB = fbos[1]; texB = texs[1]

        for (i in 0 until 2) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texs[i])
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbos[i])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texs[i], 0)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun deleteBlurFbosOnGl() {
        val fbos = intArrayOf(fboA, fboB).filter { it != 0 }.toIntArray()
        if (fbos.isNotEmpty()) GLES20.glDeleteFramebuffers(fbos.size, fbos, 0)
        val texs = intArrayOf(texA, texB).filter { it != 0 }.toIntArray()
        if (texs.isNotEmpty()) GLES20.glDeleteTextures(texs.size, texs, 0)
        fboA = 0; texA = 0; fboB = 0; texB = 0
    }

    private fun teardownSourceOnGl() {
        prepared = false
        runCatching { surfaceTexture?.setOnFrameAvailableListener(null) }
        runCatching {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        }
        mediaPlayer = null
        runCatching { decoderSurface?.release() }
        decoderSurface = null
        runCatching { surfaceTexture?.release() }
        surfaceTexture = null

        if (eglDisplay !== EGL14.EGL_NO_DISPLAY && pbufferSurface !== EGL14.EGL_NO_SURFACE) {
            runCatching { EGL14.eglMakeCurrent(eglDisplay, pbufferSurface, pbufferSurface, eglContext) }
        }
        if (sharpEglSurface !== EGL14.EGL_NO_SURFACE) {
            runCatching { EGL14.eglDestroySurface(eglDisplay, sharpEglSurface) }
            sharpEglSurface = EGL14.EGL_NO_SURFACE
        }
        if (frostedEglSurface !== EGL14.EGL_NO_SURFACE) {
            runCatching { EGL14.eglDestroySurface(eglDisplay, frostedEglSurface) }
            frostedEglSurface = EGL14.EGL_NO_SURFACE
        }
        runCatching { sharpReader?.close() }
        sharpReader = null
        runCatching { frostedReader?.close() }
        frostedReader = null
        deleteBlurFbosOnGl()
    }

    private fun teardownEglOnGl() {
        if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
            runCatching {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            }
            if (oesTexId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(oesTexId), 0)
                oesTexId = 0
            }
            if (oesProgram != 0) { GLES20.glDeleteProgram(oesProgram); oesProgram = 0 }
            if (blurProgram != 0) { GLES20.glDeleteProgram(blurProgram); blurProgram = 0 }
            if (pbufferSurface !== EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, pbufferSurface)
                pbufferSurface = EGL14.EGL_NO_SURFACE
            }
            if (eglContext !== EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compileShader(GLES20.GL_VERTEX_SHADER, vs)
        val f = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
        return GLES20.glCreateProgram().also { p ->
            GLES20.glAttachShader(p, v)
            GLES20.glAttachShader(p, f)
            GLES20.glLinkProgram(p)
            GLES20.glDeleteShader(v)
            GLES20.glDeleteShader(f)
        }
    }

    private fun compileShader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        return s
    }

    companion object {
        private const val VS_QUAD = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vTexCoord = aTexCoord;
            }
        """

        private const val FS_OES = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            uniform mat4 uSTMatrix;
            void main() {
                vec2 uv = (uSTMatrix * vec4(vTexCoord, 0.0, 1.0)).xy;
                gl_FragColor = vec4(texture2D(sTexture, uv).rgb, 1.0);
            }
        """

        private const val FS_BLUR = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            uniform vec2 uDirection;
            uniform vec2 uTexelSize;
            void main() {
                vec2 stepUv = uDirection * uTexelSize.x;
                vec3 sum = texture2D(sTexture, vTexCoord).rgb * 0.227027;
                sum += (texture2D(sTexture, vTexCoord + stepUv * 1.384615).rgb +
                        texture2D(sTexture, vTexCoord - stepUv * 1.384615).rgb) * 0.316216;
                sum += (texture2D(sTexture, vTexCoord + stepUv * 3.230769).rgb +
                        texture2D(sTexture, vTexCoord - stepUv * 3.230769).rgb) * 0.070270;
                gl_FragColor = vec4(sum, 1.0);
            }
        """
    }
}
