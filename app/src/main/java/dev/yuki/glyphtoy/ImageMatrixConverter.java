package dev.yuki.glyphtoy;

import android.graphics.Bitmap;

final class ImageMatrixConverter {
    private static final int SIZE = PixelMatrix.PHONE_4A_PRO_SIZE;
    private static final int WHITE = 0x00FFFFFF;
    private static final int BLACK = 0x00000000;

    private ImageMatrixConverter() {}

    static int[] fromBitmap(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        return fromArgbPixels(pixels, width, height);
    }

    static int[] fromArgbPixels(int[] source, int width, int height) {
        int[] luminance = new int[SIZE * SIZE];
        int min = 255;
        int max = 0;

        int cropSize = Math.min(width, height);
        int xOffset = (width - cropSize) / 2;
        int yOffset = (height - cropSize) / 2;

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int startX = xOffset + x * cropSize / SIZE;
                int endX = xOffset + Math.max(x * cropSize / SIZE + 1, (x + 1) * cropSize / SIZE);
                int startY = yOffset + y * cropSize / SIZE;
                int endY = yOffset + Math.max(y * cropSize / SIZE + 1, (y + 1) * cropSize / SIZE);
                int value = averageLuminance(source, width, startX, endX, startY, endY);
                luminance[y * SIZE + x] = value;
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }

        int threshold = max - min < 8 ? 128 : min + (max - min) / 2;
        int[] matrix = new int[SIZE * SIZE];
        for (int i = 0; i < luminance.length; i++) {
            matrix[i] = luminance[i] >= threshold ? WHITE : BLACK;
        }
        return matrix;
    }

    private static int averageLuminance(int[] source, int width, int startX, int endX, int startY, int endY) {
        long total = 0;
        int count = 0;
        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                int argb = source[y * width + x];
                int alpha = (argb >>> 24) & 0xFF;
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                total += alpha == 0 ? 255 : (red * 299L + green * 587L + blue * 114L) / 1000L;
                count++;
            }
        }
        return count == 0 ? 0 : (int) (total / count);
    }
}
