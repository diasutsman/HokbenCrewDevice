package org.webrtc2;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.view.Surface;

@TargetApi(21)
public class ScreenCapturerAndroid implements VideoCapturer, org.webrtc2.SurfaceTextureHelper.OnTextureFrameAvailableListener {
   private static final int DISPLAY_FLAGS = 3;
   private static final int VIRTUAL_DISPLAY_DPI = 400;
   private final Intent mediaProjectionPermissionResultData;
   private final MediaProjection.Callback mediaProjectionCallback;
   private int width;
   private int height;
   private VirtualDisplay virtualDisplay;
   private org.webrtc2.SurfaceTextureHelper surfaceTextureHelper;
   private CapturerObserver capturerObserver;
   private long numCapturedFrames = 0L;
   private MediaProjection mediaProjection;
   private boolean isDisposed = false;
   private MediaProjectionManager mediaProjectionManager;

   public ScreenCapturerAndroid(Intent mediaProjectionPermissionResultData, MediaProjection.Callback mediaProjectionCallback) {
      this.mediaProjectionPermissionResultData = mediaProjectionPermissionResultData;
      this.mediaProjectionCallback = mediaProjectionCallback;
   }

   private void checkNotDisposed() {
      if (this.isDisposed) {
         throw new RuntimeException("capturer is disposed.");
      }
   }

   public synchronized void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext, CapturerObserver capturerObserver) {
      this.checkNotDisposed();
      if (capturerObserver == null) {
         throw new RuntimeException("capturerObserver not set.");
      } else {
         this.capturerObserver = capturerObserver;
         if (surfaceTextureHelper == null) {
            throw new RuntimeException("surfaceTextureHelper not set.");
         } else {
            this.surfaceTextureHelper = surfaceTextureHelper;
            this.mediaProjectionManager = (MediaProjectionManager)applicationContext.getSystemService("media_projection");
         }
      }
   }

   public synchronized void startCapture(int width, int height, int ignoredFramerate) {
      this.checkNotDisposed();
      this.width = width;
      this.height = height;
      this.mediaProjection = this.mediaProjectionManager.getMediaProjection(-1, this.mediaProjectionPermissionResultData);
      this.mediaProjection.registerCallback(this.mediaProjectionCallback, this.surfaceTextureHelper.getHandler());
      this.createVirtualDisplay();
      this.capturerObserver.onCapturerStarted(true);
      this.surfaceTextureHelper.startListening(this);
   }

   public synchronized void stopCapture() {
      this.checkNotDisposed();
      ThreadUtils.invokeAtFrontUninterruptibly(this.surfaceTextureHelper.getHandler(), new Runnable() {
         public void run() {
            ScreenCapturerAndroid.this.surfaceTextureHelper.stopListening();
            ScreenCapturerAndroid.this.capturerObserver.onCapturerStopped();
            if (ScreenCapturerAndroid.this.virtualDisplay != null) {
               ScreenCapturerAndroid.this.virtualDisplay.release();
               ScreenCapturerAndroid.this.virtualDisplay = null;
            }

            if (ScreenCapturerAndroid.this.mediaProjection != null) {
               ScreenCapturerAndroid.this.mediaProjection.unregisterCallback(ScreenCapturerAndroid.this.mediaProjectionCallback);
               ScreenCapturerAndroid.this.mediaProjection.stop();
               ScreenCapturerAndroid.this.mediaProjection = null;
            }

         }
      });
   }

   public synchronized void dispose() {
      this.isDisposed = true;
   }

   public synchronized void changeCaptureFormat(int width, int height, int ignoredFramerate) {
      this.checkNotDisposed();
      this.width = width;
      this.height = height;
      if (this.virtualDisplay != null) {
         ThreadUtils.invokeAtFrontUninterruptibly(this.surfaceTextureHelper.getHandler(), new Runnable() {
            public void run() {
               ScreenCapturerAndroid.this.virtualDisplay.release();
               ScreenCapturerAndroid.this.createVirtualDisplay();
            }
         });
      }
   }

   private void createVirtualDisplay() {
      this.surfaceTextureHelper.getSurfaceTexture().setDefaultBufferSize(this.width, this.height);
      this.virtualDisplay = this.mediaProjection.createVirtualDisplay("WebRTC_ScreenCapture", this.width, this.height, 400, 3, new Surface(this.surfaceTextureHelper.getSurfaceTexture()), (VirtualDisplay.Callback)null, (Handler)null);
   }

   public void onTextureFrameAvailable(int oesTextureId, float[] transformMatrix, long timestampNs) {
      ++this.numCapturedFrames;
      this.capturerObserver.onTextureFrameCaptured(this.width, this.height, oesTextureId, transformMatrix, 0, timestampNs);
   }

   public boolean isScreencast() {
      return true;
   }

   public long getNumCapturedFrames() {
      return this.numCapturedFrames;
   }
}
