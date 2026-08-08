package tw.zack.evilisland.model;

import org.bukkit.Material;

import java.util.Arrays;

public enum InheritanceType {
    MAGIC("magic", "魔法傳承", Material.ENCHANTED_BOOK, MissionType.SCOUT,
            Material.AMETHYST_SHARD, 4, "短距離移形，不能穿越實體障礙。"),
    LIGHT_SPIRIT("light_spirit", "光靈傳承", Material.GLOWSTONE_DUST, MissionType.RESCUE,
            Material.GLOWSTONE_DUST, 6, "為附近隊員恢復少量生命與炁息。"),
    MOUNTAIN_SLEEP("mountain_sleep", "山眠傳承", Material.MOSS_BLOCK, MissionType.DEFENSE,
            Material.MOSS_BLOCK, 8, "短時間穩固身體並承受正面壓力。"),
    BINDING("binding", "縛妖術傳承", Material.LEAD, MissionType.PATROL,
            Material.STRING, 12, "限制最近敵人的移動，不直接增加傷害。");

    private final String id;
    private final String display;
    private final Material icon;
    private final MissionType missionType;
    private final Material material;
    private final int materialAmount;
    private final String ability;

    InheritanceType(String id, String display, Material icon, MissionType missionType,
                    Material material, int materialAmount, String ability) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.missionType = missionType;
        this.material = material;
        this.materialAmount = materialAmount;
        this.ability = ability;
    }

    public String id() { return id; }
    public String display() { return display; }
    public Material icon() { return icon; }
    public MissionType missionType() { return missionType; }
    public Material material() { return material; }
    public int materialAmount() { return materialAmount; }
    public String ability() { return ability; }

    public static InheritanceType parse(String id) {
        if (id == null) return null;
        return Arrays.stream(values()).filter(value -> value.id.equalsIgnoreCase(id)).findFirst().orElse(null);
    }
}
