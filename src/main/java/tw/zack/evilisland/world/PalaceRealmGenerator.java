package tw.zack.evilisland.world;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * A separated gameplay reconstruction of the enclosed realm behind the Dragon Palace.
 * The novels establish the realm, but not a complete surveyable street plan.
 */
public final class PalaceRealmGenerator extends ChunkGenerator {
    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData data) {
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = chunkX * 16 + lx;
                int z = chunkZ * 16 + lz;
                buildColumn(data, lx, lz, x, z);
            }
        }
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public int getBaseHeight(WorldInfo worldInfo, Random random, int x, int z, HeightMap heightMap) {
        return islandHeight(x, z) + 1;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, 93, 0.5);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                return Biome.THE_VOID;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo info) {
                return List.of(Biome.THE_VOID);
            }
        };
    }

    private void buildColumn(ChunkData data, int lx, int lz, int x, int z) {
        int surface = islandHeight(x, z);
        if (surface < 0) return;

        double radius = Math.sqrt((double) x * x + (double) z * z);
        int bottom = Math.max(8, 48 - (int) Math.round((surface - 70) * 0.7));
        for (int y = bottom; y <= surface; y++) {
            Material material = y == surface ? Material.MOSS_BLOCK
                    : y >= surface - 4 ? Material.DIRT : Material.DEEPSLATE;
            data.setBlock(lx, y, lz, material);
        }

        if (Math.abs(x) <= 32 && Math.abs(z) <= 32) {
            buildCentralPalace(data, lx, lz, x, z, surface);
        } else if (radius >= 105 && radius <= 108 && surface > 65) {
            for (int y = surface + 1; y <= surface + 7; y++) {
                data.setBlock(lx, y, lz, Material.QUARTZ_BRICKS);
            }
        } else if ((Math.abs(x) <= 3 || Math.abs(z) <= 3) && radius < 145) {
            data.setBlock(lx, surface, lz, Material.POLISHED_BLACKSTONE_BRICKS);
        }
    }

    private void buildCentralPalace(ChunkData data, int lx, int lz, int x, int z, int ground) {
        int edge = Math.max(Math.abs(x), Math.abs(z));
        if (edge >= 28) {
            for (int y = ground + 1; y <= ground + 17; y++) {
                boolean opening = Math.abs(x) <= 4 || Math.abs(z) <= 4;
                data.setBlock(lx, y, lz, opening ? Material.AIR : Material.DARK_PRISMARINE);
            }
        } else {
            for (int y = ground + 1; y <= ground + 16; y++) data.setBlock(lx, y, lz, Material.AIR);
        }
        data.setBlock(lx, ground + 18, lz, edge < 24 ? Material.QUARTZ_BLOCK : Material.GOLD_BLOCK);
        if (Math.abs(x) <= 5 && Math.abs(z) <= 5) {
            data.setBlock(lx, ground + 19, lz, Material.SEA_LANTERN);
        }
    }

    private int islandHeight(int x, int z) {
        double radius = Math.sqrt((double) x * x + (double) z * z);
        if (radius > 170) return -1;
        double edge = Math.max(0, 1.0 - radius / 170.0);
        double waves = Math.sin(x * 0.08) * 2.5 + Math.cos(z * 0.07) * 2.0;
        return (int) Math.round(66 + edge * 23 + waves);
    }
}
