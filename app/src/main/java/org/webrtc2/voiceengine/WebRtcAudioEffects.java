package org.webrtc2.voiceengine;

import android.annotation.TargetApi;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;

import org.webrtc2.Logging;

import java.util.List;
import java.util.UUID;

class WebRtcAudioEffects {
   private static final boolean DEBUG = false;
   private static final String TAG = "WebRtcAudioEffects";
   private static final UUID AOSP_ACOUSTIC_ECHO_CANCELER = UUID.fromString("bb392ec0-8d4d-11e0-a896-0002a5d5c51b");
   private static final UUID AOSP_NOISE_SUPPRESSOR = UUID.fromString("c06c8400-8e06-11e0-9cb6-0002a5d5c51b");
   private static AudioEffect.Descriptor[] cachedEffects = null;
   private AcousticEchoCanceler aec = null;
   private NoiseSuppressor ns = null;
   private boolean shouldEnableAec = false;
   private boolean shouldEnableNs = false;

   public static boolean isAcousticEchoCancelerSupported() {
      return WebRtcAudioUtils.runningOnJellyBeanOrHigher() && isAcousticEchoCancelerEffectAvailable();
   }

   public static boolean isNoiseSuppressorSupported() {
      return WebRtcAudioUtils.runningOnJellyBeanOrHigher() && isNoiseSuppressorEffectAvailable();
   }

   public static boolean isAcousticEchoCancelerBlacklisted() {
      List<String> blackListedModels = WebRtcAudioUtils.getBlackListedModelsForAecUsage();
      boolean isBlacklisted = blackListedModels.contains(Build.MODEL);
      if (isBlacklisted) {
         org.webrtc2.Logging.w("WebRtcAudioEffects", Build.MODEL + " is blacklisted for HW AEC usage!");
      }

      return isBlacklisted;
   }

   public static boolean isNoiseSuppressorBlacklisted() {
      List<String> blackListedModels = WebRtcAudioUtils.getBlackListedModelsForNsUsage();
      boolean isBlacklisted = blackListedModels.contains(Build.MODEL);
      if (isBlacklisted) {
         org.webrtc2.Logging.w("WebRtcAudioEffects", Build.MODEL + " is blacklisted for HW NS usage!");
      }

      return isBlacklisted;
   }

   @TargetApi(18)
   private static boolean isAcousticEchoCancelerExcludedByUUID() {
      AudioEffect.Descriptor[] arr$ = getAvailableEffects();
      int len$ = arr$.length;

      for(int i$ = 0; i$ < len$; ++i$) {
         AudioEffect.Descriptor d = arr$[i$];
         if (d.type.equals(AudioEffect.EFFECT_TYPE_AEC) && d.uuid.equals(AOSP_ACOUSTIC_ECHO_CANCELER)) {
            return true;
         }
      }

      return false;
   }

   @TargetApi(18)
   private static boolean isNoiseSuppressorExcludedByUUID() {
      AudioEffect.Descriptor[] arr$ = getAvailableEffects();
      int len$ = arr$.length;

      for(int i$ = 0; i$ < len$; ++i$) {
         AudioEffect.Descriptor d = arr$[i$];
         if (d.type.equals(AudioEffect.EFFECT_TYPE_NS) && d.uuid.equals(AOSP_NOISE_SUPPRESSOR)) {
            return true;
         }
      }

      return false;
   }

   @TargetApi(18)
   private static boolean isAcousticEchoCancelerEffectAvailable() {
      return isEffectTypeAvailable(AudioEffect.EFFECT_TYPE_AEC);
   }

   @TargetApi(18)
   private static boolean isNoiseSuppressorEffectAvailable() {
      return isEffectTypeAvailable(AudioEffect.EFFECT_TYPE_NS);
   }

   public static boolean canUseAcousticEchoCanceler() {
      boolean canUseAcousticEchoCanceler = isAcousticEchoCancelerSupported() && !WebRtcAudioUtils.useWebRtcBasedAcousticEchoCanceler() && !isAcousticEchoCancelerBlacklisted() && !isAcousticEchoCancelerExcludedByUUID();
      org.webrtc2.Logging.d("WebRtcAudioEffects", "canUseAcousticEchoCanceler: " + canUseAcousticEchoCanceler);
      return canUseAcousticEchoCanceler;
   }

   public static boolean canUseNoiseSuppressor() {
      boolean canUseNoiseSuppressor = isNoiseSuppressorSupported() && !WebRtcAudioUtils.useWebRtcBasedNoiseSuppressor() && !isNoiseSuppressorBlacklisted() && !isNoiseSuppressorExcludedByUUID();
      org.webrtc2.Logging.d("WebRtcAudioEffects", "canUseNoiseSuppressor: " + canUseNoiseSuppressor);
      return canUseNoiseSuppressor;
   }

   static WebRtcAudioEffects create() {
      if (!WebRtcAudioUtils.runningOnJellyBeanOrHigher()) {
         org.webrtc2.Logging.w("WebRtcAudioEffects", "API level 16 or higher is required!");
         return null;
      } else {
         return new WebRtcAudioEffects();
      }
   }

   private WebRtcAudioEffects() {
      org.webrtc2.Logging.d("WebRtcAudioEffects", "ctor" + WebRtcAudioUtils.getThreadInfo());
   }

   public boolean setAEC(boolean enable) {
      org.webrtc2.Logging.d("WebRtcAudioEffects", "setAEC(" + enable + ")");
      if (!canUseAcousticEchoCanceler()) {
         org.webrtc2.Logging.w("WebRtcAudioEffects", "Platform AEC is not supported");
         this.shouldEnableAec = false;
         return false;
      } else if (this.aec != null && enable != this.shouldEnableAec) {
         org.webrtc2.Logging.e("WebRtcAudioEffects", "Platform AEC state can't be modified while recording");
         return false;
      } else {
         this.shouldEnableAec = enable;
         return true;
      }
   }

   public boolean setNS(boolean enable) {
      org.webrtc2.Logging.d("WebRtcAudioEffects", "setNS(" + enable + ")");
      if (!canUseNoiseSuppressor()) {
         org.webrtc2.Logging.w("WebRtcAudioEffects", "Platform NS is not supported");
         this.shouldEnableNs = false;
         return false;
      } else if (this.ns != null && enable != this.shouldEnableNs) {
         org.webrtc2.Logging.e("WebRtcAudioEffects", "Platform NS state can't be modified while recording");
         return false;
      } else {
         this.shouldEnableNs = enable;
         return true;
      }
   }

   public void enable(int audioSession) {
      org.webrtc2.Logging.d("WebRtcAudioEffects", "enable(audioSession=" + audioSession + ")");
      assertTrue(this.aec == null);
      assertTrue(this.ns == null);
      AudioEffect.Descriptor[] arr$ = AudioEffect.queryEffects();
      int len$ = arr$.length;

      for(int i$ = 0; i$ < len$; ++i$) {
         AudioEffect.Descriptor d = arr$[i$];
         if (this.effectTypeIsVoIP(d.type)) {
            org.webrtc2.Logging.d("WebRtcAudioEffects", "name: " + d.name + ", " + "mode: " + d.connectMode + ", " + "implementor: " + d.implementor + ", " + "UUID: " + d.uuid);
         }
      }

      boolean enabled;
      boolean enable;
      if (isAcousticEchoCancelerSupported()) {
         this.aec = AcousticEchoCanceler.create(audioSession);
         if (this.aec != null) {
            enabled = this.aec.getEnabled();
            enable = this.shouldEnableAec && canUseAcousticEchoCanceler();
            if (this.aec.setEnabled(enable) != 0) {
               org.webrtc2.Logging.e("WebRtcAudioEffects", "Failed to set the AcousticEchoCanceler state");
            }

            org.webrtc2.Logging.d("WebRtcAudioEffects", "AcousticEchoCanceler: was " + (enabled ? "enabled" : "disabled") + ", enable: " + enable + ", is now: " + (this.aec.getEnabled() ? "enabled" : "disabled"));
         } else {
            org.webrtc2.Logging.e("WebRtcAudioEffects", "Failed to create the AcousticEchoCanceler instance");
         }
      }

      if (isNoiseSuppressorSupported()) {
         this.ns = NoiseSuppressor.create(audioSession);
         if (this.ns != null) {
            enabled = this.ns.getEnabled();
            enable = this.shouldEnableNs && canUseNoiseSuppressor();
            if (this.ns.setEnabled(enable) != 0) {
               org.webrtc2.Logging.e("WebRtcAudioEffects", "Failed to set the NoiseSuppressor state");
            }

            org.webrtc2.Logging.d("WebRtcAudioEffects", "NoiseSuppressor: was " + (enabled ? "enabled" : "disabled") + ", enable: " + enable + ", is now: " + (this.ns.getEnabled() ? "enabled" : "disabled"));
         } else {
            org.webrtc2.Logging.e("WebRtcAudioEffects", "Failed to create the NoiseSuppressor instance");
         }
      }

   }

   public void release() {
      Logging.d("WebRtcAudioEffects", "release");
      if (this.aec != null) {
         this.aec.release();
         this.aec = null;
      }

      if (this.ns != null) {
         this.ns.release();
         this.ns = null;
      }

   }

   @TargetApi(18)
   private boolean effectTypeIsVoIP(UUID type) {
      if (!WebRtcAudioUtils.runningOnJellyBeanMR2OrHigher()) {
         return false;
      } else {
         return AudioEffect.EFFECT_TYPE_AEC.equals(type) && isAcousticEchoCancelerSupported() || AudioEffect.EFFECT_TYPE_NS.equals(type) && isNoiseSuppressorSupported();
      }
   }

   private static void assertTrue(boolean condition) {
      if (!condition) {
         throw new AssertionError("Expected condition to be true");
      }
   }

   private static AudioEffect.Descriptor[] getAvailableEffects() {
      if (cachedEffects != null) {
         return cachedEffects;
      } else {
         cachedEffects = AudioEffect.queryEffects();
         return cachedEffects;
      }
   }

   private static boolean isEffectTypeAvailable(UUID effectType) {
      AudioEffect.Descriptor[] effects = getAvailableEffects();
      if (effects == null) {
         return false;
      } else {
         AudioEffect.Descriptor[] arr$ = effects;
         int len$ = arr$.length;

         for(int i$ = 0; i$ < len$; ++i$) {
            AudioEffect.Descriptor d = arr$[i$];
            if (d.type.equals(effectType)) {
               return true;
            }
         }

         return false;
      }
   }
}
