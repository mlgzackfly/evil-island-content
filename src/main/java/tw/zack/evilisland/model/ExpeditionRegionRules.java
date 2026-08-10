package tw.zack.evilisland.model;

import org.bukkit.Material;

import java.util.Arrays;

public final class ExpeditionRegionRules {
    private ExpeditionRegionRules() { }

    public static ExpeditionOperation operation(ExplorationSite site, long seed) {
        ExpeditionOperation[] pool = Arrays.stream(ExpeditionOperation.values())
                .filter(operation -> operation.site() == site).toArray(ExpeditionOperation[]::new);
        return pool[Math.floorMod(seed, pool.length)];
    }

    public static String boardTitle(ExplorationSite site) {
        return switch (site) {
            case EASTERN_ROUTE -> "東境深入遠征";
            case UDING_WALL -> "高原東壁勘路";
            case RONGXU_APPROACH -> "絨須邊界行動";
            case WESTERN_TRACE -> "西方遺跡搜索";
            case DRAGON_COAST -> "龍宮海岸觀測";
        };
    }

    public static String routeDisplay(ExplorationSite site, ExpeditionRoute route) {
        String[][] names = {{"舊驛道", "北側稜線", "乾涸河道"}, {"石階線", "迎風脊", "東側崖徑"},
                {"外緣標界", "林間緩坡", "舊商旅線"}, {"碎石谷", "斷柱帶", "低地廢墟"},
                {"外海浮標", "礁脊線", "退潮沙洲"}};
        return names[site.ordinal()][route.ordinal()];
    }

    public static String routeDescription(ExplorationSite site, ExpeditionRoute route) {
        if (site == ExplorationSite.EASTERN_ROUTE) return route.description();
        return switch (site) {
            case UDING_WALL -> route == ExpeditionRoute.RIDGE ? "高差與風勢最強，觀測完整但巡獵壓力較高。"
                    : route == ExpeditionRoute.RIVERBED ? "崖徑狹窄，接敵較少但必須完成三處校正。"
                    : "石階較穩定，路程與巡獵風險均衡。";
            case RONGXU_APPROACH -> route == ExpeditionRoute.RIDGE ? "視野清楚但靠近領地標界，錯誤行動容易失禮。"
                    : route == ExpeditionRoute.RIVERBED ? "商旅痕跡較多，需要核對完整消息。"
                    : "沿外緣標記前進，以判讀與引導取代清剿。";
            case WESTERN_TRACE -> route == ExpeditionRoute.RIDGE ? "斷柱密集，樣本價值高但犬戎巡獵較強。"
                    : route == ExpeditionRoute.RIVERBED ? "低地樣本分散，必須在有限攜帶量中取捨。"
                    : "碎石谷線索清楚，風險與回收價值均衡。";
            case DRAGON_COAST -> route == ExpeditionRoute.RIDGE ? "礁脊暴露於掠空者視野，同步時限較寬。"
                    : route == ExpeditionRoute.RIVERBED ? "退潮後才能通行，情報較多但撤離時間有限。"
                    : "浮標路線直接，空中威脅與潮路壓力均衡。";
            default -> route.description();
        };
    }

    public static Material routeIcon(ExplorationSite site, ExpeditionRoute route) {
        if (site == ExplorationSite.EASTERN_ROUTE) return route.icon();
        Material[][] icons = {{Material.RAIL, Material.GOAT_HORN, Material.MUD},
                {Material.STONE_STAIRS, Material.LIGHTNING_ROD, Material.POINTED_DRIPSTONE},
                {Material.WHITE_BANNER, Material.OAK_SAPLING, Material.LEAD},
                {Material.GRAVEL, Material.CRACKED_STONE_BRICKS, Material.BRUSH},
                {Material.HEART_OF_THE_SEA, Material.PRISMARINE, Material.SAND}};
        return icons[site.ordinal()][route.ordinal()];
    }

    public static int requiredClues(ExplorationSite site, ExpeditionOperation operation, ExpeditionRoute route) {
        if (site == ExplorationSite.UDING_WALL || site == ExplorationSite.DRAGON_COAST) return 3;
        if (site == ExplorationSite.WESTERN_TRACE) return 2;
        return ExpeditionRules.requiredClues(operation, route);
    }

    public static boolean combatRequired(ExplorationSite site) {
        return site != ExplorationSite.RONGXU_APPROACH;
    }

    public static boolean timedExtraction(ExplorationSite site, ExpeditionOperation operation) {
        return site == ExplorationSite.DRAGON_COAST || operation == ExpeditionOperation.CASUALTY_EVACUATION;
    }

    public static int enemyCount(ExplorationSite site, ExpeditionOperation operation, ExpeditionRoute route,
                                 int participants, int alert) {
        if (!combatRequired(site)) return 0;
        int count = ExpeditionRules.enemyCount(operation, route, participants, alert);
        if (site == ExplorationSite.UDING_WALL) count++;
        if (site == ExplorationSite.DRAGON_COAST && route == ExpeditionRoute.RIDGE) count++;
        return count;
    }

    public static SpeciesType enemy(ExplorationSite site, int index) {
        return switch (site) {
            case EASTERN_ROUTE -> SpeciesType.ZAOCHI;
            case UDING_WALL, WESTERN_TRACE -> index >= 4 ? SpeciesType.QUANRONG_ALPHA : SpeciesType.QUANRONG_HUNTER;
            case RONGXU_APPROACH -> SpeciesType.MAO_ENVOY;
            case DRAGON_COAST -> index >= 3 ? SpeciesType.YUJIANG_WINDBREAKER : SpeciesType.YUJIANG_RAIDER;
        };
    }

    public static Material clueMaterial(ExpeditionOperation operation, int index) {
        if (operation.site() == ExplorationSite.EASTERN_ROUTE) return switch (operation) {
            case LOST_CONVOY -> new Material[]{Material.MINECART, Material.BREAD, Material.ARROW}[index];
            case BLOCKADE_INFILTRATION -> new Material[]{Material.STRING, Material.OAK_SIGN, Material.FLINT}[index];
            case SUPPLY_NODE_SABOTAGE -> new Material[]{Material.REDSTONE, Material.CHARCOAL, Material.PAPER}[index];
            case CASUALTY_EVACUATION -> new Material[]{Material.WHITE_WOOL, Material.GLASS_BOTTLE,
                    Material.LEATHER_BOOTS}[index];
            default -> operation.icon();
        };
        Material[][] materials = {{Material.LIGHTNING_ROD, Material.FEATHER, Material.SPYGLASS},
                {Material.WHITE_WOOL, Material.OAK_SIGN, Material.WRITABLE_BOOK},
                {Material.BRUSH, Material.BRICK, Material.MAP},
                {Material.PRISMARINE_CRYSTALS, Material.KELP, Material.PHANTOM_MEMBRANE}};
        return materials[operation.site().ordinal() - 1][index];
    }

    public static String clueName(ExpeditionOperation operation, int index) {
        if (operation.site() == ExplorationSite.EASTERN_ROUTE) {
            String[][] names = {{"破裂的車輪", "散落的乾糧", "折斷的箭"},
                    {"刻意拉直的絆線", "反向路標", "新鮮火石屑"},
                    {"紅石粉痕", "未熄焦炭", "節點輪值紙"},
                    {"染血繃帶", "空藥瓶", "拖行足跡"}};
            return names[operation.ordinal()][index];
        }
        String[][] names = {{"崖壁校正釘", "風向羽記", "遠端觀測痕"},
                {"毛族布記", "邊界方向牌", "使者留言"},
                {"表層刷痕", "斷裂陶片", "遺跡方位圖"},
                {"潮位晶屑", "退潮水草", "掠空殘膜"}};
        return names[operation.site().ordinal() - 1][index];
    }

    public static Material objectiveMaterial(ExpeditionOperation operation, int index) {
        if (operation.site() == ExplorationSite.EASTERN_ROUTE) return switch (operation) {
            case LOST_CONVOY -> index == 0 ? Material.BARREL : Material.TOTEM_OF_UNDYING;
            case BLOCKADE_INFILTRATION -> index == 0 ? Material.BELL : Material.IRON_TRAPDOOR;
            case SUPPLY_NODE_SABOTAGE -> index == 0 ? Material.TNT : Material.REDSTONE_LAMP;
            case CASUALTY_EVACUATION -> index == 0 ? Material.GOLDEN_APPLE : Material.SPLASH_POTION;
            default -> operation.icon();
        };
        return switch (operation.site()) {
            case UDING_WALL -> index == 0 ? Material.LIGHTNING_ROD : Material.SPYGLASS;
            case RONGXU_APPROACH -> index == 0 ? Material.WHITE_BANNER : Material.WRITABLE_BOOK;
            case WESTERN_TRACE -> index == 0 ? Material.DECORATED_POT : Material.BUNDLE;
            case DRAGON_COAST -> index == 0 ? Material.SEA_LANTERN : Material.HEART_OF_THE_SEA;
            default -> operation.icon();
        };
    }

    public static String objectiveName(ExpeditionOperation operation, int index) {
        if (operation.site() == ExplorationSite.EASTERN_ROUTE) {
            String[][] names = {{"封存補給箱", "受困的車隊斥候"}, {"封鎖線警鈴", "補給通道閘門"},
                    {"主補給節點", "傳訊節點"}, {"北側傷員", "南側傷員"}};
            return names[operation.ordinal()][index];
        }
        String[][] names = {{"東壁傳訊點", "高處觀測點"}, {"外緣見證標", "使者會合記號"},
                {"遺跡主樣本", "安全攜行箱"}, {"外海潮位標", "海岸警戒標"}};
        return names[operation.site().ordinal() - 1][index];
    }
}
