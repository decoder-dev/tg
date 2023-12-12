package org.telegram.ui.Components;

import static android.opengl.GLES10.GL_FLOAT;
import static android.opengl.GLES10.GL_TEXTURE0;
import static android.opengl.GLES10.GL_TRIANGLES;
import static android.opengl.GLES20.GL_ARRAY_BUFFER;
import static android.opengl.GLES20.GL_DYNAMIC_DRAW;
import static android.opengl.GLES30.GL_TRANSFORM_FEEDBACK_BUFFER;
import static org.telegram.ui.Components.DustRendererThread.PendingPicture.LIFETIME;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.GLES20;
import android.opengl.GLES31;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

public class DustRendererThread extends Thread {
    public final int MAX_FPS;
    private final double MIN_DELTA;
    private final double MAX_DELTA;
    private final SurfaceTexture surface;
    private boolean needReset = true;
    private int phaseHandle;
    private int timeStepHandle;
    private int particleSizeHandle;
    private int pictureSizeXHandle;
    private int pictureSizeYHandle;
    private int screenSizeXHandle;
    private int screenSizeYHandle;
    private int resetHandle;
    private int pos0Handle;
    private EGL10 egl;
    private EGLDisplay eglDisplay;
    private EGLConfig eglConfig;
    private EGLSurface eglSurface;
    private EGLContext eglContext;
    private int program;
    private int[] textureId = new int[1];
    private final float particleSizeX;
    private final float particleSizeY;
    private int w, h;
    private final Runnable onEmptyCallback;
    private boolean paused = false;
    private boolean running = true;

    public DustRendererThread(SurfaceTexture surface, int w, int h, Runnable onEmptyCallback) {
        this.w = w;
        this.h = h;
        MAX_FPS = (int) AndroidUtilities.screenRefreshRate;
        MIN_DELTA = 1.0 / MAX_FPS;
        MAX_DELTA = MIN_DELTA * 4;
        this.surface = surface;
        particleSizeX = particleSizeY = getParticleSize();
        this.onEmptyCallback = onEmptyCallback;
    }

    private int getParticleSize() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
                return AndroidUtilities.dp(1.2f);
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                return AndroidUtilities.dp(1.5f);
            default:
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                return AndroidUtilities.dp(2f);
        }
    }

    @Override
    public void run() {
        init();
        long lastTime = System.nanoTime();
        while (running) {
            if (needReset) {
                bindBitmap();
                needReset = false;
            }

            final long now = System.nanoTime();
            double dt = (now - lastTime) / 1_000_000_000.;
            lastTime = now;

            if (dt < MIN_DELTA) {
                double wait = MIN_DELTA - dt;
                try {
                    long milli = (long) (wait * 1000L);
                    int nano = (int) ((wait - milli / 1000.) * 1_000_000_000);
                    sleep(milli, nano);
                } catch (Exception ignore) {
                }
                dt = MIN_DELTA;
            } else if (dt > MAX_DELTA) {
                dt = MAX_DELTA;
            }

            while (paused) {
                try {
                    sleep(1000);
                } catch (Exception ignore) {
                }
            }

            drawFrame((float) dt, System.currentTimeMillis());

            if (queue.isEmpty()) {
                if (onEmptyCallback != null) {
                    AndroidUtilities.cancelRunOnUIThread(onEmptyCallback);
                    AndroidUtilities.runOnUIThread(onEmptyCallback);
                }
            }
        }
        die();
    }

    private void drawFrame(float dt, long time) {
        if (!running) return;

        int[] array = new int[1];
        egl.eglQuerySurface(eglDisplay, eglSurface, EGL10.EGL_WIDTH, array);
        int drawnWidth = array[0];
        egl.eglQuerySurface(eglDisplay, eglSurface, EGL10.EGL_HEIGHT, array);
        int drawnHeight = array[0];

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0]);

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glClearColor(0f, 0f, 0f, 0f);

        // Uniforms
        GLES31.glUniform1f(timeStepHandle, dt);
        GLES31.glUniform2f(particleSizeHandle, particleSizeX, particleSizeY);
        GLES31.glUniform1i(screenSizeXHandle, drawnWidth);
        GLES31.glUniform1i(screenSizeYHandle, drawnHeight);

        Iterator<PendingPicture> iterator = queue.iterator();
        while (iterator.hasNext()) {
            PendingPicture pic = iterator.next();
            if (!pic.inited) {
                continue;
            }

            GLES31.glUniform1i(pictureSizeXHandle, pic.columns);
            GLES31.glUniform1i(pictureSizeYHandle, pic.rows);
            GLES31.glUniform2f(pos0Handle, pic.x0, pic.y0);
            GLES31.glUniform1f(phaseHandle, 4f * (time - pic.start) / LIFETIME);
            GLES31.glUniform1f(resetHandle, pic.reset ? 1f : 0f);
            pic.reset = false;

            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, pic.particlesData[pic.currentBuffer]);
            GLES31.glVertexAttribPointer(0, 2, GL_FLOAT, false, 20, 0);
            GLES31.glEnableVertexAttribArray(0);
            GLES31.glVertexAttribPointer(1, 2, GL_FLOAT, false, 20, 8);
            GLES31.glEnableVertexAttribArray(1);
            GLES31.glVertexAttribPointer(2, 1, GL_FLOAT, false, 20, 16);
            GLES31.glEnableVertexAttribArray(2);

            GLES31.glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, pic.particlesData[1 - pic.currentBuffer]);
            GLES31.glVertexAttribPointer(0, 2, GL_FLOAT, false, 20, 0);
            GLES31.glEnableVertexAttribArray(0);
            GLES31.glVertexAttribPointer(1, 2, GL_FLOAT, false, 20, 8);
            GLES31.glEnableVertexAttribArray(1);
            GLES31.glVertexAttribPointer(2, 1, GL_FLOAT, false, 20, 16);
            GLES31.glEnableVertexAttribArray(2);

            GLES31.glBeginTransformFeedback(GLES31.GL_TRIANGLES);
            GLES31.glDrawArrays(GL_TRIANGLES, 0, 6 * pic.columns * pic.rows);
            GLES31.glEndTransformFeedback();
            pic.currentBuffer = 1 - pic.currentBuffer;

            if (pic.onDrawnCallback != null) {
                AndroidUtilities.cancelRunOnUIThread(pic.onDrawnCallback);
                AndroidUtilities.runOnUIThread(pic.onDrawnCallback);
                pic.onDrawnCallback = null;
            }
            if (time - pic.start > LIFETIME) {
                iterator.remove();
                if (pic.particlesData != null) {
                    try {
                        GLES31.glDeleteBuffers(2, pic.particlesData, 0);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    pic.particlesData = null;
                }

                ByteBuffer buffer = ByteBuffer.allocateDirect(pic.w * pic.h * 4).order(ByteOrder.nativeOrder());

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0]);
                GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, pic.x0, pic.y0, pic.w, pic.h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
            }
        }

        egl.eglSwapBuffers(eglDisplay, eglSurface);

        checkGlErrors();
    }

    private void init() {
        egl = (EGL10) EGLContext.getEGL();
        eglDisplay = egl.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        egl.eglInitialize(eglDisplay, new int[2]);

        int[] configAttributes = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_NONE
        };
        EGLConfig[] eglConfigs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        egl.eglChooseConfig(eglDisplay, configAttributes, eglConfigs, 1, numConfigs);
        eglConfig = eglConfigs[0];

        int[] contextAttributes = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE
        };
        eglContext = egl.eglCreateContext(eglDisplay, eglConfig, egl.EGL_NO_CONTEXT, contextAttributes);

        eglSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig, surface, null);

        egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);

        int vertexShader = GLES31.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES31.glShaderSource(vertexShader, RLottieDrawable.readRes(null, R.raw.dust_vertex) + "\n// " + Math.random());
        GLES31.glCompileShader(vertexShader);
        int[] status = new int[1];
        GLES31.glGetShaderiv(vertexShader, GLES31.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            FileLog.e("compile vertex shader error: " + GLES31.glGetShaderInfoLog(vertexShader));
            GLES31.glDeleteShader(vertexShader);
            return;
        }

        int fragmentShader = GLES31.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES31.glShaderSource(fragmentShader, RLottieDrawable.readRes(null, R.raw.dust_fragment) + "\n// " + Math.random());
        GLES31.glCompileShader(fragmentShader);
        GLES31.glGetShaderiv(fragmentShader, GLES31.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            FileLog.e("compile fragment shader error: " + GLES31.glGetShaderInfoLog(fragmentShader));
            GLES31.glDeleteShader(fragmentShader);
            return;
        }

        program = GLES31.glCreateProgram();
        GLES31.glAttachShader(program, vertexShader);
        GLES31.glAttachShader(program, fragmentShader);

        String[] feedbackVaryings = {"out_pPos", "out_pVelocity", "out_pLifetime"};
        GLES31.glTransformFeedbackVaryings(program, feedbackVaryings, GLES31.GL_INTERLEAVED_ATTRIBS);

        GLES31.glLinkProgram(program);
        GLES31.glGetProgramiv(program, GLES31.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            FileLog.e("link draw program error: " + GLES31.glGetProgramInfoLog(program));
            return;
        }

        phaseHandle = GLES31.glGetUniformLocation(program, "phase");
        timeStepHandle = GLES31.glGetUniformLocation(program, "timeStep");
        particleSizeHandle = GLES31.glGetUniformLocation(program, "particleSize");
        pictureSizeXHandle = GLES31.glGetUniformLocation(program, "pictureSizeX");
        pictureSizeYHandle = GLES31.glGetUniformLocation(program, "pictureSizeY");
        screenSizeXHandle = GLES31.glGetUniformLocation(program, "screenSizeX");
        screenSizeYHandle = GLES31.glGetUniformLocation(program, "screenSizeY");
        resetHandle = GLES31.glGetUniformLocation(program, "reset");
        pos0Handle = GLES31.glGetUniformLocation(program, "pos0");

        int[] array = new int[1];
        egl.eglQuerySurface(eglDisplay, eglSurface, EGL10.EGL_WIDTH, array);
        int drawnWidth = array[0];
        egl.eglQuerySurface(eglDisplay, eglSurface, EGL10.EGL_HEIGHT, array);
        int drawnHeight = array[0];

        GLES20.glViewport(0, 0, drawnWidth, drawnHeight);

        textureId = new int[1];
        GLES20.glGenTextures(1, textureId, 0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0]);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        GLES31.glUseProgram(program);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_DITHER);
        GLES20.glDisable(GLES20.GL_STENCIL_TEST);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        checkGlErrors();
    }

    public void pause(boolean paused) {
        this.paused = paused;
    }

    public void halt() {
        running = false;
    }

    static class PendingPicture {
        static final float LIFETIME = 4000f;
        int x0, y0;
        int w, h;
        Bitmap bitmap;
        int columns;
        int rows;
        int[] particlesData = new int[2];
        int currentBuffer;
        long start;
        Runnable onDrawnCallback;
        boolean inited = false;
        boolean reset = true;

        public PendingPicture(int x0, int y0, Bitmap bitmap, Runnable onDrawnCallback) {
            this.x0 = x0;
            this.y0 = y0;
            this.bitmap = bitmap;
            this.onDrawnCallback = onDrawnCallback;
        }
    }

    ConcurrentLinkedQueue<PendingPicture> queue = new ConcurrentLinkedQueue<>();

    public void drawBitmap(Bitmap bitmap, int x, int y, Runnable onDrawnCallback) {
        queue.add(new PendingPicture(x, y, bitmap, onDrawnCallback));
        needReset = true;
    }

    private void bindBitmap() {
        for (PendingPicture pendingPicture : queue) {
            Bitmap bitmap = pendingPicture.bitmap;
            if (bitmap != null) {
                ByteBuffer buffer = ByteBuffer
                        .allocateDirect(bitmap.getWidth() * bitmap.getHeight() * 4)
                        .order(ByteOrder.nativeOrder());

                bitmap.copyPixelsToBuffer(buffer);
                buffer.position(0);

                GLES20.glActiveTexture(GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0]);
                GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, pendingPicture.x0, pendingPicture.y0, bitmap.getWidth(), bitmap.getHeight(), GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);

                pendingPicture.columns = Math.round(bitmap.getWidth() / particleSizeX);
                pendingPicture.rows = Math.round(bitmap.getHeight() / particleSizeY);

                GLES31.glDeleteBuffers(2, pendingPicture.particlesData, 0);
                pendingPicture.particlesData = new int[2];
                GLES31.glGenBuffers(2, pendingPicture.particlesData, 0);
                GLES31.glBindBuffer(GL_ARRAY_BUFFER, pendingPicture.particlesData[0]);
                GLES31.glBufferData(GL_ARRAY_BUFFER, pendingPicture.columns * pendingPicture.rows * 6 * 5 * 4, null, GL_DYNAMIC_DRAW);
                GLES31.glBindBuffer(GL_ARRAY_BUFFER, pendingPicture.particlesData[1]);
                GLES31.glBufferData(GL_ARRAY_BUFFER, pendingPicture.columns * pendingPicture.rows * 6 * 5 * 4, null, GL_DYNAMIC_DRAW);
                checkGlErrors();

                pendingPicture.w = bitmap.getWidth();
                pendingPicture.h = bitmap.getHeight();

                bitmap.recycle();

                pendingPicture.start = System.currentTimeMillis();
                pendingPicture.currentBuffer = 0;
                pendingPicture.bitmap = null;
                pendingPicture.inited = true;
            }
        }
    }

    private void die() {
        GLES20.glDeleteTextures(1, textureId, 0);

        if (program != 0) {
            try {
                GLES31.glDeleteProgram(program);
            } catch (Exception e) {
                FileLog.e(e);
            }
            program = 0;
        }
        if (egl != null) {
            try {
                egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            } catch (Exception e) {
                FileLog.e(e);
            }
            try {
                egl.eglDestroySurface(eglDisplay, eglSurface);
            } catch (Exception e) {
                FileLog.e(e);
            }
            try {
                egl.eglDestroyContext(eglDisplay, eglContext);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        try {
            surface.release();
        } catch (Exception e) {
            FileLog.e(e);
        }

        checkGlErrors();
    }

    private void checkGlErrors() {
        int err;
        while ((err = GLES31.glGetError()) != GLES31.GL_NO_ERROR) {
            FileLog.e("gles error " + err);
        }
    }
}
