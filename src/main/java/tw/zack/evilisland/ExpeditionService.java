package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import tw.zack.evilisland.model.CompanionOrder;
import tw.zack.evilisland.model.ExpeditionOperation;
import tw.zack.evilisland.model.ExpeditionOutcome;
import tw.zack.evilisland.model.ExpeditionPhase;
import tw.zack.evilisland.model.ExpeditionRoute;
import tw.zack.evilisland.model.ExpeditionRules;
import tw.zack.evilisland.model.ExpeditionSnapshot;
import tw.zack.evilisland.model.ExpeditionDirector;
import tw.zack.evilisland.model.ExpeditionEventResolution;
import tw.zack.evilisland.model.ExpeditionKit;
import tw.zack.evilisland.model.ExpeditionRouteEvent;
import tw.zack.evilisland.model.ExpeditionRunStateSnapshot;
import tw.zack.evilisland.model.ExpeditionRegionRules;
import tw.zack.evilisland.model.ExplorationSite;
import tw.zack.evilisland.model.NpcRole;
import tw.zack.evilisland.model.SpeciesType;
import tw.zack.evilisland.persistence.ExpeditionRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ExpeditionService implements Listener {
    private static final int BOTH_APPROACH_POINTS = 0b11;
    private static final int BOTH_OBJECTIVES = 0b11;

    private final EvilIslandPlugin plugin;
    private final ExpeditionRepository repository;
    private final CampaignService campaign;
    private final PlayerProfileService profiles;
    private final WeaponService weapons;
    private final SpeciesService species;
    private final CompanionService companions;
    private final EncounterService encounters;
    private final DevelopmentService development;
    private final RegionControlService regionControl;
    private final ExpeditionRewardService rewards;
    private final NamespacedKey expeditionKey;
    private final NamespacedKey actorKey;
    private final NamespacedKey sessionKey;
    private final NamespacedKey toolKey;
    private final Map<UUID, RuntimeExpedition> expeditions = new HashMap<>();
    private final Map<UUID, UUID> expeditionByMember = new HashMap<>();
    private final Map<UUID, ExpeditionRoute> selectedRoutes = new HashMap<>();
    private final Map<UUID, ExplorationSite> selectedSites = new HashMap<>();
    private final Map<UUID, Integer> selectedKits = new HashMap<>();
    private final Map<UUID, PendingInvite> pendingInvites = new HashMap<>();
    private long lastActionBarAt;

    public ExpeditionService(EvilIslandPlugin plugin, ExpeditionRepository repository, CampaignService campaign,
                             PlayerProfileService profiles, WeaponService weapons, SpeciesService species,
                             CompanionService companions, EncounterService encounters, DevelopmentService development,
                             RegionControlService regionControl) {
        this.plugin = plugin;
        this.repository = repository;
        this.campaign = campaign;
        this.profiles = profiles;
        this.weapons = weapons;
        this.species = species;
        this.companions = companions;
        this.encounters = encounters;
        this.development = development;
        this.regionControl = regionControl;
        this.rewards = new ExpeditionRewardService(plugin, repository, development, regionControl);
        expeditionKey = new NamespacedKey(plugin, "deep_expedition");
        actorKey = new NamespacedKey(plugin, "deep_expedition_actor");
        sessionKey = new NamespacedKey(plugin, "patrol_session");
        toolKey = new NamespacedKey(plugin, "expedition_order_tool");
    }

    public void load() {
        expeditions.clear();
        expeditionByMember.clear();
        for (ExpeditionSnapshot snapshot : repository.loadActive()) {
            RuntimeExpedition expedition = new RuntimeExpedition(snapshot);
            repository.state(snapshot.id()).ifPresent(expedition::loadState);
            expeditions.put(expedition.id, expedition);
            expeditionByMember.put(expedition.leader, expedition.id);
            if (expedition.partner != null) expeditionByMember.put(expedition.partner, expedition.id);
        }
        rewards.load();
        plugin.getLogger().info("Recovered " + expeditions.size() + " active deep expeditions.");
    }

    public void recover(World world) {
        for (Entity entity : world.getEntities()) recoverEntity(entity);
        for (RuntimeExpedition expedition : expeditions.values()) {
            if (expedition.world.equals(world.getName())) ensureStage(expedition);
        }
    }

    public boolean isActive(UUID playerId) {
        return expeditionByMember.containsKey(playerId);
    }

    public boolean isExpeditionEnemy(Entity entity) {
        return entity != null && "enemy".equals(entity.getPersistentDataContainer()
                .get(actorKey, PersistentDataType.STRING)) && expeditionId(entity) != null;
    }

    public boolean canTarget(Entity enemy, LivingEntity target) {
        UUID id = expeditionId(enemy);
        RuntimeExpedition expedition = id == null ? null : expeditions.get(id);
        if (expedition == null) return false;
        if (target instanceof Player player) return expedition.member(player.getUniqueId());
        return companions.isCombatReady(target) && id.equals(companions.sessionId(target));
    }

    public void openBoard(Player player) {
        openBoard(player, ExplorationSite.EASTERN_ROUTE);
    }

    public void openBoard(Player player, ExplorationSite site) {
        if (!canStart(player, true)) return;
        MenuHolder holder = new MenuHolder(MenuType.ROUTE, null, site, -1);
        Inventory inventory = createMenu(holder, ExpeditionRegionRules.boardTitle(site));
        inventory.setItem(4, item(Material.RECOVERY_COMPASS, site.display() + "現況", NamedTextColor.AQUA,
                List.of("每條路線提供不同風險；本輪行動依路線固定，失敗不會重抽。",
                        "遠征進度會保存，可主動帶回部分成果。")));
        int[] slots = {11, 13, 15};
        for (int index = 0; index < ExpeditionRoute.values().length; index++) {
            ExpeditionRoute route = ExpeditionRoute.values()[index];
            ExpeditionOperation operation = operationFor(site, route);
            boolean occupied = expeditions.values().stream().anyMatch(active -> active.site == site
                    && active.route == route);
            boolean rewardAvailable = repository.weeklyRewardAvailable(site, route,
                    campaign.state().cycle(), campaign.state().week());
            inventory.setItem(slots[index], item(occupied ? Material.BARRIER
                            : ExpeditionRegionRules.routeIcon(site, route),
                    ExpeditionRegionRules.routeDisplay(site, route),
                    occupied ? NamedTextColor.GRAY : NamedTextColor.GOLD,
                    List.of(ExpeditionRegionRules.routeDescription(site, route), "行動：" + operation.display(),
                            operation.description(),
                            rewardAvailable ? "本週高價值成果尚未取得。" : "本週成果已取得；重玩只保留練習紀錄。",
                            occupied ? "此路線已有隊伍執行遠征。" : "點擊選擇編組方式。")));
        }
        player.openInventory(inventory);
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (PendingInvite invite : List.copyOf(pendingInvites.values())) {
            if (now > invite.expiresAt) pendingInvites.remove(invite.target);
        }
        for (RuntimeExpedition expedition : List.copyOf(expeditions.values())) {
            if (!expedition.phase.running()) continue;
            expedition.actorIds.values().removeIf(id -> Bukkit.getEntity(id) == null);
            expedition.enemyIds.removeIf(id -> Bukkit.getEntity(id) == null);
            if (now - expedition.updatedAt > Math.max(3_600_000L,
                    plugin.getConfig().getLong("expeditions.abandon-after-ms", 259_200_000L))) {
                resolve(expedition, ExpeditionOutcome.ABANDONED);
                continue;
            }
            if (expedition.phase == ExpeditionPhase.OBJECTIVE && expedition.objectiveMask != 0
                    && expedition.objectiveMask != BOTH_OBJECTIVES && now > expedition.objectiveDeadline) {
                expedition.objectiveMask = 0;
                expedition.firstActivator = null;
                expedition.objectiveDeadline = 0L;
                expedition.updatedAt = now;
                save(expedition);
                tell(expedition, "兩處目標未能同步，必須重新執行。", NamedTextColor.RED);
            }
            if (expedition.phase == ExpeditionPhase.EXTRACTION
                    && ExpeditionRegionRules.timedExtraction(expedition.site, expedition.operation)
                    && expedition.objectiveDeadline > 0L && now > expedition.objectiveDeadline) {
                tell(expedition, expedition.site == ExplorationSite.DRAGON_COAST
                        ? "潮路已經封閉；隊伍只能帶回部分觀測成果。"
                        : "傷員無法繼續等待；隊伍只能帶回部分成果。", NamedTextColor.RED);
                resolve(expedition, ExpeditionOutcome.PARTIAL);
                continue;
            }
            ensureStage(expedition);
            directCompanion(expedition);
        }
        if (now - lastActionBarAt >= 2_000L) {
            lastActionBarAt = now;
            for (RuntimeExpedition expedition : expeditions.values()) showProgress(expedition);
        }
    }

    public void flush() {
        for (RuntimeExpedition expedition : expeditions.values()) save(expedition);
    }

    public void clearRuntimeState() {
        expeditions.clear();
        expeditionByMember.clear();
        pendingInvites.clear();
        selectedRoutes.clear();
        selectedSites.clear();
        selectedKits.clear();
        rewards.clearRuntimeState();
    }

    public int runSelfTest() {
        int checks = 0;
        if (ExpeditionRules.requiredClues(ExpeditionRoute.RIVERBED) == 3
                && ExpeditionRules.requiredClues(ExpeditionRoute.OLD_ROAD) == 2) checks++;
        if (ExpeditionRules.syncWindowMillis(ExpeditionOperation.SUPPLY_NODE_SABOTAGE, ExpeditionRoute.RIDGE)
                > ExpeditionRules.syncWindowMillis(ExpeditionOperation.SUPPLY_NODE_SABOTAGE,
                ExpeditionRoute.OLD_ROAD)) checks++;
        if (ExpeditionRules.enemyCount(ExpeditionOperation.BLOCKADE_INFILTRATION, ExpeditionRoute.RIDGE, 2, 1)
                > ExpeditionRules.enemyCount(ExpeditionOperation.CASUALTY_EVACUATION,
                ExpeditionRoute.RIVERBED, 1, 0)) checks++;
        if (ExpeditionRules.withdrawalOutcome(ExpeditionPhase.APPROACH, 0, 0) == ExpeditionOutcome.WITHDRAWN
                && ExpeditionRules.withdrawalOutcome(ExpeditionPhase.OBJECTIVE, 2, 0)
                == ExpeditionOutcome.PARTIAL) checks++;
        if (ExpeditionPhase.APPROACH.canAdvanceTo(ExpeditionPhase.INVESTIGATING)
                && !ExpeditionPhase.APPROACH.canAdvanceTo(ExpeditionPhase.ESCALATION)) checks++;
        if (ExpeditionRules.regionDelta(ExpeditionOutcome.COMPLETE, 2)
                > ExpeditionRules.regionDelta(ExpeditionOutcome.COMPLETE, 1)
                && ExpeditionRules.regionDelta(ExpeditionOutcome.ABANDONED, 1) < 0) checks++;
        int twoKits = ExpeditionKit.MEDICAL.mask() | ExpeditionKit.SCOUTING.mask();
        int threeKits = twoKits | ExpeditionKit.PROVISIONS.mask();
        if (ExpeditionDirector.validLoadout(twoKits, 2)
                && ExpeditionDirector.validLoadout(threeKits, 1)) checks++;
        if (ExpeditionDirector.event(77L, 0) != ExpeditionDirector.event(77L, 1)) checks++;
        if (ExpeditionDirector.resolve(ExpeditionRouteEvent.ENEMY_PATROL, false).alertDelta() == 1
                && ExpeditionDirector.resolve(ExpeditionRouteEvent.ENEMY_PATROL, true).alertDelta() == 0) checks++;
        if (CompanionOrder.values().length == 4 && CompanionOrder.INVESTIGATE.next() == CompanionOrder.EXECUTE) {
            checks++;
        }
        if (java.util.Arrays.stream(ExplorationSite.values()).allMatch(site ->
                java.util.Arrays.stream(ExpeditionOperation.values()).anyMatch(operation -> operation.site() == site))) {
            checks++;
        }
        if (!ExpeditionRegionRules.combatRequired(ExplorationSite.RONGXU_APPROACH)
                && ExpeditionRegionRules.combatRequired(ExplorationSite.WESTERN_TRACE)) checks++;
        if (ExpeditionRegionRules.requiredClues(ExplorationSite.UDING_WALL,
                ExpeditionOperation.CLIFF_RELAY, ExpeditionRoute.OLD_ROAD) == 3
                && ExpeditionRegionRules.requiredClues(ExplorationSite.WESTERN_TRACE,
                ExpeditionOperation.RUIN_MAPPING, ExpeditionRoute.OLD_ROAD) == 2) checks++;
        if (ExpeditionRegionRules.timedExtraction(ExplorationSite.DRAGON_COAST,
                ExpeditionOperation.TIDE_OBSERVATION)) checks++;
        if (ExpeditionRegionRules.enemy(ExplorationSite.UDING_WALL, 0) == SpeciesType.QUANRONG_HUNTER
                && ExpeditionRegionRules.enemy(ExplorationSite.DRAGON_COAST, 0) == SpeciesType.YUJIANG_RAIDER) {
            checks++;
        }
        return checks;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        int slot = event.getRawSlot();
        if (holder.type == MenuType.ROUTE) {
            ExpeditionRoute route = slot == 11 ? ExpeditionRoute.OLD_ROAD
                    : slot == 13 ? ExpeditionRoute.RIDGE : slot == 15 ? ExpeditionRoute.RIVERBED : null;
            if (route != null) openAssembly(player, holder.site, route);
        } else if (holder.type == MenuType.ASSEMBLY && holder.route != null) {
            if (slot >= 18 && slot <= 21) {
                toggleKit(player, holder.site, holder.route, ExpeditionKit.values()[slot - 18]);
                return;
            }
            if (slot == 11) {
                int kits = selectedKits.getOrDefault(player.getUniqueId(), 0);
                if (requireLoadout(player, kits, 1)) start(List.of(player), holder.site, holder.route, kits);
            }
            if (slot == 15) invitePartner(player, holder.site, holder.route);
            if (slot == 22) openBoard(player, holder.site);
        } else if (holder.type == MenuType.INVITE) {
            PendingInvite invite = pendingInvites.get(player.getUniqueId());
            if (slot == 11 && invite != null) acceptInvite(player, invite);
            if (slot == 15 || slot == 26) {
                pendingInvites.remove(player.getUniqueId());
                player.closeInventory();
            }
        } else if (holder.type == MenuType.WITHDRAW) {
            RuntimeExpedition expedition = expedition(player);
            if (slot == 11 && expedition != null) {
                ExpeditionOutcome outcome = ExpeditionRules.withdrawalOutcome(expedition.phase,
                        expedition.validClues, expedition.objectiveMask);
                resolve(expedition, outcome);
            }
            if (slot == 15 || slot == 26) player.closeInventory();
        } else if (holder.type == MenuType.COMMAND) {
            CompanionOrder order = slot == 10 ? CompanionOrder.FOLLOW : slot == 12 ? CompanionOrder.HOLD
                    : slot == 14 ? CompanionOrder.INVESTIGATE : slot == 16 ? CompanionOrder.EXECUTE : null;
            if (order != null) issueCompanionOrder(player, order);
        } else if (holder.type == MenuType.EVENT) {
            RuntimeExpedition expedition = expedition(player);
            if (expedition != null && (slot == 11 || slot == 15)) {
                resolveRouteEvent(expedition, player, holder.context, slot == 11);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onActorInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        UUID id = expeditionId(event.getRightClicked());
        String actor = event.getRightClicked().getPersistentDataContainer().get(actorKey, PersistentDataType.STRING);
        if (id == null || actor == null || actor.equals("enemy")) return;
        event.setCancelled(true);
        RuntimeExpedition expedition = expeditions.get(id);
        if (expedition == null || !expedition.member(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage(EvilIslandPlugin.message("這處遠征現場屬於其他編組。"));
            return;
        }
        interact(expedition, event.getPlayer(), actor);
    }

    @EventHandler(ignoreCancelled = true)
    public void onToolUse(PlayerInteractEvent event) {
        ItemStack used = event.getItem();
        if (!isTool(used)) return;
        event.setCancelled(true);
        RuntimeExpedition expedition = expedition(event.getPlayer());
        if (expedition == null) {
            event.getPlayer().getInventory().remove(used);
            return;
        }
        if (event.getPlayer().isSneaking()) {
            openWithdraw(event.getPlayer(), expedition);
            return;
        }
        if (expedition.partner != null) {
            event.getPlayer().sendMessage(EvilIslandPlugin.message("雙人編組需分頭互動；潛行使用指令牌可評估撤離。"));
            return;
        }
        Entity entity = expedition.companion == null ? null : Bukkit.getEntity(expedition.companion);
        if (entity == null || !companions.isCompanion(entity)) {
            ensureCompanion(expedition);
            entity = expedition.companion == null ? null : Bukkit.getEntity(expedition.companion);
        }
        if (entity == null) return;
        openCommandMenu(event.getPlayer(), expedition);
    }

    @EventHandler
    public void onEnemyDeath(EntityDeathEvent event) {
        if (!isExpeditionEnemy(event.getEntity())) return;
        UUID id = expeditionId(event.getEntity());
        RuntimeExpedition expedition = id == null ? null : expeditions.get(id);
        if (expedition == null) return;
        expedition.enemyIds.remove(event.getEntity().getUniqueId());
        if (expedition.phase != ExpeditionPhase.ESCALATION) return;
        expedition.enemiesRemaining = Math.max(0, expedition.enemiesRemaining - 1);
        expedition.updatedAt = System.currentTimeMillis();
        save(expedition);
        if (expedition.enemiesRemaining == 0) {
            advance(expedition, ExpeditionPhase.EXTRACTION);
        } else {
            tell(expedition, "敵襲尚餘 " + expedition.enemiesRemaining + " 個威脅。", NamedTextColor.YELLOW);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) recoverEntity(entity);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        RuntimeExpedition expedition = expedition(event.getPlayer());
        if (expedition == null) return;
        giveTool(event.getPlayer());
        event.getPlayer().sendMessage(EvilIslandPlugin.message("你有一場未完成的" + expedition.site.display() + "遠征："
                + expedition.operation.display() + "，目前「" + expedition.phase.display() + "」。",
                NamedTextColor.YELLOW));
    }

    private void openAssembly(Player player, ExplorationSite site, ExpeditionRoute route) {
        if (expeditions.values().stream().anyMatch(active -> active.site == site && active.route == route)) {
            player.sendMessage(EvilIslandPlugin.message("這條路線已有隊伍執行遠征。"));
            return;
        }
        selectedRoutes.put(player.getUniqueId(), route);
        selectedSites.put(player.getUniqueId(), site);
        selectedKits.putIfAbsent(player.getUniqueId(), 0);
        MenuHolder holder = new MenuHolder(MenuType.ASSEMBLY, route, site, -1);
        Inventory inventory = createMenu(holder, ExpeditionRegionRules.routeDisplay(site, route) + "編組");
        ExpeditionOperation operation = operationFor(site, route);
        inventory.setItem(4, item(operation.icon(), operation.display(), NamedTextColor.GOLD,
                List.of(operation.description(), ExpeditionRegionRules.routeDescription(site, route))));
        inventory.setItem(11, item(Material.PLAYER_HEAD, "單人與無跡", NamedTextColor.AQUA,
                List.of("需選 2 至 3 項整備；單人可多帶一項補足操作壓力。",
                        "無跡接受跟隨、待命、調查及執行目標命令。")));
        Player partner = nearestPartner(player);
        inventory.setItem(15, item(partner == null ? Material.GRAY_DYE : Material.TOTEM_OF_UNDYING,
                partner == null ? "附近沒有合格隊員" : "邀請「" + partner.getName() + "」",
                partner == null ? NamedTextColor.GRAY : NamedTextColor.GREEN,
                List.of("需選正好 2 項整備；同步目標必須由不同玩家操作。")));
        int mask = selectedKits.getOrDefault(player.getUniqueId(), 0);
        for (int index = 0; index < ExpeditionKit.values().length; index++) {
            ExpeditionKit kit = ExpeditionKit.values()[index];
            boolean selected = ExpeditionKit.contains(mask, kit);
            inventory.setItem(18 + index, item(selected ? Material.LIME_DYE : kit.icon(),
                    (selected ? "已攜帶｜" : "") + kit.display(), selected ? NamedTextColor.GREEN
                            : NamedTextColor.YELLOW, List.of(kit.description(), "點擊切換整備。")));
        }
        inventory.setItem(22, item(Material.ARROW, "返回路線", NamedTextColor.GRAY, List.of()));
        player.openInventory(inventory);
    }

    private void invitePartner(Player leader, ExplorationSite site, ExpeditionRoute route) {
        Player target = nearestPartner(leader);
        if (target == null) {
            leader.sendMessage(EvilIslandPlugin.message("附近沒有可加入遠征的隊員。"));
            return;
        }
        long timeout = Math.max(5_000L,
                plugin.getConfig().getLong("expeditions.invite-timeout-ms", 15_000L));
        int kits = selectedKits.getOrDefault(leader.getUniqueId(), 0);
        if (!requireLoadout(leader, kits, 2)) return;
        PendingInvite invite = new PendingInvite(leader.getUniqueId(), target.getUniqueId(), site, route, kits,
                System.currentTimeMillis() + timeout);
        pendingInvites.put(target.getUniqueId(), invite);
        MenuHolder holder = new MenuHolder(MenuType.INVITE, route, site, -1);
        Inventory inventory = createMenu(holder, site.display() + "遠征邀請");
        inventory.setItem(4, item(operationFor(site, route).icon(), leader.getName() + "的遠征編組",
                NamedTextColor.GOLD, List.of(ExpeditionRegionRules.routeDisplay(site, route),
                operationFor(site, route).display())));
        inventory.setItem(11, item(Material.LIME_DYE, "加入編組", NamedTextColor.GREEN,
                List.of("兩人需分頭完成同步目標。")));
        inventory.setItem(15, item(Material.RED_DYE, "拒絕", NamedTextColor.RED, List.of()));
        target.openInventory(inventory);
        leader.closeInventory();
        leader.sendMessage(EvilIslandPlugin.message("已向「" + target.getName() + "」提出遠征邀請。"));
    }

    private void toggleKit(Player player, ExplorationSite site, ExpeditionRoute route, ExpeditionKit kit) {
        int mask = selectedKits.getOrDefault(player.getUniqueId(), 0);
        mask = ExpeditionKit.contains(mask, kit) ? mask & ~kit.mask() : mask | kit.mask();
        selectedKits.put(player.getUniqueId(), mask);
        openAssembly(player, site, route);
    }

    private boolean requireLoadout(Player player, int mask, int participants) {
        if (ExpeditionDirector.validLoadout(mask, participants)) return true;
        int capacity = ExpeditionDirector.kitCapacity(participants);
        player.sendMessage(EvilIslandPlugin.message(participants == 1
                ? "單人遠征需選擇 2 至 " + capacity + " 項整備。"
                : "雙人遠征需選擇正好 2 項整備。", NamedTextColor.RED));
        return false;
    }

    private void acceptInvite(Player target, PendingInvite invite) {
        pendingInvites.remove(target.getUniqueId());
        Player leader = Bukkit.getPlayer(invite.leader);
        if (leader == null || !canStart(leader, false) || !canStart(target, false)
                || System.currentTimeMillis() > invite.expiresAt
                || !leader.getWorld().equals(target.getWorld())) {
            target.sendMessage(EvilIslandPlugin.message("遠征邀請已失效。"));
            target.closeInventory();
            return;
        }
        start(List.of(leader, target), invite.site, invite.route, invite.kitMask);
    }

    private void start(List<Player> members, ExplorationSite site, ExpeditionRoute route, int kitMask) {
        if (members.isEmpty() || members.size() > 2) return;
        if (!ExpeditionDirector.validLoadout(kitMask, members.size())) return;
        for (Player member : members) if (!canStart(member, true)) return;
        if (expeditions.values().stream().anyMatch(active -> active.site == site && active.route == route)) {
            members.get(0).sendMessage(EvilIslandPlugin.message("這條路線剛被其他隊伍占用。"));
            return;
        }
        Location camp = regionControl.campLocation(site);
        if (camp == null || camp.getWorld() == null) {
            members.get(0).sendMessage(EvilIslandPlugin.message(site.display() + "營地尚未完成設置。"));
            return;
        }
        UUID id = UUID.randomUUID();
        long now = System.currentTimeMillis();
        long seed = campaign.state().cycle() * 10_000L + campaign.state().epochDay() * 31L
                + site.ordinal() * 101L + route.ordinal();
        RuntimeExpedition expedition = new RuntimeExpedition(id, operationFor(site, route), route, camp,
                members.get(0).getUniqueId(), members.size() == 2 ? members.get(1).getUniqueId() : null, seed, now);
        expedition.site = site;
        expedition.kitMask = kitMask;
        if (expedition.operation == ExpeditionOperation.LOST_CONVOY
                && ExpeditionKit.contains(kitMask, ExpeditionKit.PROVISIONS)) {
            expedition.eventScore++;
        }
        expeditions.put(id, expedition);
        for (Player member : members) {
            expeditionByMember.put(member.getUniqueId(), id);
            selectedRoutes.remove(member.getUniqueId());
            selectedSites.remove(member.getUniqueId());
            selectedKits.remove(member.getUniqueId());
            giveTool(member);
            member.closeInventory();
        }
        save(expedition);
        repository.beginStage(id, ExpeditionPhase.PREPARING, now);
        if (members.size() == 1) ensureCompanion(expedition);
        tell(expedition, "遠征開始：「" + expedition.operation.display() + "」，由"
                + ExpeditionRegionRules.routeDisplay(site, route) + "推進。", NamedTextColor.GOLD);
        advance(expedition, ExpeditionPhase.APPROACH);
    }

    private void interact(RuntimeExpedition expedition, Player player, String actor) {
        if (actor.startsWith("approach:") && expedition.phase == ExpeditionPhase.APPROACH) {
            int index = parseActorIndex(actor);
            if (index == 1 && (expedition.eventMask & 1) == 0) {
                player.sendMessage(EvilIslandPlugin.message("前方標記無法判讀；先處理上一段途中狀況。"));
                return;
            }
            expedition.approachMask |= 1 << index;
            removeActor(expedition, actor);
            expedition.updatedAt = System.currentTimeMillis();
            save(expedition);
            maybeAdvanceApproach(expedition);
            if (expedition.phase == ExpeditionPhase.APPROACH) {
                player.sendMessage(EvilIslandPlugin.message("已確認推進標記，注意前方途中狀況。"));
            }
            return;
        }
        if (actor.startsWith("event:") && expedition.phase == ExpeditionPhase.APPROACH) {
            openRouteEvent(player, expedition, parseActorIndex(actor));
            return;
        }
        if (actor.startsWith("clue:") && expedition.phase == ExpeditionPhase.INVESTIGATING) {
            int index = parseActorIndex(actor);
            if ((expedition.clueMask & (1 << index)) != 0) return;
            expedition.clueMask |= 1 << index;
            removeActor(expedition, actor);
            if (expedition.operation == ExpeditionOperation.BLOCKADE_INFILTRATION
                    && index == ExpeditionRules.misleadingClue(expedition.seed)) {
                if (ExpeditionKit.contains(expedition.kitMask, ExpeditionKit.SCOUTING)) {
                    expedition.eventScore++;
                    tell(expedition, "偵察器材確認這是假跡；隊伍沒有暴露行蹤。", NamedTextColor.GREEN);
                } else {
                    expedition.alert++;
                    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 1.0f, 0.6f);
                    tell(expedition, "這是假跡，敵軍警戒提高；它不算有效情報。", NamedTextColor.RED);
                }
            } else {
                expedition.validClues++;
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.2f);
                if (expedition.site == ExplorationSite.WESTERN_TRACE) expedition.eventScore += index;
                tell(expedition, "取得有效情報 " + expedition.validClues + "/"
                        + requiredClues(expedition) + "。",
                        NamedTextColor.AQUA);
            }
            expedition.updatedAt = System.currentTimeMillis();
            save(expedition);
            if (expedition.validClues >= requiredClues(expedition)) {
                advance(expedition, ExpeditionPhase.OBJECTIVE);
            }
            return;
        }
        if (actor.startsWith("objective:") && expedition.phase == ExpeditionPhase.OBJECTIVE) {
            activateObjective(expedition, parseActorIndex(actor), player.getUniqueId(), player.getName());
            return;
        }
        if (actor.equals("extraction:0") && expedition.phase == ExpeditionPhase.EXTRACTION) {
            resolve(expedition, ExpeditionOutcome.COMPLETE);
        }
    }

    private void activateObjective(RuntimeExpedition expedition, int index, UUID activator, String display) {
        int bit = 1 << index;
        if ((expedition.objectiveMask & bit) != 0) return;
        if (expedition.objectiveMask != 0 && activator.equals(expedition.firstActivator)) {
            Player player = Bukkit.getPlayer(activator);
            if (player != null) player.sendMessage(EvilIslandPlugin.message(expedition.partner == null
                    ? "另一處必須命令無跡同步處理。" : "另一處必須由另一名隊員同步操作。", NamedTextColor.RED));
            return;
        }
        long now = System.currentTimeMillis();
        expedition.objectiveMask |= bit;
        if (expedition.firstActivator == null) {
            expedition.firstActivator = activator;
            expedition.objectiveDeadline = now + ExpeditionRules.syncWindowMillis(expedition.operation,
                    expedition.route);
        }
        removeActor(expedition, "objective:" + index);
        expedition.updatedAt = now;
        save(expedition);
        if (expedition.objectiveMask == BOTH_OBJECTIVES) {
            tell(expedition, display + (ExpeditionRegionRules.combatRequired(expedition.site)
                    ? "完成同步，敵軍正在逼近。" : "完成同步，邊界通行已獲確認。"), NamedTextColor.GOLD);
            advance(expedition, ExpeditionPhase.ESCALATION);
        } else {
            long seconds = Math.max(1L, (expedition.objectiveDeadline - now) / 1_000L);
            tell(expedition, display + "已啟動一處目標；另一處需在 " + seconds + " 秒內同步。",
                    NamedTextColor.YELLOW);
        }
    }

    private void maybeAdvanceApproach(RuntimeExpedition expedition) {
        if (expedition.phase == ExpeditionPhase.APPROACH
                && expedition.approachMask == BOTH_APPROACH_POINTS && expedition.eventMask == 0b11) {
            advance(expedition, ExpeditionPhase.INVESTIGATING);
        }
    }

    private void ensureRouteEvent(RuntimeExpedition expedition, int index, int forward) {
        ExpeditionRouteEvent routeEvent = ExpeditionDirector.event(expedition.seed, index);
        ensureActor(expedition, "event:" + index, point(expedition, forward, index == 0 ? -8 : 8),
                routeEvent.icon(), routeEvent.display());
    }

    private void openRouteEvent(Player player, RuntimeExpedition expedition, int index) {
        if (index < 0 || index > 1 || (expedition.eventMask & (1 << index)) != 0) return;
        ExpeditionRouteEvent routeEvent = ExpeditionDirector.event(expedition.seed, index);
        MenuHolder holder = new MenuHolder(MenuType.EVENT, expedition.route, expedition.site, index);
        Inventory inventory = createMenu(holder, routeEvent.display());
        inventory.setItem(4, item(routeEvent.icon(), routeEvent.display(), NamedTextColor.GOLD,
                List.of(routeEvent.description(), "建議整備：" + routeEvent.recommendedKit().display())));
        boolean prepared = ExpeditionKit.contains(expedition.kitMask, routeEvent.recommendedKit());
        inventory.setItem(11, item(prepared ? routeEvent.recommendedKit().icon() : Material.GRAY_DYE,
                prepared ? "使用「" + routeEvent.recommendedKit().display() + "」" : "缺少建議整備",
                prepared ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                List.of(prepared ? "發揮出發前整備的效果，安全處理狀況。" : "改以臨場方式處理。")));
        inventory.setItem(15, item(Material.LEATHER_BOOTS, "臨場處置", NamedTextColor.YELLOW,
                List.of("不消耗整備，但可能提高警戒或失去情報。")));
        player.openInventory(inventory);
    }

    private void resolveRouteEvent(RuntimeExpedition expedition, Player player, int index, boolean useKit) {
        if (index < 0 || index > 1 || (expedition.eventMask & (1 << index)) != 0) return;
        ExpeditionRouteEvent routeEvent = ExpeditionDirector.event(expedition.seed, index);
        if (useKit && !ExpeditionKit.contains(expedition.kitMask, routeEvent.recommendedKit())) {
            player.sendMessage(EvilIslandPlugin.message("編組沒有攜帶「" + routeEvent.recommendedKit().display()
                    + "」。", NamedTextColor.RED));
            return;
        }
        ExpeditionEventResolution resolution = ExpeditionDirector.resolve(routeEvent, useKit);
        expedition.eventMask |= 1 << index;
        expedition.eventScore += resolution.scoreDelta();
        expedition.alert = Math.max(0, expedition.alert + resolution.alertDelta());
        expedition.updatedAt = System.currentTimeMillis();
        removeActor(expedition, "event:" + index);
        if (resolution.rest()) {
            for (UUID member : expedition.members()) {
                Player online = Bukkit.getPlayer(member);
                if (online == null) continue;
                double maximum = online.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) == null
                        ? 20.0 : online.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
                online.setHealth(Math.min(maximum, online.getHealth() + 6.0));
                profiles.addQi(online, 8);
            }
        }
        save(expedition);
        player.closeInventory();
        tell(expedition, routeEvent.display() + "：" + resolution.result(), resolution.alertDelta() > 0
                ? NamedTextColor.RED : NamedTextColor.GREEN);
        maybeAdvanceApproach(expedition);
    }

    private void advance(RuntimeExpedition expedition, ExpeditionPhase next) {
        if (!expedition.phase.canAdvanceTo(next)) return;
        long now = System.currentTimeMillis();
        repository.finishStage(expedition.id, expedition.phase, now);
        cleanupActors(expedition, false);
        expedition.phase = next;
        expedition.phaseStartedAt = now;
        expedition.updatedAt = now;
        if (next == ExpeditionPhase.ESCALATION) {
            expedition.objectiveDeadline = 0L;
            expedition.enemiesRemaining = ExpeditionRegionRules.enemyCount(expedition.site, expedition.operation,
                    expedition.route, expedition.participants(), expedition.alert);
            if (expedition.operation == ExpeditionOperation.SUPPLY_NODE_SABOTAGE
                    && ExpeditionKit.contains(expedition.kitMask, ExpeditionKit.DEMOLITION)) {
                expedition.enemiesRemaining = Math.max(1, expedition.enemiesRemaining - 1);
            }
        } else if (next == ExpeditionPhase.EXTRACTION
                && ExpeditionRegionRules.timedExtraction(expedition.site, expedition.operation)) {
            long duration = expedition.site == ExplorationSite.DRAGON_COAST
                    ? plugin.getConfig().getLong("expeditions.tide-extraction-ms", 150_000L)
                    : plugin.getConfig().getLong("expeditions.casualty-extraction-ms", 120_000L);
            expedition.objectiveDeadline = now + Math.max(30_000L, duration)
                    + (expedition.operation == ExpeditionOperation.CASUALTY_EVACUATION
                    && ExpeditionKit.contains(expedition.kitMask, ExpeditionKit.MEDICAL) ? 60_000L : 0L);
        }
        save(expedition);
        repository.beginStage(expedition.id, next, now);
        ensureStage(expedition);
        tell(expedition, stageInstruction(expedition), NamedTextColor.AQUA);
        if (next == ExpeditionPhase.ESCALATION && !ExpeditionRegionRules.combatRequired(expedition.site)) {
            advance(expedition, ExpeditionPhase.EXTRACTION);
        }
    }

    private void resolve(RuntimeExpedition expedition, ExpeditionOutcome outcome) {
        if (!expedition.phase.running()) return;
        long now = System.currentTimeMillis();
        repository.finishStage(expedition.id, expedition.phase, now);
        cleanupActors(expedition, true);
        if (expedition.companion != null) companions.remove(expedition.companion);
        expedition.phase = outcome.terminalPhase();
        expedition.outcome = outcome;
        expedition.completedAt = now;
        expedition.updatedAt = now;
        save(expedition);
        boolean rewarded = rewards.resolve(expedition.id, expedition.site, expedition.operation, expedition.route,
                outcome, expedition.participants(), expedition.eventScore, campaign.state().cycle(),
                campaign.state().week(), now);
        for (UUID member : expedition.members()) {
            expeditionByMember.remove(member);
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                removeTools(player);
                player.closeInventory();
                player.sendMessage(EvilIslandPlugin.message("遠征結束：「" + outcome.display() + "」。",
                        outcome == ExpeditionOutcome.COMPLETE ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                if ((outcome == ExpeditionOutcome.COMPLETE || outcome == ExpeditionOutcome.PARTIAL) && !rewarded) {
                    player.sendMessage(EvilIslandPlugin.message("這條路線本週的高價值成果已取得，本次只保留行動紀錄。"));
                }
            }
        }
        expeditions.remove(expedition.id);
    }

    private void ensureStage(RuntimeExpedition expedition) {
        World world = Bukkit.getWorld(expedition.world);
        if (world == null || !expedition.phase.running()) return;
        if (expedition.phase == ExpeditionPhase.APPROACH) {
            if ((expedition.approachMask & 1) == 0) {
                ensureActor(expedition, "approach:0", point(expedition, 100, 0), Material.OAK_SIGN,
                        "前隊留下的路標");
            } else if ((expedition.eventMask & 1) == 0) {
                ensureRouteEvent(expedition, 0, 165);
            } else if ((expedition.approachMask & 2) == 0) {
                ensureActor(expedition, "approach:1", point(expedition, 230, 0), Material.REDSTONE_TORCH,
                        "被折斷的警戒標記");
            } else if ((expedition.eventMask & 2) == 0) {
                ensureRouteEvent(expedition, 1, 300);
            } else {
                maybeAdvanceApproach(expedition);
            }
        } else if (expedition.phase == ExpeditionPhase.INVESTIGATING) {
            for (int index = 0; index < 3; index++) if ((expedition.clueMask & (1 << index)) == 0) {
                ensureActor(expedition, "clue:" + index, point(expedition, 360 + index * 90, (index - 1) * 25),
                        ExpeditionRegionRules.clueMaterial(expedition.operation, index),
                        ExpeditionRegionRules.clueName(expedition.operation, index));
            }
        } else if (expedition.phase == ExpeditionPhase.OBJECTIVE) {
            for (int index = 0; index < 2; index++) if ((expedition.objectiveMask & (1 << index)) == 0) {
                ensureActor(expedition, "objective:" + index, point(expedition, 680, index == 0 ? -10 : 10),
                        ExpeditionRegionRules.objectiveMaterial(expedition.operation, index),
                        ExpeditionRegionRules.objectiveName(expedition.operation, index));
            }
        } else if (expedition.phase == ExpeditionPhase.ESCALATION) {
            int missing = expedition.enemiesRemaining - expedition.enemyIds.size();
            for (int index = 0; index < missing; index++) spawnEnemy(expedition, index);
        } else if (expedition.phase == ExpeditionPhase.EXTRACTION) {
            ensureActor(expedition, "extraction:0", point(expedition, 300, 0), Material.SOUL_CAMPFIRE,
                    "撤離信標");
        }
        ensureCompanion(expedition);
    }

    private void ensureActor(RuntimeExpedition expedition, String actor, Location location, Material material,
                             String name) {
        UUID existing = expedition.actorIds.get(actor);
        if (existing != null && Bukkit.getEntity(existing) != null) return;
        location.getChunk().setForceLoaded(true);
        expedition.forcedChunks.add(new ChunkPos(location.getChunk().getX(), location.getChunk().getZ()));
        for (Entity loaded : location.getChunk().getEntities()) {
            if (expedition.id.equals(expeditionId(loaded)) && actor.equals(loaded.getPersistentDataContainer()
                    .get(actorKey, PersistentDataType.STRING))) {
                expedition.actorIds.put(actor, loaded.getUniqueId());
                return;
            }
        }
        ArmorStand stand = location.getWorld().spawn(location, ArmorStand.class);
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setMarker(false);
        stand.setBasePlate(false);
        stand.setArms(true);
        stand.setPersistent(true);
        stand.setRemoveWhenFarAway(false);
        stand.setCanPickupItems(false);
        stand.getEquipment().setItemInMainHand(new ItemStack(material));
        stand.customName(Component.text(name + "（右鍵調查）", NamedTextColor.YELLOW));
        stand.setCustomNameVisible(true);
        mark(stand, expedition.id, actor);
        expedition.actorIds.put(actor, stand.getUniqueId());
    }

    private void spawnEnemy(RuntimeExpedition expedition, int index) {
        Location center = point(expedition, 660, ((index % 3) - 1) * 8);
        center.getChunk().setForceLoaded(true);
        expedition.forcedChunks.add(new ChunkPos(center.getChunk().getX(), center.getChunk().getZ()));
        for (Entity loaded : center.getChunk().getEntities()) {
            if (isExpeditionEnemy(loaded) && expedition.id.equals(expeditionId(loaded))) {
                expedition.enemyIds.add(loaded.getUniqueId());
            }
        }
        if (expedition.enemyIds.size() >= expedition.enemiesRemaining) return;
        LivingEntity enemy = expedition.site == ExplorationSite.EASTERN_ROUTE
                ? species.spawnZaochi(center, expedition.participants() == 2 ? 1.18 : 1.0,
                expedition.route == ExpeditionRoute.RIDGE ? 1.12 : 1.0)
                : species.spawnEcology(ExpeditionRegionRules.enemy(expedition.site, expedition.enemyIds.size()),
                center);
        mark(enemy, expedition.id, "enemy");
        expedition.enemyIds.add(enemy.getUniqueId());
    }

    private void directCompanion(RuntimeExpedition expedition) {
        if (expedition.partner != null || expedition.companion == null) return;
        Entity entity = Bukkit.getEntity(expedition.companion);
        if (!(entity instanceof Mob companion)) return;
        CompanionOrder order = companions.order(companion);
        if (order == CompanionOrder.INVESTIGATE && expedition.phase == ExpeditionPhase.INVESTIGATING) {
            Entity target = nearestActor(expedition, companion.getLocation(), "clue:");
            if (target == null) return;
            if (companion.getLocation().distanceSquared(target.getLocation()) <= 9.0) {
                companion.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1, 0),
                        8, 0.35, 0.5, 0.35, 0.02);
                companions.setOrder(companion, CompanionOrder.HOLD);
                tell(expedition, "無跡標出了可疑痕跡；仍需由玩家判讀。", NamedTextColor.AQUA);
            } else {
                companion.getPathfinder().moveTo(target.getLocation(), 1.15);
            }
            return;
        }
        if (order != CompanionOrder.EXECUTE || expedition.phase != ExpeditionPhase.OBJECTIVE) return;
        int missing = (expedition.objectiveMask & 1) == 0 ? 0 : (expedition.objectiveMask & 2) == 0 ? 1 : -1;
        if (missing < 0) return;
        UUID actorId = expedition.actorIds.get("objective:" + missing);
        Entity target = actorId == null ? null : Bukkit.getEntity(actorId);
        if (target == null) return;
        if (companion.getWorld().equals(target.getWorld())
                && companion.getLocation().distanceSquared(target.getLocation()) <= 7.0) {
            activateObjective(expedition, missing, companion.getUniqueId(), "無跡");
            companions.setOrder(companion, CompanionOrder.HOLD);
        } else {
            companion.getPathfinder().moveTo(target.getLocation(), 1.15);
        }
    }

    private Entity nearestActor(RuntimeExpedition expedition, Location from, String prefix) {
        Entity nearest = null;
        double distance = Double.MAX_VALUE;
        for (Map.Entry<String, UUID> entry : expedition.actorIds.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;
            Entity candidate = Bukkit.getEntity(entry.getValue());
            if (candidate == null || !candidate.getWorld().equals(from.getWorld())) continue;
            double current = candidate.getLocation().distanceSquared(from);
            if (current < distance) {
                nearest = candidate;
                distance = current;
            }
        }
        return nearest;
    }

    private void openCommandMenu(Player player, RuntimeExpedition expedition) {
        MenuHolder holder = new MenuHolder(MenuType.COMMAND, expedition.route, expedition.site, -1);
        Inventory inventory = createMenu(holder, "無跡現場命令");
        Entity entity = expedition.companion == null ? null : Bukkit.getEntity(expedition.companion);
        CompanionOrder current = entity == null ? CompanionOrder.FOLLOW : companions.order(entity);
        inventory.setItem(4, item(Material.COMPASS, "目前命令：" + current.display(), NamedTextColor.AQUA,
                List.of("直接選擇命令，不需依序循環。")));
        inventory.setItem(10, item(Material.LEAD, "跟隨", NamedTextColor.GREEN,
                List.of("回到隊長身邊並保持移動。")));
        inventory.setItem(12, item(Material.SHIELD, "原地待命", NamedTextColor.YELLOW,
                List.of("守住目前位置，不自行推進。")));
        inventory.setItem(14, item(Material.SPYGLASS, "調查線索", NamedTextColor.AQUA,
                List.of("前往最近線索並標出位置，不代替玩家判讀。")));
        inventory.setItem(16, item(Material.TARGET, "執行目標", NamedTextColor.GOLD,
                List.of("前往尚未處理的同步目標並執行。")));
        player.openInventory(inventory);
    }

    private void issueCompanionOrder(Player player, CompanionOrder order) {
        RuntimeExpedition expedition = expedition(player);
        if (expedition == null || expedition.partner != null) return;
        Entity entity = expedition.companion == null ? null : Bukkit.getEntity(expedition.companion);
        if (entity == null || !companions.isCompanion(entity)) {
            ensureCompanion(expedition);
            entity = expedition.companion == null ? null : Bukkit.getEntity(expedition.companion);
        }
        if (entity == null) return;
        if (order == CompanionOrder.INVESTIGATE && expedition.phase != ExpeditionPhase.INVESTIGATING) {
            player.sendMessage(EvilIslandPlugin.message("目前沒有可交給無跡調查的現場線索。"));
            return;
        }
        if (order == CompanionOrder.EXECUTE && expedition.phase != ExpeditionPhase.OBJECTIVE) {
            player.sendMessage(EvilIslandPlugin.message("目前沒有可交給無跡執行的同步目標。"));
            return;
        }
        companions.setOrder(entity, order);
        player.closeInventory();
        player.sendMessage(EvilIslandPlugin.message("無跡命令改為「" + order.display() + "」。",
                order == CompanionOrder.EXECUTE ? NamedTextColor.GOLD : NamedTextColor.AQUA));
    }

    private void ensureCompanion(RuntimeExpedition expedition) {
        if (expedition.partner != null || !expedition.phase.running()) return;
        Entity existing = expedition.companion == null ? null : Bukkit.getEntity(expedition.companion);
        if (existing != null && companions.isCompanion(existing)) return;
        Player leader = Bukkit.getPlayer(expedition.leader);
        Location spawn = leader != null ? leader.getLocation() : anchor(expedition);
        if (spawn == null || spawn.getWorld() == null) return;
        LivingEntity companion = companions.spawn(spawn.clone().add(1, 0, 1), expedition.leader,
                expedition.id, NpcRole.WUJI);
        companion.getPersistentDataContainer().set(expeditionKey, PersistentDataType.STRING,
                expedition.id.toString());
        expedition.companion = companion.getUniqueId();
        expedition.updatedAt = System.currentTimeMillis();
        save(expedition);
    }

    private void openWithdraw(Player player, RuntimeExpedition expedition) {
        ExpeditionOutcome estimated = ExpeditionRules.withdrawalOutcome(expedition.phase,
                expedition.validClues, expedition.objectiveMask);
        MenuHolder holder = new MenuHolder(MenuType.WITHDRAW, expedition.route, expedition.site, -1);
        Inventory inventory = createMenu(holder, "遠征撤離評估");
        inventory.setItem(4, item(Material.MAP, expedition.operation.display(), NamedTextColor.GOLD,
                List.of("目前階段：" + expedition.phase.display(), "現在撤離：" + estimated.display())));
        inventory.setItem(11, item(Material.OAK_BOAT, "確認撤離", NamedTextColor.YELLOW,
                List.of(estimated == ExpeditionOutcome.PARTIAL ? "已取得的情報與物資會帶回營地。"
                        : "目前尚無可轉化為區域成果的進度。")));
        inventory.setItem(15, item(Material.SHIELD, "繼續行動", NamedTextColor.GREEN, List.of()));
        player.openInventory(inventory);
    }

    private void showProgress(RuntimeExpedition expedition) {
        String detail = switch (expedition.phase) {
            case APPROACH -> Integer.bitCount(expedition.approachMask) + "/2 路標｜"
                    + Integer.bitCount(expedition.eventMask) + "/2 途中狀況";
            case INVESTIGATING -> expedition.validClues + "/"
                    + requiredClues(expedition) + " 情報";
            case OBJECTIVE -> Integer.bitCount(expedition.objectiveMask) + "/2 同步目標";
            case ESCALATION -> expedition.enemiesRemaining + " 個威脅";
            case EXTRACTION -> ExpeditionRegionRules.timedExtraction(expedition.site, expedition.operation)
                    && expedition.objectiveDeadline > 0L
                    ? (expedition.site == ExplorationSite.DRAGON_COAST ? "潮路剩餘 " : "傷員可支撐 ")
                    + Math.max(0L, (expedition.objectiveDeadline - System.currentTimeMillis()) / 1_000L) + " 秒"
                    : "返回撤離信標";
            default -> expedition.phase.display();
        };
        for (UUID member : expedition.members()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) player.sendActionBar(Component.text(expedition.operation.display() + "｜" + detail
                    + navigation(expedition, player), NamedTextColor.YELLOW));
        }
    }

    private String stageInstruction(RuntimeExpedition expedition) {
        return switch (expedition.phase) {
            case APPROACH -> "沿" + ExpeditionRegionRules.routeDisplay(expedition.site, expedition.route)
                    + "確認路標，並處理兩段途中狀況。";
            case INVESTIGATING -> expedition.site == ExplorationSite.WESTERN_TRACE
                    ? "三處遺跡證據只能選擇兩份帶回，先判斷價值再調查。"
                    : "調查三處現場痕跡，辨識足夠的有效情報。";
            case OBJECTIVE -> expedition.partner == null
                    ? "兩處目標必須同步；操作一處後，以指令牌命令無跡執行另一處。"
                    : "兩名隊員分頭就位，各自操作一處目標。";
            case ESCALATION -> ExpeditionRegionRules.combatRequired(expedition.site)
                    ? "同步行動驚動敵軍，清除 " + expedition.enemiesRemaining + " 個威脅。"
                    : "遵守邊界默契，不與毛族交戰。";
            case EXTRACTION -> expedition.site == ExplorationSite.DRAGON_COAST
                    ? "潮路正在封閉，限時返回沿線撤離信標。" : "任務目標完成，返回沿線的撤離信標。";
            default -> expedition.phase.display();
        };
    }

    private String navigation(RuntimeExpedition expedition, Player player) {
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (UUID id : expedition.actorIds.values()) {
            Entity actor = Bukkit.getEntity(id);
            if (actor == null || !actor.getWorld().equals(player.getWorld())) continue;
            double distance = actor.getLocation().distanceSquared(player.getLocation());
            if (distance < nearestDistance) {
                nearest = actor;
                nearestDistance = distance;
            }
        }
        if (nearest == null) return "";
        double dx = nearest.getLocation().getX() - player.getLocation().getX();
        double dz = nearest.getLocation().getZ() - player.getLocation().getZ();
        String direction = Math.abs(dx) > Math.abs(dz) ? dx >= 0 ? "東" : "西" : dz >= 0 ? "南" : "北";
        return "｜" + direction + " " + Math.round(Math.sqrt(nearestDistance)) + " 格";
    }

    private boolean canStart(Player player, boolean message) {
        String reason = null;
        if (!profiles.isEnlisted(player)) reason = "必須先完成角色測定與炁訣定型。";
        else if (!weapons.hasWeapon(player)) reason = "必須攜帶已認主的兵器。";
        else if (isActive(player.getUniqueId())) reason = "你已有一場未完成的遠征。";
        else if (encounters.hasActiveMission(player.getUniqueId())) reason = "必須先完成目前巡防。";
        if (reason != null && message) player.sendMessage(EvilIslandPlugin.message(reason, NamedTextColor.RED));
        return reason == null;
    }

    private Player nearestPartner(Player leader) {
        double radius = Math.max(5.0, plugin.getConfig().getDouble("expeditions.assembly-radius", 12.0));
        Player best = null;
        double bestDistance = radius * radius;
        for (Player candidate : Bukkit.getOnlinePlayers()) {
            if (candidate.equals(leader) || !candidate.getWorld().equals(leader.getWorld())
                    || !canStart(candidate, false)) continue;
            double distance = candidate.getLocation().distanceSquared(leader.getLocation());
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private ExpeditionOperation operationFor(ExplorationSite site, ExpeditionRoute route) {
        long seed = campaign.state().cycle() * 10_000L + campaign.state().epochDay() * 31L
                + site.ordinal() * 101L + route.ordinal();
        return ExpeditionRegionRules.operation(site, seed);
    }

    private int requiredClues(RuntimeExpedition expedition) {
        return ExpeditionRegionRules.requiredClues(expedition.site, expedition.operation, expedition.route);
    }

    private Location point(RuntimeExpedition expedition, int forward, int side) {
        World world = Bukkit.getWorld(expedition.world);
        if (world == null) return null;
        int x = (int) Math.round(expedition.anchorX + expedition.route.dx() * forward
                + expedition.route.perpendicularX() * side);
        int z = (int) Math.round(expedition.anchorZ + expedition.route.dz() * forward
                + expedition.route.perpendicularZ() * side);
        int highest = world.getHighestBlockYAt(x, z);
        int y = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 3, highest + 1));
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private Location anchor(RuntimeExpedition expedition) {
        World world = Bukkit.getWorld(expedition.world);
        return world == null ? null : new Location(world, expedition.anchorX, expedition.anchorY, expedition.anchorZ);
    }

    private void mark(Entity entity, UUID expeditionId, String actor) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(expeditionKey, PersistentDataType.STRING, expeditionId.toString());
        data.set(sessionKey, PersistentDataType.STRING, expeditionId.toString());
        data.set(actorKey, PersistentDataType.STRING, actor);
    }

    private void recoverEntity(Entity entity) {
        UUID id = expeditionId(entity);
        RuntimeExpedition expedition = id == null ? null : expeditions.get(id);
        if (expedition == null) return;
        String actor = entity.getPersistentDataContainer().get(actorKey, PersistentDataType.STRING);
        if (actor == null) {
            if (companions.isCompanion(entity)) expedition.companion = entity.getUniqueId();
        } else if (actor.equals("enemy")) {
            entity.getLocation().getChunk().setForceLoaded(true);
            expedition.forcedChunks.add(new ChunkPos(entity.getLocation().getChunk().getX(),
                    entity.getLocation().getChunk().getZ()));
            expedition.enemyIds.add(entity.getUniqueId());
        } else {
            entity.getLocation().getChunk().setForceLoaded(true);
            expedition.forcedChunks.add(new ChunkPos(entity.getLocation().getChunk().getX(),
                    entity.getLocation().getChunk().getZ()));
            UUID duplicate = expedition.actorIds.putIfAbsent(actor, entity.getUniqueId());
            if (duplicate != null && !duplicate.equals(entity.getUniqueId())) entity.remove();
        }
    }

    private UUID expeditionId(Entity entity) {
        if (entity == null) return null;
        String value = entity.getPersistentDataContainer().get(expeditionKey, PersistentDataType.STRING);
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void removeActor(RuntimeExpedition expedition, String actor) {
        UUID id = expedition.actorIds.remove(actor);
        Entity entity = id == null ? null : Bukkit.getEntity(id);
        if (entity != null) entity.remove();
    }

    private void cleanupActors(RuntimeExpedition expedition, boolean includeEnemies) {
        for (UUID id : expedition.actorIds.values()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
        expedition.actorIds.clear();
        if (includeEnemies) {
            for (UUID id : expedition.enemyIds) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) entity.remove();
            }
            expedition.enemyIds.clear();
            World world = Bukkit.getWorld(expedition.world);
            if (world != null) for (ChunkPos chunk : expedition.forcedChunks) {
                world.setChunkForceLoaded(chunk.x, chunk.z, false);
            }
            expedition.forcedChunks.clear();
        }
    }

    private void tell(RuntimeExpedition expedition, String text, NamedTextColor color) {
        for (UUID member : expedition.members()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) player.sendMessage(EvilIslandPlugin.message(text, color));
        }
    }

    private void save(RuntimeExpedition expedition) {
        repository.save(expedition.snapshot());
        repository.saveState(expedition.stateSnapshot());
    }

    private RuntimeExpedition expedition(Player player) {
        UUID id = expeditionByMember.get(player.getUniqueId());
        return id == null ? null : expeditions.get(id);
    }

    private Inventory createMenu(MenuHolder holder, String title) {
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text(title));
        holder.inventory = inventory;
        return inventory;
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color));
        if (!lore.isEmpty()) meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private void giveTool(Player player) {
        if (player.getInventory().all(Material.COMPASS).values().stream().anyMatch(this::isTool)) return;
        ItemStack tool = item(Material.COMPASS, "遠征指令牌", NamedTextColor.GOLD,
                List.of("使用：開啟無跡現場命令。", "潛行使用：評估並確認撤離。"));
        ItemMeta meta = tool.getItemMeta();
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
        tool.setItemMeta(meta);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(tool);
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private boolean isTool(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE);
    }

    private void removeTools(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isTool(contents[slot])) player.getInventory().setItem(slot, null);
        }
    }

    private int parseActorIndex(String actor) {
        try {
            return Integer.parseInt(actor.substring(actor.indexOf(':') + 1));
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private enum MenuType { ROUTE, ASSEMBLY, INVITE, WITHDRAW, COMMAND, EVENT }

    private static final class MenuHolder implements InventoryHolder {
        private final MenuType type;
        private final ExpeditionRoute route;
        private final ExplorationSite site;
        private final int context;
        private Inventory inventory;

        private MenuHolder(MenuType type, ExpeditionRoute route) {
            this(type, route, ExplorationSite.EASTERN_ROUTE, -1);
        }

        private MenuHolder(MenuType type, ExpeditionRoute route, int context) {
            this(type, route, ExplorationSite.EASTERN_ROUTE, context);
        }

        private MenuHolder(MenuType type, ExpeditionRoute route, ExplorationSite site, int context) {
            this.type = type;
            this.route = route;
            this.site = site;
            this.context = context;
        }

        @Override public Inventory getInventory() { return inventory; }
    }

    private record PendingInvite(UUID leader, UUID target, ExplorationSite site, ExpeditionRoute route,
                                 int kitMask, long expiresAt) { }
    private record ChunkPos(int x, int z) { }

    private static final class RuntimeExpedition {
        private final UUID id;
        private final ExpeditionOperation operation;
        private final ExpeditionRoute route;
        private final String world;
        private final double anchorX;
        private final double anchorY;
        private final double anchorZ;
        private final UUID leader;
        private final UUID partner;
        private final long seed;
        private final long startedAt;
        private final Map<String, UUID> actorIds = new HashMap<>();
        private final Set<UUID> enemyIds = new HashSet<>();
        private final Set<ChunkPos> forcedChunks = new HashSet<>();
        private ExpeditionPhase phase;
        private ExplorationSite site = ExplorationSite.EASTERN_ROUTE;
        private ExpeditionOutcome outcome;
        private UUID companion;
        private int approachMask;
        private int clueMask;
        private int validClues;
        private int objectiveMask;
        private UUID firstActivator;
        private long objectiveDeadline;
        private int alert;
        private int enemiesRemaining;
        private int kitMask;
        private int eventMask;
        private int eventScore;
        private long phaseStartedAt;
        private long completedAt;
        private long updatedAt;

        private RuntimeExpedition(UUID id, ExpeditionOperation operation, ExpeditionRoute route, Location anchor,
                                  UUID leader, UUID partner, long seed, long now) {
            this.id = id;
            this.operation = operation;
            this.route = route;
            this.world = anchor.getWorld().getName();
            this.anchorX = anchor.getX();
            this.anchorY = anchor.getY();
            this.anchorZ = anchor.getZ();
            this.leader = leader;
            this.partner = partner;
            this.seed = seed;
            this.startedAt = now;
            this.phase = ExpeditionPhase.PREPARING;
            this.phaseStartedAt = now;
            this.updatedAt = now;
        }

        private RuntimeExpedition(ExpeditionSnapshot snapshot) {
            id = snapshot.id(); operation = snapshot.operation(); route = snapshot.route(); phase = snapshot.phase();
            outcome = snapshot.outcome(); world = snapshot.world(); anchorX = snapshot.anchorX();
            anchorY = snapshot.anchorY(); anchorZ = snapshot.anchorZ(); leader = snapshot.leader();
            partner = snapshot.partner(); companion = snapshot.companion(); seed = snapshot.seed();
            approachMask = snapshot.approachMask(); clueMask = snapshot.clueMask();
            validClues = Integer.bitCount(clueMask);
            if (operation == ExpeditionOperation.BLOCKADE_INFILTRATION
                    && (clueMask & (1 << ExpeditionRules.misleadingClue(seed))) != 0) validClues--;
            objectiveMask = snapshot.objectiveMask(); firstActivator = snapshot.firstActivator();
            objectiveDeadline = snapshot.objectiveDeadline(); alert = snapshot.alert();
            enemiesRemaining = snapshot.enemiesRemaining(); startedAt = snapshot.startedAt();
            phaseStartedAt = snapshot.phaseStartedAt(); completedAt = snapshot.completedAt();
            updatedAt = snapshot.updatedAt();
        }

        private boolean member(UUID id) { return leader.equals(id) || id.equals(partner); }
        private int participants() { return partner == null ? 1 : 2; }
        private List<UUID> members() { return partner == null ? List.of(leader) : List.of(leader, partner); }

        private ExpeditionSnapshot snapshot() {
            return new ExpeditionSnapshot(id, operation, route, phase, outcome, world, anchorX, anchorY, anchorZ,
                    leader, partner, companion, seed, approachMask, clueMask, objectiveMask, firstActivator,
                    objectiveDeadline, alert, enemiesRemaining, startedAt, phaseStartedAt, completedAt, updatedAt);
        }

        private void loadState(ExpeditionRunStateSnapshot state) {
            site = state.site();
            kitMask = state.kitMask();
            eventMask = state.eventMask();
            eventScore = state.eventScore();
        }

        private ExpeditionRunStateSnapshot stateSnapshot() {
            return new ExpeditionRunStateSnapshot(id, site, kitMask, eventMask, eventScore, updatedAt);
        }
    }
}
