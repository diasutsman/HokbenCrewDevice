package org.webrtc2;

import android.content.Context;
import android.hardware.Camera;
import android.os.Handler;
import android.os.SystemClock;
import android.view.WindowManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** @deprecated */
@Deprecated
public class VideoCapturerAndroid implements CameraVideoCapturer, Camera.PreviewCallback, org.webrtc2.SurfaceTextureHelper.OnTextureFrameAvailableListener {
   private static final String TAG = "VideoCapturerAndroid";
   private static final int CAMERA_STOP_TIMEOUT_MS = 7000;
   private static final org.webrtc2.Histogram videoCapturerAndroidStartTimeMsHistogram = org.webrtc2.Histogram.createCounts("WebRTC.Android.VideoCapturerAndroid.StartTimeMs", 1, 10000, 50);
   private static final org.webrtc2.Histogram videoCapturerAndroidStopTimeMsHistogram = org.webrtc2.Histogram.createCounts("WebRTC.Android.VideoCapturerAndroid.StopTimeMs", 1, 10000, 50);
   private static final org.webrtc2.Histogram videoCapturerAndroidResolutionHistogram;
   private Camera camera;
   private final AtomicBoolean isCameraRunning = new AtomicBoolean();
   private volatile Handler cameraThreadHandler;
   private Context applicationContext;
   private final Object cameraIdLock = new Object();
   private int id;
   private Camera.CameraInfo info;
   private CameraStatistics cameraStatistics;
   private int requestedWidth;
   private int requestedHeight;
   private int requestedFramerate;
   private CameraEnumerationAndroid.CaptureFormat captureFormat;
   private final Object pendingCameraSwitchLock = new Object();
   private volatile boolean pendingCameraSwitch;
   private CapturerObserver frameObserver = null;
   private final CameraEventsHandler eventsHandler;
   private boolean firstFrameReported;
   private static final int NUMBER_OF_CAPTURE_BUFFERS = 3;
   private final Set<byte[]> queuedBuffers = new HashSet();
   private final boolean isCapturingToTexture;
   private org.webrtc2.SurfaceTextureHelper surfaceHelper;
   private static final int MAX_OPEN_CAMERA_ATTEMPTS = 3;
   private static final int OPEN_CAMERA_DELAY_MS = 500;
   private int openCameraAttempts;
   private long startStartTimeNs;
   private final Camera.ErrorCallback cameraErrorCallback = new Camera.ErrorCallback() {
      public void onError(int error, Camera camera) {
         String errorMessage;
         if (error == 100) {
            errorMessage = "Camera server died!";
         } else {
            errorMessage = "Camera error: " + error;
         }

         Logging.e("VideoCapturerAndroid", errorMessage);
         if (VideoCapturerAndroid.this.eventsHandler != null) {
            if (error == 2) {
               VideoCapturerAndroid.this.eventsHandler.onCameraDisconnected();
            } else {
               VideoCapturerAndroid.this.eventsHandler.onCameraError(errorMessage);
            }
         }

      }
   };

   public static VideoCapturerAndroid create(String name, CameraEventsHandler eventsHandler) {
      return create(name, eventsHandler, false);
   }

   /** @deprecated */
   @Deprecated
   public static VideoCapturerAndroid create(String name, CameraEventsHandler eventsHandler, boolean captureToTexture) {
      try {
         return new VideoCapturerAndroid(name, eventsHandler, captureToTexture);
      } catch (RuntimeException var4) {
         RuntimeException e = var4;
         Logging.e("VideoCapturerAndroid", "Couldn't create camera.", e);
         return null;
      }
   }

   public void printStackTrace() {
      Thread cameraThread = null;
      if (this.cameraThreadHandler != null) {
         cameraThread = this.cameraThreadHandler.getLooper().getThread();
      }

      if (cameraThread != null) {
         StackTraceElement[] cameraStackTraces = cameraThread.getStackTrace();
         if (cameraStackTraces.length > 0) {
            Logging.d("VideoCapturerAndroid", "VideoCapturerAndroid stacks trace:");
            StackTraceElement[] arr$ = cameraStackTraces;
            int len$ = arr$.length;

            for(int i$ = 0; i$ < len$; ++i$) {
               StackTraceElement stackTrace = arr$[i$];
               Logging.d("VideoCapturerAndroid", stackTrace.toString());
            }
         }
      }

   }

   public void switchCamera(final CameraSwitchHandler switchEventsHandler) {
      if (Camera.getNumberOfCameras() < 2) {
         if (switchEventsHandler != null) {
            switchEventsHandler.onCameraSwitchError("No camera to switch to.");
         }

      } else {
         synchronized(this.pendingCameraSwitchLock) {
            if (this.pendingCameraSwitch) {
               Logging.w("VideoCapturerAndroid", "Ignoring camera switch request.");
               if (switchEventsHandler != null) {
                  switchEventsHandler.onCameraSwitchError("Pending camera switch already in progress.");
               }

               return;
            }

            this.pendingCameraSwitch = true;
         }

         boolean didPost = this.maybePostOnCameraThread(new Runnable() {
            public void run() {
               VideoCapturerAndroid.this.switchCameraOnCameraThread();
               synchronized(VideoCapturerAndroid.this.pendingCameraSwitchLock) {
                  VideoCapturerAndroid.this.pendingCameraSwitch = false;
               }

               if (switchEventsHandler != null) {
                  switchEventsHandler.onCameraSwitchDone(VideoCapturerAndroid.this.info.facing == 1);
               }

            }
         });
         if (!didPost && switchEventsHandler != null) {
            switchEventsHandler.onCameraSwitchError("Camera is stopped.");
         }

      }
   }

   public void changeCaptureFormat(final int width, final int height, final int framerate) {
      this.maybePostOnCameraThread(new Runnable() {
         public void run() {
            VideoCapturerAndroid.this.startPreviewOnCameraThread(width, height, framerate);
         }
      });
   }

   private int getCurrentCameraId() {
      synchronized(this.cameraIdLock) {
         return this.id;
      }
   }

   public boolean isCapturingToTexture() {
      return this.isCapturingToTexture;
   }

   public VideoCapturerAndroid(String cameraName, CameraEventsHandler eventsHandler, boolean captureToTexture) {
      if (Camera.getNumberOfCameras() == 0) {
         throw new RuntimeException("No cameras available");
      } else {
         if (cameraName != null && !cameraName.equals("")) {
            this.id = org.webrtc2.Camera1Enumerator.getCameraIndex(cameraName);
         } else {
            this.id = 0;
         }

         this.eventsHandler = eventsHandler;
         this.isCapturingToTexture = captureToTexture;
         Logging.d("VideoCapturerAndroid", "VideoCapturerAndroid isCapturingToTexture : " + this.isCapturingToTexture);
      }
   }

   private void checkIsOnCameraThread() {
      if (this.cameraThreadHandler == null) {
         Logging.e("VideoCapturerAndroid", "Camera is not initialized - can't check thread.");
      } else if (Thread.currentThread() != this.cameraThreadHandler.getLooper().getThread()) {
         throw new IllegalStateException("Wrong thread");
      }

   }

   private boolean maybePostOnCameraThread(Runnable runnable) {
      return this.maybePostDelayedOnCameraThread(0, runnable);
   }

   private boolean maybePostDelayedOnCameraThread(int delayMs, Runnable runnable) {
      return this.cameraThreadHandler != null && this.isCameraRunning.get() && this.cameraThreadHandler.postAtTime(runnable, this, SystemClock.uptimeMillis() + (long)delayMs);
   }

   public void dispose() {
      Logging.d("VideoCapturerAndroid", "dispose");
   }

   private boolean isInitialized() {
      return this.applicationContext != null && this.frameObserver != null;
   }

   public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext, CapturerObserver frameObserver) {
      Logging.d("VideoCapturerAndroid", "initialize");
      if (applicationContext == null) {
         throw new IllegalArgumentException("applicationContext not set.");
      } else if (frameObserver == null) {
         throw new IllegalArgumentException("frameObserver not set.");
      } else if (this.isInitialized()) {
         throw new IllegalStateException("Already initialized");
      } else {
         this.applicationContext = applicationContext;
         this.frameObserver = frameObserver;
         this.surfaceHelper = surfaceTextureHelper;
         this.cameraThreadHandler = surfaceTextureHelper == null ? null : surfaceTextureHelper.getHandler();
      }
   }

   public void startCapture(final int width, final int height, final int framerate) {
      Logging.d("VideoCapturerAndroid", "startCapture requested: " + width + "x" + height + "@" + framerate);
      if (!this.isInitialized()) {
         throw new IllegalStateException("startCapture called in uninitialized state");
      } else if (this.surfaceHelper == null) {
         this.frameObserver.onCapturerStarted(false);
         if (this.eventsHandler != null) {
            this.eventsHandler.onCameraError("No SurfaceTexture created.");
         }

      } else if (this.isCameraRunning.getAndSet(true)) {
         Logging.e("VideoCapturerAndroid", "Camera has already been started.");
      } else {
         boolean didPost = this.maybePostOnCameraThread(new Runnable() {
            public void run() {
               VideoCapturerAndroid.this.openCameraAttempts = 0;
               VideoCapturerAndroid.this.startCaptureOnCameraThread(width, height, framerate);
            }
         });
         if (!didPost) {
            this.frameObserver.onCapturerStarted(false);
            if (this.eventsHandler != null) {
               this.eventsHandler.onCameraError("Could not post task to camera thread.");
            }

            this.isCameraRunning.set(false);
         }

      }
   }

   private void startCaptureOnCameraThread(final int width, final int height, final int framerate) {
      this.checkIsOnCameraThread();
      this.startStartTimeNs = System.nanoTime();
      if (!this.isCameraRunning.get()) {
         Logging.e("VideoCapturerAndroid", "startCaptureOnCameraThread: Camera is stopped");
      } else if (this.camera != null) {
         Logging.e("VideoCapturerAndroid", "startCaptureOnCameraThread: Camera has already been started.");
      } else {
         this.firstFrameReported = false;

         try {
            try {
               synchronized(this.cameraIdLock) {
                  Logging.d("VideoCapturerAndroid", "Opening camera " + this.id);
                  if (this.eventsHandler != null) {
                     this.eventsHandler.onCameraOpening(org.webrtc2.Camera1Enumerator.getDeviceName(this.id));
                  }

                  this.camera = Camera.open(this.id);
                  this.info = new Camera.CameraInfo();
                  Camera.getCameraInfo(this.id, this.info);
               }
            } catch (RuntimeException var7) {
               ++this.openCameraAttempts;
               if (this.openCameraAttempts < 3) {
                  Logging.e("VideoCapturerAndroid", "Camera.open failed, retrying", var7);
                  this.maybePostDelayedOnCameraThread(500, new Runnable() {
                     public void run() {
                        VideoCapturerAndroid.this.startCaptureOnCameraThread(width, height, framerate);
                     }
                  });
                  return;
               }

               throw var7;
            }

            this.camera.setPreviewTexture(this.surfaceHelper.getSurfaceTexture());
            Logging.d("VideoCapturerAndroid", "Camera orientation: " + this.info.orientation + " .Device orientation: " + this.getDeviceOrientation());
            this.camera.setErrorCallback(this.cameraErrorCallback);
            this.startPreviewOnCameraThread(width, height, framerate);
            this.frameObserver.onCapturerStarted(true);
            if (this.isCapturingToTexture) {
               this.surfaceHelper.startListening(this);
            }

            this.cameraStatistics = new CameraStatistics(this.surfaceHelper, this.eventsHandler);
         } catch (RuntimeException | IOException var8) {
            Exception e = var8;
            Logging.e("VideoCapturerAndroid", "startCapture failed", e);
            this.stopCaptureOnCameraThread(true);
            this.frameObserver.onCapturerStarted(false);
            if (this.eventsHandler != null) {
               this.eventsHandler.onCameraError("Camera can not be started.");
            }
         }

      }
   }

   private void startPreviewOnCameraThread(int width, int height, int framerate) {
      this.checkIsOnCameraThread();
      if (this.isCameraRunning.get() && this.camera != null) {
         Logging.d("VideoCapturerAndroid", "startPreviewOnCameraThread requested: " + width + "x" + height + "@" + framerate);
         this.requestedWidth = width;
         this.requestedHeight = height;
         this.requestedFramerate = framerate;
         Camera.Parameters parameters = this.camera.getParameters();
         List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> supportedFramerates = org.webrtc2.Camera1Enumerator.convertFramerates(parameters.getSupportedPreviewFpsRange());
         Logging.d("VideoCapturerAndroid", "Available fps ranges: " + supportedFramerates);
         CameraEnumerationAndroid.CaptureFormat.FramerateRange fpsRange = CameraEnumerationAndroid.getClosestSupportedFramerateRange(supportedFramerates, framerate);
         List<Size> supportedPreviewSizes = org.webrtc2.Camera1Enumerator.convertSizes(parameters.getSupportedPreviewSizes());
         Size previewSize = CameraEnumerationAndroid.getClosestSupportedSize(supportedPreviewSizes, width, height);
         CameraEnumerationAndroid.reportCameraResolution(videoCapturerAndroidResolutionHistogram, previewSize);
         Logging.d("VideoCapturerAndroid", "Available preview sizes: " + supportedPreviewSizes);
         CameraEnumerationAndroid.CaptureFormat captureFormat = new CameraEnumerationAndroid.CaptureFormat(previewSize.width, previewSize.height, fpsRange);
         if (!captureFormat.equals(this.captureFormat)) {
            Logging.d("VideoCapturerAndroid", "isVideoStabilizationSupported: " + parameters.isVideoStabilizationSupported());
            if (parameters.isVideoStabilizationSupported()) {
               parameters.setVideoStabilization(true);
            }

            if (captureFormat.framerate.max > 0) {
               parameters.setPreviewFpsRange(captureFormat.framerate.min, captureFormat.framerate.max);
            }

            parameters.setPreviewSize(previewSize.width, previewSize.height);
            if (!this.isCapturingToTexture) {
               captureFormat.getClass();
               parameters.setPreviewFormat(17);
            }

            Size pictureSize = CameraEnumerationAndroid.getClosestSupportedSize(Camera1Enumerator.convertSizes(parameters.getSupportedPictureSizes()), width, height);
            parameters.setPictureSize(pictureSize.width, pictureSize.height);
            if (this.captureFormat != null) {
               this.camera.stopPreview();
               this.camera.setPreviewCallbackWithBuffer((Camera.PreviewCallback)null);
            }

            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes.contains("continuous-video")) {
               Logging.d("VideoCapturerAndroid", "Enable continuous auto focus mode.");
               parameters.setFocusMode("continuous-video");
            }

            Logging.d("VideoCapturerAndroid", "Start capturing: " + captureFormat);
            this.captureFormat = captureFormat;
            this.camera.setParameters(parameters);
            this.camera.setDisplayOrientation(0);
            if (!this.isCapturingToTexture) {
               this.queuedBuffers.clear();
               int frameSize = captureFormat.frameSize();

               for(int i = 0; i < 3; ++i) {
                  ByteBuffer buffer = ByteBuffer.allocateDirect(frameSize);
                  this.queuedBuffers.add(buffer.array());
                  this.camera.addCallbackBuffer(buffer.array());
               }

               this.camera.setPreviewCallbackWithBuffer(this);
            }

            this.camera.startPreview();
         }
      } else {
         Logging.e("VideoCapturerAndroid", "startPreviewOnCameraThread: Camera is stopped");
      }
   }

   public void stopCapture() throws InterruptedException {
      Logging.d("VideoCapturerAndroid", "stopCapture");
      final CountDownLatch barrier = new CountDownLatch(1);
      boolean didPost = this.maybePostOnCameraThread(new Runnable() {
         public void run() {
            VideoCapturerAndroid.this.stopCaptureOnCameraThread(true);
            barrier.countDown();
         }
      });
      if (!didPost) {
         Logging.e("VideoCapturerAndroid", "Calling stopCapture() for already stopped camera.");
      } else {
         if (!barrier.await(7000L, TimeUnit.MILLISECONDS)) {
            Logging.e("VideoCapturerAndroid", "Camera stop timeout");
            this.printStackTrace();
            if (this.eventsHandler != null) {
               this.eventsHandler.onCameraError("Camera stop timeout");
            }
         }

         this.frameObserver.onCapturerStopped();
         Logging.d("VideoCapturerAndroid", "stopCapture done");
      }
   }

   private void stopCaptureOnCameraThread(boolean stopHandler) {
      this.checkIsOnCameraThread();
      Logging.d("VideoCapturerAndroid", "stopCaptureOnCameraThread");
      long stopStartTime = System.nanoTime();
      if (this.surfaceHelper != null) {
         this.surfaceHelper.stopListening();
      }

      if (stopHandler) {
         this.isCameraRunning.set(false);
         this.cameraThreadHandler.removeCallbacksAndMessages(this);
      }

      if (this.cameraStatistics != null) {
         this.cameraStatistics.release();
         this.cameraStatistics = null;
      }

      Logging.d("VideoCapturerAndroid", "Stop preview.");
      if (this.camera != null) {
         this.camera.stopPreview();
         this.camera.setPreviewCallbackWithBuffer((Camera.PreviewCallback)null);
      }

      this.queuedBuffers.clear();
      this.captureFormat = null;
      Logging.d("VideoCapturerAndroid", "Release camera.");
      if (this.camera != null) {
         this.camera.release();
         this.camera = null;
      }

      if (this.eventsHandler != null) {
         this.eventsHandler.onCameraClosed();
      }

      int stopTimeMs = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStartTime);
      videoCapturerAndroidStopTimeMsHistogram.addSample(stopTimeMs);
      Logging.d("VideoCapturerAndroid", "stopCaptureOnCameraThread done");
   }

   private void switchCameraOnCameraThread() {
      this.checkIsOnCameraThread();
      if (!this.isCameraRunning.get()) {
         Logging.e("VideoCapturerAndroid", "switchCameraOnCameraThread: Camera is stopped");
      } else {
         Logging.d("VideoCapturerAndroid", "switchCameraOnCameraThread");
         this.stopCaptureOnCameraThread(false);
         synchronized(this.cameraIdLock) {
            this.id = (this.id + 1) % Camera.getNumberOfCameras();
         }

         this.startCaptureOnCameraThread(this.requestedWidth, this.requestedHeight, this.requestedFramerate);
         Logging.d("VideoCapturerAndroid", "switchCameraOnCameraThread done");
      }
   }

   private int getDeviceOrientation() {
      int orientation;
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
      if (this.info.facing == 0) {
         rotation = 360 - rotation;
      }

      return (this.info.orientation + rotation) % 360;
   }

   public void onPreviewFrame(byte[] data, Camera callbackCamera) {
      this.checkIsOnCameraThread();
      if (!this.isCameraRunning.get()) {
         Logging.e("VideoCapturerAndroid", "onPreviewFrame: Camera is stopped");
      } else if (this.queuedBuffers.contains(data)) {
         if (this.camera != callbackCamera) {
            throw new RuntimeException("Unexpected camera in callback!");
         } else {
            long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
            if (!this.firstFrameReported) {
               this.onFirstFrameAvailable();
            }

            this.cameraStatistics.addFrame();
            this.frameObserver.onByteBufferFrameCaptured(data, this.captureFormat.width, this.captureFormat.height, this.getFrameOrientation(), captureTimeNs);
            this.camera.addCallbackBuffer(data);
         }
      }
   }

   public void onTextureFrameAvailable(int oesTextureId, float[] transformMatrix, long timestampNs) {
      this.checkIsOnCameraThread();
      if (!this.isCameraRunning.get()) {
         Logging.e("VideoCapturerAndroid", "onTextureFrameAvailable: Camera is stopped");
         this.surfaceHelper.returnTextureFrame();
      } else {
         int rotation = this.getFrameOrientation();
         if (this.info.facing == 1) {
            transformMatrix = org.webrtc2.RendererCommon.multiplyMatrices(transformMatrix, RendererCommon.horizontalFlipMatrix());
         }

         if (!this.firstFrameReported) {
            this.onFirstFrameAvailable();
         }

         this.cameraStatistics.addFrame();
         this.frameObserver.onTextureFrameCaptured(this.captureFormat.width, this.captureFormat.height, oesTextureId, transformMatrix, rotation, timestampNs);
      }
   }

   private void onFirstFrameAvailable() {
      if (this.eventsHandler != null) {
         this.eventsHandler.onFirstFrameAvailable();
      }

      int startTimeMs = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - this.startStartTimeNs);
      videoCapturerAndroidStartTimeMsHistogram.addSample(startTimeMs);
      this.firstFrameReported = true;
   }

   public boolean isScreencast() {
      return false;
   }

   static {
      videoCapturerAndroidResolutionHistogram = org.webrtc2.Histogram.createEnumeration("WebRTC.Android.VideoCapturerAndroid.Resolution", CameraEnumerationAndroid.COMMON_RESOLUTIONS.size());
   }
}
