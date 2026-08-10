package tw.zack.evilisland.expedition;

import org.bukkit.Material;
import tw.zack.evilisland.model.ExpeditionOperation;
import tw.zack.evilisland.model.ExpeditionPhase;
import tw.zack.evilisland.model.ExpeditionRegionRules;
import tw.zack.evilisland.model.ExpeditionRoute;
import tw.zack.evilisland.model.ExpeditionRules;
import tw.zack.evilisland.model.ExpeditionStoryChoice;
import tw.zack.evilisland.model.ExplorationSite;
import tw.zack.evilisland.model.SpeciesType;

abstract class AbstractExpeditionScenario implements ExpeditionScenario {
    private final ExplorationSite site;

    AbstractExpeditionScenario(ExplorationSite site) {
        this.site = site;
    }

    @Override public ExplorationSite site() { return site; }
    @Override public String boardTitle() { return ExpeditionRegionRules.boardTitle(site); }
    @Override public String routeDisplay(ExpeditionRoute route) {
        return ExpeditionRegionRules.routeDisplay(site, route);
    }
    @Override public String routeDescription(ExpeditionRoute route) {
        return ExpeditionRegionRules.routeDescription(site, route);
    }
    @Override public Material routeIcon(ExpeditionRoute route) {
        return ExpeditionRegionRules.routeIcon(site, route);
    }
    @Override public ExpeditionOperation operation(long seed) {
        return ExpeditionRegionRules.operation(site, seed);
    }
    @Override public int requiredClues(ExpeditionOperation operation, ExpeditionRoute route,
                                       ExpeditionStoryChoice direction) {
        int base = ExpeditionRegionRules.requiredClues(site, operation, route);
        return direction == ExpeditionStoryChoice.SECURE ? Math.max(2, base - 1) : base;
    }
    @Override public int enemyCount(ExpeditionOperation operation, ExpeditionRoute route, int participants,
                                    int alert, ExpeditionStoryChoice direction) {
        int base = ExpeditionRegionRules.enemyCount(site, operation, route, participants, alert);
        if (base == 0) return 0;
        if (direction == ExpeditionStoryChoice.SECURE) return Math.max(1, base - 1);
        if (direction == ExpeditionStoryChoice.CONNECT) return base + 1;
        return base;
    }
    @Override public SpeciesType enemy(int index) { return ExpeditionRegionRules.enemy(site, index); }
    @Override public long syncWindowMillis(ExpeditionOperation operation, ExpeditionRoute route,
                                            ExpeditionStoryChoice direction) {
        long base = ExpeditionRules.syncWindowMillis(operation, route);
        if (direction == ExpeditionStoryChoice.SECURE) return Math.max(8_000L, base - 3_000L);
        if (direction == ExpeditionStoryChoice.CONNECT) return base + 3_000L;
        return base;
    }
    @Override public ExpeditionPhase phaseAfterObjective() { return ExpeditionPhase.ESCALATION; }
    @Override public Material clueMaterial(ExpeditionOperation operation, int index) {
        return ExpeditionRegionRules.clueMaterial(operation, index);
    }
    @Override public String clueName(ExpeditionOperation operation, int index) {
        return ExpeditionRegionRules.clueName(operation, index);
    }
    @Override public Material objectiveMaterial(ExpeditionOperation operation, int index) {
        return ExpeditionRegionRules.objectiveMaterial(operation, index);
    }
    @Override public String objectiveName(ExpeditionOperation operation, int index) {
        return ExpeditionRegionRules.objectiveName(operation, index);
    }
    @Override public boolean combatRequired() { return true; }
    @Override public boolean timedExtraction(ExpeditionOperation operation) { return false; }
}
