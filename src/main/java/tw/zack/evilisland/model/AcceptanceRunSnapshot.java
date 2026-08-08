package tw.zack.evilisland.model;

import java.util.UUID;

public record AcceptanceRunSnapshot(UUID id, AcceptanceState state, String world, int centerX, int centerY,
                                    int centerZ, int checksPassed, int checksTotal, String summary,
                                    long startedAt, long updatedAt) {
}
