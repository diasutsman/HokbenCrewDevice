package org.webrtc2;

import android.annotation.SuppressLint;
import android.graphics.Point;
import android.graphics.Rect;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.opengles.GL10;

public class VideoRendererGui implements GLSurfaceView.Renderer {
   private static VideoRendererGui instance = null;
   private static Runnable eglContextReady = null;
   private static final String TAG = "VideoRendererGui";
   private GLSurfaceView surface;
   private static org.webrtc2.EglBase.Context eglContext = null;
   private boolean onSurfaceCreatedCalled;
   private int screenWidth;
   private int screenHeight;
   private final ArrayList<YuvImageRenderer> yuvImageRenderers;
   private static Thread renderFrameThread;
   private static Thread drawThread;

   private VideoRendererGui(GLSurfaceView surface) {
      this.surface = surface;
      surface.setPreserveEGLContextOnPause(true);
      surface.setEGLContextClientVersion(2);
      surface.setRenderer(this);
      surface.setRenderMode(0);
      this.yuvImageRenderers = new ArrayList();
   }

   public static synchronized void setView(GLSurfaceView surface, Runnable eglContextReadyCallback) {
      Logging.d("VideoRendererGui", "VideoRendererGui.setView");
      instance = new VideoRendererGui(surface);
      eglContextReady = eglContextReadyCallback;
   }

   public static synchronized EglBase.Context getEglBaseContext() {
      return eglContext;
   }

   public static synchronized void dispose() {
      if (instance != null) {
         Logging.d("VideoRendererGui", "VideoRendererGui.dispose");
         synchronized(instance.yuvImageRenderers) {
            Iterator i$ = instance.yuvImageRenderers.iterator();

            while(true) {
               if (!i$.hasNext()) {
                  instance.yuvImageRenderers.clear();
                  break;
               }

               YuvImageRenderer yuvImageRenderer = (YuvImageRenderer)i$.next();
               yuvImageRenderer.release();
            }
         }

         renderFrameThread = null;
         drawThread = null;
         instance.surface = null;
         eglContext = null;
         eglContextReady = null;
         instance = null;
      }
   }

   public static org.webrtc2.VideoRenderer createGui(int x, int y, int width, int height, org.webrtc2.RendererCommon.ScalingType scalingType, boolean mirror) throws Exception {
      YuvImageRenderer javaGuiRenderer = create(x, y, width, height, scalingType, mirror);
      return new org.webrtc2.VideoRenderer(javaGuiRenderer);
   }

   public static org.webrtc2.VideoRenderer.Callbacks createGuiRenderer(int x, int y, int width, int height, org.webrtc2.RendererCommon.ScalingType scalingType, boolean mirror) {
      return create(x, y, width, height, scalingType, mirror);
   }

   public static synchronized YuvImageRenderer create(int x, int y, int width, int height, org.webrtc2.RendererCommon.ScalingType scalingType, boolean mirror) {
      return create(x, y, width, height, scalingType, mirror, new GlRectDrawer());
   }

   public static synchronized YuvImageRenderer create(int x, int y, int width, int height, org.webrtc2.RendererCommon.ScalingType scalingType, boolean mirror, org.webrtc2.RendererCommon.GlDrawer drawer) {
      if (x >= 0 && x <= 100 && y >= 0 && y <= 100 && width >= 0 && width <= 100 && height >= 0 && height <= 100 && x + width <= 100 && y + height <= 100) {
         if (instance == null) {
            throw new RuntimeException("Attempt to create yuv renderer before setting GLSurfaceView");
         } else {
            final YuvImageRenderer yuvImageRenderer = new YuvImageRenderer(instance.surface, instance.yuvImageRenderers.size(), x, y, width, height, scalingType, mirror, drawer);
            synchronized(instance.yuvImageRenderers) {
               if (instance.onSurfaceCreatedCalled) {
                  final CountDownLatch countDownLatch = new CountDownLatch(1);
                  instance.surface.queueEvent(new Runnable() {
                     public void run() {
                        yuvImageRenderer.createTextures();
                        yuvImageRenderer.setScreenSize(VideoRendererGui.instance.screenWidth, VideoRendererGui.instance.screenHeight);
                        countDownLatch.countDown();
                     }
                  });

                  try {
                     countDownLatch.await();
                  } catch (InterruptedException var12) {
                     InterruptedException e = var12;
                     throw new RuntimeException(e);
                  }
               }

               instance.yuvImageRenderers.add(yuvImageRenderer);
               return yuvImageRenderer;
            }
         }
      } else {
         throw new RuntimeException("Incorrect window parameters.");
      }
   }

   public static synchronized void update(org.webrtc2.VideoRenderer.Callbacks renderer, int x, int y, int width, int height, org.webrtc2.RendererCommon.ScalingType scalingType, boolean mirror) {
      Logging.d("VideoRendererGui", "VideoRendererGui.update");
      if (instance == null) {
         throw new RuntimeException("Attempt to update yuv renderer before setting GLSurfaceView");
      } else {
         synchronized(instance.yuvImageRenderers) {
            Iterator i$ = instance.yuvImageRenderers.iterator();

            while(i$.hasNext()) {
               YuvImageRenderer yuvImageRenderer = (YuvImageRenderer)i$.next();
               if (yuvImageRenderer == renderer) {
                  yuvImageRenderer.setPosition(x, y, width, height, scalingType, mirror);
               }
            }

         }
      }
   }

   public static synchronized void setRendererEvents(org.webrtc2.VideoRenderer.Callbacks renderer, org.webrtc2.RendererCommon.RendererEvents rendererEvents) {
      Logging.d("VideoRendererGui", "VideoRendererGui.setRendererEvents");
      if (instance == null) {
         throw new RuntimeException("Attempt to set renderer events before setting GLSurfaceView");
      } else {
         synchronized(instance.yuvImageRenderers) {
            Iterator i$ = instance.yuvImageRenderers.iterator();

            while(i$.hasNext()) {
               YuvImageRenderer yuvImageRenderer = (YuvImageRenderer)i$.next();
               if (yuvImageRenderer == renderer) {
                  yuvImageRenderer.rendererEvents = rendererEvents;
               }
            }

         }
      }
   }

   public static synchronized void remove(org.webrtc2.VideoRenderer.Callbacks renderer) {
      Logging.d("VideoRendererGui", "VideoRendererGui.remove");
      if (instance == null) {
         throw new RuntimeException("Attempt to remove renderer before setting GLSurfaceView");
      } else {
         synchronized(instance.yuvImageRenderers) {
            int index = instance.yuvImageRenderers.indexOf(renderer);
            if (index == -1) {
               Logging.w("VideoRendererGui", "Couldn't remove renderer (not present in current list)");
            } else {
               ((YuvImageRenderer)instance.yuvImageRenderers.remove(index)).release();
            }

         }
      }
   }

   public static synchronized void reset(org.webrtc2.VideoRenderer.Callbacks renderer) {
      Logging.d("VideoRendererGui", "VideoRendererGui.reset");
      if (instance == null) {
         throw new RuntimeException("Attempt to reset renderer before setting GLSurfaceView");
      } else {
         synchronized(instance.yuvImageRenderers) {
            Iterator i$ = instance.yuvImageRenderers.iterator();

            while(i$.hasNext()) {
               YuvImageRenderer yuvImageRenderer = (YuvImageRenderer)i$.next();
               if (yuvImageRenderer == renderer) {
                  yuvImageRenderer.reset();
               }
            }

         }
      }
   }

   private static void printStackTrace(Thread thread, String threadName) {
      if (thread != null) {
         StackTraceElement[] stackTraces = thread.getStackTrace();
         if (stackTraces.length > 0) {
            Logging.d("VideoRendererGui", threadName + " stacks trace:");
            StackTraceElement[] arr$ = stackTraces;
            int len$ = arr$.length;

            for(int i$ = 0; i$ < len$; ++i$) {
               StackTraceElement stackTrace = arr$[i$];
               Logging.d("VideoRendererGui", stackTrace.toString());
            }
         }
      }

   }

   public static synchronized void printStackTraces() {
      if (instance != null) {
         printStackTrace(renderFrameThread, "Render frame thread");
         printStackTrace(drawThread, "Draw thread");
      }
   }

   @SuppressLint({"NewApi"})
   public void onSurfaceCreated(GL10 unused, EGLConfig config) {
      Logging.d("VideoRendererGui", "VideoRendererGui.onSurfaceCreated");
      Class var3 = VideoRendererGui.class;
      synchronized(VideoRendererGui.class) {
         if (org.webrtc2.EglBase14.isEGL14Supported()) {
            eglContext = new EglBase14.Context(EGL14.eglGetCurrentContext());
         } else {
            eglContext = new EglBase10.Context(((EGL10)EGLContext.getEGL()).eglGetCurrentContext());
         }

         Logging.d("VideoRendererGui", "VideoRendererGui EGL Context: " + eglContext);
      }

      synchronized(this.yuvImageRenderers) {
         Iterator i$ = this.yuvImageRenderers.iterator();

         while(true) {
            if (!i$.hasNext()) {
               this.onSurfaceCreatedCalled = true;
               break;
            }

            YuvImageRenderer yuvImageRenderer = (YuvImageRenderer)i$.next();
            yuvImageRenderer.createTextures();
         }
      }

      org.webrtc2.GlUtil.checkNoGLES2Error("onSurfaceCreated done");
      GLES20.glPixelStorei(3317, 1);
      GLES20.glClearColor(0.15F, 0.15F, 0.15F, 1.0F);
      var3 = VideoRendererGui.class;
      synchronized(VideoRendererGui.class) {
         if (eglContextReady != null) {
            eglContextReady.run();
         }

      }
   }

   public void onSurfaceChanged(GL10 unused, int width, int height) {
      Logging.d("VideoRendererGui", "VideoRendererGui.onSurfaceChanged: " + width + " x " + height + "  ");
      this.screenWidth = width;
      this.screenHeight = height;
      synchronized(this.yuvImageRenderers) {
         Iterator i$ = this.yuvImageRenderers.iterator();

         while(i$.hasNext()) {
            YuvImageRenderer yuvImageRenderer = (YuvImageRenderer)i$.next();
            yuvImageRenderer.setScreenSize(this.screenWidth, this.screenHeight);
         }

      }
   }

   public void onDrawFrame(GL10 unused) {
      if (drawThread == null) {
         drawThread = Thread.currentThread();
      }

      GLES20.glViewport(0, 0, this.screenWidth, this.screenHeight);
      GLES20.glClear(16384);
      synchronized(this.yuvImageRenderers) {
         Iterator i$ = this.yuvImageRenderers.iterator();

         while(i$.hasNext()) {
            YuvImageRenderer yuvImageRenderer = (YuvImageRenderer)i$.next();
            yuvImageRenderer.draw();
         }

      }
   }

   private static class YuvImageRenderer implements org.webrtc2.VideoRenderer.Callbacks {
      private GLSurfaceView surface;
      private int id;
      private int[] yuvTextures;
      private final org.webrtc2.RendererCommon.YuvUploader yuvUploader;
      private final org.webrtc2.RendererCommon.GlDrawer drawer;
      private GlTextureFrameBuffer textureCopy;
      private org.webrtc2.VideoRenderer.I420Frame pendingFrame;
      private final Object pendingFrameLock;
      private RendererType rendererType;
      private org.webrtc2.RendererCommon.ScalingType scalingType;
      private boolean mirror;
      private org.webrtc2.RendererCommon.RendererEvents rendererEvents;
      boolean seenFrame;
      private int framesReceived;
      private int framesDropped;
      private int framesRendered;
      private long startTimeNs;
      private long drawTimeNs;
      private long copyTimeNs;
      private final Rect layoutInPercentage;
      private final Rect displayLayout;
      private float[] layoutMatrix;
      private boolean updateLayoutProperties;
      private final Object updateLayoutLock;
      private float[] rotatedSamplingMatrix;
      private int screenWidth;
      private int screenHeight;
      private int videoWidth;
      private int videoHeight;
      private int rotationDegree;

      private YuvImageRenderer(GLSurfaceView surface, int id, int x, int y, int width, int height, org.webrtc2.RendererCommon.ScalingType scalingType, boolean mirror, org.webrtc2.RendererCommon.GlDrawer drawer) {
         this.yuvTextures = new int[]{0, 0, 0};
         this.yuvUploader = new org.webrtc2.RendererCommon.YuvUploader();
         this.pendingFrameLock = new Object();
         this.startTimeNs = -1L;
         this.displayLayout = new Rect();
         this.updateLayoutLock = new Object();
         Logging.d("VideoRendererGui", "YuvImageRenderer.Create id: " + id);
         this.surface = surface;
         this.id = id;
         this.scalingType = scalingType;
         this.mirror = mirror;
         this.drawer = drawer;
         this.layoutInPercentage = new Rect(x, y, Math.min(100, x + width), Math.min(100, y + height));
         this.updateLayoutProperties = false;
         this.rotationDegree = 0;
      }

      public synchronized void reset() {
         this.seenFrame = false;
      }

      private synchronized void release() {
         this.surface = null;
         this.drawer.release();
         synchronized(this.pendingFrameLock) {
            if (this.pendingFrame != null) {
               org.webrtc2.VideoRenderer.renderFrameDone(this.pendingFrame);
               this.pendingFrame = null;
            }

         }
      }

      private void createTextures() {
         Logging.d("VideoRendererGui", "  YuvImageRenderer.createTextures " + this.id + " on GL thread:" + Thread.currentThread().getId());

         for(int i = 0; i < 3; ++i) {
            this.yuvTextures[i] = org.webrtc2.GlUtil.generateTexture(3553);
         }

         this.textureCopy = new GlTextureFrameBuffer(6407);
      }

      private void updateLayoutMatrix() {
         synchronized(this.updateLayoutLock) {
            if (this.updateLayoutProperties) {
               this.displayLayout.set((this.screenWidth * this.layoutInPercentage.left + 99) / 100, (this.screenHeight * this.layoutInPercentage.top + 99) / 100, this.screenWidth * this.layoutInPercentage.right / 100, this.screenHeight * this.layoutInPercentage.bottom / 100);
               Logging.d("VideoRendererGui", "ID: " + this.id + ". AdjustTextureCoords. Allowed display size: " + this.displayLayout.width() + " x " + this.displayLayout.height() + ". Video: " + this.videoWidth + " x " + this.videoHeight + ". Rotation: " + this.rotationDegree + ". Mirror: " + this.mirror);
               float videoAspectRatio = this.rotationDegree % 180 == 0 ? (float)this.videoWidth / (float)this.videoHeight : (float)this.videoHeight / (float)this.videoWidth;
               Point displaySize = org.webrtc2.RendererCommon.getDisplaySize(this.scalingType, videoAspectRatio, this.displayLayout.width(), this.displayLayout.height());
               this.displayLayout.inset((this.displayLayout.width() - displaySize.x) / 2, (this.displayLayout.height() - displaySize.y) / 2);
               Logging.d("VideoRendererGui", "  Adjusted display size: " + this.displayLayout.width() + " x " + this.displayLayout.height());
               this.layoutMatrix = org.webrtc2.RendererCommon.getLayoutMatrix(this.mirror, videoAspectRatio, (float)this.displayLayout.width() / (float)this.displayLayout.height());
               this.updateLayoutProperties = false;
               Logging.d("VideoRendererGui", "  AdjustTextureCoords done");
            }
         }
      }

      private void draw() {
         if (this.seenFrame) {
            long now = System.nanoTime();
            boolean isNewFrame;
            synchronized(this.pendingFrameLock) {
               isNewFrame = this.pendingFrame != null;
               if (isNewFrame && this.startTimeNs == -1L) {
                  this.startTimeNs = now;
               }

               if (isNewFrame) {
                  this.rotatedSamplingMatrix = org.webrtc2.RendererCommon.rotateTextureMatrix(this.pendingFrame.samplingMatrix, (float)this.pendingFrame.rotationDegree);
                  if (this.pendingFrame.yuvFrame) {
                     this.rendererType = RendererType.RENDERER_YUV;
                     this.yuvUploader.uploadYuvData(this.yuvTextures, this.pendingFrame.width, this.pendingFrame.height, this.pendingFrame.yuvStrides, this.pendingFrame.yuvPlanes);
                  } else {
                     this.rendererType = RendererType.RENDERER_TEXTURE;
                     this.textureCopy.setSize(this.pendingFrame.rotatedWidth(), this.pendingFrame.rotatedHeight());
                     GLES20.glBindFramebuffer(36160, this.textureCopy.getFrameBufferId());
                     GlUtil.checkNoGLES2Error("glBindFramebuffer");
                     this.drawer.drawOes(this.pendingFrame.textureId, this.rotatedSamplingMatrix, this.textureCopy.getWidth(), this.textureCopy.getHeight(), 0, 0, this.textureCopy.getWidth(), this.textureCopy.getHeight());
                     this.rotatedSamplingMatrix = org.webrtc2.RendererCommon.identityMatrix();
                     GLES20.glBindFramebuffer(36160, 0);
                     GLES20.glFinish();
                  }

                  this.copyTimeNs += System.nanoTime() - now;
                  org.webrtc2.VideoRenderer.renderFrameDone(this.pendingFrame);
                  this.pendingFrame = null;
               }
            }

            this.updateLayoutMatrix();
            float[] texMatrix = org.webrtc2.RendererCommon.multiplyMatrices(this.rotatedSamplingMatrix, this.layoutMatrix);
            int viewportY = this.screenHeight - this.displayLayout.bottom;
            if (this.rendererType == RendererType.RENDERER_YUV) {
               this.drawer.drawYuv(this.yuvTextures, texMatrix, this.videoWidth, this.videoHeight, this.displayLayout.left, viewportY, this.displayLayout.width(), this.displayLayout.height());
            } else {
               this.drawer.drawRgb(this.textureCopy.getTextureId(), texMatrix, this.videoWidth, this.videoHeight, this.displayLayout.left, viewportY, this.displayLayout.width(), this.displayLayout.height());
            }

            if (isNewFrame) {
               ++this.framesRendered;
               this.drawTimeNs += System.nanoTime() - now;
               if (this.framesRendered % 300 == 0) {
                  this.logStatistics();
               }
            }

         }
      }

      private void logStatistics() {
         long timeSinceFirstFrameNs = System.nanoTime() - this.startTimeNs;
         Logging.d("VideoRendererGui", "ID: " + this.id + ". Type: " + this.rendererType + ". Frames received: " + this.framesReceived + ". Dropped: " + this.framesDropped + ". Rendered: " + this.framesRendered);
         if (this.framesReceived > 0 && this.framesRendered > 0) {
            Logging.d("VideoRendererGui", "Duration: " + (int)((double)timeSinceFirstFrameNs / 1000000.0) + " ms. FPS: " + (double)this.framesRendered * 1.0E9 / (double)timeSinceFirstFrameNs);
            Logging.d("VideoRendererGui", "Draw time: " + (int)(this.drawTimeNs / (long)(1000 * this.framesRendered)) + " us. Copy time: " + (int)(this.copyTimeNs / (long)(1000 * this.framesReceived)) + " us");
         }

      }

      public void setScreenSize(int screenWidth, int screenHeight) {
         synchronized(this.updateLayoutLock) {
            if (screenWidth != this.screenWidth || screenHeight != this.screenHeight) {
               Logging.d("VideoRendererGui", "ID: " + this.id + ". YuvImageRenderer.setScreenSize: " + screenWidth + " x " + screenHeight);
               this.screenWidth = screenWidth;
               this.screenHeight = screenHeight;
               this.updateLayoutProperties = true;
            }
         }
      }

      public void setPosition(int x, int y, int width, int height, org.webrtc2.RendererCommon.ScalingType scalingType, boolean mirror) {
         Rect layoutInPercentage = new Rect(x, y, Math.min(100, x + width), Math.min(100, y + height));
         synchronized(this.updateLayoutLock) {
            if (!layoutInPercentage.equals(this.layoutInPercentage) || scalingType != this.scalingType || mirror != this.mirror) {
               Logging.d("VideoRendererGui", "ID: " + this.id + ". YuvImageRenderer.setPosition: (" + x + ", " + y + ") " + width + " x " + height + ". Scaling: " + scalingType + ". Mirror: " + mirror);
               this.layoutInPercentage.set(layoutInPercentage);
               this.scalingType = scalingType;
               this.mirror = mirror;
               this.updateLayoutProperties = true;
            }
         }
      }

      private void setSize(int videoWidth, int videoHeight, int rotation) {
         if (videoWidth != this.videoWidth || videoHeight != this.videoHeight || rotation != this.rotationDegree) {
            if (this.rendererEvents != null) {
               Logging.d("VideoRendererGui", "ID: " + this.id + ". Reporting frame resolution changed to " + videoWidth + " x " + videoHeight);
               this.rendererEvents.onFrameResolutionChanged(videoWidth, videoHeight, rotation);
            }

            synchronized(this.updateLayoutLock) {
               Logging.d("VideoRendererGui", "ID: " + this.id + ". YuvImageRenderer.setSize: " + videoWidth + " x " + videoHeight + " rotation " + rotation);
               this.videoWidth = videoWidth;
               this.videoHeight = videoHeight;
               this.rotationDegree = rotation;
               this.updateLayoutProperties = true;
               Logging.d("VideoRendererGui", "  YuvImageRenderer.setSize done.");
            }
         }
      }

      public synchronized void renderFrame(org.webrtc2.VideoRenderer.I420Frame frame) {
         if (this.surface == null) {
            org.webrtc2.VideoRenderer.renderFrameDone(frame);
         } else {
            if (VideoRendererGui.renderFrameThread == null) {
               VideoRendererGui.renderFrameThread = Thread.currentThread();
            }

            if (!this.seenFrame && this.rendererEvents != null) {
               Logging.d("VideoRendererGui", "ID: " + this.id + ". Reporting first rendered frame.");
               this.rendererEvents.onFirstFrameRendered();
            }

            ++this.framesReceived;
            synchronized(this.pendingFrameLock) {
               if (frame.yuvFrame && (frame.yuvStrides[0] < frame.width || frame.yuvStrides[1] < frame.width / 2 || frame.yuvStrides[2] < frame.width / 2)) {
                  Logging.e("VideoRendererGui", "Incorrect strides " + frame.yuvStrides[0] + ", " + frame.yuvStrides[1] + ", " + frame.yuvStrides[2]);
                  org.webrtc2.VideoRenderer.renderFrameDone(frame);
                  return;
               }

               if (this.pendingFrame != null) {
                  ++this.framesDropped;
                  VideoRenderer.renderFrameDone(frame);
                  this.seenFrame = true;
                  return;
               }

               this.pendingFrame = frame;
            }

            this.setSize(frame.width, frame.height, frame.rotationDegree);
            this.seenFrame = true;
            this.surface.requestRender();
         }
      }

      // $FF: synthetic method
      YuvImageRenderer(GLSurfaceView x0, int x1, int x2, int x3, int x4, int x5, org.webrtc2.RendererCommon.ScalingType x6, boolean x7, RendererCommon.GlDrawer x8, Object x9) {
         this(x0, x1, x2, x3, x4, x5, x6, x7, x8);
      }

      private static enum RendererType {
         RENDERER_YUV,
         RENDERER_TEXTURE;
      }
   }
}
