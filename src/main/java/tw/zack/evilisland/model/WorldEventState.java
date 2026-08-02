package tw.zack.evilisland.model;

import java.util.EnumSet;
import java.util.Set;

public enum WorldEventState {
    PREPARING,
    ACTIVE,
    RETREAT,
    SUCCEEDED,
    FAILED,
    COOLDOWN;

    public boolean canTransitionTo(WorldEventState next) {
        if (next == null || next == this) {
            return false;
        }
        Set<WorldEventState> allowed = switch (this) {
            case PREPARING -> EnumSet.of(ACTIVE, FAILED);
            case ACTIVE -> EnumSet.of(RETREAT, SUCCEEDED, FAILED);
            case RETREAT -> EnumSet.of(ACTIVE, FAILED);
            case SUCCEEDED, FAILED -> EnumSet.of(COOLDOWN);
            case COOLDOWN -> EnumSet.of(PREPARING);
        };
        return allowed.contains(next);
    }

    public boolean running() {
        return this == PREPARING || this == ACTIVE || this == RETREAT;
    }
}
