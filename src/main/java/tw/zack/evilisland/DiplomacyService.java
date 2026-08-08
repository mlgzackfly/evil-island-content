package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import tw.zack.evilisland.model.ContractResolution;
import tw.zack.evilisland.model.Faction;
import tw.zack.evilisland.model.FactionContract;
import tw.zack.evilisland.model.FactionContractSnapshot;
import tw.zack.evilisland.model.FactionContractState;
import tw.zack.evilisland.model.MissionContract;
import tw.zack.evilisland.model.MissionType;
import tw.zack.evilisland.model.SpeciesType;
import tw.zack.evilisland.persistence.DiplomacyRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DiplomacyService implements Listener {
    private final EvilIslandPlugin plugin;
    private final DiplomacyRepository repository;
    private final CampaignService campaign;
    private final DevelopmentService development;
    private final DaoFieldService daoFields;
    private final SpeciesService species;
    private final NamespacedKey envoyKey;
    private final NamespacedKey conflictKey;
    private FactionContractSnapshot current;
    private int loadedWeekKey;

    public DiplomacyService(EvilIslandPlugin plugin, DiplomacyRepository repository, CampaignService campaign,
                            DevelopmentService development, DaoFieldService daoFields, SpeciesService species) {
        this.plugin = plugin;
        this.repository = repository;
        this.campaign = campaign;
        this.development = development;
        this.daoFields = daoFields;
        this.species = species;
        this.envoyKey = new NamespacedKey(plugin, "faction_envoy");
        this.conflictKey = new NamespacedKey(plugin, "faction_conflict");
    }

    public void load() {
        loadCurrent();
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshScene, 180L);
    }

    public void tick() {
        FactionContract expected = FactionContract.forWeek(campaign.state().week(), development.activeRoute());
        if (weekKey() != loadedWeekKey || current == null || current.contract() != expected) {
            loadCurrent();
            refreshScene();
        } else if (findEnvoy() == null) {
            refreshScene();
        }
        resolveAbandonedConflict();
    }

    public void recordMission(MissionContract mission, Set<UUID> members, boolean fullReward) {
        ensureCurrent();
        if (!fullReward) return;
        if (current.state() == FactionContractState.RESOLVED
                && current.resolution() == ContractResolution.COOPERATE
                && current.contract().accepts(mission.missionType())) {
            awardCredit(members);
            return;
        }
        if (current.state() != FactionContractState.ACTIVE) return;
        MissionType required = current.contract().requiredType(current.progress());
        if (required != mission.missionType()) return;
        int progress = current.progress() + 1;
        FactionContractState state = progress >= current.contract().stageCount()
                ? FactionContractState.READY : FactionContractState.ACTIVE;
        current = new FactionContractSnapshot(current.cycle(), current.contract(), progress,
                ContractResolution.NONE, state, System.currentTimeMillis());
        repository.saveContract(current);
        awardCredit(members);
        Bukkit.broadcast(EvilIslandPlugin.message("異族交涉「" + current.contract().display() + "」推進至 "
                + progress + "/" + current.contract().stageCount() + "。", NamedTextColor.GOLD));
        refreshScene();
    }

    public void refreshForRoute() {
        loadCurrent();
        refreshScene();
    }

    public void openContract(Player player) {
        ensureCurrent();
        ContractHolder holder = new ContractHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("本週異族交涉"));
        holder.inventory = inventory;
        MissionType required = current.contract().requiredType(current.progress());
        List<String> status = new ArrayList<>();
        status.add("進度：" + current.progress() + "/" + current.contract().stageCount());
        status.add(current.state() == FactionContractState.ACTIVE && required != null
                ? "目前需要完成一次「" + required.display() + "」任務。"
                : "決議狀態：" + current.resolution().display());
        status.add("關係：" + current.contract().faction().relation(
                development.state().reputation(current.contract().faction())));
        inventory.setItem(4, item(current.contract().icon(), current.contract().display(),
                NamedTextColor.GOLD, status));

        if (current.state() == FactionContractState.READY) {
            inventory.setItem(11, item(Material.LIME_BANNER, "締結合作", NamedTextColor.GREEN,
                    List.of("消耗公共庫存 4 份" + current.contract().cooperationResource().display() + "。",
                            "改善關係並開放本週限定物資。")));
            inventory.setItem(13, item(Material.GRAY_BANNER, "保持距離", NamedTextColor.GRAY,
                    List.of("不消耗物資，也不開放限定物資。")));
            if (current.contract() == FactionContract.QUANRONG_HUNT) {
                inventory.setItem(15, item(Material.IRON_SWORD, "驅離獵隊", NamedTextColor.RED,
                        List.of("在城外處理一場可避免的衝突。", "關係將下降，不提供永久戰力。")));
            }
        } else if (current.state() == FactionContractState.RESOLVED
                && current.resolution() == ContractResolution.COOPERATE) {
            int stock = repository.stock(current.contract().faction(), weekKey(), initialStock());
            int credit = repository.credit(player.getUniqueId(), current.contract().faction(), weekKey());
            inventory.setItem(13, item(shopMaterial(current.contract().faction()), "本週互助物資",
                    NamedTextColor.AQUA, List.of("個人額度：" + credit, "全服剩餘：" + stock,
                            "點擊消耗 1 點額度取得一份。")));
        } else if (current.state() == FactionContractState.CONFLICT) {
            inventory.setItem(13, item(Material.CROSSBOW, "城外衝突尚未結束", NamedTextColor.RED,
                    List.of("擊退所有有標記的犬戎獵隊後才會結算。")));
        } else {
            inventory.setItem(13, item(Material.WRITABLE_BOOK, "先完成交涉前置", NamedTextColor.YELLOW,
                    List.of(required == null ? "等待本週決議。" : "從撼山巡防員選擇「"
                            + required.display() + "」類型任務。")));
        }
        player.openInventory(inventory);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnvoyInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !event.getRightClicked().getPersistentDataContainer().has(envoyKey, PersistentDataType.STRING)) {
            return;
        }
        event.setCancelled(true);
        openContract(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ContractHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        ensureCurrent();
        if (current.state() == FactionContractState.READY) {
            if (event.getRawSlot() == 11) resolve(player, ContractResolution.COOPERATE);
            else if (event.getRawSlot() == 13) resolve(player, ContractResolution.WITHDRAW);
            else if (event.getRawSlot() == 15 && current.contract() == FactionContract.QUANRONG_HUNT) {
                beginConflict(player);
            }
        } else if (current.state() == FactionContractState.RESOLVED
                && current.resolution() == ContractResolution.COOPERATE && event.getRawSlot() == 13) {
            purchase(player);
        }
    }

    @EventHandler
    public void onConflictDeath(EntityDeathEvent event) {
        String marker = event.getEntity().getPersistentDataContainer().get(conflictKey, PersistentDataType.STRING);
        if (marker == null || current == null || !marker.equals(String.valueOf(current.cycle()))) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (current.state() != FactionContractState.CONFLICT || remainingConflictEnemies() > 0) return;
            current = new FactionContractSnapshot(current.cycle(), current.contract(), current.progress(),
                    ContractResolution.CONFLICT, FactionContractState.RESOLVED, System.currentTimeMillis());
            repository.saveContract(current);
            development.adjustReputation(Faction.QUANRONG, -8);
            development.addResource(current.contract().cooperationResource(), 2);
            Bukkit.broadcast(EvilIslandPlugin.message("犬戎獵場衝突結束；新城取得少量戰利物資，但關係惡化。",
                    NamedTextColor.RED));
            refreshScene();
        });
    }

    private void resolve(Player player, ContractResolution resolution) {
        if (current.state() != FactionContractState.READY) return;
        if (resolution == ContractResolution.COOPERATE && !development.spendResources(
                Map.of(current.contract().cooperationResource(), 4))) {
            player.sendMessage(EvilIslandPlugin.message("公共庫存不足，尚需 4 份"
                    + current.contract().cooperationResource().display() + "。", NamedTextColor.RED));
            return;
        }
        current = new FactionContractSnapshot(current.cycle(), current.contract(), current.progress(), resolution,
                FactionContractState.RESOLVED, System.currentTimeMillis());
        repository.saveContract(current);
        if (resolution == ContractResolution.COOPERATE) {
            development.adjustReputation(current.contract().faction(), 10);
            repository.stock(current.contract().faction(), weekKey(), initialStock());
            Bukkit.broadcast(EvilIslandPlugin.message(player.getName() + "代表新城與"
                    + current.contract().faction().display() + "完成本週互助決議。", NamedTextColor.GREEN));
        } else {
            development.adjustReputation(current.contract().faction(), -1);
            player.sendMessage(EvilIslandPlugin.message("新城選擇保持距離，本週交涉結束。"));
        }
        refreshScene();
        openContract(player);
    }

    private void beginConflict(Player player) {
        current = new FactionContractSnapshot(current.cycle(), current.contract(), current.progress(),
                ContractResolution.CONFLICT, FactionContractState.CONFLICT, System.currentTimeMillis());
        repository.saveContract(current);
        Location base = daoFields.patrolCenter(player.getWorld());
        if (base == null) base = player.getLocation().clone().add(18, 0, 0);
        for (int index = 0; index < 3; index++) {
            LivingEntity enemy = species.spawnEcology(SpeciesType.QUANRONG_HUNTER,
                    ground(base.clone().add(index * 3 - 3, 0, index % 2 == 0 ? 3 : -3)));
            enemy.getPersistentDataContainer().set(conflictKey, PersistentDataType.STRING,
                    String.valueOf(current.cycle()));
        }
        LivingEntity alpha = species.spawnEcology(SpeciesType.QUANRONG_ALPHA, ground(base.clone().add(5, 0, 0)));
        alpha.getPersistentDataContainer().set(conflictKey, PersistentDataType.STRING,
                String.valueOf(current.cycle()));
        player.closeInventory();
        Bukkit.broadcast(EvilIslandPlugin.message("犬戎獵隊拒絕撤離，衝突在新城外圍爆發。", NamedTextColor.RED));
        refreshScene();
    }

    private void purchase(Player player) {
        Faction faction = current.contract().faction();
        repository.stock(faction, weekKey(), initialStock());
        if (!repository.purchase(player.getUniqueId(), faction, weekKey(), 1)) {
            player.sendMessage(EvilIslandPlugin.message("個人額度不足，或本週全服庫存已領完。", NamedTextColor.RED));
            openContract(player);
            return;
        }
        ItemStack reward = shopReward(faction);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(reward);
        overflow.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        player.sendMessage(EvilIslandPlugin.message("取得" + faction.display() + "提供的本週互助物資。",
                NamedTextColor.GREEN));
        openContract(player);
    }

    private void loadCurrent() {
        FactionContract contract = FactionContract.forWeek(campaign.state().week(), development.activeRoute());
        current = repository.loadContract(campaign.state().cycle(), contract.faction()).orElseGet(() -> {
            FactionContractSnapshot initial = FactionContractSnapshot.initial(campaign.state().cycle(), contract,
                    System.currentTimeMillis());
            repository.saveContract(initial);
            return initial;
        });
        loadedWeekKey = weekKey();
    }

    private void ensureCurrent() {
        FactionContract expected = FactionContract.forWeek(campaign.state().week(), development.activeRoute());
        if (current == null || weekKey() != loadedWeekKey || current.contract() != expected) loadCurrent();
    }

    private void awardCredit(Set<UUID> members) {
        int maximum = Math.max(1, plugin.getConfig().getInt("diplomacy.personal-credit-cap", 3));
        for (UUID member : members) repository.addCredit(member, current.contract().faction(), weekKey(),
                maximum, System.currentTimeMillis());
    }

    private void resolveAbandonedConflict() {
        if (current == null || current.state() != FactionContractState.CONFLICT
                || remainingConflictEnemies() > 0) return;
        current = new FactionContractSnapshot(current.cycle(), current.contract(), current.progress(),
                ContractResolution.CONFLICT, FactionContractState.RESOLVED, System.currentTimeMillis());
        repository.saveContract(current);
        development.adjustReputation(Faction.QUANRONG, -8);
        development.addResource(current.contract().cooperationResource(), 2);
        Bukkit.broadcast(EvilIslandPlugin.message("犬戎獵場衝突已結束；關係惡化，少量物資送入公共庫存。",
                NamedTextColor.RED));
        refreshScene();
    }

    private void refreshScene() {
        Location post = daoFields.guardPost();
        if (post == null || post.getWorld() == null) return;
        for (Entity entity : post.getWorld().getNearbyEntities(post, 52, 30, 52)) {
            if (entity.getPersistentDataContainer().has(envoyKey, PersistentDataType.STRING)) entity.remove();
        }
        ensureCurrent();
        Location location = ground(post.clone().add(0, 0, 7));
        Mob envoy = spawnEnvoy(current.contract().faction(), location,
                current.contract().faction().display() + "使者　" + envoySuffix(),
                current.state() == FactionContractState.READY ? NamedTextColor.YELLOW : NamedTextColor.AQUA);
        envoy.getPersistentDataContainer().set(envoyKey, PersistentDataType.STRING, current.contract().faction().id());
    }

    public Mob spawnAcceptanceEnvoy(Faction faction, Location location) {
        return spawnEnvoy(faction, ground(location), "驗收使者　" + faction.display(), NamedTextColor.LIGHT_PURPLE);
    }

    private Mob spawnEnvoy(Faction faction, Location location, String name, NamedTextColor color) {
        Mob envoy = switch (faction) {
            case QUANRONG -> location.getWorld().spawn(location, Pillager.class);
            case MAO -> location.getWorld().spawn(location, Villager.class);
            case NAJIN -> location.getWorld().spawn(location, WanderingTrader.class);
            case QIULONG -> location.getWorld().spawn(location, Drowned.class);
            default -> location.getWorld().spawn(location, Villager.class);
        };
        envoy.customName(Component.text(name, color));
        envoy.setCustomNameVisible(true);
        envoy.setAI(false);
        envoy.setInvulnerable(true);
        envoy.setCollidable(false);
        envoy.setPersistent(true);
        envoy.setRemoveWhenFarAway(false);
        if (envoy instanceof Zombie zombie) zombie.setShouldBurnInDay(false);
        return envoy;
    }

    private Entity findEnvoy() {
        Location post = daoFields.guardPost();
        if (post == null || post.getWorld() == null) return null;
        return post.getWorld().getNearbyEntities(post, 52, 30, 52).stream()
                .filter(entity -> entity.getPersistentDataContainer().has(envoyKey, PersistentDataType.STRING))
                .findFirst().orElse(null);
    }

    private int remainingConflictEnemies() {
        World world = daoFields.cityCenter() == null ? null : daoFields.cityCenter().getWorld();
        if (world == null) return 0;
        String cycle = String.valueOf(current.cycle());
        return (int) world.getEntities().stream().filter(entity -> !entity.isDead() && cycle.equals(
                entity.getPersistentDataContainer().get(conflictKey, PersistentDataType.STRING))).count();
    }

    private String envoySuffix() {
        return switch (current.state()) {
            case ACTIVE -> "交涉中";
            case READY -> "等待決議";
            case CONFLICT -> "衝突中";
            case RESOLVED -> current.resolution().display();
        };
    }

    private int weekKey() {
        return campaign.state().cycle() * 10 + campaign.state().week();
    }

    private int initialStock() {
        return Math.max(1, plugin.getConfig().getInt("diplomacy.weekly-stock", 4));
    }

    private Material shopMaterial(Faction faction) {
        return switch (faction) {
            case QUANRONG -> Material.COOKED_BEEF;
            case MAO -> Material.HONEY_BOTTLE;
            case NAJIN -> Material.COPPER_INGOT;
            case QIULONG -> Material.PRISMARINE_CRYSTALS;
            default -> Material.BREAD;
        };
    }

    private ItemStack shopReward(Faction faction) {
        int amount = switch (faction) {
            case QUANRONG -> 4;
            case MAO -> 2;
            case NAJIN -> 6;
            case QIULONG -> 3;
            default -> 2;
        };
        return new ItemStack(shopMaterial(faction), amount);
    }

    private Location ground(Location location) {
        World world = location.getWorld();
        int y = world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ()) + 1;
        return new Location(world, location.getBlockX() + 0.5, y, location.getBlockZ() + 0.5);
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color));
        meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    public int runSelfTest() {
        int checks = 0;
        if (FactionContract.values().length == 4) checks++;
        if (FactionContract.forWeek(1, development.activeRoute()) != null) checks++;
        if (current != null && current.contract().stageCount() == 2) checks++;
        if (initialStock() > 0) checks++;
        return checks;
    }

    private static final class ContractHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
}
