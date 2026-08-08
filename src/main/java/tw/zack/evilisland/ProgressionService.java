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
import tw.zack.evilisland.model.EssenceSample;
import tw.zack.evilisland.model.GrowthRules;

import java.time.Duration;
import java.util.List;

public final class ProgressionService implements Listener {
    private final EvilIslandPlugin plugin;
    private final PlayerProfileService profiles;
    private final DaoFieldService daoFields;
    private final GameItemService items;
    private final EncounterService encounters;
    private final GrowthService growth;

    public ProgressionService(EvilIslandPlugin plugin, PlayerProfileService profiles, DaoFieldService daoFields,
                              GameItemService items, EncounterService encounters, GrowthService growth) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.daoFields = daoFields;
        this.items = items;
        this.encounters = encounters;
        this.growth = growth;
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
        int capacity = growth.remainingCapacity(player);
        if (capacity <= 0) {
            player.sendMessage(EvilIslandPlugin.message("目前妖質容量已滿，需先完成下一階易質。",
                    NamedTextColor.RED));
            return;
        }
        List<EssenceSample> samples = items.consumeRemains(player.getInventory(), capacity);
        int produced = growth.addEssence(player, samples);
        if (produced <= 0) {
            player.sendMessage(EvilIslandPlugin.message(items.countRemains(player.getInventory()) > 0
                    ? "剩餘容量不足以容納這批遺骸的純度。" : "煉化臺中沒有可處理的妖物遺骸。"));
            return;
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 0.7f);
        player.sendMessage(EvilIslandPlugin.message("完成煉化，取得 " + produced + " 份妖質。現有妖質："
                + profiles.essence(player) + "/" + growth.capacity(player) + "，平均純度 "
                + String.format(java.util.Locale.ROOT, "%.1f", growth.averagePurity(player)) + "；來源："
                + growth.sourceSummary(player) + "。",
                NamedTextColor.GOLD));
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
        GrowthService.TransformationResult result = growth.transform(player);
        if (result.type() == GrowthService.ResultType.MAXIMUM_STAGE) {
            player.sendMessage(EvilIslandPlugin.message("目前三階易質已完成；後續成長改由技法與傳承展開。"));
            return;
        }
        if (result.type() == GrowthService.ResultType.INSUFFICIENT_ESSENCE) {
            player.sendMessage(EvilIslandPlugin.message("妖質不足，第 " + result.stage() + " 階需要 "
                    + result.requiredEssence() + " 份。"));
            return;
        }
        if (result.type() == GrowthService.ResultType.INSUFFICIENT_PURITY) {
            player.sendMessage(EvilIslandPlugin.message("妖質純度不足，第 " + result.stage() + " 階平均純度需達 "
                    + String.format(java.util.Locale.ROOT, "%.1f", GrowthRules.requiredPurity(result.stage()))
                    + "。", NamedTextColor.RED));
            return;
        }
        if (result.type() == GrowthService.ResultType.REJECTED) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 160, 0, true, true));
            player.damage(4.0 + result.stage());
            player.sendMessage(EvilIslandPlugin.message("妖質產生排斥，易質失敗並損失 "
                    + result.lostEssence() + " 份妖質；排斥累積 " + growth.rejection(player) + "。",
                    NamedTextColor.RED));
            return;
        }

        int stage = result.stage();
        profiles.setObjective(player, stage == 1 ? ObjectiveStage.DEFEAT_XINGTIAN : ObjectiveStage.COMPLETE);
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        player.setHealth(Math.min(maxHealth == null ? 20.0 : maxHealth.getValue(), player.getHealth() + 8.0));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.8f);
        player.showTitle(Title.title(
                Component.text("第 " + stage + " 階易質完成", NamedTextColor.GOLD),
                Component.text("妖質容量與炁息容量提升；低道息區仍會使身體衰弱", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000))
        ));
        if (stage == 1) encounters.spawnXingtian(player);
    }

    public String objectiveText(Player player) {
        if (!profiles.isEnlisted(player)) {
            return profiles.isMeasured(player)
                    ? "返回聚炁鏡庭，完成炁訣存想定型。"
                    : "前往新城聚炁鏡庭接受炁息測定。";
        }
        String mission = encounters.missionObjective(player);
        if (mission != null) {
            return mission;
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
            int next = profiles.transformations(player) + 1;
            if (next <= GrowthRules.MAX_TRANSFORMATIONS
                    && profiles.essence(player) >= GrowthRules.requiredEssence(next)) {
                return "可在聚炁鏡旁嘗試第 " + next + " 階易質；注意純度與排斥。";
            }
            return "前往新城東門查看今日輕疾巡防公告，選擇下一次出勤。";
        }
        return "阻止高道息荒原上的刑天統領。";
    }

    private int requiredEssence() {
        return GrowthRules.requiredEssence(1);
    }
}
