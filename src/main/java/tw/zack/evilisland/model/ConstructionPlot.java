package tw.zack.evilisland.model;

public record ConstructionPlot(CityProject project, String world, int x, int y, int z, int rotation,
                               int level, String status) {
}
