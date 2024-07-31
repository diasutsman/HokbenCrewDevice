package org.webrtc2;

import android.graphics.Point;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.view.View;
import android.view.View.MeasureSpec;

import java.nio.ByteBuffer;

public class RendererCommon {
   private static float BALANCED_VISIBLE_FRACTION = 0.5625F;

   public static final float[] identityMatrix() {
      return new float[]{1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F};
   }

   public static final float[] verticalFlipMatrix() {
      return new float[]{1.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F};
   }

   public static final float[] horizontalFlipMatrix() {
      return new float[]{-1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F};
   }

   public static float[] rotateTextureMatrix(float[] textureMatrix, float rotationDegree) {
      float[] rotationMatrix = new float[16];
      Matrix.setRotateM(rotationMatrix, 0, rotationDegree, 0.0F, 0.0F, 1.0F);
      adjustOrigin(rotationMatrix);
      return multiplyMatrices(textureMatrix, rotationMatrix);
   }

   public static float[] multiplyMatrices(float[] a, float[] b) {
      float[] resultMatrix = new float[16];
      Matrix.multiplyMM(resultMatrix, 0, a, 0, b, 0);
      return resultMatrix;
   }

   public static float[] getLayoutMatrix(boolean mirror, float videoAspectRatio, float displayAspectRatio) {
      float scaleX = 1.0F;
      float scaleY = 1.0F;
      if (displayAspectRatio > videoAspectRatio) {
         scaleY = videoAspectRatio / displayAspectRatio;
      } else {
         scaleX = displayAspectRatio / videoAspectRatio;
      }

      if (mirror) {
         scaleX *= -1.0F;
      }

      float[] matrix = new float[16];
      Matrix.setIdentityM(matrix, 0);
      Matrix.scaleM(matrix, 0, scaleX, scaleY, 1.0F);
      adjustOrigin(matrix);
      return matrix;
   }

   public static Point getDisplaySize(ScalingType scalingType, float videoAspectRatio, int maxDisplayWidth, int maxDisplayHeight) {
      return getDisplaySize(convertScalingTypeToVisibleFraction(scalingType), videoAspectRatio, maxDisplayWidth, maxDisplayHeight);
   }

   private static void adjustOrigin(float[] matrix) {
      matrix[12] -= 0.5F * (matrix[0] + matrix[4]);
      matrix[13] -= 0.5F * (matrix[1] + matrix[5]);
      matrix[12] += 0.5F;
      matrix[13] += 0.5F;
   }

   private static float convertScalingTypeToVisibleFraction(ScalingType scalingType) {
      switch (scalingType) {
         case SCALE_ASPECT_FIT:
            return 1.0F;
         case SCALE_ASPECT_FILL:
            return 0.0F;
         case SCALE_ASPECT_BALANCED:
            return BALANCED_VISIBLE_FRACTION;
         default:
            throw new IllegalArgumentException();
      }
   }

   private static Point getDisplaySize(float minVisibleFraction, float videoAspectRatio, int maxDisplayWidth, int maxDisplayHeight) {
      if (minVisibleFraction != 0.0F && videoAspectRatio != 0.0F) {
         int width = Math.min(maxDisplayWidth, Math.round((float)maxDisplayHeight / minVisibleFraction * videoAspectRatio));
         int height = Math.min(maxDisplayHeight, Math.round((float)maxDisplayWidth / minVisibleFraction / videoAspectRatio));
         return new Point(width, height);
      } else {
         return new Point(maxDisplayWidth, maxDisplayHeight);
      }
   }

   public static enum ScalingType {
      SCALE_ASPECT_FIT,
      SCALE_ASPECT_FILL,
      SCALE_ASPECT_BALANCED;
   }

   public static class VideoLayoutMeasure {
      private ScalingType scalingTypeMatchOrientation;
      private ScalingType scalingTypeMismatchOrientation;

      public VideoLayoutMeasure() {
         this.scalingTypeMatchOrientation = ScalingType.SCALE_ASPECT_BALANCED;
         this.scalingTypeMismatchOrientation = ScalingType.SCALE_ASPECT_BALANCED;
      }

      public void setScalingType(ScalingType scalingType) {
         this.scalingTypeMatchOrientation = scalingType;
         this.scalingTypeMismatchOrientation = scalingType;
      }

      public void setScalingType(ScalingType scalingTypeMatchOrientation, ScalingType scalingTypeMismatchOrientation) {
         this.scalingTypeMatchOrientation = scalingTypeMatchOrientation;
         this.scalingTypeMismatchOrientation = scalingTypeMismatchOrientation;
      }

      public Point measure(int widthSpec, int heightSpec, int frameWidth, int frameHeight) {
         int maxWidth = View.getDefaultSize(Integer.MAX_VALUE, widthSpec);
         int maxHeight = View.getDefaultSize(Integer.MAX_VALUE, heightSpec);
         if (frameWidth != 0 && frameHeight != 0 && maxWidth != 0 && maxHeight != 0) {
            float frameAspect = (float)frameWidth / (float)frameHeight;
            float displayAspect = (float)maxWidth / (float)maxHeight;
            ScalingType scalingType = frameAspect > 1.0F == displayAspect > 1.0F ? this.scalingTypeMatchOrientation : this.scalingTypeMismatchOrientation;
            Point layoutSize = RendererCommon.getDisplaySize(scalingType, frameAspect, maxWidth, maxHeight);
            if (MeasureSpec.getMode(widthSpec) == 1073741824) {
               layoutSize.x = maxWidth;
            }

            if (MeasureSpec.getMode(heightSpec) == 1073741824) {
               layoutSize.y = maxHeight;
            }

            return layoutSize;
         } else {
            return new Point(maxWidth, maxHeight);
         }
      }
   }

   public static class YuvUploader {
      private ByteBuffer copyBuffer;

      public void uploadYuvData(int[] outputYuvTextures, int width, int height, int[] strides, ByteBuffer[] planes) {
         int[] planeWidths = new int[]{width, width / 2, width / 2};
         int[] planeHeights = new int[]{height, height / 2, height / 2};
         int copyCapacityNeeded = 0;

         int i;
         for(i = 0; i < 3; ++i) {
            if (strides[i] > planeWidths[i]) {
               copyCapacityNeeded = Math.max(copyCapacityNeeded, planeWidths[i] * planeHeights[i]);
            }
         }

         if (copyCapacityNeeded > 0 && (this.copyBuffer == null || this.copyBuffer.capacity() < copyCapacityNeeded)) {
            this.copyBuffer = ByteBuffer.allocateDirect(copyCapacityNeeded);
         }

         for(i = 0; i < 3; ++i) {
            GLES20.glActiveTexture('蓀' + i);
            GLES20.glBindTexture(3553, outputYuvTextures[i]);
            ByteBuffer packedByteBuffer;
            if (strides[i] == planeWidths[i]) {
               packedByteBuffer = planes[i];
            } else {
               VideoRenderer.nativeCopyPlane(planes[i], planeWidths[i], planeHeights[i], strides[i], this.copyBuffer, planeWidths[i]);
               packedByteBuffer = this.copyBuffer;
            }

            GLES20.glTexImage2D(3553, 0, 6409, planeWidths[i], planeHeights[i], 0, 6409, 5121, packedByteBuffer);
         }

      }
   }

   public interface GlDrawer {
      void drawOes(int var1, float[] var2, int var3, int var4, int var5, int var6, int var7, int var8);

      void drawRgb(int var1, float[] var2, int var3, int var4, int var5, int var6, int var7, int var8);

      void drawYuv(int[] var1, float[] var2, int var3, int var4, int var5, int var6, int var7, int var8);

      void release();
   }

   public interface RendererEvents {
      void onFirstFrameRendered();

      void onFrameResolutionChanged(int var1, int var2, int var3);
   }
}
