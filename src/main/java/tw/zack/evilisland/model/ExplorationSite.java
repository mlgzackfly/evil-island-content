package tw.zack.evilisland.model;

import org.bukkit.Material;
import tw.zack.evilisland.world.WorldLandmark;

import java.util.Locale;

public enum ExplorationSite {
    EASTERN_ROUTE("eastern_route", "東境補給路", WorldLandmark.NEW_CITY, 180, 70, Material.CAMPFIRE,
            WorldResource.PROVISIONS),
    UDING_WALL("uding_wall", "宇定高原東壁", WorldLandmark.UDING_CLIFF, 0, 0, Material.SPYGLASS,
            WorldResource.MASONRY),
    RONGXU_APPROACH("rongxu_approach", "絨須洞外緣", WorldLandmark.RONGXU_CAVE, -45, -30, Material.WHITE_WOOL,
            WorldResource.TIMBER),
    WESTERN_TRACE("western_trace", "西方荒野遺跡", WorldLandmark.WESTERN_WILDS, 70, 35, Material.BRUSH,
            WorldResource.COMPONENTS),
    DRAGON_COAST("dragon_coast", "龍宮海岸觀測點", WorldLandmark.DRAGON_PALACE, 85, 60,
            Material.PRISMARINE_CRYSTALS, WorldResource.SPECIAL);

    private final String id;
    private final String display;
    private final WorldLandmark landmark;
    private final int offsetX;
    private final int offsetZ;
    private final Material icon;
    private final WorldResource reward;

    ExplorationSite(String id, String display, WorldLandmark landmark, int offsetX, int offsetZ,
                    Material icon, WorldResource reward) {
        this.id = id;
        this.display = display;
        this.landmark = landmark;
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
        this.icon = icon;
        this.reward = reward;
    }

    public String id() { return id; }
    public String display() { return display; }
    public WorldLandmark landmark() { return landmark; }
    public int offsetX() { return offsetX; }
    public int offsetZ() { return offsetZ; }
    public Material icon() { return icon; }
    public WorldResource reward() { return reward; }

    public static ExplorationSite parse(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (ExplorationSite site : values()) {
            if (site.id.equals(normalized)) return site;
        }
        return null;
    }
}
