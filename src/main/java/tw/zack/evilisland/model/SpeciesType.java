package tw.zack.evilisland.model;

import java.util.Locale;

public enum SpeciesType {
    ZAOCHI("zaochi", "鑿齒戰士"),
    XINGTIAN("xingtian", "刑天統領");

    private final String id;
    private final String display;

    SpeciesType(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public static SpeciesType parse(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (SpeciesType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
