package org.webrtc2;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.os.Build.VERSION;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MediaCodecVideoDecoder {
   private static final String TAG = "MediaCodecVideoDecoder";
   private static final long MAX_DECODE_TIME_MS = 200L;
   private static final int DEQUEUE_INPUT_TIMEOUT = 500000;
   private static final int MEDIA_CODEC_RELEASE_TIMEOUT_MS = 5000;
   private static final int MAX_QUEUED_OUTPUTBUFFERS = 3;
   private static MediaCodecVideoDecoder runningInstance = null;
   private static MediaCodecVideoDecoderErrorCallback errorCallback = null;
   private static int codecErrors = 0;
   private static Set<String> hwDecoderDisabledTypes = new HashSet();
   private Thread mediaCodecThread;
   private MediaCodec mediaCodec;
   private ByteBuffer[] inputBuffers;
   private ByteBuffer[] outputBuffers;
   private static final String VP8_MIME_TYPE = "video/x-vnd.on2.vp8";
   private static final String VP9_MIME_TYPE = "video/x-vnd.on2.vp9";
   private static final String H264_MIME_TYPE = "video/avc";
   private static final String[] supportedVp8HwCodecPrefixes = new String[]{"OMX.qcom.", "OMX.Nvidia.", "OMX.Exynos.", "OMX.Intel."};
   private static final String[] supportedVp9HwCodecPrefixes = new String[]{"OMX.qcom.", "OMX.Exynos."};
   private static final String[] supportedH264HwCodecPrefixes = new String[]{"OMX.qcom.", "OMX.Intel.", "OMX.Exynos."};
   private static final int COLOR_QCOM_FORMATYVU420PackedSemiPlanar32m4ka = 2141391873;
   private static final int COLOR_QCOM_FORMATYVU420PackedSemiPlanar16m4ka = 2141391874;
   private static final int COLOR_QCOM_FORMATYVU420PackedSemiPlanar64x32Tile2m8ka = 2141391875;
   private static final int COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m = 2141391876;
   private static final List<Integer> supportedColorList = Arrays.asList(19, 21, 2141391872, 2141391873, 2141391874, 2141391875, 2141391876);
   private int colorFormat;
   private int width;
   private int height;
   private int stride;
   private int sliceHeight;
   private boolean hasDecodedFirstFrame;
   private final Queue<TimeStamps> decodeStartTimeMs = new LinkedList();
   private boolean useSurface;
   private TextureListener textureListener;
   private int droppedFrames;
   private Surface surface = null;
   private final Queue<DecodedOutputBuffer> dequeuedSurfaceOutputBuffers = new LinkedList();

   public static void setErrorCallback(MediaCodecVideoDecoderErrorCallback errorCallback) {
      Logging.d("MediaCodecVideoDecoder", "Set error callback");
      MediaCodecVideoDecoder.errorCallback = errorCallback;
   }

   public static void disableVp8HwCodec() {
      Logging.w("MediaCodecVideoDecoder", "VP8 decoding is disabled by application.");
      hwDecoderDisabledTypes.add("video/x-vnd.on2.vp8");
   }

   public static void disableVp9HwCodec() {
      Logging.w("MediaCodecVideoDecoder", "VP9 decoding is disabled by application.");
      hwDecoderDisabledTypes.add("video/x-vnd.on2.vp9");
   }

   public static void disableH264HwCodec() {
      Logging.w("MediaCodecVideoDecoder", "H.264 decoding is disabled by application.");
      hwDecoderDisabledTypes.add("video/avc");
   }

   public static boolean isVp8HwSupported() {
      return !hwDecoderDisabledTypes.contains("video/x-vnd.on2.vp8") && findDecoder("video/x-vnd.on2.vp8", supportedVp8HwCodecPrefixes) != null;
   }

   public static boolean isVp9HwSupported() {
      return !hwDecoderDisabledTypes.contains("video/x-vnd.on2.vp9") && findDecoder("video/x-vnd.on2.vp9", supportedVp9HwCodecPrefixes) != null;
   }

   public static boolean isH264HwSupported() {
      return !hwDecoderDisabledTypes.contains("video/avc") && findDecoder("video/avc", supportedH264HwCodecPrefixes) != null;
   }

   public static void printStackTrace() {
      if (runningInstance != null && runningInstance.mediaCodecThread != null) {
         StackTraceElement[] mediaCodecStackTraces = runningInstance.mediaCodecThread.getStackTrace();
         if (mediaCodecStackTraces.length > 0) {
            Logging.d("MediaCodecVideoDecoder", "MediaCodecVideoDecoder stacks trace:");
            StackTraceElement[] arr$ = mediaCodecStackTraces;
            int len$ = arr$.length;

            for(int i$ = 0; i$ < len$; ++i$) {
               StackTraceElement stackTrace = arr$[i$];
               Logging.d("MediaCodecVideoDecoder", stackTrace.toString());
            }
         }
      }

   }

   private static DecoderProperties findDecoder(String mime, String[] supportedCodecPrefixes) {
      if (VERSION.SDK_INT < 19) {
         return null;
      } else {
         Logging.d("MediaCodecVideoDecoder", "Trying to find HW decoder for mime " + mime);

         for(int i = 0; i < MediaCodecList.getCodecCount(); ++i) {
            MediaCodecInfo info = null;

            try {
               info = MediaCodecList.getCodecInfoAt(i);
            } catch (IllegalArgumentException var13) {
               IllegalArgumentException e = var13;
               Logging.e("MediaCodecVideoDecoder", "Cannot retrieve decoder codec info", e);
            }

            if (info != null && !info.isEncoder()) {
               String name = null;
               String[] arr$ = info.getSupportedTypes();
               int len$ = arr$.length;


               for(int j = 0; j < len$; ++j) {
                  String mimeType = arr$[i];
                  if (mimeType.equals(mime)) {
                     name = info.getName();
                     break;
                  }
               }

               if (name != null) {
                  Logging.d("MediaCodecVideoDecoder", "Found candidate decoder " + name);
                  boolean supportedCodec = false;
                  String[] arr1 = supportedCodecPrefixes;
                  len$ = supportedCodecPrefixes.length;

                  int supportedColorFormat;
                  for(supportedColorFormat = 0; supportedColorFormat < len$; ++supportedColorFormat) {
                     String codecPrefix = arr1[supportedColorFormat];
                     if (name.startsWith(codecPrefix)) {
                        supportedCodec = true;
                        break;
                     }
                  }

                  if (supportedCodec) {
                     MediaCodecInfo.CodecCapabilities capabilities;
                     try {
                        capabilities = info.getCapabilitiesForType(mime);
                     } catch (IllegalArgumentException var14) {
                        Logging.e("MediaCodecVideoDecoder", "Cannot retrieve decoder capabilities", var14);
                        continue;
                     }

                     int[] arr2 = capabilities.colorFormats;
                     supportedColorFormat = arr2.length;

                     int len4;
                     for(int i$ = 0; i$ < supportedColorFormat; ++i$) {
                        len4 = arr2[i$];
                        Logging.v("MediaCodecVideoDecoder", "   Color: 0x" + Integer.toHexString(len4));
                     }

                     Iterator i$ = supportedColorList.iterator();

                     while(i$.hasNext()) {
                        supportedColorFormat = (Integer)i$.next();
                        int[] arr7 = capabilities.colorFormats;
                        len4 = arr7.length;

                        for(int k = 0; k < len4; ++k) {
                           int codecColorFormat = arr7[k];
                           if (codecColorFormat == supportedColorFormat) {
                              Logging.d("MediaCodecVideoDecoder", "Found target decoder " + name + ". Color: 0x" + Integer.toHexString(codecColorFormat));
                              return new DecoderProperties(name, codecColorFormat);
                           }
                        }
                     }
                  }
               }
            }
         }

         Logging.d("MediaCodecVideoDecoder", "No HW decoder found for mime " + mime);
         return null;
      }
   }

   private void checkOnMediaCodecThread() throws IllegalStateException {
      if (this.mediaCodecThread.getId() != Thread.currentThread().getId()) {
         throw new IllegalStateException("MediaCodecVideoDecoder previously operated on " + this.mediaCodecThread + " but is now called on " + Thread.currentThread());
      }
   }

   private boolean initDecode(VideoCodecType type, int width, int height, org.webrtc2.SurfaceTextureHelper surfaceTextureHelper) {
      if (this.mediaCodecThread != null) {
         throw new RuntimeException("initDecode: Forgot to release()?");
      } else {
         String mime = null;
         this.useSurface = surfaceTextureHelper != null;
         String[] supportedCodecPrefixes = null;
         if (type == VideoCodecType.VIDEO_CODEC_VP8) {
            mime = "video/x-vnd.on2.vp8";
            supportedCodecPrefixes = supportedVp8HwCodecPrefixes;
         } else if (type == VideoCodecType.VIDEO_CODEC_VP9) {
            mime = "video/x-vnd.on2.vp9";
            supportedCodecPrefixes = supportedVp9HwCodecPrefixes;
         } else {
            if (type != VideoCodecType.VIDEO_CODEC_H264) {
               throw new RuntimeException("initDecode: Non-supported codec " + type);
            }

            mime = "video/avc";
            supportedCodecPrefixes = supportedH264HwCodecPrefixes;
         }

         DecoderProperties properties = findDecoder(mime, supportedCodecPrefixes);
         if (properties == null) {
            throw new RuntimeException("Cannot find HW decoder for " + type);
         } else {
            Logging.d("MediaCodecVideoDecoder", "Java initDecode: " + type + " : " + width + " x " + height + ". Color: 0x" + Integer.toHexString(properties.colorFormat) + ". Use Surface: " + this.useSurface);
            runningInstance = this;
            this.mediaCodecThread = Thread.currentThread();

            try {
               this.width = width;
               this.height = height;
               this.stride = width;
               this.sliceHeight = height;
               if (this.useSurface) {
                  this.textureListener = new TextureListener(surfaceTextureHelper);
                  this.surface = new Surface(surfaceTextureHelper.getSurfaceTexture());
               }

               MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
               if (!this.useSurface) {
                  format.setInteger("color-format", properties.colorFormat);
               }

               Logging.d("MediaCodecVideoDecoder", "  Format: " + format);
               this.mediaCodec = MediaCodecVideoEncoder.createByCodecName(properties.codecName);
               if (this.mediaCodec == null) {
                  Logging.e("MediaCodecVideoDecoder", "Can not create media decoder");
                  return false;
               } else {
                  this.mediaCodec.configure(format, this.surface, (MediaCrypto)null, 0);
                  this.mediaCodec.start();
                  this.colorFormat = properties.colorFormat;
                  this.outputBuffers = this.mediaCodec.getOutputBuffers();
                  this.inputBuffers = this.mediaCodec.getInputBuffers();
                  this.decodeStartTimeMs.clear();
                  this.hasDecodedFirstFrame = false;
                  this.dequeuedSurfaceOutputBuffers.clear();
                  this.droppedFrames = 0;
                  Logging.d("MediaCodecVideoDecoder", "Input buffers: " + this.inputBuffers.length + ". Output buffers: " + this.outputBuffers.length);
                  return true;
               }
            } catch (IllegalStateException var9) {
               IllegalStateException e = var9;
               Logging.e("MediaCodecVideoDecoder", "initDecode failed", e);
               return false;
            }
         }
      }
   }

   private void reset(int width, int height) {
      if (this.mediaCodecThread != null && this.mediaCodec != null) {
         Logging.d("MediaCodecVideoDecoder", "Java reset: " + width + " x " + height);
         this.mediaCodec.flush();
         this.width = width;
         this.height = height;
         this.decodeStartTimeMs.clear();
         this.dequeuedSurfaceOutputBuffers.clear();
         this.hasDecodedFirstFrame = false;
         this.droppedFrames = 0;
      } else {
         throw new RuntimeException("Incorrect reset call for non-initialized decoder.");
      }
   }

   private void release() {
      Logging.d("MediaCodecVideoDecoder", "Java releaseDecoder. Total number of dropped frames: " + this.droppedFrames);
      this.checkOnMediaCodecThread();
      final CountDownLatch releaseDone = new CountDownLatch(1);
      Runnable runMediaCodecRelease = new Runnable() {
         public void run() {
            try {
               Logging.d("MediaCodecVideoDecoder", "Java releaseDecoder on release thread");
               MediaCodecVideoDecoder.this.mediaCodec.stop();
               MediaCodecVideoDecoder.this.mediaCodec.release();
               Logging.d("MediaCodecVideoDecoder", "Java releaseDecoder on release thread done");
            } catch (Exception var2) {
               Exception e = var2;
               Logging.e("MediaCodecVideoDecoder", "Media decoder release failed", e);
            }

            releaseDone.countDown();
         }
      };
      (new Thread(runMediaCodecRelease)).start();
      if (!ThreadUtils.awaitUninterruptibly(releaseDone, 5000L)) {
         Logging.e("MediaCodecVideoDecoder", "Media decoder release timeout");
         ++codecErrors;
         if (errorCallback != null) {
            Logging.e("MediaCodecVideoDecoder", "Invoke codec error callback. Errors: " + codecErrors);
            errorCallback.onMediaCodecVideoDecoderCriticalError(codecErrors);
         }
      }

      this.mediaCodec = null;
      this.mediaCodecThread = null;
      runningInstance = null;
      if (this.useSurface) {
         this.surface.release();
         this.surface = null;
         this.textureListener.release();
      }

      Logging.d("MediaCodecVideoDecoder", "Java releaseDecoder done");
   }

   private int dequeueInputBuffer() {
      this.checkOnMediaCodecThread();

      try {
         return this.mediaCodec.dequeueInputBuffer(500000L);
      } catch (IllegalStateException var2) {
         IllegalStateException e = var2;
         Logging.e("MediaCodecVideoDecoder", "dequeueIntputBuffer failed", e);
         return -2;
      }
   }

   private boolean queueInputBuffer(int inputBufferIndex, int size, long presentationTimeStamUs, long timeStampMs, long ntpTimeStamp) {
      this.checkOnMediaCodecThread();

      try {
         this.inputBuffers[inputBufferIndex].position(0);
         this.inputBuffers[inputBufferIndex].limit(size);
         this.decodeStartTimeMs.add(new TimeStamps(SystemClock.elapsedRealtime(), timeStampMs, ntpTimeStamp));
         this.mediaCodec.queueInputBuffer(inputBufferIndex, 0, size, presentationTimeStamUs, 0);
         return true;
      } catch (IllegalStateException var10) {
         IllegalStateException e = var10;
         Logging.e("MediaCodecVideoDecoder", "decode failed", e);
         return false;
      }
   }

   private DecodedOutputBuffer dequeueOutputBuffer(int dequeueTimeoutMs) {
      this.checkOnMediaCodecThread();
      if (this.decodeStartTimeMs.isEmpty()) {
         return null;
      } else {
         MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

         while(true) {
            int result = this.mediaCodec.dequeueOutputBuffer(info, TimeUnit.MILLISECONDS.toMicros((long)dequeueTimeoutMs));
            switch (result) {
               case -3:
                  this.outputBuffers = this.mediaCodec.getOutputBuffers();
                  Logging.d("MediaCodecVideoDecoder", "Decoder output buffers changed: " + this.outputBuffers.length);
                  if (this.hasDecodedFirstFrame) {
                     throw new RuntimeException("Unexpected output buffer change event.");
                  }
                  break;
               case -2:
                  MediaFormat format = this.mediaCodec.getOutputFormat();
                  Logging.d("MediaCodecVideoDecoder", "Decoder format changed: " + format.toString());
                  int new_width = format.getInteger("width");
                  int new_height = format.getInteger("height");
                  if (this.hasDecodedFirstFrame && (new_width != this.width || new_height != this.height)) {
                     throw new RuntimeException("Unexpected size change. Configured " + this.width + "*" + this.height + ". New " + new_width + "*" + new_height);
                  }

                  this.width = format.getInteger("width");
                  this.height = format.getInteger("height");
                  if (!this.useSurface && format.containsKey("color-format")) {
                     this.colorFormat = format.getInteger("color-format");
                     Logging.d("MediaCodecVideoDecoder", "Color: 0x" + Integer.toHexString(this.colorFormat));
                     if (!supportedColorList.contains(this.colorFormat)) {
                        throw new IllegalStateException("Non supported color format: " + this.colorFormat);
                     }
                  }

                  if (format.containsKey("stride")) {
                     this.stride = format.getInteger("stride");
                  }

                  if (format.containsKey("slice-height")) {
                     this.sliceHeight = format.getInteger("slice-height");
                  }

                  Logging.d("MediaCodecVideoDecoder", "Frame stride and slice height: " + this.stride + " x " + this.sliceHeight);
                  this.stride = Math.max(this.width, this.stride);
                  this.sliceHeight = Math.max(this.height, this.sliceHeight);
                  break;
               case -1:
                  return null;
               default:
                  this.hasDecodedFirstFrame = true;
                  TimeStamps timeStamps = (TimeStamps)this.decodeStartTimeMs.remove();
                  long decodeTimeMs = SystemClock.elapsedRealtime() - timeStamps.decodeStartTimeMs;
                  if (decodeTimeMs > 200L) {
                     Logging.e("MediaCodecVideoDecoder", "Very high decode time: " + decodeTimeMs + "ms" + ". Q size: " + this.decodeStartTimeMs.size() + ". Might be caused by resuming H264 decoding after a pause.");
                     decodeTimeMs = 200L;
                  }

                  return new DecodedOutputBuffer(result, info.offset, info.size, TimeUnit.MICROSECONDS.toMillis(info.presentationTimeUs), timeStamps.timeStampMs, timeStamps.ntpTimeStampMs, decodeTimeMs, SystemClock.elapsedRealtime());
            }
         }
      }
   }

   private DecodedTextureBuffer dequeueTextureBuffer(int dequeueTimeoutMs) {
      this.checkOnMediaCodecThread();
      if (!this.useSurface) {
         throw new IllegalStateException("dequeueTexture() called for byte buffer decoding.");
      } else {
         DecodedOutputBuffer outputBuffer = this.dequeueOutputBuffer(dequeueTimeoutMs);
         if (outputBuffer != null) {
            this.dequeuedSurfaceOutputBuffers.add(outputBuffer);
         }

         this.MaybeRenderDecodedTextureBuffer();
         DecodedTextureBuffer renderedBuffer = this.textureListener.dequeueTextureBuffer(dequeueTimeoutMs);
         if (renderedBuffer != null) {
            this.MaybeRenderDecodedTextureBuffer();
            return renderedBuffer;
         } else if (this.dequeuedSurfaceOutputBuffers.size() < Math.min(3, this.outputBuffers.length) && (dequeueTimeoutMs <= 0 || this.dequeuedSurfaceOutputBuffers.isEmpty())) {
            return null;
         } else {
            ++this.droppedFrames;
            DecodedOutputBuffer droppedFrame = (DecodedOutputBuffer)this.dequeuedSurfaceOutputBuffers.remove();
            if (dequeueTimeoutMs > 0) {
               Logging.w("MediaCodecVideoDecoder", "Draining decoder. Dropping frame with TS: " + droppedFrame.presentationTimeStampMs + ". Total number of dropped frames: " + this.droppedFrames);
            } else {
               Logging.w("MediaCodecVideoDecoder", "Too many output buffers " + this.dequeuedSurfaceOutputBuffers.size() + ". Dropping frame with TS: " + droppedFrame.presentationTimeStampMs + ". Total number of dropped frames: " + this.droppedFrames);
            }

            this.mediaCodec.releaseOutputBuffer(droppedFrame.index, false);
            return new DecodedTextureBuffer(0, (float[])null, droppedFrame.presentationTimeStampMs, droppedFrame.timeStampMs, droppedFrame.ntpTimeStampMs, droppedFrame.decodeTimeMs, SystemClock.elapsedRealtime() - droppedFrame.endDecodeTimeMs);
         }
      }
   }

   private void MaybeRenderDecodedTextureBuffer() {
      if (!this.dequeuedSurfaceOutputBuffers.isEmpty() && !this.textureListener.isWaitingForTexture()) {
         DecodedOutputBuffer buffer = (DecodedOutputBuffer)this.dequeuedSurfaceOutputBuffers.remove();
         this.textureListener.addBufferToRender(buffer);
         this.mediaCodec.releaseOutputBuffer(buffer.index, true);
      }
   }

   private void returnDecodedOutputBuffer(int index) throws IllegalStateException, MediaCodec.CodecException {
      this.checkOnMediaCodecThread();
      if (this.useSurface) {
         throw new IllegalStateException("returnDecodedOutputBuffer() called for surface decoding.");
      } else {
         this.mediaCodec.releaseOutputBuffer(index, false);
      }
   }

   private static class TextureListener implements org.webrtc2.SurfaceTextureHelper.OnTextureFrameAvailableListener {
      private final org.webrtc2.SurfaceTextureHelper surfaceTextureHelper;
      private final Object newFrameLock = new Object();
      private DecodedOutputBuffer bufferToRender;
      private DecodedTextureBuffer renderedBuffer;

      public TextureListener(SurfaceTextureHelper surfaceTextureHelper) {
         this.surfaceTextureHelper = surfaceTextureHelper;
         surfaceTextureHelper.startListening(this);
      }

      public void addBufferToRender(DecodedOutputBuffer buffer) {
         if (this.bufferToRender != null) {
            Logging.e("MediaCodecVideoDecoder", "Unexpected addBufferToRender() called while waiting for a texture.");
            throw new IllegalStateException("Waiting for a texture.");
         } else {
            this.bufferToRender = buffer;
         }
      }

      public boolean isWaitingForTexture() {
         synchronized(this.newFrameLock) {
            return this.bufferToRender != null;
         }
      }

      public void onTextureFrameAvailable(int oesTextureId, float[] transformMatrix, long timestampNs) {
         synchronized(this.newFrameLock) {
            if (this.renderedBuffer != null) {
               Logging.e("MediaCodecVideoDecoder", "Unexpected onTextureFrameAvailable() called while already holding a texture.");
               throw new IllegalStateException("Already holding a texture.");
            } else {
               this.renderedBuffer = new DecodedTextureBuffer(oesTextureId, transformMatrix, this.bufferToRender.presentationTimeStampMs, this.bufferToRender.timeStampMs, this.bufferToRender.ntpTimeStampMs, this.bufferToRender.decodeTimeMs, SystemClock.elapsedRealtime() - this.bufferToRender.endDecodeTimeMs);
               this.bufferToRender = null;
               this.newFrameLock.notifyAll();
            }
         }
      }

      public DecodedTextureBuffer dequeueTextureBuffer(int timeoutMs) {
         synchronized(this.newFrameLock) {
            if (this.renderedBuffer == null && timeoutMs > 0 && this.isWaitingForTexture()) {
               try {
                  this.newFrameLock.wait((long)timeoutMs);
               } catch (InterruptedException var5) {
                  Thread.currentThread().interrupt();
               }
            }

            DecodedTextureBuffer returnedBuffer = this.renderedBuffer;
            this.renderedBuffer = null;
            return returnedBuffer;
         }
      }

      public void release() {
         this.surfaceTextureHelper.stopListening();
         synchronized(this.newFrameLock) {
            if (this.renderedBuffer != null) {
               this.surfaceTextureHelper.returnTextureFrame();
               this.renderedBuffer = null;
            }

         }
      }
   }

   private static class DecodedTextureBuffer {
      private final int textureID;
      private final float[] transformMatrix;
      private final long presentationTimeStampMs;
      private final long timeStampMs;
      private final long ntpTimeStampMs;
      private final long decodeTimeMs;
      private final long frameDelayMs;

      public DecodedTextureBuffer(int textureID, float[] transformMatrix, long presentationTimeStampMs, long timeStampMs, long ntpTimeStampMs, long decodeTimeMs, long frameDelay) {
         this.textureID = textureID;
         this.transformMatrix = transformMatrix;
         this.presentationTimeStampMs = presentationTimeStampMs;
         this.timeStampMs = timeStampMs;
         this.ntpTimeStampMs = ntpTimeStampMs;
         this.decodeTimeMs = decodeTimeMs;
         this.frameDelayMs = frameDelay;
      }
   }

   private static class DecodedOutputBuffer {
      private final int index;
      private final int offset;
      private final int size;
      private final long presentationTimeStampMs;
      private final long timeStampMs;
      private final long ntpTimeStampMs;
      private final long decodeTimeMs;
      private final long endDecodeTimeMs;

      public DecodedOutputBuffer(int index, int offset, int size, long presentationTimeStampMs, long timeStampMs, long ntpTimeStampMs, long decodeTime, long endDecodeTime) {
         this.index = index;
         this.offset = offset;
         this.size = size;
         this.presentationTimeStampMs = presentationTimeStampMs;
         this.timeStampMs = timeStampMs;
         this.ntpTimeStampMs = ntpTimeStampMs;
         this.decodeTimeMs = decodeTime;
         this.endDecodeTimeMs = endDecodeTime;
      }
   }

   private static class TimeStamps {
      private final long decodeStartTimeMs;
      private final long timeStampMs;
      private final long ntpTimeStampMs;

      public TimeStamps(long decodeStartTimeMs, long timeStampMs, long ntpTimeStampMs) {
         this.decodeStartTimeMs = decodeStartTimeMs;
         this.timeStampMs = timeStampMs;
         this.ntpTimeStampMs = ntpTimeStampMs;
      }
   }

   private static class DecoderProperties {
      public final String codecName;
      public final int colorFormat;

      public DecoderProperties(String codecName, int colorFormat) {
         this.codecName = codecName;
         this.colorFormat = colorFormat;
      }
   }

   public interface MediaCodecVideoDecoderErrorCallback {
      void onMediaCodecVideoDecoderCriticalError(int var1);
   }

   public static enum VideoCodecType {
      VIDEO_CODEC_VP8,
      VIDEO_CODEC_VP9,
      VIDEO_CODEC_H264;
   }
}
