package dev.yuki.glyphtoy;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import java.time.LocalTime;

public final class YukiClockToyService extends Service {
    private static final String TAG = "YukiClockToy";
    private static final int MSG_GLYPH_TOY = 1000;
    private static final String MSG_GLYPH_TOY_DATA = "data";
    private static final String EVENT_AOD = "aod";
    private static final String EVENT_CHANGE = "change";
    private static final long CLOCK_REFRESH_MS = 1_000L;

    private GlyphMatrixBridge bridge;
    private final Runnable renderTick = this::render;

    private final Handler serviceHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (isGlyphToyMessage(msg)) {
                handleToyEvent(msg.getData());
                return;
            }
            super.handleMessage(msg);
        }
    };
    private final Messenger serviceMessenger = new Messenger(serviceHandler);

    @Override
    public IBinder onBind(Intent intent) {
        initGlyph();
        return serviceMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        serviceHandler.removeCallbacks(renderTick);
        if (bridge != null) {
            bridge.unInit();
            bridge = null;
        }
        return false;
    }

    private void initGlyph() {
        bridge = new GlyphMatrixBridge(this);
        bridge.init(new GlyphMatrixBridge.Listener() {
            @Override
            public void onConnected() {
                bridge.registerPhone4aPro();
                render();
            }

            @Override
            public void onDisconnected() {
                Log.i(TAG, "Glyph Matrix service disconnected");
            }

            @Override
            public void onError(String message, Throwable throwable) {
                Log.e(TAG, message, throwable);
            }
        });
    }

    private boolean isGlyphToyMessage(Message msg) {
        return msg.what == MSG_GLYPH_TOY || hasGlyphEventData(msg.getData());
    }

    private boolean hasGlyphEventData(Bundle bundle) {
        return bundle != null && (bundle.containsKey(MSG_GLYPH_TOY_DATA) || bundle.containsKey("event"));
    }

    private void handleToyEvent(Bundle bundle) {
        String event = bundle == null ? "" : bundle.getString(MSG_GLYPH_TOY_DATA, bundle.getString("event", ""));
        if (EVENT_AOD.equals(event)) {
            GlyphDisplayPolicy.restartDisplaySession(this);
            render();
        } else if (EVENT_CHANGE.equals(event)) {
            cycleDisplayMode();
        } else {
            render();
        }
    }

    private void cycleDisplayMode() {
        GlyphDisplayMode nextMode = MatrixStorage.loadDisplayMode(this).next();
        MatrixStorage.saveDisplayMode(this, nextMode);
        render();
    }

    private void render() {
        if (bridge == null) {
            return;
        }
        GlyphDisplayPolicy.shouldTurnOff(this, LocalTime.now());

        GlyphDisplayMode mode = MatrixStorage.loadDisplayMode(this);
        int[] customFrame = MatrixStorage.loadCustomFrame(this);
        bridge.setToyFrame(frameForMode(mode, customFrame, LocalTime.now()));
        scheduleNextRender(mode, customFrame == null);
    }

    private int[] frameForMode(GlyphDisplayMode mode, int[] customFrame, LocalTime now) {
        if (mode == GlyphDisplayMode.HEART) {
            return PixelMatrix.heart();
        }
        if (mode == GlyphDisplayMode.CLOCK) {
            return PixelMatrix.clock(now);
        }
        return customFrame == null ? PixelMatrix.clock(now) : customFrame;
    }

    private void scheduleNextRender(GlyphDisplayMode mode, boolean customFallsBackToClock) {
        serviceHandler.removeCallbacks(renderTick);
        if (mode == GlyphDisplayMode.CLOCK || customFallsBackToClock) {
            serviceHandler.postDelayed(renderTick, CLOCK_REFRESH_MS);
        }
    }
}
