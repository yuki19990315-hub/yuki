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

    static void restartDisplaySession(Context context) {
        MatrixStorage.clearDisplaySession(context);
        ensureDisplaySession(context, SystemClock.elapsedRealtime());
    }

    static boolean isDisplayDurationExpired(Context context) {
        int minutes = MatrixStorage.loadDisplayDurationMinutes(context);
        if (minutes <= 0) {
            MatrixStorage.clearDisplaySession(context);
            return false;
        }

        long now = SystemClock.elapsedRealtime();
        long deadlineAt = ensureDisplaySession(context, now);
        return now >= deadlineAt;
    }

    static long millisUntilDisplayDeadline(Context context) {
        int minutes = MatrixStorage.loadDisplayDurationMinutes(context);
        if (minutes <= 0) {
            return Long.MAX_VALUE;
        }
        long now = SystemClock.elapsedRealtime();
        long deadlineAt = ensureDisplaySession(context, now);
        return Math.max(0L, deadlineAt - now);
    }

    static String describe(Context context) {
        int duration = MatrixStorage.loadDisplayDurationMinutes(context);
        String durationText = duration <= 0 ? "スリープ時間なし（常時表示）" : "スリープ開始から" + duration + "分で消灯";
        int start = MatrixStorage.loadQuietStartHour(context);
        int end = MatrixStorage.loadQuietEndHour(context);
        String quietText = start == end ? "夜間消灯なし" : String.format("%02d:00〜%02d:00は消灯", start, end);
        String remainingText = remainingDisplayText(context, duration);
        return durationText + remainingText + " / " + quietText;
    }

    private static long ensureDisplaySession(Context context, long now) {
        long startedAt = MatrixStorage.loadDisplaySessionStartedAt(context);
        long deadlineAt = MatrixStorage.loadDisplaySessionDeadlineAt(context);
        if (startedAt <= 0L || deadlineAt <= startedAt) {
            startedAt = now;
            deadlineAt = now + MatrixStorage.loadDisplayDurationMinutes(context) * 60_000L;
            MatrixStorage.saveDisplaySession(context, startedAt, deadlineAt);
        }
        return deadlineAt;
    }

    private static String remainingDisplayText(Context context, int duration) {
        if (duration <= 0) {
            return "";
        }
        long startedAt = MatrixStorage.loadDisplaySessionStartedAt(context);
        long deadlineAt = MatrixStorage.loadDisplaySessionDeadlineAt(context);
        if (startedAt <= 0L || deadlineAt <= startedAt) {
            return "";
        }
        long remainingMillis = deadlineAt - SystemClock.elapsedRealtime();
        if (remainingMillis <= 0L) {
            return "（残り0分）";
        }
        long remainingMinutes = Math.max(1L, (remainingMillis + 59_999L) / 60_000L);
        return "（残り約" + remainingMinutes + "分）";
    }
}
