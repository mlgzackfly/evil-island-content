package tw.zack.evilisland;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import tw.zack.evilisland.model.MissionContract;
import tw.zack.evilisland.model.PlayerActivitySnapshot;
import tw.zack.evilisland.persistence.MissionTelemetryRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MissionTelemetryService implements Listener {
    private final EvilIslandPlugin plugin;
    private final MissionTelemetryRepository repository;
    private final CampaignService campaign;
    private final PlayerProfileService profiles;

    public MissionTelemetryService(EvilIslandPlugin plugin, MissionTelemetryRepository repository,
                                   CampaignService campaign, PlayerProfileService profiles) {
        this.plugin = plugin;
        this.repository = repository;
        this.campaign = campaign;
        this.profiles = profiles;
    }

    public void start(UUID id, MissionContract contract, int players) {
        repository.start(id, contract.missionType(), players, System.currentTimeMillis(),
                "{\"contract\":\"" + contract.id() + "\"}");
    }

    public void succeed(UUID id) {
        repository.finish(id, "succeeded", "", System.currentTimeMillis());
    }

    public void fail(UUID id, String reason) {
        repository.finish(id, "failed", reason, System.currentTimeMillis());
    }

    public void discard(UUID id) {
        repository.delete(id);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Optional<PlayerActivitySnapshot> stored = repository.activity(player.getUniqueId());
        int lastCatchup = stored.map(PlayerActivitySnapshot::lastCatchupCycle).orElse(0);
        long threshold = Math.max(24L, plugin.getConfig().getLong("telemetry.catchup-after-hours", 72L))
                * 60L * 60L * 1000L;
        boolean eligible = stored.isPresent() && now - stored.get().lastSeen() >= threshold
                && lastCatchup < campaign.state().cycle() && profiles.isEnlisted(player);
        if (eligible) {
            int amount = Math.max(1, plugin.getConfig().getInt("telemetry.catchup-bread", 4));
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack(Material.BREAD, amount));
            overflow.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
            lastCatchup = campaign.state().cycle();
            player.sendMessage(EvilIslandPlugin.message("你離城多日，值勤處補發 " + amount
                    + " 份基礎口糧；不包含妖質、熟練或永久能力。", NamedTextColor.GOLD));
        }
        repository.saveActivity(new PlayerActivitySnapshot(player.getUniqueId(), now, lastCatchup));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        int lastCatchup = repository.activity(playerId).map(PlayerActivitySnapshot::lastCatchupCycle).orElse(0);
        repository.saveActivity(new PlayerActivitySnapshot(playerId, System.currentTimeMillis(), lastCatchup));
    }

    public int runSelfTest() {
        int checks = 0;
        if (plugin.getConfig().getLong("telemetry.catchup-after-hours", 72L) >= 24L) checks++;
        if (plugin.getConfig().getInt("telemetry.catchup-bread", 4) > 0) checks++;
        if (repository.countByResult("active") >= 0) checks++;
        return checks;
    }
}
