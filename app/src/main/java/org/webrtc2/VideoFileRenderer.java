package org.webrtc2;

import android.os.Handler;
import android.os.HandlerThread;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

public class VideoFileRenderer implements org.webrtc2.VideoRenderer.Callbacks {
   private static final String TAG = "VideoFileRenderer";
   private final HandlerThread renderThread;
   private final Object handlerLock = new Object();
   private final Handler renderThreadHandler;
   private final FileOutputStream videoOutFile;
   private final int outputFileWidth;
   private final int outputFileHeight;
   private final int outputFrameSize;
   private final ByteBuffer outputFrameBuffer;
   private org.webrtc2.EglBase eglBase;
   private org.webrtc2.YuvConverter yuvConverter;

   public VideoFileRenderer(String outputFile, int outputFileWidth, int outputFileHeight, final org.webrtc2.EglBase.Context sharedContext) throws IOException {
      if (outputFileWidth % 2 != 1 && outputFileHeight % 2 != 1) {
         this.outputFileWidth = outputFileWidth;
         this.outputFileHeight = outputFileHeight;
         this.outputFrameSize = outputFileWidth * outputFileHeight * 3 / 2;
         this.outputFrameBuffer = ByteBuffer.allocateDirect(this.outputFrameSize);
         this.videoOutFile = new FileOutputStream(outputFile);
         this.videoOutFile.write(("YUV4MPEG2 C420 W" + outputFileWidth + " H" + outputFileHeight + " Ip F30:1 A1:1\n").getBytes());
         this.renderThread = new HandlerThread("VideoFileRenderer");
         this.renderThread.start();
         this.renderThreadHandler = new Handler(this.renderThread.getLooper());
         ThreadUtils.invokeAtFrontUninterruptibly(this.renderThreadHandler, new Runnable() {
            public void run() {
               VideoFileRenderer.this.eglBase = org.webrtc2.EglBase.create(sharedContext, EglBase.CONFIG_PIXEL_BUFFER);
               VideoFileRenderer.this.eglBase.createDummyPbufferSurface();
               VideoFileRenderer.this.eglBase.makeCurrent();
               VideoFileRenderer.this.yuvConverter = new YuvConverter();
            }
         });
      } else {
         throw new IllegalArgumentException("Does not support uneven width or height");
      }
   }

   public void renderFrame(final org.webrtc2.VideoRenderer.I420Frame frame) {
      this.renderThreadHandler.post(new Runnable() {
         public void run() {
            VideoFileRenderer.this.renderFrameOnRenderThread(frame);
         }
      });
   }

   private void renderFrameOnRenderThread(org.webrtc2.VideoRenderer.I420Frame frame) {
      float frameAspectRatio = (float)frame.rotatedWidth() / (float)frame.rotatedHeight();
      float[] rotatedSamplingMatrix = org.webrtc2.RendererCommon.rotateTextureMatrix(frame.samplingMatrix, (float)frame.rotationDegree);
      float[] layoutMatrix = org.webrtc2.RendererCommon.getLayoutMatrix(false, frameAspectRatio, (float)this.outputFileWidth / (float)this.outputFileHeight);
      float[] texMatrix = RendererCommon.multiplyMatrices(rotatedSamplingMatrix, layoutMatrix);

      try {
         this.videoOutFile.write("FRAME\n".getBytes());
         if (!frame.yuvFrame) {
            this.yuvConverter.convert(this.outputFrameBuffer, this.outputFileWidth, this.outputFileHeight, this.outputFileWidth, frame.textureId, texMatrix);
            int stride = this.outputFileWidth;
            byte[] data = this.outputFrameBuffer.array();
            int offset = this.outputFrameBuffer.arrayOffset();
            this.videoOutFile.write(data, offset, this.outputFileWidth * this.outputFileHeight);

            int r;
            for(r = this.outputFileHeight; r < this.outputFileHeight * 3 / 2; ++r) {
               this.videoOutFile.write(data, offset + r * stride, stride / 2);
            }

            for(r = this.outputFileHeight; r < this.outputFileHeight * 3 / 2; ++r) {
               this.videoOutFile.write(data, offset + r * stride + stride / 2, stride / 2);
            }
         } else {
            nativeI420Scale(frame.yuvPlanes[0], frame.yuvStrides[0], frame.yuvPlanes[1], frame.yuvStrides[1], frame.yuvPlanes[2], frame.yuvStrides[2], frame.width, frame.height, this.outputFrameBuffer, this.outputFileWidth, this.outputFileHeight);
            this.videoOutFile.write(this.outputFrameBuffer.array(), this.outputFrameBuffer.arrayOffset(), this.outputFrameSize);
         }
      } catch (IOException var13) {
         IOException e = var13;
         Logging.e("VideoFileRenderer", "Failed to write to file for video out");
         throw new RuntimeException(e);
      } finally {
         VideoRenderer.renderFrameDone(frame);
      }

   }

   public void release() {
      final CountDownLatch cleanupBarrier = new CountDownLatch(1);
      this.renderThreadHandler.post(new Runnable() {
         public void run() {
            try {
               VideoFileRenderer.this.videoOutFile.close();
            } catch (IOException var2) {
               Logging.d("VideoFileRenderer", "Error closing output video file");
            }

            VideoFileRenderer.this.yuvConverter.release();
            VideoFileRenderer.this.eglBase.release();
            VideoFileRenderer.this.renderThread.quit();
            cleanupBarrier.countDown();
         }
      });
      ThreadUtils.awaitUninterruptibly(cleanupBarrier);
   }

   public static native void nativeI420Scale(ByteBuffer var0, int var1, ByteBuffer var2, int var3, ByteBuffer var4, int var5, int var6, int var7, ByteBuffer var8, int var9, int var10);
}
