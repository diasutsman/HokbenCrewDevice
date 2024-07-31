package org.webrtc2;

public interface SdpObserver {
   void onCreateSuccess(SessionDescription var1);

   void onSetSuccess();

   void onCreateFailure(String var1);

   void onSetFailure(String var1);
}
