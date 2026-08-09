package tw.zack.evilisland.model;

import java.util.Locale;

public enum LivingEventArc {
    SAFE_ROAD("safe_road", "補給道路"),
    BORDER_SIGNALS("border_signals", "邊境警訊"),
    OUTLAND_RELATIONS("outland_relations", "異族邊界"),
    COASTAL_WARNING("coastal_warning", "海岸警戒");

    private final String id;
    private final String display;

    LivingEventArc(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() { return id; }
    public String display() { return display; }

    public static LivingEventArc parse(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (LivingEventArc arc : values()) {
            if (arc.id.equals(normalized)) return arc;
        }
        return null;
    }
}
