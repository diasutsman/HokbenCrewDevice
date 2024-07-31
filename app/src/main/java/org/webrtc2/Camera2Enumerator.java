package org.webrtc2;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.SystemClock;
import android.os.Build.VERSION;
import android.util.AndroidException;
import android.util.Range;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@TargetApi(21)
public class Camera2Enumerator implements CameraEnumerator {
   private static final String TAG = "Camera2Enumerator";
   private static final double NANO_SECONDS_PER_SECOND = 1.0E9;
   private static final Map<String, List<CameraEnumerationAndroid.CaptureFormat>> cachedSupportedFormats = new HashMap();
   final Context context;
   final CameraManager cameraManager;

   public Camera2Enumerator(Context context) {
      this.context = context;
      this.cameraManager = (CameraManager)context.getSystemService("camera");
   }

   public String[] getDeviceNames() {
      try {
         return this.cameraManager.getCameraIdList();
      } catch (AndroidException var2) {
         AndroidException e = var2;
         Logging.e("Camera2Enumerator", "Camera access exception: " + e);
         return new String[0];
      }
   }

   public boolean isFrontFacing(String deviceName) {
      CameraCharacteristics characteristics = this.getCameraCharacteristics(deviceName);
      return characteristics != null && (Integer)characteristics.get(CameraCharacteristics.LENS_FACING) == 0;
   }

   public boolean isBackFacing(String deviceName) {
      CameraCharacteristics characteristics = this.getCameraCharacteristics(deviceName);
      return characteristics != null && (Integer)characteristics.get(CameraCharacteristics.LENS_FACING) == 1;
   }

   public List<CameraEnumerationAndroid.CaptureFormat> getSupportedFormats(String deviceName) {
      return getSupportedFormats(this.context, deviceName);
   }

   public CameraVideoCapturer createCapturer(String deviceName, CameraVideoCapturer.CameraEventsHandler eventsHandler) {
      return new Camera2Capturer(this.context, deviceName, eventsHandler);
   }

   private CameraCharacteristics getCameraCharacteristics(String deviceName) {
      try {
         return this.cameraManager.getCameraCharacteristics(deviceName);
      } catch (AndroidException var3) {
         AndroidException e = var3;
         Logging.e("Camera2Enumerator", "Camera access exception: " + e);
         return null;
      }
   }

   public static boolean isSupported(Context context) {
      if (VERSION.SDK_INT < 21) {
         return false;
      } else {
         CameraManager cameraManager = (CameraManager)context.getSystemService("camera");

         try {
            String[] cameraIds = cameraManager.getCameraIdList();
            String[] arr$ = cameraIds;
            int len$ = arr$.length;

            for(int i$ = 0; i$ < len$; ++i$) {
               String id = arr$[i$];
               CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
               if ((Integer)characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) == 2) {
                  return false;
               }
            }

            return true;
         } catch (AndroidException var8) {
            AndroidException e = var8;
            Logging.e("Camera2Enumerator", "Camera access exception: " + e);
            return false;
         }
      }
   }

   static int getFpsUnitFactor(Range<Integer>[] fpsRanges) {
      if (fpsRanges.length == 0) {
         return 1000;
      } else {
         return (Integer)fpsRanges[0].getUpper() < 1000 ? 1000 : 1;
      }
   }

   static List<Size> getSupportedSizes(CameraCharacteristics cameraCharacteristics) {
      StreamConfigurationMap streamMap = (StreamConfigurationMap)cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      int supportLevel = (Integer)cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
      android.util.Size[] nativeSizes = streamMap.getOutputSizes(SurfaceTexture.class);
      List<Size> sizes = convertSizes(nativeSizes);
      if (VERSION.SDK_INT < 22 && supportLevel == 2) {
         Rect activeArraySize = (Rect)cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
         ArrayList<Size> filteredSizes = new ArrayList();
         Iterator i$ = sizes.iterator();

         while(i$.hasNext()) {
            Size size = (Size)i$.next();
            if (activeArraySize.width() * size.height == activeArraySize.height() * size.width) {
               filteredSizes.add(size);
            }
         }

         return filteredSizes;
      } else {
         return sizes;
      }
   }

   static List<CameraEnumerationAndroid.CaptureFormat> getSupportedFormats(Context context, String cameraId) {
      return getSupportedFormats((CameraManager)context.getSystemService("camera"), cameraId);
   }

   static List<CameraEnumerationAndroid.CaptureFormat> getSupportedFormats(CameraManager cameraManager, String cameraId) {
      synchronized(cachedSupportedFormats) {
         if (cachedSupportedFormats.containsKey(cameraId)) {
            return (List)cachedSupportedFormats.get(cameraId);
         } else {
            Logging.d("Camera2Enumerator", "Get supported formats for camera index " + cameraId + ".");
            long startTimeMs = SystemClock.elapsedRealtime();

            CameraCharacteristics cameraCharacteristics;
            try {
               cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            } catch (Exception var19) {
               Exception ex = var19;
               Logging.e("Camera2Enumerator", "getCameraCharacteristics(): " + ex);
               return new ArrayList();
            }

            StreamConfigurationMap streamMap = (StreamConfigurationMap)cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Range<Integer>[] fpsRanges = (Range[])cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> framerateRanges = convertFramerates(fpsRanges, getFpsUnitFactor(fpsRanges));
            List<Size> sizes = getSupportedSizes(cameraCharacteristics);
            int defaultMaxFps = 0;

            CameraEnumerationAndroid.CaptureFormat.FramerateRange framerateRange;
            for(Iterator i$ = framerateRanges.iterator(); i$.hasNext(); defaultMaxFps = Math.max(defaultMaxFps, framerateRange.max)) {
               framerateRange = (CameraEnumerationAndroid.CaptureFormat.FramerateRange)i$.next();
            }

            List<CameraEnumerationAndroid.CaptureFormat> formatList = new ArrayList();
            Iterator i$ = sizes.iterator();

            while(i$.hasNext()) {
               Size size = (Size)i$.next();
               long minFrameDurationNs = 0L;

               try {
                  minFrameDurationNs = streamMap.getOutputMinFrameDuration(SurfaceTexture.class, new android.util.Size(size.width, size.height));
               } catch (Exception var18) {
               }

               int maxFps = minFrameDurationNs == 0L ? defaultMaxFps : (int)Math.round(1.0E9 / (double)minFrameDurationNs) * 1000;
               formatList.add(new CameraEnumerationAndroid.CaptureFormat(size.width, size.height, 0, maxFps));
               Logging.d("Camera2Enumerator", "Format: " + size.width + "x" + size.height + "@" + maxFps);
            }

            cachedSupportedFormats.put(cameraId, formatList);
            long endTimeMs = SystemClock.elapsedRealtime();
            Logging.d("Camera2Enumerator", "Get supported formats for camera index " + cameraId + " done." + " Time spent: " + (endTimeMs - startTimeMs) + " ms.");
            return formatList;
         }
      }
   }

   private static List<Size> convertSizes(android.util.Size[] cameraSizes) {
      List<Size> sizes = new ArrayList();
      android.util.Size[] arr$ = cameraSizes;
      int len$ = arr$.length;

      for(int i$ = 0; i$ < len$; ++i$) {
         android.util.Size size = arr$[i$];
         sizes.add(new Size(size.getWidth(), size.getHeight()));
      }

      return sizes;
   }

   static List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> convertFramerates(Range<Integer>[] arrayRanges, int unitFactor) {
      List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> ranges = new ArrayList();
      Range[] arr$ = arrayRanges;
      int len$ = arr$.length;

      for(int i$ = 0; i$ < len$; ++i$) {
         Range<Integer> range = arr$[i$];
         ranges.add(new CameraEnumerationAndroid.CaptureFormat.FramerateRange((Integer)range.getLower() * unitFactor, (Integer)range.getUpper() * unitFactor));
      }

      return ranges;
   }
}
