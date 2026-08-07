package tw.zack.evilisland.model;

import java.util.UUID;

public record WeaponMasterySnapshot(UUID playerId, WeaponType weapon, int mastery, TechniquePath technique,
                                    long updatedAt) {
    public WeaponMasterySnapshot {
        mastery = Math.max(0, mastery);
        technique = technique == null ? TechniquePath.UNTRAINED : technique;
    }
}
