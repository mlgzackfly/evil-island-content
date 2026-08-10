package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import tw.zack.evilisland.model.JourneyMilestone;
import tw.zack.evilisland.model.JourneySnapshot;
import tw.zack.evilisland.model.JourneyStep;
import tw.zack.evilisland.model.MainlineChapter;
import tw.zack.evilisland.persistence.DatabaseManager;
import tw.zack.evilisland.persistence.JourneyRepository;
import tw.zack.evilisland.persistence.ExpeditionRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class MainlineService implements Listener {
    private final EvilIslandPlugin plugin;
    private final DatabaseManager database;
    private final JourneyRepository repository;
    private final CampaignService campaign;
    private final PlayerProfileService profiles;
    private final WeaponService weapons;
    private final ExpeditionRepository expeditions;
    private final Map<UUID, JourneySnapshot> journeys = new HashMap<>();

    public MainlineService(EvilIslandPlugin plugin, DatabaseManager database, JourneyRepository repository,
                           CampaignService campaign, PlayerProfileService profiles, WeaponService weapons,
                           ExpeditionRepository expeditions) {
        this.plugin = plugin;
        this.database = database;
        this.repository = repository;
        this.campaign = campaign;
        this.profiles = profiles;
        this.weapons = weapons;
        this.expeditions = expeditions;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        JourneySnapshot journey = synchronize(player);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            MainlineChapter chapter = chapter();
            player.sendMessage(EvilIslandPlugin.message("本週主線｜" + chapter.display() + "："
                    + chapter.purpose(), NamedTextColor.AQUA));
            player.sendMessage(Component.text("目前目標：" + objective(player), NamedTextColor.YELLOW));
        }, 30L);
    }

    public MainlineChapter chapter() {
        return MainlineChapter.fromWeek(campaign.state().week());
    }

    public JourneySnapshot journey(Player player) {
        return synchronize(player);
    }

    public String objective(Player player) {
        JourneyStep step = synchronize(player).step();
        return step == JourneyStep.MAINLINE ? chapter().objective() : step.objective();
    }

    public String summary(Player player) {
        JourneyStep step = synchronize(player).step();
        return "第 " + chapter().week() + " 週｜" + chapter().display() + "｜"
                + (step == JourneyStep.MAINLINE ? "主線行動" : "新兵旅程：" + step.display());
    }

    public void record(Player player, JourneyMilestone milestone) {
        record(player.getUniqueId(), milestone);
    }

    public void record(UUID playerId, JourneyMilestone milestone) {
        long now = System.currentTimeMillis();
        JourneySnapshot previous = journeys.computeIfAbsent(playerId,
                id -> repository.find(id).orElseGet(() -> JourneySnapshot.initial(id, now)));
        if (previous.has(milestone)) return;
        JourneySnapshot changed = previous.record(milestone, now);
        journeys.put(playerId, changed);
        database.submit(() -> repository.save(changed)).exceptionally(exception -> {
            plugin.getLogger().log(Level.SEVERE, "Cannot save player journey", exception);
            return null;
        });
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            player.sendMessage(EvilIslandPlugin.message("新兵旅程更新｜" + changed.step().display() + "："
                    + objective(player), NamedTextColor.YELLOW));
        }
    }

    public void reset(Player player) {
        journeys.remove(player.getUniqueId());
        repository.delete(player.getUniqueId());
    }

    public int runSelfTest() {
        JourneySnapshot snapshot = JourneySnapshot.initial(new UUID(0L, 30L), 1L);
        int checks = snapshot.step() == JourneyStep.AWAKEN_QI ? 1 : 0;
        for (JourneyMilestone milestone : JourneyMilestone.values()) snapshot = snapshot.record(milestone, 2L);
        if (snapshot.step() == JourneyStep.MAINLINE) checks++;
        if (MainlineChapter.values().length == 4 && MainlineChapter.fromWeek(4) == MainlineChapter.HOLD_NEW_CITY) {
            checks++;
        }
        if (java.util.Arrays.stream(MainlineChapter.values()).allMatch(chapter -> !chapter.objective().isBlank())) {
            checks++;
        }
        return checks;
    }

    private JourneySnapshot synchronize(Player player) {
        long now = System.currentTimeMillis();
        JourneySnapshot snapshot = journeys.computeIfAbsent(player.getUniqueId(), id -> repository.find(id)
                .orElseGet(() -> JourneySnapshot.initial(id, now)));
        JourneySnapshot changed = snapshot;
        if (profiles.isFormulaLocked(player)) changed = changed.record(JourneyMilestone.QI_AWAKENED, now);
        if (weapons.hasWeapon(player)) changed = changed.record(JourneyMilestone.WEAPON_CLAIMED, now);
        if (profiles.zaochiKills(player) > 0 || profiles.transformations(player) > 0) {
            changed = changed.record(JourneyMilestone.PATROL_COMPLETED, now);
        }
        if (!changed.has(JourneyMilestone.EXPEDITION_COMPLETED)
                && expeditions.hasStoryDecision(player.getUniqueId())) {
            changed = changed.record(JourneyMilestone.CAMP_REACHED, now)
                    .record(JourneyMilestone.EXPEDITION_STARTED, now)
                    .record(JourneyMilestone.WITHDRAWAL_REVIEWED, now)
                    .record(JourneyMilestone.EXPEDITION_COMPLETED, now);
        }
        if (changed.milestoneMask() != snapshot.milestoneMask()) {
            journeys.put(player.getUniqueId(), changed);
            JourneySnapshot saved = changed;
            database.submit(() -> repository.save(saved));
        }
        return changed;
    }
}
