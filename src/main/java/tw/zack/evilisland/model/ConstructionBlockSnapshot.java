package tw.zack.evilisland.model;

public record ConstructionBlockSnapshot(CityProject project, String world, int x, int y, int z,
                                        String originalData, String placedData) {
}
