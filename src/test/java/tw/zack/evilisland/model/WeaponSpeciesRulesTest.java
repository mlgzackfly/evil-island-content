package tw.zack.evilisland.model;

import dev.zack.rpgengine.ContentRegistry;
import dev.zack.rpgengine.LinearProgression;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public final class WeaponSpeciesRulesTest {
    private WeaponSpeciesRulesTest() {
    }

    public static void main(String[] args) {
        if (WeaponType.values().length != 6) {
            throw new AssertionError("Expected six initial weapon types");
        }
        if (SpeciesType.values().length != 8) {
            throw new AssertionError("Expected eight implemented species roles");
        }
        if (Arrays.stream(SpeciesType.values()).filter(SpeciesType::hostile).count() != 6
                || Arrays.stream(SpeciesType.values()).filter(SpeciesType::elite).count() != 3) {
            throw new AssertionError("Species ecology roles are incomplete");
        }
        if (new HashSet<>(Arrays.stream(WeaponType.values()).map(WeaponType::id).toList()).size()
                != WeaponType.values().length) {
            throw new AssertionError("Weapon ids must be unique");
        }
        if (new HashSet<>(Arrays.stream(SpeciesType.values()).map(SpeciesType::id).toList()).size()
                != SpeciesType.values().length) {
            throw new AssertionError("Species ids must be unique");
        }
        ContentRegistry<WeaponType> weapons = new ContentRegistry<>();
        Arrays.stream(WeaponType.values()).forEach(weapons::register);
        ContentRegistry<SpeciesType> species = new ContentRegistry<>();
        Arrays.stream(SpeciesType.values()).forEach(species::register);
        if (weapons.size() != WeaponType.values().length || species.size() != SpeciesType.values().length) {
            throw new AssertionError("Engine registries must contain every private content definition");
        }
        LinearProgression<ObjectiveStage> progression = new LinearProgression<>(List.of(ObjectiveStage.values()));
        if (progression.next(ObjectiveStage.UNENLISTED) != ObjectiveStage.HUNT_ZAOCHI) {
            throw new AssertionError("Engine progression order is incorrect");
        }
        int lane = SpeciesTactics.formationLane(new UUID(0L, 7L));
        if (lane < -1 || lane > 1) {
            throw new AssertionError("Formation lane must be left, center, or right");
        }
        if (!SpeciesTactics.isEnraged(44.0, 100.0, 0.45)
                || SpeciesTactics.isEnraged(46.0, 100.0, 0.45)) {
            throw new AssertionError("Enrage threshold is incorrect");
        }
        if (SpeciesTactics.scaledCooldown(5000L, true, 0.7) != 3500L) {
            throw new AssertionError("Enraged cooldown scaling is incorrect");
        }
        System.out.println("WeaponSpeciesRulesTest passed");
    }
}
