package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import tw.zack.evilisland.model.IntelReportSnapshot;
import tw.zack.evilisland.model.LivingEventSnapshot;
import tw.zack.evilisland.model.ResidentIntelRules;
import tw.zack.evilisland.model.ResidentRole;
import tw.zack.evilisland.persistence.ResidentIntelRepository;
import tw.zack.evilisland.world.WorldAtlasService;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ResidentIntelService implements Listener {
    private static final int[][][] SCHEDULE = {
            {{10, 4}, {19, 2}, {9, -7}, {14, 7}},
            {{-8, -5}, {-15, -9}, {-7, 7}, {-3, -12}},
            {{-4, 8}, {3, 14}, {8, 5}, {-11, 4}},
            {{5, -6}, {-4, -14}, {-10, -4}, {4, -8}},
            {{-12, 1}, {-18, 5}, {-6, 12}, {-14, -6}},
            {{8, 9}, {14, 10}, {13, -5}, {7, 13}}
    };

    private final EvilIslandPlugin plugin;
    private final ResidentIntelRepository repository;
    private final DaoFieldService daoFields;
    private final WorldAtlasService atlas;
    private final LivingWorldService livingWorld;
    private final NamespacedKey residentKey;
    private final Map<ResidentRole, UUID> actors = new EnumMap<>(ResidentRole.class);
    private List<IntelReportSnapshot> reports = new ArrayList<>();
    private UUID loadedEvent;
    private boolean indexed;

    public ResidentIntelService(EvilIslandPlugin plugin, ResidentIntelRepository repository,
                                DaoFieldService daoFields, WorldAtlasService atlas,
                                LivingWorldService livingWorld) {
        this.plugin = plugin;
        this.repository = repository;
        this.daoFields = daoFields;
        this.atlas = atlas;
        this.livingWorld = livingWorld;
        this.residentKey = new NamespacedKey(plugin, "resident_role");
    }

    public void load() {
        syncReports(livingWorld.activeEventWithoutSync());
        tick();
    }

    public void tick() {
        LivingEventSnapshot active = livingWorld.activeEventWithoutSync();
        syncReports(active);
        Location center = daoFields.cityCenter();
        if (center == null || center.getWorld() == null) return;
        indexResidents(center.getWorld());
        int period = period(center.getWorld().getTime());
        for (ResidentRole role : ResidentRole.values()) {
            int[] offset = SCHEDULE[role.ordinal()][period];
            Location destination = ground(center.clone().add(offset[0] * atlas.coordinateScale(), 0,
                    offset[1] * atlas.coordinateScale()));
            Entity existing = actors.get(role) == null ? null : Bukkit.getEntity(actors.get(role));
            Villager resident = existing instanceof Villager villager ? villager : null;
            if (resident == null || !resident.isValid()) {
                resident = destination.getWorld().spawn(destination, Villager.class,
                        actor -> configure(actor, role));
                actors.put(role, resident.getUniqueId());
            } else if (!resident.getWorld().equals(destination.getWorld())
                    || resident.getLocation().distanceSquared(destination) > 4.0) {
                resident.teleport(destination);
            }
        }
    }

    public int enemyReduction(LivingEventSnapshot event) {
        if (event == null) return 0;
        syncReports(event);
        return ResidentIntelRules.verified(event.type(), reports, requiredTruths())
                ? Math.max(0, plugin.getConfig().getInt("living-world.resident-intel.enemy-reduction", 1)) : 0;
    }

    @EventHandler(ignoreCancelled = true)
    public void onResidentInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        String value = event.getRightClicked().getPersistentDataContainer().get(residentKey, PersistentDataType.STRING);
        ResidentRole role = ResidentRole.parse(value);
        if (role == null) return;
        event.setCancelled(true);
        report(event.getPlayer(), role);
    }

    public int runSelfTest() {
        int checks = 0;
        if (ResidentRole.values().length == 6) checks++;
        if (java.util.Arrays.stream(tw.zack.evilisland.model.LivingEventType.values()).allMatch(type ->
                java.util.Arrays.stream(ResidentRole.values()).filter(role ->
                        ResidentIntelRules.truthful(type, role)).count() == 4)) checks++;
        if (java.util.Arrays.stream(tw.zack.evilisland.model.LivingEventType.values()).allMatch(type ->
                java.util.Arrays.stream(ResidentRole.values()).filter(role -> !ResidentIntelRules.truthful(type, role))
                        .allMatch(role -> ResidentIntelRules.claimedRegion(type, role) != type.region()))) checks++;
        if (actors.size() == ResidentRole.values().length && actors.values().stream()
                .allMatch(id -> Bukkit.getEntity(id) instanceof Villager)) checks++;
        if (loadedEvent == null || repository.load(loadedEvent).size() == reports.size()) checks++;
        return checks;
    }

    private void report(Player player, ResidentRole role) {
        LivingEventSnapshot active = livingWorld.activeEvent();
        if (active == null) {
            player.sendMessage(EvilIslandPlugin.message(role.display() + "目前沒有新的區域消息。"));
            return;
        }
        syncReports(active);
        boolean wasVerified = ResidentIntelRules.verified(active.type(), reports, requiredTruths());
        IntelReportSnapshot report = new IntelReportSnapshot(active.id(), role, player.getUniqueId(),
                System.currentTimeMillis());
        boolean added = repository.add(report);
        if (added) reports = repository.load(active.id());
        String claim = role.display() + "表示，動靜似乎來自「"
                + ResidentIntelRules.claimedRegion(active.type(), role).display() + "」。";
        player.sendMessage(EvilIslandPlugin.message(claim, NamedTextColor.YELLOW));
        if (!added) player.sendMessage(Component.text("這份消息已由其他玩家記錄。", NamedTextColor.GRAY));
        if (reports.size() < requiredTruths()) {
            player.sendMessage(Component.text("尚需更多不同居民的消息才能交叉核對（" + reports.size() + "/"
                            + requiredTruths() + "）。",
                    NamedTextColor.GRAY));
            return;
        }
        boolean truthful = ResidentIntelRules.truthful(active.type(), role);
        player.sendMessage(Component.text("交叉核對：這份消息" + (truthful ? "與其他線索吻合。" : "存在矛盾。"),
                truthful ? NamedTextColor.GREEN : NamedTextColor.RED));
        long trueCount = reports.stream().filter(value -> ResidentIntelRules.truthful(active.type(), value.resident()))
                .count();
        player.sendMessage(Component.text("可信情報 " + Math.min(requiredTruths(), trueCount) + "/"
                + requiredTruths() + "，已記錄來源 "
                + reports.size() + "/6。", NamedTextColor.GRAY));
        if (!wasVerified && ResidentIntelRules.verified(active.type(), reports, requiredTruths())) {
            Bukkit.broadcast(EvilIslandPlugin.message("居民消息已完成交叉核對，危機增援壓力降低一層。",
                    NamedTextColor.GREEN));
        }
    }

    private void syncReports(LivingEventSnapshot active) {
        UUID eventId = active == null ? null : active.id();
        if (java.util.Objects.equals(loadedEvent, eventId)) return;
        loadedEvent = eventId;
        reports = eventId == null ? new ArrayList<>() : new ArrayList<>(repository.load(eventId));
    }

    private void indexResidents(World world) {
        if (indexed) return;
        indexed = true;
        for (Entity entity : world.getEntities()) {
            ResidentRole role = ResidentRole.parse(entity.getPersistentDataContainer().get(
                    residentKey, PersistentDataType.STRING));
            if (role == null) continue;
            if (!actors.containsKey(role)) actors.put(role, entity.getUniqueId());
            else entity.remove();
        }
    }

    private void configure(Villager actor, ResidentRole role) {
        actor.customName(Component.text(role.display(), NamedTextColor.AQUA));
        actor.setCustomNameVisible(true);
        actor.setAI(false);
        actor.setInvulnerable(true);
        actor.setPersistent(true);
        actor.setRemoveWhenFarAway(false);
        actor.getPersistentDataContainer().set(residentKey, PersistentDataType.STRING, role.id());
    }

    private Location ground(Location location) {
        int y = location.getWorld().getHighestBlockYAt(location.getBlockX(), location.getBlockZ()) + 1;
        return new Location(location.getWorld(), location.getBlockX() + 0.5, y, location.getBlockZ() + 0.5);
    }

    private int period(long time) {
        if (time < 1_000L || time >= 23_000L) return 0;
        if (time < 12_000L) return 1;
        if (time < 14_000L) return 2;
        return 3;
    }

    private int requiredTruths() {
        return Math.max(2, Math.min(4,
                plugin.getConfig().getInt("living-world.resident-intel.required-truths", 3)));
    }
}
