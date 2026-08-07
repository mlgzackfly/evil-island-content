package tw.zack.evilisland.model;

import java.util.Arrays;

public enum MissionContract {
    EAST_CLEARANCE("east_clearance", "東境清道", "清除靠近緩衝帶的鑿齒斥候。",
            CampaignMetric.DEFENSE, 3, 0, 1.00, 1.00, 1.00, 1.00, 0, 6.0, 1),
    SUPPLY_ROUTE("supply_route", "補給道護衛", "打通工坊車隊使用的東側道路。",
            CampaignMetric.SUPPLY, 4, 1, 1.05, 1.05, 1.05, 1.05, 1, 8.0, 1),
    WATCHTOWER_RECON("watchtower_recon", "荒原瞭望", "逼出藏匿的敵軍，帶回活動情報。",
            CampaignMetric.INTELLIGENCE, 4, -1, 1.25, 1.15, 1.10, 1.05, 0, 11.0, 2),
    MISSING_PATROL("missing_patrol", "失聯隊搜索", "沿失聯路線搜索並壓制追兵。",
            CampaignMetric.MORALE, 4, 1, 1.10, 1.10, 1.10, 1.10, 1, 10.0, 2),
    BROKEN_WARD("broken_ward", "破損防線", "在息壤防線缺口封住突入小隊。",
            CampaignMetric.DEFENSE, 5, 2, 1.15, 1.20, 1.20, 1.20, 1, 6.0, 3),
    REMAINS_RECOVERY("remains_recovery", "遺骸回收", "深入交戰舊址，回收可煉化材料。",
            CampaignMetric.SUPPLY, 5, 2, 1.20, 1.15, 1.15, 1.10, 2, 13.0, 3),
    TRACK_COMMANDER("track_commander", "統領追跡", "追查精銳足跡，迫使刑天提早現身。",
            CampaignMetric.INTELLIGENCE, 5, 0, 1.30, 1.25, 1.30, 1.20, 1, 14.0, 3),
    HOLD_EAST_GATE("hold_east_gate", "東門阻擊", "在城門外正面承受敵軍衝擊。",
            CampaignMetric.MORALE, 5, 3, 1.15, 1.25, 1.35, 1.25, 2, 5.0, 3),
    MIRROR_PERIMETER("mirror_perimeter", "聚炁鏡外巡", "清查可能影響聚炁鏡場的妖物。",
            CampaignMetric.DEFENSE, 4, 0, 1.15, 1.10, 1.20, 1.10, 1, 9.0, 2),
    WORKSHOP_MATERIALS("workshop_materials", "工坊急需", "替煉化工坊奪回短缺的妖物材料。",
            CampaignMetric.SUPPLY, 4, 1, 1.10, 1.15, 1.10, 1.15, 2, 10.0, 2),
    DEEP_FIELD_SCOUT("deep_field_scout", "深野偵巡", "越過慣常巡線，確認敵軍集結方向。",
            CampaignMetric.INTELLIGENCE, 6, 1, 1.30, 1.25, 1.25, 1.25, 2, 16.0, 4),
    RELIEF_COLUMN("relief_column", "外圍解圍", "替受困的外圍巡防隊吸引主力。",
            CampaignMetric.MORALE, 6, 3, 1.25, 1.30, 1.35, 1.30, 3, 12.0, 4),
    TIMBER_REQUISITION("timber_requisition", "城牆木料", "繳交修復外牆所需的原木。",
            CampaignMetric.DEFENSE, 4, "OAK_LOG", "橡木原木", 16, 1),
    STONE_REQUISITION("stone_requisition", "工事石料", "繳交道路與防線所需的石材。",
            CampaignMetric.SUPPLY, 4, "COBBLESTONE", "鵝卵石", 24, 1),
    FIELD_RATIONS("field_rations", "前線糧秣", "替外圍巡防隊補充可保存糧秣。",
            CampaignMetric.MORALE, 4, "WHEAT", "小麥", 18, 2),
    MIRROR_COMPONENTS("mirror_components", "聚炁鏡構件", "取得工坊修補聚炁鏡座的金屬。",
            CampaignMetric.INTELLIGENCE, 5, "COPPER_INGOT", "銅錠", 8, 3),
    PRESERVED_RATIONS("preserved_rations", "耐放糧食", "補充遠線巡防需要的耐放糧食。",
            CampaignMetric.SUPPLY, 4, "DRIED_KELP", "海帶乾", 20, 2),
    NORTH_RIDGE_OBSERVATION("north_ridge_observation", "北側高地觀測", "前往北側高地啟動輕疾觀測標。",
            CampaignMetric.INTELLIGENCE, 4, 30, -58, 1),
    SOUTH_ROUTE_SURVEY("south_route_survey", "南路踏查", "確認南側補給路線是否仍可通行。",
            CampaignMetric.SUPPLY, 4, 42, 52, 2),
    EASTERN_LINE_MARKING("eastern_line_marking", "東境界線標定", "深入東境重新標定安全界線。",
            CampaignMetric.DEFENSE, 5, 68, 18, 3),
    LOST_SIGNAL_SEARCH("lost_signal_search", "失聯輕疾搜索", "追查荒原上中斷的輕疾訊號。",
            CampaignMetric.MORALE, 5, 56, -48, 3),
    FAR_EAST_ROUTE_SCAN("far_east_route_scan", "遠東路線勘察", "確認更遠的東境路線與撤退點。",
            CampaignMetric.INTELLIGENCE, 6, 82, 46, 4),
    EASTERN_MEDIC_ESCORT("eastern_medic_escort", "東境醫療護送", "護送醫療員前往東境巡防線。",
            CampaignMetric.DEFENSE, 5, MissionType.ESCORT, 48, 20, 2),
    WORKSHOP_CART_ESCORT("workshop_cart_escort", "工坊載具護送", "帶領工坊運輸員通過高道息區。",
            CampaignMetric.SUPPLY, 5, MissionType.ESCORT, 62, -12, 3),
    SIGNAL_TEAM_ESCORT("signal_team_escort", "輕疾小隊護送", "護送輕疾隊員前往新的觀測位置。",
            CampaignMetric.INTELLIGENCE, 4, MissionType.ESCORT, 55, -40, 3),
    OUTER_FAMILY_ESCORT("outer_family_escort", "外圍居民接應", "將外圍居民安全帶回可控區域。",
            CampaignMetric.MORALE, 5, MissionType.ESCORT, 36, 54, 2),
    WARD_REPAIR_ESCORT("ward_repair_escort", "防線修繕護送", "護送工匠與器材前往息壤防線缺口。",
            CampaignMetric.DEFENSE, 6, MissionType.ESCORT, 70, 34, 4),
    BREACHED_LINE_RESCUE("breached_line_rescue", "防線缺口救援", "找回在防線缺口倒地的巡防員。",
            CampaignMetric.DEFENSE, 5, MissionType.RESCUE, 58, 24, 3),
    LOST_PORTER_RESCUE("lost_porter_rescue", "失聯運輸員", "沿散落物資追蹤失聯運輸員。",
            CampaignMetric.SUPPLY, 4, MissionType.RESCUE, 44, -36, 2),
    WUJI_TRACE_RESCUE("wuji_trace_rescue", "無跡追蹤救援", "根據地面追蹤線索找回失聯偵察員。",
            CampaignMetric.INTELLIGENCE, 5, MissionType.RESCUE, 72, -28, 4),
    SURVIVOR_RETURN("survivor_return", "幸存者返城", "尋獲荒原幸存者並帶回新城。",
            CampaignMetric.MORALE, 5, MissionType.RESCUE, 50, 48, 3),
    MEDIC_TEAM_RECOVERY("medic_team_recovery", "醫療隊接應", "搜救遭追擊的前線醫療隊員。",
            CampaignMetric.MORALE, 6, MissionType.RESCUE, 64, 38, 4);

    private final String id;
    private final String display;
    private final String summary;
    private final CampaignMetric metric;
    private final int stateReward;
    private final int extraZaochi;
    private final double zaochiHealthMultiplier;
    private final double zaochiDamageMultiplier;
    private final double bossHealthMultiplier;
    private final double bossDamageMultiplier;
    private final int bonusRemains;
    private final double spawnRadius;
    private final int risk;
    private final MissionType missionType;
    private final String objectiveMaterial;
    private final String objectiveDisplay;
    private final int objectiveAmount;
    private final int targetOffsetX;
    private final int targetOffsetZ;

    MissionContract(String id, String display, String summary, CampaignMetric metric, int stateReward,
                   int extraZaochi, double zaochiHealthMultiplier, double zaochiDamageMultiplier,
                   double bossHealthMultiplier, double bossDamageMultiplier, int bonusRemains,
                   double spawnRadius, int risk) {
        this.id = id;
        this.display = display;
        this.summary = summary;
        this.metric = metric;
        this.stateReward = stateReward;
        this.extraZaochi = extraZaochi;
        this.zaochiHealthMultiplier = zaochiHealthMultiplier;
        this.zaochiDamageMultiplier = zaochiDamageMultiplier;
        this.bossHealthMultiplier = bossHealthMultiplier;
        this.bossDamageMultiplier = bossDamageMultiplier;
        this.bonusRemains = bonusRemains;
        this.spawnRadius = spawnRadius;
        this.risk = risk;
        this.missionType = MissionType.PATROL;
        this.objectiveMaterial = "";
        this.objectiveDisplay = "";
        this.objectiveAmount = 0;
        this.targetOffsetX = 0;
        this.targetOffsetZ = 0;
    }

    MissionContract(String id, String display, String summary, CampaignMetric metric, int stateReward,
                   String objectiveMaterial, String objectiveDisplay, int objectiveAmount, int risk) {
        this(id, display, summary, metric, stateReward, MissionType.GATHER,
                objectiveMaterial, objectiveDisplay, objectiveAmount, 0, 0, risk);
    }

    MissionContract(String id, String display, String summary, CampaignMetric metric, int stateReward,
                   int targetOffsetX, int targetOffsetZ, int risk) {
        this(id, display, summary, metric, stateReward, MissionType.SCOUT,
                "", "", 1, targetOffsetX, targetOffsetZ, risk);
    }

    MissionContract(String id, String display, String summary, CampaignMetric metric, int stateReward,
                    MissionType missionType, int targetOffsetX, int targetOffsetZ, int risk) {
        this(id, display, summary, metric, stateReward, missionType,
                "", "", 1, targetOffsetX, targetOffsetZ, risk);
    }

    MissionContract(String id, String display, String summary, CampaignMetric metric, int stateReward,
                   MissionType missionType, String objectiveMaterial, String objectiveDisplay,
                   int objectiveAmount, int targetOffsetX, int targetOffsetZ, int risk) {
        this.id = id;
        this.display = display;
        this.summary = summary;
        this.metric = metric;
        this.stateReward = stateReward;
        this.extraZaochi = 0;
        this.zaochiHealthMultiplier = 1.0;
        this.zaochiDamageMultiplier = 1.0;
        this.bossHealthMultiplier = 1.0;
        this.bossDamageMultiplier = 1.0;
        this.bonusRemains = 0;
        this.spawnRadius = 0.0;
        this.risk = risk;
        this.missionType = missionType;
        this.objectiveMaterial = objectiveMaterial;
        this.objectiveDisplay = objectiveDisplay;
        this.objectiveAmount = objectiveAmount;
        this.targetOffsetX = targetOffsetX;
        this.targetOffsetZ = targetOffsetZ;
    }

    public String id() { return id; }
    public String display() { return display; }
    public String summary() { return summary; }
    public CampaignMetric metric() { return metric; }
    public int stateReward() { return stateReward; }
    public int extraZaochi() { return extraZaochi; }
    public double zaochiHealthMultiplier() { return zaochiHealthMultiplier; }
    public double zaochiDamageMultiplier() { return zaochiDamageMultiplier; }
    public double bossHealthMultiplier() { return bossHealthMultiplier; }
    public double bossDamageMultiplier() { return bossDamageMultiplier; }
    public int bonusRemains() { return bonusRemains; }
    public double spawnRadius() { return spawnRadius; }
    public int risk() { return risk; }
    public MissionType missionType() { return missionType; }
    public String objectiveMaterial() { return objectiveMaterial; }
    public String objectiveDisplay() { return objectiveDisplay; }
    public int objectiveAmount() { return objectiveAmount; }
    public int targetOffsetX() { return targetOffsetX; }
    public int targetOffsetZ() { return targetOffsetZ; }

    public static MissionContract parse(String id) {
        if (id == null) return null;
        return Arrays.stream(values()).filter(contract -> contract.id.equalsIgnoreCase(id)).findFirst().orElse(null);
    }
}
