package tw.zack.evilisland.world;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import java.util.List;

public final class EvilIslandBiomeProvider extends BiomeProvider {
    private static final List<Biome> BIOMES = List.of(
            Biome.OCEAN,
            Biome.PLAINS,
            Biome.FOREST,
            Biome.JUNGLE,
            Biome.STONY_PEAKS,
            Biome.DESERT,
            Biome.DRIPSTONE_CAVES
    );
    private final int coordinateScale;

    public EvilIslandBiomeProvider() {
        this(1);
    }

    public EvilIslandBiomeProvider(int coordinateScale) {
        this.coordinateScale = Math.max(1, coordinateScale);
    }

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        x = Math.floorDiv(x, coordinateScale);
        z = Math.floorDiv(z, coordinateScale);
        if (EvilIslandWorldGenerator.isDragonPalace(x, z)) {
            return Biome.DEEP_OCEAN;
        }
        if (EvilIslandWorldGenerator.isMagicIsland(x, z)) {
            return Biome.WINDSWEPT_FOREST;
        }
        if (EvilIslandWorldGenerator.isEastContinent(x, z)) {
            return z > 1200 ? Biome.SAVANNA : Biome.PLAINS;
        }
        if (!EvilIslandWorldGenerator.isEvilIsland(x, z)) {
            return Biome.OCEAN;
        }
        if (x > 650) {
            return Biome.STONY_PEAKS;
        }
        if (x < -1200) {
            return Math.abs(z) > 900 ? Biome.JUNGLE : Biome.FOREST;
        }
        return Biome.PLAINS;
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        return BIOMES;
    }
}
