package tw.zack.evilisland.model;

import org.bukkit.Material;

public enum ExpeditionOperation {
    LOST_CONVOY(ExplorationSite.EASTERN_ROUTE, "lost_convoy", "失聯車隊", Material.CHEST_MINECART,
            "追查失聯補給，確認倖存者並回收可用物資。"),
    BLOCKADE_INFILTRATION(ExplorationSite.EASTERN_ROUTE, "blockade_infiltration", "封鎖線滲透", Material.TRIPWIRE_HOOK,
            "辨識敵軍假跡，關閉警報並打開補給通道。"),
    SUPPLY_NODE_SABOTAGE(ExplorationSite.EASTERN_ROUTE, "supply_node_sabotage", "補給節點破壞", Material.TNT,
            "標定兩處節點，在敵軍反應前同步破壞。"),
    CASUALTY_EVACUATION(ExplorationSite.EASTERN_ROUTE, "casualty_evacuation", "傷員撤運", Material.GOLDEN_APPLE,
            "取得醫療線索，同時穩定兩名分散的傷員。"),
    CLIFF_RELAY(ExplorationSite.UDING_WALL, "cliff_relay", "崖壁傳訊接力", Material.LIGHTNING_ROD,
            "沿高差路線校正傳訊點，維持高原東壁聯絡。"),
    WIND_SURVEY(ExplorationSite.UDING_WALL, "wind_survey", "風口觀測", Material.GOAT_HORN,
            "在暴露稜線完成多點觀測，辨識巡獵動向。"),
    BOUNDARY_ESCORT(ExplorationSite.RONGXU_APPROACH, "boundary_escort", "邊界引導", Material.WHITE_BANNER,
            "依毛族邊界標記引導隊伍，不以戰鬥破壞領地默契。"),
    MISSING_ENVOY(ExplorationSite.RONGXU_APPROACH, "missing_envoy", "失聯使者", Material.WRITABLE_BOOK,
            "核對使者足跡與訊息，建立安全會合點。"),
    RUIN_MAPPING(ExplorationSite.WESTERN_TRACE, "ruin_mapping", "遺跡測繪", Material.BRUSH,
            "在有限攜帶能力下選擇最有價值的遺跡證據。"),
    RELIC_RECOVERY(ExplorationSite.WESTERN_TRACE, "relic_recovery", "遺物回收", Material.BRICK,
            "判斷遺物風險並選擇可安全帶回的樣本。"),
    TIDE_OBSERVATION(ExplorationSite.DRAGON_COAST, "tide_observation", "潮路觀測", Material.HEART_OF_THE_SEA,
            "在潮路改變前同步觀測兩處海岸信標。"),
    SKY_WARNING(ExplorationSite.DRAGON_COAST, "sky_warning", "掠空警戒", Material.PHANTOM_MEMBRANE,
            "建立海岸空中警戒，及時撤回禺彊活動情報。" );

    private final ExplorationSite site;
    private final String id;
    private final String display;
    private final Material icon;
    private final String description;

    ExpeditionOperation(ExplorationSite site, String id, String display, Material icon, String description) {
        this.site = site;
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.description = description;
    }

    public String id() { return id; }
    public ExplorationSite site() { return site; }
    public String display() { return display; }
    public Material icon() { return icon; }
    public String description() { return description; }

    public static ExpeditionOperation parse(String value) {
        if (value == null) return null;
        for (ExpeditionOperation operation : values()) if (operation.id.equalsIgnoreCase(value)) return operation;
        return null;
    }
}
