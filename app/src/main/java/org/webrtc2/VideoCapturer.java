package org.webrtc2;

import android.content.Context;

public interface VideoCapturer {
   void initialize(SurfaceTextureHelper var1, Context var2, CapturerObserver var3);

   void startCapture(int var1, int var2, int var3);

   void stopCapture() throws InterruptedException;

   void changeCaptureFormat(int var1, int var2, int var3);

   void dispose();

   boolean isScreencast();

   public static class AndroidVideoTrackSourceObserver implements CapturerObserver {
      private final long nativeSource;

      public AndroidVideoTrackSourceObserver(long nativeSource) {
         this.nativeSource = nativeSource;
      }

      public void onCapturerStarted(boolean success) {
         this.nativeCapturerStarted(this.nativeSource, success);
      }

      public void onCapturerStopped() {
         this.nativeCapturerStopped(this.nativeSource);
      }

      public void onByteBufferFrameCaptured(byte[] data, int width, int height, int rotation, long timeStamp) {
         this.nativeOnByteBufferFrameCaptured(this.nativeSource, data, data.length, width, height, rotation, timeStamp);
      }

      public void onTextureFrameCaptured(int width, int height, int oesTextureId, float[] transformMatrix, int rotation, long timestamp) {
         this.nativeOnTextureFrameCaptured(this.nativeSource, width, height, oesTextureId, transformMatrix, rotation, timestamp);
      }

      private native void nativeCapturerStarted(long var1, boolean var3);

      private native void nativeCapturerStopped(long var1);

      private native void nativeOnByteBufferFrameCaptured(long var1, byte[] var3, int var4, int var5, int var6, int var7, long var8);

      private native void nativeOnTextureFrameCaptured(long var1, int var3, int var4, int var5, float[] var6, int var7, long var8);
   }

   public interface CapturerObserver {
      void onCapturerStarted(boolean var1);

      void onCapturerStopped();

      void onByteBufferFrameCaptured(byte[] var1, int var2, int var3, int var4, long var5);

      void onTextureFrameCaptured(int var1, int var2, int var3, float[] var4, int var5, long var6);
   }
}
