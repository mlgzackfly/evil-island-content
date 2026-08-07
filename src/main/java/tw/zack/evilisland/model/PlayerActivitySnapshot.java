package tw.zack.evilisland.model;

import java.util.UUID;

public record PlayerActivitySnapshot(UUID playerId, long lastSeen, int lastCatchupCycle) {
}
