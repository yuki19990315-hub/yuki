package dev.yuki.glyphtoy;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.widget.RemoteViews;

public final class MatrixWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateWidgets(context, appWidgetManager, appWidgetIds);
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, MatrixWidgetProvider.class);
        updateWidgets(context, manager, manager.getAppWidgetIds(provider));
    }

    private static void updateWidgets(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        int[] frame = MatrixStorage.loadCustomFrame(context);
        if (frame == null) {
            frame = PixelMatrix.heart();
        }
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_matrix);
            views.setImageViewBitmap(R.id.widget_matrix_image, MatrixBitmapRenderer.render(frame, 256));
            views.setTextViewText(R.id.widget_matrix_title, context.getString(R.string.widget_name_matrix));
            manager.updateAppWidget(appWidgetId, views);
        }
    }
}
