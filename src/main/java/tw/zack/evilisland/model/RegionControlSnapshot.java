package tw.zack.evilisland.model;

public record RegionControlSnapshot(
        ExplorationSite site,
        RegionState state,
        int stability,
        int campLevel,
        int supplies,
        String world,
        int x,
        int y,
        int z,
        long updatedAt
) {
    public RegionControlSnapshot {
        if (site == null || state == null || world == null) {
            throw new IllegalArgumentException("Region control fields cannot be null");
        }
        stability = RegionControlRules.clampStability(stability);
        campLevel = RegionControlRules.clampCampLevel(campLevel);
        supplies = RegionControlRules.clampSupplies(supplies);
    }

    public static RegionControlSnapshot initial(ExplorationSite site, long now) {
        return new RegionControlSnapshot(site, RegionState.TENSE, RegionControlRules.INITIAL_STABILITY,
                1, 3, "", 0, 0, 0, now);
    }

    public RegionControlSnapshot adjust(int delta, long now) {
        int next = RegionControlRules.clampStability(stability + delta);
        return new RegionControlSnapshot(site, RegionControlRules.stateAfter(state, next), next,
                campLevel, supplies, world, x, y, z, Math.max(now, updatedAt + 1));
    }

    public RegionControlSnapshot withCamp(int level, int stock, long now) {
        return new RegionControlSnapshot(site, state, stability, level, stock, world, x, y, z,
                Math.max(now, updatedAt + 1));
    }

    public RegionControlSnapshot withAnchor(String worldName, int anchorX, int anchorY, int anchorZ, long now) {
        return new RegionControlSnapshot(site, state, stability, campLevel, supplies, worldName,
                anchorX, anchorY, anchorZ, Math.max(now, updatedAt + 1));
    }

    public boolean placed() { return !world.isBlank(); }
}
