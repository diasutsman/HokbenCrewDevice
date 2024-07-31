package org.webrtc2.voiceengine;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Build;
import java.util.Timer;
import java.util.TimerTask;

import org.webrtc2.Logging;

public class WebRtcAudioManager {
   private static final boolean DEBUG = false;
   private static final String TAG = "WebRtcAudioManager";
   private static boolean useStereoOutput = false;
   private static boolean useStereoInput = false;
   private static boolean blacklistDeviceForOpenSLESUsage = false;
   private static boolean blacklistDeviceForOpenSLESUsageIsOverridden = false;
   private static final int BITS_PER_SAMPLE = 16;
   private static final int DEFAULT_FRAME_PER_BUFFER = 256;
   private static final String[] AUDIO_MODES = new String[]{"MODE_NORMAL", "MODE_RINGTONE", "MODE_IN_CALL", "MODE_IN_COMMUNICATION"};
   private final long nativeAudioManager;
   private final Context context;
   private final AudioManager audioManager;
   private boolean initialized = false;
   private int nativeSampleRate;
   private int nativeChannels;
   private boolean hardwareAEC;
   private boolean hardwareAGC;
   private boolean hardwareNS;
   private boolean lowLatencyOutput;
   private boolean lowLatencyInput;
   private boolean proAudio;
   private int sampleRate;
   private int outputChannels;
   private int inputChannels;
   private int outputBufferSize;
   private int inputBufferSize;
   private final VolumeLogger volumeLogger;

   public static synchronized void setBlacklistDeviceForOpenSLESUsage(boolean enable) {
      blacklistDeviceForOpenSLESUsageIsOverridden = true;
      blacklistDeviceForOpenSLESUsage = enable;
   }

   public static synchronized void setStereoOutput(boolean enable) {
      org.webrtc2.Logging.w("WebRtcAudioManager", "Overriding default output behavior: setStereoOutput(" + enable + ')');
      useStereoOutput = enable;
   }

   public static synchronized void setStereoInput(boolean enable) {
      org.webrtc2.Logging.w("WebRtcAudioManager", "Overriding default input behavior: setStereoInput(" + enable + ')');
      useStereoInput = enable;
   }

   public static synchronized boolean getStereoOutput() {
      return useStereoOutput;
   }

   public static synchronized boolean getStereoInput() {
      return useStereoInput;
   }

   WebRtcAudioManager(Context context, long nativeAudioManager) {
      org.webrtc2.Logging.d("WebRtcAudioManager", "ctor" + org.webrtc2.voiceengine.WebRtcAudioUtils.getThreadInfo());
      this.context = context;
      this.nativeAudioManager = nativeAudioManager;
      this.audioManager = (AudioManager)context.getSystemService("audio");
      this.volumeLogger = new VolumeLogger(this.audioManager);
      this.storeAudioParameters();
      this.nativeCacheAudioParameters(this.sampleRate, this.outputChannels, this.inputChannels, this.hardwareAEC, this.hardwareAGC, this.hardwareNS, this.lowLatencyOutput, this.lowLatencyInput, this.proAudio, this.outputBufferSize, this.inputBufferSize, nativeAudioManager);
   }

   private boolean init() {
      org.webrtc2.Logging.d("WebRtcAudioManager", "init" + org.webrtc2.voiceengine.WebRtcAudioUtils.getThreadInfo());
      if (this.initialized) {
         return true;
      } else {
         org.webrtc2.Logging.d("WebRtcAudioManager", "audio mode is: " + AUDIO_MODES[this.audioManager.getMode()]);
         this.initialized = true;
         this.volumeLogger.start();
         return true;
      }
   }

   private void dispose() {
      org.webrtc2.Logging.d("WebRtcAudioManager", "dispose" + org.webrtc2.voiceengine.WebRtcAudioUtils.getThreadInfo());
      if (this.initialized) {
         this.volumeLogger.stop();
      }
   }

   private boolean isCommunicationModeEnabled() {
      return this.audioManager.getMode() == 3;
   }

   private boolean isDeviceBlacklistedForOpenSLESUsage() {
      boolean blacklisted = blacklistDeviceForOpenSLESUsageIsOverridden ? blacklistDeviceForOpenSLESUsage : org.webrtc2.voiceengine.WebRtcAudioUtils.deviceIsBlacklistedForOpenSLESUsage();
      if (blacklisted) {
         org.webrtc2.Logging.e("WebRtcAudioManager", Build.MODEL + " is blacklisted for OpenSL ES usage!");
      }

      return blacklisted;
   }

   private void storeAudioParameters() {
      this.outputChannels = getStereoOutput() ? 2 : 1;
      this.inputChannels = getStereoInput() ? 2 : 1;
      this.sampleRate = this.getNativeOutputSampleRate();
      this.hardwareAEC = isAcousticEchoCancelerSupported();
      this.hardwareAGC = false;
      this.hardwareNS = isNoiseSuppressorSupported();
      this.lowLatencyOutput = this.isLowLatencyOutputSupported();
      this.lowLatencyInput = this.isLowLatencyInputSupported();
      this.proAudio = this.isProAudioSupported();
      this.outputBufferSize = this.lowLatencyOutput ? this.getLowLatencyOutputFramesPerBuffer() : getMinOutputFrameSize(this.sampleRate, this.outputChannels);
      this.inputBufferSize = this.lowLatencyInput ? this.getLowLatencyInputFramesPerBuffer() : getMinInputFrameSize(this.sampleRate, this.inputChannels);
   }

   private boolean hasEarpiece() {
      return this.context.getPackageManager().hasSystemFeature("android.hardware.telephony");
   }

   private boolean isLowLatencyOutputSupported() {
      return isOpenSLESSupported() && this.context.getPackageManager().hasSystemFeature("android.hardware.audio.low_latency");
   }

   public boolean isLowLatencyInputSupported() {
      return org.webrtc2.voiceengine.WebRtcAudioUtils.runningOnLollipopOrHigher() && this.isLowLatencyOutputSupported();
   }

   private boolean isProAudioSupported() {
      return org.webrtc2.voiceengine.WebRtcAudioUtils.runningOnMarshmallowOrHigher() && this.context.getPackageManager().hasSystemFeature("android.hardware.audio.pro");
   }

   private int getNativeOutputSampleRate() {
      if (org.webrtc2.voiceengine.WebRtcAudioUtils.runningOnEmulator()) {
         org.webrtc2.Logging.d("WebRtcAudioManager", "Running emulator, overriding sample rate to 8 kHz.");
         return 8000;
      } else if (org.webrtc2.voiceengine.WebRtcAudioUtils.isDefaultSampleRateOverridden()) {
         org.webrtc2.Logging.d("WebRtcAudioManager", "Default sample rate is overriden to " + org.webrtc2.voiceengine.WebRtcAudioUtils.getDefaultSampleRateHz() + " Hz");
         return org.webrtc2.voiceengine.WebRtcAudioUtils.getDefaultSampleRateHz();
      } else {
         int sampleRateHz;
         if (org.webrtc2.voiceengine.WebRtcAudioUtils.runningOnJellyBeanMR1OrHigher()) {
            sampleRateHz = this.getSampleRateOnJellyBeanMR10OrHigher();
         } else {
            sampleRateHz = org.webrtc2.voiceengine.WebRtcAudioUtils.getDefaultSampleRateHz();
         }

         org.webrtc2.Logging.d("WebRtcAudioManager", "Sample rate is set to " + sampleRateHz + " Hz");
         return sampleRateHz;
      }
   }

   @TargetApi(17)
   private int getSampleRateOnJellyBeanMR10OrHigher() {
      String sampleRateString = this.audioManager.getProperty("android.media.property.OUTPUT_SAMPLE_RATE");
      return sampleRateString == null ? org.webrtc2.voiceengine.WebRtcAudioUtils.getDefaultSampleRateHz() : Integer.parseInt(sampleRateString);
   }

   @TargetApi(17)
   private int getLowLatencyOutputFramesPerBuffer() {
      assertTrue(this.isLowLatencyOutputSupported());
      if (!org.webrtc2.voiceengine.WebRtcAudioUtils.runningOnJellyBeanMR1OrHigher()) {
         return 256;
      } else {
         String framesPerBuffer = this.audioManager.getProperty("android.media.property.OUTPUT_FRAMES_PER_BUFFER");
         return framesPerBuffer == null ? 256 : Integer.parseInt(framesPerBuffer);
      }
   }

   private static boolean isAcousticEchoCancelerSupported() {
      return org.webrtc2.voiceengine.WebRtcAudioEffects.canUseAcousticEchoCanceler();
   }

   private static boolean isNoiseSuppressorSupported() {
      return WebRtcAudioEffects.canUseNoiseSuppressor();
   }

   private static int getMinOutputFrameSize(int sampleRateInHz, int numChannels) {
      int bytesPerFrame = numChannels * 2;
      int channelConfig = numChannels == 1 ? 4 : 12;
      return AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, 2) / bytesPerFrame;
   }

   private int getLowLatencyInputFramesPerBuffer() {
      assertTrue(this.isLowLatencyInputSupported());
      return this.getLowLatencyOutputFramesPerBuffer();
   }

   private static int getMinInputFrameSize(int sampleRateInHz, int numChannels) {
      int bytesPerFrame = numChannels * 2;
      int channelConfig = numChannels == 1 ? 16 : 12;
      return AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, 2) / bytesPerFrame;
   }

   private static boolean isOpenSLESSupported() {
      return WebRtcAudioUtils.runningOnGingerBreadOrHigher();
   }

   private static void assertTrue(boolean condition) {
      if (!condition) {
         throw new AssertionError("Expected condition to be true");
      }
   }

   private native void nativeCacheAudioParameters(int var1, int var2, int var3, boolean var4, boolean var5, boolean var6, boolean var7, boolean var8, boolean var9, int var10, int var11, long var12);

   private static class VolumeLogger {
      private static final String THREAD_NAME = "WebRtcVolumeLevelLoggerThread";
      private static final int TIMER_PERIOD_IN_SECONDS = 10;
      private final AudioManager audioManager;
      private Timer timer;

      public VolumeLogger(AudioManager audioManager) {
         this.audioManager = audioManager;
      }

      public void start() {
         this.timer = new Timer("WebRtcVolumeLevelLoggerThread");
         this.timer.schedule(new LogVolumeTask(this.audioManager.getStreamMaxVolume(2), this.audioManager.getStreamMaxVolume(0)), 0L, 10000L);
      }

      private void stop() {
         if (this.timer != null) {
            this.timer.cancel();
            this.timer = null;
         }

      }

      private class LogVolumeTask extends TimerTask {
         private final int maxRingVolume;
         private final int maxVoiceCallVolume;

         LogVolumeTask(int maxRingVolume, int maxVoiceCallVolume) {
            this.maxRingVolume = maxRingVolume;
            this.maxVoiceCallVolume = maxVoiceCallVolume;
         }

         public void run() {
            int mode = VolumeLogger.this.audioManager.getMode();
            if (mode == 1) {
               org.webrtc2.Logging.d("WebRtcAudioManager", "STREAM_RING stream volume: " + VolumeLogger.this.audioManager.getStreamVolume(2) + " (max=" + this.maxRingVolume + ")");
            } else if (mode == 3) {
               Logging.d("WebRtcAudioManager", "VOICE_CALL stream volume: " + VolumeLogger.this.audioManager.getStreamVolume(0) + " (max=" + this.maxVoiceCallVolume + ")");
            }

         }
      }
   }
}
