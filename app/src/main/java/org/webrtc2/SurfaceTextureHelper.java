package org.webrtc2;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.os.Build.VERSION;

import java.nio.ByteBuffer;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class SurfaceTextureHelper {
   private static final String TAG = "SurfaceTextureHelper";
   private final Handler handler;
   private final org.webrtc2.EglBase eglBase;
   private final SurfaceTexture surfaceTexture;
   private final int oesTextureId;
   private org.webrtc2.YuvConverter yuvConverter;
   private OnTextureFrameAvailableListener listener;
   private boolean hasPendingTexture;
   private volatile boolean isTextureInUse;
   private boolean isQuitting;
   private OnTextureFrameAvailableListener pendingListener;
   final Runnable setListenerRunnable;

   public static SurfaceTextureHelper create(final String threadName, final org.webrtc2.EglBase.Context sharedContext) {
      HandlerThread thread = new HandlerThread(threadName);
      thread.start();
      final Handler handler = new Handler(thread.getLooper());
      return (SurfaceTextureHelper)ThreadUtils.invokeAtFrontUninterruptibly(handler, new Callable<SurfaceTextureHelper>() {
         public SurfaceTextureHelper call() {
            try {
               return new SurfaceTextureHelper(sharedContext, handler);
            } catch (RuntimeException var2) {
               RuntimeException e = var2;
               Logging.e("SurfaceTextureHelper", threadName + " create failure", e);
               return null;
            }
         }
      });
   }

   private SurfaceTextureHelper(org.webrtc2.EglBase.Context sharedContext, Handler handler) {
      this.hasPendingTexture = false;
      this.isTextureInUse = false;
      this.isQuitting = false;
      this.setListenerRunnable = new Runnable() {
         public void run() {
            Logging.d("SurfaceTextureHelper", "Setting listener to " + SurfaceTextureHelper.this.pendingListener);
            SurfaceTextureHelper.this.listener = SurfaceTextureHelper.this.pendingListener;
            SurfaceTextureHelper.this.pendingListener = null;
            if (SurfaceTextureHelper.this.hasPendingTexture) {
               SurfaceTextureHelper.this.updateTexImage();
               SurfaceTextureHelper.this.hasPendingTexture = false;
            }

         }
      };
      if (handler.getLooper().getThread() != Thread.currentThread()) {
         throw new IllegalStateException("SurfaceTextureHelper must be created on the handler thread");
      } else {
         this.handler = handler;
         this.eglBase = org.webrtc2.EglBase.create(sharedContext, org.webrtc2.EglBase.CONFIG_PIXEL_BUFFER);

         try {
            this.eglBase.createDummyPbufferSurface();
            this.eglBase.makeCurrent();
         } catch (RuntimeException var4) {
            RuntimeException e = var4;
            this.eglBase.release();
            handler.getLooper().quit();
            throw e;
         }

         this.oesTextureId = GlUtil.generateTexture(36197);
         this.surfaceTexture = new SurfaceTexture(this.oesTextureId);
         this.surfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
               SurfaceTextureHelper.this.hasPendingTexture = true;
               SurfaceTextureHelper.this.tryDeliverTextureFrame();
            }
         });
      }
   }

   public void startListening(OnTextureFrameAvailableListener listener) {
      if (this.listener == null && this.pendingListener == null) {
         this.pendingListener = listener;
         this.handler.post(this.setListenerRunnable);
      } else {
         throw new IllegalStateException("SurfaceTextureHelper listener has already been set.");
      }
   }

   public void stopListening() {
      Logging.d("SurfaceTextureHelper", "stopListening()");
      this.handler.removeCallbacks(this.setListenerRunnable);
      ThreadUtils.invokeAtFrontUninterruptibly(this.handler, new Runnable() {
         public void run() {
            SurfaceTextureHelper.this.listener = null;
            SurfaceTextureHelper.this.pendingListener = null;
         }
      });
   }

   public SurfaceTexture getSurfaceTexture() {
      return this.surfaceTexture;
   }

   public Handler getHandler() {
      return this.handler;
   }

   public void returnTextureFrame() {
      this.handler.post(new Runnable() {
         public void run() {
            SurfaceTextureHelper.this.isTextureInUse = false;
            if (SurfaceTextureHelper.this.isQuitting) {
               SurfaceTextureHelper.this.release();
            } else {
               SurfaceTextureHelper.this.tryDeliverTextureFrame();
            }

         }
      });
   }

   public boolean isTextureInUse() {
      return this.isTextureInUse;
   }

   public void dispose() {
      Logging.d("SurfaceTextureHelper", "dispose()");
      ThreadUtils.invokeAtFrontUninterruptibly(this.handler, new Runnable() {
         public void run() {
            SurfaceTextureHelper.this.isQuitting = true;
            if (!SurfaceTextureHelper.this.isTextureInUse) {
               SurfaceTextureHelper.this.release();
            }

         }
      });
   }

   public void textureToYUV(final ByteBuffer buf, final int width, final int height, final int stride, final int textureId, final float[] transformMatrix) {
      if (textureId != this.oesTextureId) {
         throw new IllegalStateException("textureToByteBuffer called with unexpected textureId");
      } else {
         ThreadUtils.invokeAtFrontUninterruptibly(this.handler, new Runnable() {
            public void run() {
               if (SurfaceTextureHelper.this.yuvConverter == null) {
                  SurfaceTextureHelper.this.yuvConverter = new YuvConverter();
               }

               SurfaceTextureHelper.this.yuvConverter.convert(buf, width, height, stride, textureId, transformMatrix);
            }
         });
      }
   }

   private void updateTexImage() {
      synchronized(org.webrtc2.EglBase.lock) {
         this.surfaceTexture.updateTexImage();
      }
   }

   private void tryDeliverTextureFrame() {
      if (this.handler.getLooper().getThread() != Thread.currentThread()) {
         throw new IllegalStateException("Wrong thread.");
      } else if (!this.isQuitting && this.hasPendingTexture && !this.isTextureInUse && this.listener != null) {
         this.isTextureInUse = true;
         this.hasPendingTexture = false;
         this.updateTexImage();
         float[] transformMatrix = new float[16];
         this.surfaceTexture.getTransformMatrix(transformMatrix);
         long timestampNs = VERSION.SDK_INT >= 14 ? this.surfaceTexture.getTimestamp() : TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
         this.listener.onTextureFrameAvailable(this.oesTextureId, transformMatrix, timestampNs);
      }
   }

   private void release() {
      if (this.handler.getLooper().getThread() != Thread.currentThread()) {
         throw new IllegalStateException("Wrong thread.");
      } else if (!this.isTextureInUse && this.isQuitting) {
         if (this.yuvConverter != null) {
            this.yuvConverter.release();
         }

         GLES20.glDeleteTextures(1, new int[]{this.oesTextureId}, 0);
         this.surfaceTexture.release();
         this.eglBase.release();
         this.handler.getLooper().quit();
      } else {
         throw new IllegalStateException("Unexpected release.");
      }
   }

   // $FF: synthetic method
   SurfaceTextureHelper(EglBase.Context x0, Handler x1, Object x2) {
      this(x0, x1);
   }

   public interface OnTextureFrameAvailableListener {
      void onTextureFrameAvailable(int var1, float[] var2, long var3);
   }
}
