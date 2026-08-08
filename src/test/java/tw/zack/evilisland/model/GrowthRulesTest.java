package tw.zack.evilisland.model;

import java.util.Arrays;
import java.util.HashSet;

public final class GrowthRulesTest {
    private GrowthRulesTest() {
    }

    public static void main(String[] args) {
        assert GrowthRules.capacity(0) == 8;
        assert GrowthRules.capacity(3) == 26;
        assert GrowthRules.capacity(99) == 26;
        assert GrowthRules.requiredEssence(1) == 3;
        assert GrowthRules.requiredEssence(3) == 7;
        assert GrowthRules.requiredPurity(2) == 1.5;
        assert GrowthRules.successChance(2, 3.0, 80, 0)
                > GrowthRules.successChance(2, 1.5, 20, 3);
        assert GrowthRules.successChance(3, 0.0, 0, 99) == 0.20;
        assert GrowthRules.failureLoss(3) < GrowthRules.requiredEssence(3);
        assert InheritanceType.values().length == 4;
        assert new HashSet<>(Arrays.stream(InheritanceType.values())
                .map(InheritanceType::missionType).toList()).size() == 4;
        assert Arrays.stream(InheritanceType.values()).allMatch(type -> type.materialAmount() > 0);
        System.out.println("GrowthRulesTest passed");
    }
}
