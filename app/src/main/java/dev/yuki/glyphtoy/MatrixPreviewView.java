package dev.yuki.glyphtoy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Bitmap;
import android.view.View;

final class MatrixPreviewView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] frame = PixelMatrix.heart();

    MatrixPreviewView(Context context) {
        super(context);
        setMinimumHeight((int) (180 * getResources().getDisplayMetrics().density));
    }

    void setFrame(int[] frame) {
        this.frame = frame.clone();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float available = Math.min(getWidth(), getHeight());
        float left = (getWidth() - available) / 2f;
        float top = (getHeight() - available) / 2f;

        Bitmap bitmap = MatrixBitmapRenderer.render(frame, Math.max(1, (int) available));
        canvas.drawBitmap(bitmap, left, top, paint);
    }
}
