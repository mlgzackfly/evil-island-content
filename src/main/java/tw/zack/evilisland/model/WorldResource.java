package tw.zack.evilisland.model;

import org.bukkit.Material;

import java.util.Locale;

public enum WorldResource {
    TIMBER("timber", "建材木料", Material.OAK_LOG),
    MASONRY("masonry", "築城石材", Material.STONE_BRICKS),
    PROVISIONS("provisions", "城防糧秣", Material.BREAD),
    COMPONENTS("components", "息壤構件", Material.COPPER_INGOT),
    SPECIAL("special", "異族素材", Material.AMETHYST_SHARD);

    private final String id;
    private final String display;
    private final Material icon;

    WorldResource(String id, String display, Material icon) {
        this.id = id;
        this.display = display;
        this.icon = icon;
    }

    public String id() { return id; }
    public String display() { return display; }
    public Material icon() { return icon; }

    public static WorldResource parse(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (WorldResource resource : values()) {
            if (resource.id.equals(normalized)) return resource;
        }
        return null;
    }
}
