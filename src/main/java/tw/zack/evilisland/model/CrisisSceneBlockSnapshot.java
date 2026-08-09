package tw.zack.evilisland.model;

import java.util.UUID;

public record CrisisSceneBlockSnapshot(
        UUID eventId,
        String world,
        int x,
        int y,
        int z,
        String originalData,
        String activeData,
        String resolvedData,
        String expiredData,
        String placedData
) {
    public CrisisSceneBlockSnapshot {
        if (eventId == null || world == null || originalData == null || activeData == null
                || resolvedData == null || expiredData == null || placedData == null) {
            throw new IllegalArgumentException("Crisis scene block fields cannot be null");
        }
    }

    public String dataFor(CrisisSceneState state) {
        return switch (state) {
            case ACTIVE -> activeData;
            case RESOLVED -> resolvedData;
            case EXPIRED -> expiredData;
            case CONFLICT -> placedData;
        };
    }

    public CrisisSceneBlockSnapshot withPlacedData(String data) {
        return new CrisisSceneBlockSnapshot(eventId, world, x, y, z, originalData,
                activeData, resolvedData, expiredData, data);
    }
}
