package tw.zack.evilisland.model;

import java.util.Locale;

public enum QiTendency {
    OUTWARD("outward", "發散"),
    INWARD("inward", "內聚");

    private final String id;
    private final String display;

    QiTendency(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public static QiTendency parse(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (QiTendency tendency : values()) {
            if (tendency.id.equals(normalized)) {
                return tendency;
            }
        }
        return null;
    }
}
