package tw.zack.evilisland.model;

import java.util.Arrays;
import java.util.HashSet;

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
        System.out.println("WeaponSpeciesRulesTest passed");
    }
}
