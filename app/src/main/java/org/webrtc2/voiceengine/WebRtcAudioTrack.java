package org.webrtc2.voiceengine;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Process;

import org.webrtc2.Logging;

import java.nio.ByteBuffer;

public class WebRtcAudioTrack {
   private static final boolean DEBUG = false;
   private static final String TAG = "WebRtcAudioTrack";
   private static final int BITS_PER_SAMPLE = 16;
   private static final int CALLBACK_BUFFER_SIZE_MS = 10;
   private static final int BUFFERS_PER_SECOND = 100;
   private final Context context;
   private final long nativeAudioTrack;
   private final AudioManager audioManager;
   private ByteBuffer byteBuffer;
   private AudioTrack audioTrack = null;
   private AudioTrackThread audioThread = null;
   private static volatile boolean speakerMute = false;
   private byte[] emptyBytes;

   WebRtcAudioTrack(Context context, long nativeAudioTrack) {
      org.webrtc2.Logging.d("WebRtcAudioTrack", "ctor" + WebRtcAudioUtils.getThreadInfo());
      this.context = context;
      this.nativeAudioTrack = nativeAudioTrack;
      this.audioManager = (AudioManager)context.getSystemService("audio");
   }

   private boolean initPlayout(int sampleRate, int channels) {
      org.webrtc2.Logging.d("WebRtcAudioTrack", "initPlayout(sampleRate=" + sampleRate + ", channels=" + channels + ")");
      int bytesPerFrame = channels * 2;
      ByteBuffer var10001 = this.byteBuffer;
      this.byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * (sampleRate / 100));
      org.webrtc2.Logging.d("WebRtcAudioTrack", "byteBuffer.capacity: " + this.byteBuffer.capacity());
      this.emptyBytes = new byte[this.byteBuffer.capacity()];
      this.nativeCacheDirectBufferAddress(this.byteBuffer, this.nativeAudioTrack);
      int channelConfig = this.channelCountToConfiguration(channels);
      int minBufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRate, channelConfig, 2);
      org.webrtc2.Logging.d("WebRtcAudioTrack", "AudioTrack.getMinBufferSize: " + minBufferSizeInBytes);
      if (minBufferSizeInBytes < this.byteBuffer.capacity()) {
         org.webrtc2.Logging.e("WebRtcAudioTrack", "AudioTrack.getMinBufferSize returns an invalid value.");
         return false;
      } else if (this.audioTrack != null) {
         org.webrtc2.Logging.e("WebRtcAudioTrack", "Conflict with existing AudioTrack.");
         return false;
      } else {
         try {
            this.audioTrack = new AudioTrack(0, sampleRate, channelConfig, 2, minBufferSizeInBytes, 1);
         } catch (IllegalArgumentException var7) {
            IllegalArgumentException e = var7;
            org.webrtc2.Logging.d("WebRtcAudioTrack", e.getMessage());
            this.releaseAudioResources();
            return false;
         }

         if (this.audioTrack != null && this.audioTrack.getState() == 1) {
            this.logMainParameters();
            this.logMainParametersExtended();
            return true;
         } else {
            org.webrtc2.Logging.e("WebRtcAudioTrack", "Initialization of audio track failed.");
            this.releaseAudioResources();
            return false;
         }
      }
   }

   private boolean startPlayout() {
      org.webrtc2.Logging.d("WebRtcAudioTrack", "startPlayout");
      assertTrue(this.audioTrack != null);
      assertTrue(this.audioThread == null);
      if (this.audioTrack.getState() != 1) {
         org.webrtc2.Logging.e("WebRtcAudioTrack", "AudioTrack instance is not successfully initialized.");
         return false;
      } else {
         this.audioThread = new AudioTrackThread("AudioTrackJavaThread");
         this.audioThread.start();
         return true;
      }
   }

   private boolean stopPlayout() {
      org.webrtc2.Logging.d("WebRtcAudioTrack", "stopPlayout");
      assertTrue(this.audioThread != null);
      this.logUnderrunCount();
      this.audioThread.joinThread();
      this.audioThread = null;
      this.releaseAudioResources();
      return true;
   }

   private int getStreamMaxVolume() {
      org.webrtc2.Logging.d("WebRtcAudioTrack", "getStreamMaxVolume");
      assertTrue(this.audioManager != null);
      return this.audioManager.getStreamMaxVolume(0);
   }

   private boolean setStreamVolume(int volume) {
      org.webrtc2.Logging.d("WebRtcAudioTrack", "setStreamVolume(" + volume + ")");
      assertTrue(this.audioManager != null);
      if (this.isVolumeFixed()) {
         org.webrtc2.Logging.e("WebRtcAudioTrack", "The device implements a fixed volume policy.");
         return false;
      } else {
         this.audioManager.setStreamVolume(0, volume, 0);
         return true;
      }
   }

   private boolean isVolumeFixed() {
      return !WebRtcAudioUtils.runningOnLollipopOrHigher() ? false : this.audioManager.isVolumeFixed();
   }

   private int getStreamVolume() {
      org.webrtc2.Logging.d("WebRtcAudioTrack", "getStreamVolume");
      assertTrue(this.audioManager != null);
      return this.audioManager.getStreamVolume(0);
   }

   private void logMainParameters() {
      StringBuilder var10001 = (new StringBuilder()).append("AudioTrack: session ID: ").append(this.audioTrack.getAudioSessionId()).append(", ").append("channels: ").append(this.audioTrack.getChannelCount()).append(", ").append("sample rate: ").append(this.audioTrack.getSampleRate()).append(", ").append("max gain: ");
      AudioTrack var10002 = this.audioTrack;
      org.webrtc2.Logging.d("WebRtcAudioTrack", var10001.append(AudioTrack.getMaxVolume()).toString());
   }

   private void logMainParametersExtended() {
      if (WebRtcAudioUtils.runningOnMarshmallowOrHigher()) {
         org.webrtc2.Logging.d("WebRtcAudioTrack", "AudioTrack: buffer size in frames: " + this.audioTrack.getBufferSizeInFrames());
      }

      if (WebRtcAudioUtils.runningOnNougatOrHigher()) {
         org.webrtc2.Logging.d("WebRtcAudioTrack", "AudioTrack: buffer capacity in frames: " + this.audioTrack.getBufferCapacityInFrames());
      }

   }

   private void logUnderrunCount() {
      if (WebRtcAudioUtils.runningOnNougatOrHigher()) {
         org.webrtc2.Logging.d("WebRtcAudioTrack", "underrun count: " + this.audioTrack.getUnderrunCount());
      }

   }

   private static void assertTrue(boolean condition) {
      if (!condition) {
         throw new AssertionError("Expected condition to be true");
      }
   }

   private int channelCountToConfiguration(int channels) {
      return channels == 1 ? 4 : 12;
   }

   private native void nativeCacheDirectBufferAddress(ByteBuffer var1, long var2);

   private native void nativeGetPlayoutData(int var1, long var2);

   public static void setSpeakerMute(boolean mute) {
      org.webrtc2.Logging.w("WebRtcAudioTrack", "setSpeakerMute(" + mute + ")");
      speakerMute = mute;
   }

   private void releaseAudioResources() {
      if (this.audioTrack != null) {
         this.audioTrack.release();
         this.audioTrack = null;
      }

   }

   private class AudioTrackThread extends Thread {
      private volatile boolean keepAlive = true;

      public AudioTrackThread(String name) {
         super(name);
      }

      public void run() {
         Process.setThreadPriority(-19);
         org.webrtc2.Logging.d("WebRtcAudioTrack", "AudioTrackThread" + WebRtcAudioUtils.getThreadInfo());

         try {
            WebRtcAudioTrack.this.audioTrack.play();
            WebRtcAudioTrack.assertTrue(WebRtcAudioTrack.this.audioTrack.getPlayState() == 3);
         } catch (IllegalStateException var4) {
            IllegalStateException e = var4;
            org.webrtc2.Logging.e("WebRtcAudioTrack", "AudioTrack.play failed: " + e.getMessage());
            WebRtcAudioTrack.this.releaseAudioResources();
            return;
         }

         for(int sizeInBytes = WebRtcAudioTrack.this.byteBuffer.capacity(); this.keepAlive; WebRtcAudioTrack.this.byteBuffer.rewind()) {
            WebRtcAudioTrack.this.nativeGetPlayoutData(sizeInBytes, WebRtcAudioTrack.this.nativeAudioTrack);
            WebRtcAudioTrack.assertTrue(sizeInBytes <= WebRtcAudioTrack.this.byteBuffer.remaining());
            if (WebRtcAudioTrack.speakerMute) {
               WebRtcAudioTrack.this.byteBuffer.clear();
               WebRtcAudioTrack.this.byteBuffer.put(WebRtcAudioTrack.this.emptyBytes);
               WebRtcAudioTrack.this.byteBuffer.position(0);
            }

//            int bytesWrittenx = false;
            int bytesWritten;
            if (WebRtcAudioUtils.runningOnLollipopOrHigher()) {
               bytesWritten = this.writeOnLollipop(WebRtcAudioTrack.this.audioTrack, WebRtcAudioTrack.this.byteBuffer, sizeInBytes);
            } else {
               bytesWritten = this.writePreLollipop(WebRtcAudioTrack.this.audioTrack, WebRtcAudioTrack.this.byteBuffer, sizeInBytes);
            }

            if (bytesWritten != sizeInBytes) {
               org.webrtc2.Logging.e("WebRtcAudioTrack", "AudioTrack.write failed: " + bytesWritten);
               if (bytesWritten == -3) {
                  this.keepAlive = false;
               }
            }
         }

         try {
            WebRtcAudioTrack.this.audioTrack.stop();
         } catch (IllegalStateException var3) {
            IllegalStateException ex = var3;
            Logging.e("WebRtcAudioTrack", "AudioTrack.stop failed: " + ex.getMessage());
         }

         WebRtcAudioTrack.assertTrue(WebRtcAudioTrack.this.audioTrack.getPlayState() == 1);
         WebRtcAudioTrack.this.audioTrack.flush();
      }

      private int writeOnLollipop(AudioTrack audioTrack, ByteBuffer byteBuffer, int sizeInBytes) {
         return audioTrack.write(byteBuffer, sizeInBytes, 0);
      }

      private int writePreLollipop(AudioTrack audioTrack, ByteBuffer byteBuffer, int sizeInBytes) {
         return audioTrack.write(byteBuffer.array(), byteBuffer.arrayOffset(), sizeInBytes);
      }

      public void joinThread() {
         this.keepAlive = false;

         while(this.isAlive()) {
            try {
               this.join();
            } catch (InterruptedException var2) {
            }
         }

      }
   }
}
