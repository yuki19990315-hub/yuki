package dev.yuki.glyphtoy;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

final class PixelMatrix {
    static final int PHONE_4A_PRO_SIZE = 13;
    private static final int ON = 0x00FFFFFF;
    private static final int DIM = 0x00606060;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmm");

    private PixelMatrix() {}

    static int[] clock(LocalTime time) {
        int[] pixels = new int[PHONE_4A_PRO_SIZE * PHONE_4A_PRO_SIZE];
        String value = TIME_FORMAT.format(time);
        drawDigit(pixels, value.charAt(0) - '0', 0, 1);
        drawDigit(pixels, value.charAt(1) - '0', 3, 1);
        set(pixels, 6, 4, time.getSecond() % 2 == 0 ? ON : DIM);
        set(pixels, 6, 7, time.getSecond() % 2 == 0 ? ON : DIM);
        drawDigit(pixels, value.charAt(2) - '0', 7, 1);
        drawDigit(pixels, value.charAt(3) - '0', 10, 1);
        drawHeart(pixels, 4, 10);
        return pixels;
    }

    static int[] heart() {
        int[] pixels = new int[PHONE_4A_PRO_SIZE * PHONE_4A_PRO_SIZE];
        drawHeart(pixels, 4, 4);
        return pixels;
    }

    private static void drawHeart(int[] pixels, int x, int y) {
        int[][] points = {{1,0},{3,0},{0,1},{2,1},{4,1},{0,2},{1,2},{2,2},{3,2},{4,2},{1,3},{2,3},{3,3},{2,4}};
        for (int[] point : points) {
            set(pixels, x + point[0], y + point[1], ON);
        }
    }

    private static void drawDigit(int[] pixels, int digit, int x, int y) {
        String[] rows = DIGITS[digit];
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < rows[row].length(); col++) {
                if (rows[row].charAt(col) == '1') {
                    set(pixels, x + col, y + row, ON);
                }
            }
        }
    }

    private static void set(int[] pixels, int x, int y, int value) {
        if (x < 0 || x >= PHONE_4A_PRO_SIZE || y < 0 || y >= PHONE_4A_PRO_SIZE) {
            return;
        }
        pixels[y * PHONE_4A_PRO_SIZE + x] = value;
    }

    private static final String[][] DIGITS = {
            {"111", "101", "101", "101", "101", "101", "111"},
            {"010", "110", "010", "010", "010", "010", "111"},
            {"111", "001", "001", "111", "100", "100", "111"},
            {"111", "001", "001", "111", "001", "001", "111"},
            {"101", "101", "101", "111", "001", "001", "001"},
            {"111", "100", "100", "111", "001", "001", "111"},
            {"111", "100", "100", "111", "101", "101", "111"},
            {"111", "001", "001", "001", "001", "001", "001"},
            {"111", "101", "101", "111", "101", "101", "111"},
            {"111", "101", "101", "111", "001", "001", "111"}
    };
}
