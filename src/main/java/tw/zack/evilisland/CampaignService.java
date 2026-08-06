package tw.zack.evilisland;

import tw.zack.evilisland.model.CampaignRules;
import tw.zack.evilisland.model.CampaignSnapshot;
import tw.zack.evilisland.model.CampaignWeek;
import tw.zack.evilisland.model.MissionContract;
import tw.zack.evilisland.model.MissionType;
import tw.zack.evilisland.persistence.CampaignRepository;
import tw.zack.evilisland.persistence.DatabaseManager;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.logging.Level;

public final class CampaignService {
    private final EvilIslandPlugin plugin;
    private final DatabaseManager database;
    private final CampaignRepository repository;
    private CampaignSnapshot state;

    public CampaignService(EvilIslandPlugin plugin, DatabaseManager database, CampaignRepository repository) {
        this.plugin = plugin;
        this.database = database;
        this.repository = repository;
    }

    public void load() {
        long today = epochDay();
        state = repository.find().orElseGet(() -> CampaignSnapshot.initial(today, System.currentTimeMillis()));
        CampaignSnapshot advanced = CampaignRules.advanceTo(state, today, System.currentTimeMillis());
        if (!advanced.equals(state)) {
            state = advanced;
        }
        repository.save(state);
        plugin.getLogger().info("Campaign cycle " + state.cycle() + ", week " + state.week()
                + ", day " + state.day() + " loaded.");
    }

    public void tickDay() {
        CampaignSnapshot advanced = CampaignRules.advanceTo(state, epochDay(), System.currentTimeMillis());
        if (!advanced.equals(state)) {
            state = advanced;
            saveAsync();
        }
    }

    public CampaignSnapshot state() {
        tickDay();
        return state;
    }

    public List<MissionContract> board() {
        return CampaignRules.board(state());
    }

    public List<MissionContract> patrolBoard() {
        return CampaignRules.patrolBoard(state());
    }

    public boolean complete(MissionContract contract) {
        tickDay();
        CampaignSnapshot completed = CampaignRules.complete(state, contract, System.currentTimeMillis());
        if (completed.equals(state)) {
            return false;
        }
        state = completed;
        saveAsync();
        return true;
    }

    public int defenseEnemyModifier() {
        int defense = state().defense();
        if (defense < 35) return 2;
        if (defense < 50) return 1;
        if (defense >= 75) return -1;
        return 0;
    }

    public int weeklyEnemyModifier() {
        return CampaignWeek.fromWeek(state().week()).extraEnemies();
    }

    public double weeklyBossHealthMultiplier() {
        return CampaignWeek.fromWeek(state().week()).bossHealthMultiplier();
    }

    public double intelligenceEnemyHealthMultiplier() {
        int intelligence = state().intelligence();
        if (intelligence < 35) return 1.12;
        if (intelligence >= 70) return 0.92;
        return 1.0;
    }

    public double moraleEnemyDamageMultiplier() {
        int morale = state().morale();
        if (morale < 35) return 1.10;
        if (morale >= 70) return 0.94;
        return 1.0;
    }

    public int supplyRewardBonus() {
        CampaignSnapshot value = state();
        return (value.supply() >= 70 ? 1 : 0) + CampaignWeek.fromWeek(value.week()).bonusRemains();
    }

    public String activeModifierText() {
        List<String> modifiers = new java.util.ArrayList<>();
        int enemies = defenseEnemyModifier();
        if (enemies > 0) modifiers.add("低城防：敵軍 +" + enemies);
        if (enemies < 0) modifiers.add("高城防：敵軍 " + enemies);
        int weeklyEnemies = weeklyEnemyModifier();
        if (weeklyEnemies > 0) modifiers.add("本週警戒：敵軍 +" + weeklyEnemies);
        double health = intelligenceEnemyHealthMultiplier();
        if (health > 1.0) modifiers.add("情報不足：敵軍更耐打");
        if (health < 1.0) modifiers.add("情報充足：敵軍弱點已標記");
        double damage = moraleEnemyDamageMultiplier();
        if (damage > 1.0) modifiers.add("民心低落：支援受阻");
        if (damage < 1.0) modifiers.add("民心穩定：前線支援生效");
        if (state().supply() >= 70) modifiers.add("供應充足：結算遺骸 +1");
        if (CampaignWeek.fromWeek(state().week()).bonusRemains() > 0) {
            modifiers.add("決戰出勤：結算遺骸 +1");
        }
        return modifiers.isEmpty() ? "城況修正：目前無額外影響" : String.join("；", modifiers);
    }

    public int runSelfTest() {
        CampaignSnapshot value = state();
        int checks = value.week() >= 1 && value.week() <= 4 && value.day() >= 1 && value.day() <= 7 ? 1 : 0;
        List<MissionContract> options = board();
        if (options.size() == 3 && new java.util.HashSet<>(options).size() == 3) checks++;
        if (options.stream().map(MissionContract::missionType).distinct().count() == MissionType.values().length) {
            checks++;
        }
        if (java.util.Arrays.stream(MissionContract.values()).map(MissionContract::id).distinct().count()
                == MissionContract.values().length) checks++;
        boolean objectivesValid = java.util.Arrays.stream(MissionContract.values()).allMatch(contract -> switch (
                contract.missionType()) {
            case PATROL -> contract.spawnRadius() > 0.0;
            case GATHER -> !contract.objectiveMaterial().isBlank() && contract.objectiveAmount() > 0;
            case SCOUT -> contract.targetOffsetX() != 0 || contract.targetOffsetZ() != 0;
        });
        if (objectivesValid) checks++;
        return checks;
    }

    public String scheduleText() {
        CampaignSnapshot value = state();
        return "第 " + value.cycle() + " 輪・第 " + value.week() + " 週第 " + value.day() + " 日・"
                + CampaignWeek.fromWeek(value.week()).display();
    }

    public String metricsText() {
        CampaignSnapshot value = state();
        return "城防 " + value.defense() + "　供應 " + value.supply()
                + "　情報 " + value.intelligence() + "　民心 " + value.morale();
    }

    public void flush() {
        if (state != null) {
            CampaignSnapshot snapshot = state;
            database.submit(() -> repository.save(snapshot)).join();
        }
    }

    private void saveAsync() {
        CampaignSnapshot snapshot = state;
        database.submit(() -> repository.save(snapshot))
                .exceptionally(exception -> {
                    plugin.getLogger().log(Level.SEVERE, "Cannot save campaign state", exception);
                    return null;
                });
    }

    private long epochDay() {
        String configured = plugin.getConfig().getString("campaign.time-zone", "Asia/Taipei");
        ZoneId zone;
        try {
            zone = ZoneId.of(configured);
        } catch (RuntimeException ignored) {
            zone = ZoneId.systemDefault();
        }
        return LocalDate.now(zone).toEpochDay();
    }
}
