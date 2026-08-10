package tw.zack.evilisland.expedition;

import tw.zack.evilisland.model.ExplorationSite;

import java.util.EnumMap;
import java.util.Map;

public final class ExpeditionScenarioRegistry {
    private static final ExpeditionScenarioRegistry STANDARD = new ExpeditionScenarioRegistry();
    private final Map<ExplorationSite, ExpeditionScenario> scenarios = new EnumMap<>(ExplorationSite.class);

    public ExpeditionScenarioRegistry() {
        register(new EasternRouteScenario());
        register(new UdingWallScenario());
        register(new RongxuApproachScenario());
        register(new WesternTraceScenario());
        register(new DragonCoastScenario());
        if (scenarios.size() != ExplorationSite.values().length) {
            throw new IllegalStateException("Every expedition site requires a scenario controller");
        }
    }

    public static ExpeditionScenarioRegistry standard() { return STANDARD; }

    public ExpeditionScenario forSite(ExplorationSite site) {
        ExpeditionScenario scenario = scenarios.get(site);
        if (scenario == null) throw new IllegalArgumentException("Unknown expedition site " + site);
        return scenario;
    }

    public int size() { return scenarios.size(); }

    private void register(ExpeditionScenario scenario) {
        if (scenarios.putIfAbsent(scenario.site(), scenario) != null) {
            throw new IllegalStateException("Duplicate expedition scenario " + scenario.site());
        }
    }
}
