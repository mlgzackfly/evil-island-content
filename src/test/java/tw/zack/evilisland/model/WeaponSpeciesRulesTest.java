package tw.zack.evilisland.model;

import dev.zack.rpgengine.ContentRegistry;
import dev.zack.rpgengine.LinearProgression;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public final class WeaponSpeciesRulesTest {
    private WeaponSpeciesRulesTest() {
    }

    public static void main(String[] args) {
        if (WeaponType.values().length != 6) {
            throw new AssertionError("Expected six initial weapon types");
        }
        if (SpeciesType.values().length != 2) {
            throw new AssertionError("Expected the first two implemented species");
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
        System.out.println("WeaponSpeciesRulesTest passed");
    }
}
