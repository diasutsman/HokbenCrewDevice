package org.webrtc2;

import java.util.LinkedList;

public class RtpParameters {
   public final LinkedList<Encoding> encodings = new LinkedList();
   public final LinkedList<Codec> codecs = new LinkedList();

   public static class Codec {
      int payloadType;
      String mimeType;
      int clockRate;
      int channels = 1;
   }

   public static class Encoding {
      public boolean active = true;
      public Integer maxBitrateBps;
   }
}
