package tw.zack.evilisland.world;

import java.util.Locale;

public enum WorldLandmark {
    NEW_CITY("new-city", "東大陸新城", 4300, 0),
    SUI_AN("suian", "歲安城", 700, 70),
    QINGTIAN_TOWER("qingtian", "擎天塔", 700, 0),
    JIUHUI_MOUNTAIN("jiuhui", "九回山", 1180, 0),
    JIUHUI_CITY("jiuhui-city", "九回城", 1250, 0),
    MOUNTAIN_PASS("mountain-pass", "山口鎮", 1540, -80),
    RONGXU_CAVE("rongxu", "絨須洞", 1320, 620),
    DRAGON_PALACE("dragon-palace", "龍宮", 2500, -1900),
    MAGIC_ISLAND("magic-island", "魔法島", 3420, 1750),
    WESTERN_WILDS("western-wilds", "西方荒野", -1500, 0),
    UDING_CLIFF("uding-cliff", "宇定高原東壁", 1480, 320);

    private final String id;
    private final String display;
    private final int x;
    private final int z;

    WorldLandmark(String id, String display, int x, int z) {
        this.id = id;
        this.display = display;
        this.x = x;
        this.z = z;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public int x() {
        return x;
    }

    public int z() {
        return z;
    }

    public static WorldLandmark parse(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (WorldLandmark landmark : values()) {
            if (landmark.id.equals(normalized)) {
                return landmark;
            }
        }
        return null;
    }
}
