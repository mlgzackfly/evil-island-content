package tw.zack.evilisland.model;

import org.bukkit.Material;

public enum ExpeditionRoute {
    OLD_ROAD("old_road", "舊驛道", Material.RAIL, 1, 0,
            "路況明確，敵軍警戒與目標時限均衡。"),
    RIDGE("ridge", "北側稜線", Material.GOAT_HORN, 0, -1,
            "地勢暴露，敵襲較強，但同步目標有較長視野。"),
    RIVERBED("riverbed", "乾涸河道", Material.MUD, 0, 1,
            "接敵較少，沿線痕跡分散，必須取得更多情報。" );

    private final String id;
    private final String display;
    private final Material icon;
    private final int dx;
    private final int dz;
    private final String description;

    ExpeditionRoute(String id, String display, Material icon, int dx, int dz, String description) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.dx = dx;
        this.dz = dz;
        this.description = description;
    }

    public String id() { return id; }
    public String display() { return display; }
    public Material icon() { return icon; }
    public int dx() { return dx; }
    public int dz() { return dz; }
    public int perpendicularX() { return -dz; }
    public int perpendicularZ() { return dx; }
    public String description() { return description; }

    public static ExpeditionRoute parse(String value) {
        if (value == null) return null;
        for (ExpeditionRoute route : values()) if (route.id.equalsIgnoreCase(value)) return route;
        return null;
    }
}
