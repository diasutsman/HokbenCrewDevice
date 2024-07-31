package org.webrtc2;

import android.content.Context;
import android.os.Build.VERSION;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NetworkMonitor {
   private static final String TAG = "NetworkMonitor";
   private static NetworkMonitor instance;
   private final Context applicationContext;
   private final ArrayList<Long> nativeNetworkObservers;
   private final ArrayList<NetworkObserver> networkObservers;
   private NetworkMonitorAutoDetect autoDetector;
   private NetworkMonitorAutoDetect.ConnectionType currentConnectionType;

   private NetworkMonitor(Context context) {
      this.currentConnectionType = NetworkMonitorAutoDetect.ConnectionType.CONNECTION_UNKNOWN;
      assertIsTrue(context != null);
      this.applicationContext = context.getApplicationContext() == null ? context : context.getApplicationContext();
      this.nativeNetworkObservers = new ArrayList();
      this.networkObservers = new ArrayList();
   }

   public static NetworkMonitor init(Context context) {
      if (!isInitialized()) {
         instance = new NetworkMonitor(context);
      }

      return instance;
   }

   public static boolean isInitialized() {
      return instance != null;
   }

   public static NetworkMonitor getInstance() {
      return instance;
   }

   public static void setAutoDetectConnectivityState(boolean shouldAutoDetect) {
      getInstance().setAutoDetectConnectivityStateInternal(shouldAutoDetect);
   }

   private static void assertIsTrue(boolean condition) {
      if (!condition) {
         throw new AssertionError("Expected to be true");
      }
   }

   private void startMonitoring(long nativeObserver) {
      Logging.d("NetworkMonitor", "Start monitoring from native observer " + nativeObserver);
      this.nativeNetworkObservers.add(nativeObserver);
      this.setAutoDetectConnectivityStateInternal(true);
   }

   private void stopMonitoring(long nativeObserver) {
      Logging.d("NetworkMonitor", "Stop monitoring from native observer " + nativeObserver);
      this.setAutoDetectConnectivityStateInternal(false);
      this.nativeNetworkObservers.remove(nativeObserver);
   }

   private static int androidSdkInt() {
      return VERSION.SDK_INT;
   }

   private NetworkMonitorAutoDetect.ConnectionType getCurrentConnectionType() {
      return this.currentConnectionType;
   }

   private long getCurrentDefaultNetId() {
      return this.autoDetector == null ? -1L : this.autoDetector.getDefaultNetId();
   }

   private void destroyAutoDetector() {
      if (this.autoDetector != null) {
         this.autoDetector.destroy();
         this.autoDetector = null;
      }

   }

   private void setAutoDetectConnectivityStateInternal(boolean shouldAutoDetect) {
      if (!shouldAutoDetect) {
         this.destroyAutoDetector();
      } else {
         if (this.autoDetector == null) {
            this.autoDetector = new NetworkMonitorAutoDetect(new NetworkMonitorAutoDetect.Observer() {
               public void onConnectionTypeChanged(NetworkMonitorAutoDetect.ConnectionType newConnectionType) {
                  NetworkMonitor.this.updateCurrentConnectionType(newConnectionType);
               }

               public void onNetworkConnect(NetworkMonitorAutoDetect.NetworkInformation networkInfo) {
                  NetworkMonitor.this.notifyObserversOfNetworkConnect(networkInfo);
               }

               public void onNetworkDisconnect(long networkHandle) {
                  NetworkMonitor.this.notifyObserversOfNetworkDisconnect(networkHandle);
               }
            }, this.applicationContext);
            NetworkMonitorAutoDetect.NetworkState networkState = this.autoDetector.getCurrentNetworkState();
            this.updateCurrentConnectionType(NetworkMonitorAutoDetect.getConnectionType(networkState));
            this.updateActiveNetworkList();
         }

      }
   }

   private void updateCurrentConnectionType(NetworkMonitorAutoDetect.ConnectionType newConnectionType) {
      this.currentConnectionType = newConnectionType;
      this.notifyObserversOfConnectionTypeChange(newConnectionType);
   }

   private void notifyObserversOfConnectionTypeChange(NetworkMonitorAutoDetect.ConnectionType newConnectionType) {
      Iterator i$ = this.nativeNetworkObservers.iterator();

      while(i$.hasNext()) {
         long nativeObserver = (Long)i$.next();
         this.nativeNotifyConnectionTypeChanged(nativeObserver);
      }

      i$ = this.networkObservers.iterator();

      while(i$.hasNext()) {
         NetworkObserver observer = (NetworkObserver)i$.next();
         observer.onConnectionTypeChanged(newConnectionType);
      }

   }

   private void notifyObserversOfNetworkConnect(NetworkMonitorAutoDetect.NetworkInformation networkInfo) {
      Iterator i$ = this.nativeNetworkObservers.iterator();

      while(i$.hasNext()) {
         long nativeObserver = (Long)i$.next();
         this.nativeNotifyOfNetworkConnect(nativeObserver, networkInfo);
      }

   }

   private void notifyObserversOfNetworkDisconnect(long networkHandle) {
      Iterator i$ = this.nativeNetworkObservers.iterator();

      while(i$.hasNext()) {
         long nativeObserver = (Long)i$.next();
         this.nativeNotifyOfNetworkDisconnect(nativeObserver, networkHandle);
      }

   }

   private void updateActiveNetworkList() {
      List<NetworkMonitorAutoDetect.NetworkInformation> networkInfoList = this.autoDetector.getActiveNetworkList();
      if (networkInfoList != null && networkInfoList.size() != 0) {
         NetworkMonitorAutoDetect.NetworkInformation[] networkInfos = new NetworkMonitorAutoDetect.NetworkInformation[networkInfoList.size()];
         networkInfos = (NetworkMonitorAutoDetect.NetworkInformation[])networkInfoList.toArray(networkInfos);
         Iterator i$ = this.nativeNetworkObservers.iterator();

         while(i$.hasNext()) {
            long nativeObserver = (Long)i$.next();
            this.nativeNotifyOfActiveNetworkList(nativeObserver, networkInfos);
         }

      }
   }

   public static void addNetworkObserver(NetworkObserver observer) {
      getInstance().addNetworkObserverInternal(observer);
   }

   private void addNetworkObserverInternal(NetworkObserver observer) {
      this.networkObservers.add(observer);
   }

   public static void removeNetworkObserver(NetworkObserver observer) {
      getInstance().removeNetworkObserverInternal(observer);
   }

   private void removeNetworkObserverInternal(NetworkObserver observer) {
      this.networkObservers.remove(observer);
   }

   public static boolean isOnline() {
      NetworkMonitorAutoDetect.ConnectionType connectionType = getInstance().getCurrentConnectionType();
      return connectionType != NetworkMonitorAutoDetect.ConnectionType.CONNECTION_NONE;
   }

   private native void nativeNotifyConnectionTypeChanged(long var1);

   private native void nativeNotifyOfNetworkConnect(long var1, NetworkMonitorAutoDetect.NetworkInformation var3);

   private native void nativeNotifyOfNetworkDisconnect(long var1, long var3);

   private native void nativeNotifyOfActiveNetworkList(long var1, NetworkMonitorAutoDetect.NetworkInformation[] var3);

   static void resetInstanceForTests(Context context) {
      instance = new NetworkMonitor(context);
   }

   public static NetworkMonitorAutoDetect getAutoDetectorForTest() {
      return getInstance().autoDetector;
   }

   public interface NetworkObserver {
      void onConnectionTypeChanged(NetworkMonitorAutoDetect.ConnectionType var1);
   }
}
