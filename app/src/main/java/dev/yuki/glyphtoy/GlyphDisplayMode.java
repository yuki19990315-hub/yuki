package dev.yuki.glyphtoy;

enum GlyphDisplayMode {
    CUSTOM("custom", "選択中のコマ"),
    CLOCK("clock", "時計"),
    HEART("heart", "ハート");

    static final GlyphDisplayMode DEFAULT = CUSTOM;

    private final String storageValue;
    private final String label;

    GlyphDisplayMode(String storageValue, String label) {
        this.storageValue = storageValue;
        this.label = label;
    }

    String storageValue() {
        return storageValue;
    }

    String label() {
        return label;
    }

    GlyphDisplayMode next() {
        if (this == CUSTOM) {
            return CLOCK;
        }
        if (this == CLOCK) {
            return HEART;
        }
        return CUSTOM;
    }

    static GlyphDisplayMode fromStorageValue(String value) {
        for (GlyphDisplayMode mode : values()) {
            if (mode.storageValue.equals(value)) {
                return mode;
            }
        }
        return DEFAULT;
    }
}
