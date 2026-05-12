package dev.yuki.glyphtoy;

import android.content.Context;
import android.os.SystemClock;

import java.time.LocalTime;

final class GlyphDisplayPolicy {
    private GlyphDisplayPolicy() {}

    static boolean shouldTurnOff(Context context, LocalTime now) {
        return isQuietHour(context, now) || isDisplayDurationExpired(context);
    }

    static boolean isQuietHour(Context context, LocalTime now) {
        int start = MatrixStorage.loadQuietStartHour(context);
        int end = MatrixStorage.loadQuietEndHour(context);
        if (start == end) {
            return false;
        }
        int hour = now.getHour();
        if (start < end) {
            return hour >= start && hour < end;
        }
        return hour >= start || hour < end;
    }

    static boolean isDisplayDurationExpired(Context context) {
        int minutes = MatrixStorage.loadDisplayDurationMinutes(context);
        if (minutes <= 0) {
            return false;
        }
        long startedAt = MatrixStorage.loadDisplaySessionStartedAt(context);
        if (startedAt == 0L) {
            MatrixStorage.saveDisplaySessionStartedAt(context, SystemClock.elapsedRealtime());
            return false;
        }
        long elapsed = SystemClock.elapsedRealtime() - startedAt;
        return elapsed >= minutes * 60_000L;
    }

    static String describe(Context context) {
        int duration = MatrixStorage.loadDisplayDurationMinutes(context);
        String durationText = duration <= 0 ? "常時表示" : "スリープ後約" + duration + "分";
        int start = MatrixStorage.loadQuietStartHour(context);
        int end = MatrixStorage.loadQuietEndHour(context);
        String quietText = start == end ? "夜間消灯なし" : String.format("%02d:00〜%02d:00は消灯", start, end);
        return durationText + " / " + quietText;
    }
}
