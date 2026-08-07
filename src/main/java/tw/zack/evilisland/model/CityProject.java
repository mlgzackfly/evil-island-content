package tw.zack.evilisland.model;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public enum CityProject {
    WALLS("walls", "新城外牆", Material.STONE_BRICK_WALL, "提高工事耐久並降低守城突破代價"),
    QI_MIRROR("qi_mirror", "聚炁鏡陣", Material.LODESTONE, "改善新城炁息恢復並支援遠行"),
    WORKSHOP("workshop", "軍械工坊", Material.SMITHING_TABLE, "開放兵器技法研習與維護"),
    SCOUT_POST("scout_post", "輕疾站", Material.SPYGLASS, "揭露危險路線並增加探索情報"),
    AIR_DEFENSE("air_defense", "防空弩臺", Material.CROSSBOW, "壓制飛行妖族及決戰增援");

    private final String id;
    private final String display;
    private final Material icon;
    private final String benefit;

    CityProject(String id, String display, Material icon, String benefit) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.benefit = benefit;
    }

    public String id() { return id; }
    public String display() { return display; }
    public Material icon() { return icon; }
    public String benefit() { return benefit; }
    public int maximumLevel() { return 3; }

    public Map<WorldResource, Integer> costForLevel(int nextLevel) {
        int level = Math.max(1, Math.min(maximumLevel(), nextLevel));
        EnumMap<WorldResource, Integer> cost = new EnumMap<>(WorldResource.class);
        switch (this) {
            case WALLS -> {
                cost.put(WorldResource.MASONRY, 5 + level * 3);
                cost.put(WorldResource.TIMBER, 2 + level);
            }
            case QI_MIRROR -> {
                cost.put(WorldResource.COMPONENTS, 4 + level * 2);
                cost.put(WorldResource.SPECIAL, level);
            }
            case WORKSHOP -> {
                cost.put(WorldResource.TIMBER, 3 + level * 2);
                cost.put(WorldResource.COMPONENTS, 3 + level * 2);
            }
            case SCOUT_POST -> {
                cost.put(WorldResource.TIMBER, 4 + level * 2);
                cost.put(WorldResource.PROVISIONS, 2 + level);
            }
            case AIR_DEFENSE -> {
                cost.put(WorldResource.MASONRY, 4 + level * 2);
                cost.put(WorldResource.COMPONENTS, 5 + level * 2);
                cost.put(WorldResource.SPECIAL, level);
            }
        }
        return Map.copyOf(cost);
    }

    public static CityProject parse(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (CityProject project : values()) {
            if (project.id.equals(normalized)) return project;
        }
        return null;
    }
}
