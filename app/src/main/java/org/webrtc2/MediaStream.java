package org.webrtc2;

import java.util.LinkedList;

public class MediaStream {
   public final LinkedList<org.webrtc2.AudioTrack> audioTracks = new LinkedList();
   public final LinkedList<org.webrtc2.VideoTrack> videoTracks = new LinkedList();
   public final LinkedList<org.webrtc2.VideoTrack> preservedVideoTracks = new LinkedList();
   final long nativeStream;

   public MediaStream(long nativeStream) {
      this.nativeStream = nativeStream;
   }

   public boolean addTrack(org.webrtc2.AudioTrack track) {
      if (nativeAddAudioTrack(this.nativeStream, track.nativeTrack)) {
         this.audioTracks.add(track);
         return true;
      } else {
         return false;
      }
   }

   public boolean addTrack(org.webrtc2.VideoTrack track) {
      if (nativeAddVideoTrack(this.nativeStream, track.nativeTrack)) {
         this.videoTracks.add(track);
         return true;
      } else {
         return false;
      }
   }

   public boolean addPreservedTrack(org.webrtc2.VideoTrack track) {
      if (nativeAddVideoTrack(this.nativeStream, track.nativeTrack)) {
         this.preservedVideoTracks.add(track);
         return true;
      } else {
         return false;
      }
   }

   public boolean removeTrack(org.webrtc2.AudioTrack track) {
      this.audioTracks.remove(track);
      return nativeRemoveAudioTrack(this.nativeStream, track.nativeTrack);
   }

   public boolean removeTrack(org.webrtc2.VideoTrack track) {
      this.videoTracks.remove(track);
      this.preservedVideoTracks.remove(track);
      return nativeRemoveVideoTrack(this.nativeStream, track.nativeTrack);
   }

   public void dispose() {
      while(!this.audioTracks.isEmpty()) {
         org.webrtc2.AudioTrack track = (AudioTrack)this.audioTracks.getFirst();
         this.removeTrack(track);
         track.dispose();
      }

      while(!this.videoTracks.isEmpty()) {
         org.webrtc2.VideoTrack track = (org.webrtc2.VideoTrack)this.videoTracks.getFirst();
         this.removeTrack(track);
         track.dispose();
      }

      while(!this.preservedVideoTracks.isEmpty()) {
         this.removeTrack((VideoTrack)this.preservedVideoTracks.getFirst());
      }

      free(this.nativeStream);
   }

   public String label() {
      return nativeLabel(this.nativeStream);
   }

   public String toString() {
      return "[" + this.label() + ":A=" + this.audioTracks.size() + ":V=" + this.videoTracks.size() + "]";
   }

   private static native boolean nativeAddAudioTrack(long var0, long var2);

   private static native boolean nativeAddVideoTrack(long var0, long var2);

   private static native boolean nativeRemoveAudioTrack(long var0, long var2);

   private static native boolean nativeRemoveVideoTrack(long var0, long var2);

   private static native String nativeLabel(long var0);

   private static native void free(long var0);
}
