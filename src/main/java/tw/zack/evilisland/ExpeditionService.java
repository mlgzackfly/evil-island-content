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
import tw.zack.evilisland.model.NpcRole;
import tw.zack.evilisland.model.WorldResource;
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
    private final NamespacedKey expeditionKey;
    private final NamespacedKey actorKey;
    private final NamespacedKey sessionKey;
    private final NamespacedKey toolKey;
    private final Map<UUID, RuntimeExpedition> expeditions = new HashMap<>();
    private final Map<UUID, UUID> expeditionByMember = new HashMap<>();
    private final Map<UUID, ExpeditionRoute> selectedRoutes = new HashMap<>();
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
            expeditions.put(expedition.id, expedition);
            expeditionByMember.put(expedition.leader, expedition.id);
            if (expedition.partner != null) expeditionByMember.put(expedition.partner, expedition.id);
        }
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
        if (!canStart(player, true)) return;
        MenuHolder holder = new MenuHolder(MenuType.ROUTE, null);
        Inventory inventory = createMenu(holder, "東境深入遠征");
        inventory.setItem(4, item(Material.RECOVERY_COMPASS, "補給線現況", NamedTextColor.AQUA,
                List.of("每條路線提供不同風險；本輪行動依路線固定，失敗不會重抽。",
                        "遠征進度會保存，可主動帶回部分成果。")));
        int[] slots = {11, 13, 15};
        for (int index = 0; index < ExpeditionRoute.values().length; index++) {
            ExpeditionRoute route = ExpeditionRoute.values()[index];
            ExpeditionOperation operation = operationFor(route);
            boolean occupied = expeditions.values().stream().anyMatch(active -> active.route == route);
            inventory.setItem(slots[index], item(occupied ? Material.BARRIER : route.icon(), route.display(),
                    occupied ? NamedTextColor.GRAY : NamedTextColor.GOLD,
                    List.of(route.description(), "行動：" + operation.display(), operation.description(),
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
                    && expedition.operation == ExpeditionOperation.CASUALTY_EVACUATION
                    && expedition.objectiveDeadline > 0L && now > expedition.objectiveDeadline) {
                tell(expedition, "傷員無法繼續等待；隊伍只能帶回部分成果。", NamedTextColor.RED);
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
            if (route != null) openAssembly(player, route);
        } else if (holder.type == MenuType.ASSEMBLY && holder.route != null) {
            if (slot == 11) start(List.of(player), holder.route);
            if (slot == 15) invitePartner(player, holder.route);
            if (slot == 22) openBoard(player);
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
        CompanionOrder order = companions.order(entity).next();
        companions.setOrder(entity, order);
        event.getPlayer().sendMessage(EvilIslandPlugin.message("無跡命令改為「" + order.display() + "」。",
                order == CompanionOrder.EXECUTE ? NamedTextColor.GOLD : NamedTextColor.AQUA));
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
        event.getPlayer().sendMessage(EvilIslandPlugin.message("你有一場未完成的東境遠征："
                + expedition.operation.display() + "，目前「" + expedition.phase.display() + "」。",
                NamedTextColor.YELLOW));
    }

    private void openAssembly(Player player, ExpeditionRoute route) {
        if (expeditions.values().stream().anyMatch(active -> active.route == route)) {
            player.sendMessage(EvilIslandPlugin.message("這條路線已有隊伍執行遠征。"));
            return;
        }
        selectedRoutes.put(player.getUniqueId(), route);
        MenuHolder holder = new MenuHolder(MenuType.ASSEMBLY, route);
        Inventory inventory = createMenu(holder, route.display() + "編組");
        ExpeditionOperation operation = operationFor(route);
        inventory.setItem(4, item(operation.icon(), operation.display(), NamedTextColor.GOLD,
                List.of(operation.description(), route.description())));
        inventory.setItem(11, item(Material.PLAYER_HEAD, "單人與無跡", NamedTextColor.AQUA,
                List.of("無跡不代替戰鬥輸出，但會接受跟隨、待命與執行目標命令。",
                        "同步目標必須命令無跡處理另一處。")));
        Player partner = nearestPartner(player);
        inventory.setItem(15, item(partner == null ? Material.GRAY_DYE : Material.TOTEM_OF_UNDYING,
                partner == null ? "附近沒有合格隊員" : "邀請「" + partner.getName() + "」",
                partner == null ? NamedTextColor.GRAY : NamedTextColor.GREEN,
                List.of("雙人敵軍依人數調整；同步目標必須由不同玩家操作。")));
        inventory.setItem(22, item(Material.ARROW, "返回路線", NamedTextColor.GRAY, List.of()));
        player.openInventory(inventory);
    }

    private void invitePartner(Player leader, ExpeditionRoute route) {
        Player target = nearestPartner(leader);
        if (target == null) {
            leader.sendMessage(EvilIslandPlugin.message("附近沒有可加入遠征的隊員。"));
            return;
        }
        long timeout = Math.max(5_000L,
                plugin.getConfig().getLong("expeditions.invite-timeout-ms", 15_000L));
        PendingInvite invite = new PendingInvite(leader.getUniqueId(), target.getUniqueId(), route,
                System.currentTimeMillis() + timeout);
        pendingInvites.put(target.getUniqueId(), invite);
        MenuHolder holder = new MenuHolder(MenuType.INVITE, route);
        Inventory inventory = createMenu(holder, "東境遠征邀請");
        inventory.setItem(4, item(operationFor(route).icon(), leader.getName() + "的遠征編組",
                NamedTextColor.GOLD, List.of(route.display(), operationFor(route).display())));
        inventory.setItem(11, item(Material.LIME_DYE, "加入編組", NamedTextColor.GREEN,
                List.of("兩人需分頭完成同步目標。")));
        inventory.setItem(15, item(Material.RED_DYE, "拒絕", NamedTextColor.RED, List.of()));
        target.openInventory(inventory);
        leader.closeInventory();
        leader.sendMessage(EvilIslandPlugin.message("已向「" + target.getName() + "」提出遠征邀請。"));
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
        start(List.of(leader, target), invite.route);
    }

    private void start(List<Player> members, ExpeditionRoute route) {
        if (members.isEmpty() || members.size() > 2) return;
        for (Player member : members) if (!canStart(member, true)) return;
        if (expeditions.values().stream().anyMatch(active -> active.route == route)) {
            members.get(0).sendMessage(EvilIslandPlugin.message("這條路線剛被其他隊伍占用。"));
            return;
        }
        Location camp = regionControl.campLocation(tw.zack.evilisland.model.ExplorationSite.EASTERN_ROUTE);
        if (camp == null || camp.getWorld() == null) {
            members.get(0).sendMessage(EvilIslandPlugin.message("東境營地尚未完成設置。"));
            return;
        }
        UUID id = UUID.randomUUID();
        long now = System.currentTimeMillis();
        long seed = campaign.state().cycle() * 10_000L + campaign.state().epochDay() * 31L + route.ordinal();
        RuntimeExpedition expedition = new RuntimeExpedition(id, operationFor(route), route, camp,
                members.get(0).getUniqueId(), members.size() == 2 ? members.get(1).getUniqueId() : null, seed, now);
        expeditions.put(id, expedition);
        for (Player member : members) {
            expeditionByMember.put(member.getUniqueId(), id);
            selectedRoutes.remove(member.getUniqueId());
            giveTool(member);
            member.closeInventory();
        }
        save(expedition);
        repository.beginStage(id, ExpeditionPhase.PREPARING, now);
        if (members.size() == 1) ensureCompanion(expedition);
        tell(expedition, "遠征開始：「" + expedition.operation.display() + "」，由"
                + route.display() + "推進。", NamedTextColor.GOLD);
        advance(expedition, ExpeditionPhase.APPROACH);
    }

    private void interact(RuntimeExpedition expedition, Player player, String actor) {
        if (actor.startsWith("approach:") && expedition.phase == ExpeditionPhase.APPROACH) {
            int index = parseActorIndex(actor);
            if (index == 1 && (expedition.approachMask & 1) == 0) {
                player.sendMessage(EvilIslandPlugin.message("前方標記無法判讀；先確認上一處路標。"));
                return;
            }
            expedition.approachMask |= 1 << index;
            removeActor(expedition, actor);
            expedition.updatedAt = System.currentTimeMillis();
            save(expedition);
            if (expedition.approachMask == BOTH_APPROACH_POINTS) advance(expedition, ExpeditionPhase.INVESTIGATING);
            else player.sendMessage(EvilIslandPlugin.message("已確認第一處推進標記，繼續沿線搜索。"));
            return;
        }
        if (actor.startsWith("clue:") && expedition.phase == ExpeditionPhase.INVESTIGATING) {
            int index = parseActorIndex(actor);
            if ((expedition.clueMask & (1 << index)) != 0) return;
            expedition.clueMask |= 1 << index;
            removeActor(expedition, actor);
            if (expedition.operation == ExpeditionOperation.BLOCKADE_INFILTRATION
                    && index == ExpeditionRules.misleadingClue(expedition.seed)) {
                expedition.alert++;
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 1.0f, 0.6f);
                tell(expedition, "這是假跡，敵軍警戒提高；它不算有效情報。", NamedTextColor.RED);
            } else {
                expedition.validClues++;
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.2f);
                tell(expedition, "取得有效情報 " + expedition.validClues + "/"
                        + ExpeditionRules.requiredClues(expedition.operation, expedition.route) + "。",
                        NamedTextColor.AQUA);
            }
            expedition.updatedAt = System.currentTimeMillis();
            save(expedition);
            if (expedition.validClues >= ExpeditionRules.requiredClues(expedition.operation, expedition.route)) {
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
            tell(expedition, display + "完成同步，敵軍正在逼近。", NamedTextColor.GOLD);
            advance(expedition, ExpeditionPhase.ESCALATION);
        } else {
            long seconds = Math.max(1L, (expedition.objectiveDeadline - now) / 1_000L);
            tell(expedition, display + "已啟動一處目標；另一處需在 " + seconds + " 秒內同步。",
                    NamedTextColor.YELLOW);
        }
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
            expedition.enemiesRemaining = ExpeditionRules.enemyCount(expedition.operation, expedition.route,
                    expedition.participants(), expedition.alert);
        } else if (next == ExpeditionPhase.EXTRACTION
                && expedition.operation == ExpeditionOperation.CASUALTY_EVACUATION) {
            expedition.objectiveDeadline = now + Math.max(30_000L,
                    plugin.getConfig().getLong("expeditions.casualty-extraction-ms", 120_000L));
        }
        save(expedition);
        repository.beginStage(expedition.id, next, now);
        ensureStage(expedition);
        tell(expedition, stageInstruction(expedition), NamedTextColor.AQUA);
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
        regionControl.recordExpedition(expedition.id, outcome, expedition.participants());
        reward(expedition, outcome);
        for (UUID member : expedition.members()) {
            expeditionByMember.remove(member);
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                removeTools(player);
                player.closeInventory();
                player.sendMessage(EvilIslandPlugin.message("遠征結束：「" + outcome.display() + "」。",
                        outcome == ExpeditionOutcome.COMPLETE ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            }
        }
        expeditions.remove(expedition.id);
    }

    private void reward(RuntimeExpedition expedition, ExpeditionOutcome outcome) {
        if (outcome != ExpeditionOutcome.COMPLETE && outcome != ExpeditionOutcome.PARTIAL) return;
        int amount = outcome == ExpeditionOutcome.COMPLETE
                ? expedition.operation == ExpeditionOperation.LOST_CONVOY ? 3 : 2 : 1;
        WorldResource primary = switch (expedition.operation) {
            case LOST_CONVOY, CASUALTY_EVACUATION -> WorldResource.PROVISIONS;
            case BLOCKADE_INFILTRATION -> WorldResource.TIMBER;
            case SUPPLY_NODE_SABOTAGE -> WorldResource.COMPONENTS;
        };
        development.addResource(primary, amount);
        if (outcome == ExpeditionOutcome.COMPLETE && primary != WorldResource.PROVISIONS) {
            development.addResource(WorldResource.PROVISIONS, 1);
        }
    }

    private void ensureStage(RuntimeExpedition expedition) {
        World world = Bukkit.getWorld(expedition.world);
        if (world == null || !expedition.phase.running()) return;
        if (expedition.phase == ExpeditionPhase.APPROACH) {
            for (int index = 0; index < 2; index++) if ((expedition.approachMask & (1 << index)) == 0) {
                ensureActor(expedition, "approach:" + index, point(expedition, 100 + index * 130, 0),
                        index == 0 ? Material.OAK_SIGN : Material.REDSTONE_TORCH,
                        index == 0 ? "前隊留下的路標" : "被折斷的警戒標記");
            }
        } else if (expedition.phase == ExpeditionPhase.INVESTIGATING) {
            for (int index = 0; index < 3; index++) if ((expedition.clueMask & (1 << index)) == 0) {
                ensureActor(expedition, "clue:" + index, point(expedition, 360 + index * 90, (index - 1) * 25),
                        clueMaterial(expedition.operation, index), clueName(expedition.operation, index));
            }
        } else if (expedition.phase == ExpeditionPhase.OBJECTIVE) {
            for (int index = 0; index < 2; index++) if ((expedition.objectiveMask & (1 << index)) == 0) {
                ensureActor(expedition, "objective:" + index, point(expedition, 680, index == 0 ? -10 : 10),
                        objectiveMaterial(expedition.operation, index), objectiveName(expedition.operation, index));
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
        LivingEntity enemy = species.spawnZaochi(center, expedition.participants() == 2 ? 1.18 : 1.0,
                expedition.route == ExpeditionRoute.RIDGE ? 1.12 : 1.0);
        mark(enemy, expedition.id, "enemy");
        expedition.enemyIds.add(enemy.getUniqueId());
    }

    private void directCompanion(RuntimeExpedition expedition) {
        if (expedition.partner != null || expedition.companion == null
                || expedition.phase != ExpeditionPhase.OBJECTIVE) return;
        Entity entity = Bukkit.getEntity(expedition.companion);
        if (!(entity instanceof Mob companion) || companions.order(companion) != CompanionOrder.EXECUTE) return;
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
        MenuHolder holder = new MenuHolder(MenuType.WITHDRAW, expedition.route);
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
            case APPROACH -> Integer.bitCount(expedition.approachMask) + "/2 路標";
            case INVESTIGATING -> expedition.validClues + "/"
                    + ExpeditionRules.requiredClues(expedition.operation, expedition.route) + " 情報";
            case OBJECTIVE -> Integer.bitCount(expedition.objectiveMask) + "/2 同步目標";
            case ESCALATION -> expedition.enemiesRemaining + " 個威脅";
            case EXTRACTION -> expedition.operation == ExpeditionOperation.CASUALTY_EVACUATION
                    && expedition.objectiveDeadline > 0L
                    ? "傷員可支撐 " + Math.max(0L,
                    (expedition.objectiveDeadline - System.currentTimeMillis()) / 1_000L) + " 秒"
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
            case APPROACH -> "沿" + expedition.route.display() + "依序確認兩處路標。";
            case INVESTIGATING -> "調查三處現場痕跡，辨識足夠的有效情報。";
            case OBJECTIVE -> expedition.partner == null
                    ? "兩處目標必須同步；操作一處後，以指令牌命令無跡執行另一處。"
                    : "兩名隊員分頭就位，各自操作一處目標。";
            case ESCALATION -> "同步行動驚動敵軍，清除 " + expedition.enemiesRemaining + " 個威脅。";
            case EXTRACTION -> "任務目標完成，返回沿線的撤離信標。";
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

    private ExpeditionOperation operationFor(ExpeditionRoute route) {
        long seed = campaign.state().cycle() * 10_000L + campaign.state().epochDay() * 31L + route.ordinal();
        return ExpeditionRules.operation(seed);
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
                List.of("使用：切換無跡命令。", "潛行使用：評估並確認撤離。"));
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

    private Material clueMaterial(ExpeditionOperation operation, int index) {
        return switch (operation) {
            case LOST_CONVOY -> new Material[]{Material.MINECART, Material.BREAD, Material.ARROW}[index];
            case BLOCKADE_INFILTRATION -> new Material[]{Material.STRING, Material.OAK_SIGN, Material.FLINT}[index];
            case SUPPLY_NODE_SABOTAGE -> new Material[]{Material.REDSTONE, Material.CHARCOAL, Material.PAPER}[index];
            case CASUALTY_EVACUATION -> new Material[]{Material.WHITE_WOOL, Material.GLASS_BOTTLE,
                    Material.LEATHER_BOOTS}[index];
        };
    }

    private String clueName(ExpeditionOperation operation, int index) {
        String[][] names = {{"破裂的車輪", "散落的乾糧", "折斷的箭"},
                {"刻意拉直的絆線", "反向路標", "新鮮火石屑"},
                {"紅石粉痕", "未熄焦炭", "節點輪值紙"},
                {"染血繃帶", "空藥瓶", "拖行足跡"}};
        return names[operation.ordinal()][index];
    }

    private Material objectiveMaterial(ExpeditionOperation operation, int index) {
        return switch (operation) {
            case LOST_CONVOY -> index == 0 ? Material.BARREL : Material.TOTEM_OF_UNDYING;
            case BLOCKADE_INFILTRATION -> index == 0 ? Material.BELL : Material.IRON_TRAPDOOR;
            case SUPPLY_NODE_SABOTAGE -> index == 0 ? Material.TNT : Material.REDSTONE_LAMP;
            case CASUALTY_EVACUATION -> index == 0 ? Material.GOLDEN_APPLE : Material.SPLASH_POTION;
        };
    }

    private String objectiveName(ExpeditionOperation operation, int index) {
        String[][] names = {{"封存補給箱", "受困的車隊斥候"}, {"封鎖線警鈴", "補給通道閘門"},
                {"主補給節點", "傳訊節點"}, {"北側傷員", "南側傷員"}};
        return names[operation.ordinal()][index];
    }

    private enum MenuType { ROUTE, ASSEMBLY, INVITE, WITHDRAW }

    private static final class MenuHolder implements InventoryHolder {
        private final MenuType type;
        private final ExpeditionRoute route;
        private Inventory inventory;

        private MenuHolder(MenuType type, ExpeditionRoute route) {
            this.type = type;
            this.route = route;
        }

        @Override public Inventory getInventory() { return inventory; }
    }

    private record PendingInvite(UUID leader, UUID target, ExpeditionRoute route, long expiresAt) { }
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
    }
}
