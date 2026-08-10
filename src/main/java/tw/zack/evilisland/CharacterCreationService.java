package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tw.zack.evilisland.model.Formula;
import tw.zack.evilisland.model.FormulaPath;
import tw.zack.evilisland.model.QiTendency;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class CharacterCreationService implements Listener {
    private final EvilIslandPlugin plugin;
    private final PlayerProfileService profiles;
    private final DaoFieldService daoFields;
    private Consumer<Player> formulaLockedListener = ignored -> { };

    public CharacterCreationService(EvilIslandPlugin plugin, PlayerProfileService profiles, DaoFieldService daoFields) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.daoFields = daoFields;
    }

    public void setFormulaLockedListener(Consumer<Player> listener) {
        formulaLockedListener = listener == null ? ignored -> { } : listener;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!profiles.isMeasured(player)) {
                player.sendMessage(EvilIslandPlugin.message("前往新城西南側聚炁鏡庭，右鍵中央聚炁鏡接受炁息測定。", NamedTextColor.YELLOW));
            } else if (!profiles.isFormulaLocked(player)) {
                player.sendMessage(EvilIslandPlugin.message("你的先天傾向已測定；再次右鍵聚炁鏡，決定尚未定型的炁訣路線。", NamedTextColor.YELLOW));
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMirrorInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null
                || !event.getAction().isRightClick() || !daoFields.isMirror(event.getClickedBlock())
                || profiles.isFormulaLocked(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!profiles.isMeasured(player)) {
            QiTendency tendency = profiles.measureTendency(player);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f,
                    tendency == QiTendency.OUTWARD ? 1.35f : 0.8f);
            player.showTitle(Title.title(
                    Component.text(tendency.display() + "型", NamedTextColor.AQUA),
                    Component.text(tendency == QiTendency.OUTWARD
                            ? "炁息自然外發，感應範圍較廣" : "炁息自然內斂，護體運用較穩", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(3000), Duration.ofMillis(700))
            ));
            player.sendMessage(EvilIslandPlugin.message("測定結果已永久記錄，先天傾向不能修改。再次右鍵聚炁鏡以選擇存想路線。", NamedTextColor.GREEN));
            return;
        }
        openPathMenu(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) {
            return;
        }

        if (holder instanceof PathMenuHolder pathMenu) {
            FormulaPath path = pathMenu.options.get(event.getRawSlot());
            if (path == null) {
                return;
            }
            if (path.isMixed()) {
                openRatioMenu(player, path.primary(), path.secondary());
            } else {
                openConfirmation(player, path);
            }
        } else if (holder instanceof RatioMenuHolder ratioMenu) {
            FormulaPath path = ratioMenu.options.get(event.getRawSlot());
            if (path != null) {
                openConfirmation(player, path);
            } else if (event.getRawSlot() == 22) {
                openPathMenu(player);
            }
        } else if (holder instanceof ConfirmationHolder confirmation) {
            if (event.getRawSlot() == 11) {
                openPathMenu(player);
            } else if (event.getRawSlot() == 15) {
                confirmFormula(player, confirmation.path);
            }
        }
    }

    private void openPathMenu(Player player) {
        PathMenuHolder holder = new PathMenuHolder();
        Inventory inventory = createInventory(holder, 27, "選擇炁訣存想路線");
        addOption(inventory, holder.options, 10, FormulaPath.pure(Formula.BAO), Material.FIRE_CHARGE);
        addOption(inventory, holder.options, 12, FormulaPath.pure(Formula.QING), Material.FEATHER);
        addOption(inventory, holder.options, 14, FormulaPath.pure(Formula.ROU), Material.SLIME_BALL);
        addOption(inventory, holder.options, 16, FormulaPath.pure(Formula.NING), Material.SHIELD);
        addOption(inventory, holder.options, 20, FormulaPath.mixed(Formula.BAO, Formula.QING, 50), Material.BLAZE_POWDER);
        addOption(inventory, holder.options, 22, FormulaPath.mixed(Formula.QING, Formula.ROU, 50), Material.PHANTOM_MEMBRANE);
        addOption(inventory, holder.options, 24, FormulaPath.mixed(Formula.ROU, Formula.NING, 50), Material.SCUTE);
        player.openInventory(inventory);
    }

    private void openRatioMenu(Player player, Formula first, Formula second) {
        RatioMenuHolder holder = new RatioMenuHolder();
        Inventory inventory = createInventory(holder, 27, "決定雙修存想比例");
        addOption(inventory, holder.options, 11, FormulaPath.mixed(first, second, 70), Material.AMETHYST_SHARD);
        addOption(inventory, holder.options, 13, FormulaPath.mixed(first, second, 50), Material.AMETHYST_SHARD);
        addOption(inventory, holder.options, 15, FormulaPath.mixed(first, second, 30), Material.AMETHYST_SHARD);
        inventory.setItem(22, menuItem(Material.RED_CONCRETE, "返回重新選擇", NamedTextColor.RED,
                List.of("尚未定型，可以返回上一頁。")));
        player.openInventory(inventory);
    }

    private void openConfirmation(Player player, FormulaPath path) {
        ConfirmationHolder holder = new ConfirmationHolder(path);
        Inventory inventory = createInventory(holder, 27, "確認炁訣定型");
        inventory.setItem(13, pathItem(path, Material.NETHER_STAR));
        inventory.setItem(11, menuItem(Material.RED_CONCRETE, "返回", NamedTextColor.RED,
                List.of("返回選擇其他存想路線。")));
        inventory.setItem(15, menuItem(Material.LIME_CONCRETE, "確認定型", NamedTextColor.GREEN,
                List.of("定型後不能切換或重新分配比例。")));
        player.openInventory(inventory);
    }

    private void confirmFormula(Player player, FormulaPath path) {
        player.closeInventory();
        if (!profiles.lockFormula(player, path)) {
            player.sendMessage(EvilIslandPlugin.message("炁訣已經定型，不能再次修改。", NamedTextColor.RED));
            return;
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.05f);
        player.showTitle(Title.title(
                Component.text(path.display() + "定型", NamedTextColor.GOLD),
                Component.text("此後修煉只會深化這條路線", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(3000), Duration.ofMillis(700))
        ));
        player.sendMessage(EvilIslandPlugin.message("炁訣定型完成。前往新城東門，右鍵撼山巡防員接受第一次巡防。", NamedTextColor.GREEN));
        formulaLockedListener.accept(player);
    }

    private Inventory createInventory(MenuHolder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, Component.text(title));
        holder.inventory = inventory;
        return inventory;
    }

    private void addOption(Inventory inventory, Map<Integer, FormulaPath> options, int slot,
                           FormulaPath path, Material material) {
        options.put(slot, path);
        inventory.setItem(slot, pathItem(path, material));
    }

    private ItemStack pathItem(FormulaPath path, Material material) {
        String detail = path.isMixed()
                ? path.primary().description() + "／" + path.secondary().description()
                : path.primary().description();
        return menuItem(material, path.display(), NamedTextColor.AQUA,
                List.of(detail, path.isMixed() ? "選取後仍需決定雙修比例。" : "選取後進入最終確認。"));
    }

    private ItemStack menuItem(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color));
        meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private abstract static class MenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class PathMenuHolder extends MenuHolder {
        private final Map<Integer, FormulaPath> options = new HashMap<>();
    }

    private static final class RatioMenuHolder extends MenuHolder {
        private final Map<Integer, FormulaPath> options = new HashMap<>();
    }

    private static final class ConfirmationHolder extends MenuHolder {
        private final FormulaPath path;

        private ConfirmationHolder(FormulaPath path) {
            this.path = path;
        }
    }
}
