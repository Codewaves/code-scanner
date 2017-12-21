/*
 * MIT License
 *
 * Copyright (c) 2017 Yuriy Budiyev [yuriy.budiyev@yandex.ru]
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.budiyev.android.codescanner;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Build;
import android.support.annotation.NonNull;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

import com.google.zxing.client.android.camera.CameraConfigurationUtils;

final class Utils {
    private static final float SQUARE_RATIO = 0.75f;
    private static final float PORTRAIT_WIDTH_RATIO = 0.75f;
    private static final float PORTRAIT_HEIGHT_RATIO = 0.75f;
    private static final float LANDSCAPE_WIDTH_RATIO = 2.2f;
    private static final float LANDSCAPE_HEIGHT_RATIO = 0.4f;
    private static final float MAX_DISTORTION = 0.5f;
    private static final int MIN_PREVIEW_PIXELS = 442368;

    private Utils() {
    }

    public static void optimizeParameters(@NonNull Camera.Parameters parameters) {
        CameraConfigurationUtils.setBestPreviewFPS(parameters);
        CameraConfigurationUtils.setBarcodeSceneMode(parameters);
        CameraConfigurationUtils.setVideoStabilization(parameters);
        parameters.setPreviewFormat(ImageFormat.NV21);
    }

    @NonNull
    public static Point findSuitableImageSize(@NonNull Camera.Parameters parameters, int frameWidth, int frameHeight) {
        List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
        if (sizes != null && !sizes.isEmpty()) {
            Collections.sort(sizes, new CameraSizeComparator());
            float frameRatio = (float) frameWidth / (float) frameHeight;
            for (Camera.Size size : sizes) {
                int width = size.width;
                int height = size.height;
                if (width * height >= MIN_PREVIEW_PIXELS &&
                        Math.abs(frameRatio - (float) width / (float) height) <= MAX_DISTORTION) {
                    return new Point(width, height);
                }
            }
        }
        Camera.Size defaultSize = parameters.getPreviewSize();
        if (defaultSize == null) {
            throw new CodeScannerException("Unable to configure camera preview size");
        }
        return new Point(defaultSize.width, defaultSize.height);
    }

    public static boolean disableAutoFocus(@NonNull Camera.Parameters parameters) {
        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes == null || focusModes.isEmpty()) {
            return false;
        }
        String focusMode = parameters.getFocusMode();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
            if (Camera.Parameters.FOCUS_MODE_FIXED.equals(focusMode)) {
                return false;
            } else {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
                return true;
            }
        }
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            if (Camera.Parameters.FOCUS_MODE_AUTO.equals(focusMode)) {
                return false;
            } else {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                return true;
            }
        }
        return false;
    }

    public static boolean setAutoFocusMode(@NonNull Camera.Parameters parameters, AutoFocusMode autoFocusMode) {
        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes == null || focusModes.isEmpty()) {
            return false;
        }
        if (autoFocusMode == AutoFocusMode.CONTINUOUS) {
            if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE.equals(parameters.getFocusMode())) {
                return false;
            }
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                return true;
            }
        }
        if (Camera.Parameters.FOCUS_MODE_AUTO.equals(parameters.getFocusMode())) {
            return false;
        }
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            return true;
        } else {
            return false;
        }
    }

    public static boolean setFlashMode(@NonNull Camera.Parameters parameters, @NonNull String flashMode) {
        if (flashMode.equals(parameters.getFlashMode())) {
            return false;
        }
        List<String> flashModes = parameters.getSupportedFlashModes();
        if (flashModes != null && flashModes.contains(flashMode)) {
            parameters.setFlashMode(flashMode);
            return true;
        }
        return false;
    }

    public static int getDisplayOrientation(@NonNull Context context, @NonNull Camera.CameraInfo cameraInfo) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            throw new CodeScannerException("Unable to access window manager");
        }
        int degrees;
        int rotation = windowManager.getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            default:
                if (rotation % 90 == 0) {
                    degrees = (360 + rotation) % 360;
                } else {
                    throw new CodeScannerException("Invalid display rotation");
                }
        }
        return ((cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? 180 : 360) + cameraInfo.orientation -
                degrees) % 360;
    }

    public static boolean isPortrait(int orientation) {
        return orientation == 90 || orientation == 270;
    }

    public static boolean isLaidOut(@NonNull View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return view.isLaidOut();
        } else {
            return view.getWidth() > 0 && view.getHeight() > 0;
        }
    }

    @NonNull
    public static Point getPreviewSize(int imageWidth, int imageHeight, int frameWidth, int frameHeight) {
        if (imageWidth == frameWidth && imageHeight == frameHeight) {
            return new Point(frameWidth, frameHeight);
        }
        int imageDivisor = greatestCommonDivisor(imageWidth, imageHeight);
        int imageRatioWidth = imageWidth / imageDivisor;
        int imageRatioHeight = imageHeight / imageDivisor;
        int resultWidth = imageRatioWidth * frameHeight / imageRatioHeight;
        if (resultWidth < frameWidth) {
            return new Point(frameWidth, imageRatioHeight * frameWidth / imageRatioWidth);
        } else {
            return new Point(resultWidth, frameHeight);
        }
    }

    @NonNull
    @SuppressWarnings("SuspiciousNameCombination")
    public static Rect getViewFrameRect(boolean squareFrame, int width, int height) {
        int frameWidth;
        int frameHeight;
        if (squareFrame) {
            frameWidth = Math.round(Math.min(width, height) * SQUARE_RATIO);
            frameHeight = frameWidth;
        } else {
            if (width >= height) {
                frameHeight = Math.round(height * LANDSCAPE_HEIGHT_RATIO);
                frameWidth = Math.round(LANDSCAPE_WIDTH_RATIO * frameHeight);
            } else {
                frameWidth = Math.round(width * PORTRAIT_WIDTH_RATIO);
                frameHeight = Math.round(PORTRAIT_HEIGHT_RATIO * frameWidth);
            }
        }
        int left = (width - frameWidth) / 2;
        int top = (height - frameHeight) / 2;
        return new Rect(left, top, left + frameWidth, top + frameHeight);
    }

    @NonNull
    public static Rect getImageFrameRect(boolean squareFrame, int imageWidth, int imageHeight,
            @NonNull Point previewSize, @NonNull Point viewSize) {
        int previewWidth = previewSize.getX();
        int previewHeight = previewSize.getY();
        int viewWidth = viewSize.getX();
        int viewHeight = viewSize.getY();
        Rect viewFrameRect = getViewFrameRect(squareFrame, viewWidth, viewHeight);
        int wD = (previewWidth - viewWidth) / 2;
        int hD = (previewHeight - viewHeight) / 2;
        float wR = (float) imageWidth / (float) previewWidth;
        float hR = (float) imageHeight / (float) previewHeight;
        return new Rect(Math.round((viewFrameRect.getLeft() + wD) * wR), Math.round((viewFrameRect.getTop() + hD) * hR),
                Math.round((viewFrameRect.getRight() + wD) * wR), Math.round((viewFrameRect.getBottom() + hD) * hR));
    }

    public static byte[] rotateNV21(byte[] yuv, int width, int height, int rotation) {
        if (rotation == 0 || rotation == 360) {
            return yuv;
        }
        if (rotation % 90 != 0 || rotation < 0 || rotation > 270) {
            throw new IllegalArgumentException("0 <= rotation < 360, rotation % 90 == 0");
        }
        byte[] output = new byte[yuv.length];
        int frameSize = width * height;
        boolean swap = rotation % 180 != 0;
        boolean flipX = rotation % 270 != 0;
        boolean flipY = rotation >= 180;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int yIn = j * width + i;
                int uIn = frameSize + (j >> 1) * width + (i & ~1);
                int vIn = uIn + 1;
                int wOut = swap ? height : width;
                int hOut = swap ? width : height;
                int iSwapped = swap ? j : i;
                int jSwapped = swap ? i : j;
                int iOut = flipX ? wOut - iSwapped - 1 : iSwapped;
                int jOut = flipY ? hOut - jSwapped - 1 : jSwapped;
                int yOut = jOut * wOut + iOut;
                int uOut = frameSize + (jOut >> 1) * wOut + (iOut & ~1);
                int vOut = uOut + 1;
                output[yOut] = (byte) (0xff & yuv[yIn]);
                output[uOut] = (byte) (0xff & yuv[uIn]);
                output[vOut] = (byte) (0xff & yuv[vIn]);
            }
        }
        return output;
    }

    private static int greatestCommonDivisor(int a, int b) {
        while (a > 0 && b > 0) {
            if (a > b) {
                a %= b;
            } else {
                b %= a;
            }
        }
        return a + b;
    }

    public static final class SuppressErrorCallback implements ErrorCallback {
        @Override
        public void onError(@NonNull Exception error) {
            // Do nothing
        }
    }

    private static final class CameraSizeComparator implements Comparator<Camera.Size> {
        @Override
        public int compare(@NonNull Camera.Size a, @NonNull Camera.Size b) {
            int aPixels = a.height * a.width;
            int bPixels = b.height * b.width;
            if (bPixels < aPixels) {
                return -1;
            } else if (bPixels > aPixels) {
                return 1;
            } else {
                return 0;
            }
        }
    }
}
