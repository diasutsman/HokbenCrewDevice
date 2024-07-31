package org.webrtc2;

public class RtpSender {
   final long nativeRtpSender;
   private MediaStreamTrack cachedTrack;
   private boolean ownsTrack = true;

   public RtpSender(long nativeRtpSender) {
      this.nativeRtpSender = nativeRtpSender;
      long track = nativeGetTrack(nativeRtpSender);
      this.cachedTrack = track == 0L ? null : new MediaStreamTrack(track);
   }

   public boolean setTrack(MediaStreamTrack track, boolean takeOwnership) {
      if (!nativeSetTrack(this.nativeRtpSender, track == null ? 0L : track.nativeTrack)) {
         return false;
      } else {
         if (this.cachedTrack != null && this.ownsTrack) {
            this.cachedTrack.dispose();
         }

         this.cachedTrack = track;
         this.ownsTrack = takeOwnership;
         return true;
      }
   }

   public MediaStreamTrack track() {
      return this.cachedTrack;
   }

   public boolean setParameters(RtpParameters parameters) {
      return nativeSetParameters(this.nativeRtpSender, parameters);
   }

   public RtpParameters getParameters() {
      return nativeGetParameters(this.nativeRtpSender);
   }

   public String id() {
      return nativeId(this.nativeRtpSender);
   }

   public void dispose() {
      if (this.cachedTrack != null && this.ownsTrack) {
         this.cachedTrack.dispose();
      }

      free(this.nativeRtpSender);
   }

   private static native boolean nativeSetTrack(long var0, long var2);

   private static native long nativeGetTrack(long var0);

   private static native boolean nativeSetParameters(long var0, RtpParameters var2);

   private static native RtpParameters nativeGetParameters(long var0);

   private static native String nativeId(long var0);

   private static native void free(long var0);
}
