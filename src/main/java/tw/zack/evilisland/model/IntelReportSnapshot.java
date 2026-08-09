package tw.zack.evilisland.model;

import java.util.UUID;

public record IntelReportSnapshot(UUID eventId, ResidentRole resident, UUID reporter, long collectedAt) {
    public IntelReportSnapshot {
        if (eventId == null || resident == null || reporter == null || collectedAt < 0) {
            throw new IllegalArgumentException("Invalid intel report");
        }
    }
}
