package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import tw.zack.evilisland.model.Formula;
import tw.zack.evilisland.model.FormulaPath;
import tw.zack.evilisland.model.ObjectiveStage;
import tw.zack.evilisland.model.PlayerProfileSnapshot;
import tw.zack.evilisland.model.QiTendency;
import tw.zack.evilisland.persistence.DatabaseManager;
import tw.zack.evilisland.persistence.PlayerProfileRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class PlayerProfileService implements Listener {
    private final EvilIslandPlugin plugin;
    private final DatabaseManager database;
    private final PlayerProfileRepository repository;
    private final NamespacedKey tendencyKey;
    private final NamespacedKey formulaKey;
    private final NamespacedKey secondaryFormulaKey;
    private final NamespacedKey formulaRatioKey;
    private final NamespacedKey qiKey;
    private final NamespacedKey essenceKey;
    private final NamespacedKey transformationsKey;
    private final NamespacedKey objectiveKey;
    private final NamespacedKey zaochiKillsKey;
    private final Map<UUID, Optional<PlayerProfileSnapshot>> preloaded = new ConcurrentHashMap<>();
    private final Map<UUID, State> states = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();

    public PlayerProfileService(EvilIslandPlugin plugin, DatabaseManager database,
                                PlayerProfileRepository repository) {
        this.plugin = plugin;
        this.database = database;
        this.repository = repository;
        tendencyKey = new NamespacedKey(plugin, "qi_tendency");
        formulaKey = new NamespacedKey(plugin, "formula");
        secondaryFormulaKey = new NamespacedKey(plugin, "formula_secondary");
        formulaRatioKey = new NamespacedKey(plugin, "formula_primary_percent");
        qiKey = new NamespacedKey(plugin, "qi");
        essenceKey = new NamespacedKey(plugin, "demon_essence");
        transformationsKey = new NamespacedKey(plugin, "transformations");
        objectiveKey = new NamespacedKey(plugin, "objective");
        zaochiKillsKey = new NamespacedKey(plugin, "zaochi_kills");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            preloaded.put(event.getUniqueId(), repository.find(event.getUniqueId()));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Cannot load profile for " + event.getUniqueId(), exception);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("角色資料載入失敗，請稍後再試。", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        attach(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        State state = states.remove(player.getUniqueId());
        dirty.remove(player.getUniqueId());
        preloaded.remove(player.getUniqueId());
        if (state != null) {
            PlayerProfileSnapshot snapshot = state.snapshot(player.getUniqueId());
            database.submit(() -> repository.save(snapshot))
                    .exceptionally(exception -> {
                        plugin.getLogger().log(Level.SEVERE, "Cannot save profile for " + player.getUniqueId(), exception);
                        return null;
                    });
        }
    }

    public boolean isEnlisted(Player player) {
        State state = state(player);
        return state.tendency != null && state.formulaPath != null;
    }

    public boolean isMeasured(Player player) {
        return state(player).tendency != null;
    }

    public boolean isFormulaLocked(Player player) {
        return state(player).formulaPath != null;
    }

    public QiTendency measureTendency(Player player) {
        State state = state(player);
        if (state.tendency != null) {
            return state.tendency;
        }
        long innateValue = player.getUniqueId().getMostSignificantBits()
                ^ Long.rotateLeft(player.getUniqueId().getLeastSignificantBits(), 17);
        state.tendency = (Long.bitCount(innateValue) & 1) == 0
                ? QiTendency.OUTWARD : QiTendency.INWARD;
        state.qi = maxQi(state);
        markDirty(player);
        return state.tendency;
    }

    public boolean lockFormula(Player player, FormulaPath path) {
        State state = state(player);
        if (state.tendency == null || state.formulaPath != null) {
            return false;
        }
        state.formulaPath = path;
        state.essence = 0;
        state.transformations = 0;
        state.zaochiKills = 0;
        state.objective = ObjectiveStage.REPORT_PATROL;
        state.qi = maxQi(state);
        markDirty(player);
        return true;
    }

    public void reset(Player player) {
        UUID uuid = player.getUniqueId();
        states.put(uuid, State.blank(player.getName()));
        dirty.remove(uuid);
        clearLegacy(player);
        database.submit(() -> repository.delete(uuid))
                .exceptionally(exception -> {
                    plugin.getLogger().log(Level.SEVERE, "Cannot reset profile for " + uuid, exception);
                    return null;
                });
    }

    public QiTendency tendency(Player player) {
        return state(player).tendency;
    }

    public Formula formula(Player player) {
        FormulaPath path = state(player).formulaPath;
        return path == null ? null : path.dominant();
    }

    public FormulaPath formulaPath(Player player) {
        return state(player).formulaPath;
    }

    public int qi(Player player) {
        State state = state(player);
        return Math.min(state.qi, maxQi(state));
    }

    public void setQi(Player player, int value) {
        State state = state(player);
        int clamped = Math.max(0, Math.min(maxQi(state), value));
        if (state.qi != clamped) {
            state.qi = clamped;
            markDirty(player);
        }
    }

    public void addQi(Player player, int amount) {
        setQi(player, qi(player) + amount);
    }

    public boolean spendQi(Player player, int amount) {
        if (qi(player) < amount) {
            return false;
        }
        setQi(player, qi(player) - amount);
        return true;
    }

    public int maxQi(Player player) {
        return maxQi(state(player));
    }

    public int essence(Player player) {
        return state(player).essence;
    }

    public void addEssence(Player player, int amount) {
        State state = state(player);
        int value = Math.max(0, state.essence + amount);
        if (state.essence != value) {
            state.essence = value;
            markDirty(player);
        }
    }

    public boolean spendEssence(Player player, int amount) {
        if (essence(player) < amount) {
            return false;
        }
        addEssence(player, -amount);
        return true;
    }

    public int transformations(Player player) {
        return state(player).transformations;
    }

    public void setTransformations(Player player, int value) {
        State state = state(player);
        state.transformations = Math.max(0, value);
        state.qi = maxQi(state);
        markDirty(player);
    }

    public ObjectiveStage objective(Player player) {
        return state(player).objective;
    }

    public void setObjective(Player player, ObjectiveStage stage) {
        State state = state(player);
        if (state.objective != stage) {
            state.objective = stage;
            markDirty(player);
        }
    }

    public int zaochiKills(Player player) {
        return state(player).zaochiKills;
    }

    public void recordZaochiKill(Player player) {
        State state = state(player);
        state.zaochiKills++;
        markDirty(player);
    }

    public void flushDirty() {
        if (dirty.isEmpty()) {
            return;
        }
        Set<UUID> flushing = new HashSet<>(dirty);
        List<PlayerProfileSnapshot> snapshots = new ArrayList<>();
        for (UUID uuid : flushing) {
            State state = states.get(uuid);
            if (state != null) {
                snapshots.add(state.snapshot(uuid));
            }
        }
        dirty.removeAll(flushing);
        database.submit(() -> repository.saveAll(snapshots))
                .whenComplete((ignored, exception) -> {
                    if (exception == null) {
                        return;
                    }
                    plugin.getLogger().log(Level.SEVERE, "Cannot flush player profiles", exception);
                    if (plugin.isEnabled()) {
                        Bukkit.getScheduler().runTask(plugin, () -> dirty.addAll(flushing));
                    }
                });
    }

    public void flushAll() {
        List<PlayerProfileSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<UUID, State> entry : states.entrySet()) {
            snapshots.add(entry.getValue().snapshot(entry.getKey()));
        }
        database.submit(() -> repository.saveAll(snapshots)).join();
        dirty.clear();
    }

    private void attach(Player player) {
        Optional<PlayerProfileSnapshot> loaded = preloaded.remove(player.getUniqueId());
        if (loaded == null) {
            loaded = repository.find(player.getUniqueId());
        }
        if (loaded != null && loaded.isPresent()) {
            State state = State.from(loaded.get());
            if (!state.name.equals(player.getName())) {
                state.name = player.getName();
                dirty.add(player.getUniqueId());
            }
            states.put(player.getUniqueId(), state);
            clearLegacy(player);
            return;
        }

        State migrated = readLegacy(player);
        states.put(player.getUniqueId(), migrated);
        if (!legacyPresent(player)) {
            return;
        }
        PlayerProfileSnapshot snapshot = migrated.snapshot(player.getUniqueId());
        database.submit(() -> repository.save(snapshot))
                .whenComplete((ignored, exception) -> {
                    if (exception != null) {
                        plugin.getLogger().log(Level.SEVERE,
                                "Cannot migrate legacy PDC profile for " + player.getUniqueId(), exception);
                    } else if (plugin.isEnabled()) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                clearLegacy(player);
                            }
                        });
                    }
                });
    }

    private State state(Player player) {
        State existing = states.get(player.getUniqueId());
        if (existing != null) {
            return existing;
        }
        attach(player);
        return states.get(player.getUniqueId());
    }

    private State readLegacy(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        QiTendency tendency = QiTendency.parse(data.get(tendencyKey, PersistentDataType.STRING));
        Formula primary = Formula.parse(data.get(formulaKey, PersistentDataType.STRING));
        Formula secondary = Formula.parse(data.get(secondaryFormulaKey, PersistentDataType.STRING));
        FormulaPath path = null;
        if (primary != null) {
            if (secondary == null) {
                path = FormulaPath.pure(primary);
            } else {
                Integer ratio = data.get(formulaRatioKey, PersistentDataType.INTEGER);
                try {
                    path = FormulaPath.mixed(primary, secondary, ratio == null ? 50 : ratio);
                } catch (IllegalArgumentException ignored) {
                    path = FormulaPath.pure(primary);
                }
            }
        }
        State state = new State(player.getName());
        state.tendency = tendency;
        state.formulaPath = path;
        state.qi = integer(data, qiKey);
        state.essence = integer(data, essenceKey);
        state.transformations = integer(data, transformationsKey);
        state.objective = ObjectiveStage.fromId(integer(data, objectiveKey));
        state.zaochiKills = integer(data, zaochiKillsKey);
        if (state.tendency != null && state.qi == 0 && !data.has(qiKey, PersistentDataType.INTEGER)) {
            state.qi = maxQi(state);
        }
        return state;
    }

    private boolean legacyPresent(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        return data.has(tendencyKey) || data.has(formulaKey) || data.has(secondaryFormulaKey)
                || data.has(formulaRatioKey) || data.has(qiKey) || data.has(essenceKey)
                || data.has(transformationsKey) || data.has(objectiveKey) || data.has(zaochiKillsKey);
    }

    private void clearLegacy(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        data.remove(tendencyKey);
        data.remove(formulaKey);
        data.remove(secondaryFormulaKey);
        data.remove(formulaRatioKey);
        data.remove(qiKey);
        data.remove(essenceKey);
        data.remove(transformationsKey);
        data.remove(objectiveKey);
        data.remove(zaochiKillsKey);
    }

    private int integer(PersistentDataContainer data, NamespacedKey key) {
        Integer value = data.get(key, PersistentDataType.INTEGER);
        return value == null ? 0 : Math.max(0, value);
    }

    private void markDirty(Player player) {
        State state = states.get(player.getUniqueId());
        if (state != null) {
            state.name = player.getName();
            dirty.add(player.getUniqueId());
        }
    }

    private int maxQi(State state) {
        int base = state.tendency == QiTendency.OUTWARD ? 120 : 110;
        return base + state.transformations * 20;
    }

    private static final class State {
        private String name;
        private QiTendency tendency;
        private FormulaPath formulaPath;
        private int qi;
        private int essence;
        private int transformations;
        private ObjectiveStage objective = ObjectiveStage.UNENLISTED;
        private int zaochiKills;

        private State(String name) {
            this.name = name;
        }

        private static State blank(String name) {
            return new State(name);
        }

        private static State from(PlayerProfileSnapshot snapshot) {
            State state = new State(snapshot.name());
            state.tendency = snapshot.tendency();
            state.formulaPath = snapshot.formulaPath();
            state.qi = snapshot.qi();
            state.essence = snapshot.essence();
            state.transformations = snapshot.transformations();
            state.objective = snapshot.objective();
            state.zaochiKills = snapshot.zaochiKills();
            return state;
        }

        private PlayerProfileSnapshot snapshot(UUID uuid) {
            return new PlayerProfileSnapshot(uuid, name, tendency, formulaPath, qi, essence,
                    transformations, objective, zaochiKills, System.currentTimeMillis());
        }
    }
}
