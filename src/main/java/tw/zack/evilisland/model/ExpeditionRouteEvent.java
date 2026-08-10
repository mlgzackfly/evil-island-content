package tw.zack.evilisland.model;

import org.bukkit.Material;

public enum ExpeditionRouteEvent {
    COLLAPSED_PATH("collapsed_path", "崩塌路段", Material.COBBLESTONE, ExpeditionKit.DEMOLITION,
            "前路被崩落土石切斷。"),
    ENEMY_PATROL("enemy_patrol", "敵軍巡邏", Material.CROSSBOW, ExpeditionKit.SCOUTING,
            "巡邏隊正在反覆掃視道路。"),
    WOUNDED_SCOUT("wounded_scout", "負傷斥候", Material.WHITE_WOOL, ExpeditionKit.MEDICAL,
            "一名斥候倒在路旁，仍有微弱反應。"),
    ABANDONED_CACHE("abandoned_cache", "棄置補給", Material.BARREL, ExpeditionKit.PROVISIONS,
            "半毀的補給箱可能還有可用物資。"),
    FALSE_SIGNAL("false_signal", "可疑信號", Material.REDSTONE_TORCH, ExpeditionKit.SCOUTING,
            "遠處信號與已知標記並不一致。"),
    SAFE_REST("safe_rest", "隱蔽休整點", Material.CAMPFIRE, ExpeditionKit.PROVISIONS,
            "背風處適合短暫整理隊伍。" );

    private final String id;
    private final String display;
    private final Material icon;
    private final ExpeditionKit recommendedKit;
    private final String description;

    ExpeditionRouteEvent(String id, String display, Material icon, ExpeditionKit recommendedKit,
                         String description) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.recommendedKit = recommendedKit;
        this.description = description;
    }

    public String id() { return id; }
    public String display() { return display; }
    public Material icon() { return icon; }
    public ExpeditionKit recommendedKit() { return recommendedKit; }
    public String description() { return description; }
}
