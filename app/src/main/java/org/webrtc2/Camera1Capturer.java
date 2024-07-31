package org.webrtc2;

import android.content.Context;

public class Camera1Capturer extends org.webrtc2.CameraCapturer {
   private final boolean captureToTexture;

   public Camera1Capturer(String cameraName, CameraVideoCapturer.CameraEventsHandler eventsHandler, boolean captureToTexture) {
      super(cameraName, eventsHandler, new Camera1Enumerator(captureToTexture));
      this.captureToTexture = captureToTexture;
   }

   protected void createCameraSession(org.webrtc2.CameraSession.CreateSessionCallback createSessionCallback, org.webrtc2.CameraSession.Events events, Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, String cameraName, int width, int height, int framerate) {
      org.webrtc2.Camera1Session.create(createSessionCallback, events, this.captureToTexture, applicationContext, surfaceTextureHelper, Camera1Enumerator.getCameraIndex(cameraName), width, height, framerate);
   }
}
