package tw.zack.evilisland.model;

public enum JourneyMilestone {
    QI_AWAKENED(1 << 0),
    WEAPON_CLAIMED(1 << 1),
    PATROL_COMPLETED(1 << 2),
    CAMP_REACHED(1 << 3),
    EXPEDITION_STARTED(1 << 4),
    WITHDRAWAL_REVIEWED(1 << 5),
    EXPEDITION_COMPLETED(1 << 6);

    private final int mask;

    JourneyMilestone(int mask) {
        this.mask = mask;
    }

    public int mask() {
        return mask;
    }

    public boolean presentIn(int value) {
        return (value & mask) != 0;
    }
}
