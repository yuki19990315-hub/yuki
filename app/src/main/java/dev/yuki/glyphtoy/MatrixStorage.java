package dev.yuki.glyphtoy;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

final class MatrixStorage {
    private static final String PREFS = "matrix_storage";
    private static final String KEY_CUSTOM_FRAME = "custom_frame";
    private static final String KEY_GIF_FRAMES = "gif_frames";
    private static final String KEY_SELECTED_GIF_FRAME_INDEX = "selected_gif_frame_index";
    private static final String KEY_DISPLAY_DURATION_MINUTES = "display_duration_minutes";
    private static final String KEY_DISPLAY_SESSION_STARTED_AT = "display_session_started_at";
    private static final String KEY_DISPLAY_SESSION_DEADLINE_AT = "display_session_deadline_at";
    private static final String KEY_QUIET_START_HOUR = "quiet_start_hour";
    private static final String KEY_QUIET_END_HOUR = "quiet_end_hour";
    private static final int SIZE = PixelMatrix.PHONE_4A_PRO_SIZE * PixelMatrix.PHONE_4A_PRO_SIZE;
    private static final int DEFAULT_DISPLAY_DURATION_MINUTES = 15;
    private static final int DEFAULT_QUIET_START_HOUR = 23;
    private static final int DEFAULT_QUIET_END_HOUR = 7;

    private MatrixStorage() {}

    static void saveCustomFrame(Context context, int[] frame) {
        prefs(context).edit().putString(KEY_CUSTOM_FRAME, encodeFrame(frame)).apply();
    }

    static int[] loadCustomFrame(Context context) {
        return decodeFrame(prefs(context).getString(KEY_CUSTOM_FRAME, null));
    }

    static void saveGifFrames(Context context, int[][] frames) {
        if (frames == null || frames.length == 0) {
            prefs(context).edit().remove(KEY_GIF_FRAMES).apply();
            return;
        }
        StringBuilder encoded = new StringBuilder(frames.length * (SIZE + 1));
        for (int i = 0; i < frames.length; i++) {
            if (i > 0) {
                encoded.append('|');
            }
            encoded.append(encodeFrame(frames[i]));
        }
        prefs(context).edit().putString(KEY_GIF_FRAMES, encoded.toString()).apply();
    }

    static int[][] loadGifFrames(Context context) {
        String encoded = prefs(context).getString(KEY_GIF_FRAMES, null);
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        String[] parts = encoded.split("\\|", -1);
        List<int[]> frames = new ArrayList<>();
        for (String part : parts) {
            int[] frame = decodeFrame(part);
            if (frame != null) {
                frames.add(frame);
            }
        }
        return frames.isEmpty() ? null : frames.toArray(new int[frames.size()][]);
    }

    static int loadSelectedGifFrameIndex(Context context) {
        return Math.max(0, prefs(context).getInt(KEY_SELECTED_GIF_FRAME_INDEX, 0));
    }

    static void saveSelectedGifFrameIndex(Context context, int index) {
        prefs(context).edit().putInt(KEY_SELECTED_GIF_FRAME_INDEX, Math.max(0, index)).apply();
    }

    static int loadDisplayDurationMinutes(Context context) {
        return prefs(context).getInt(KEY_DISPLAY_DURATION_MINUTES, DEFAULT_DISPLAY_DURATION_MINUTES);
    }

    static void saveDisplayDurationMinutes(Context context, int minutes) {
        prefs(context).edit()
                .putInt(KEY_DISPLAY_DURATION_MINUTES, minutes)
                .remove(KEY_DISPLAY_SESSION_STARTED_AT)
                .remove(KEY_DISPLAY_SESSION_DEADLINE_AT)
                .apply();
    }

    static long loadDisplaySessionStartedAt(Context context) {
        return prefs(context).getLong(KEY_DISPLAY_SESSION_STARTED_AT, 0L);
    }

    static long loadDisplaySessionDeadlineAt(Context context) {
        return prefs(context).getLong(KEY_DISPLAY_SESSION_DEADLINE_AT, 0L);
    }

    static void saveDisplaySession(Context context, long startedAt, long deadlineAt) {
        prefs(context).edit()
                .putLong(KEY_DISPLAY_SESSION_STARTED_AT, startedAt)
                .putLong(KEY_DISPLAY_SESSION_DEADLINE_AT, deadlineAt)
                .apply();
    }

    static void clearDisplaySession(Context context) {
        prefs(context).edit()
                .remove(KEY_DISPLAY_SESSION_STARTED_AT)
                .remove(KEY_DISPLAY_SESSION_DEADLINE_AT)
                .apply();
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

    private static String encodeFrame(int[] frame) {
        StringBuilder encoded = new StringBuilder(SIZE);
        for (int i = 0; i < SIZE; i++) {
            int value = frame != null && i < frame.length ? frame[i] : 0;
            encoded.append(value == 0 ? '0' : '1');
        }
        return encoded.toString();
    }

    private static int[] decodeFrame(String encoded) {
        if (encoded == null || encoded.length() != SIZE) {
            return null;
        }
        int[] frame = new int[SIZE];
        for (int i = 0; i < encoded.length(); i++) {
            frame[i] = encoded.charAt(i) == '1' ? 0x00FFFFFF : 0x00000000;
        }
        return frame;
    }

    private static int normalizeHour(int hour) {
        return ((hour % 24) + 24) % 24;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
