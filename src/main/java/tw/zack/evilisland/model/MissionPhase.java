package tw.zack.evilisland.model;

public enum MissionPhase {
    PATROL("patrol"),
    GATHER("gather"),
    SCOUT("scout"),
    BOSS_READY("boss_ready"),
    BOSS("boss"),
    COMPLETE_PENDING("complete_pending");

    private final String id;

    MissionPhase(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static MissionPhase parse(String id) {
        if (id == null) {
            return null;
        }
        for (MissionPhase phase : values()) {
            if (phase.id.equalsIgnoreCase(id)) {
                return phase;
            }
        }
        return null;
    }
}
