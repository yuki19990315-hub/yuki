package dev.yuki.glyphtoy;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

final class MatrixBitmapRenderer {
    private static final int SIZE = PixelMatrix.PHONE_4A_PRO_SIZE;

    private MatrixBitmapRenderer() {}

    static Bitmap render(int[] frame, int bitmapSize) {
        return render(frame, null, bitmapSize);
    }

    static Bitmap render(int[] frame, int[] previousFrame, int bitmapSize) {
        Bitmap bitmap = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cell = bitmapSize / (float) SIZE;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(12, 12, 12));
        canvas.drawRoundRect(0, 0, bitmapSize, bitmapSize, cell, cell, paint);

        float radius = cell * 0.34f;
        paint.setColor(Color.rgb(35, 35, 35));
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                canvas.drawCircle(x * cell + cell / 2f, y * cell + cell / 2f, radius, paint);
            }
        }

        if (previousFrame != null) {
            paint.setColor(Color.argb(92, 185, 185, 185));
            drawLitDots(canvas, paint, previousFrame, cell, radius * 0.92f);
        }

        paint.setColor(Color.WHITE);
        drawLitDots(canvas, paint, frame, cell, radius);
        return bitmap;
    }

    private static void drawLitDots(Canvas canvas, Paint paint, int[] frame, float cell, float radius) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (isLit(frame, y * SIZE + x)) {
                    canvas.drawCircle(x * cell + cell / 2f, y * cell + cell / 2f, radius, paint);
                }
            }
        }
    }

    private static boolean isLit(int[] frame, int index) {
        return frame != null && index < frame.length && frame[index] != 0;
    }
}
