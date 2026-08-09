package tw.zack.evilisland.model;

import java.util.Locale;

public enum ResidentRole {
    WATCHER("watcher", "城門守望員"),
    ARTISAN("artisan", "息壤工匠"),
    PORTER("porter", "補給搬運員"),
    HEALER("healer", "傷患照料員"),
    SCOUT("scout", "輕疾抄報員"),
    MERCHANT("merchant", "遠路行商");

    private final String id;
    private final String display;

    ResidentRole(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() { return id; }
    public String display() { return display; }

    public static ResidentRole parse(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (ResidentRole role : values()) if (role.id.equals(normalized)) return role;
        return null;
    }
}
