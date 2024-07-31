package org.webrtc2;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.Arrays;

public abstract class CameraCapturer implements CameraVideoCapturer {
   private static final String TAG = "CameraCapturer";
   private static final int MAX_OPEN_CAMERA_ATTEMPTS = 3;
   private static final int OPEN_CAMERA_DELAY_MS = 500;
   private static final int OPEN_CAMERA_TIMEOUT = 10000;
   private final CameraEnumerator cameraEnumerator;
   private final CameraEventsHandler eventsHandler;
   private final Handler uiThreadHandler;
   private final org.webrtc2.CameraSession.CreateSessionCallback createSessionCallback = new org.webrtc2.CameraSession.CreateSessionCallback() {
      public void onDone(org.webrtc2.CameraSession session) {
         CameraCapturer.this.checkIsOnCameraThread();
         Logging.d("CameraCapturer", "Create session done");
         CameraCapturer.this.uiThreadHandler.removeCallbacks(CameraCapturer.this.openCameraTimeoutRunnable);
         synchronized(CameraCapturer.this.stateLock) {
            CameraCapturer.this.capturerObserver.onCapturerStarted(true);
            CameraCapturer.this.sessionOpening = false;
            CameraCapturer.this.currentSession = session;
            CameraCapturer.this.cameraStatistics = new CameraStatistics(CameraCapturer.this.surfaceHelper, CameraCapturer.this.eventsHandler);
            CameraCapturer.this.firstFrameObserved = false;
            CameraCapturer.this.stateLock.notifyAll();
            if (CameraCapturer.this.switchState == SwitchState.IN_PROGRESS) {
               if (CameraCapturer.this.switchEventsHandler != null) {
                  CameraCapturer.this.switchEventsHandler.onCameraSwitchDone(CameraCapturer.this.cameraEnumerator.isFrontFacing(CameraCapturer.this.cameraName));
                  CameraCapturer.this.switchEventsHandler = null;
               }

               CameraCapturer.this.switchState = SwitchState.IDLE;
            } else if (CameraCapturer.this.switchState == SwitchState.PENDING) {
               CameraCapturer.this.switchState = SwitchState.IDLE;
               CameraCapturer.this.switchCameraInternal(CameraCapturer.this.switchEventsHandler);
            }

         }
      }

      public void onFailure(String error) {
         CameraCapturer.this.checkIsOnCameraThread();
         CameraCapturer.this.uiThreadHandler.removeCallbacks(CameraCapturer.this.openCameraTimeoutRunnable);
         synchronized(CameraCapturer.this.stateLock) {
            CameraCapturer.this.capturerObserver.onCapturerStarted(false);
            CameraCapturer.this.openAttemptsRemaining--;
            if (CameraCapturer.this.openAttemptsRemaining <= 0) {
               Logging.w("CameraCapturer", "Opening camera failed, passing: " + error);
               CameraCapturer.this.sessionOpening = false;
               CameraCapturer.this.stateLock.notifyAll();
               if (CameraCapturer.this.switchState != SwitchState.IDLE) {
                  if (CameraCapturer.this.switchEventsHandler != null) {
                     CameraCapturer.this.switchEventsHandler.onCameraSwitchError(error);
                     CameraCapturer.this.switchEventsHandler = null;
                  }

                  CameraCapturer.this.switchState = SwitchState.IDLE;
               }

               CameraCapturer.this.eventsHandler.onCameraError(error);
            } else {
               Logging.w("CameraCapturer", "Opening camera failed, retry: " + error);
               CameraCapturer.this.createSessionInternal(500);
            }

         }
      }
   };
   private final org.webrtc2.CameraSession.Events cameraSessionEventsHandler = new org.webrtc2.CameraSession.Events() {
      public void onCameraOpening() {
         CameraCapturer.this.checkIsOnCameraThread();
         synchronized(CameraCapturer.this.stateLock) {
            if (CameraCapturer.this.currentSession != null) {
               Logging.w("CameraCapturer", "onCameraOpening while session was open.");
            } else {
               CameraCapturer.this.eventsHandler.onCameraOpening(CameraCapturer.this.cameraName);
            }
         }
      }

      public void onCameraError(org.webrtc2.CameraSession session, String error) {
         CameraCapturer.this.checkIsOnCameraThread();
         synchronized(CameraCapturer.this.stateLock) {
            if (session != CameraCapturer.this.currentSession) {
               Logging.w("CameraCapturer", "onCameraError from another session: " + error);
            } else {
               CameraCapturer.this.eventsHandler.onCameraError(error);
               CameraCapturer.this.stopCapture();
            }
         }
      }

      public void onCameraDisconnected(org.webrtc2.CameraSession session) {
         CameraCapturer.this.checkIsOnCameraThread();
         synchronized(CameraCapturer.this.stateLock) {
            if (session != CameraCapturer.this.currentSession) {
               Logging.w("CameraCapturer", "onCameraDisconnected from another session.");
            } else {
               CameraCapturer.this.eventsHandler.onCameraDisconnected();
               CameraCapturer.this.stopCapture();
            }
         }
      }

      public void onCameraClosed(org.webrtc2.CameraSession session) {
         CameraCapturer.this.checkIsOnCameraThread();
         synchronized(CameraCapturer.this.stateLock) {
            if (session != CameraCapturer.this.currentSession && CameraCapturer.this.currentSession != null) {
               Logging.d("CameraCapturer", "onCameraClosed from another session.");
            } else {
               CameraCapturer.this.eventsHandler.onCameraClosed();
            }
         }
      }

      public void onByteBufferFrameCaptured(org.webrtc2.CameraSession session, byte[] data, int width, int height, int rotation, long timestamp) {
         CameraCapturer.this.checkIsOnCameraThread();
         synchronized(CameraCapturer.this.stateLock) {
            if (session != CameraCapturer.this.currentSession) {
               Logging.w("CameraCapturer", "onByteBufferFrameCaptured from another session.");
            } else {
               if (!CameraCapturer.this.firstFrameObserved) {
                  CameraCapturer.this.eventsHandler.onFirstFrameAvailable();
                  CameraCapturer.this.firstFrameObserved = true;
               }

               CameraCapturer.this.cameraStatistics.addFrame();
               CameraCapturer.this.capturerObserver.onByteBufferFrameCaptured(data, width, height, rotation, timestamp);
            }
         }
      }

      public void onTextureFrameCaptured(org.webrtc2.CameraSession session, int width, int height, int oesTextureId, float[] transformMatrix, int rotation, long timestamp) {
         CameraCapturer.this.checkIsOnCameraThread();
         synchronized(CameraCapturer.this.stateLock) {
            if (session != CameraCapturer.this.currentSession) {
               Logging.w("CameraCapturer", "onTextureFrameCaptured from another session.");
               CameraCapturer.this.surfaceHelper.returnTextureFrame();
            } else {
               if (!CameraCapturer.this.firstFrameObserved) {
                  CameraCapturer.this.eventsHandler.onFirstFrameAvailable();
                  CameraCapturer.this.firstFrameObserved = true;
               }

               CameraCapturer.this.cameraStatistics.addFrame();
               CameraCapturer.this.capturerObserver.onTextureFrameCaptured(width, height, oesTextureId, transformMatrix, rotation, timestamp);
            }
         }
      }
   };
   private final Runnable openCameraTimeoutRunnable = new Runnable() {
      public void run() {
         CameraCapturer.this.eventsHandler.onCameraError("Camera failed to start within timeout.");
      }
   };
   private Handler cameraThreadHandler;
   private Context applicationContext;
   private CapturerObserver capturerObserver;
   private SurfaceTextureHelper surfaceHelper;
   private final Object stateLock = new Object();
   private boolean sessionOpening;
   private org.webrtc2.CameraSession currentSession;
   private String cameraName;
   private int width;
   private int height;
   private int framerate;
   private int openAttemptsRemaining;
   private SwitchState switchState;
   private CameraSwitchHandler switchEventsHandler;
   private CameraStatistics cameraStatistics;
   private boolean firstFrameObserved;

   public CameraCapturer(String cameraName, CameraEventsHandler eventsHandler, CameraEnumerator cameraEnumerator) {
      this.switchState = SwitchState.IDLE;
      if (eventsHandler == null) {
         eventsHandler = new CameraEventsHandler() {
            public void onCameraError(String errorDescription) {
            }

            public void onCameraDisconnected() {
            }

            public void onCameraFreezed(String errorDescription) {
            }

            public void onCameraOpening(String cameraName) {
            }

            public void onFirstFrameAvailable() {
            }

            public void onCameraClosed() {
            }
         };
      }

      this.eventsHandler = eventsHandler;
      this.cameraEnumerator = cameraEnumerator;
      this.cameraName = cameraName;
      this.uiThreadHandler = new Handler(Looper.getMainLooper());
      String[] deviceNames = cameraEnumerator.getDeviceNames();
      if (deviceNames.length == 0) {
         throw new RuntimeException("No cameras attached.");
      } else if (!Arrays.asList(deviceNames).contains(this.cameraName)) {
         throw new IllegalArgumentException("Camera name " + this.cameraName + " does not match any known camera device.");
      }
   }

   public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext, CapturerObserver capturerObserver) {
      this.applicationContext = applicationContext;
      this.capturerObserver = capturerObserver;
      this.surfaceHelper = surfaceTextureHelper;
      this.cameraThreadHandler = surfaceTextureHelper == null ? null : surfaceTextureHelper.getHandler();
   }

   public void startCapture(int width, int height, int framerate) {
      Logging.d("CameraCapturer", "startCapture: " + width + "x" + height + "@" + framerate);
      synchronized(this.stateLock) {
         if (!this.sessionOpening && this.currentSession == null) {
            this.width = width;
            this.height = height;
            this.framerate = framerate;
            this.sessionOpening = true;
            this.openAttemptsRemaining = 3;
            this.createSessionInternal(0);
         } else {
            Logging.w("CameraCapturer", "Session already open");
         }
      }
   }

   private void createSessionInternal(int delayMs) {
      this.uiThreadHandler.postDelayed(this.openCameraTimeoutRunnable, (long)(delayMs + 10000));
      this.cameraThreadHandler.postDelayed(new Runnable() {
         public void run() {
            CameraCapturer.this.createCameraSession(CameraCapturer.this.createSessionCallback, CameraCapturer.this.cameraSessionEventsHandler, CameraCapturer.this.applicationContext, CameraCapturer.this.surfaceHelper, CameraCapturer.this.cameraName, CameraCapturer.this.width, CameraCapturer.this.height, CameraCapturer.this.framerate);
         }
      }, (long)delayMs);
   }

   public void stopCapture() {
      Logging.d("CameraCapturer", "Stop capture");
      synchronized(this.stateLock) {
         while(this.sessionOpening) {
            Logging.d("CameraCapturer", "Stop capture: Waiting for session to open");
            ThreadUtils.waitUninterruptibly(this.stateLock);
         }

         if (this.currentSession != null) {
            Logging.d("CameraCapturer", "Stop capture: Nulling session");
            this.cameraStatistics.release();
            this.cameraStatistics = null;
            final org.webrtc2.CameraSession oldSession = this.currentSession;
            this.cameraThreadHandler.post(new Runnable() {
               public void run() {
                  oldSession.stop();
               }
            });
            this.currentSession = null;
            this.capturerObserver.onCapturerStopped();
         } else {
            Logging.d("CameraCapturer", "Stop capture: No session open");
         }
      }

      Logging.d("CameraCapturer", "Stop capture done");
   }

   public void changeCaptureFormat(int width, int height, int framerate) {
      Logging.d("CameraCapturer", "changeCaptureFormat: " + width + "x" + height + "@" + framerate);
      synchronized(this.stateLock) {
         this.stopCapture();
         this.startCapture(width, height, framerate);
      }
   }

   public void dispose() {
      Logging.d("CameraCapturer", "dispose");
      this.stopCapture();
   }

   public void switchCamera(final CameraSwitchHandler switchEventsHandler) {
      Logging.d("CameraCapturer", "switchCamera");
      this.cameraThreadHandler.post(new Runnable() {
         public void run() {
            CameraCapturer.this.switchCameraInternal(switchEventsHandler);
         }
      });
   }

   public boolean isScreencast() {
      return false;
   }

   public void printStackTrace() {
      Thread cameraThread = null;
      if (this.cameraThreadHandler != null) {
         cameraThread = this.cameraThreadHandler.getLooper().getThread();
      }

      if (cameraThread != null) {
         StackTraceElement[] cameraStackTrace = cameraThread.getStackTrace();
         if (cameraStackTrace.length > 0) {
            Logging.d("CameraCapturer", "CameraCapturer stack trace:");
            StackTraceElement[] arr$ = cameraStackTrace;
            int len$ = arr$.length;

            for(int i$ = 0; i$ < len$; ++i$) {
               StackTraceElement traceElem = arr$[i$];
               Logging.d("CameraCapturer", traceElem.toString());
            }
         }
      }

   }

   private void switchCameraInternal(CameraSwitchHandler switchEventsHandler) {
      Logging.d("CameraCapturer", "switchCamera internal");
      String[] deviceNames = this.cameraEnumerator.getDeviceNames();
      if (deviceNames.length < 2) {
         if (switchEventsHandler != null) {
            switchEventsHandler.onCameraSwitchError("No camera to switch to.");
         }

      } else {
         synchronized(this.stateLock) {
            if (this.switchState != SwitchState.IDLE) {
               Logging.d("CameraCapturer", "switchCamera switchInProgress");
               if (switchEventsHandler != null) {
                  switchEventsHandler.onCameraSwitchError("Camera switch already in progress.");
               }

               return;
            }

            if (!this.sessionOpening && this.currentSession == null) {
               Logging.d("CameraCapturer", "switchCamera: No session open");
               if (switchEventsHandler != null) {
                  switchEventsHandler.onCameraSwitchError("Camera is not running.");
               }

               return;
            }

            this.switchEventsHandler = switchEventsHandler;
            if (this.sessionOpening) {
               this.switchState = SwitchState.PENDING;
               return;
            }

            this.switchState = SwitchState.IN_PROGRESS;
            Logging.d("CameraCapturer", "switchCamera: Stopping session");
            this.cameraStatistics.release();
            this.cameraStatistics = null;
            final org.webrtc2.CameraSession oldSession = this.currentSession;
            this.cameraThreadHandler.post(new Runnable() {
               public void run() {
                  oldSession.stop();
               }
            });
            this.currentSession = null;
            int cameraNameIndex = Arrays.asList(deviceNames).indexOf(this.cameraName);
            this.cameraName = deviceNames[(cameraNameIndex + 1) % deviceNames.length];
            this.sessionOpening = true;
            this.openAttemptsRemaining = 1;
            this.createSessionInternal(0);
         }

         Logging.d("CameraCapturer", "switchCamera done");
      }
   }

   private void checkIsOnCameraThread() {
      if (Thread.currentThread() != this.cameraThreadHandler.getLooper().getThread()) {
         Logging.e("CameraCapturer", "Check is on camera thread failed.");
         throw new RuntimeException("Not on camera thread.");
      }
   }

   protected String getCameraName() {
      synchronized(this.stateLock) {
         return this.cameraName;
      }
   }

   protected abstract void createCameraSession(org.webrtc2.CameraSession.CreateSessionCallback var1, org.webrtc2.CameraSession.Events var2, Context var3, SurfaceTextureHelper var4, String var5, int var6, int var7, int var8);

   static enum SwitchState {
      IDLE,
      PENDING,
      IN_PROGRESS;
   }
}
