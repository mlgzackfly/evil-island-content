package tw.zack.evilisland;

import tw.zack.evilisland.model.NpcRole;
import tw.zack.evilisland.model.NpcRosterRules;
import tw.zack.evilisland.model.NpcRosterSnapshot;
import tw.zack.evilisland.persistence.DatabaseManager;
import tw.zack.evilisland.persistence.NpcRosterRepository;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;

public final class NpcRosterService {
    private final EvilIslandPlugin plugin;
    private final DatabaseManager database;
    private final NpcRosterRepository repository;
    private final Map<NpcRole, NpcRosterSnapshot> states = new EnumMap<>(NpcRole.class);

    public NpcRosterService(EvilIslandPlugin plugin, DatabaseManager database, NpcRosterRepository repository) {
        this.plugin = plugin;
        this.database = database;
        this.repository = repository;
    }

    public void load() {
        long now = System.currentTimeMillis();
        states.putAll(repository.findAll());
        for (NpcRole role : NpcRole.values()) {
            states.putIfAbsent(role, new NpcRosterSnapshot(role, 0, 0L, now));
            states.put(role, normalize(states.get(role), now));
            repository.save(states.get(role));
        }
        plugin.getLogger().info("NPC roster loaded with " + states.size() + " roles.");
    }

    public NpcRosterSnapshot state(NpcRole role) {
        long now = System.currentTimeMillis();
        NpcRosterSnapshot current = normalize(states.get(role), now);
        states.put(role, current);
        return current;
    }

    public boolean available(NpcRole role) {
        return state(role).available(System.currentTimeMillis(), fatigueLimit());
    }

    public void completeMission(NpcRole role) {
        if (role == null) return;
        long now = System.currentTimeMillis();
        NpcRosterSnapshot updated = NpcRosterRules.completeMission(state(role),
                plugin.getConfig().getInt("npc-roster.mission-fatigue", 18), now);
        update(updated);
    }

    public void abortMission(NpcRole role) {
        if (role == null) return;
        long now = System.currentTimeMillis();
        NpcRosterSnapshot updated = NpcRosterRules.completeMission(state(role),
                plugin.getConfig().getInt("npc-roster.abort-fatigue", 6), now);
        update(updated);
    }

    public void injure(NpcRole role) {
        if (role == null) return;
        long now = System.currentTimeMillis();
        NpcRosterSnapshot updated = NpcRosterRules.injure(state(role),
                plugin.getConfig().getLong("npc-roster.injury-duration-ms", 1800000L), now);
        update(updated);
    }

    public void treat(NpcRole role) {
        long now = System.currentTimeMillis();
        NpcRosterSnapshot updated = NpcRosterRules.treat(state(role),
                plugin.getConfig().getInt("npc-roster.treatment-fatigue-relief", 35), now);
        update(updated);
    }

    public String statusText(NpcRole role) {
        NpcRosterSnapshot state = state(role);
        long now = System.currentTimeMillis();
        if (state.injured(now)) {
            long minutes = Math.max(1L, (state.injuredUntil() - now + 59999L) / 60000L);
            return "負傷休養中（約 " + minutes + " 分鐘）・疲勞 " + state.fatigue() + "/100";
        }
        if (!state.available(now, fatigueLimit())) {
            return "疲勞過高，暫停出勤・疲勞 " + state.fatigue() + "/100";
        }
        return "可出勤・疲勞 " + state.fatigue() + "/100";
    }

    public int runSelfTest() {
        long now = System.currentTimeMillis();
        int checks = states.size() == NpcRole.values().length ? 1 : 0;
        if (java.util.Arrays.stream(NpcRole.values()).allMatch(role -> state(role).role() == role)) checks++;
        if (java.util.Arrays.stream(NpcRole.values()).allMatch(role -> state(role).fatigue() >= 0
                && state(role).fatigue() <= 100)) checks++;
        return checks;
    }

    public void flush() {
        Map<NpcRole, NpcRosterSnapshot> snapshots = new EnumMap<>(states);
        database.submit(() -> snapshots.values().forEach(repository::save)).join();
    }

    private NpcRosterSnapshot normalize(NpcRosterSnapshot state, long now) {
        if (state == null) throw new IllegalStateException("NPC roster has not been loaded");
        return NpcRosterRules.normalize(state, now,
                plugin.getConfig().getLong("npc-roster.fatigue-recovery-interval-ms", 600000L));
    }

    private int fatigueLimit() {
        return plugin.getConfig().getInt("npc-roster.fatigue-limit", 80);
    }

    private void update(NpcRosterSnapshot state) {
        states.put(state.role(), state);
        database.submit(() -> repository.save(state)).exceptionally(exception -> {
            plugin.getLogger().log(Level.SEVERE, "Cannot save NPC roster", exception);
            return null;
        });
    }
}
