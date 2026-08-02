package tw.zack.evilisland.model;

import java.util.UUID;

public record PlayerProfileSnapshot(
        UUID uuid,
        String name,
        QiTendency tendency,
        FormulaPath formulaPath,
        int qi,
        int essence,
        int transformations,
        ObjectiveStage objective,
        int zaochiKills,
        long updatedAt
) {
    public static PlayerProfileSnapshot blank(UUID uuid, String name) {
        return new PlayerProfileSnapshot(uuid, name, null, null, 0, 0, 0,
                ObjectiveStage.UNENLISTED, 0, System.currentTimeMillis());
    }

    public boolean measured() {
        return tendency != null;
    }

    public boolean formulaLocked() {
        return formulaPath != null;
    }
}
