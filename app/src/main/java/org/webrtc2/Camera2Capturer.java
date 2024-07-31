package org.webrtc2;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.camera2.CameraManager;

@TargetApi(21)
public class Camera2Capturer extends org.webrtc2.CameraCapturer {
   private final Context context;
   private final CameraManager cameraManager;

   public Camera2Capturer(Context context, String cameraName, CameraVideoCapturer.CameraEventsHandler eventsHandler) {
      super(cameraName, eventsHandler, new Camera2Enumerator(context));
      this.context = context;
      this.cameraManager = (CameraManager)context.getSystemService("camera");
   }

   protected void createCameraSession(org.webrtc2.CameraSession.CreateSessionCallback createSessionCallback, org.webrtc2.CameraSession.Events events, Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, String cameraName, int width, int height, int framerate) {
      org.webrtc2.Camera2Session.create(createSessionCallback, events, applicationContext, this.cameraManager, surfaceTextureHelper, cameraName, width, height, framerate);
   }
}
