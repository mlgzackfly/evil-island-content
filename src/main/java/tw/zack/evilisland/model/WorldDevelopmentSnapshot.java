package tw.zack.evilisland.model;

import java.util.EnumMap;
import java.util.Map;

public record WorldDevelopmentSnapshot(
        int cycle,
        Map<WorldResource, Integer> resources,
        Map<CityProject, Integer> projects,
        Map<Faction, Integer> reputation,
        Map<ExplorationSite, Integer> discoveries,
        Map<EventChain, Integer> chains,
        String lastEnding,
        long updatedAt
) {
    public WorldDevelopmentSnapshot {
        cycle = Math.max(1, cycle);
        resources = normalized(WorldResource.class, resources, 0, 999);
        projects = normalized(CityProject.class, projects, 0, 3);
        reputation = normalized(Faction.class, reputation, -100, 100);
        discoveries = normalized(ExplorationSite.class, discoveries, 0, Integer.MAX_VALUE);
        chains = normalized(EventChain.class, chains, 0, 3);
        lastEnding = lastEnding == null ? "" : lastEnding;
    }

    public static WorldDevelopmentSnapshot initial(int cycle, long now) {
        return new WorldDevelopmentSnapshot(cycle, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), "", now);
    }

    public int resource(WorldResource resource) { return resources.getOrDefault(resource, 0); }
    public int project(CityProject project) { return projects.getOrDefault(project, 0); }
    public int reputation(Faction faction) { return reputation.getOrDefault(faction, 0); }
    public int discoveryCycle(ExplorationSite site) { return discoveries.getOrDefault(site, 0); }
    public int chainProgress(EventChain chain) { return chains.getOrDefault(chain, 0); }
    public boolean discovered(ExplorationSite site) { return discoveryCycle(site) > 0; }
    public boolean chainComplete(EventChain chain) { return chainProgress(chain) >= chain.stageCount(); }

    private static <E extends Enum<E>> Map<E, Integer> normalized(Class<E> type, Map<E, Integer> source,
                                                                   int minimum, int maximum) {
        EnumMap<E, Integer> result = new EnumMap<>(type);
        if (source != null) {
            source.forEach((key, value) -> {
                if (key != null && value != null) result.put(key, Math.max(minimum, Math.min(maximum, value)));
            });
        }
        return Map.copyOf(result);
    }
}
