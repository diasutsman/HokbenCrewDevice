package org.webrtc2;

import android.graphics.SurfaceTexture;
import android.view.Surface;

public abstract class EglBase {
   public static final Object lock = new Object();
   private static final int EGL_OPENGL_ES2_BIT = 4;
   private static final int EGL_RECORDABLE_ANDROID = 12610;
   public static final int[] CONFIG_PLAIN = new int[]{12324, 8, 12323, 8, 12322, 8, 12352, 4, 12344};
   public static final int[] CONFIG_RGBA = new int[]{12324, 8, 12323, 8, 12322, 8, 12321, 8, 12352, 4, 12344};
   public static final int[] CONFIG_PIXEL_BUFFER = new int[]{12324, 8, 12323, 8, 12322, 8, 12352, 4, 12339, 1, 12344};
   public static final int[] CONFIG_PIXEL_RGBA_BUFFER = new int[]{12324, 8, 12323, 8, 12322, 8, 12321, 8, 12352, 4, 12339, 1, 12344};
   public static final int[] CONFIG_RECORDABLE = new int[]{12324, 8, 12323, 8, 12322, 8, 12352, 4, 12610, 1, 12344};

   public static EglBase create(Context sharedContext, int[] configAttributes) {
      return (EglBase)(!EglBase14.isEGL14Supported() || sharedContext != null && !(sharedContext instanceof EglBase14.Context) ? new org.webrtc2.EglBase10((EglBase10.Context)sharedContext, configAttributes) : new EglBase14((EglBase14.Context)sharedContext, configAttributes));
   }

   public static EglBase create() {
      return create((Context)null, CONFIG_PLAIN);
   }

   public static EglBase create(Context sharedContext) {
      return create(sharedContext, CONFIG_PLAIN);
   }

   public abstract void createSurface(Surface var1);

   public abstract void createSurface(SurfaceTexture var1);

   public abstract void createDummyPbufferSurface();

   public abstract void createPbufferSurface(int var1, int var2);

   public abstract Context getEglBaseContext();

   public abstract boolean hasSurface();

   public abstract int surfaceWidth();

   public abstract int surfaceHeight();

   public abstract void releaseSurface();

   public abstract void release();

   public abstract void makeCurrent();

   public abstract void detachCurrent();

   public abstract void swapBuffers();

   public static class Context {
   }
}
