package tw.zack.evilisland.model;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

public enum EventChain {
    SAFE_ROUTE("safe_route", "東境安全線", Material.RAIL,
            List.of(MissionType.SCOUT, MissionType.ESCORT, MissionType.GATHER)),
    DISPLACED_PEOPLE("displaced_people", "荒原流民", Material.TOTEM_OF_UNDYING,
            List.of(MissionType.RESCUE, MissionType.GATHER, MissionType.DEFENSE)),
    ENEMY_MUSTER("enemy_muster", "鑿齒集結", Material.IRON_AXE,
            List.of(MissionType.PATROL, MissionType.SCOUT, MissionType.DEFENSE));

    private final String id;
    private final String display;
    private final Material icon;
    private final List<MissionType> stages;

    EventChain(String id, String display, Material icon, List<MissionType> stages) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.stages = List.copyOf(stages);
    }

    public String id() { return id; }
    public String display() { return display; }
    public Material icon() { return icon; }
    public int stageCount() { return stages.size(); }
    public MissionType requiredType(int progress) {
        return progress >= 0 && progress < stages.size() ? stages.get(progress) : null;
    }

    public static EventChain parse(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (EventChain chain : values()) {
            if (chain.id.equals(normalized)) return chain;
        }
        return null;
    }
}
