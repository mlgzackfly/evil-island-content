package tw.zack.evilisland.model;

import dev.zack.rpgengine.ProgressionStep;

public enum ObjectiveStage implements ProgressionStep {
    UNENLISTED(0),
    HUNT_ZAOCHI(1),
    REFINE_REMAINS(2),
    FIRST_TRANSFORMATION(3),
    DEFEAT_XINGTIAN(4),
    COMPLETE(5),
    REPORT_PATROL(6);

    private final int id;

    ObjectiveStage(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    @Override
    public int order() {
        return id;
    }

    public static ObjectiveStage fromId(int id) {
        for (ObjectiveStage stage : values()) {
            if (stage.id == id) {
                return stage;
            }
        }
        return UNENLISTED;
    }
}
