package tw.zack.evilisland.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class LivingEventRules {
    private LivingEventRules() {
    }

    public static LivingEventType select(int cycle, int week, int day, List<LivingEventType> recent) {
        List<LivingEventType> available = java.util.Arrays.stream(LivingEventType.values())
                .filter(type -> type.availableInWeek(week)).toList();
        Set<LivingEventType> excluded = recent == null || recent.isEmpty()
                ? EnumSet.noneOf(LivingEventType.class) : EnumSet.copyOf(recent);
        List<LivingEventType> candidates = available.stream().filter(type -> !excluded.contains(type)).toList();
        if (candidates.isEmpty()) candidates = available;
        int seed = Math.max(1, cycle) * 31 + Math.max(1, week) * 17 + Math.max(1, day) * 13;
        return candidates.get(Math.floorMod(seed, candidates.size()));
    }

    public static List<MissionContract> missionBoard(List<MissionContract> base, LivingEventSnapshot active) {
        if (active == null || active.state() != LivingEventState.ACTIVE || base == null || base.isEmpty()) {
            return base == null ? List.of() : List.copyOf(base);
        }
        MissionContract target = active.type().contract();
        if (base.contains(target)) return List.copyOf(base);
        List<MissionContract> board = new ArrayList<>(base);
        int replacement = -1;
        for (int index = 0; index < board.size(); index++) {
            if (board.get(index).missionType() == target.missionType()) {
                replacement = index;
                break;
            }
        }
        if (replacement < 0) replacement = board.size() - 1;
        board.set(replacement, target);
        return List.copyOf(board);
    }

    public static int regionPressure(List<LivingEventSnapshot> history, ExplorationSite region, int cycle) {
        int pressure = 0;
        if (history == null) return pressure;
        List<LivingEventSnapshot> ordered = history.stream()
                .filter(event -> event.cycle() == cycle && event.type().region() == region)
                .sorted(Comparator.comparingLong(LivingEventSnapshot::createdAt)).toList();
        for (LivingEventSnapshot event : ordered) {
            if (event.state() == LivingEventState.EXPIRED) pressure = Math.min(3, pressure + 1);
            if (event.state() == LivingEventState.RESOLVED) pressure = Math.max(0, pressure - 1);
        }
        return pressure;
    }

    public static int missionEnemyModifier(LivingEventSnapshot active, MissionContract contract, int pressure) {
        if (active == null || active.state() != LivingEventState.ACTIVE || contract == null
                || active.type().contract() != contract) return 0;
        return 1 + Math.min(1, Math.max(0, pressure));
    }

    public static int arcProgress(List<LivingEventSnapshot> history, LivingEventArc arc, int cycle) {
        if (history == null) return 0;
        return (int) history.stream().filter(event -> event.cycle() == cycle && event.type().arc() == arc
                && event.state() == LivingEventState.RESOLVED).count();
    }
}
