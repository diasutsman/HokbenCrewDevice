package org.webrtc2;

import android.opengl.GLES20;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

class YuvConverter {
   private static final FloatBuffer DEVICE_RECTANGLE = org.webrtc2.GlUtil.createFloatBuffer(new float[]{-1.0F, -1.0F, 1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F});
   private static final FloatBuffer TEXTURE_RECTANGLE = org.webrtc2.GlUtil.createFloatBuffer(new float[]{0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F});
   private static final String VERTEX_SHADER = "varying vec2 interp_tc;\nattribute vec4 in_pos;\nattribute vec4 in_tc;\n\nuniform mat4 texMatrix;\n\nvoid main() {\n    gl_Position = in_pos;\n    interp_tc = (texMatrix * in_tc).xy;\n}\n";
   private static final String FRAGMENT_SHADER = "#extension GL_OES_EGL_image_external : require\nprecision mediump float;\nvarying vec2 interp_tc;\n\nuniform samplerExternalOES oesTex;\nuniform vec2 xUnit;\nuniform vec4 coeffs;\n\nvoid main() {\n  gl_FragColor.r = coeffs.a + dot(coeffs.rgb,\n      texture2D(oesTex, interp_tc - 1.5 * xUnit).rgb);\n  gl_FragColor.g = coeffs.a + dot(coeffs.rgb,\n      texture2D(oesTex, interp_tc - 0.5 * xUnit).rgb);\n  gl_FragColor.b = coeffs.a + dot(coeffs.rgb,\n      texture2D(oesTex, interp_tc + 0.5 * xUnit).rgb);\n  gl_FragColor.a = coeffs.a + dot(coeffs.rgb,\n      texture2D(oesTex, interp_tc + 1.5 * xUnit).rgb);\n}\n";
   private final int frameBufferId;
   private final int frameTextureId;
   private final org.webrtc2.GlShader shader;
   private final int texMatrixLoc;
   private final int xUnitLoc;
   private final int coeffsLoc;
   private final ThreadUtils.ThreadChecker threadChecker = new ThreadUtils.ThreadChecker();
   private int frameBufferWidth;
   private int frameBufferHeight;
   private boolean released = false;

   public YuvConverter() {
      this.threadChecker.checkIsOnValidThread();
      this.frameTextureId = org.webrtc2.GlUtil.generateTexture(3553);
      this.frameBufferWidth = 0;
      this.frameBufferHeight = 0;
      int[] frameBuffers = new int[1];
      GLES20.glGenFramebuffers(1, frameBuffers, 0);
      this.frameBufferId = frameBuffers[0];
      GLES20.glBindFramebuffer(36160, this.frameBufferId);
      org.webrtc2.GlUtil.checkNoGLES2Error("Generate framebuffer");
      GLES20.glFramebufferTexture2D(36160, 36064, 3553, this.frameTextureId, 0);
      org.webrtc2.GlUtil.checkNoGLES2Error("Attach texture to framebuffer");
      GLES20.glBindFramebuffer(36160, 0);
      this.shader = new GlShader("varying vec2 interp_tc;\nattribute vec4 in_pos;\nattribute vec4 in_tc;\n\nuniform mat4 texMatrix;\n\nvoid main() {\n    gl_Position = in_pos;\n    interp_tc = (texMatrix * in_tc).xy;\n}\n", "#extension GL_OES_EGL_image_external : require\nprecision mediump float;\nvarying vec2 interp_tc;\n\nuniform samplerExternalOES oesTex;\nuniform vec2 xUnit;\nuniform vec4 coeffs;\n\nvoid main() {\n  gl_FragColor.r = coeffs.a + dot(coeffs.rgb,\n      texture2D(oesTex, interp_tc - 1.5 * xUnit).rgb);\n  gl_FragColor.g = coeffs.a + dot(coeffs.rgb,\n      texture2D(oesTex, interp_tc - 0.5 * xUnit).rgb);\n  gl_FragColor.b = coeffs.a + dot(coeffs.rgb,\n      texture2D(oesTex, interp_tc + 0.5 * xUnit).rgb);\n  gl_FragColor.a = coeffs.a + dot(coeffs.rgb,\n      texture2D(oesTex, interp_tc + 1.5 * xUnit).rgb);\n}\n");
      this.shader.useProgram();
      this.texMatrixLoc = this.shader.getUniformLocation("texMatrix");
      this.xUnitLoc = this.shader.getUniformLocation("xUnit");
      this.coeffsLoc = this.shader.getUniformLocation("coeffs");
      GLES20.glUniform1i(this.shader.getUniformLocation("oesTex"), 0);
      org.webrtc2.GlUtil.checkNoGLES2Error("Initialize fragment shader uniform values.");
      this.shader.setVertexAttribArray("in_pos", 2, DEVICE_RECTANGLE);
      this.shader.setVertexAttribArray("in_tc", 2, TEXTURE_RECTANGLE);
   }

   public void convert(ByteBuffer buf, int width, int height, int stride, int srcTextureId, float[] transformMatrix) {
      this.threadChecker.checkIsOnValidThread();
      if (this.released) {
         throw new IllegalStateException("YuvConverter.convert called on released object");
      } else if (stride % 8 != 0) {
         throw new IllegalArgumentException("Invalid stride, must be a multiple of 8");
      } else if (stride < width) {
         throw new IllegalArgumentException("Invalid stride, must >= width");
      } else {
         int y_width = (width + 3) / 4;
         int uv_width = (width + 7) / 8;
         int uv_height = (height + 1) / 2;
         int total_height = height + uv_height;
         int size = stride * total_height;
         if (buf.capacity() < size) {
            throw new IllegalArgumentException("YuvConverter.convert called with too small buffer");
         } else {
            transformMatrix = RendererCommon.multiplyMatrices(transformMatrix, RendererCommon.verticalFlipMatrix());
            GLES20.glBindFramebuffer(36160, this.frameBufferId);
            org.webrtc2.GlUtil.checkNoGLES2Error("glBindFramebuffer");
            if (this.frameBufferWidth != stride / 4 || this.frameBufferHeight != total_height) {
               this.frameBufferWidth = stride / 4;
               this.frameBufferHeight = total_height;
               GLES20.glActiveTexture(33984);
               GLES20.glBindTexture(3553, this.frameTextureId);
               GLES20.glTexImage2D(3553, 0, 6408, this.frameBufferWidth, this.frameBufferHeight, 0, 6408, 5121, (Buffer)null);
               int status = GLES20.glCheckFramebufferStatus(36160);
               if (status != 36053) {
                  throw new IllegalStateException("Framebuffer not complete, status: " + status);
               }
            }

            GLES20.glActiveTexture(33984);
            GLES20.glBindTexture(36197, srcTextureId);
            GLES20.glUniformMatrix4fv(this.texMatrixLoc, 1, false, transformMatrix, 0);
            GLES20.glViewport(0, 0, y_width, height);
            GLES20.glUniform2f(this.xUnitLoc, transformMatrix[0] / (float)width, transformMatrix[1] / (float)width);
            GLES20.glUniform4f(this.coeffsLoc, 0.299F, 0.587F, 0.114F, 0.0F);
            GLES20.glDrawArrays(5, 0, 4);
            GLES20.glViewport(0, height, uv_width, uv_height);
            GLES20.glUniform2f(this.xUnitLoc, 2.0F * transformMatrix[0] / (float)width, 2.0F * transformMatrix[1] / (float)width);
            GLES20.glUniform4f(this.coeffsLoc, -0.169F, -0.331F, 0.499F, 0.5F);
            GLES20.glDrawArrays(5, 0, 4);
            GLES20.glViewport(stride / 8, height, uv_width, uv_height);
            GLES20.glUniform4f(this.coeffsLoc, 0.499F, -0.418F, -0.0813F, 0.5F);
            GLES20.glDrawArrays(5, 0, 4);
            GLES20.glReadPixels(0, 0, this.frameBufferWidth, this.frameBufferHeight, 6408, 5121, buf);
            GlUtil.checkNoGLES2Error("YuvConverter.convert");
            GLES20.glBindFramebuffer(36160, 0);
            GLES20.glBindTexture(3553, 0);
            GLES20.glBindTexture(36197, 0);
         }
      }
   }

   public void release() {
      this.threadChecker.checkIsOnValidThread();
      this.released = true;
      this.shader.release();
      GLES20.glDeleteTextures(1, new int[]{this.frameTextureId}, 0);
      GLES20.glDeleteFramebuffers(1, new int[]{this.frameBufferId}, 0);
      this.frameBufferWidth = 0;
      this.frameBufferHeight = 0;
   }
}
