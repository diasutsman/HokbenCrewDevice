package org.webrtc2;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.os.Build.VERSION;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@TargetApi(19)
public class MediaCodecVideoEncoder {
   private static final String TAG = "MediaCodecVideoEncoder";
   private static final int MEDIA_CODEC_RELEASE_TIMEOUT_MS = 5000;
   private static final int DEQUEUE_TIMEOUT = 0;
   private static final int BITRATE_ADJUSTMENT_FPS = 30;
   private static final int MAXIMUM_INITIAL_FPS = 30;
   private static final double BITRATE_CORRECTION_SEC = 3.0;
   private static final double BITRATE_CORRECTION_MAX_SCALE = 2.0;
   private static final int BITRATE_CORRECTION_STEPS = 10;
   private static MediaCodecVideoEncoder runningInstance = null;
   private static MediaCodecVideoEncoderErrorCallback errorCallback = null;
   private static int codecErrors = 0;
   private static Set<String> hwEncoderDisabledTypes = new HashSet();
   private Thread mediaCodecThread;
   private MediaCodec mediaCodec;
   private ByteBuffer[] outputBuffers;
   private org.webrtc2.EglBase14 eglBase;
   private int width;
   private int height;
   private Surface inputSurface;
   private org.webrtc2.GlRectDrawer drawer;
   private static final String VP8_MIME_TYPE = "video/x-vnd.on2.vp8";
   private static final String VP9_MIME_TYPE = "video/x-vnd.on2.vp9";
   private static final String H264_MIME_TYPE = "video/avc";
   private static final MediaCodecProperties qcomVp8HwProperties;
   private static final MediaCodecProperties exynosVp8HwProperties;
   private static final MediaCodecProperties[] vp8HwList;
   private static final MediaCodecProperties qcomVp9HwProperties;
   private static final MediaCodecProperties exynosVp9HwProperties;
   private static final MediaCodecProperties[] vp9HwList;
   private static final MediaCodecProperties qcomH264HwProperties;
   private static final MediaCodecProperties exynosH264HwProperties;
   private static final MediaCodecProperties[] h264HwList;
   private static final String[] H264_HW_EXCEPTION_MODELS;
   private static final int VIDEO_ControlRateConstant = 2;
   private static final int COLOR_QCOM_FORMATYUV420PackedSemiPlanar32m = 2141391876;
   private static final int[] supportedColorList;
   private static final int[] supportedSurfaceColorList;
   private VideoCodecType type;
   private int colorFormat;
   private BitrateAdjustmentType bitrateAdjustmentType;
   private double bitrateAccumulator;
   private double bitrateAccumulatorMax;
   private double bitrateObservationTimeMs;
   private int bitrateAdjustmentScaleExp;
   private int targetBitrateBps;
   private int targetFps;
   private ByteBuffer configData;

   public MediaCodecVideoEncoder() {
      this.bitrateAdjustmentType = BitrateAdjustmentType.NO_ADJUSTMENT;
      this.configData = null;
   }

   public static void setErrorCallback(MediaCodecVideoEncoderErrorCallback errorCallback) {
      Logging.d("MediaCodecVideoEncoder", "Set error callback");
      MediaCodecVideoEncoder.errorCallback = errorCallback;
   }

   public static void disableVp8HwCodec() {
      Logging.w("MediaCodecVideoEncoder", "VP8 encoding is disabled by application.");
      hwEncoderDisabledTypes.add("video/x-vnd.on2.vp8");
   }

   public static void disableVp9HwCodec() {
      Logging.w("MediaCodecVideoEncoder", "VP9 encoding is disabled by application.");
      hwEncoderDisabledTypes.add("video/x-vnd.on2.vp9");
   }

   public static void disableH264HwCodec() {
      Logging.w("MediaCodecVideoEncoder", "H.264 encoding is disabled by application.");
      hwEncoderDisabledTypes.add("video/avc");
   }

   public static boolean isVp8HwSupported() {
      return !hwEncoderDisabledTypes.contains("video/x-vnd.on2.vp8") && findHwEncoder("video/x-vnd.on2.vp8", vp8HwList, supportedColorList) != null;
   }

   public static boolean isVp9HwSupported() {
      return !hwEncoderDisabledTypes.contains("video/x-vnd.on2.vp9") && findHwEncoder("video/x-vnd.on2.vp9", vp9HwList, supportedColorList) != null;
   }

   public static boolean isH264HwSupported() {
      return !hwEncoderDisabledTypes.contains("video/avc") && findHwEncoder("video/avc", h264HwList, supportedColorList) != null;
   }

   public static boolean isVp8HwSupportedUsingTextures() {
      return !hwEncoderDisabledTypes.contains("video/x-vnd.on2.vp8") && findHwEncoder("video/x-vnd.on2.vp8", vp8HwList, supportedSurfaceColorList) != null;
   }

   public static boolean isVp9HwSupportedUsingTextures() {
      return !hwEncoderDisabledTypes.contains("video/x-vnd.on2.vp9") && findHwEncoder("video/x-vnd.on2.vp9", vp9HwList, supportedSurfaceColorList) != null;
   }

   public static boolean isH264HwSupportedUsingTextures() {
      return !hwEncoderDisabledTypes.contains("video/avc") && findHwEncoder("video/avc", h264HwList, supportedSurfaceColorList) != null;
   }

   private static EncoderProperties findHwEncoder(String mime, MediaCodecProperties[] supportedHwCodecProperties, int[] colorList) {
      if (VERSION.SDK_INT < 19) {
         return null;
      } else {
         if (mime.equals("video/avc")) {
            List<String> exceptionModels = Arrays.asList(H264_HW_EXCEPTION_MODELS);
            if (exceptionModels.contains(Build.MODEL)) {
               Logging.w("MediaCodecVideoEncoder", "Model: " + Build.MODEL + " has black listed H.264 encoder.");
               return null;
            }
         }

         for(int i = 0; i < MediaCodecList.getCodecCount(); ++i) {
            MediaCodecInfo info = null;

            try {
               info = MediaCodecList.getCodecInfoAt(i);
            } catch (IllegalArgumentException var17) {
               IllegalArgumentException e = var17;
               Logging.e("MediaCodecVideoEncoder", "Cannot retrieve encoder codec info", e);
            }

            if (info != null && info.isEncoder()) {
               String name = null;
               String[] arr$ = info.getSupportedTypes();
               int len$ = arr$.length;

               for(int i$ = 0; i$ < len$; ++i$) {
                  String mimeType = arr$[i$];
                  if (mimeType.equals(mime)) {
                     name = info.getName();
                     break;
                  }
               }

               if (name != null) {
                  Logging.v("MediaCodecVideoEncoder", "Found candidate encoder " + name);
                  boolean supportedCodec = false;
                  BitrateAdjustmentType bitrateAdjustmentType = BitrateAdjustmentType.NO_ADJUSTMENT;
                  MediaCodecProperties[] arr10 = supportedHwCodecProperties;
                  int len5 = supportedHwCodecProperties.length;

                  int ik;
                  for(ik = 0; ik < len5; ++ik) {
                     MediaCodecProperties codecProperties = arr10[ik];
                     if (name.startsWith(codecProperties.codecPrefix)) {
                        if (VERSION.SDK_INT >= codecProperties.minSdk) {
                           if (codecProperties.bitrateAdjustmentType != BitrateAdjustmentType.NO_ADJUSTMENT) {
                              bitrateAdjustmentType = codecProperties.bitrateAdjustmentType;
                              Logging.w("MediaCodecVideoEncoder", "Codec " + name + " requires bitrate adjustment: " + bitrateAdjustmentType);
                           }

                           supportedCodec = true;
                           break;
                        }

                        Logging.w("MediaCodecVideoEncoder", "Codec " + name + " is disabled due to SDK version " + VERSION.SDK_INT);
                     }
                  }

                  if (supportedCodec) {
                     MediaCodecInfo.CodecCapabilities capabilities;
                     try {
                        capabilities = info.getCapabilitiesForType(mime);
                     } catch (IllegalArgumentException var18) {
                        Logging.e("MediaCodecVideoEncoder", "Cannot retrieve encoder capabilities", var18);
                        continue;
                     }

                     int[] arr8 = capabilities.colorFormats;
                     len5 = arr8.length;

                     int supportedColorFormat;
                     int i$;
                     for(i$ = 0; i$ < len5; ++i$) {
                        supportedColorFormat = arr8[i$];
                        Logging.v("MediaCodecVideoEncoder", "   Color: 0x" + Integer.toHexString(supportedColorFormat));
                     }

                     arr8 = colorList;
                     len5 = colorList.length;

                     for(i$ = 0; i$ < len5; ++i$) {
                        supportedColorFormat = arr8[i$];
                        int[] arr100 = capabilities.colorFormats;
                        int len1000 = arr100.length;

                        for(int jkjkj = 0; jkjkj < len1000; ++jkjkj) {
                           int codecColorFormat = arr100[jkjkj];
                           if (codecColorFormat == supportedColorFormat) {
                              Logging.d("MediaCodecVideoEncoder", "Found target encoder for mime " + mime + " : " + name + ". Color: 0x" + Integer.toHexString(codecColorFormat) + ". Bitrate adjustment: " + bitrateAdjustmentType);
                              return new EncoderProperties(name, codecColorFormat, bitrateAdjustmentType);
                           }
                        }
                     }
                  }
               }
            }
         }

         return null;
      }
   }

   private void checkOnMediaCodecThread() {
      if (this.mediaCodecThread.getId() != Thread.currentThread().getId()) {
         throw new RuntimeException("MediaCodecVideoEncoder previously operated on " + this.mediaCodecThread + " but is now called on " + Thread.currentThread());
      }
   }

   public static void printStackTrace() {
      if (runningInstance != null && runningInstance.mediaCodecThread != null) {
         StackTraceElement[] mediaCodecStackTraces = runningInstance.mediaCodecThread.getStackTrace();
         if (mediaCodecStackTraces.length > 0) {
            Logging.d("MediaCodecVideoEncoder", "MediaCodecVideoEncoder stacks trace:");
            StackTraceElement[] arr$ = mediaCodecStackTraces;
            int len$ = arr$.length;

            for(int i$ = 0; i$ < len$; ++i$) {
               StackTraceElement stackTrace = arr$[i$];
               Logging.d("MediaCodecVideoEncoder", stackTrace.toString());
            }
         }
      }

   }

   static MediaCodec createByCodecName(String codecName) {
      try {
         return MediaCodec.createByCodecName(codecName);
      } catch (Exception var2) {
         return null;
      }
   }

   boolean initEncode(VideoCodecType type, int width, int height, int kbps, int fps, org.webrtc2.EglBase14.Context sharedContext) {
      boolean useSurface = sharedContext != null;
      Logging.d("MediaCodecVideoEncoder", "Java initEncode: " + type + " : " + width + " x " + height + ". @ " + kbps + " kbps. Fps: " + fps + ". Encode from texture : " + useSurface);
      this.width = width;
      this.height = height;
      if (this.mediaCodecThread != null) {
         throw new RuntimeException("Forgot to release()?");
      } else {
         EncoderProperties properties = null;
         String mime = null;
         int keyFrameIntervalSec = 0;
         if (type == VideoCodecType.VIDEO_CODEC_VP8) {
            mime = "video/x-vnd.on2.vp8";
            properties = findHwEncoder("video/x-vnd.on2.vp8", vp8HwList, useSurface ? supportedSurfaceColorList : supportedColorList);
            keyFrameIntervalSec = 100;
         } else if (type == VideoCodecType.VIDEO_CODEC_VP9) {
            mime = "video/x-vnd.on2.vp9";
            properties = findHwEncoder("video/x-vnd.on2.vp9", vp9HwList, useSurface ? supportedSurfaceColorList : supportedColorList);
            keyFrameIntervalSec = 100;
         } else if (type == VideoCodecType.VIDEO_CODEC_H264) {
            mime = "video/avc";
            properties = findHwEncoder("video/avc", h264HwList, useSurface ? supportedSurfaceColorList : supportedColorList);
            keyFrameIntervalSec = 20;
         }

         if (properties == null) {
            throw new RuntimeException("Can not find HW encoder for " + type);
         } else {
            runningInstance = this;
            this.colorFormat = properties.colorFormat;
            this.bitrateAdjustmentType = properties.bitrateAdjustmentType;
            if (this.bitrateAdjustmentType == BitrateAdjustmentType.FRAMERATE_ADJUSTMENT) {
               fps = 30;
            } else {
               fps = Math.min(fps, 30);
            }

            Logging.d("MediaCodecVideoEncoder", "Color format: " + this.colorFormat + ". Bitrate adjustment: " + this.bitrateAdjustmentType + ". Initial fps: " + fps);
            this.targetBitrateBps = 1000 * kbps;
            this.targetFps = fps;
            this.bitrateAccumulatorMax = (double)this.targetBitrateBps / 8.0;
            this.bitrateAccumulator = 0.0;
            this.bitrateObservationTimeMs = 0.0;
            this.bitrateAdjustmentScaleExp = 0;
            this.mediaCodecThread = Thread.currentThread();

            try {
               MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
               format.setInteger("bitrate", this.targetBitrateBps);
               format.setInteger("bitrate-mode", 2);
               format.setInteger("color-format", properties.colorFormat);
               format.setInteger("frame-rate", this.targetFps);
               format.setInteger("i-frame-interval", keyFrameIntervalSec);
               Logging.d("MediaCodecVideoEncoder", "  Format: " + format);
               this.mediaCodec = createByCodecName(properties.codecName);
               this.type = type;
               if (this.mediaCodec == null) {
                  Logging.e("MediaCodecVideoEncoder", "Can not create media encoder");
                  return false;
               } else {
                  this.mediaCodec.configure(format, (Surface)null, (MediaCrypto)null, 1);
                  if (useSurface) {
                     this.eglBase = new EglBase14(sharedContext, EglBase.CONFIG_RECORDABLE);
                     this.inputSurface = this.mediaCodec.createInputSurface();
                     this.eglBase.createSurface(this.inputSurface);
                     this.drawer = new GlRectDrawer();
                  }

                  this.mediaCodec.start();
                  this.outputBuffers = this.mediaCodec.getOutputBuffers();
                  Logging.d("MediaCodecVideoEncoder", "Output buffers: " + this.outputBuffers.length);
                  return true;
               }
            } catch (IllegalStateException var12) {
               IllegalStateException e = var12;
               Logging.e("MediaCodecVideoEncoder", "initEncode failed", e);
               return false;
            }
         }
      }
   }

   ByteBuffer[] getInputBuffers() {
      ByteBuffer[] inputBuffers = this.mediaCodec.getInputBuffers();
      Logging.d("MediaCodecVideoEncoder", "Input buffers: " + inputBuffers.length);
      return inputBuffers;
   }

   boolean encodeBuffer(boolean isKeyframe, int inputBuffer, int size, long presentationTimestampUs) {
      this.checkOnMediaCodecThread();

      try {
         if (isKeyframe) {
            Logging.d("MediaCodecVideoEncoder", "Sync frame request");
            Bundle b = new Bundle();
            b.putInt("request-sync", 0);
            this.mediaCodec.setParameters(b);
         }

         this.mediaCodec.queueInputBuffer(inputBuffer, 0, size, presentationTimestampUs, 0);
         return true;
      } catch (IllegalStateException var7) {
         IllegalStateException e = var7;
         Logging.e("MediaCodecVideoEncoder", "encodeBuffer failed", e);
         return false;
      }
   }

   boolean encodeTexture(boolean isKeyframe, int oesTextureId, float[] transformationMatrix, long presentationTimestampUs) {
      this.checkOnMediaCodecThread();

      try {
         if (isKeyframe) {
            Logging.d("MediaCodecVideoEncoder", "Sync frame request");
            Bundle b = new Bundle();
            b.putInt("request-sync", 0);
            this.mediaCodec.setParameters(b);
         }

         this.eglBase.makeCurrent();
         GLES20.glClear(16384);
         this.drawer.drawOes(oesTextureId, transformationMatrix, this.width, this.height, 0, 0, this.width, this.height);
         this.eglBase.swapBuffers(TimeUnit.MICROSECONDS.toNanos(presentationTimestampUs));
         return true;
      } catch (RuntimeException var7) {
         RuntimeException e = var7;
         Logging.e("MediaCodecVideoEncoder", "encodeTexture failed", e);
         return false;
      }
   }

   void release() {
      Logging.d("MediaCodecVideoEncoder", "Java releaseEncoder");
      this.checkOnMediaCodecThread();
      final CountDownLatch releaseDone = new CountDownLatch(1);
      Runnable runMediaCodecRelease = new Runnable() {
         public void run() {
            try {
               Logging.d("MediaCodecVideoEncoder", "Java releaseEncoder on release thread");
               MediaCodecVideoEncoder.this.mediaCodec.stop();
               MediaCodecVideoEncoder.this.mediaCodec.release();
               Logging.d("MediaCodecVideoEncoder", "Java releaseEncoder on release thread done");
            } catch (Exception var2) {
               Exception e = var2;
               Logging.e("MediaCodecVideoEncoder", "Media encoder release failed", e);
            }

            releaseDone.countDown();
         }
      };
      (new Thread(runMediaCodecRelease)).start();
      if (!ThreadUtils.awaitUninterruptibly(releaseDone, 5000L)) {
         Logging.e("MediaCodecVideoEncoder", "Media encoder release timeout");
         ++codecErrors;
         if (errorCallback != null) {
            Logging.e("MediaCodecVideoEncoder", "Invoke codec error callback. Errors: " + codecErrors);
            errorCallback.onMediaCodecVideoEncoderCriticalError(codecErrors);
         }
      }

      this.mediaCodec = null;
      this.mediaCodecThread = null;
      if (this.drawer != null) {
         this.drawer.release();
         this.drawer = null;
      }

      if (this.eglBase != null) {
         this.eglBase.release();
         this.eglBase = null;
      }

      if (this.inputSurface != null) {
         this.inputSurface.release();
         this.inputSurface = null;
      }

      runningInstance = null;
      Logging.d("MediaCodecVideoEncoder", "Java releaseEncoder done");
   }

   private boolean setRates(int kbps, int frameRate) {
      this.checkOnMediaCodecThread();
      int codecBitrateBps = 1000 * kbps;
      if (this.bitrateAdjustmentType == BitrateAdjustmentType.DYNAMIC_ADJUSTMENT) {
         this.bitrateAccumulatorMax = (double)codecBitrateBps / 8.0;
         if (this.targetBitrateBps > 0 && codecBitrateBps < this.targetBitrateBps) {
            this.bitrateAccumulator = this.bitrateAccumulator * (double)codecBitrateBps / (double)this.targetBitrateBps;
         }
      }

      this.targetBitrateBps = codecBitrateBps;
      this.targetFps = frameRate;
      if (this.bitrateAdjustmentType == BitrateAdjustmentType.FRAMERATE_ADJUSTMENT && this.targetFps > 0) {
         codecBitrateBps = 30 * this.targetBitrateBps / this.targetFps;
         Logging.v("MediaCodecVideoEncoder", "setRates: " + kbps + " -> " + codecBitrateBps / 1000 + " kbps. Fps: " + this.targetFps);
      } else if (this.bitrateAdjustmentType == BitrateAdjustmentType.DYNAMIC_ADJUSTMENT) {
         Logging.v("MediaCodecVideoEncoder", "setRates: " + kbps + " kbps. Fps: " + this.targetFps + ". ExpScale: " + this.bitrateAdjustmentScaleExp);
         if (this.bitrateAdjustmentScaleExp != 0) {
            codecBitrateBps = (int)((double)codecBitrateBps * this.getBitrateScale(this.bitrateAdjustmentScaleExp));
         }
      } else {
         Logging.v("MediaCodecVideoEncoder", "setRates: " + kbps + " kbps. Fps: " + this.targetFps);
      }

      try {
         Bundle params = new Bundle();
         params.putInt("video-bitrate", codecBitrateBps);
         this.mediaCodec.setParameters(params);
         return true;
      } catch (IllegalStateException var5) {
         IllegalStateException e = var5;
         Logging.e("MediaCodecVideoEncoder", "setRates failed", e);
         return false;
      }
   }

   int dequeueInputBuffer() {
      this.checkOnMediaCodecThread();

      try {
         return this.mediaCodec.dequeueInputBuffer(0L);
      } catch (IllegalStateException var2) {
         IllegalStateException e = var2;
         Logging.e("MediaCodecVideoEncoder", "dequeueIntputBuffer failed", e);
         return -2;
      }
   }

   OutputBufferInfo dequeueOutputBuffer() {
      this.checkOnMediaCodecThread();

      try {
         MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
         int result = this.mediaCodec.dequeueOutputBuffer(info, 0L);
         if (result >= 0) {
            boolean isConfigFrame = (info.flags & 2) != 0;
            if (isConfigFrame) {
               Logging.d("MediaCodecVideoEncoder", "Config frame generated. Offset: " + info.offset + ". Size: " + info.size);
               this.configData = ByteBuffer.allocateDirect(info.size);
               this.outputBuffers[result].position(info.offset);
               this.outputBuffers[result].limit(info.offset + info.size);
               this.configData.put(this.outputBuffers[result]);
               this.mediaCodec.releaseOutputBuffer(result, false);
               result = this.mediaCodec.dequeueOutputBuffer(info, 0L);
            }
         }

         if (result >= 0) {
            ByteBuffer outputBuffer = this.outputBuffers[result].duplicate();
            outputBuffer.position(info.offset);
            outputBuffer.limit(info.offset + info.size);
            this.reportEncodedFrame(info.size);
            boolean isKeyFrame = (info.flags & 1) != 0;
            if (isKeyFrame) {
               Logging.d("MediaCodecVideoEncoder", "Sync frame generated");
            }

            if (isKeyFrame && this.type == VideoCodecType.VIDEO_CODEC_H264) {
               Logging.d("MediaCodecVideoEncoder", "Appending config frame of size " + this.configData.capacity() + " to output buffer with offset " + info.offset + ", size " + info.size);
               ByteBuffer keyFrameBuffer = ByteBuffer.allocateDirect(this.configData.capacity() + info.size);
               this.configData.rewind();
               keyFrameBuffer.put(this.configData);
               keyFrameBuffer.put(outputBuffer);
               keyFrameBuffer.position(0);
               return new OutputBufferInfo(result, keyFrameBuffer, isKeyFrame, info.presentationTimeUs);
            } else {
               return new OutputBufferInfo(result, outputBuffer.slice(), isKeyFrame, info.presentationTimeUs);
            }
         } else if (result == -3) {
            this.outputBuffers = this.mediaCodec.getOutputBuffers();
            return this.dequeueOutputBuffer();
         } else if (result == -2) {
            return this.dequeueOutputBuffer();
         } else if (result == -1) {
            return null;
         } else {
            throw new RuntimeException("dequeueOutputBuffer: " + result);
         }
      } catch (IllegalStateException var6) {
         IllegalStateException e = var6;
         Logging.e("MediaCodecVideoEncoder", "dequeueOutputBuffer failed", e);
         return new OutputBufferInfo(-1, (ByteBuffer)null, false, -1L);
      }
   }

   private double getBitrateScale(int bitrateAdjustmentScaleExp) {
      return Math.pow(2.0, (double)bitrateAdjustmentScaleExp / 10.0);
   }

   private void reportEncodedFrame(int size) {
      if (this.targetFps != 0 && this.bitrateAdjustmentType == BitrateAdjustmentType.DYNAMIC_ADJUSTMENT) {
         double expectedBytesPerFrame = (double)this.targetBitrateBps / (8.0 * (double)this.targetFps);
         this.bitrateAccumulator += (double)size - expectedBytesPerFrame;
         this.bitrateObservationTimeMs += 1000.0 / (double)this.targetFps;
         double bitrateAccumulatorCap = 3.0 * this.bitrateAccumulatorMax;
         this.bitrateAccumulator = Math.min(this.bitrateAccumulator, bitrateAccumulatorCap);
         this.bitrateAccumulator = Math.max(this.bitrateAccumulator, -bitrateAccumulatorCap);
         if (this.bitrateObservationTimeMs > 3000.0) {
            Logging.d("MediaCodecVideoEncoder", "Acc: " + (int)this.bitrateAccumulator + ". Max: " + (int)this.bitrateAccumulatorMax + ". ExpScale: " + this.bitrateAdjustmentScaleExp);
            boolean bitrateAdjustmentScaleChanged = false;
            if (this.bitrateAccumulator > this.bitrateAccumulatorMax) {
               this.bitrateAccumulator = this.bitrateAccumulatorMax;
               --this.bitrateAdjustmentScaleExp;
               bitrateAdjustmentScaleChanged = true;
            } else if (this.bitrateAccumulator < -this.bitrateAccumulatorMax) {
               ++this.bitrateAdjustmentScaleExp;
               this.bitrateAccumulator = -this.bitrateAccumulatorMax;
               bitrateAdjustmentScaleChanged = true;
            }

            if (bitrateAdjustmentScaleChanged) {
               this.bitrateAdjustmentScaleExp = Math.min(this.bitrateAdjustmentScaleExp, 10);
               this.bitrateAdjustmentScaleExp = Math.max(this.bitrateAdjustmentScaleExp, -10);
               Logging.d("MediaCodecVideoEncoder", "Adjusting bitrate scale to " + this.bitrateAdjustmentScaleExp + ". Value: " + this.getBitrateScale(this.bitrateAdjustmentScaleExp));
               this.setRates(this.targetBitrateBps / 1000, this.targetFps);
            }

            this.bitrateObservationTimeMs = 0.0;
         }

      }
   }

   boolean releaseOutputBuffer(int index) {
      this.checkOnMediaCodecThread();

      try {
         this.mediaCodec.releaseOutputBuffer(index, false);
         return true;
      } catch (IllegalStateException var3) {
         IllegalStateException e = var3;
         Logging.e("MediaCodecVideoEncoder", "releaseOutputBuffer failed", e);
         return false;
      }
   }

   static {
      qcomVp8HwProperties = new MediaCodecProperties("OMX.qcom.", 19, BitrateAdjustmentType.NO_ADJUSTMENT);
      exynosVp8HwProperties = new MediaCodecProperties("OMX.Exynos.", 23, BitrateAdjustmentType.DYNAMIC_ADJUSTMENT);
      vp8HwList = new MediaCodecProperties[]{qcomVp8HwProperties, exynosVp8HwProperties};
      qcomVp9HwProperties = new MediaCodecProperties("OMX.qcom.", 23, BitrateAdjustmentType.NO_ADJUSTMENT);
      exynosVp9HwProperties = new MediaCodecProperties("OMX.Exynos.", 23, BitrateAdjustmentType.NO_ADJUSTMENT);
      vp9HwList = new MediaCodecProperties[]{qcomVp9HwProperties, exynosVp9HwProperties};
      qcomH264HwProperties = new MediaCodecProperties("OMX.qcom.", 19, BitrateAdjustmentType.NO_ADJUSTMENT);
      exynosH264HwProperties = new MediaCodecProperties("OMX.Exynos.", 21, BitrateAdjustmentType.FRAMERATE_ADJUSTMENT);
      h264HwList = new MediaCodecProperties[]{qcomH264HwProperties, exynosH264HwProperties};
      H264_HW_EXCEPTION_MODELS = new String[]{"SAMSUNG-SGH-I337", "Nexus 7", "Nexus 4"};
      supportedColorList = new int[]{19, 21, 2141391872, 2141391876};
      supportedSurfaceColorList = new int[]{2130708361};
   }

   static class OutputBufferInfo {
      public final int index;
      public final ByteBuffer buffer;
      public final boolean isKeyFrame;
      public final long presentationTimestampUs;

      public OutputBufferInfo(int index, ByteBuffer buffer, boolean isKeyFrame, long presentationTimestampUs) {
         this.index = index;
         this.buffer = buffer;
         this.isKeyFrame = isKeyFrame;
         this.presentationTimestampUs = presentationTimestampUs;
      }
   }

   private static class EncoderProperties {
      public final String codecName;
      public final int colorFormat;
      public final BitrateAdjustmentType bitrateAdjustmentType;

      public EncoderProperties(String codecName, int colorFormat, BitrateAdjustmentType bitrateAdjustmentType) {
         this.codecName = codecName;
         this.colorFormat = colorFormat;
         this.bitrateAdjustmentType = bitrateAdjustmentType;
      }
   }

   public interface MediaCodecVideoEncoderErrorCallback {
      void onMediaCodecVideoEncoderCriticalError(int var1);
   }

   private static class MediaCodecProperties {
      public final String codecPrefix;
      public final int minSdk;
      public final BitrateAdjustmentType bitrateAdjustmentType;

      MediaCodecProperties(String codecPrefix, int minSdk, BitrateAdjustmentType bitrateAdjustmentType) {
         this.codecPrefix = codecPrefix;
         this.minSdk = minSdk;
         this.bitrateAdjustmentType = bitrateAdjustmentType;
      }
   }

   public static enum BitrateAdjustmentType {
      NO_ADJUSTMENT,
      FRAMERATE_ADJUSTMENT,
      DYNAMIC_ADJUSTMENT;
   }

   public static enum VideoCodecType {
      VIDEO_CODEC_VP8,
      VIDEO_CODEC_VP9,
      VIDEO_CODEC_H264;
   }
}
