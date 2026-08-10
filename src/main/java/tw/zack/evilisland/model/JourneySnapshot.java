package tw.zack.evilisland.model;

import java.util.UUID;

public record JourneySnapshot(UUID playerId, int milestoneMask, long startedAt, long updatedAt) {
    public static JourneySnapshot initial(UUID playerId, long now) {
        return new JourneySnapshot(playerId, 0, now, now);
    }

    public boolean has(JourneyMilestone milestone) {
        return milestone.presentIn(milestoneMask);
    }

    public JourneySnapshot record(JourneyMilestone milestone, long now) {
        return new JourneySnapshot(playerId, milestoneMask | milestone.mask(), startedAt, now);
    }

    public JourneyStep step() {
        if (!has(JourneyMilestone.QI_AWAKENED)) return JourneyStep.AWAKEN_QI;
        if (!has(JourneyMilestone.WEAPON_CLAIMED)) return JourneyStep.CLAIM_WEAPON;
        if (!has(JourneyMilestone.PATROL_COMPLETED)) return JourneyStep.COMPLETE_PATROL;
        if (!has(JourneyMilestone.CAMP_REACHED)) return JourneyStep.REACH_CAMP;
        if (!has(JourneyMilestone.EXPEDITION_STARTED)) return JourneyStep.START_EXPEDITION;
        if (!has(JourneyMilestone.WITHDRAWAL_REVIEWED)) return JourneyStep.REVIEW_WITHDRAWAL;
        if (!has(JourneyMilestone.EXPEDITION_COMPLETED)) return JourneyStep.COMPLETE_EXPEDITION;
        return JourneyStep.MAINLINE;
    }
}
