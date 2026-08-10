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
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import tw.zack.evilisland.model.ExpeditionStoryChoice;
import tw.zack.evilisland.model.ExpeditionStoryProgressSnapshot;
import tw.zack.evilisland.model.ExplorationSite;

import java.util.Collection;

public final class ExpeditionStoryWorldService {
    private final RegionControlService regionControl;
    private final NamespacedKey sceneKey;

    public ExpeditionStoryWorldService(EvilIslandPlugin plugin, RegionControlService regionControl) {
        this.regionControl = regionControl;
        this.sceneKey = new NamespacedKey(plugin, "expedition_story_scene");
    }

    public void reconcile(Collection<ExpeditionStoryProgressSnapshot> progress) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(sceneKey, PersistentDataType.STRING)) entity.remove();
            }
        }
        progress.forEach(this::show);
    }

    public void show(ExpeditionStoryProgressSnapshot progress) {
        if (progress.lastChoice() == null) return;
        remove(progress.site());
        Location camp = regionControl.campLocation(progress.site());
        if (camp == null || camp.getWorld() == null) return;
        Location first = ground(camp.clone().add(-3.5, 0, 2.5));
        Location second = ground(camp.clone().add(-3.5, 0, 0.5));
        spawnMarker(first, progress, "first", progress.lastChoice() == ExpeditionStoryChoice.SECURE
                ? Material.SHIELD : Material.COMPASS);
        spawnMarker(second, progress, "second", progress.lastChoice() == ExpeditionStoryChoice.SECURE
                ? Material.IRON_BARS : Material.LANTERN);
        Location label = first.clone().add(0, 2.2, -1.0);
        TextDisplay display = label.getWorld().spawn(label, TextDisplay.class);
        display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        display.setPersistent(true);
        display.text(Component.text((progress.lastChoice() == ExpeditionStoryChoice.SECURE
                ? "內側警戒線" : "外側回訊線") + "\n" + progress.site().display() + "｜"
                + (progress.completed() ? "故事完成" : "進入第 " + progress.chapter() + " 章"),
                progress.lastChoice() == ExpeditionStoryChoice.SECURE
                        ? NamedTextColor.YELLOW : NamedTextColor.AQUA));
        tag(display, progress.site(), "label");
    }

    private void spawnMarker(Location location, ExpeditionStoryProgressSnapshot progress, String role,
                             Material material) {
        ArmorStand stand = location.getWorld().spawn(location, ArmorStand.class);
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setBasePlate(false);
        stand.setArms(true);
        stand.setPersistent(true);
        stand.setRemoveWhenFarAway(false);
        stand.getEquipment().setItemInMainHand(new ItemStack(material));
        tag(stand, progress.site(), role);
    }

    private void remove(ExplorationSite site) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                String value = entity.getPersistentDataContainer().get(sceneKey, PersistentDataType.STRING);
                if (value != null && value.startsWith(site.id() + ":")) entity.remove();
            }
        }
    }

    private void tag(Entity entity, ExplorationSite site, String role) {
        entity.getPersistentDataContainer().set(sceneKey, PersistentDataType.STRING, site.id() + ":" + role);
    }

    private Location ground(Location location) {
        int y = location.getWorld().getHighestBlockYAt(location.getBlockX(), location.getBlockZ()) + 1;
        location.setY(Math.max(location.getY(), y));
        return location;
    }
}
