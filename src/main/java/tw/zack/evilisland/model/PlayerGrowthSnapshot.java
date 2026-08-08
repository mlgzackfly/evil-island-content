package tw.zack.evilisland.model;

import java.util.UUID;

public record PlayerGrowthSnapshot(UUID playerId, int rejection, long updatedAt) {
    public PlayerGrowthSnapshot {
        rejection = Math.max(0, rejection);
    }
}
