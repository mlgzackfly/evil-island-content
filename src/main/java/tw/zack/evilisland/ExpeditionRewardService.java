package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import tw.zack.evilisland.model.ExpeditionConsequenceSnapshot;
import tw.zack.evilisland.model.ExpeditionOperation;
import tw.zack.evilisland.model.ExpeditionOutcome;
import tw.zack.evilisland.model.ExpeditionRoute;
import tw.zack.evilisland.model.ExplorationSite;
import tw.zack.evilisland.model.WorldResource;
import tw.zack.evilisland.persistence.ExpeditionRepository;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class ExpeditionRewardService {
    private final EvilIslandPlugin plugin;
    private final ExpeditionRepository repository;
    private final DevelopmentService development;
    private final RegionControlService regionControl;
    private final NamespacedKey consequenceKey;
    private final Map<ExplorationSite, UUID> displays = new EnumMap<>(ExplorationSite.class);

    public ExpeditionRewardService(EvilIslandPlugin plugin, ExpeditionRepository repository,
                                   DevelopmentService development, RegionControlService regionControl) {
        this.plugin = plugin;
        this.repository = repository;
        this.development = development;
        this.regionControl = regionControl;
        consequenceKey = new NamespacedKey(plugin, "expedition_consequence");
    }

    public void load() {
        displays.clear();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                ExplorationSite site = ExplorationSite.parse(entity.getPersistentDataContainer()
                        .get(consequenceKey, PersistentDataType.STRING));
                if (site == null) continue;
                UUID duplicate = displays.putIfAbsent(site, entity.getUniqueId());
                if (duplicate != null) entity.remove();
            }
        }
        repository.consequences().forEach(this::show);
    }

    public boolean resolve(UUID expeditionId, ExplorationSite site, ExpeditionOperation operation,
                           ExpeditionRoute route, ExpeditionOutcome outcome, int participants, int eventScore,
                           int cycle, int week, long now) {
        repository.recordRegionOutcome(site, operation, outcome, now);
        boolean valuable = outcome == ExpeditionOutcome.COMPLETE || outcome == ExpeditionOutcome.PARTIAL;
        boolean rewarded = valuable && repository.claimWeeklyReward(site, route, cycle, week, expeditionId, now);
        if (rewarded) {
            regionControl.recordExpedition(expeditionId, site, outcome, participants);
            grant(operation, outcome, eventScore);
        } else if (outcome == ExpeditionOutcome.ABANDONED) {
            regionControl.recordExpedition(expeditionId, site, outcome, participants);
        }
        Location camp = regionControl.campLocation(site);
        if (camp != null && camp.getWorld() != null) {
            ExpeditionConsequenceSnapshot consequence = new ExpeditionConsequenceSnapshot(site, expeditionId,
                    operation, outcome, camp.getWorld().getName(), camp.getX() + 3.5, camp.getY(),
                    camp.getZ() + 2.5, now);
            repository.saveConsequence(consequence);
            show(consequence);
        }
        return rewarded;
    }

    public void clearRuntimeState() {
        displays.clear();
    }

    private void grant(ExpeditionOperation operation, ExpeditionOutcome outcome, int eventScore) {
        int amount = outcome == ExpeditionOutcome.COMPLETE
                ? operation == ExpeditionOperation.LOST_CONVOY ? 3 : 2 : 1;
        if (outcome == ExpeditionOutcome.COMPLETE && eventScore >= 3) amount++;
        WorldResource primary = switch (operation) {
            case LOST_CONVOY, CASUALTY_EVACUATION -> WorldResource.PROVISIONS;
            case BLOCKADE_INFILTRATION -> WorldResource.TIMBER;
            case SUPPLY_NODE_SABOTAGE -> WorldResource.COMPONENTS;
            default -> operation.site().reward();
        };
        development.addResource(primary, amount);
        if (outcome == ExpeditionOutcome.COMPLETE && primary != WorldResource.PROVISIONS) {
            development.addResource(WorldResource.PROVISIONS, 1);
        }
    }

    private void show(ExpeditionConsequenceSnapshot snapshot) {
        World world = Bukkit.getWorld(snapshot.world());
        if (world == null) return;
        UUID previous = displays.remove(snapshot.site());
        Entity old = previous == null ? null : Bukkit.getEntity(previous);
        if (old != null) old.remove();
        Location location = new Location(world, snapshot.x(), snapshot.y(), snapshot.z());
        int y = world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ()) + 1;
        location.setY(Math.max(location.getY(), y));
        ArmorStand stand = world.spawn(location, ArmorStand.class);
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setMarker(false);
        stand.setBasePlate(false);
        stand.setArms(true);
        stand.setPersistent(true);
        stand.setRemoveWhenFarAway(false);
        stand.getEquipment().setItemInMainHand(new ItemStack(material(snapshot.operation(), snapshot.outcome())));
        stand.customName(Component.text(name(snapshot), snapshot.outcome() == ExpeditionOutcome.COMPLETE
                ? NamedTextColor.GREEN : snapshot.outcome() == ExpeditionOutcome.PARTIAL
                ? NamedTextColor.YELLOW : NamedTextColor.RED));
        stand.setCustomNameVisible(true);
        stand.getPersistentDataContainer().set(consequenceKey, PersistentDataType.STRING, snapshot.site().id());
        displays.put(snapshot.site(), stand.getUniqueId());
    }

    private Material material(ExpeditionOperation operation, ExpeditionOutcome outcome) {
        if (outcome == ExpeditionOutcome.ABANDONED || outcome == ExpeditionOutcome.WITHDRAWN) {
            return Material.REDSTONE_TORCH;
        }
        return switch (operation) {
            case LOST_CONVOY -> Material.CHEST_MINECART;
            case BLOCKADE_INFILTRATION -> Material.LANTERN;
            case SUPPLY_NODE_SABOTAGE -> Material.REDSTONE_LAMP;
            case CASUALTY_EVACUATION -> Material.WHITE_BED;
            default -> operation.icon();
        };
    }

    private String name(ExpeditionConsequenceSnapshot snapshot) {
        var direction = repository.storyProgress(snapshot.site()).direction();
        return snapshot.site().display() + "｜" + snapshot.operation().display() + "："
                + snapshot.outcome().display() + (direction == null ? "" : "｜" + direction.display());
    }
}
