package org.webrtc2;

import android.hardware.Camera;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Camera1Enumerator implements CameraEnumerator {
   private static final String TAG = "Camera1Enumerator";
   private static List<List<CameraEnumerationAndroid.CaptureFormat>> cachedSupportedFormats;
   private final boolean captureToTexture;

   public Camera1Enumerator() {
      this(true);
   }

   public Camera1Enumerator(boolean captureToTexture) {
      this.captureToTexture = captureToTexture;
   }

   public String[] getDeviceNames() {
      ArrayList<String> namesList = new ArrayList();

      for(int i = 0; i < Camera.getNumberOfCameras(); ++i) {
         String name = getDeviceName(i);
         if (name != null) {
            namesList.add(name);
            Logging.d("Camera1Enumerator", "Index: " + i + ". " + name);
         } else {
            Logging.e("Camera1Enumerator", "Index: " + i + ". Failed to query camera name.");
         }
      }

      String[] namesArray = new String[namesList.size()];
      return (String[])namesList.toArray(namesArray);
   }

   public boolean isFrontFacing(String deviceName) {
      Camera.CameraInfo info = getCameraInfo(getCameraIndex(deviceName));
      return info != null && info.facing == 1;
   }

   public boolean isBackFacing(String deviceName) {
      Camera.CameraInfo info = getCameraInfo(getCameraIndex(deviceName));
      return info != null && info.facing == 0;
   }

   public List<CameraEnumerationAndroid.CaptureFormat> getSupportedFormats(String deviceName) {
      return getSupportedFormats(getCameraIndex(deviceName));
   }

   public CameraVideoCapturer createCapturer(String deviceName, CameraVideoCapturer.CameraEventsHandler eventsHandler) {
      return new Camera1Capturer(deviceName, eventsHandler, this.captureToTexture);
   }

   private static Camera.CameraInfo getCameraInfo(int index) {
      Camera.CameraInfo info = new Camera.CameraInfo();

      try {
         Camera.getCameraInfo(index, info);
         return info;
      } catch (Exception var3) {
         Exception e = var3;
         Logging.e("Camera1Enumerator", "getCameraInfo failed on index " + index, e);
         return null;
      }
   }

   static synchronized List<CameraEnumerationAndroid.CaptureFormat> getSupportedFormats(int cameraId) {
      if (cachedSupportedFormats == null) {
         cachedSupportedFormats = new ArrayList();

         for(int i = 0; i < Camera.getNumberOfCameras(); ++i) {
            cachedSupportedFormats.add(enumerateFormats(i));
         }
      }

      return (List)cachedSupportedFormats.get(cameraId);
   }

   private static List<CameraEnumerationAndroid.CaptureFormat> enumerateFormats(int cameraId) {
      Logging.d("Camera1Enumerator", "Get supported formats for camera index " + cameraId + ".");
      long startTimeMs = SystemClock.elapsedRealtime();
      Camera camera = null;

      Camera.Parameters parameters;
      label94: {
         ArrayList var6;
         try {
            Logging.d("Camera1Enumerator", "Opening camera with index " + cameraId);
            camera = Camera.open(cameraId);
            parameters = camera.getParameters();
            break label94;
         } catch (RuntimeException var15) {
            RuntimeException e = var15;
            Logging.e("Camera1Enumerator", "Open camera failed on camera index " + cameraId, e);
            var6 = new ArrayList();
         } finally {
            if (camera != null) {
               camera.release();
            }

         }

         return var6;
      }

      List<CameraEnumerationAndroid.CaptureFormat> formatList = new ArrayList();

      try {
         int minFps = 0;
         int maxFps = 0;
         List<int[]> listFpsRange = parameters.getSupportedPreviewFpsRange();
         if (listFpsRange != null) {
            int[] range = (int[])listFpsRange.get(listFpsRange.size() - 1);
            minFps = range[0];
            maxFps = range[1];
         }

         Iterator i$ = parameters.getSupportedPreviewSizes().iterator();

         while(i$.hasNext()) {
            Camera.Size size = (Camera.Size)i$.next();
            formatList.add(new CameraEnumerationAndroid.CaptureFormat(size.width, size.height, minFps, maxFps));
         }
      } catch (Exception var14) {
         Exception e = var14;
         Logging.e("Camera1Enumerator", "getSupportedFormats() failed on camera index " + cameraId, e);
      }

      long endTimeMs = SystemClock.elapsedRealtime();
      Logging.d("Camera1Enumerator", "Get supported formats for camera index " + cameraId + " done." + " Time spent: " + (endTimeMs - startTimeMs) + " ms.");
      return formatList;
   }

   static List<Size> convertSizes(List<Camera.Size> cameraSizes) {
      List<Size> sizes = new ArrayList();
      Iterator i$ = cameraSizes.iterator();

      while(i$.hasNext()) {
         Camera.Size size = (Camera.Size)i$.next();
         sizes.add(new Size(size.width, size.height));
      }

      return sizes;
   }

   static List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> convertFramerates(List<int[]> arrayRanges) {
      List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> ranges = new ArrayList();
      Iterator i$ = arrayRanges.iterator();

      while(i$.hasNext()) {
         int[] range = (int[])i$.next();
         ranges.add(new CameraEnumerationAndroid.CaptureFormat.FramerateRange(range[0], range[1]));
      }

      return ranges;
   }

   static int getCameraIndex(String deviceName) {
      Logging.d("Camera1Enumerator", "getCameraIndex: " + deviceName);

      for(int i = 0; i < Camera.getNumberOfCameras(); ++i) {
         if (deviceName.equals(getDeviceName(i))) {
            return i;
         }
      }

      throw new IllegalArgumentException("No such camera: " + deviceName);
   }

   static String getDeviceName(int index) {
      Camera.CameraInfo info = getCameraInfo(index);
      if (info == null) {
         return null;
      } else {
         String facing = info.facing == 1 ? "front" : "back";
         return "Camera " + index + ", Facing " + facing + ", Orientation " + info.orientation;
      }
   }
}
