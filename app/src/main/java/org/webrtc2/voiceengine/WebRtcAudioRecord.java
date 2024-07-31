package org.webrtc2.voiceengine;

import android.content.Context;
import android.media.AudioRecord;
import android.os.Process;
import java.nio.ByteBuffer;

import org.webrtc2.Logging;
import org.webrtc2.ThreadUtils;

public class WebRtcAudioRecord {
   private static final boolean DEBUG = false;
   private static final String TAG = "WebRtcAudioRecord";
   private static final int BITS_PER_SAMPLE = 16;
   private static final int CALLBACK_BUFFER_SIZE_MS = 10;
   private static final int BUFFERS_PER_SECOND = 100;
   private static final int BUFFER_SIZE_FACTOR = 2;
   private static final long AUDIO_RECORD_THREAD_JOIN_TIMEOUT_MS = 2000L;
   private final long nativeAudioRecord;
   private final Context context;
   private WebRtcAudioEffects effects = null;
   private ByteBuffer byteBuffer;
   private AudioRecord audioRecord = null;
   private AudioRecordThread audioThread = null;
   private static volatile boolean microphoneMute = false;
   private byte[] emptyBytes;

   WebRtcAudioRecord(Context context, long nativeAudioRecord) {
      org.webrtc2.Logging.d("WebRtcAudioRecord", "ctor" + WebRtcAudioUtils.getThreadInfo());
      this.context = context;
      this.nativeAudioRecord = nativeAudioRecord;
      this.effects = WebRtcAudioEffects.create();
   }

   private boolean enableBuiltInAEC(boolean enable) {
      org.webrtc2.Logging.d("WebRtcAudioRecord", "enableBuiltInAEC(" + enable + ')');
      if (this.effects == null) {
         org.webrtc2.Logging.e("WebRtcAudioRecord", "Built-in AEC is not supported on this platform");
         return false;
      } else {
         return this.effects.setAEC(enable);
      }
   }

   private boolean enableBuiltInNS(boolean enable) {
      org.webrtc2.Logging.d("WebRtcAudioRecord", "enableBuiltInNS(" + enable + ')');
      if (this.effects == null) {
         org.webrtc2.Logging.e("WebRtcAudioRecord", "Built-in NS is not supported on this platform");
         return false;
      } else {
         return this.effects.setNS(enable);
      }
   }

   private int initRecording(int sampleRate, int channels) {
      org.webrtc2.Logging.d("WebRtcAudioRecord", "initRecording(sampleRate=" + sampleRate + ", channels=" + channels + ")");
      if (!WebRtcAudioUtils.hasPermission(this.context, "android.permission.RECORD_AUDIO")) {
         org.webrtc2.Logging.e("WebRtcAudioRecord", "RECORD_AUDIO permission is missing");
         return -1;
      } else if (this.audioRecord != null) {
         org.webrtc2.Logging.e("WebRtcAudioRecord", "InitRecording() called twice without StopRecording()");
         return -1;
      } else {
         int bytesPerFrame = channels * 2;
         int framesPerBuffer = sampleRate / 100;
         this.byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * framesPerBuffer);
         org.webrtc2.Logging.d("WebRtcAudioRecord", "byteBuffer.capacity: " + this.byteBuffer.capacity());
         this.emptyBytes = new byte[this.byteBuffer.capacity()];
         this.nativeCacheDirectBufferAddress(this.byteBuffer, this.nativeAudioRecord);
         int channelConfig = this.channelCountToConfiguration(channels);
         int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, 2);
         if (minBufferSize != -1 && minBufferSize != -2) {
            org.webrtc2.Logging.d("WebRtcAudioRecord", "AudioRecord.getMinBufferSize: " + minBufferSize);
            int bufferSizeInBytes = Math.max(2 * minBufferSize, this.byteBuffer.capacity());
            org.webrtc2.Logging.d("WebRtcAudioRecord", "bufferSizeInBytes: " + bufferSizeInBytes);

            try {
               this.audioRecord = new AudioRecord(7, sampleRate, channelConfig, 2, bufferSizeInBytes);
            } catch (IllegalArgumentException var9) {
               IllegalArgumentException e = var9;
               org.webrtc2.Logging.e("WebRtcAudioRecord", e.getMessage());
               this.releaseAudioResources();
               return -1;
            }

            if (this.audioRecord != null && this.audioRecord.getState() == 1) {
               if (this.effects != null) {
                  this.effects.enable(this.audioRecord.getAudioSessionId());
               }

               this.logMainParameters();
               this.logMainParametersExtended();
               return framesPerBuffer;
            } else {
               org.webrtc2.Logging.e("WebRtcAudioRecord", "Failed to create a new AudioRecord instance");
               this.releaseAudioResources();
               return -1;
            }
         } else {
            org.webrtc2.Logging.e("WebRtcAudioRecord", "AudioRecord.getMinBufferSize failed: " + minBufferSize);
            return -1;
         }
      }
   }

   private boolean startRecording() {
      org.webrtc2.Logging.d("WebRtcAudioRecord", "startRecording");
      assertTrue(this.audioRecord != null);
      assertTrue(this.audioThread == null);

      try {
         this.audioRecord.startRecording();
      } catch (IllegalStateException var2) {
         IllegalStateException e = var2;
         org.webrtc2.Logging.e("WebRtcAudioRecord", "AudioRecord.startRecording failed: " + e.getMessage());
         return false;
      }

      if (this.audioRecord.getRecordingState() != 3) {
         org.webrtc2.Logging.e("WebRtcAudioRecord", "AudioRecord.startRecording failed");
         return false;
      } else {
         this.audioThread = new AudioRecordThread("AudioRecordJavaThread");
         this.audioThread.start();
         return true;
      }
   }

   private boolean stopRecording() {
      org.webrtc2.Logging.d("WebRtcAudioRecord", "stopRecording");
      assertTrue(this.audioThread != null);
      this.audioThread.stopThread();
      if (!ThreadUtils.joinUninterruptibly(this.audioThread, 2000L)) {
         org.webrtc2.Logging.e("WebRtcAudioRecord", "Join of AudioRecordJavaThread timed out");
      }

      this.audioThread = null;
      if (this.effects != null) {
         this.effects.release();
      }

      this.releaseAudioResources();
      return true;
   }

   private void logMainParameters() {
      org.webrtc2.Logging.d("WebRtcAudioRecord", "AudioRecord: session ID: " + this.audioRecord.getAudioSessionId() + ", " + "channels: " + this.audioRecord.getChannelCount() + ", " + "sample rate: " + this.audioRecord.getSampleRate());
   }

   private void logMainParametersExtended() {
      if (WebRtcAudioUtils.runningOnMarshmallowOrHigher()) {
         org.webrtc2.Logging.d("WebRtcAudioRecord", "AudioRecord: buffer size in frames: " + this.audioRecord.getBufferSizeInFrames());
      }

   }

   private static void assertTrue(boolean condition) {
      if (!condition) {
         throw new AssertionError("Expected condition to be true");
      }
   }

   private int channelCountToConfiguration(int channels) {
      return channels == 1 ? 16 : 12;
   }

   private native void nativeCacheDirectBufferAddress(ByteBuffer var1, long var2);

   private native void nativeDataIsRecorded(int var1, long var2);

   public static void setMicrophoneMute(boolean mute) {
      org.webrtc2.Logging.w("WebRtcAudioRecord", "setMicrophoneMute(" + mute + ")");
      microphoneMute = mute;
   }

   private void releaseAudioResources() {
      if (this.audioRecord != null) {
         this.audioRecord.release();
         this.audioRecord = null;
      }

   }

   private class AudioRecordThread extends Thread {
      private volatile boolean keepAlive = true;

      public AudioRecordThread(String name) {
         super(name);
      }

      public void run() {
         Process.setThreadPriority(-19);
         org.webrtc2.Logging.d("WebRtcAudioRecord", "AudioRecordThread" + WebRtcAudioUtils.getThreadInfo());
         WebRtcAudioRecord.assertTrue(WebRtcAudioRecord.this.audioRecord.getRecordingState() == 3);
         long lastTime = System.nanoTime();

         while(this.keepAlive) {
            int bytesRead = WebRtcAudioRecord.this.audioRecord.read(WebRtcAudioRecord.this.byteBuffer, WebRtcAudioRecord.this.byteBuffer.capacity());
            if (bytesRead == WebRtcAudioRecord.this.byteBuffer.capacity()) {
               if (WebRtcAudioRecord.microphoneMute) {
                  WebRtcAudioRecord.this.byteBuffer.clear();
                  WebRtcAudioRecord.this.byteBuffer.put(WebRtcAudioRecord.this.emptyBytes);
               }

               WebRtcAudioRecord.this.nativeDataIsRecorded(bytesRead, WebRtcAudioRecord.this.nativeAudioRecord);
            } else {
               org.webrtc2.Logging.e("WebRtcAudioRecord", "AudioRecord.read failed: " + bytesRead);
               if (bytesRead == -3) {
                  this.keepAlive = false;
               }
            }
         }

         try {
            if (WebRtcAudioRecord.this.audioRecord != null) {
               WebRtcAudioRecord.this.audioRecord.stop();
            }
         } catch (IllegalStateException var4) {
            IllegalStateException e = var4;
            org.webrtc2.Logging.e("WebRtcAudioRecord", "AudioRecord.stop failed: " + e.getMessage());
         }

      }

      public void stopThread() {
         Logging.d("WebRtcAudioRecord", "stopThread");
         this.keepAlive = false;
      }
   }
}
