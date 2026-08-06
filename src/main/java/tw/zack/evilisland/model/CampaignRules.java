package tw.zack.evilisland.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CampaignRules {
    private CampaignRules() {
    }

    public static CampaignSnapshot advanceTo(CampaignSnapshot state, long targetEpochDay, long now) {
        CampaignSnapshot current = state;
        long elapsed = Math.max(0L, Math.min(365L, targetEpochDay - state.epochDay()));
        for (long count = 0; count < elapsed; count++) {
            CampaignWeek phase = CampaignWeek.fromWeek(current.week());
            int defense = current.defense() - phase.defenseDrain();
            int supply = current.supply() - phase.supplyDrain();
            int intelligence = current.intelligence();
            int morale = current.morale() - phase.moraleDrain();
            if (!current.completedToday()) {
                defense -= 2;
                intelligence -= 1;
                morale -= 2;
            }

            int cycle = current.cycle();
            int week = current.week();
            int day = current.day() + 1;
            if (day > 7) {
                day = 1;
                week++;
                if (week > 4) {
                    week = 1;
                    cycle++;
                }
            }
            current = new CampaignSnapshot(cycle, week, day, defense, supply, intelligence, morale,
                    current.epochDay() + 1, false, "", now);
        }
        return current;
    }

    public static CampaignSnapshot complete(CampaignSnapshot state, PatrolContract contract, long now) {
        if (state.completedToday()) {
            return state;
        }
        int defense = state.defense();
        int supply = state.supply();
        int intelligence = state.intelligence();
        int morale = state.morale() + 1;
        switch (contract.metric()) {
            case DEFENSE -> defense += contract.stateReward();
            case SUPPLY -> supply += contract.stateReward();
            case INTELLIGENCE -> intelligence += contract.stateReward();
            case MORALE -> morale += contract.stateReward();
        }
        return new CampaignSnapshot(state.cycle(), state.week(), state.day(), defense, supply,
                intelligence, morale, state.epochDay(), true, contract.id(), now);
    }

    public static List<PatrolContract> board(CampaignSnapshot state) {
        CampaignMetric weakest = List.of(CampaignMetric.values()).stream()
                .min(Comparator.comparingInt(state::metric).thenComparing(Enum::ordinal))
                .orElse(CampaignMetric.DEFENSE);
        PatrolContract[] contracts = PatrolContract.values();
        List<PatrolContract> options = new ArrayList<>();
        int seed = Math.floorMod(state.absoluteDay() * 5 + state.cycle(), contracts.length);
        addDistinct(options, findMetricContract(weakest, seed));
        addDistinct(options, contracts[Math.floorMod(seed + 3, contracts.length)]);
        addDistinct(options, contracts[Math.floorMod(seed + 7, contracts.length)]);
        for (PatrolContract contract : contracts) {
            addDistinct(options, contract);
            if (options.size() == 3) break;
        }
        return List.copyOf(options.subList(0, 3));
    }

    private static PatrolContract findMetricContract(CampaignMetric metric, int seed) {
        PatrolContract[] matches = java.util.Arrays.stream(PatrolContract.values())
                .filter(contract -> contract.metric() == metric).toArray(PatrolContract[]::new);
        return matches[Math.floorMod(seed, matches.length)];
    }

    private static void addDistinct(List<PatrolContract> options, PatrolContract contract) {
        if (!options.contains(contract)) options.add(contract);
    }
}
