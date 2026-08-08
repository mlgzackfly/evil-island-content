package tw.zack.evilisland.model;

import java.util.UUID;

public record EssenceSourceSnapshot(
        UUID playerId,
        String source,
        int amount,
        int purityPoints,
        long updatedAt
) {
    public EssenceSourceSnapshot {
        amount = Math.max(0, amount);
        purityPoints = Math.max(0, purityPoints);
    }

    public double averagePurity() {
        return amount == 0 ? 0.0 : (double) purityPoints / amount;
    }
}
