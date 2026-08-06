package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import tw.zack.evilisland.model.ObjectiveStage;
import tw.zack.evilisland.model.PatrolPhase;
import tw.zack.evilisland.model.PatrolScaling;
import tw.zack.evilisland.model.PatrolContract;
import tw.zack.evilisland.model.CampaignSnapshot;
import tw.zack.evilisland.model.SpeciesType;
import tw.zack.evilisland.model.WorldEventState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class EncounterService implements Listener {
    private final EvilIslandPlugin plugin;
    private final PlayerProfileService profiles;
    private final DaoFieldService daoFields;
    private final GameItemService items;
    private final SpeciesService species;
    private final WeaponService weapons;
    private final CompanionService companions;
    private final WorldEventService worldEvents;
    private final CampaignService campaign;
    private final NamespacedKey guardKey;
    private final NamespacedKey sessionKey;
    private final NamespacedKey anchorKey;
    private final NamespacedKey membersKey;
    private final NamespacedKey phaseKey;
    private final NamespacedKey remainingKey;
    private final NamespacedKey pendingZaochiKey;
    private final NamespacedKey pendingXingtianKey;
    private final NamespacedKey pendingBonusKey;
    private final NamespacedKey pendingCompletionKey;
    private final NamespacedKey contractKey;
    private final Map<UUID, PatrolSession> sessions = new HashMap<>();
    private final Map<UUID, UUID> sessionByMember = new HashMap<>();
    private final Map<UUID, PendingInvite> pendingInvites = new HashMap<>();
    private final Map<UUID, PatrolContract> selectedContracts = new HashMap<>();

    public EncounterService(EvilIslandPlugin plugin, PlayerProfileService profiles, DaoFieldService daoFields,
                            GameItemService items, SpeciesService species, WeaponService weapons,
                            CompanionService companions, WorldEventService worldEvents, CampaignService campaign) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.daoFields = daoFields;
        this.items = items;
        this.species = species;
        this.weapons = weapons;
        this.companions = companions;
        this.worldEvents = worldEvents;
        this.campaign = campaign;
        guardKey = new NamespacedKey(plugin, "new_city_guard");
        sessionKey = new NamespacedKey(plugin, "patrol_session");
        anchorKey = new NamespacedKey(plugin, "patrol_anchor");
        membersKey = new NamespacedKey(plugin, "patrol_members");
        phaseKey = new NamespacedKey(plugin, "patrol_phase");
        remainingKey = new NamespacedKey(plugin, "patrol_remaining");
        pendingZaochiKey = new NamespacedKey(plugin, "pending_zaochi_remains");
        pendingXingtianKey = new NamespacedKey(plugin, "pending_xingtian_remains");
        pendingBonusKey = new NamespacedKey(plugin, "pending_bonus_remains");
        pendingCompletionKey = new NamespacedKey(plugin, "pending_patrol_completion");
        contractKey = new NamespacedKey(plugin, "patrol_contract");
    }

    public void recover(World world) {
        for (Entity entity : world.getEntities()) {
            if (isAnchor(entity)) {
                recoverAnchor(entity);
            }
        }
        for (PatrolSession session : sessions.values()) {
            Entity anchor = Bukkit.getEntity(session.anchorId);
            if (anchor != null) {
                worldEvents.recover(session.id, "east_patrol", anchor.getLocation(),
                        session.phase == PatrolPhase.COMPLETE_PENDING);
            }
        }
        worldEvents.reconcileRunning("east_patrol", sessions.keySet());
        for (Entity entity : world.getEntities()) {
            UUID id = sessionId(entity);
            PatrolSession session = id == null ? null : sessions.get(id);
            if (session != null && companions.isCompanion(entity)) {
                session.companionId = entity.getUniqueId();
            }
        }
    }

    public void clearRuntimeState() {
        sessions.clear();
        sessionByMember.clear();
        pendingInvites.clear();
        selectedContracts.clear();
    }

    public int runPersistenceSelfTest(Location location) {
        UUID id = UUID.randomUUID();
        UUID memberId = new UUID(0L, 3L);
        PatrolSession original = new PatrolSession(id, location.getWorld().getUID(), Set.of(memberId),
                PatrolPhase.BOSS_READY, PatrolContract.DEEP_FIELD_SCOUT);
        ArmorStand anchor = createAnchor(location, original);
        original.anchorId = anchor.getUniqueId();
        original.remaining = 2;
        original.pendingZaochi.put(memberId, 3);
        original.pendingXingtian.put(memberId, 1);
        original.pendingBonus.put(memberId, 2);
        original.pendingCompletion.add(memberId);
        sessions.put(id, original);
        sessionByMember.put(memberId, id);
        updateAnchor(original);

        sessions.remove(id);
        sessionByMember.remove(memberId);
        recoverAnchor(anchor);
        PatrolSession restored = sessions.get(id);
        int checks = 0;
        if (restored != null) checks++;
        if (restored != null && restored.phase == PatrolPhase.BOSS_READY && restored.remaining == 2) checks++;
        if (restored != null && restored.members.contains(memberId)) checks++;
        if (restored != null && restored.pendingZaochi.getOrDefault(memberId, 0) == 3
                && restored.pendingXingtian.getOrDefault(memberId, 0) == 1) checks++;
        if (restored != null && restored.pendingCompletion.contains(memberId)) checks++;
        if (restored != null && restored.contract == PatrolContract.DEEP_FIELD_SCOUT) checks++;
        if (restored != null && restored.pendingBonus.getOrDefault(memberId, 0) == 2) checks++;
        cleanupSession(id);
        return checks;
    }

    public void setupGuard() {
        Location post = daoFields.guardPost();
        if (post == null) {
            return;
        }
        for (IronGolem golem : post.getWorld().getEntitiesByClass(IronGolem.class)) {
            if (golem.getPersistentDataContainer().has(guardKey, PersistentDataType.BYTE)) {
                golem.remove();
            }
        }
        IronGolem guard = post.getWorld().spawn(post, IronGolem.class);
        guard.getPersistentDataContainer().set(guardKey, PersistentDataType.BYTE, (byte) 1);
        guard.customName(Component.text("撼山巡防員", NamedTextColor.GREEN));
        guard.setCustomNameVisible(true);
        guard.setPlayerCreated(true);
        guard.setPersistent(true);
        setAttribute(guard, Attribute.GENERIC_MAX_HEALTH, 160.0);
        guard.setHealth(160.0);
    }

    public boolean isEncounterEnemy(Entity entity) {
        return species.isSpecies(entity);
    }

    public boolean canTarget(Entity enemy, LivingEntity target) {
        UUID id = sessionId(enemy);
        if (id == null) {
            return true;
        }
        PatrolSession session = sessions.get(id);
        if (session == null) {
            return false;
        }
        if (target instanceof Player player) {
            return session.members.contains(player.getUniqueId());
        }
        return companions.isCombatReady(target) && id.equals(companions.sessionId(target));
    }

    public boolean sameEncounter(Entity first, Entity second) {
        return Objects.equals(sessionId(first), sessionId(second));
    }

    public void spawnXingtian(Player player) {
        PatrolSession session = sessionFor(player);
        if (session == null) {
            spawnStandaloneXingtian(player);
            return;
        }
        if (session.phase == PatrolPhase.BOSS) {
            player.sendMessage(EvilIslandPlugin.message("刑天統領已在東境活動。"));
            return;
        }
        if (session.phase != PatrolPhase.BOSS_READY) {
            player.sendMessage(EvilIslandPlugin.message("先完成東境鑿齒巡防。"));
            return;
        }
        for (UUID memberId : session.members) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null || !member.isOnline() || profiles.transformations(member) == 0) {
                player.sendMessage(EvilIslandPlugin.message("全體隊員都必須在線並完成第一次易質，才能迎戰刑天。"));
                return;
            }
        }

        Location center = daoFields.patrolCenter(player.getWorld());
        if (center == null) {
            return;
        }
        PatrolScaling scaling = scaling(session.members.size());
        session.phase = PatrolPhase.BOSS;
        session.remaining = 3;
        updateAnchor(session);
        Location spawn = ground(center.clone().add(10, 0, 0));
        tag(species.spawnXingtian(spawn,
                scaling.bossHealthMultiplier() * session.contract.bossHealthMultiplier()
                        * campaign.intelligenceEnemyHealthMultiplier() * campaign.weeklyBossHealthMultiplier(),
                scaling.bossDamageMultiplier() * session.contract.bossDamageMultiplier()
                        * campaign.moraleEnemyDamageMultiplier()), session);
        tag(species.spawnZaochi(ground(spawn.clone().add(-4, 0, 3)),
                scaling.zaochiHealthMultiplier() * session.contract.zaochiHealthMultiplier()
                        * campaign.intelligenceEnemyHealthMultiplier(),
                scaling.zaochiDamageMultiplier() * session.contract.zaochiDamageMultiplier()
                        * campaign.moraleEnemyDamageMultiplier()), session);
        tag(species.spawnZaochi(ground(spawn.clone().add(-4, 0, -3)),
                scaling.zaochiHealthMultiplier() * session.contract.zaochiHealthMultiplier()
                        * campaign.intelligenceEnemyHealthMultiplier(),
                scaling.zaochiDamageMultiplier() * session.contract.zaochiDamageMultiplier()
                        * campaign.moraleEnemyDamageMultiplier()), session);
        spawn.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, spawn.clone().add(0, 1, 0),
                4, 1.2, 0.5, 1.2, 0.02);
        forEachOnline(session, member -> {
            profiles.setObjective(member, ObjectiveStage.DEFEAT_XINGTIAN);
            member.sendMessage(EvilIslandPlugin.message("刑天統領率眾逼近新城東境。", NamedTextColor.DARK_RED));
        });
    }

    public void spawnForAdmin(Player player, String type) {
        String normalized = type.toLowerCase(Locale.ROOT);
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() < 0.001) {
            direction.setX(1);
        }
        Location location = ground(player.getLocation().add(direction.normalize().multiply(5)));
        if (normalized.equals(SpeciesType.XINGTIAN.id())) {
            species.spawnXingtian(location);
        } else {
            species.spawnZaochi(location);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGuardInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !event.getRightClicked().getPersistentDataContainer().has(guardKey, PersistentDataType.BYTE)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!profiles.isMeasured(player)) {
            player.sendMessage(EvilIslandPlugin.message("先到聚炁鏡庭接受炁息測定。"));
            return;
        }
        if (!profiles.isFormulaLocked(player)) {
            player.sendMessage(EvilIslandPlugin.message("你的炁息尚未完成存想定型。"));
            return;
        }
        if (!weapons.hasWeapon(player)) {
            player.sendMessage(EvilIslandPlugin.message("巡防前先領取一件歲安軍團登記兵器。", NamedTextColor.YELLOW));
            weapons.openArmory(player);
            return;
        }
        PatrolSession active = sessionFor(player);
        if (active != null) {
            openActiveMenu(player, active);
        } else {
            openContractMenu(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof PatrolMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) {
            return;
        }
        int slot = event.getRawSlot();
        if (holder.type == MenuType.CONTRACT) {
            int option = slot == 11 ? 0 : slot == 13 ? 1 : slot == 15 ? 2 : -1;
            if (option >= 0) {
                List<PatrolContract> board = campaign.board();
                if (option < board.size()) {
                    selectedContracts.put(player.getUniqueId(), board.get(option));
                    openAssemblyMenu(player);
                }
            }
        } else if (holder.type == MenuType.ASSEMBLY) {
            if (slot == 11) {
                player.closeInventory();
                beginPatrol(List.of(player), selectedContract(player));
            } else if (slot == 15) {
                Player partner = nearestPartner(player);
                if (partner == null) {
                    player.sendMessage(EvilIslandPlugin.message("附近沒有已定型、持有兵器且進度相同的可編組玩家。"));
                    return;
                }
                sendDuoInvite(player, partner, selectedContract(player));
            }
        } else if (holder.type == MenuType.ACTIVE && slot == 22) {
            openCancelConfirmation(player, holder.sessionId);
        } else if (holder.type == MenuType.CANCEL) {
            if (slot == 11) {
                PatrolSession session = sessions.get(holder.sessionId);
                if (session != null) {
                    openActiveMenu(player, session);
                }
            } else if (slot == 15) {
                player.closeInventory();
                cancelSession(holder.sessionId);
            }
        } else if (holder.type == MenuType.INVITE) {
            if (slot != 11 && slot != 15) {
                return;
            }
            PendingInvite invite = pendingInvites.remove(player.getUniqueId());
            player.closeInventory();
            if (invite == null || !invite.leaderId.equals(holder.sessionId)
                    || invite.expiresAt < System.currentTimeMillis()) {
                player.sendMessage(EvilIslandPlugin.message("雙人編組邀請已失效。"));
                return;
            }
            Player leader = Bukkit.getPlayer(invite.leaderId);
            if (slot == 15 && leader != null && leader.isOnline()) {
                beginPatrol(List.of(leader, player), invite.contract);
            } else if (leader != null) {
                leader.sendMessage(EvilIslandPlugin.message(player.getName() + "沒有加入本次雙人巡防。"));
            }
        }
    }

    @EventHandler
    public void onEnemyDeath(EntityDeathEvent event) {
        SpeciesType type = species.type(event.getEntity());
        if (type == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        PatrolSession session = sessions.get(sessionId(event.getEntity()));
        if (session == null) {
            event.getEntity().getWorld().dropItemNaturally(event.getEntity().getLocation(),
                    items.createRemains(type.id(), type == SpeciesType.XINGTIAN ? 3 : 1));
            Player killer = event.getEntity().getKiller();
            if (killer != null && profiles.isEnlisted(killer)) {
                if (type == SpeciesType.ZAOCHI) {
                    profiles.recordZaochiKill(killer);
                } else {
                    profiles.setObjective(killer, ObjectiveStage.COMPLETE);
                }
            }
            return;
        }

        if (type == SpeciesType.ZAOCHI) {
            rewardMembers(session, SpeciesType.ZAOCHI, 1);
            session.remaining = Math.max(0, session.remaining - 1);
            if (session.phase == PatrolPhase.PATROL && session.remaining == 0) {
                session.phase = PatrolPhase.BOSS_READY;
                boolean ready = session.members.stream().allMatch(memberId -> {
                    Player member = Bukkit.getPlayer(memberId);
                    return member != null && member.isOnline() && profiles.transformations(member) > 0;
                });
                forEachOnline(session, member -> {
                    profiles.setObjective(member, ready ? ObjectiveStage.DEFEAT_XINGTIAN : ObjectiveStage.REFINE_REMAINS);
                    member.sendMessage(EvilIslandPlugin.message(ready
                            ? "前鋒已清除，刑天統領正率眾逼近。"
                            : "鑿齒巡防完成；全隊返回新城煉化遺骸並完成第一次易質。",
                            NamedTextColor.GREEN));
                });
                if (ready) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Player leader = session.members.stream().map(Bukkit::getPlayer)
                                .filter(Objects::nonNull).filter(Player::isOnline).findFirst().orElse(null);
                        if (leader != null && session.phase == PatrolPhase.BOSS_READY) spawnXingtian(leader);
                    }, 60L);
                }
            }
            updateAnchor(session);
            return;
        }

        rewardMembers(session, SpeciesType.XINGTIAN, 1);
        boolean firstCompletion = campaign.complete(session.contract);
        int completionRemains = session.contract.bonusRemains() + campaign.supplyRewardBonus();
        if (firstCompletion && completionRemains > 0) {
            rewardBonusRemains(session, completionRemains);
        }
        for (UUID memberId : session.members) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                profiles.setObjective(member, ObjectiveStage.COMPLETE);
            } else {
                session.pendingCompletion.add(memberId);
            }
        }
        session.phase = PatrolPhase.COMPLETE_PENDING;
        session.remaining = 0;
        updateAnchor(session);
        worldEvents.transition(session.id, WorldEventState.SUCCEEDED);
        Bukkit.getServer().broadcast(EvilIslandPlugin.message(
                displayMembers(session) + "完成「" + session.contract.display() + "」；"
                        + (firstCompletion ? session.contract.metric().display() + "獲得提升。" : "今日城況獎勵已結算。"),
                NamedTextColor.GREEN));
        removeSessionActors(session);
        if (session.pendingCompletion.isEmpty() && session.pendingZaochi.isEmpty()
                && session.pendingXingtian.isEmpty() && session.pendingBonus.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> cleanupSession(session.id));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> restoreMember(event.getPlayer()), 10L);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isAnchor(entity)) {
                recoverAnchor(entity);
            }
        }
        for (Entity entity : event.getEntities()) {
            UUID id = sessionId(entity);
            PatrolSession session = id == null ? null : sessions.get(id);
            if (session != null && companions.isCompanion(entity)) {
                session.companionId = entity.getUniqueId();
            }
        }
    }

    private void beginPatrol(List<Player> members, PatrolContract contract) {
        if (members.isEmpty() || members.size() > 2) {
            return;
        }
        World world = members.get(0).getWorld();
        if (sessions.values().stream().anyMatch(session -> session.world.equals(world.getUID())
                && session.phase != PatrolPhase.COMPLETE_PENDING)) {
            members.get(0).sendMessage(EvilIslandPlugin.message("東境巡防區已有另一支隊伍執行任務。"));
            return;
        }
        for (Player member : members) {
            if (!canJoin(member) || !member.getWorld().equals(world)) {
                members.get(0).sendMessage(EvilIslandPlugin.message("隊員狀態已改變，無法開始巡防。"));
                return;
            }
        }
        Location center = daoFields.patrolCenter(world);
        if (center == null) {
            members.get(0).sendMessage(EvilIslandPlugin.message("巡防區尚未完成設定。"));
            return;
        }

        UUID id = UUID.randomUUID();
        Set<UUID> memberIds = new HashSet<>();
        members.forEach(member -> memberIds.add(member.getUniqueId()));
        PatrolSession session = new PatrolSession(id, world.getUID(), memberIds,
                PatrolPhase.PATROL, contract);
        ArmorStand anchor = createAnchor(center, session);
        session.anchorId = anchor.getUniqueId();
        sessions.put(id, session);
        memberIds.forEach(memberId -> sessionByMember.put(memberId, id));
        memberIds.forEach(selectedContracts::remove);
        worldEvents.create(id, "east_patrol", center);

        PatrolScaling scaling = scaling(members.size());
        if (scaling.companion()) {
            LivingEntity companion = companions.spawn(members.get(0).getLocation(), members.get(0), id);
            tag(companion, session);
            session.companionId = companion.getUniqueId();
        }
        int zaochiCount = Math.max(1, scaling.zaochiCount() + contract.extraZaochi()
                + campaign.defenseEnemyModifier() + campaign.weeklyEnemyModifier());
        session.remaining = zaochiCount;
        for (int index = 0; index < zaochiCount; index++) {
            double angle = Math.PI * 2.0 * index / zaochiCount;
            Location spawn = ground(center.clone().add(Math.cos(angle) * contract.spawnRadius(), 0,
                    Math.sin(angle) * contract.spawnRadius()));
            tag(species.spawnZaochi(spawn,
                    scaling.zaochiHealthMultiplier() * contract.zaochiHealthMultiplier()
                            * campaign.intelligenceEnemyHealthMultiplier(),
                    scaling.zaochiDamageMultiplier() * contract.zaochiDamageMultiplier()
                            * campaign.moraleEnemyDamageMultiplier()), session);
        }
        updateAnchor(session);
        worldEvents.transition(id, WorldEventState.ACTIVE);
        for (Player member : members) {
            profiles.setObjective(member, ObjectiveStage.HUNT_ZAOCHI);
            member.sendMessage(EvilIslandPlugin.message("巡防編組完成：" + displayMembers(session)
                    + "，任務「" + contract.display() + "」。", NamedTextColor.GREEN));
            member.sendMessage(EvilIslandPlugin.message("鑿齒小隊出現在東門外高道息區。", NamedTextColor.RED));
        }
    }

    private void rewardMembers(PatrolSession session, SpeciesType type, int count) {
        int purity = type == SpeciesType.XINGTIAN ? 3 : 1;
        for (UUID memberId : session.members) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                for (int index = 0; index < count; index++) {
                    give(member, items.createRemains(type.id(), purity));
                }
                if (type == SpeciesType.ZAOCHI) {
                    profiles.recordZaochiKill(member);
                    member.sendMessage(EvilIslandPlugin.message("全隊取得一份鑿齒遺骸。"));
                } else {
                    member.sendMessage(EvilIslandPlugin.message("全隊取得一份刑天遺骸。", NamedTextColor.GOLD));
                }
            } else {
                Map<UUID, Integer> pending = type == SpeciesType.ZAOCHI
                        ? session.pendingZaochi : session.pendingXingtian;
                pending.merge(memberId, count, Integer::sum);
            }
        }
    }

    private void rewardBonusRemains(PatrolSession session, int count) {
        for (UUID memberId : session.members) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                for (int index = 0; index < count; index++) {
                    give(member, items.createRemains(SpeciesType.ZAOCHI.id(), 1));
                }
                member.sendMessage(EvilIslandPlugin.message("任務與城況加發 " + count + " 份遺骸。",
                        NamedTextColor.GOLD));
            } else {
                session.pendingBonus.merge(memberId, count, Integer::sum);
            }
        }
    }

    private void restoreMember(Player player) {
        PatrolSession session = sessionFor(player);
        if (session == null) {
            return;
        }
        Integer zaochiValue = session.pendingZaochi.remove(player.getUniqueId());
        int zaochi = zaochiValue == null ? 0 : zaochiValue;
        Integer xingtianValue = session.pendingXingtian.remove(player.getUniqueId());
        int xingtian = xingtianValue == null ? 0 : xingtianValue;
        Integer bonusValue = session.pendingBonus.remove(player.getUniqueId());
        int bonus = bonusValue == null ? 0 : bonusValue;
        for (int index = 0; index < zaochi; index++) {
            give(player, items.createRemains(SpeciesType.ZAOCHI.id(), 1));
            profiles.recordZaochiKill(player);
        }
        for (int index = 0; index < xingtian; index++) {
            give(player, items.createRemains(SpeciesType.XINGTIAN.id(), 3));
        }
        for (int index = 0; index < bonus; index++) {
            give(player, items.createRemains(SpeciesType.ZAOCHI.id(), 1));
        }
        if (zaochi + xingtian + bonus > 0) {
            player.sendMessage(EvilIslandPlugin.message("已補發離線期間的巡防遺骸。", NamedTextColor.GOLD));
        }
        if (session.pendingCompletion.remove(player.getUniqueId())) {
            profiles.setObjective(player, ObjectiveStage.COMPLETE);
            player.sendMessage(EvilIslandPlugin.message("東境巡防結算已恢復。", NamedTextColor.GREEN));
        } else if (session.phase == PatrolPhase.BOSS_READY) {
            boolean ready = session.members.stream().allMatch(memberId -> {
                Player member = Bukkit.getPlayer(memberId);
                return member != null && member.isOnline() && profiles.transformations(member) > 0;
            });
            profiles.setObjective(player, ready ? ObjectiveStage.DEFEAT_XINGTIAN : ObjectiveStage.REFINE_REMAINS);
            if (ready) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (session.phase == PatrolPhase.BOSS_READY && player.isOnline()) spawnXingtian(player);
                }, 20L);
            }
        } else if (session.phase == PatrolPhase.BOSS) {
            profiles.setObjective(player, ObjectiveStage.DEFEAT_XINGTIAN);
        }
        updateAnchor(session);
        if (session.phase == PatrolPhase.COMPLETE_PENDING && session.pendingCompletion.isEmpty()
                && session.pendingZaochi.isEmpty() && session.pendingXingtian.isEmpty()
                && session.pendingBonus.isEmpty()) {
            cleanupSession(session.id);
        }
    }

    private void openAssemblyMenu(Player player) {
        PatrolMenuHolder holder = new PatrolMenuHolder(MenuType.ASSEMBLY, null);
        Inventory inventory = createInventory(holder, "東境巡防編組");
        PatrolContract contract = selectedContract(player);
        inventory.setItem(4, menuItem(Material.WRITABLE_BOOK, contract.display(), NamedTextColor.GOLD,
                contractLore(contract)));
        inventory.setItem(11, menuItem(Material.PLAYER_HEAD, "單人巡防", NamedTextColor.AQUA,
                List.of("由一名揚武巡防員提供遠程支援。", "敵軍依單人規模調整。")));
        Player partner = nearestPartner(player);
        inventory.setItem(15, menuItem(partner == null ? Material.GRAY_DYE : Material.TOTEM_OF_UNDYING,
                partner == null ? "雙人編組不可用" : "與「" + partner.getName() + "」雙人巡防",
                partner == null ? NamedTextColor.GRAY : NamedTextColor.GREEN,
                List.of(partner == null ? "附近沒有進度相同的合格隊員。" : "雙人模式會增加敵軍數量與強度。")));
        player.openInventory(inventory);
    }

    private void openContractMenu(Player player) {
        PatrolMenuHolder holder = new PatrolMenuHolder(MenuType.CONTRACT, null);
        Inventory inventory = createInventory(holder, "輕疾巡防公告");
        CampaignSnapshot state = campaign.state();
        inventory.setItem(4, menuItem(Material.RECOVERY_COMPASS, campaign.scheduleText(), NamedTextColor.AQUA,
                List.of(campaign.metricsText(), campaign.activeModifierText(), state.completedToday()
                        ? "今日城況獎勵已結算，仍可出勤取得遺骸。"
                        : "今日首次完成任務會改變新城城況。")));
        int[] slots = {11, 13, 15};
        List<PatrolContract> board = campaign.board();
        for (int index = 0; index < board.size(); index++) {
            PatrolContract contract = board.get(index);
            Material icon = switch (contract.metric()) {
                case DEFENSE -> Material.SHIELD;
                case SUPPLY -> Material.CHEST;
                case INTELLIGENCE -> Material.SPYGLASS;
                case MORALE -> Material.BELL;
            };
            inventory.setItem(slots[index], menuItem(icon, contract.display(), NamedTextColor.GOLD,
                    contractLore(contract)));
        }
        player.openInventory(inventory);
    }

    private PatrolContract selectedContract(Player player) {
        PatrolContract selected = selectedContracts.get(player.getUniqueId());
        List<PatrolContract> board = campaign.board();
        return selected != null && board.contains(selected) ? selected : board.get(0);
    }

    private List<String> contractLore(PatrolContract contract) {
        return List.of(contract.summary(), "影響：" + contract.metric().display() + " +" + contract.stateReward(),
                "危險：" + "◆".repeat(contract.risk()) + "◇".repeat(4 - contract.risk()),
                contract.bonusRemains() == 0 ? "額外報酬：無" : "額外報酬：遺骸 " + contract.bonusRemains());
    }

    private void openActiveMenu(Player player, PatrolSession session) {
        PatrolMenuHolder holder = new PatrolMenuHolder(MenuType.ACTIVE, session.id);
        Inventory inventory = createInventory(holder, "目前巡防編組");
        inventory.setItem(13, menuItem(Material.COMPASS, phaseDisplay(session), NamedTextColor.AQUA,
                List.of("任務：" + session.contract.display(), "隊員：" + displayMembers(session),
                        "剩餘敵軍：" + session.remaining)));
        inventory.setItem(22, menuItem(Material.BARRIER, "終止本次巡防", NamedTextColor.RED,
                List.of("移除本次敵軍與 NPC，保留角色既有成長。")));
        player.openInventory(inventory);
    }

    private void openCancelConfirmation(Player player, UUID sessionId) {
        PatrolMenuHolder holder = new PatrolMenuHolder(MenuType.CANCEL, sessionId);
        Inventory inventory = createInventory(holder, "確認終止巡防");
        inventory.setItem(11, menuItem(Material.LIME_CONCRETE, "返回編組", NamedTextColor.GREEN, List.of()));
        inventory.setItem(15, menuItem(Material.RED_CONCRETE, "確認終止", NamedTextColor.RED,
                List.of("全隊將返回可重新編組狀態。")));
        player.openInventory(inventory);
    }

    private void sendDuoInvite(Player leader, Player partner, PatrolContract contract) {
        long expiresAt = System.currentTimeMillis()
                + plugin.getConfig().getLong("patrol-party.invite-timeout-ms", 15000L);
        pendingInvites.put(partner.getUniqueId(), new PendingInvite(leader.getUniqueId(), expiresAt, contract));
        PatrolMenuHolder holder = new PatrolMenuHolder(MenuType.INVITE, leader.getUniqueId());
        Inventory inventory = createInventory(holder, "雙人巡防邀請");
        inventory.setItem(13, menuItem(Material.PLAYER_HEAD, leader.getName() + "邀請你加入巡防",
                NamedTextColor.AQUA, List.of("任務：" + contract.display(), "雙人模式會提高敵軍數量與強度。")));
        inventory.setItem(11, menuItem(Material.RED_CONCRETE, "婉拒", NamedTextColor.RED, List.of()));
        inventory.setItem(15, menuItem(Material.LIME_CONCRETE, "加入編組", NamedTextColor.GREEN, List.of()));
        leader.closeInventory();
        partner.openInventory(inventory);
        leader.sendMessage(EvilIslandPlugin.message("已向 " + partner.getName() + " 發出雙人巡防邀請。"));
        partner.sendMessage(EvilIslandPlugin.message("請在編組介面確認是否加入。", NamedTextColor.YELLOW));
        long delay = Math.max(20L, (expiresAt - System.currentTimeMillis() + 49L) / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> pendingInvites.remove(partner.getUniqueId(),
                new PendingInvite(leader.getUniqueId(), expiresAt, contract)), delay);
    }

    private void cancelSession(UUID sessionId) {
        PatrolSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        forEachOnline(session, member -> {
            profiles.setObjective(member, profiles.transformations(member) > 0
                    ? ObjectiveStage.DEFEAT_XINGTIAN : ObjectiveStage.REPORT_PATROL);
            member.sendMessage(EvilIslandPlugin.message("本次巡防已終止，可重新向撼山巡防員編組。"));
        });
        worldEvents.transition(session.id, WorldEventState.FAILED);
        removeSessionActors(session);
        cleanupSession(session.id);
    }

    private void removeSessionActors(PatrolSession session) {
        World world = Bukkit.getWorld(session.world);
        if (world == null) {
            return;
        }
        for (Entity entity : new ArrayList<>(world.getEntities())) {
            if (session.id.equals(sessionId(entity)) && !isAnchor(entity)) {
                entity.remove();
            }
        }
        if (session.companionId != null) {
            companions.remove(session.companionId);
            session.companionId = null;
        }
    }

    private void cleanupSession(UUID sessionId) {
        PatrolSession session = sessions.remove(sessionId);
        if (session == null) {
            return;
        }
        session.members.forEach(sessionByMember::remove);
        Entity anchor = Bukkit.getEntity(session.anchorId);
        if (anchor != null) {
            anchor.getChunk().setForceLoaded(false);
            anchor.remove();
        }
    }

    private void spawnStandaloneXingtian(Player player) {
        Location center = daoFields.patrolCenter(player.getWorld());
        if (center == null) {
            return;
        }
        boolean exists = center.getWorld().getNearbyEntities(center, 64, 32, 64).stream()
                .anyMatch(entity -> species.type(entity) == SpeciesType.XINGTIAN);
        if (exists) {
            player.sendMessage(EvilIslandPlugin.message("刑天統領已在東境活動。"));
            return;
        }
        Location spawn = ground(center.clone().add(10, 0, 0));
        species.spawnXingtian(spawn);
        species.spawnZaochi(ground(spawn.clone().add(-4, 0, 3)));
        species.spawnZaochi(ground(spawn.clone().add(-4, 0, -3)));
        Bukkit.getServer().broadcast(EvilIslandPlugin.message("刑天統領率眾逼近新城東境。", NamedTextColor.DARK_RED));
    }

    private Player nearestPartner(Player leader) {
        double radius = plugin.getConfig().getDouble("patrol-party.assembly-radius", 12.0);
        Player best = null;
        double bestDistance = radius * radius;
        for (Player candidate : leader.getWorld().getPlayers()) {
            if (candidate.equals(leader) || !canJoin(candidate)
                    || profiles.transformations(candidate) != profiles.transformations(leader)) {
                continue;
            }
            double distance = candidate.getLocation().distanceSquared(leader.getLocation());
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean canJoin(Player player) {
        return profiles.isEnlisted(player) && weapons.hasWeapon(player)
                && sessionFor(player) == null;
    }

    private PatrolScaling scaling(int players) {
        return PatrolScaling.forPlayers(players,
                plugin.getConfig().getInt("encounters.patrol.zaochi-count", 3),
                plugin.getConfig().getInt("patrol-party.scaling.zaochi-per-extra-player", 2),
                plugin.getConfig().getDouble("patrol-party.scaling.zaochi-health-per-extra-player", 0.35),
                plugin.getConfig().getDouble("patrol-party.scaling.zaochi-damage-per-extra-player", 0.12),
                plugin.getConfig().getDouble("patrol-party.scaling.boss-health-per-extra-player", 0.45),
                plugin.getConfig().getDouble("patrol-party.scaling.boss-damage-per-extra-player", 0.15));
    }

    private ArmorStand createAnchor(Location location, PatrolSession session) {
        location.getChunk().setForceLoaded(true);
        ArmorStand anchor = location.getWorld().spawn(location, ArmorStand.class);
        anchor.setInvisible(true);
        anchor.setMarker(true);
        anchor.setGravity(false);
        anchor.setInvulnerable(true);
        anchor.setPersistent(true);
        anchor.getPersistentDataContainer().set(anchorKey, PersistentDataType.BYTE, (byte) 1);
        tag(anchor, session);
        return anchor;
    }

    private void tag(Entity entity, PatrolSession session) {
        entity.getPersistentDataContainer().set(sessionKey, PersistentDataType.STRING, session.id.toString());
    }

    private void updateAnchor(PatrolSession session) {
        Entity anchor = Bukkit.getEntity(session.anchorId);
        if (anchor == null || !isAnchor(anchor)) {
            return;
        }
        PersistentDataContainer data = anchor.getPersistentDataContainer();
        data.set(membersKey, PersistentDataType.STRING, encodeMembers(session.members));
        data.set(phaseKey, PersistentDataType.STRING, session.phase.id());
        data.set(remainingKey, PersistentDataType.INTEGER, session.remaining);
        writeCounts(data, pendingZaochiKey, session.pendingZaochi);
        writeCounts(data, pendingXingtianKey, session.pendingXingtian);
        writeCounts(data, pendingBonusKey, session.pendingBonus);
        data.set(pendingCompletionKey, PersistentDataType.STRING, encodeMembers(session.pendingCompletion));
        data.set(contractKey, PersistentDataType.STRING, session.contract.id());
    }

    private void recoverAnchor(Entity anchor) {
        UUID id = sessionId(anchor);
        PatrolPhase phase = PatrolPhase.parse(anchor.getPersistentDataContainer().get(phaseKey, PersistentDataType.STRING));
        Set<UUID> members = decodeMembers(anchor.getPersistentDataContainer().get(membersKey, PersistentDataType.STRING));
        PatrolContract contract = PatrolContract.parse(
                anchor.getPersistentDataContainer().get(contractKey, PersistentDataType.STRING));
        if (id == null || phase == null || members.isEmpty() || anchor.getWorld() == null) {
            anchor.getChunk().setForceLoaded(false);
            anchor.remove();
            return;
        }
        PatrolSession session = sessions.computeIfAbsent(id,
                ignored -> new PatrolSession(id, anchor.getWorld().getUID(), members, phase,
                        contract == null ? PatrolContract.EAST_CLEARANCE : contract));
        anchor.getChunk().setForceLoaded(true);
        session.anchorId = anchor.getUniqueId();
        session.phase = phase;
        Integer remaining = anchor.getPersistentDataContainer().get(remainingKey, PersistentDataType.INTEGER);
        session.remaining = remaining == null ? 0 : Math.max(0, remaining);
        session.pendingZaochi.putAll(readCounts(anchor.getPersistentDataContainer(), pendingZaochiKey));
        session.pendingXingtian.putAll(readCounts(anchor.getPersistentDataContainer(), pendingXingtianKey));
        session.pendingBonus.putAll(readCounts(anchor.getPersistentDataContainer(), pendingBonusKey));
        session.pendingCompletion.addAll(decodeMembers(
                anchor.getPersistentDataContainer().get(pendingCompletionKey, PersistentDataType.STRING)));
        members.forEach(memberId -> sessionByMember.put(memberId, id));
    }

    private PatrolSession sessionFor(Player player) {
        UUID id = sessionByMember.get(player.getUniqueId());
        return id == null ? null : sessions.get(id);
    }

    private UUID sessionId(Entity entity) {
        if (entity == null) {
            return null;
        }
        String value = entity.getPersistentDataContainer().get(sessionKey, PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isAnchor(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(anchorKey, PersistentDataType.BYTE);
    }

    private void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack remaining : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), remaining);
        }
    }

    private String displayMembers(PatrolSession session) {
        List<String> names = new ArrayList<>();
        for (UUID memberId : session.members) {
            Player player = Bukkit.getPlayer(memberId);
            names.add(player == null ? memberId.toString().substring(0, 8) : player.getName());
        }
        return String.join("、", names);
    }

    private String phaseDisplay(PatrolSession session) {
        return switch (session.phase) {
            case PATROL -> "鑿齒巡防進行中";
            case BOSS_READY -> "等待全隊完成易質";
            case BOSS -> "刑天迎擊進行中";
            case COMPLETE_PENDING -> "等待離線隊員結算";
        };
    }

    private void forEachOnline(PatrolSession session, java.util.function.Consumer<Player> action) {
        for (UUID memberId : session.members) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null && player.isOnline()) {
                action.accept(player);
            }
        }
    }

    private Inventory createInventory(PatrolMenuHolder holder, String title) {
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text(title));
        holder.inventory = inventory;
        return inventory;
    }

    private ItemStack menuItem(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color));
        meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private Location ground(Location location) {
        World world = location.getWorld();
        int y = world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ()) + 1;
        return new Location(world, location.getBlockX() + 0.5, y, location.getBlockZ() + 0.5);
    }

    private void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private String encodeMembers(Set<UUID> members) {
        return members.stream().map(UUID::toString).sorted().reduce((left, right) -> left + "," + right).orElse("");
    }

    private Set<UUID> decodeMembers(String encoded) {
        Set<UUID> result = new HashSet<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        for (String value : encoded.split(",")) {
            try {
                result.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                // Ignore one corrupt member without discarding the entire session.
            }
        }
        return result;
    }

    private void writeCounts(PersistentDataContainer data, NamespacedKey key, Map<UUID, Integer> counts) {
        String encoded = counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .sorted()
                .reduce((left, right) -> left + "," + right).orElse("");
        data.set(key, PersistentDataType.STRING, encoded);
    }

    private Map<UUID, Integer> readCounts(PersistentDataContainer data, NamespacedKey key) {
        Map<UUID, Integer> result = new HashMap<>();
        String encoded = data.get(key, PersistentDataType.STRING);
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        for (String entry : encoded.split(",")) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                result.put(UUID.fromString(parts[0]), Math.max(0, Integer.parseInt(parts[1])));
            } catch (IllegalArgumentException ignored) {
                // Ignore a corrupt reward entry and recover the remaining session.
            }
        }
        return result;
    }

    private enum MenuType {
        CONTRACT,
        ASSEMBLY,
        ACTIVE,
        CANCEL,
        INVITE
    }

    private static final class PatrolMenuHolder implements InventoryHolder {
        private final MenuType type;
        private final UUID sessionId;
        private Inventory inventory;

        private PatrolMenuHolder(MenuType type, UUID sessionId) {
            this.type = type;
            this.sessionId = sessionId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class PatrolSession {
        private final UUID id;
        private final UUID world;
        private final Set<UUID> members;
        private final Map<UUID, Integer> pendingZaochi = new HashMap<>();
        private final Map<UUID, Integer> pendingXingtian = new HashMap<>();
        private final Map<UUID, Integer> pendingBonus = new HashMap<>();
        private final Set<UUID> pendingCompletion = new HashSet<>();
        private final PatrolContract contract;
        private PatrolPhase phase;
        private UUID anchorId;
        private UUID companionId;
        private int remaining;

        private PatrolSession(UUID id, UUID world, Set<UUID> members, PatrolPhase phase, PatrolContract contract) {
            this.id = id;
            this.world = world;
            this.members = new HashSet<>(members);
            this.phase = phase;
            this.contract = contract;
        }
    }

    private record PendingInvite(UUID leaderId, long expiresAt, PatrolContract contract) {
    }
}
