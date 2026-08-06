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

    public static CampaignSnapshot complete(CampaignSnapshot state, MissionContract contract, long now) {
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

    public static List<MissionContract> board(CampaignSnapshot state) {
        CampaignMetric weakest = List.of(CampaignMetric.values()).stream()
                .min(Comparator.comparingInt(state::metric).thenComparing(Enum::ordinal))
                .orElse(CampaignMetric.DEFENSE);
        List<MissionContract> options = new ArrayList<>();
        int seed = state.absoluteDay() * 5 + state.cycle();
        for (MissionType type : MissionType.values()) {
            addDistinct(options, findTypeContract(type, weakest, seed + type.ordinal() * 3));
        }
        return List.copyOf(options);
    }

    public static List<MissionContract> patrolBoard(CampaignSnapshot state) {
        MissionContract[] patrols = java.util.Arrays.stream(MissionContract.values())
                .filter(contract -> contract.missionType() == MissionType.PATROL)
                .toArray(MissionContract[]::new);
        int seed = state.absoluteDay() * 5 + state.cycle();
        return List.of(patrols[Math.floorMod(seed, patrols.length)],
                patrols[Math.floorMod(seed + 3, patrols.length)],
                patrols[Math.floorMod(seed + 7, patrols.length)]);
    }

    private static MissionContract findTypeContract(MissionType type, CampaignMetric metric, int seed) {
        MissionContract[] matches = java.util.Arrays.stream(MissionContract.values())
                .filter(contract -> contract.missionType() == type && contract.metric() == metric)
                .toArray(MissionContract[]::new);
        if (matches.length == 0) {
            matches = java.util.Arrays.stream(MissionContract.values())
                    .filter(contract -> contract.missionType() == type).toArray(MissionContract[]::new);
        }
        return matches[Math.floorMod(seed, matches.length)];
    }

    private static void addDistinct(List<MissionContract> options, MissionContract contract) {
        if (!options.contains(contract)) options.add(contract);
    }
}
