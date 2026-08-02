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
import tw.zack.evilisland.model.SpeciesType;

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
    private final NamespacedKey guardKey;
    private final NamespacedKey sessionKey;
    private final NamespacedKey anchorKey;
    private final NamespacedKey membersKey;
    private final NamespacedKey phaseKey;
    private final NamespacedKey remainingKey;
    private final NamespacedKey pendingZaochiKey;
    private final NamespacedKey pendingXingtianKey;
    private final NamespacedKey pendingCompletionKey;
    private final Map<UUID, PatrolSession> sessions = new HashMap<>();
    private final Map<UUID, UUID> sessionByMember = new HashMap<>();
    private final Map<UUID, PendingInvite> pendingInvites = new HashMap<>();

    public EncounterService(EvilIslandPlugin plugin, PlayerProfileService profiles, DaoFieldService daoFields,
                            GameItemService items, SpeciesService species, WeaponService weapons,
                            CompanionService companions) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.daoFields = daoFields;
        this.items = items;
        this.species = species;
        this.weapons = weapons;
        this.companions = companions;
        guardKey = new NamespacedKey(plugin, "new_city_guard");
        sessionKey = new NamespacedKey(plugin, "patrol_session");
        anchorKey = new NamespacedKey(plugin, "patrol_anchor");
        membersKey = new NamespacedKey(plugin, "patrol_members");
        phaseKey = new NamespacedKey(plugin, "patrol_phase");
        remainingKey = new NamespacedKey(plugin, "patrol_remaining");
        pendingZaochiKey = new NamespacedKey(plugin, "pending_zaochi_remains");
        pendingXingtianKey = new NamespacedKey(plugin, "pending_xingtian_remains");
        pendingCompletionKey = new NamespacedKey(plugin, "pending_patrol_completion");
    }

    public void recover(World world) {
        for (Entity entity : world.getEntities()) {
            if (isAnchor(entity)) {
                recoverAnchor(entity);
            }
        }
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
    }

    public int runPersistenceSelfTest(Location location) {
        UUID id = UUID.randomUUID();
        UUID memberId = new UUID(0L, 3L);
        PatrolSession original = new PatrolSession(id, location.getWorld().getUID(), Set.of(memberId),
                PatrolPhase.BOSS_READY);
        ArmorStand anchor = createAnchor(location, original);
        original.anchorId = anchor.getUniqueId();
        original.remaining = 2;
        original.pendingZaochi.put(memberId, 3);
        original.pendingXingtian.put(memberId, 1);
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
        tag(species.spawnXingtian(spawn, scaling.bossHealthMultiplier(), scaling.bossDamageMultiplier()), session);
        tag(species.spawnZaochi(ground(spawn.clone().add(-4, 0, 3)),
                scaling.zaochiHealthMultiplier(), scaling.zaochiDamageMultiplier()), session);
        tag(species.spawnZaochi(ground(spawn.clone().add(-4, 0, -3)),
                scaling.zaochiHealthMultiplier(), scaling.zaochiDamageMultiplier()), session);
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
        if (profiles.objective(player) == ObjectiveStage.COMPLETE) {
            player.sendMessage(EvilIslandPlugin.message("本輪東境巡防已經完成。"));
            return;
        }
        PatrolSession active = sessionFor(player);
        if (active != null) {
            openActiveMenu(player, active);
        } else {
            openAssemblyMenu(player);
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
        if (holder.type == MenuType.ASSEMBLY) {
            if (slot == 11) {
                player.closeInventory();
                beginPatrol(List.of(player));
            } else if (slot == 15) {
                Player partner = nearestPartner(player);
                if (partner == null) {
                    player.sendMessage(EvilIslandPlugin.message("附近沒有已定型、持有兵器且進度相同的可編組玩家。"));
                    return;
                }
                sendDuoInvite(player, partner);
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
                beginPatrol(List.of(leader, player));
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
                forEachOnline(session, member -> {
                    profiles.setObjective(member, ObjectiveStage.REFINE_REMAINS);
                    member.sendMessage(EvilIslandPlugin.message(
                            "鑿齒巡防完成；全隊返回新城煉化遺骸並完成第一次易質。", NamedTextColor.GREEN));
                });
            }
            updateAnchor(session);
            return;
        }

        rewardMembers(session, SpeciesType.XINGTIAN, 1);
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
        Bukkit.getServer().broadcast(EvilIslandPlugin.message(
                displayMembers(session) + "擊倒刑天統領，東境巡防暫告完成。", NamedTextColor.GREEN));
        removeSessionActors(session);
        if (session.pendingCompletion.isEmpty() && session.pendingZaochi.isEmpty() && session.pendingXingtian.isEmpty()) {
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

    private void beginPatrol(List<Player> members) {
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
        boolean bossOnly = members.stream().allMatch(member -> profiles.transformations(member) > 0);
        PatrolSession session = new PatrolSession(id, world.getUID(), memberIds,
                bossOnly ? PatrolPhase.BOSS_READY : PatrolPhase.PATROL);
        ArmorStand anchor = createAnchor(center, session);
        session.anchorId = anchor.getUniqueId();
        sessions.put(id, session);
        memberIds.forEach(memberId -> sessionByMember.put(memberId, id));

        PatrolScaling scaling = scaling(members.size());
        if (scaling.companion()) {
            LivingEntity companion = companions.spawn(members.get(0).getLocation(), members.get(0), id);
            tag(companion, session);
            session.companionId = companion.getUniqueId();
        }
        if (bossOnly) {
            updateAnchor(session);
            spawnXingtian(members.get(0));
            return;
        }

        session.remaining = scaling.zaochiCount();
        for (int index = 0; index < scaling.zaochiCount(); index++) {
            double angle = Math.PI * 2.0 * index / scaling.zaochiCount();
            Location spawn = ground(center.clone().add(Math.cos(angle) * 6.0, 0, Math.sin(angle) * 6.0));
            tag(species.spawnZaochi(spawn, scaling.zaochiHealthMultiplier(), scaling.zaochiDamageMultiplier()), session);
        }
        updateAnchor(session);
        for (Player member : members) {
            profiles.setObjective(member, ObjectiveStage.HUNT_ZAOCHI);
            member.sendMessage(EvilIslandPlugin.message("巡防編組完成：" + displayMembers(session) + "。", NamedTextColor.GREEN));
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

    private void restoreMember(Player player) {
        PatrolSession session = sessionFor(player);
        if (session == null) {
            return;
        }
        Integer zaochiValue = session.pendingZaochi.remove(player.getUniqueId());
        int zaochi = zaochiValue == null ? 0 : zaochiValue;
        Integer xingtianValue = session.pendingXingtian.remove(player.getUniqueId());
        int xingtian = xingtianValue == null ? 0 : xingtianValue;
        for (int index = 0; index < zaochi; index++) {
            give(player, items.createRemains(SpeciesType.ZAOCHI.id(), 1));
            profiles.recordZaochiKill(player);
        }
        for (int index = 0; index < xingtian; index++) {
            give(player, items.createRemains(SpeciesType.XINGTIAN.id(), 3));
        }
        if (zaochi + xingtian > 0) {
            player.sendMessage(EvilIslandPlugin.message("已補發離線期間的巡防遺骸。", NamedTextColor.GOLD));
        }
        if (session.pendingCompletion.remove(player.getUniqueId())) {
            profiles.setObjective(player, ObjectiveStage.COMPLETE);
            player.sendMessage(EvilIslandPlugin.message("東境巡防結算已恢復。", NamedTextColor.GREEN));
        } else if (session.phase == PatrolPhase.BOSS_READY) {
            profiles.setObjective(player, ObjectiveStage.REFINE_REMAINS);
        } else if (session.phase == PatrolPhase.BOSS) {
            profiles.setObjective(player, ObjectiveStage.DEFEAT_XINGTIAN);
        }
        updateAnchor(session);
        if (session.phase == PatrolPhase.COMPLETE_PENDING && session.pendingCompletion.isEmpty()
                && session.pendingZaochi.isEmpty() && session.pendingXingtian.isEmpty()) {
            cleanupSession(session.id);
        }
    }

    private void openAssemblyMenu(Player player) {
        PatrolMenuHolder holder = new PatrolMenuHolder(MenuType.ASSEMBLY, null);
        Inventory inventory = createInventory(holder, "東境巡防編組");
        inventory.setItem(11, menuItem(Material.PLAYER_HEAD, "單人巡防", NamedTextColor.AQUA,
                List.of("由一名揚武巡防員提供遠程支援。")));
        Player partner = nearestPartner(player);
        inventory.setItem(15, menuItem(partner == null ? Material.GRAY_DYE : Material.TOTEM_OF_UNDYING,
                partner == null ? "雙人編組不可用" : "與「" + partner.getName() + "」雙人巡防",
                partner == null ? NamedTextColor.GRAY : NamedTextColor.GREEN,
                List.of(partner == null ? "附近沒有進度相同的合格隊員。" : "雙人模式會增加敵軍數量與強度。")));
        player.openInventory(inventory);
    }

    private void openActiveMenu(Player player, PatrolSession session) {
        PatrolMenuHolder holder = new PatrolMenuHolder(MenuType.ACTIVE, session.id);
        Inventory inventory = createInventory(holder, "目前巡防編組");
        inventory.setItem(13, menuItem(Material.COMPASS, phaseDisplay(session), NamedTextColor.AQUA,
                List.of("隊員：" + displayMembers(session), "剩餘敵軍：" + session.remaining)));
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

    private void sendDuoInvite(Player leader, Player partner) {
        long expiresAt = System.currentTimeMillis()
                + plugin.getConfig().getLong("patrol-party.invite-timeout-ms", 15000L);
        pendingInvites.put(partner.getUniqueId(), new PendingInvite(leader.getUniqueId(), expiresAt));
        PatrolMenuHolder holder = new PatrolMenuHolder(MenuType.INVITE, leader.getUniqueId());
        Inventory inventory = createInventory(holder, "雙人巡防邀請");
        inventory.setItem(13, menuItem(Material.PLAYER_HEAD, leader.getName() + "邀請你加入巡防",
                NamedTextColor.AQUA, List.of("雙人模式會提高敵軍數量與強度。")));
        inventory.setItem(11, menuItem(Material.RED_CONCRETE, "婉拒", NamedTextColor.RED, List.of()));
        inventory.setItem(15, menuItem(Material.LIME_CONCRETE, "加入編組", NamedTextColor.GREEN, List.of()));
        leader.closeInventory();
        partner.openInventory(inventory);
        leader.sendMessage(EvilIslandPlugin.message("已向 " + partner.getName() + " 發出雙人巡防邀請。"));
        partner.sendMessage(EvilIslandPlugin.message("請在編組介面確認是否加入。", NamedTextColor.YELLOW));
        long delay = Math.max(20L, (expiresAt - System.currentTimeMillis() + 49L) / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> pendingInvites.remove(partner.getUniqueId(),
                new PendingInvite(leader.getUniqueId(), expiresAt)), delay);
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
                && profiles.objective(player) != ObjectiveStage.COMPLETE && sessionFor(player) == null;
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
        data.set(pendingCompletionKey, PersistentDataType.STRING, encodeMembers(session.pendingCompletion));
    }

    private void recoverAnchor(Entity anchor) {
        UUID id = sessionId(anchor);
        PatrolPhase phase = PatrolPhase.parse(anchor.getPersistentDataContainer().get(phaseKey, PersistentDataType.STRING));
        Set<UUID> members = decodeMembers(anchor.getPersistentDataContainer().get(membersKey, PersistentDataType.STRING));
        if (id == null || phase == null || members.isEmpty() || anchor.getWorld() == null) {
            anchor.getChunk().setForceLoaded(false);
            anchor.remove();
            return;
        }
        PatrolSession session = sessions.computeIfAbsent(id,
                ignored -> new PatrolSession(id, anchor.getWorld().getUID(), members, phase));
        anchor.getChunk().setForceLoaded(true);
        session.anchorId = anchor.getUniqueId();
        session.phase = phase;
        Integer remaining = anchor.getPersistentDataContainer().get(remainingKey, PersistentDataType.INTEGER);
        session.remaining = remaining == null ? 0 : Math.max(0, remaining);
        session.pendingZaochi.putAll(readCounts(anchor.getPersistentDataContainer(), pendingZaochiKey));
        session.pendingXingtian.putAll(readCounts(anchor.getPersistentDataContainer(), pendingXingtianKey));
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
        private final Set<UUID> pendingCompletion = new HashSet<>();
        private PatrolPhase phase;
        private UUID anchorId;
        private UUID companionId;
        private int remaining;

        private PatrolSession(UUID id, UUID world, Set<UUID> members, PatrolPhase phase) {
            this.id = id;
            this.world = world;
            this.members = new HashSet<>(members);
            this.phase = phase;
        }
    }

    private record PendingInvite(UUID leaderId, long expiresAt) {
    }
}
