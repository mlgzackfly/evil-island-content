package tw.zack.evilisland.model;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

public enum FactionContract {
    QUANRONG_HUNT("quanrong_hunt", "犬戎獵場", Faction.QUANRONG, Material.BONE,
            List.of(MissionType.SCOUT, MissionType.ESCORT), WorldResource.PROVISIONS),
    MAO_SETTLEMENT("mao_settlement", "毛族安置", Faction.MAO, Material.WHITE_WOOL,
            List.of(MissionType.RESCUE, MissionType.GATHER), WorldResource.TIMBER),
    NAJIN_CARAVAN("najin_caravan", "納金商旅", Faction.NAJIN, Material.COPPER_INGOT,
            List.of(MissionType.ESCORT, MissionType.GATHER), WorldResource.COMPONENTS),
    QIULONG_TIDE_ROUTE("qiulong_tide_route", "虯龍潮路", Faction.QIULONG, Material.PRISMARINE_CRYSTALS,
            List.of(MissionType.SCOUT, MissionType.DEFENSE), WorldResource.SPECIAL);

    private final String id;
    private final String display;
    private final Faction faction;
    private final Material icon;
    private final List<MissionType> stages;
    private final WorldResource cooperationResource;

    FactionContract(String id, String display, Faction faction, Material icon, List<MissionType> stages,
                    WorldResource cooperationResource) {
        this.id = id;
        this.display = display;
        this.faction = faction;
        this.icon = icon;
        this.stages = List.copyOf(stages);
        this.cooperationResource = cooperationResource;
    }

    public String id() { return id; }
    public String display() { return display; }
    public Faction faction() { return faction; }
    public Material icon() { return icon; }
    public int stageCount() { return stages.size(); }
    public MissionType requiredType(int progress) {
        return progress >= 0 && progress < stages.size() ? stages.get(progress) : null;
    }
    public WorldResource cooperationResource() { return cooperationResource; }
    public boolean accepts(MissionType type) { return stages.contains(type); }

    public static FactionContract forWeek(int week, CityRoute route) {
        int phase = Math.max(1, Math.min(4, week));
        if (route == CityRoute.FORTRESS) return switch (phase) {
            case 1 -> QUANRONG_HUNT;
            case 2 -> MAO_SETTLEMENT;
            case 3 -> NAJIN_CARAVAN;
            default -> QIULONG_TIDE_ROUTE;
        };
        if (route == CityRoute.EXPEDITION) return switch (phase) {
            case 1 -> NAJIN_CARAVAN;
            case 2 -> QIULONG_TIDE_ROUTE;
            case 3 -> QUANRONG_HUNT;
            default -> MAO_SETTLEMENT;
        };
        if (route == CityRoute.QI_CIVIC) return switch (phase) {
            case 1 -> MAO_SETTLEMENT;
            case 2 -> NAJIN_CARAVAN;
            case 3 -> QIULONG_TIDE_ROUTE;
            default -> QUANRONG_HUNT;
        };
        return switch (phase) {
            case 1 -> NAJIN_CARAVAN;
            case 2 -> MAO_SETTLEMENT;
            case 3 -> QUANRONG_HUNT;
            default -> QIULONG_TIDE_ROUTE;
        };
    }

    public static FactionContract forFaction(Faction faction) {
        for (FactionContract contract : values()) {
            if (contract.faction == faction) return contract;
        }
        return null;
    }

    public static FactionContract parse(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (FactionContract contract : values()) {
            if (contract.id.equals(normalized)) return contract;
        }
        return null;
    }
}
