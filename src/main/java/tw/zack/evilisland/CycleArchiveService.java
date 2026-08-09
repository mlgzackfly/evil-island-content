package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import tw.zack.evilisland.model.BossVariant;
import tw.zack.evilisland.model.CycleHistorySnapshot;
import tw.zack.evilisland.persistence.CycleArchiveRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CycleArchiveService implements Listener {
    private final EvilIslandPlugin plugin;
    private final CycleArchiveRepository repository;
    private final CampaignService campaign;
    private final DaoFieldService daoFields;
    private final NamespacedKey actorKey;
    private UUID historianId;
    private UUID signId;

    public CycleArchiveService(EvilIslandPlugin plugin, CycleArchiveRepository repository,
                               CampaignService campaign, DaoFieldService daoFields) {
        this.plugin = plugin;
        this.repository = repository;
        this.campaign = campaign;
        this.daoFields = daoFields;
        this.actorKey = new NamespacedKey(plugin, "cycle_archive_actor");
    }

    public void load() {
        Bukkit.getScheduler().runTask(plugin, this::refreshScene);
    }

    public void recordBoss(BossVariant variant) {
        if (variant == null) return;
        repository.recordBoss(campaign.state().cycle(), variant, System.currentTimeMillis());
    }

    public void open(Player player) {
        ArchiveHolder holder = new ArchiveHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("新城輪次史館"));
        holder.inventory = inventory;
        BossVariant current = campaign.bossVariant();
        inventory.setItem(4, item(Material.BLACK_BANNER, "本輪首領預告：" + current.display(),
                List.of("依目前共同方針形成。", current.behavior(), stats(current))));
        List<CycleHistorySnapshot> history = repository.recent(5);
        int[] slots = {10, 12, 14, 16, 22};
        for (int index = 0; index < history.size() && index < slots.length; index++) {
            CycleHistorySnapshot cycle = history.get(index);
            String boss = cycle.bossVariant() == null ? "本輪未留下迎戰紀錄"
                    : cycle.bossVariant().display() + "・" + cycle.bossVariant().behavior();
            inventory.setItem(slots[index], item(Material.WRITTEN_BOOK,
                    "第 " + cycle.cycle() + " 輪・" + cycle.ending(), List.of(cycle.summary(), boss)));
        }
        if (history.isEmpty()) inventory.setItem(13, item(Material.BOOK, "尚無完成輪次",
                List.of("第一輪結算後，世界成果會保存於此。")));
        player.openInventory(inventory);
    }

    public void tick() {
        refreshScene();
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !"historian".equals(event.getRightClicked()
                .getPersistentDataContainer().get(actorKey, PersistentDataType.STRING))) return;
        event.setCancelled(true);
        open(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ArchiveHolder) event.setCancelled(true);
    }

    public int runSelfTest() {
        int checks = 0;
        if (BossVariant.values().length == 3) checks++;
        if (java.util.Arrays.stream(BossVariant.values()).map(BossVariant::behavior).distinct().count() == 3) checks++;
        if (BossVariant.SIEGE_BREAKER.slamRadiusMultiplier() > 1.0) checks++;
        if (BossVariant.SUPPLY_RAIDER.commandRadiusMultiplier() > 1.0) checks++;
        if (BossVariant.HUNTED_COMMANDER.chargeCooldownMultiplier() < 1.0) checks++;
        Entity historian = historianId == null ? null : Bukkit.getEntity(historianId);
        Entity sign = signId == null ? null : Bukkit.getEntity(signId);
        if (historian instanceof Villager && historian.isValid() && sign instanceof TextDisplay && sign.isValid()) checks++;
        return checks;
    }

    private void refreshScene() {
        Location center = daoFields.cityCenter();
        if (center == null || center.getWorld() == null) return;
        Villager historian = null;
        TextDisplay sign = null;
        for (Entity entity : center.getWorld().getEntities()) {
            String role = entity.getPersistentDataContainer().get(actorKey, PersistentDataType.STRING);
            if (role == null) continue;
            if (role.equals("historian") && entity instanceof Villager candidate && historian == null) {
                historian = candidate;
            } else if (role.equals("sign") && entity instanceof TextDisplay candidate && sign == null) {
                sign = candidate;
            } else entity.remove();
        }
        Location position = ground(center.clone().add(0, 0, -20));
        if (historian == null) {
            historian = position.getWorld().spawn(position, Villager.class, actor -> {
                actor.customName(Component.text("輪次史官", NamedTextColor.GOLD));
                actor.setCustomNameVisible(true);
                actor.setAI(false);
                actor.setInvulnerable(true);
                actor.setPersistent(true);
                actor.setRemoveWhenFarAway(false);
                actor.getPersistentDataContainer().set(actorKey, PersistentDataType.STRING, "historian");
            });
        } else historian.teleport(position);
        if (sign == null) {
            sign = position.getWorld().spawn(position.clone().add(0, 2.7, 0), TextDisplay.class, display -> {
                display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                display.setPersistent(true);
                display.getPersistentDataContainer().set(actorKey, PersistentDataType.STRING, "sign");
            });
        }
        sign.text(Component.text("新城輪次史館\n本輪：" + campaign.bossVariant().display(), NamedTextColor.GOLD));
        historianId = historian.getUniqueId();
        signId = sign.getUniqueId();
    }

    private Location ground(Location location) {
        int y = location.getWorld().getHighestBlockYAt(location.getBlockX(), location.getBlockZ()) + 1;
        return new Location(location.getWorld(), location.getBlockX() + 0.5, y, location.getBlockZ() + 0.5);
    }

    private String stats(BossVariant variant) {
        return "生命 ×" + variant.healthMultiplier() + "　傷害 ×" + variant.damageMultiplier()
                + "　護衛 +" + variant.extraZaochi();
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW));
        meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private static final class ArchiveHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
}
