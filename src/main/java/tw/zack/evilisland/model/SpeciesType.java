package tw.zack.evilisland.model;

import dev.zack.rpgengine.ContentDefinition;

import java.util.Locale;

public enum SpeciesType implements ContentDefinition {
    ZAOCHI("zaochi", "鑿齒戰士", true, false),
    XINGTIAN("xingtian", "刑天統領", true, true),
    QUANRONG_HUNTER("quanrong_hunter", "犬戎獵手", true, false),
    QUANRONG_ALPHA("quanrong_alpha", "犬戎領獵", true, true),
    YUJIANG_RAIDER("yujiang_raider", "禺彊掠空者", true, false),
    YUJIANG_WINDBREAKER("yujiang_windbreaker", "禺彊破風者", true, true),
    MAO_ENVOY("mao_envoy", "毛族使者", false, false),
    NAJIN_TRADER("najin_trader", "納金族商旅", false, false);

    private final String id;
    private final String display;
    private final boolean hostile;
    private final boolean elite;

    SpeciesType(String id, String display, boolean hostile, boolean elite) {
        this.id = id;
        this.display = display;
        this.hostile = hostile;
        this.elite = elite;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public boolean hostile() { return hostile; }
    public boolean elite() { return elite; }

    public static SpeciesType parse(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (SpeciesType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
