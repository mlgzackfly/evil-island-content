package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import tw.zack.evilisland.model.EssenceSample;
import tw.zack.evilisland.model.EssenceSourceSnapshot;
import tw.zack.evilisland.model.GrowthRules;
import tw.zack.evilisland.model.PlayerGrowthSnapshot;
import tw.zack.evilisland.model.SpeciesType;
import tw.zack.evilisland.persistence.DatabaseManager;
import tw.zack.evilisland.persistence.GrowthRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class GrowthService implements Listener {
    public enum ResultType {
        SUCCESS,
        REJECTED,
        INSUFFICIENT_ESSENCE,
        INSUFFICIENT_PURITY,
        MAXIMUM_STAGE
    }

    public record TransformationResult(ResultType type, int stage, int requiredEssence,
                                       double averagePurity, double chance, int lostEssence) {
    }

    private final EvilIslandPlugin plugin;
    private final DatabaseManager database;
    private final GrowthRepository repository;
    private final PlayerProfileService profiles;
    private final DaoFieldService daoFields;
    private final Map<UUID, GrowthState> states = new HashMap<>();
    private final Map<UUID, GrowthState> preloaded = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public GrowthService(EvilIslandPlugin plugin, DatabaseManager database, GrowthRepository repository,
                         PlayerProfileService profiles, DaoFieldService daoFields) {
        this.plugin = plugin;
        this.database = database;
        this.repository = repository;
        this.profiles = profiles;
        this.daoFields = daoFields;
    }

    public int capacity(Player player) {
        return GrowthRules.capacity(profiles.transformations(player));
    }

    public int remainingCapacity(Player player) {
        return Math.max(0, capacity(player) - profiles.essence(player));
    }

    public int rejection(Player player) {
        return state(player).rejection;
    }

    public double averagePurity(Player player) {
        GrowthState state = state(player);
        int amount = state.sources.values().stream().mapToInt(SourceState::amount).sum();
        int points = state.sources.values().stream().mapToInt(SourceState::purityPoints).sum();
        return amount == 0 ? 0.0 : (double) points / amount;
    }

    public String sourceSummary(Player player) {
        GrowthState state = state(player);
        if (state.sources.isEmpty()) return "尚無已煉化來源";
        return state.sources.entrySet().stream()
                .sorted(Map.Entry.<String, SourceState>comparingByValue(
                        Comparator.comparingInt(SourceState::amount)).reversed())
                .limit(3)
                .map(entry -> sourceName(entry.getKey()) + " " + entry.getValue().amount())
                .reduce((left, right) -> left + "、" + right).orElse("尚無已煉化來源");
    }

    public int addEssence(Player player, List<EssenceSample> samples) {
        GrowthState state = state(player);
        int added = 0;
        long now = System.currentTimeMillis();
        for (EssenceSample sample : samples) {
            if (sample.amount() <= 0) continue;
            SourceState current = state.sources.getOrDefault(sample.source(), new SourceState(0, 0));
            state.sources.put(sample.source(), new SourceState(current.amount() + sample.amount(),
                    current.purityPoints() + sample.amount() * sample.purity()));
            added += sample.amount();
        }
        if (added > 0) {
            profiles.addEssence(player, added);
            state.updatedAt = now;
            saveAsync(player.getUniqueId(), state);
        }
        return added;
    }

    public TransformationResult transform(Player player) {
        GrowthState state = state(player);
        int stage = profiles.transformations(player) + 1;
        if (stage > GrowthRules.MAX_TRANSFORMATIONS) {
            return new TransformationResult(ResultType.MAXIMUM_STAGE, stage, 0,
                    averagePurity(player), 0.0, 0);
        }
        int required = GrowthRules.requiredEssence(stage);
        if (profiles.essence(player) < required) {
            return new TransformationResult(ResultType.INSUFFICIENT_ESSENCE, stage, required,
                    averagePurity(player), 0.0, 0);
        }
        double purity = previewPurity(state, required);
        if (purity < GrowthRules.requiredPurity(stage)) {
            return new TransformationResult(ResultType.INSUFFICIENT_PURITY, stage, required, purity, 0.0, 0);
        }
        int dao = daoFields.reading(player.getLocation()).dao();
        double chance = stage == 1
                ? plugin.getConfig().getDouble("progression.first-transformation-success", 1.0)
                : GrowthRules.successChance(stage, purity, dao, state.rejection);
        if (random.nextDouble() > chance) {
            int loss = Math.min(profiles.essence(player), GrowthRules.failureLoss(stage));
            consume(state, loss);
            profiles.spendEssence(player, loss);
            state.rejection++;
            state.updatedAt = System.currentTimeMillis();
            saveAsync(player.getUniqueId(), state);
            return new TransformationResult(ResultType.REJECTED, stage, required, purity, chance, loss);
        }
        consume(state, required);
        profiles.spendEssence(player, required);
        profiles.setTransformations(player, stage);
        state.rejection = Math.max(0, state.rejection - 1);
        state.updatedAt = System.currentTimeMillis();
        saveAsync(player.getUniqueId(), state);
        return new TransformationResult(ResultType.SUCCESS, stage, required, purity, chance, 0);
    }

    public int runSelfTest() {
        int checks = 0;
        if (GrowthRules.capacity(3) > GrowthRules.capacity(1)) checks++;
        if (GrowthRules.requiredEssence(1) < GrowthRules.requiredEssence(3)) checks++;
        if (GrowthRules.requiredPurity(1) < GrowthRules.requiredPurity(3)) checks++;
        if (GrowthRules.successChance(2, 3.0, 70, 0)
                > GrowthRules.successChance(2, 1.5, 40, 2)) checks++;
        if (GrowthRules.failureLoss(3) < GrowthRules.requiredEssence(3)) checks++;
        return checks;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            preloaded.put(event.getUniqueId(), load(event.getUniqueId()));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Cannot load player growth", exception);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("易質資料載入失敗，請稍後再試。", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        GrowthState loaded = preloaded.remove(playerId);
        states.put(playerId, loaded == null ? load(playerId) : loaded);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        GrowthState state = states.remove(event.getPlayer().getUniqueId());
        if (state != null) saveAsync(event.getPlayer().getUniqueId(), state);
    }

    public void flush() {
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        states.forEach((playerId, state) -> saves.add(saveAsync(playerId, state)));
        CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new)).join();
    }

    public void reset(Player player) {
        UUID playerId = player.getUniqueId();
        states.remove(playerId);
        preloaded.remove(playerId);
    }

    private GrowthState state(Player player) {
        GrowthState state = states.computeIfAbsent(player.getUniqueId(), this::load);
        int stored = state.sources.values().stream().mapToInt(SourceState::amount).sum();
        int actual = profiles.essence(player);
        if (stored < actual) {
            SourceState legacy = state.sources.getOrDefault("legacy", new SourceState(0, 0));
            int difference = actual - stored;
            state.sources.put("legacy", new SourceState(legacy.amount() + difference,
                    legacy.purityPoints() + difference));
        } else if (stored > actual) {
            consume(state, stored - actual);
        }
        return state;
    }

    private String sourceName(String source) {
        SpeciesType type = SpeciesType.parse(source);
        return type == null ? (source.equals("legacy") ? "既有妖質" : source) : type.display();
    }

    private GrowthState load(UUID playerId) {
        PlayerGrowthSnapshot growth = repository.loadGrowth(playerId)
                .orElse(new PlayerGrowthSnapshot(playerId, 0, System.currentTimeMillis()));
        Map<String, SourceState> sources = new HashMap<>();
        for (EssenceSourceSnapshot source : repository.loadSources(playerId)) {
            sources.put(source.source(), new SourceState(source.amount(), source.purityPoints()));
        }
        return new GrowthState(growth.rejection(), growth.updatedAt(), sources);
    }

    private double previewPurity(GrowthState state, int amount) {
        int remaining = amount;
        double points = 0.0;
        List<SourceState> ordered = state.sources.values().stream()
                .sorted(Comparator.comparingDouble(SourceState::averagePurity).reversed()).toList();
        for (SourceState source : ordered) {
            int taken = Math.min(remaining, source.amount());
            points += taken * source.averagePurity();
            remaining -= taken;
            if (remaining == 0) break;
        }
        return amount == 0 || remaining > 0 ? 0.0 : points / amount;
    }

    private void consume(GrowthState state, int amount) {
        int remaining = Math.max(0, amount);
        List<Map.Entry<String, SourceState>> ordered = state.sources.entrySet().stream()
                .sorted(Map.Entry.<String, SourceState>comparingByValue(
                        Comparator.comparingDouble(SourceState::averagePurity)).reversed()).toList();
        for (Map.Entry<String, SourceState> entry : ordered) {
            if (remaining == 0) break;
            SourceState source = entry.getValue();
            int taken = Math.min(remaining, source.amount());
            int left = source.amount() - taken;
            int pointsLeft = left == 0 ? 0 : (int) Math.round(source.averagePurity() * left);
            if (left == 0) state.sources.remove(entry.getKey());
            else state.sources.put(entry.getKey(), new SourceState(left, pointsLeft));
            remaining -= taken;
        }
    }

    private CompletableFuture<Void> saveAsync(UUID playerId, GrowthState state) {
        PlayerGrowthSnapshot growth = new PlayerGrowthSnapshot(playerId, state.rejection, state.updatedAt);
        List<EssenceSourceSnapshot> sources = state.sources.entrySet().stream()
                .map(entry -> new EssenceSourceSnapshot(playerId, entry.getKey(), entry.getValue().amount(),
                        entry.getValue().purityPoints(), state.updatedAt)).toList();
        return database.submit(() -> {
            repository.saveGrowth(growth);
            repository.replaceSources(playerId, sources);
        }).exceptionally(exception -> {
            plugin.getLogger().log(Level.SEVERE, "Cannot save player growth", exception);
            return null;
        });
    }

    private record SourceState(int amount, int purityPoints) {
        private double averagePurity() {
            return amount == 0 ? 0.0 : (double) purityPoints / amount;
        }
    }

    private static final class GrowthState {
        private int rejection;
        private long updatedAt;
        private final Map<String, SourceState> sources;

        private GrowthState(int rejection, long updatedAt, Map<String, SourceState> sources) {
            this.rejection = rejection;
            this.updatedAt = updatedAt;
            this.sources = sources;
        }
    }
}
