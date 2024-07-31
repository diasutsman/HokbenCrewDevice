package org.webrtc2;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.os.Build.VERSION;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NetworkMonitorAutoDetect extends BroadcastReceiver {
   static final long INVALID_NET_ID = -1L;
   private static final String TAG = "NetworkMonitorAutoDetect";
   private final Observer observer;
   private final IntentFilter intentFilter;
   private final Context context;
   private final ConnectivityManager.NetworkCallback mobileNetworkCallback;
   private final ConnectivityManager.NetworkCallback allNetworkCallback;
   private ConnectivityManagerDelegate connectivityManagerDelegate;
   private WifiManagerDelegate wifiManagerDelegate;
   private boolean isRegistered;
   private ConnectionType connectionType;
   private String wifiSSID;

   @SuppressLint({"NewApi"})
   public NetworkMonitorAutoDetect(Observer observer, Context context) {
      this.observer = observer;
      this.context = context;
      this.connectivityManagerDelegate = new ConnectivityManagerDelegate(context);
      this.wifiManagerDelegate = new WifiManagerDelegate(context);
      NetworkState networkState = this.connectivityManagerDelegate.getNetworkState();
      this.connectionType = getConnectionType(networkState);
      this.wifiSSID = this.getWifiSSID(networkState);
      this.intentFilter = new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE");
      this.registerReceiver();
      if (this.connectivityManagerDelegate.supportNetworkCallback()) {
         ConnectivityManager.NetworkCallback tempNetworkCallback = new ConnectivityManager.NetworkCallback();

         try {
            this.connectivityManagerDelegate.requestMobileNetwork(tempNetworkCallback);
         } catch (SecurityException var6) {
            org.webrtc2.Logging.w("NetworkMonitorAutoDetect", "Unable to obtain permission to request a cellular network.");
            tempNetworkCallback = null;
         }

         this.mobileNetworkCallback = tempNetworkCallback;
         this.allNetworkCallback = new SimpleNetworkCallback();
         this.connectivityManagerDelegate.registerNetworkCallback(this.allNetworkCallback);
      } else {
         this.mobileNetworkCallback = null;
         this.allNetworkCallback = null;
      }

   }

   void setConnectivityManagerDelegateForTests(ConnectivityManagerDelegate delegate) {
      this.connectivityManagerDelegate = delegate;
   }

   void setWifiManagerDelegateForTests(WifiManagerDelegate delegate) {
      this.wifiManagerDelegate = delegate;
   }

   boolean isReceiverRegisteredForTesting() {
      return this.isRegistered;
   }

   List<NetworkInformation> getActiveNetworkList() {
      return this.connectivityManagerDelegate.getActiveNetworkList();
   }

   public void destroy() {
      if (this.allNetworkCallback != null) {
         this.connectivityManagerDelegate.releaseCallback(this.allNetworkCallback);
      }

      if (this.mobileNetworkCallback != null) {
         this.connectivityManagerDelegate.releaseCallback(this.mobileNetworkCallback);
      }

      this.unregisterReceiver();
   }

   private void registerReceiver() {
      if (!this.isRegistered) {
         this.isRegistered = true;
         this.context.registerReceiver(this, this.intentFilter);
      }
   }

   private void unregisterReceiver() {
      if (this.isRegistered) {
         this.isRegistered = false;
         this.context.unregisterReceiver(this);
      }
   }

   public NetworkState getCurrentNetworkState() {
      return this.connectivityManagerDelegate.getNetworkState();
   }

   public long getDefaultNetId() {
      return this.connectivityManagerDelegate.getDefaultNetId();
   }

   public static ConnectionType getConnectionType(NetworkState networkState) {
      if (!networkState.isConnected()) {
         return ConnectionType.CONNECTION_NONE;
      } else {
         switch (networkState.getNetworkType()) {
            case 0:
               switch (networkState.getNetworkSubType()) {
                  case 1:
                  case 2:
                  case 4:
                  case 7:
                  case 11:
                     return ConnectionType.CONNECTION_2G;
                  case 3:
                  case 5:
                  case 6:
                  case 8:
                  case 9:
                  case 10:
                  case 12:
                  case 14:
                  case 15:
                     return ConnectionType.CONNECTION_3G;
                  case 13:
                     return ConnectionType.CONNECTION_4G;
                  default:
                     return ConnectionType.CONNECTION_UNKNOWN_CELLULAR;
               }
            case 1:
               return ConnectionType.CONNECTION_WIFI;
            case 2:
            case 3:
            case 4:
            case 5:
            case 8:
            default:
               return ConnectionType.CONNECTION_UNKNOWN;
            case 6:
               return ConnectionType.CONNECTION_4G;
            case 7:
               return ConnectionType.CONNECTION_BLUETOOTH;
            case 9:
               return ConnectionType.CONNECTION_ETHERNET;
         }
      }
   }

   private String getWifiSSID(NetworkState networkState) {
      return getConnectionType(networkState) != ConnectionType.CONNECTION_WIFI ? "" : this.wifiManagerDelegate.getWifiSSID();
   }

   public void onReceive(Context context, Intent intent) {
      NetworkState networkState = this.getCurrentNetworkState();
      if ("android.net.conn.CONNECTIVITY_CHANGE".equals(intent.getAction())) {
         this.connectionTypeChanged(networkState);
      }

   }

   private void connectionTypeChanged(NetworkState networkState) {
      ConnectionType newConnectionType = getConnectionType(networkState);
      String newWifiSSID = this.getWifiSSID(networkState);
      if (newConnectionType != this.connectionType || !newWifiSSID.equals(this.wifiSSID)) {
         this.connectionType = newConnectionType;
         this.wifiSSID = newWifiSSID;
         org.webrtc2.Logging.d("NetworkMonitorAutoDetect", "Network connectivity changed, type is: " + this.connectionType);
         this.observer.onConnectionTypeChanged(newConnectionType);
      }
   }

   @SuppressLint({"NewApi"})
   private static long networkToNetId(Network network) {
      return VERSION.SDK_INT >= 23 ? network.getNetworkHandle() : (long)Integer.parseInt(network.toString());
   }

   public interface Observer {
      void onConnectionTypeChanged(ConnectionType var1);

      void onNetworkConnect(NetworkInformation var1);

      void onNetworkDisconnect(long var1);
   }

   static class WifiManagerDelegate {
      private final Context context;

      WifiManagerDelegate(Context context) {
         this.context = context;
      }

      WifiManagerDelegate() {
         this.context = null;
      }

      String getWifiSSID() {
         Intent intent = this.context.registerReceiver((BroadcastReceiver)null, new IntentFilter("android.net.wifi.STATE_CHANGE"));
         if (intent != null) {
            WifiInfo wifiInfo = (WifiInfo)intent.getParcelableExtra("wifiInfo");
            if (wifiInfo != null) {
               String ssid = wifiInfo.getSSID();
               if (ssid != null) {
                  return ssid;
               }
            }
         }

         return "";
      }
   }

   static class ConnectivityManagerDelegate {
      private final ConnectivityManager connectivityManager;
      // $FF: synthetic field
      static final boolean $assertionsDisabled = true;

      ConnectivityManagerDelegate(Context context) {
         this.connectivityManager = (ConnectivityManager)context.getSystemService("connectivity");
      }

      ConnectivityManagerDelegate() {
         this.connectivityManager = null;
      }

      NetworkState getNetworkState() {
         return this.connectivityManager == null ? new NetworkState(false, -1, -1) : this.getNetworkState(this.connectivityManager.getActiveNetworkInfo());
      }

      @SuppressLint({"NewApi"})
      NetworkState getNetworkState(Network network) {
         return this.connectivityManager == null ? new NetworkState(false, -1, -1) : this.getNetworkState(this.connectivityManager.getNetworkInfo(network));
      }

      NetworkState getNetworkState(NetworkInfo networkInfo) {
         return networkInfo != null && networkInfo.isConnected() ? new NetworkState(true, networkInfo.getType(), networkInfo.getSubtype()) : new NetworkState(false, -1, -1);
      }

      @SuppressLint({"NewApi"})
      Network[] getAllNetworks() {
         return this.connectivityManager == null ? new Network[0] : this.connectivityManager.getAllNetworks();
      }

      List<NetworkInformation> getActiveNetworkList() {
         if (!this.supportNetworkCallback()) {
            return null;
         } else {
            ArrayList<NetworkInformation> netInfoList = new ArrayList();
            Network[] arr$ = this.getAllNetworks();
            int len$ = arr$.length;

            for(int i$ = 0; i$ < len$; ++i$) {
               Network network = arr$[i$];
               NetworkInformation info = this.networkToInfo(network);
               if (info != null) {
                  netInfoList.add(info);
               }
            }

            return netInfoList;
         }
      }

      @SuppressLint({"NewApi"})
      long getDefaultNetId() {
         if (!this.supportNetworkCallback()) {
            return -1L;
         } else {
            NetworkInfo defaultNetworkInfo = this.connectivityManager.getActiveNetworkInfo();
            if (defaultNetworkInfo == null) {
               return -1L;
            } else {
               Network[] networks = this.getAllNetworks();
               long defaultNetId = -1L;
               Network[] arr$ = networks;
               int len$ = arr$.length;

               for(int i$ = 0; i$ < len$; ++i$) {
                  Network network = arr$[i$];
                  if (this.hasInternetCapability(network)) {
                     NetworkInfo networkInfo = this.connectivityManager.getNetworkInfo(network);
                     if (networkInfo != null && networkInfo.getType() == defaultNetworkInfo.getType()) {
                        if (!$assertionsDisabled && defaultNetId != -1L) {
                           throw new AssertionError();
                        }

                        defaultNetId = NetworkMonitorAutoDetect.networkToNetId(network);
                     }
                  }
               }

               return defaultNetId;
            }
         }
      }

      @SuppressLint({"NewApi"})
      private NetworkInformation networkToInfo(Network network) {
         LinkProperties linkProperties = this.connectivityManager.getLinkProperties(network);
         if (linkProperties == null) {
            org.webrtc2.Logging.w("NetworkMonitorAutoDetect", "Detected unknown network: " + network.toString());
            return null;
         } else if (linkProperties.getInterfaceName() == null) {
            org.webrtc2.Logging.w("NetworkMonitorAutoDetect", "Null interface name for network " + network.toString());
            return null;
         } else {
            NetworkState networkState = this.getNetworkState(network);
            if (networkState.connected && networkState.getNetworkType() == 17) {
               networkState = this.getNetworkState();
            }

            ConnectionType connectionType = NetworkMonitorAutoDetect.getConnectionType(networkState);
            if (connectionType == ConnectionType.CONNECTION_NONE) {
               org.webrtc2.Logging.d("NetworkMonitorAutoDetect", "Network " + network.toString() + " is disconnected");
               return null;
            } else {
               if (connectionType == ConnectionType.CONNECTION_UNKNOWN || connectionType == ConnectionType.CONNECTION_UNKNOWN_CELLULAR) {
                  org.webrtc2.Logging.d("NetworkMonitorAutoDetect", "Network " + network.toString() + " connection type is " + connectionType + " because it has type " + networkState.getNetworkType() + " and subtype " + networkState.getNetworkSubType());
               }

               NetworkInformation networkInformation = new NetworkInformation(linkProperties.getInterfaceName(), connectionType, NetworkMonitorAutoDetect.networkToNetId(network), this.getIPAddresses(linkProperties));
               return networkInformation;
            }
         }
      }

      @SuppressLint({"NewApi"})
      boolean hasInternetCapability(Network network) {
         if (this.connectivityManager == null) {
            return false;
         } else {
            NetworkCapabilities capabilities = this.connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasCapability(12);
         }
      }

      @SuppressLint({"NewApi"})
      public void registerNetworkCallback(ConnectivityManager.NetworkCallback networkCallback) {
         this.connectivityManager.registerNetworkCallback((new NetworkRequest.Builder()).addCapability(12).build(), networkCallback);
      }

      @SuppressLint({"NewApi"})
      public void requestMobileNetwork(ConnectivityManager.NetworkCallback networkCallback) {
         NetworkRequest.Builder builder = new NetworkRequest.Builder();
         builder.addCapability(12).addTransportType(0);
         this.connectivityManager.requestNetwork(builder.build(), networkCallback);
      }

      @SuppressLint({"NewApi"})
      IPAddress[] getIPAddresses(LinkProperties linkProperties) {
         IPAddress[] ipAddresses = new IPAddress[linkProperties.getLinkAddresses().size()];
         int i = 0;

         for(Iterator i$ = linkProperties.getLinkAddresses().iterator(); i$.hasNext(); ++i) {
            LinkAddress linkAddress = (LinkAddress)i$.next();
            ipAddresses[i] = new IPAddress(linkAddress.getAddress().getAddress());
         }

         return ipAddresses;
      }

      @SuppressLint({"NewApi"})
      public void releaseCallback(ConnectivityManager.NetworkCallback networkCallback) {
         if (this.supportNetworkCallback()) {
            org.webrtc2.Logging.d("NetworkMonitorAutoDetect", "Unregister network callback");
            this.connectivityManager.unregisterNetworkCallback(networkCallback);
         }

      }

      public boolean supportNetworkCallback() {
         return VERSION.SDK_INT >= 21 && this.connectivityManager != null;
      }

      static {
         boolean var10000;
         if (!NetworkMonitorAutoDetect.class.desiredAssertionStatus()) {
            var10000 = true;
         } else {
            var10000 = false;
         }

      }
   }

   @SuppressLint({"NewApi"})
   private class SimpleNetworkCallback extends ConnectivityManager.NetworkCallback {
      private SimpleNetworkCallback() {
      }

      public void onAvailable(Network network) {
         org.webrtc2.Logging.d("NetworkMonitorAutoDetect", "Network becomes available: " + network.toString());
         this.onNetworkChanged(network);
      }

      public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
         org.webrtc2.Logging.d("NetworkMonitorAutoDetect", "capabilities changed: " + networkCapabilities.toString());
         this.onNetworkChanged(network);
      }

      public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
         org.webrtc2.Logging.d("NetworkMonitorAutoDetect", "link properties changed: " + linkProperties.toString());
         this.onNetworkChanged(network);
      }

      public void onLosing(Network network, int maxMsToLive) {
         org.webrtc2.Logging.d("NetworkMonitorAutoDetect", "Network " + network.toString() + " is about to lose in " + maxMsToLive + "ms");
      }

      public void onLost(Network network) {
         Logging.d("NetworkMonitorAutoDetect", "Network " + network.toString() + " is disconnected");
         NetworkMonitorAutoDetect.this.observer.onNetworkDisconnect(NetworkMonitorAutoDetect.networkToNetId(network));
      }

      private void onNetworkChanged(Network network) {
         NetworkInformation networkInformation = NetworkMonitorAutoDetect.this.connectivityManagerDelegate.networkToInfo(network);
         if (networkInformation != null) {
            NetworkMonitorAutoDetect.this.observer.onNetworkConnect(networkInformation);
         }

      }

      // $FF: synthetic method
      SimpleNetworkCallback(Object x1) {
         this();
      }
   }

   static class NetworkState {
      private final boolean connected;
      private final int type;
      private final int subtype;

      public NetworkState(boolean connected, int type, int subtype) {
         this.connected = connected;
         this.type = type;
         this.subtype = subtype;
      }

      public boolean isConnected() {
         return this.connected;
      }

      public int getNetworkType() {
         return this.type;
      }

      public int getNetworkSubType() {
         return this.subtype;
      }
   }

   public static class NetworkInformation {
      public final String name;
      public final ConnectionType type;
      public final long handle;
      public final IPAddress[] ipAddresses;

      public NetworkInformation(String name, ConnectionType type, long handle, IPAddress[] addresses) {
         this.name = name;
         this.type = type;
         this.handle = handle;
         this.ipAddresses = addresses;
      }
   }

   public static class IPAddress {
      public final byte[] address;

      public IPAddress(byte[] address) {
         this.address = address;
      }
   }

   public static enum ConnectionType {
      CONNECTION_UNKNOWN,
      CONNECTION_ETHERNET,
      CONNECTION_WIFI,
      CONNECTION_4G,
      CONNECTION_3G,
      CONNECTION_2G,
      CONNECTION_UNKNOWN_CELLULAR,
      CONNECTION_BLUETOOTH,
      CONNECTION_NONE;
   }
}
