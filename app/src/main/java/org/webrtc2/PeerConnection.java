package org.webrtc2;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class PeerConnection {
   private final List<org.webrtc2.MediaStream> localStreams;
   private final long nativePeerConnection;
   private final long nativeObserver;
   private List<org.webrtc2.RtpSender> senders;
   private List<org.webrtc2.RtpReceiver> receivers;

   PeerConnection(long nativePeerConnection, long nativeObserver) {
      this.nativePeerConnection = nativePeerConnection;
      this.nativeObserver = nativeObserver;
      this.localStreams = new LinkedList();
      this.senders = new LinkedList();
      this.receivers = new LinkedList();
   }

   public native SessionDescription getLocalDescription();

   public native SessionDescription getRemoteDescription();

   public native org.webrtc2.DataChannel createDataChannel(String var1, org.webrtc2.DataChannel.Init var2);

   public native void createOffer(org.webrtc2.SdpObserver var1, MediaConstraints var2);

   public native void createAnswer(org.webrtc2.SdpObserver var1, MediaConstraints var2);

   public native void setLocalDescription(org.webrtc2.SdpObserver var1, SessionDescription var2);

   public native void setRemoteDescription(SdpObserver var1, SessionDescription var2);

   public native boolean setConfiguration(RTCConfiguration var1);

   public boolean addIceCandidate(org.webrtc2.IceCandidate candidate) {
      return this.nativeAddIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp);
   }

   public boolean removeIceCandidates(org.webrtc2.IceCandidate[] candidates) {
      return this.nativeRemoveIceCandidates(candidates);
   }

   public boolean addStream(org.webrtc2.MediaStream stream) {
      boolean ret = this.nativeAddLocalStream(stream.nativeStream);
      if (!ret) {
         return false;
      } else {
         this.localStreams.add(stream);
         return true;
      }
   }

   public void removeStream(org.webrtc2.MediaStream stream) {
      this.nativeRemoveLocalStream(stream.nativeStream);
      this.localStreams.remove(stream);
   }

   public org.webrtc2.RtpSender createSender(String kind, String stream_id) {
      org.webrtc2.RtpSender new_sender = this.nativeCreateSender(kind, stream_id);
      if (new_sender != null) {
         this.senders.add(new_sender);
      }

      return new_sender;
   }

   public List<org.webrtc2.RtpSender> getSenders() {
      Iterator i$ = this.senders.iterator();

      while(i$.hasNext()) {
         org.webrtc2.RtpSender sender = (org.webrtc2.RtpSender)i$.next();
         sender.dispose();
      }

      this.senders = this.nativeGetSenders();
      return Collections.unmodifiableList(this.senders);
   }

   public List<org.webrtc2.RtpReceiver> getReceivers() {
      Iterator i$ = this.receivers.iterator();

      while(i$.hasNext()) {
         org.webrtc2.RtpReceiver receiver = (org.webrtc2.RtpReceiver)i$.next();
         receiver.dispose();
      }

      this.receivers = this.nativeGetReceivers();
      return Collections.unmodifiableList(this.receivers);
   }

   public boolean getStats(org.webrtc2.StatsObserver observer, MediaStreamTrack track) {
      return this.nativeGetStats(observer, track == null ? 0L : track.nativeTrack);
   }

   public boolean startRtcEventLog(int file_descriptor, int max_size_bytes) {
      return this.nativeStartRtcEventLog(file_descriptor, max_size_bytes);
   }

   public void stopRtcEventLog() {
      this.nativeStopRtcEventLog();
   }

   public native SignalingState signalingState();

   public native IceConnectionState iceConnectionState();

   public native IceGatheringState iceGatheringState();

   public native void close();

   public void dispose() {
      this.close();
      Iterator i$ = this.localStreams.iterator();

      while(i$.hasNext()) {
         org.webrtc2.MediaStream stream = (org.webrtc2.MediaStream)i$.next();
         this.nativeRemoveLocalStream(stream.nativeStream);
         stream.dispose();
      }

      this.localStreams.clear();
      i$ = this.senders.iterator();

      while(i$.hasNext()) {
         org.webrtc2.RtpSender sender = (org.webrtc2.RtpSender)i$.next();
         sender.dispose();
      }

      this.senders.clear();
      i$ = this.receivers.iterator();

      while(i$.hasNext()) {
         org.webrtc2.RtpReceiver receiver = (org.webrtc2.RtpReceiver)i$.next();
         receiver.dispose();
      }

      this.receivers.clear();
      freePeerConnection(this.nativePeerConnection);
      freeObserver(this.nativeObserver);
   }

   private static native void freePeerConnection(long var0);

   private static native void freeObserver(long var0);

   private native boolean nativeAddIceCandidate(String var1, int var2, String var3);

   private native boolean nativeRemoveIceCandidates(org.webrtc2.IceCandidate[] var1);

   private native boolean nativeAddLocalStream(long var1);

   private native void nativeRemoveLocalStream(long var1);

   private native boolean nativeGetStats(StatsObserver var1, long var2);

   private native org.webrtc2.RtpSender nativeCreateSender(String var1, String var2);

   private native List<RtpSender> nativeGetSenders();

   private native List<RtpReceiver> nativeGetReceivers();

   private native boolean nativeStartRtcEventLog(int var1, int var2);

   private native void nativeStopRtcEventLog();

   static {
      System.loadLibrary("jingle_peerconnection_so");
   }

   public static class RTCConfiguration {
      public IceTransportsType iceTransportsType;
      public List<IceServer> iceServers;
      public BundlePolicy bundlePolicy;
      public RtcpMuxPolicy rtcpMuxPolicy;
      public TcpCandidatePolicy tcpCandidatePolicy;
      public CandidateNetworkPolicy candidateNetworkPolicy;
      public int audioJitterBufferMaxPackets;
      public boolean audioJitterBufferFastAccelerate;
      public int iceConnectionReceivingTimeout;
      public int iceBackupCandidatePairPingInterval;
      public KeyType keyType;
      public ContinualGatheringPolicy continualGatheringPolicy;
      public int iceCandidatePoolSize;
      public boolean pruneTurnPorts;
      public boolean presumeWritableWhenFullyRelayed;

      public RTCConfiguration(List<IceServer> iceServers) {
         this.iceTransportsType = IceTransportsType.ALL;
         this.bundlePolicy = BundlePolicy.BALANCED;
         this.rtcpMuxPolicy = RtcpMuxPolicy.REQUIRE;
         this.tcpCandidatePolicy = TcpCandidatePolicy.ENABLED;
         CandidateNetworkPolicy var10001 = this.candidateNetworkPolicy;
         this.candidateNetworkPolicy = CandidateNetworkPolicy.ALL;
         this.iceServers = iceServers;
         this.audioJitterBufferMaxPackets = 50;
         this.audioJitterBufferFastAccelerate = false;
         this.iceConnectionReceivingTimeout = -1;
         this.iceBackupCandidatePairPingInterval = -1;
         this.keyType = KeyType.ECDSA;
         this.continualGatheringPolicy = ContinualGatheringPolicy.GATHER_ONCE;
         this.iceCandidatePoolSize = 0;
         this.pruneTurnPorts = false;
         this.presumeWritableWhenFullyRelayed = false;
      }
   }

   public static enum ContinualGatheringPolicy {
      GATHER_ONCE,
      GATHER_CONTINUALLY;
   }

   public static enum KeyType {
      RSA,
      ECDSA;
   }

   public static enum CandidateNetworkPolicy {
      ALL,
      LOW_COST;
   }

   public static enum TcpCandidatePolicy {
      ENABLED,
      DISABLED;
   }

   public static enum RtcpMuxPolicy {
      NEGOTIATE,
      REQUIRE;
   }

   public static enum BundlePolicy {
      BALANCED,
      MAXBUNDLE,
      MAXCOMPAT;
   }

   public static enum IceTransportsType {
      NONE,
      RELAY,
      NOHOST,
      ALL;
   }

   public static class IceServer {
      public final String uri;
      public final String username;
      public final String password;

      public IceServer(String uri) {
         this(uri, "", "");
      }

      public IceServer(String uri, String username, String password) {
         this.uri = uri;
         this.username = username;
         this.password = password;
      }

      public String toString() {
         return this.uri + "[" + this.username + ":" + this.password + "]";
      }
   }

   public interface Observer {
      void onSignalingChange(SignalingState var1);

      void onIceConnectionChange(IceConnectionState var1);

      void onIceConnectionReceivingChange(boolean var1);

      void onIceGatheringChange(IceGatheringState var1);

      void onIceCandidate(org.webrtc2.IceCandidate var1);

      void onIceCandidatesRemoved(IceCandidate[] var1);

      void onAddStream(org.webrtc2.MediaStream var1);

      void onRemoveStream(MediaStream var1);

      void onDataChannel(DataChannel var1);

      void onRenegotiationNeeded();
   }

   public static enum SignalingState {
      STABLE,
      HAVE_LOCAL_OFFER,
      HAVE_LOCAL_PRANSWER,
      HAVE_REMOTE_OFFER,
      HAVE_REMOTE_PRANSWER,
      CLOSED;
   }

   public static enum IceConnectionState {
      NEW,
      CHECKING,
      CONNECTED,
      COMPLETED,
      FAILED,
      DISCONNECTED,
      CLOSED;
   }

   public static enum IceGatheringState {
      NEW,
      GATHERING,
      COMPLETE;
   }
}
