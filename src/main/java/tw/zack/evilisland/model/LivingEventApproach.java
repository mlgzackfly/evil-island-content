package tw.zack.evilisland.model;

import java.util.Locale;

public enum LivingEventApproach {
    NONE("none", "尚未決定"),
    FIELD("field", "現場應對"),
    LOGISTICS("logistics", "物資調度");

    private final String id;
    private final String display;

    LivingEventApproach(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() { return id; }
    public String display() { return display; }

    public static LivingEventApproach parse(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (LivingEventApproach approach : values()) {
            if (approach.id.equals(normalized)) return approach;
        }
        return null;
    }
}
