package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import tw.zack.evilisland.model.ObjectiveStage;

import java.time.Duration;
import java.util.Random;

public final class ProgressionService implements Listener {
    private final EvilIslandPlugin plugin;
    private final PlayerProfileService profiles;
    private final DaoFieldService daoFields;
    private final GameItemService items;
    private final EncounterService encounters;
    private final Random random = new Random();

    public ProgressionService(EvilIslandPlugin plugin, PlayerProfileService profiles, DaoFieldService daoFields,
                              GameItemService items, EncounterService encounters) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.daoFields = daoFields;
        this.items = items;
        this.encounters = encounters;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!profiles.isEnlisted(player)) {
            return;
        } else {
            player.sendMessage(EvilIslandPlugin.message("東境巡防紀錄已恢復。" + objectiveText(player)));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStationInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (daoFields.isRefinery(event.getClickedBlock())) {
            event.setCancelled(true);
            refine(player);
        } else if (daoFields.isMirror(event.getClickedBlock())) {
            event.setCancelled(true);
            transform(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRemainPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !items.isRemains(event.getItem().getItemStack())) {
            return;
        }
        player.sendMessage(EvilIslandPlugin.message("取得妖物遺骸；它尚不是妖質，需帶回煉化臺。"));
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            int required = requiredEssence();
            if (profiles.transformations(player) == 0 && items.countRemains(player.getInventory()) + profiles.essence(player) >= required) {
                profiles.setObjective(player, ObjectiveStage.REFINE_REMAINS);
                player.sendMessage(EvilIslandPlugin.message("材料已足夠，返回新城的煉化臺。", NamedTextColor.YELLOW));
            }
        });
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!profiles.isEnlisted(player)) {
            return;
        }
        int lost;
        if (event.getKeepInventory()) {
            lost = items.removeAllRemains(player.getInventory());
        } else {
            int before = event.getDrops().size();
            items.removeRemainsFromDrops(event.getDrops());
            lost = before - event.getDrops().size();
        }
        if (lost > 0) {
            player.sendMessage(EvilIslandPlugin.message("你在巡防中失去所有隨身妖物遺骸。", NamedTextColor.RED));
            if (profiles.transformations(player) == 0 && profiles.essence(player) < requiredEssence()) {
                profiles.setObjective(player, ObjectiveStage.HUNT_ZAOCHI);
            }
        }
    }

    public void refine(Player player) {
        if (!profiles.isEnlisted(player)) {
            player.sendMessage(EvilIslandPlugin.message("先完成軍團報到。"));
            return;
        }
        int produced = items.consumeAllRemains(player.getInventory());
        if (produced <= 0) {
            player.sendMessage(EvilIslandPlugin.message("煉化臺中沒有可處理的妖物遺骸。"));
            return;
        }
        profiles.addEssence(player, produced);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 0.7f);
        player.sendMessage(EvilIslandPlugin.message("完成煉化，取得 " + produced + " 份妖質。現有妖質：" + profiles.essence(player) + "。", NamedTextColor.GOLD));
        if (profiles.transformations(player) == 0 && profiles.essence(player) >= requiredEssence()) {
            profiles.setObjective(player, ObjectiveStage.FIRST_TRANSFORMATION);
            player.sendMessage(EvilIslandPlugin.message("前往聚炁鏡旁，以右鍵啟動第一次易質。"));
        }
    }

    public void transform(Player player) {
        if (!profiles.isEnlisted(player)) {
            player.sendMessage(EvilIslandPlugin.message("先完成軍團報到。"));
            return;
        }
        if (profiles.transformations(player) > 0) {
            player.sendMessage(EvilIslandPlugin.message("這個垂直切片目前只開放第一次易質。"));
            return;
        }
        int required = requiredEssence();
        if (profiles.essence(player) < required) {
            player.sendMessage(EvilIslandPlugin.message("妖質不足，需要 " + required + " 份。"));
            return;
        }

        double chance = plugin.getConfig().getDouble("progression.first-transformation-success", 1.0);
        if (random.nextDouble() > chance) {
            profiles.spendEssence(player, 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 160, 0, true, true));
            player.damage(4.0);
            player.sendMessage(EvilIslandPlugin.message("妖質產生排斥，易質失敗並損失一份妖質。", NamedTextColor.RED));
            return;
        }

        profiles.spendEssence(player, required);
        profiles.setTransformations(player, 1);
        profiles.setObjective(player, ObjectiveStage.DEFEAT_XINGTIAN);
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        player.setHealth(Math.min(maxHealth == null ? 20.0 : maxHealth.getValue(), player.getHealth() + 8.0));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.8f);
        player.showTitle(Title.title(
                Component.text("第一次易質完成", NamedTextColor.GOLD),
                Component.text("炁息容量提升；低道息區將使身體衰弱", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000))
        ));
        encounters.spawnXingtian(player);
    }

    public String objectiveText(Player player) {
        if (!profiles.isEnlisted(player)) {
            return profiles.isMeasured(player)
                    ? "返回聚炁鏡庭，完成炁訣存想定型。"
                    : "前往新城聚炁鏡庭接受炁息測定。";
        }
        int remains = items.countRemains(player.getInventory());
        int required = requiredEssence();
        if (profiles.objective(player) == ObjectiveStage.REPORT_PATROL) {
            return "前往新城東門，向撼山巡防員報到。";
        }
        if (profiles.transformations(player) == 0) {
            if (profiles.essence(player) >= required) {
                return "在聚炁鏡旁完成第一次易質。";
            }
            if (remains > 0) {
                return "返回新城煉化臺；遺骸 " + remains + "，妖質 " + profiles.essence(player) + "/" + required + "。";
            }
            return "前往東境高道息區取得鑿齒遺骸；妖質 " + profiles.essence(player) + "/" + required + "。";
        }
        if (profiles.objective(player) == ObjectiveStage.COMPLETE) {
            return "東境巡防完成；新城防線暫時穩定。";
        }
        return "阻止高道息荒原上的刑天統領。";
    }

    private int requiredEssence() {
        return plugin.getConfig().getInt("progression.first-transformation-essence", 3);
    }
}
