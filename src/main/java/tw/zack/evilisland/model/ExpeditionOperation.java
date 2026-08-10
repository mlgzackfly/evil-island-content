package tw.zack.evilisland.model;

import org.bukkit.Material;

public enum ExpeditionOperation {
    LOST_CONVOY("lost_convoy", "失聯車隊", Material.CHEST_MINECART,
            "追查失聯補給，確認倖存者並回收可用物資。"),
    BLOCKADE_INFILTRATION("blockade_infiltration", "封鎖線滲透", Material.TRIPWIRE_HOOK,
            "辨識敵軍假跡，關閉警報並打開補給通道。"),
    SUPPLY_NODE_SABOTAGE("supply_node_sabotage", "補給節點破壞", Material.TNT,
            "標定兩處節點，在敵軍反應前同步破壞。"),
    CASUALTY_EVACUATION("casualty_evacuation", "傷員撤運", Material.GOLDEN_APPLE,
            "取得醫療線索，同時穩定兩名分散的傷員。" );

    private final String id;
    private final String display;
    private final Material icon;
    private final String description;

    ExpeditionOperation(String id, String display, Material icon, String description) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.description = description;
    }

    public String id() { return id; }
    public String display() { return display; }
    public Material icon() { return icon; }
    public String description() { return description; }

    public static ExpeditionOperation parse(String value) {
        if (value == null) return null;
        for (ExpeditionOperation operation : values()) if (operation.id.equalsIgnoreCase(value)) return operation;
        return null;
    }
}
