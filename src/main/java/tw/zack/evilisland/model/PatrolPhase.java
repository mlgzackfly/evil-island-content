package tw.zack.evilisland.model;

public enum PatrolPhase {
    PATROL("patrol"),
    BOSS_READY("boss_ready"),
    BOSS("boss"),
    COMPLETE_PENDING("complete_pending");

    private final String id;

    PatrolPhase(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static PatrolPhase parse(String id) {
        if (id == null) {
            return null;
        }
        for (PatrolPhase phase : values()) {
            if (phase.id.equalsIgnoreCase(id)) {
                return phase;
            }
        }
        return null;
    }
}
