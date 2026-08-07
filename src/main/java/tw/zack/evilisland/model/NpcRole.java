package tw.zack.evilisland.model;

import java.util.Arrays;

public enum NpcRole {
    YANGWU("yangwu", "揚武巡防員"),
    WUJI("wuji", "無跡斥候");

    private final String id;
    private final String display;

    NpcRole(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public static NpcRole parse(String id) {
        if (id == null) return null;
        return Arrays.stream(values()).filter(role -> role.id.equalsIgnoreCase(id)).findFirst().orElse(null);
    }
}
