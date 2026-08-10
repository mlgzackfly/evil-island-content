package tw.zack.evilisland.model;

public final class ExpeditionDirector {
    private ExpeditionDirector() { }

    public static ExpeditionRouteEvent event(long seed, int index) {
        ExpeditionRouteEvent[] events = ExpeditionRouteEvent.values();
        int first = Math.floorMod(seed ^ (seed >>> 17), events.length);
        int selected = Math.floorMod(first + index * 3 + 1, events.length);
        if (index > 0 && selected == first) selected = (selected + 1) % events.length;
        return events[index == 0 ? first : selected];
    }

    public static int kitCapacity(int participants) {
        return participants <= 1 ? 3 : 2;
    }

    public static boolean validLoadout(int kitMask, int participants) {
        int count = Integer.bitCount(kitMask);
        return count >= 2 && count <= kitCapacity(participants)
                && (kitMask & ~0b1111) == 0;
    }

    public static ExpeditionKit preferredKit(ExpeditionOperation operation) {
        return switch (operation) {
            case LOST_CONVOY -> ExpeditionKit.PROVISIONS;
            case BLOCKADE_INFILTRATION -> ExpeditionKit.SCOUTING;
            case SUPPLY_NODE_SABOTAGE -> ExpeditionKit.DEMOLITION;
            case CASUALTY_EVACUATION -> ExpeditionKit.MEDICAL;
        };
    }

    public static ExpeditionEventResolution resolve(ExpeditionRouteEvent event, boolean useKit) {
        if (useKit) {
            return switch (event) {
                case WOUNDED_SCOUT -> new ExpeditionEventResolution(0, 2, false, "斥候傷勢已穩定，提供了可靠情報。" );
                case ABANDONED_CACHE -> new ExpeditionEventResolution(0, 2, false, "可用補給已安全重新封裝。" );
                case SAFE_REST -> new ExpeditionEventResolution(0, 1, true, "隊伍完成隱蔽休整。" );
                default -> new ExpeditionEventResolution(0, 1, false, "隊伍以準備好的器材安全通過。" );
            };
        }
        return switch (event) {
            case COLLAPSED_PATH -> new ExpeditionEventResolution(1, 0, false, "隊伍繞行暴露地帶，警戒提高。" );
            case ENEMY_PATROL, FALSE_SIGNAL -> new ExpeditionEventResolution(1, -1, false,
                    "臨場處置留下痕跡，敵軍提高警戒。" );
            case WOUNDED_SCOUT -> new ExpeditionEventResolution(0, -1, false, "缺乏醫療器材，只能記下位置繼續前進。" );
            case ABANDONED_CACHE -> new ExpeditionEventResolution(1, 0, false, "補給無法安全搬運，翻動痕跡可能引來敵軍。" );
            case SAFE_REST -> new ExpeditionEventResolution(0, 0, false, "隊伍放棄休整，維持原有狀態前進。" );
        };
    }
}
