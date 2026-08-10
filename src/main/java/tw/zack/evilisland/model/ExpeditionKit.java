package tw.zack.evilisland.model;

import org.bukkit.Material;

public enum ExpeditionKit {
    MEDICAL(0, "medical", "醫療包", Material.GOLDEN_APPLE, "穩定傷員並降低撤運壓力。"),
    SCOUTING(1, "scouting", "偵察器材", Material.SPYGLASS, "辨認假跡、巡邏與安全通路。"),
    PROVISIONS(2, "provisions", "備用糧秣", Material.COOKED_BEEF, "支撐途中休整與長程繞行。"),
    DEMOLITION(3, "demolition", "破壞工具", Material.IRON_PICKAXE, "處理崩塌、封鎖及補給節點。" );

    private final int bit;
    private final String id;
    private final String display;
    private final Material icon;
    private final String description;

    ExpeditionKit(int bit, String id, String display, Material icon, String description) {
        this.bit = bit;
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.description = description;
    }

    public int mask() { return 1 << bit; }
    public String id() { return id; }
    public String display() { return display; }
    public Material icon() { return icon; }
    public String description() { return description; }

    public static boolean contains(int mask, ExpeditionKit kit) {
        return kit != null && (mask & kit.mask()) != 0;
    }
}
