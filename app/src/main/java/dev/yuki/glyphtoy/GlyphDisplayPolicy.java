package dev.yuki.glyphtoy;

import android.content.Context;

import java.time.LocalTime;

final class GlyphDisplayPolicy {
    private GlyphDisplayPolicy() {}

    static boolean shouldTurnOff(Context context, LocalTime now) {
        migrateOldAutoOffPreferences(context);
        return false;
    }

    static void restartDisplaySession(Context context) {
        MatrixStorage.clearDisplaySession(context);
    }

    static long millisUntilDisplayDeadline(Context context) {
        return Long.MAX_VALUE;
    }

    static String describe(Context context) {
        migrateOldAutoOffPreferences(context);
        return "アプリ側の自動消灯なし / 選択モード: "
                + MatrixStorage.loadDisplayMode(context).label()
                + "（手動で変えるまで維持）";
    }

    private static void migrateOldAutoOffPreferences(Context context) {
        MatrixStorage.loadDisplayDurationMinutes(context);
        MatrixStorage.loadQuietStartHour(context);
        MatrixStorage.loadQuietEndHour(context);
        MatrixStorage.clearDisplaySession(context);
    }
}
