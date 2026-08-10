package tw.zack.evilisland.expedition;

import tw.zack.evilisland.model.ExpeditionKit;
import tw.zack.evilisland.model.ExpeditionOperation;
import tw.zack.evilisland.model.ExpeditionRoute;
import tw.zack.evilisland.model.ExpeditionStoryChoice;
import tw.zack.evilisland.model.SpeciesType;

public final class ExpeditionCombatDirector {
    public int enemyCount(ExpeditionScenario scenario, ExpeditionOperation operation, ExpeditionRoute route,
                          int participants, int alert, ExpeditionStoryChoice direction, int kitMask) {
        int count = scenario.enemyCount(operation, route, participants, alert, direction);
        if (count > 0 && operation == ExpeditionOperation.SUPPLY_NODE_SABOTAGE
                && ExpeditionKit.contains(kitMask, ExpeditionKit.DEMOLITION)) {
            count = Math.max(1, count - 1);
        }
        return count;
    }

    public SpeciesType enemy(ExpeditionScenario scenario, int spawned) {
        return scenario.enemy(spawned);
    }
}
