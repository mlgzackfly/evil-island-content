package tw.zack.evilisland.model;

public record ExpeditionCampBlockSnapshot(
        ExplorationSite site,
        String world,
        int x,
        int y,
        int z,
        String originalData,
        String levelOneData,
        String levelTwoData,
        String lostData,
        String placedData
) {
    public ExpeditionCampBlockSnapshot {
        if (site == null || world == null || originalData == null || levelOneData == null
                || levelTwoData == null || lostData == null || placedData == null) {
            throw new IllegalArgumentException("Camp block fields cannot be null");
        }
    }

    public String desired(RegionControlSnapshot region) {
        if (region.state() == RegionState.LOST) return lostData;
        return region.campLevel() >= 2 ? levelTwoData : levelOneData;
    }

    public ExpeditionCampBlockSnapshot withPlacedData(String value) {
        return new ExpeditionCampBlockSnapshot(site, world, x, y, z, originalData, levelOneData,
                levelTwoData, lostData, value);
    }
}
