package org.webrtc2;

public class IceCandidate {
   public final String sdpMid;
   public final int sdpMLineIndex;
   public final String sdp;

   public IceCandidate(String sdpMid, int sdpMLineIndex, String sdp) {
      this.sdpMid = sdpMid;
      this.sdpMLineIndex = sdpMLineIndex;
      this.sdp = sdp;
   }

   public String toString() {
      return this.sdpMid + ":" + this.sdpMLineIndex + ":" + this.sdp;
   }
}
