package tw.zack.evilisland.model;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public enum LivingEventType {
    SUPPLY_CART_BLOCKED("supply_cart_blocked", "補給車隊受阻", "東境補給路出現連續阻斷，工坊車隊無法按時返城。",
            LivingEventArc.SAFE_ROAD, ExplorationSite.EASTERN_ROUTE, 1, MissionContract.WORKSHOP_CART_ESCORT,
            CampaignMetric.SUPPLY, WorldResource.TIMBER, 2, WorldResource.PROVISIONS, 2),
    LOST_SIGNAL("lost_signal", "高原訊號中斷", "宇定高原東壁的輕疾訊號突然中止，巡查人員去向不明。",
            LivingEventArc.BORDER_SIGNALS, ExplorationSite.UDING_WALL, 1, MissionContract.WUJI_TRACE_RESCUE,
            CampaignMetric.INTELLIGENCE, WorldResource.COMPONENTS, 2, WorldResource.PROVISIONS, 2),
    RONGXU_SHELTER("rongxu_shelter", "洞外安置爭議", "絨須洞外緣的臨時安置地缺乏材料，往來族群開始彼此猜疑。",
            LivingEventArc.OUTLAND_RELATIONS, ExplorationSite.RONGXU_APPROACH, 1,
            MissionContract.TIMBER_REQUISITION, CampaignMetric.MORALE,
            WorldResource.TIMBER, 3, WorldResource.PROVISIONS, 2),
    HUNTING_BOUNDARY("hunting_boundary", "獵界越線", "洞外獵隊越過原有界線，巡防與交涉都面臨壓力。",
            LivingEventArc.OUTLAND_RELATIONS, ExplorationSite.RONGXU_APPROACH, 2,
            MissionContract.MIRROR_PERIMETER, CampaignMetric.DEFENSE,
            WorldResource.PROVISIONS, 3, WorldResource.TIMBER, 1),
    WESTERN_CARAVAN("western_caravan", "西路商隊延誤", "西方荒野的商隊未依約抵達，沿途標記也遭人移動。",
            LivingEventArc.SAFE_ROAD, ExplorationSite.WESTERN_TRACE, 2, MissionContract.SIGNAL_TEAM_ESCORT,
            CampaignMetric.SUPPLY, WorldResource.PROVISIONS, 2, WorldResource.COMPONENTS, 2),
    TIDAL_WARNING("tidal_warning", "潮路警訊", "龍宮海岸潮路變化異常，原有觀測點無法判斷安全時段。",
            LivingEventArc.COASTAL_WARNING, ExplorationSite.DRAGON_COAST, 2,
            MissionContract.NORTH_RIDGE_OBSERVATION, CampaignMetric.INTELLIGENCE,
            WorldResource.COMPONENTS, 2, WorldResource.SPECIAL, 1),
    WALL_PROBE("wall_probe", "高原防線試探", "敵方在宇定高原東壁反覆試探守備空隙，攻勢可能只是前兆。",
            LivingEventArc.BORDER_SIGNALS, ExplorationSite.UDING_WALL, 3, MissionContract.EAST_GATE_HOLD,
            CampaignMetric.DEFENSE, WorldResource.MASONRY, 3, WorldResource.PROVISIONS, 2),
    WIND_RAID("wind_raid", "海岸掠空", "禺彊自海岸上空逼近觀測線，訊息傳遞開始斷續。",
            LivingEventArc.COASTAL_WARNING, ExplorationSite.DRAGON_COAST, 3,
            MissionContract.SIGNAL_POST_DEFENSE, CampaignMetric.INTELLIGENCE,
            WorldResource.COMPONENTS, 3, WorldResource.SPECIAL, 1),
    STOLEN_COMPONENTS("stolen_components", "構件失竊", "運往東境設施的息壤構件在交接前失蹤，護送員也未返城。",
            LivingEventArc.SAFE_ROAD, ExplorationSite.EASTERN_ROUTE, 3, MissionContract.LOST_PORTER_RESCUE,
            CampaignMetric.SUPPLY, WorldResource.TIMBER, 2, WorldResource.COMPONENTS, 3),
    CIVILIAN_WITHDRAWAL("civilian_withdrawal", "外圍撤離受阻", "新城外圍居民的撤離路線遭到壓迫，需要有人打通回城路。",
            LivingEventArc.SAFE_ROAD, ExplorationSite.EASTERN_ROUTE, 4,
            MissionContract.OUTER_FAMILY_ESCORT, CampaignMetric.MORALE,
            WorldResource.PROVISIONS, 3, WorldResource.TIMBER, 2),
    WESTERN_MUSTER("western_muster", "荒野集結痕跡", "西方荒野出現多批新足跡，方向與規模仍無法確認。",
            LivingEventArc.OUTLAND_RELATIONS, ExplorationSite.WESTERN_TRACE, 4,
            MissionContract.DEEP_FIELD_SCOUT, CampaignMetric.INTELLIGENCE,
            WorldResource.PROVISIONS, 2, WorldResource.COMPONENTS, 3),
    SHORELINE_BREACH("shoreline_breach", "海岸防線破口", "龍宮海岸的警戒線遭到突破，散落隊伍正往內陸移動。",
            LivingEventArc.COASTAL_WARNING, ExplorationSite.DRAGON_COAST, 4, MissionContract.RELIEF_COLUMN,
            CampaignMetric.DEFENSE, WorldResource.MASONRY, 3, WorldResource.SPECIAL, 2);

    private final String id;
    private final String display;
    private final String summary;
    private final LivingEventArc arc;
    private final ExplorationSite region;
    private final int preferredWeek;
    private final MissionContract contract;
    private final CampaignMetric metric;
    private final WorldResource firstResource;
    private final int firstAmount;
    private final WorldResource secondResource;
    private final int secondAmount;

    LivingEventType(String id, String display, String summary, LivingEventArc arc, ExplorationSite region,
                    int preferredWeek, MissionContract contract, CampaignMetric metric,
                    WorldResource firstResource, int firstAmount,
                    WorldResource secondResource, int secondAmount) {
        this.id = id;
        this.display = display;
        this.summary = summary;
        this.arc = arc;
        this.region = region;
        this.preferredWeek = preferredWeek;
        this.contract = contract;
        this.metric = metric;
        this.firstResource = firstResource;
        this.firstAmount = firstAmount;
        this.secondResource = secondResource;
        this.secondAmount = secondAmount;
    }

    public String id() { return id; }
    public String display() { return display; }
    public String summary() { return summary; }
    public LivingEventArc arc() { return arc; }
    public ExplorationSite region() { return region; }
    public int preferredWeek() { return preferredWeek; }
    public MissionContract contract() { return contract; }
    public CampaignMetric metric() { return metric; }

    public boolean availableInWeek(int week) {
        return Math.abs(Math.max(1, Math.min(4, week)) - preferredWeek) <= 1;
    }

    public Map<WorldResource, Integer> logisticsCost() {
        EnumMap<WorldResource, Integer> cost = new EnumMap<>(WorldResource.class);
        cost.put(firstResource, firstAmount);
        cost.merge(secondResource, secondAmount, Integer::sum);
        return Map.copyOf(cost);
    }

    public static LivingEventType parse(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (LivingEventType type : values()) {
            if (type.id.equals(normalized)) return type;
        }
        return null;
    }
}
