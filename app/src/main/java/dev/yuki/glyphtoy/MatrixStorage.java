package dev.yuki.glyphtoy;

import android.content.Context;
import android.content.SharedPreferences;

final class MatrixStorage {
    private static final String PREFS = "matrix_storage";
    private static final String KEY_CUSTOM_FRAME = "custom_frame";
    private static final String KEY_DISPLAY_DURATION_MINUTES = "display_duration_minutes";
    private static final String KEY_DISPLAY_SESSION_STARTED_AT = "display_session_started_at";
    private static final String KEY_QUIET_START_HOUR = "quiet_start_hour";
    private static final String KEY_QUIET_END_HOUR = "quiet_end_hour";
    private static final int SIZE = PixelMatrix.PHONE_4A_PRO_SIZE * PixelMatrix.PHONE_4A_PRO_SIZE;
    private static final int DEFAULT_DISPLAY_DURATION_MINUTES = 1;
    private static final int DEFAULT_QUIET_START_HOUR = 23;
    private static final int DEFAULT_QUIET_END_HOUR = 7;

    private MatrixStorage() {}

    static void saveCustomFrame(Context context, int[] frame) {
        StringBuilder encoded = new StringBuilder(frame.length * 2);
        for (int value : frame) {
            encoded.append(value == 0 ? '0' : '1');
        }
        prefs(context).edit().putString(KEY_CUSTOM_FRAME, encoded.toString()).apply();
    }

    static int[] loadCustomFrame(Context context) {
        String encoded = prefs(context).getString(KEY_CUSTOM_FRAME, null);
        if (encoded == null || encoded.length() != SIZE) {
            return null;
        }
        int[] frame = new int[SIZE];
        for (int i = 0; i < encoded.length(); i++) {
            frame[i] = encoded.charAt(i) == '1' ? 0x00FFFFFF : 0x00000000;
        }
        return frame;
    }

    static int loadDisplayDurationMinutes(Context context) {
        return prefs(context).getInt(KEY_DISPLAY_DURATION_MINUTES, DEFAULT_DISPLAY_DURATION_MINUTES);
    }

    static void saveDisplayDurationMinutes(Context context, int minutes) {
        prefs(context).edit()
                .putInt(KEY_DISPLAY_DURATION_MINUTES, minutes)
                .remove(KEY_DISPLAY_SESSION_STARTED_AT)
                .apply();
    }

    static long loadDisplaySessionStartedAt(Context context) {
        return prefs(context).getLong(KEY_DISPLAY_SESSION_STARTED_AT, 0L);
    }

    static void saveDisplaySessionStartedAt(Context context, long elapsedRealtime) {
        prefs(context).edit().putLong(KEY_DISPLAY_SESSION_STARTED_AT, elapsedRealtime).apply();
    }

    static void clearDisplaySession(Context context) {
        prefs(context).edit().remove(KEY_DISPLAY_SESSION_STARTED_AT).apply();
    }

    static int loadQuietStartHour(Context context) {
        return prefs(context).getInt(KEY_QUIET_START_HOUR, DEFAULT_QUIET_START_HOUR);
    }

    static int loadQuietEndHour(Context context) {
        return prefs(context).getInt(KEY_QUIET_END_HOUR, DEFAULT_QUIET_END_HOUR);
    }

    static void saveQuietHours(Context context, int startHour, int endHour) {
        prefs(context).edit()
                .putInt(KEY_QUIET_START_HOUR, normalizeHour(startHour))
                .putInt(KEY_QUIET_END_HOUR, normalizeHour(endHour))
                .apply();
    }

    private static int normalizeHour(int hour) {
        return ((hour % 24) + 24) % 24;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
