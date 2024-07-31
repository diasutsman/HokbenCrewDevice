package org.webrtc2;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.concurrent.CountDownLatch;

public class SurfaceViewRenderer extends SurfaceView implements SurfaceHolder.Callback, org.webrtc2.VideoRenderer.Callbacks {
   private static final String TAG = "SurfaceViewRenderer";
   private final String resourceName = this.getResourceName();
   private final org.webrtc2.RendererCommon.VideoLayoutMeasure videoLayoutMeasure = new org.webrtc2.RendererCommon.VideoLayoutMeasure();
   private final org.webrtc2.EglRenderer eglRenderer;
   private org.webrtc2.RendererCommon.RendererEvents rendererEvents;
   private final Object layoutLock = new Object();
   private boolean isFirstFrameRendered;
   private int rotatedFrameWidth;
   private int rotatedFrameHeight;
   private int frameRotation;
   private boolean enableFixedSize;
   private int surfaceWidth;
   private int surfaceHeight;

   public SurfaceViewRenderer(Context context) {
      super(context);
      this.eglRenderer = new org.webrtc2.EglRenderer(this.resourceName);
      this.getHolder().addCallback(this);
   }

   public SurfaceViewRenderer(Context context, AttributeSet attrs) {
      super(context, attrs);
      this.eglRenderer = new org.webrtc2.EglRenderer(this.resourceName);
      this.getHolder().addCallback(this);
   }

   public void init(org.webrtc2.EglBase.Context sharedContext, org.webrtc2.RendererCommon.RendererEvents rendererEvents) {
      this.init(sharedContext, rendererEvents, org.webrtc2.EglBase.CONFIG_PLAIN, new GlRectDrawer());
   }

   public void init(EglBase.Context sharedContext, org.webrtc2.RendererCommon.RendererEvents rendererEvents, int[] configAttributes, org.webrtc2.RendererCommon.GlDrawer drawer) {
      ThreadUtils.checkIsOnMainThread();
      this.rendererEvents = rendererEvents;
      synchronized(this.layoutLock) {
         this.rotatedFrameWidth = 0;
         this.rotatedFrameHeight = 0;
         this.frameRotation = 0;
      }

      this.eglRenderer.init(sharedContext, configAttributes, drawer);
   }

   public void release() {
      this.eglRenderer.release();
   }

   public void addFrameListener(org.webrtc2.EglRenderer.FrameListener listener, float scale, org.webrtc2.RendererCommon.GlDrawer drawer) {
      this.eglRenderer.addFrameListener(listener, scale, drawer);
   }

   public void addFrameListener(org.webrtc2.EglRenderer.FrameListener listener, float scale) {
      this.eglRenderer.addFrameListener(listener, scale);
   }

   public void removeFrameListener(EglRenderer.FrameListener listener) {
      this.eglRenderer.removeFrameListener(listener);
   }

   public void setEnableHardwareScaler(boolean enabled) {
      ThreadUtils.checkIsOnMainThread();
      this.enableFixedSize = enabled;
      this.updateSurfaceSize();
   }

   public void setMirror(boolean mirror) {
      this.eglRenderer.setMirror(mirror);
   }

   public void setScalingType(org.webrtc2.RendererCommon.ScalingType scalingType) {
      ThreadUtils.checkIsOnMainThread();
      this.videoLayoutMeasure.setScalingType(scalingType);
   }

   public void setScalingType(org.webrtc2.RendererCommon.ScalingType scalingTypeMatchOrientation, RendererCommon.ScalingType scalingTypeMismatchOrientation) {
      ThreadUtils.checkIsOnMainThread();
      this.videoLayoutMeasure.setScalingType(scalingTypeMatchOrientation, scalingTypeMismatchOrientation);
   }

   public void setFpsReduction(float fps) {
      this.eglRenderer.setFpsReduction(fps);
   }

   public void disableFpsReduction() {
      this.eglRenderer.disableFpsReduction();
   }

   public void pauseVideo() {
      this.eglRenderer.pauseVideo();
   }

   public void renderFrame(org.webrtc2.VideoRenderer.I420Frame frame) {
      this.updateFrameDimensionsAndReportEvents(frame);
      this.eglRenderer.renderFrame(frame);
   }

   protected void onMeasure(int widthSpec, int heightSpec) {
      ThreadUtils.checkIsOnMainThread();
      Point size;
      synchronized(this.layoutLock) {
         size = this.videoLayoutMeasure.measure(widthSpec, heightSpec, this.rotatedFrameWidth, this.rotatedFrameHeight);
      }

      this.setMeasuredDimension(size.x, size.y);
      this.logD("onMeasure(). New size: " + size.x + "x" + size.y);
   }

   protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
      ThreadUtils.checkIsOnMainThread();
      this.eglRenderer.setLayoutAspectRatio((float)(right - left) / (float)(bottom - top));
      this.updateSurfaceSize();
   }

   private void updateSurfaceSize() {
      ThreadUtils.checkIsOnMainThread();
      synchronized(this.layoutLock) {
         if (this.enableFixedSize && this.rotatedFrameWidth != 0 && this.rotatedFrameHeight != 0 && this.getWidth() != 0 && this.getHeight() != 0) {
            float layoutAspectRatio = (float)this.getWidth() / (float)this.getHeight();
            float frameAspectRatio = (float)this.rotatedFrameWidth / (float)this.rotatedFrameHeight;
            int drawnFrameWidth;
            int drawnFrameHeight;
            if (frameAspectRatio > layoutAspectRatio) {
               drawnFrameWidth = (int)((float)this.rotatedFrameHeight * layoutAspectRatio);
               drawnFrameHeight = this.rotatedFrameHeight;
            } else {
               drawnFrameWidth = this.rotatedFrameWidth;
               drawnFrameHeight = (int)((float)this.rotatedFrameWidth / layoutAspectRatio);
            }

            int width = Math.min(this.getWidth(), drawnFrameWidth);
            int height = Math.min(this.getHeight(), drawnFrameHeight);
            this.logD("updateSurfaceSize. Layout size: " + this.getWidth() + "x" + this.getHeight() + ", frame size: " + this.rotatedFrameWidth + "x" + this.rotatedFrameHeight + ", requested surface size: " + width + "x" + height + ", old surface size: " + this.surfaceWidth + "x" + this.surfaceHeight);
            if (width != this.surfaceWidth || height != this.surfaceHeight) {
               this.surfaceWidth = width;
               this.surfaceHeight = height;
               this.getHolder().setFixedSize(width, height);
            }
         } else {
            this.surfaceWidth = this.surfaceHeight = 0;
            this.getHolder().setSizeFromLayout();
         }

      }
   }

   public void surfaceCreated(SurfaceHolder holder) {
      ThreadUtils.checkIsOnMainThread();
      this.eglRenderer.createEglSurface(holder.getSurface());
      this.surfaceWidth = this.surfaceHeight = 0;
      this.updateSurfaceSize();
   }

   public void surfaceDestroyed(SurfaceHolder holder) {
      ThreadUtils.checkIsOnMainThread();
      final CountDownLatch completionLatch = new CountDownLatch(1);
      this.eglRenderer.releaseEglSurface(new Runnable() {
         public void run() {
            completionLatch.countDown();
         }
      });
      ThreadUtils.awaitUninterruptibly(completionLatch);
   }

   public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      ThreadUtils.checkIsOnMainThread();
      this.logD("surfaceChanged: format: " + format + " size: " + width + "x" + height);
   }

   private String getResourceName() {
      try {
         return this.getResources().getResourceEntryName(this.getId()) + ": ";
      } catch (Resources.NotFoundException var2) {
         return "";
      }
   }

   private void updateFrameDimensionsAndReportEvents(VideoRenderer.I420Frame frame) {
      synchronized(this.layoutLock) {
         if (!this.isFirstFrameRendered) {
            this.isFirstFrameRendered = true;
            this.logD("Reporting first rendered frame.");
            if (this.rendererEvents != null) {
               this.rendererEvents.onFirstFrameRendered();
            }
         }

         if (this.rotatedFrameWidth != frame.rotatedWidth() || this.rotatedFrameHeight != frame.rotatedHeight() || this.frameRotation != frame.rotationDegree) {
            this.logD("Reporting frame resolution changed to " + frame.width + "x" + frame.height + " with rotation " + frame.rotationDegree);
            if (this.rendererEvents != null) {
               this.rendererEvents.onFrameResolutionChanged(frame.width, frame.height, frame.rotationDegree);
            }

            this.rotatedFrameWidth = frame.rotatedWidth();
            this.rotatedFrameHeight = frame.rotatedHeight();
            this.frameRotation = frame.rotationDegree;
            this.post(new Runnable() {
               public void run() {
                  SurfaceViewRenderer.this.updateSurfaceSize();
                  SurfaceViewRenderer.this.requestLayout();
               }
            });
         }

      }
   }

   private void logD(String string) {
      Logging.d("SurfaceViewRenderer", this.resourceName + string);
   }
}
