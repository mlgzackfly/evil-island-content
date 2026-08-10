package tw.zack.evilisland.model;

import org.bukkit.Material;

public enum ExpeditionStoryChoice {
    SECURE("secure", "穩固防線", Material.SHIELD,
            "先封存風險與不明通路，讓新城能在明確界線內站穩。"),
    CONNECT("connect", "保留往來", Material.COMPASS,
            "留下可辨識的通路與訊號，讓不同族群仍有重新接觸的餘地。" );

    private final String id;
    private final String display;
    private final Material icon;
    private final String description;

    ExpeditionStoryChoice(String id, String display, Material icon, String description) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.description = description;
    }

    public String id() { return id; }
    public String display() { return display; }
    public Material icon() { return icon; }
    public String description() { return description; }

    public static ExpeditionStoryChoice parse(String value) {
        if (value == null || value.isBlank()) return null;
        for (ExpeditionStoryChoice choice : values()) if (choice.id.equalsIgnoreCase(value)) return choice;
        return null;
    }
}
