package tw.zack.evilisland.world;

public final class EvilIslandShape {
    public static final long DEFAULT_SEED = 4290053L;
    private static final int MAGIC_ISLAND_X = 3420;
    private static final int MAGIC_ISLAND_Z = 1750;
    private static final int DRAGON_PALACE_X = 2500;
    private static final int DRAGON_PALACE_Z = -1900;
    static final int SUI_AN_X = 700;
    static final int SUI_AN_Z = 0;
    static final int SUI_AN_HALF_X = 270;
    static final int SUI_AN_HALF_Z = 430;

    private EvilIslandShape() {
    }

    public static int surfaceHeight(int x, int z) {
        return terrainHeight(DEFAULT_SEED, x, z);
    }

    public static boolean isEvilIsland(int x, int z) {
        if (x < -2700 || x > 1580) return false;
        double progress = (1580.0 - x) / 4280.0;
        double halfWidth = 430.0 + progress * 1250.0;
        double fringe = smoothNoise(DEFAULT_SEED, x, z, 180.0, 71) * 95.0;
        if (x < -1900) fringe += Math.sin(z / 83.0) * 110.0;
        return Math.abs(z) <= halfWidth + fringe;
    }

    public static boolean isEastContinent(int x, int z) {
        double coast = 3990.0 + smoothNoise(DEFAULT_SEED, x, z, 260.0, 201) * 120.0;
        return x >= coast;
    }

    public static boolean isMagicIsland(int x, int z) {
        return distanceSquared(x, z, MAGIC_ISLAND_X, MAGIC_ISLAND_Z) <= 205L * 205L;
    }

    public static boolean isDragonPalace(int x, int z) {
        return distanceSquared(x, z, DRAGON_PALACE_X, DRAGON_PALACE_Z) <= 95L * 95L;
    }

    public static String canonicalRegion(int x, int z) {
        if (isMagicIsland(x, z)) return "魔法島";
        if (isDragonPalace(x, z)) return "龍宮海域";
        if (isEastContinent(x, z)) return "東大陸";
        if (!isEvilIsland(x, z)) return "內海";
        if (suiAnRadius(x, z) <= 1.12) return "歲安城";
        if (distanceSquared(x, z, 1180, 0) < 380L * 380L) return "九回山";
        if (x > 620) return "宇定高原";
        if (x < -1200) return "西方荒野";
        return "噩盡島平原";
    }

    static int terrainHeight(long seed, int x, int z) {
        double suiAnRadius = suiAnRadius(x, z);
        if (suiAnRadius <= 1.0) return 78;
        if (Math.abs(x - 4300) <= 230 && Math.abs(z) <= 230) return 76;
        if (x >= 1482 && x <= 1598 && z >= -138 && z <= -22) return mountainPassTerraceHeight(x);
        if (isMagicIsland(x, z)) {
            double distance = Math.sqrt(distanceSquared(x, z, MAGIC_ISLAND_X, MAGIC_ISLAND_Z));
            return (int) Math.round(72 + Math.max(0, 1.0 - distance / 205.0) * 28
                    + fractalNoise(seed, x, z, 95.0, 301) * 5);
        }
        if (isEastContinent(x, z)) {
            return (int) Math.round(78 + fractalNoise(seed, x, z, 180.0, 211) * 10
                    + Math.max(0, x - 4300) * 0.005);
        }
        if (!isEvilIsland(x, z)) {
            return (int) Math.round(38 + fractalNoise(seed, x, z, 210.0, 17) * 8);
        }

        double progress = (1580.0 - x) / 4280.0;
        double halfWidth = 430.0 + progress * 1250.0 + smoothNoise(seed, x, z, 180.0, 71) * 95.0;
        double coastalDepth = Math.min(Math.min(x + 2700.0, 1580.0 - x), halfWidth - Math.abs(z));
        double base = x > 620 ? 116 + (x - 620) * 0.028 : 72 + Math.max(0, x + 700) * 0.007;
        double mountain = 78.0 * gaussian(x, z, 1180, 0, 330, 300);
        double southeast = 38.0 * gaussian(x, z, 1320, 620, 300, 260);
        double height = base + mountain + southeast + fractalNoise(seed, x, z, 145.0, 31) * 11;
        if (coastalDepth < 85) {
            double blend = Math.max(0, coastalDepth / 85.0);
            height = 58 + (height - 58) * blend;
        }
        if (isBlueYaoRiver(x, z)) return 59;
        if (suiAnRadius <= 1.18) {
            double blend = (suiAnRadius - 1.0) / 0.18;
            blend = blend * blend * (3.0 - 2.0 * blend);
            height = 78 + (height - 78) * blend;
        }
        return Math.max(56, (int) Math.round(height));
    }

    static double suiAnRadius(int x, int z) {
        double dx = (x - SUI_AN_X) / (double) SUI_AN_HALF_X;
        double dz = (z - SUI_AN_Z) / (double) SUI_AN_HALF_Z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    static int mountainPassTerraceHeight(int x) {
        int terrace = Math.max(61, Math.min(66, Math.floorDiv(x, 24)));
        return 153 - (terrace - 61) * 18;
    }

    private static boolean isBlueYaoRiver(int x, int z) {
        if (z < -1650 || z > 1150) return false;
        double riverX = 410 + Math.max(0, z) * 0.08 + Math.sin(z / 170.0) * 35;
        double width = z < -350 ? 38 : 24;
        return Math.abs(x - riverX) <= width;
    }

    private static double gaussian(int x, int z, int cx, int cz, double rx, double rz) {
        double dx = (x - cx) / rx;
        double dz = (z - cz) / rz;
        return Math.exp(-(dx * dx + dz * dz) * 2.0);
    }

    private static double fractalNoise(long seed, int x, int z, double scale, int salt) {
        double total = 0;
        double amplitude = 1;
        double weight = 0;
        for (int octave = 0; octave < 4; octave++) {
            total += smoothNoise(seed, x, z, scale, salt + octave * 31) * amplitude;
            weight += amplitude;
            amplitude *= 0.5;
            scale *= 0.5;
        }
        return total / weight;
    }

    private static double smoothNoise(long seed, int x, int z, double scale, int salt) {
        double fx = x / scale;
        double fz = z / scale;
        int x0 = (int) Math.floor(fx);
        int z0 = (int) Math.floor(fz);
        double tx = fade(fx - x0);
        double tz = fade(fz - z0);
        double a = randomUnit(seed, x0, z0, salt);
        double b = randomUnit(seed, x0 + 1, z0, salt);
        double c = randomUnit(seed, x0, z0 + 1, salt);
        double d = randomUnit(seed, x0 + 1, z0 + 1, salt);
        return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
    }

    private static double randomUnit(long seed, int x, int z, int salt) {
        long hash = positiveHash(x ^ (int) seed, z ^ (int) (seed >>> 32), salt);
        return (hash % 2000001L) / 1000000.0 - 1.0;
    }

    private static long positiveHash(int x, int z, int salt) {
        long value = x * 341873128712L + z * 132897987541L + salt * 42317861L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value & Long.MAX_VALUE;
    }

    private static double fade(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static long distanceSquared(int x, int z, int centerX, int centerZ) {
        long dx = x - centerX;
        long dz = z - centerZ;
        return dx * dx + dz * dz;
    }
}
