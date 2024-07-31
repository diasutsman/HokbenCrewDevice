package org.webrtc2;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.util.Range;
import android.view.Surface;
import android.view.WindowManager;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@TargetApi(21)
public class Camera2Session implements org.webrtc2.CameraSession {
   private static final String TAG = "Camera2Session";
   private static final org.webrtc2.Histogram camera2StartTimeMsHistogram = org.webrtc2.Histogram.createCounts("WebRTC.Android.Camera2.StartTimeMs", 1, 10000, 50);
   private static final org.webrtc2.Histogram camera2StopTimeMsHistogram = org.webrtc2.Histogram.createCounts("WebRTC.Android.Camera2.StopTimeMs", 1, 10000, 50);
   private static final org.webrtc2.Histogram camera2ResolutionHistogram;
   private final Handler cameraThreadHandler;
   private final CreateSessionCallback callback;
   private final Events events;
   private final Context applicationContext;
   private final CameraManager cameraManager;
   private final SurfaceTextureHelper surfaceTextureHelper;
   private final String cameraId;
   private final int width;
   private final int height;
   private final int framerate;
   private CameraCharacteristics cameraCharacteristics;
   private int cameraOrientation;
   private boolean isCameraFrontFacing;
   private int fpsUnitFactor;
   private CameraEnumerationAndroid.CaptureFormat captureFormat;
   private CameraDevice cameraDevice;
   private Surface surface;
   private CameraCaptureSession captureSession;
   private SessionState state;
   private boolean firstFrameReported;
   private final long constructionTimeNs;

   public static void create(CreateSessionCallback callback, Events events, Context applicationContext, CameraManager cameraManager, SurfaceTextureHelper surfaceTextureHelper, String cameraId, int width, int height, int framerate) {
      new Camera2Session(callback, events, applicationContext, cameraManager, surfaceTextureHelper, cameraId, width, height, framerate);
   }

   private Camera2Session(CreateSessionCallback callback, Events events, Context applicationContext, CameraManager cameraManager, SurfaceTextureHelper surfaceTextureHelper, String cameraId, int width, int height, int framerate) {
      this.state = SessionState.RUNNING;
      this.firstFrameReported = false;
      Logging.d("Camera2Session", "Create new camera2 session on camera " + cameraId);
      this.constructionTimeNs = System.nanoTime();
      this.cameraThreadHandler = new Handler();
      this.callback = callback;
      this.events = events;
      this.applicationContext = applicationContext;
      this.cameraManager = cameraManager;
      this.surfaceTextureHelper = surfaceTextureHelper;
      this.cameraId = cameraId;
      this.width = width;
      this.height = height;
      this.framerate = framerate;
      this.start();
   }

   private void start() {
      this.checkIsOnCameraThread();
      Logging.d("Camera2Session", "start");

      try {
         this.cameraCharacteristics = this.cameraManager.getCameraCharacteristics(this.cameraId);
      } catch (CameraAccessException var2) {
         CameraAccessException e = var2;
         this.reportError("getCameraCharacteristics(): " + e.getMessage());
      }

      this.cameraOrientation = (Integer)this.cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
      this.isCameraFrontFacing = (Integer)this.cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == 0;
      this.findCaptureFormat();
      this.openCamera();
   }

   private void findCaptureFormat() {
      this.checkIsOnCameraThread();
      Range<Integer>[] fpsRanges = (Range[])this.cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
      this.fpsUnitFactor = Camera2Enumerator.getFpsUnitFactor(fpsRanges);
      List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> framerateRanges = Camera2Enumerator.convertFramerates(fpsRanges, this.fpsUnitFactor);
      List<Size> sizes = Camera2Enumerator.getSupportedSizes(this.cameraCharacteristics);
      Logging.d("Camera2Session", "Available preview sizes: " + sizes);
      Logging.d("Camera2Session", "Available fps ranges: " + framerateRanges);
      if (framerateRanges.isEmpty() || sizes.isEmpty()) {
         this.reportError("No supported capture formats.");
      }

      CameraEnumerationAndroid.CaptureFormat.FramerateRange bestFpsRange = CameraEnumerationAndroid.getClosestSupportedFramerateRange(framerateRanges, this.framerate);
      Size bestSize = CameraEnumerationAndroid.getClosestSupportedSize(sizes, this.width, this.height);
      CameraEnumerationAndroid.reportCameraResolution(camera2ResolutionHistogram, bestSize);
      this.captureFormat = new CameraEnumerationAndroid.CaptureFormat(bestSize.width, bestSize.height, bestFpsRange);
      Logging.d("Camera2Session", "Using capture format: " + this.captureFormat);
   }

   private void openCamera() {
      this.checkIsOnCameraThread();
      Logging.d("Camera2Session", "Opening camera " + this.cameraId);
      this.events.onCameraOpening();

      try {
         this.cameraManager.openCamera(this.cameraId, new CameraStateCallback(), this.cameraThreadHandler);
      } catch (CameraAccessException var2) {
         CameraAccessException e = var2;
         this.reportError("Failed to open camera: " + e);
      }

   }

   public void stop() {
      Logging.d("Camera2Session", "Stop camera2 session on camera " + this.cameraId);
      this.checkIsOnCameraThread();
      if (this.state != SessionState.STOPPED) {
         long stopStartTime = System.nanoTime();
         this.state = SessionState.STOPPED;
         this.stopInternal();
         int stopTimeMs = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStartTime);
         camera2StopTimeMsHistogram.addSample(stopTimeMs);
      }

   }

   private void stopInternal() {
      Logging.d("Camera2Session", "Stop internal");
      this.checkIsOnCameraThread();
      this.surfaceTextureHelper.stopListening();
      if (this.captureSession != null) {
         this.captureSession.close();
         this.captureSession = null;
      }

      if (this.surface != null) {
         this.surface.release();
         this.surface = null;
      }

      if (this.cameraDevice != null) {
         this.cameraDevice.close();
         this.cameraDevice = null;
      }

      Logging.d("Camera2Session", "Stop done");
   }

   private void reportError(String error) {
      this.checkIsOnCameraThread();
      Logging.e("Camera2Session", "Error: " + error);
      boolean startFailure = this.captureSession == null;
      this.state = SessionState.STOPPED;
      this.stopInternal();
      if (startFailure) {
         this.callback.onFailure(error);
      } else {
         this.events.onCameraError(this, error);
      }

   }

   private int getDeviceOrientation() {
      int orientation ;
      WindowManager wm = (WindowManager)this.applicationContext.getSystemService("window");
      switch (wm.getDefaultDisplay().getRotation()) {
         case 0:
         default:
            orientation = 0;
            break;
         case 1:
            orientation = 90;
            break;
         case 2:
            orientation = 180;
            break;
         case 3:
            orientation = 270;
      }

      return orientation;
   }

   private int getFrameOrientation() {
      int rotation = this.getDeviceOrientation();
      if (!this.isCameraFrontFacing) {
         rotation = 360 - rotation;
      }

      return (this.cameraOrientation + rotation) % 360;
   }

   private void checkIsOnCameraThread() {
      if (Thread.currentThread() != this.cameraThreadHandler.getLooper().getThread()) {
         throw new IllegalStateException("Wrong thread");
      }
   }

   static {
      camera2ResolutionHistogram = org.webrtc2.Histogram.createEnumeration("WebRTC.Android.Camera2.Resolution", CameraEnumerationAndroid.COMMON_RESOLUTIONS.size());
   }

   private class CameraCaptureCallback extends CameraCaptureSession.CaptureCallback {
      private CameraCaptureCallback() {
      }

      public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
         Logging.d("Camera2Session", "Capture failed: " + failure);
      }

      // $FF: synthetic method
      CameraCaptureCallback(Object x1) {
         this();
      }
   }

   private class CaptureSessionCallback extends CameraCaptureSession.StateCallback {
      private CaptureSessionCallback() {
      }

      public void onConfigureFailed(CameraCaptureSession session) {
         Camera2Session.this.checkIsOnCameraThread();
         session.close();
         Camera2Session.this.reportError("Failed to configure capture session.");
      }

      public void onConfigured(CameraCaptureSession session) {
         Camera2Session.this.checkIsOnCameraThread();
         Logging.d("Camera2Session", "Camera capture session configured.");
         Camera2Session.this.captureSession = session;

         try {
            CaptureRequest.Builder captureRequestBuilder = Camera2Session.this.cameraDevice.createCaptureRequest(3);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range(Camera2Session.this.captureFormat.framerate.min / Camera2Session.this.fpsUnitFactor, Camera2Session.this.captureFormat.framerate.max / Camera2Session.this.fpsUnitFactor));
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, 1);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);
            this.chooseStabilizationMode(captureRequestBuilder);
            this.chooseFocusMode(captureRequestBuilder);
            captureRequestBuilder.addTarget(Camera2Session.this.surface);
            session.setRepeatingRequest(captureRequestBuilder.build(), Camera2Session.this.new CameraCaptureCallback(), Camera2Session.this.cameraThreadHandler);
         } catch (CameraAccessException var3) {
            CameraAccessException e = var3;
            Camera2Session.this.reportError("Failed to start capture request. " + e);
            return;
         }

         Camera2Session.this.surfaceTextureHelper.startListening(new SurfaceTextureHelper.OnTextureFrameAvailableListener() {
            public void onTextureFrameAvailable(int oesTextureId, float[] transformMatrix, long timestampNs) {
               Camera2Session.this.checkIsOnCameraThread();
               if (Camera2Session.this.state != SessionState.RUNNING) {
                  Logging.d("Camera2Session", "Texture frame captured but camera is no longer running.");
                  Camera2Session.this.surfaceTextureHelper.returnTextureFrame();
               } else {
                  int rotation;
                  if (!Camera2Session.this.firstFrameReported) {
                     Camera2Session.this.firstFrameReported = true;
                     rotation = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - Camera2Session.this.constructionTimeNs);
                     Camera2Session.camera2StartTimeMsHistogram.addSample(rotation);
                  }

                  rotation = Camera2Session.this.getFrameOrientation();
                  if (Camera2Session.this.isCameraFrontFacing) {
                     transformMatrix = RendererCommon.multiplyMatrices(transformMatrix, RendererCommon.horizontalFlipMatrix());
                  }

                  transformMatrix = RendererCommon.rotateTextureMatrix(transformMatrix, (float)(-Camera2Session.this.cameraOrientation));
                  Camera2Session.this.events.onTextureFrameCaptured(Camera2Session.this, Camera2Session.this.captureFormat.width, Camera2Session.this.captureFormat.height, oesTextureId, transformMatrix, rotation, timestampNs);
               }
            }
         });
         Logging.d("Camera2Session", "Camera device successfully started.");
         Camera2Session.this.callback.onDone(Camera2Session.this);
      }

      private void chooseStabilizationMode(CaptureRequest.Builder captureRequestBuilder) {
         int[] availableOpticalStabilization = (int[])Camera2Session.this.cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
         int[] arr$x;
         int i$;
         int mode;
         if (availableOpticalStabilization != null) {
            arr$x = availableOpticalStabilization;
            int len$ = arr$x.length;

            for(i$ = 0; i$ < len$; ++i$) {
               mode = arr$x[i$];
               if (mode == 1) {
                  captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, 1);
                  captureRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, 0);
                  Logging.d("Camera2Session", "Using optical stabilization.");
                  return;
               }
            }
         }

         arr$x = (int[])Camera2Session.this.cameraCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
         int[] arr$ = arr$x;
         i$ = arr$x.length;

         for(mode = 0; mode < i$; ++mode) {
            int modex = arr$[mode];
            if (modex == 1) {
               captureRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, 1);
               captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, 0);
               Logging.d("Camera2Session", "Using video stabilization.");
               return;
            }
         }

         Logging.d("Camera2Session", "Stabilization not available.");
      }

      private void chooseFocusMode(CaptureRequest.Builder captureRequestBuilder) {
         int[] availableFocusModes = (int[])Camera2Session.this.cameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
         int[] arr$ = availableFocusModes;
         int len$ = arr$.length;

         for(int i$ = 0; i$ < len$; ++i$) {
            int mode = arr$[i$];
            if (mode == 3) {
               captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, 3);
               Logging.d("Camera2Session", "Using continuous video auto-focus.");
               return;
            }
         }

         Logging.d("Camera2Session", "Auto-focus is not available.");
      }

      // $FF: synthetic method
      CaptureSessionCallback(Object x1) {
         this();
      }
   }

   private class CameraStateCallback extends CameraDevice.StateCallback {
      private CameraStateCallback() {
      }

      private String getErrorDescription(int errorCode) {
         switch (errorCode) {
            case 1:
               return "Camera device is in use already.";
            case 2:
               return "Camera device could not be opened because there are too many other open camera devices.";
            case 3:
               return "Camera device could not be opened due to a device policy.";
            case 4:
               return "Camera device has encountered a fatal error.";
            case 5:
               return "Camera service has encountered a fatal error.";
            default:
               return "Unknown camera error: " + errorCode;
         }
      }

      public void onDisconnected(CameraDevice camera) {
         Camera2Session.this.checkIsOnCameraThread();
         boolean startFailure = Camera2Session.this.captureSession == null;
         Camera2Session.this.state = SessionState.STOPPED;
         Camera2Session.this.stopInternal();
         if (startFailure) {
            Camera2Session.this.callback.onFailure("Camera disconnected / evicted.");
         } else {
            Camera2Session.this.events.onCameraDisconnected(Camera2Session.this);
         }

      }

      public void onError(CameraDevice camera, int errorCode) {
         Camera2Session.this.checkIsOnCameraThread();
         Camera2Session.this.reportError(this.getErrorDescription(errorCode));
      }

      public void onOpened(CameraDevice camera) {
         Camera2Session.this.checkIsOnCameraThread();
         Logging.d("Camera2Session", "Camera opened.");
         Camera2Session.this.cameraDevice = camera;
         SurfaceTexture surfaceTexture = Camera2Session.this.surfaceTextureHelper.getSurfaceTexture();
         surfaceTexture.setDefaultBufferSize(Camera2Session.this.captureFormat.width, Camera2Session.this.captureFormat.height);
         Camera2Session.this.surface = new Surface(surfaceTexture);

         try {
            camera.createCaptureSession(Arrays.asList(Camera2Session.this.surface), Camera2Session.this.new CaptureSessionCallback(), Camera2Session.this.cameraThreadHandler);
         } catch (CameraAccessException var4) {
            CameraAccessException e = var4;
            Camera2Session.this.reportError("Failed to create capture session. " + e);
         }
      }

      public void onClosed(CameraDevice camera) {
         Camera2Session.this.checkIsOnCameraThread();
         Logging.d("Camera2Session", "Camera device closed.");
         Camera2Session.this.events.onCameraClosed(Camera2Session.this);
      }

      // $FF: synthetic method
      CameraStateCallback(Object x1) {
         this();
      }
   }

   private static enum SessionState {
      RUNNING,
      STOPPED;
   }
}
