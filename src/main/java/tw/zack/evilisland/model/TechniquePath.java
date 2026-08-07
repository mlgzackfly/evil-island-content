package tw.zack.evilisland.model;

import org.bukkit.Material;

import java.util.Locale;

public enum TechniquePath {
    UNTRAINED("untrained", "尚未研習", Material.GRAY_DYE, "維持兵器原有招式"),
    MOBILITY("mobility", "疾行運用", Material.FEATHER, "招式後取得短暫移動能力"),
    CONTROL("control", "制敵運用", Material.CHAIN, "招式附帶短暫緩速或牽制"),
    GUARD("guard", "守勢運用", Material.SHIELD, "招式後取得短暫減傷");

    private final String id;
    private final String display;
    private final Material icon;
    private final String summary;

    TechniquePath(String id, String display, Material icon, String summary) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.summary = summary;
    }

    public String id() { return id; }
    public String display() { return display; }
    public Material icon() { return icon; }
    public String summary() { return summary; }

    public static TechniquePath parse(String value) {
        if (value == null) return UNTRAINED;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (TechniquePath path : values()) {
            if (path.id.equals(normalized)) return path;
        }
        return UNTRAINED;
    }
}
