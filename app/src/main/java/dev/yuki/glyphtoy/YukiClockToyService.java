package dev.yuki.glyphtoy;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;
import android.util.Log;

import java.time.LocalTime;

public final class YukiClockToyService extends Service {
    private static final String TAG = "YukiClockToy";
    private static final int MSG_GLYPH_TOY = 1000;
    private static final String MSG_GLYPH_TOY_DATA = "data";
    private static final String EVENT_AOD = "aod";
    private static final String EVENT_CHANGE = "change";

    private GlyphMatrixBridge bridge;
    private boolean heartMode;
    private final Runnable turnOffCheck = this::render;

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
        serviceHandler.removeCallbacks(turnOffCheck);
        if (bridge != null) {
            if (shouldTurnOffOnUnbind()) {
                bridge.turnOff();
            }
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
        String event = bundle.getString(MSG_GLYPH_TOY_DATA, bundle.getString("event", ""));
        if (EVENT_AOD.equals(event)) {
            GlyphDisplayPolicy.restartDisplaySession(this);
            render();
        } else if (EVENT_CHANGE.equals(event)) {
            heartMode = !heartMode;
            render();
        }
    }

    private void render() {
        if (bridge == null) {
            return;
        }
        if (GlyphDisplayPolicy.shouldTurnOff(this, LocalTime.now())) {
            bridge.turnOff();
            return;
        }

        int[] customFrame = MatrixStorage.loadCustomFrame(this);
        if (heartMode) {
            bridge.setToyFrame(PixelMatrix.heart());
        } else if (customFrame != null) {
            bridge.setToyFrame(customFrame);
        } else {
            bridge.setToyFrame(PixelMatrix.clock(LocalTime.now()));
        }
        scheduleTurnOffCheck();
    }

    private void scheduleTurnOffCheck() {
        serviceHandler.removeCallbacks(turnOffCheck);
        long delay = GlyphDisplayPolicy.millisUntilDisplayDeadline(this);
        if (delay == Long.MAX_VALUE) {
            return;
        }
        if (delay <= 0L) {
            render();
            return;
        }
        serviceHandler.postDelayed(turnOffCheck, Math.max(1_000L, delay));
    }

    private boolean shouldTurnOffOnUnbind() {
        if (GlyphDisplayPolicy.isQuietHour(this, LocalTime.now())) {
            return true;
        }
        int minutes = MatrixStorage.loadDisplayDurationMinutes(this);
        long deadlineAt = MatrixStorage.loadDisplaySessionDeadlineAt(this);
        return minutes > 0 && deadlineAt > 0L && SystemClock.elapsedRealtime() >= deadlineAt;
    }
}
