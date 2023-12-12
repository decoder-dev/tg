package org.telegram.ui.Components;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

import java.util.concurrent.ConcurrentLinkedQueue;

public class DustEffect {
    /*
    Flag indicating should we keep SurfaceTexture alive on ChatActivity, for faster animations
    or init effect only on message deletion.
    Set it to true for chats with frequent deletions (like chat with fast self-destroyed messages).
    By default it is false for not wasting battery on empty TextureView rendering and empty thread running
     */
    public boolean keepAlive = false;

    private static DustEffect instance;
    private final ConcurrentLinkedQueue<PendingPicture> queue = new ConcurrentLinkedQueue<>();
    private final Object lock = new Object();

    public static DustEffect getInstance(View view) {
        if (view == null) {
            return null;
        }
        if (instance == null) {
            ViewGroup rootView = getRootView(view);
            if (rootView == null) {
                return null;
            }
            instance = new DustEffect(makeTextureViewContainer(rootView));
        }
        return instance;
    }

    private static ViewGroup getRootView(View view) {
        Activity activity = AndroidUtilities.findActivity(view.getContext());
        if (activity == null) {
            return null;
        }
        View rootView = activity.findViewById(android.R.id.content).getRootView();
        if (!(rootView instanceof ViewGroup)) {
            return null;
        }
        return (ViewGroup) rootView;
    }

    public static void pause(boolean pause) {
        if (instance != null && instance.thread != null) {
            instance.thread.pause(pause);
        }
    }

    private static FrameLayout makeTextureViewContainer(ViewGroup rootView) {
        FrameLayout container = new FrameLayout(rootView.getContext());
        rootView.addView(container);
        return container;
    }

    private final ViewGroup textureViewContainer;
    private final TextureView textureView;
    private DustRendererThread thread;
    public boolean destroyed;

    public void destroy() {
        if (destroyed) return;
        destroyed = true;
        instance = null;
        if (thread != null) {
            thread.halt();
            thread = null;
        }
        textureViewContainer.removeView(textureView);
        if (textureViewContainer.getParent() instanceof ViewGroup) {
            ViewGroup rootView = (ViewGroup) textureViewContainer.getParent();
            rootView.removeView(textureViewContainer);
        }
    }

    public DustEffect(ViewGroup container) {
        textureViewContainer = container;
        textureView = new TextureView(textureViewContainer.getContext());

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                synchronized (lock) {
                    if (thread == null) {
                        thread = new DustRendererThread(surface, width, height, () -> {
                            if (!keepAlive) {
                                surface.release();
                                destroy();
                            }
                        });
                        thread.start();
                        for (PendingPicture pic : queue) {
                            thread.drawBitmap(pic.bitmap, pic.x, pic.y, pic.onDrawnCallback);
                        }
                        queue.clear();
                    }
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                if (thread != null) {
                    thread.halt();
                    thread = null;
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

            }
        });

        textureView.setOpaque(false);
        textureViewContainer.addView(textureView);

        textureViewContainer.addView(new View(container.getContext()) {
            final Paint paint = new Paint();

            @Override
            protected void onDraw(Canvas canvas) {
                for (PendingPicture pic : queue) {
                    canvas.drawBitmap(pic.bitmap, pic.x, pic.y, paint);

                    if (pic.onLoadingBitmapDrawnCallback != null) {
                        pic.onLoadingBitmapDrawnCallback.run();
                        pic.onLoadingBitmapDrawnCallback = null;
                    }
                }
            }
        });
    }

    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    static class PendingPicture {
        Bitmap bitmap;
        int x;
        int y;
        Runnable onDrawnCallback;
        Runnable onLoadingBitmapDrawnCallback;

        public PendingPicture(Bitmap b, int x, int y, Runnable onDrawnCallback, Runnable onLoadingBitmapDrawnCallback) {
            this.bitmap = b;
            this.x = x;
            this.y = y;
            this.onDrawnCallback = onDrawnCallback;
            this.onLoadingBitmapDrawnCallback = onLoadingBitmapDrawnCallback;
        }
    }

    public void drawBitmap(Bitmap b, int x, int y, Runnable onDrawnCallback, Runnable onLoadingBitmapDrawnCallback) {
        synchronized (lock) {
            if (thread != null) {
                thread.drawBitmap(b, x, y, onDrawnCallback);
            } else {
                queue.add(new PendingPicture(b, x, y, onDrawnCallback, onLoadingBitmapDrawnCallback));
            }
        }
    }
}
