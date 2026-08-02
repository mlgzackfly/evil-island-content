package tw.zack.evilisland;

import org.bukkit.Location;
import tw.zack.evilisland.model.WorldEventSnapshot;
import tw.zack.evilisland.model.WorldEventState;
import tw.zack.evilisland.persistence.DatabaseManager;
import tw.zack.evilisland.persistence.WorldEventRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class WorldEventService {
    private final EvilIslandPlugin plugin;
    private final DatabaseManager database;
    private final WorldEventRepository repository;
    private final Map<UUID, WorldEventSnapshot> events = new HashMap<>();

    public WorldEventService(EvilIslandPlugin plugin, DatabaseManager database,
                             WorldEventRepository repository) {
        this.plugin = plugin;
        this.database = database;
        this.repository = repository;
    }

    public void load() {
        events.clear();
        for (WorldEventSnapshot event : repository.findAll()) {
            events.put(event.id(), event);
        }
        long running = events.values().stream().filter(event -> event.state().running()).count();
        plugin.getLogger().info("Loaded " + events.size() + " world event records (" + running + " running)." );
    }

    public void create(UUID id, String type, Location anchor) {
        WorldEventSnapshot event = new WorldEventSnapshot(id, type, WorldEventState.PREPARING,
                anchor.getWorld().getUID(), anchor.getX(), anchor.getY(), anchor.getZ(), "{}",
                System.currentTimeMillis());
        events.put(id, event);
        saveAsync(event);
    }

    public boolean transition(UUID id, WorldEventState next) {
        WorldEventSnapshot current = events.get(id);
        if (current == null || !current.state().canTransitionTo(next)) {
            return false;
        }
        WorldEventSnapshot updated = current.withState(next, System.currentTimeMillis());
        events.put(id, updated);
        saveAsync(updated);
        return true;
    }

    public void reconcileRunning(String type, Set<UUID> activeIds) {
        for (WorldEventSnapshot event : new ArrayList<>(events.values())) {
            if (event.type().equals(type) && event.state().running() && !activeIds.contains(event.id())) {
                transition(event.id(), WorldEventState.FAILED);
                plugin.getLogger().warning("Recovered orphaned world event " + event.id() + " as FAILED.");
            }
        }
    }

    public void recover(UUID id, String type, Location anchor, boolean completed) {
        WorldEventSnapshot event = events.get(id);
        if (event == null) {
            create(id, type, anchor);
            event = events.get(id);
        }
        if (event.state() == WorldEventState.PREPARING) {
            transition(id, WorldEventState.ACTIVE);
            event = events.get(id);
        }
        if (completed && event.state() == WorldEventState.ACTIVE) {
            transition(id, WorldEventState.SUCCEEDED);
        }
    }

    public WorldEventSnapshot event(UUID id) {
        return events.get(id);
    }

    public int size() {
        return events.size();
    }

    public int runPersistenceSelfTest(Location location) {
        UUID id = UUID.randomUUID();
        create(id, "selftest", location);
        int checks = event(id) != null && event(id).state() == WorldEventState.PREPARING ? 1 : 0;
        if (transition(id, WorldEventState.ACTIVE)) {
            checks++;
        }
        flushAll();
        WorldEventSnapshot stored = repository.find(id).orElse(null);
        if (stored != null && stored.state() == WorldEventState.ACTIVE
                && stored.world().equals(location.getWorld().getUID())) {
            checks++;
        }
        events.remove(id);
        database.submit(() -> repository.delete(id)).join();
        return checks;
    }

    public void flushAll() {
        var snapshots = new ArrayList<>(events.values());
        database.submit(() -> snapshots.forEach(repository::save)).join();
    }

    private void saveAsync(WorldEventSnapshot event) {
        database.submit(() -> repository.save(event))
                .exceptionally(exception -> {
                    plugin.getLogger().log(Level.SEVERE, "Cannot save world event " + event.id(), exception);
                    return null;
                });
    }
}
