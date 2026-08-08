package tw.zack.evilisland.model;

import java.util.UUID;

public record AcceptanceBlockSnapshot(UUID runId, String world, int x, int y, int z,
                                      String originalData, String placedData) {
}
