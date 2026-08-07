package tw.zack.evilisland.model;

import org.bukkit.Material;

import java.util.Locale;

public enum Faction {
    SUI_AN("sui_an", "歲安軍團", Material.IRON_SWORD),
    NEW_CITY("new_city", "人類新城", Material.BELL),
    QUANRONG("quanrong", "犬戎", Material.BONE),
    MAO("mao", "毛族", Material.WHITE_WOOL),
    NAJIN("najin", "納金族", Material.GOLD_NUGGET),
    QIULONG("qiulong", "虯龍", Material.PRISMARINE_CRYSTALS);

    private final String id;
    private final String display;
    private final Material icon;

    Faction(String id, String display, Material icon) {
        this.id = id;
        this.display = display;
        this.icon = icon;
    }

    public String id() { return id; }
    public String display() { return display; }
    public Material icon() { return icon; }

    public String relation(int reputation) {
        if (reputation <= -50) return "敵對";
        if (reputation <= -15) return "警戒";
        if (reputation < 25) return "中立";
        if (reputation < 60) return "互利";
        return "盟約";
    }

    public static Faction parse(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (Faction faction : values()) {
            if (faction.id.equals(normalized)) return faction;
        }
        return null;
    }
}
