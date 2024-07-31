package org.webrtc2;

public class SessionDescription {
   public final Type type;
   public final String description;

   public SessionDescription(Type type, String description) {
      this.type = type;
      this.description = description;
   }

   public static enum Type {
      OFFER,
      PRANSWER,
      ANSWER;

      public String canonicalForm() {
         return this.name().toLowerCase();
      }

      public static Type fromCanonicalForm(String canonical) {
         return (Type)valueOf(Type.class, canonical.toUpperCase());
      }
   }
}
