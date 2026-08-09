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
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import tw.zack.evilisland.model.ObjectiveStage;
import tw.zack.evilisland.model.MissionPhase;
import tw.zack.evilisland.model.PatrolScaling;
import tw.zack.evilisland.model.MissionContract;
import tw.zack.evilisland.model.CampaignSnapshot;
import tw.zack.evilisland.model.CampaignStrategy;
import tw.zack.evilisland.model.DefenseBalance;
import tw.zack.evilisland.model.MissionType;
import tw.zack.evilisland.model.MissionBalance;
import tw.zack.evilisland.model.NpcRole;
import tw.zack.evilisland.model.NpcRosterSnapshot;
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
    private final NpcRosterService npcRoster;
    private final DevelopmentService development;
    private MissionTelemetryService telemetry;
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
    private final NamespacedKey pendingQiKey;
    private final NamespacedKey contractKey;
    private final NamespacedKey missionActorKey;
    private final NamespacedKey fullRewardsKey;
    private final NamespacedKey supportRoleKey;
    private final NamespacedKey actorIdKey;
    private final NamespacedKey fortificationKey;
    private final NamespacedKey fortificationHealthKey;
    private final NamespacedKey fortificationEntryKey;
    private final NamespacedKey waveKey;
    private final NamespacedKey breachesKey;
    private final NamespacedKey fortificationStateKey;
    private final Map<UUID, MissionSession> sessions = new HashMap<>();
    private final Map<UUID, UUID> sessionByMember = new HashMap<>();
    private final Map<UUID, PendingInvite> pendingInvites = new HashMap<>();
    private final Map<UUID, MissionContract> selectedContracts = new HashMap<>();
    private final Map<UUID, CampaignStrategy> selectedStrategies = new HashMap<>();

    public EncounterService(EvilIslandPlugin plugin, PlayerProfileService profiles, DaoFieldService daoFields,
                            GameItemService items, SpeciesService species, WeaponService weapons,
                            CompanionService companions, WorldEventService worldEvents, CampaignService campaign) {
        this(plugin, profiles, daoFields, items, species, weapons, companions, worldEvents, campaign, null);
    }

    public EncounterService(EvilIslandPlugin plugin, PlayerProfileService profiles, DaoFieldService daoFields,
                            GameItemService items, SpeciesService species, WeaponService weapons,
                            CompanionService companions, WorldEventService worldEvents, CampaignService campaign,
                            NpcRosterService npcRoster) {
        this(plugin, profiles, daoFields, items, species, weapons, companions, worldEvents, campaign, npcRoster, null);
    }

    public EncounterService(EvilIslandPlugin plugin, PlayerProfileService profiles, DaoFieldService daoFields,
                            GameItemService items, SpeciesService species, WeaponService weapons,
                            CompanionService companions, WorldEventService worldEvents, CampaignService campaign,
                            NpcRosterService npcRoster, DevelopmentService development) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.daoFields = daoFields;
        this.items = items;
        this.species = species;
        this.weapons = weapons;
        this.companions = companions;
        this.worldEvents = worldEvents;
        this.campaign = campaign;
        this.npcRoster = npcRoster;
        this.development = development;
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
        pendingQiKey = new NamespacedKey(plugin, "pending_mission_qi");
        contractKey = new NamespacedKey(plugin, "patrol_contract");
        missionActorKey = new NamespacedKey(plugin, "mission_actor");
        fullRewardsKey = new NamespacedKey(plugin, "mission_full_rewards");
        supportRoleKey = new NamespacedKey(plugin, "mission_support_role");
        actorIdKey = new NamespacedKey(plugin, "mission_actor_id");
        fortificationKey = new NamespacedKey(plugin, "mission_fortification");
        fortificationHealthKey = new NamespacedKey(plugin, "fortification_health");
        fortificationEntryKey = new NamespacedKey(plugin, "fortification_entry");
        waveKey = new NamespacedKey(plugin, "defense_wave");
        breachesKey = new NamespacedKey(plugin, "defense_breaches");
        fortificationStateKey = new NamespacedKey(plugin, "fortification_state");
    }

    public void recover(World world) {
        for (Entity entity : world.getEntities()) {
            if (isAnchor(entity)) {
                recoverAnchor(entity);
            }
        }
        for (MissionSession session : sessions.values()) {
            Entity anchor = Bukkit.getEntity(session.anchorId);
            if (anchor != null) {
                worldEvents.recover(session.id, eventType(session), anchor.getLocation(),
                        session.phase == MissionPhase.COMPLETE_PENDING);
            }
        }
        Set<UUID> patrolIds = sessions.values().stream().filter(session -> !isDefense(session))
                .map(session -> session.id).collect(java.util.stream.Collectors.toSet());
        Set<UUID> defenseIds = sessions.values().stream().filter(this::isDefense)
                .map(session -> session.id).collect(java.util.stream.Collectors.toSet());
        worldEvents.reconcileRunning("east_patrol", patrolIds);
        worldEvents.reconcileRunning("city_defense", defenseIds);
        for (Entity entity : world.getEntities()) {
            recoverSessionEntity(entity);
        }
    }

    public void tick() {
        for (MissionSession session : new ArrayList<>(sessions.values())) {
            switch (session.phase) {
                case SCOUT -> tickScout(session);
                case ESCORT -> tickEscort(session);
                case RESCUE_SEARCH -> tickRescueSearch(session);
                case RESCUE_RETURN -> tickRescueReturn(session);
                case DEFENSE -> tickDefense(session);
                default -> { }
            }
        }
    }

    private void tickScout(MissionSession session) {
        Entity actor = ensureMissionActor(session, MissionPhase.SCOUT);
        if (actor != null && actor.isValid()) {
            actor.getWorld().spawnParticle(Particle.END_ROD, actor.getLocation().add(0, 1.2, 0),
                    plugin.getConfig().getInt("missions.scout.marker-particles", 2),
                    0.25, 0.4, 0.25, 0.01);
        }
    }

    private void tickEscort(MissionSession session) {
        Entity actor = ensureMissionActor(session, MissionPhase.ESCORT);
        if (!(actor instanceof Mob escort)) return;
        Player leader = nearestMember(session, escort.getLocation());
        if (leader != null) followMissionActor(escort, leader);
        Location target = missionTarget(session);
        Location start = guardPost(session);
        if (target == null || start == null) return;
        if (session.remaining < 0 && escort.getLocation().distanceSquared(start) > 18.0 * 18.0) {
            spawnFieldAmbush(session, escort.getLocation());
        }
        if (session.remaining == 0 && escort.getLocation().distanceSquared(target)
                <= square(plugin.getConfig().getDouble("missions.escort.arrival-radius", 7.0))) {
            completeFieldMission(session, "護送隊已抵達指定巡防點，路線重新接通。");
        }
    }

    private void tickRescueSearch(MissionSession session) {
        Entity actor = ensureMissionActor(session, MissionPhase.RESCUE_SEARCH);
        if (actor != null && actor.isValid()) {
            actor.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, actor.getLocation().add(0, 1.0, 0),
                    2, 0.35, 0.25, 0.35, 0.0);
        }
    }

    private void tickRescueReturn(MissionSession session) {
        Entity actor = ensureMissionActor(session, MissionPhase.RESCUE_RETURN);
        if (!(actor instanceof Mob survivor)) return;
        Player leader = nearestMember(session, survivor.getLocation());
        if (leader != null) followMissionActor(survivor, leader);
        Location guard = guardPost(session);
        if (guard != null && session.remaining == 0 && survivor.getLocation().distanceSquared(guard)
                <= square(plugin.getConfig().getDouble("missions.rescue.return-radius", 8.0))) {
            completeFieldMission(session, "失聯人員已平安返回新城巡防站。");
        }
    }

    private void tickDefense(MissionSession session) {
        Location center = defenseCenter(session);
        if (center == null) return;
        ensureFortifications(session, center);
        long now = System.currentTimeMillis();
        for (UUID id : Set.copyOf(session.fortificationIds)) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof ArmorStand fortification) || !entity.isValid()) {
                session.fortificationIds.remove(id);
                continue;
            }
            int entry = fortificationEntry(fortification);
            int health = session.fortificationHealth.getOrDefault(entry, fortificationHealth(fortification));
            if (health <= 0) continue;
            for (Entity nearby : fortification.getNearbyEntities(4.5, 3.0, 4.5)) {
                if (!(nearby instanceof LivingEntity enemy) || species.type(enemy) != SpeciesType.ZAOCHI
                        || !session.id.equals(sessionId(enemy))) continue;
                enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 25, 1, false, false, true));
                long lastDamage = session.lastFortificationDamage.getOrDefault(id, 0L);
                if (now - lastDamage >= plugin.getConfig().getLong(
                        "missions.defense.fortification-damage-interval-ms", 3000L)) {
                    setFortificationHealth(session, fortification, entry, health - 1);
                    session.lastFortificationDamage.put(id, now);
                    updateAnchor(session);
                }
                break;
            }
        }

        double breachRadius = plugin.getConfig().getDouble("missions.defense.breach-radius", 5.0);
        for (Entity entity : center.getWorld().getNearbyEntities(center, breachRadius, 5.0, breachRadius)) {
            if (species.type(entity) != SpeciesType.ZAOCHI || !session.id.equals(sessionId(entity))) continue;
            entity.remove();
            session.remaining = Math.max(0, session.remaining - 1);
            session.breaches++;
            forEachOnline(session, member -> member.sendMessage(EvilIslandPlugin.message(
                    "一名鑿齒突破防線；失守 " + session.breaches + "/"
                            + plugin.getConfig().getInt("missions.defense.max-breaches", 3) + "。",
                    NamedTextColor.RED)));
            if (DefenseBalance.failed(session.breaches,
                    plugin.getConfig().getInt("missions.defense.max-breaches", 3))) {
                failDefenseSession(session);
                return;
            }
            advanceDefenseWaveIfCleared(session);
            updateAnchor(session);
        }
        advanceDefenseWaveIfCleared(session);
    }

    public void clearRuntimeState() {
        sessions.clear();
        sessionByMember.clear();
        pendingInvites.clear();
        selectedContracts.clear();
        selectedStrategies.clear();
    }

    public int runPersistenceSelfTest(Location location) {
        UUID id = UUID.randomUUID();
        UUID memberId = new UUID(0L, 3L);
        MissionSession original = new MissionSession(id, location.getWorld().getUID(), Set.of(memberId),
                MissionPhase.SCOUT, MissionContract.NORTH_RIDGE_OBSERVATION);
        ArmorStand anchor = createAnchor(location, original);
        original.anchorId = anchor.getUniqueId();
        original.remaining = 1;
        original.pendingZaochi.put(memberId, 3);
        original.pendingXingtian.put(memberId, 1);
        original.pendingBonus.put(memberId, 2);
        original.pendingCompletion.add(memberId);
        original.pendingQi.add(memberId);
        original.fullRewards = false;
        original.supportRole = NpcRole.WUJI;
        original.wave = 2;
        original.breaches = 1;
        original.fortificationHealth.put(0, 2);
        sessions.put(id, original);
        sessionByMember.put(memberId, id);
        updateAnchor(original);

        sessions.remove(id);
        sessionByMember.remove(memberId);
        recoverAnchor(anchor);
        MissionSession restored = sessions.get(id);
        int checks = 0;
        if (restored != null) checks++;
        if (restored != null && restored.phase == MissionPhase.SCOUT && restored.remaining == 1) checks++;
        if (restored != null && restored.members.contains(memberId)) checks++;
        if (restored != null && restored.pendingZaochi.getOrDefault(memberId, 0) == 3
                && restored.pendingXingtian.getOrDefault(memberId, 0) == 1) checks++;
        if (restored != null && restored.pendingCompletion.contains(memberId)) checks++;
        if (restored != null && restored.contract == MissionContract.NORTH_RIDGE_OBSERVATION) checks++;
        if (restored != null && restored.pendingBonus.getOrDefault(memberId, 0) == 2) checks++;
        if (restored != null && restored.pendingQi.contains(memberId)) checks++;
        if (restored != null && !restored.fullRewards) checks++;
        if (restored != null && restored.supportRole == NpcRole.WUJI) checks++;
        if (restored != null && restored.wave == 2 && restored.breaches == 1) checks++;
        if (restored != null && restored.fortificationHealth.getOrDefault(0, 0) == 2) checks++;
        cleanupSession(id);
        if (telemetry != null) telemetry.discard(id);
        return checks;
    }

    public int runSceneSelfTest(Location location) {
        UUID id = UUID.randomUUID();
        MissionSession escortSession = new MissionSession(id, location.getWorld().getUID(),
                Set.of(new UUID(0L, 4L)), MissionPhase.ESCORT, MissionContract.EASTERN_MEDIC_ESCORT);
        Villager escort = createMissionNpc(location, escortSession, false);
        Villager survivor = createMissionNpc(location.clone().add(2, 0, 0), escortSession, true);
        ArmorStand fortification = createFortification(location.clone().add(4, 0, 0), escortSession, 0, 2);
        int checks = isMissionActor(escort) && isMissionActor(survivor) ? 1 : 0;
        if (id.equals(sessionId(escort)) && id.equals(sessionId(survivor))) checks++;
        if (escort.hasAI() && !survivor.hasAI()) checks++;
        if (escort.isInvulnerable() && survivor.isInvulnerable()) checks++;
        if (isFortification(fortification)) checks++;
        if (id.equals(sessionId(fortification)) && fortificationEntry(fortification) == 0) checks++;
        if (fortificationHealth(fortification) == 2) checks++;
        IronGolem guard = location.getWorld().getEntitiesByClass(IronGolem.class).stream()
                .filter(entity -> entity.getPersistentDataContainer().has(guardKey, PersistentDataType.BYTE))
                .findFirst().orElse(null);
        if (guard != null) checks++;
        if (guard != null && !guard.hasAI() && guard.isInvulnerable()) checks++;
        escort.remove();
        survivor.remove();
        fortification.remove();
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
        guard.setAI(false);
        guard.setInvulnerable(true);
        guard.setCollidable(false);
        setAttribute(guard, Attribute.GENERIC_MAX_HEALTH, 160.0);
        guard.setHealth(160.0);
    }

    public void setTelemetryService(MissionTelemetryService telemetry) {
        this.telemetry = telemetry;
    }

    public boolean isEncounterEnemy(Entity entity) {
        return species.isHostile(entity);
    }

    public boolean canTarget(Entity enemy, LivingEntity target) {
        UUID id = sessionId(enemy);
        if (id == null) {
            return true;
        }
        MissionSession session = sessions.get(id);
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
        MissionSession session = sessionFor(player);
        if (session == null) {
            spawnStandaloneXingtian(player);
            return;
        }
        if (session.phase == MissionPhase.BOSS) {
            player.sendMessage(EvilIslandPlugin.message("刑天統領已在東境活動。"));
            return;
        }
        if (session.phase != MissionPhase.BOSS_READY) {
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
        session.phase = MissionPhase.BOSS;
        int extraEnemies = Math.max(0, campaign.weeklyBossExtraEnemies()
                + (development == null ? 0 : development.bossEscortModifier()));
        session.remaining = 3 + extraEnemies;
        updateAnchor(session);
        Location spawn = ground(center.clone().add(10, 0, 0));
        LivingEntity boss = species.spawnXingtian(spawn,
                MissionBalance.bossHealth(scaling.bossHealthMultiplier() * session.contract.bossHealthMultiplier()
                        * campaign.intelligenceEnemyHealthMultiplier() * campaign.weeklyBossHealthMultiplier()),
                MissionBalance.bossDamage(scaling.bossDamageMultiplier() * session.contract.bossDamageMultiplier()
                        * campaign.moraleEnemyDamageMultiplier() * campaign.weeklyBossDamageMultiplier()));
        if (campaign.state().week() == 4) {
            species.setXingtianDisplayName(boss, campaign.bossVariant().display());
        }
        tag(boss, session);
        tag(species.spawnZaochi(ground(spawn.clone().add(-4, 0, 3)),
                MissionBalance.regularHealth(scaling.zaochiHealthMultiplier()
                        * session.contract.zaochiHealthMultiplier() * campaign.intelligenceEnemyHealthMultiplier()),
                MissionBalance.regularDamage(scaling.zaochiDamageMultiplier()
                        * session.contract.zaochiDamageMultiplier() * campaign.moraleEnemyDamageMultiplier())), session);
        tag(species.spawnZaochi(ground(spawn.clone().add(-4, 0, -3)),
                MissionBalance.regularHealth(scaling.zaochiHealthMultiplier()
                        * session.contract.zaochiHealthMultiplier() * campaign.intelligenceEnemyHealthMultiplier()),
                MissionBalance.regularDamage(scaling.zaochiDamageMultiplier()
                        * session.contract.zaochiDamageMultiplier() * campaign.moraleEnemyDamageMultiplier())), session);
        for (int index = 0; index < extraEnemies; index++) {
            double angle = Math.PI * 2.0 * index / Math.max(1, extraEnemies);
            tag(species.spawnZaochi(ground(spawn.clone().add(Math.cos(angle) * 6.0, 0,
                            Math.sin(angle) * 6.0)),
                    MissionBalance.regularHealth(scaling.zaochiHealthMultiplier()
                            * campaign.intelligenceEnemyHealthMultiplier()),
                    MissionBalance.regularDamage(scaling.zaochiDamageMultiplier()
                            * campaign.moraleEnemyDamageMultiplier())), session);
        }
        spawn.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, spawn.clone().add(0, 1, 0),
                4, 1.2, 0.5, 1.2, 0.02);
        forEachOnline(session, member -> {
            profiles.setObjective(member, ObjectiveStage.DEFEAT_XINGTIAN);
            member.sendMessage(EvilIslandPlugin.message((campaign.state().week() == 4
                    ? campaign.bossVariant().display() : "刑天統領") + "率眾逼近新城東境。",
                    NamedTextColor.DARK_RED));
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
            player.sendMessage(EvilIslandPlugin.message("出城前先領取一件歲安軍團登記兵器。", NamedTextColor.YELLOW));
            weapons.openArmory(player);
            return;
        }
        MissionSession active = sessionFor(player);
        if (active != null) {
            openActiveMenu(player, active);
        } else {
            openContractMenu(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMissionActorInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isMissionActor(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        UUID id = sessionId(event.getRightClicked());
        MissionSession session = id == null ? null : sessions.get(id);
        Player player = event.getPlayer();
        if (session == null || !session.members.contains(player.getUniqueId())) {
            player.sendMessage(EvilIslandPlugin.message("這個任務目標不屬於你的編組。"));
            return;
        }
        if (session.phase == MissionPhase.SCOUT) {
            completeFieldMission(session, "輕疾觀測標已啟動，荒原情報送回新城。");
        } else if (session.phase == MissionPhase.RESCUE_SEARCH && event.getRightClicked() instanceof Mob survivor) {
            survivor.setAI(true);
            survivor.customName(Component.text("失聯巡防員（已獲救）", NamedTextColor.GREEN));
            session.phase = MissionPhase.RESCUE_RETURN;
            spawnFieldAmbush(session, survivor.getLocation());
            updateAnchor(session);
            forEachOnline(session, member -> member.sendMessage(EvilIslandPlugin.message(
                    "已扶起失聯巡防員；清除追兵後帶他返回新城。", NamedTextColor.YELLOW)));
        } else if (session.phase == MissionPhase.ESCORT) {
            player.sendMessage(EvilIslandPlugin.message("護送員會跟隨距離最近的編組成員。"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFortificationInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof ArmorStand fortification)
                || !isFortification(fortification)) return;
        event.setCancelled(true);
        MissionSession session = sessions.get(sessionId(fortification));
        Player player = event.getPlayer();
        if (session == null || session.phase != MissionPhase.DEFENSE
                || !session.members.contains(player.getUniqueId())) {
            player.sendMessage(EvilIslandPlugin.message("這道工事不屬於你的守城編組。"));
            return;
        }
        int entry = fortificationEntry(fortification);
        int health = session.fortificationHealth.getOrDefault(entry, fortificationHealth(fortification));
        int maximum = fortificationMaximumHealth();
        if (health >= maximum) {
            player.sendMessage(EvilIslandPlugin.message("這道工事目前不需要修復。"));
            return;
        }
        Material repair = Material.matchMaterial(plugin.getConfig().getString(
                "missions.defense.repair-material", "COBBLESTONE"));
        if (repair == null || countMaterial(player, repair) < 1) {
            player.sendMessage(EvilIslandPlugin.message("修復工事需要一份鵝卵石。"));
            return;
        }
        removeMaterial(player, repair, 1);
        setFortificationHealth(session, fortification, entry, health + 1);
        updateAnchor(session);
        player.sendMessage(EvilIslandPlugin.message("工事已修復至 " + (health + 1) + "/" + maximum + "。",
                NamedTextColor.GREEN));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MissionMenuHolder holder)) {
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
                List<MissionContract> board = availableContracts(player);
                if (option < board.size()) {
                    selectedContracts.put(player.getUniqueId(), board.get(option));
                    openAssemblyMenu(player);
                }
            } else if (slot == 22) {
                openRosterMenu(player);
            } else if (slot == 20) {
                openWeeklyMenu(player);
            } else if (slot == 24 && development != null) {
                development.openHub(player);
            }
        } else if (holder.type == MenuType.ASSEMBLY) {
            if (slot == 11) {
                player.closeInventory();
                beginMission(List.of(player), selectedContract(player));
            } else if (slot == 15) {
                Player partner = nearestPartner(player);
                if (partner == null) {
                    player.sendMessage(EvilIslandPlugin.message("附近沒有已定型、持有兵器且進度相同的可編組玩家。"));
                    return;
                }
                sendDuoInvite(player, partner, selectedContract(player));
            }
        } else if (holder.type == MenuType.ACTIVE) {
            if (slot == 20) {
                MissionSession session = sessions.get(holder.sessionId);
                if (session != null && session.phase == MissionPhase.GATHER) {
                    submitGathering(player, session);
                }
            } else if (slot == 22) {
                openCancelConfirmation(player, holder.sessionId);
            }
        } else if (holder.type == MenuType.CANCEL) {
            if (slot == 11) {
                MissionSession session = sessions.get(holder.sessionId);
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
                beginMission(List.of(leader, player), invite.contract);
            } else if (leader != null) {
                leader.sendMessage(EvilIslandPlugin.message(player.getName() + "沒有加入本次雙人巡防。"));
            }
        } else if (holder.type == MenuType.ROSTER) {
            NpcRole role = slot == 10 ? NpcRole.HANSHAN : slot == 12 ? NpcRole.YANGWU
                    : slot == 14 ? NpcRole.WUJI : slot == 16 ? NpcRole.DOUTIAN : null;
            if (role != null) treatNpc(player, role);
            if (slot == 22) openContractMenu(player);
        } else if (holder.type == MenuType.WEEKLY) {
            if (slot == 22) {
                openContractMenu(player);
                return;
            }
            if (campaign.state().weeklyResolved()) {
                player.sendMessage(EvilIslandPlugin.message("本週共同方針已鎖定。"));
                return;
            }
            CampaignStrategy strategy = slot == 10 ? CampaignStrategy.FORTIFY
                    : slot == 13 ? CampaignStrategy.PROVISION
                    : slot == 16 ? CampaignStrategy.RECON : null;
            if (strategy != null) {
                selectedStrategies.put(player.getUniqueId(), strategy);
                openWeeklyConfirmation(player, strategy);
            }
        } else if (holder.type == MenuType.WEEKLY_CONFIRM) {
            if (slot == 11) {
                selectedStrategies.remove(player.getUniqueId());
                openWeeklyMenu(player);
            } else if (slot == 15) {
                CampaignStrategy strategy = selectedStrategies.remove(player.getUniqueId());
                if (strategy == null || !campaign.resolveWeekly(strategy)) {
                    player.sendMessage(EvilIslandPlugin.message("本週共同方針已由其他巡防員決定。"));
                }
                openContractMenu(player);
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
        MissionSession session = sessions.get(sessionId(event.getEntity()));
        if (session == null) {
            event.getEntity().getWorld().dropItemNaturally(event.getEntity().getLocation(),
                    items.createRemains(type.id(), type == SpeciesType.XINGTIAN ? 3 : type.elite() ? 2 : 1));
            Player killer = event.getEntity().getKiller();
            if (killer != null && profiles.isEnlisted(killer)) {
                if (development != null) development.recordSpeciesDefeat(type);
                if (type == SpeciesType.ZAOCHI) {
                    profiles.recordZaochiKill(killer);
                } else if (type == SpeciesType.XINGTIAN) {
                    profiles.setObjective(killer, ObjectiveStage.COMPLETE);
                }
            }
            return;
        }

        if (type == SpeciesType.ZAOCHI) {
            if (session.contract.missionType() == MissionType.PATROL) {
                rewardMembers(session, SpeciesType.ZAOCHI, 1);
            }
            session.remaining = Math.max(0, session.remaining - 1);
            if (session.phase == MissionPhase.PATROL && session.remaining == 0) {
                session.phase = MissionPhase.BOSS_READY;
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
                        if (leader != null && session.phase == MissionPhase.BOSS_READY) spawnXingtian(leader);
                    }, 60L);
                }
            }
            if (session.phase == MissionPhase.DEFENSE) {
                advanceDefenseWaveIfCleared(session);
            }
            updateAnchor(session);
            return;
        }

        rewardMembers(session, SpeciesType.XINGTIAN, 1);
        boolean firstCompletion = campaign.complete(session.contract);
        if (development != null) development.recordMission(session.contract, session.members, firstCompletion);
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
        session.phase = MissionPhase.COMPLETE_PENDING;
        session.remaining = 0;
        updateAnchor(session);
        worldEvents.transition(session.id, WorldEventState.SUCCEEDED);
        if (telemetry != null) telemetry.succeed(session.id);
        completeSupportDuty(session);
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
            recoverSessionEntity(entity);
        }
    }

    private void beginMission(List<Player> members, MissionContract contract) {
        if (members.isEmpty() || members.size() > 2) {
            return;
        }
        World world = members.get(0).getWorld();
        if (sessions.values().stream().anyMatch(session -> session.world.equals(world.getUID())
                && session.phase != MissionPhase.COMPLETE_PENDING)) {
            members.get(0).sendMessage(EvilIslandPlugin.message("東境巡防區已有另一支隊伍執行任務。"));
            return;
        }
        for (Player member : members) {
            if (!canJoin(member) || !member.getWorld().equals(world)) {
                members.get(0).sendMessage(EvilIslandPlugin.message("隊員狀態已改變，無法開始巡防。"));
                return;
            }
        }
        Location center = contract.missionType() == MissionType.DEFENSE
                ? defenseCenter(world) : daoFields.patrolCenter(world);
        if (center == null) {
            members.get(0).sendMessage(EvilIslandPlugin.message("巡防區尚未完成設定。"));
            return;
        }

        UUID id = UUID.randomUUID();
        Set<UUID> memberIds = new HashSet<>();
        members.forEach(member -> memberIds.add(member.getUniqueId()));
        MissionPhase initialPhase = switch (contract.missionType()) {
            case PATROL -> MissionPhase.PATROL;
            case GATHER -> MissionPhase.GATHER;
            case SCOUT -> MissionPhase.SCOUT;
            case ESCORT -> MissionPhase.ESCORT;
            case RESCUE -> MissionPhase.RESCUE_SEARCH;
            case DEFENSE -> MissionPhase.DEFENSE;
        };
        MissionSession session = new MissionSession(id, world.getUID(), memberIds, initialPhase, contract);
        session.fullRewards = members.stream().anyMatch(member -> profiles.transformations(member) == 0)
                || !campaign.state().completedToday();
        ArmorStand anchor = createAnchor(center, session);
        session.anchorId = anchor.getUniqueId();
        sessions.put(id, session);
        memberIds.forEach(memberId -> sessionByMember.put(memberId, id));
        memberIds.forEach(selectedContracts::remove);
        worldEvents.create(id, eventType(session), center);
        if (telemetry != null) telemetry.start(id, contract, memberIds.size());

        if (contract.missionType() == MissionType.PATROL) {
            startCombatPatrol(session, members, center);
        } else if (contract.missionType() == MissionType.GATHER) {
            session.remaining = scaledObjectiveAmount(contract, members.size());
        } else if (contract.missionType() == MissionType.SCOUT) {
            session.remaining = 1;
            Location target = ground(center.clone().add(contract.targetOffsetX(), 0, contract.targetOffsetZ()));
            ArmorStand marker = createScoutMarker(target, session);
            session.actorId = marker.getUniqueId();
            startSoloSupport(session, members, NpcRole.WUJI);
        } else if (contract.missionType() == MissionType.ESCORT) {
            session.remaining = -1;
            Location start = guardPost(session);
            if (start != null) session.actorId = createMissionNpc(start, session, false).getUniqueId();
            startSoloSupport(session, members, NpcRole.YANGWU);
        } else if (contract.missionType() == MissionType.RESCUE) {
            session.remaining = 0;
            Location target = missionTarget(session);
            if (target != null) session.actorId = createMissionNpc(target, session, true).getUniqueId();
            startSoloSupport(session, members, NpcRole.WUJI);
        } else if (contract.missionType() == MissionType.DEFENSE) {
            startDefense(session, members, center);
        }
        updateAnchor(session);
        worldEvents.transition(id, WorldEventState.ACTIVE);
        for (Player member : members) {
            profiles.setObjective(member, contract.missionType() == MissionType.PATROL
                    ? ObjectiveStage.HUNT_ZAOCHI : ObjectiveStage.REPORT_PATROL);
            member.sendMessage(EvilIslandPlugin.message("任務編組完成：" + displayMembers(session)
                    + "，任務「" + contract.display() + "」。", NamedTextColor.GREEN));
            member.sendMessage(EvilIslandPlugin.message(missionInstruction(session), NamedTextColor.YELLOW));
        }
    }

    private void startCombatPatrol(MissionSession session, List<Player> members, Location center) {
        PatrolScaling scaling = scaling(members.size());
        boolean support = startSoloSupport(session, members, NpcRole.DOUTIAN);
        int zaochiCount = Math.max(1, scaling.zaochiCount() + session.contract.extraZaochi()
                + campaign.defenseEnemyModifier() + campaign.weeklyEnemyModifier()
                - (!support && members.size() == 1
                ? plugin.getConfig().getInt("npc-roster.solo-no-support-enemy-reduction", 1) : 0));
        double noSupportMultiplier = !support && members.size() == 1
                ? plugin.getConfig().getDouble("npc-roster.solo-no-support-combat-multiplier", 0.90) : 1.0;
        session.remaining = zaochiCount;
        for (int index = 0; index < zaochiCount; index++) {
            double angle = Math.PI * 2.0 * index / zaochiCount;
            Location spawn = ground(center.clone().add(Math.cos(angle) * session.contract.spawnRadius(), 0,
                    Math.sin(angle) * session.contract.spawnRadius()));
            tag(species.spawnZaochi(spawn,
                    MissionBalance.regularHealth(scaling.zaochiHealthMultiplier()
                            * session.contract.zaochiHealthMultiplier() * campaign.intelligenceEnemyHealthMultiplier()
                            * noSupportMultiplier),
                    MissionBalance.regularDamage(scaling.zaochiDamageMultiplier()
                            * session.contract.zaochiDamageMultiplier() * campaign.moraleEnemyDamageMultiplier()
                            * noSupportMultiplier)), session);
        }
    }

    private void startDefense(MissionSession session, List<Player> members, Location center) {
        startSoloSupport(session, members, NpcRole.HANSHAN);
        session.wave = 1;
        session.breaches = 0;
        int maximum = fortificationMaximumHealth();
        for (int entry = 0; entry < session.contract.defenseEntrances(); entry++) {
            session.fortificationHealth.put(entry, maximum);
            ArmorStand fortification = createFortification(defenseEntry(center, entry, true), session, entry, maximum);
            session.fortificationIds.add(fortification.getUniqueId());
        }
        spawnDefenseWave(session, center);
    }

    private void spawnDefenseWave(MissionSession session, Location center) {
        int total = DefenseBalance.enemyCount(session.contract.defenseEntrances(), session.wave,
                session.members.size());
        int perEntrance = Math.max(1, total / session.contract.defenseEntrances()
                + (development == null ? 0 : development.defenseEnemyPerEntranceModifier()));
        total = perEntrance * session.contract.defenseEntrances();
        session.remaining = total;
        double health = session.members.size() == 1 ? 0.90 : 1.10;
        double damage = session.members.size() == 1 ? 0.92 : 1.08;
        for (int entry = 0; entry < session.contract.defenseEntrances(); entry++) {
            Location approach = defenseEntry(center, entry, false);
            for (int index = 0; index < perEntrance; index++) {
                Location spawn = ground(approach.clone().add((index % 2) * 1.8, 0, (index / 2) * 1.8));
                tag(species.spawnZaochi(spawn, MissionBalance.regularHealth(health),
                        MissionBalance.regularDamage(damage)), session);
            }
        }
        updateAnchor(session);
        forEachOnline(session, member -> member.sendMessage(EvilIslandPlugin.message(
                "第 " + session.wave + "/" + session.contract.defenseWaves()
                        + " 波攻勢自 " + session.contract.defenseEntrances() + " 個方向逼近。",
                NamedTextColor.RED)));
    }

    private void advanceDefenseWaveIfCleared(MissionSession session) {
        if (session.phase != MissionPhase.DEFENSE || session.remaining > 0 || session.wavePending) return;
        if (!DefenseBalance.hasNextWave(session.wave, session.contract.defenseWaves())) {
            completeFieldMission(session, "東門多路攻勢已被擊退，防線暫時穩定。");
            return;
        }
        session.wavePending = true;
        forEachOnline(session, member -> member.sendMessage(EvilIslandPlugin.message(
                "本波已清除；利用空檔右鍵工事並消耗鵝卵石修復。", NamedTextColor.YELLOW)));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.phase != MissionPhase.DEFENSE) return;
            session.wavePending = false;
            session.wave++;
            Location center = defenseCenter(session);
            if (center != null) spawnDefenseWave(session, center);
        }, plugin.getConfig().getLong("missions.defense.intermission-ticks", 80L));
    }

    private void failDefenseSession(MissionSession session) {
        campaign.recordDefenseFailure();
        if (development != null) development.damageAfterDefenseFailure(session.breaches);
        session.phase = MissionPhase.COMPLETE_PENDING;
        forEachOnline(session, member -> {
            profiles.setObjective(member, ObjectiveStage.REPORT_PATROL);
            member.sendMessage(EvilIslandPlugin.message("突破數超過防線承受上限，本次守城失敗。",
                    NamedTextColor.RED));
        });
        if (session.supportRole != null && npcRoster != null) npcRoster.abortMission(session.supportRole);
        worldEvents.transition(session.id, WorldEventState.FAILED);
        if (telemetry != null) telemetry.fail(session.id, "defense_breached");
        removeSessionActors(session);
        cleanupSession(session.id);
    }

    private boolean startSoloSupport(MissionSession session, List<Player> members, NpcRole role) {
        if (members.size() != 1) return false;
        Player owner = members.get(0);
        if (npcRoster != null && !npcRoster.available(role)) {
            owner.sendMessage(EvilIslandPlugin.message(role.display() + "目前無法出勤；單人難度已自動下修。",
                    NamedTextColor.YELLOW));
            return false;
        }
        LivingEntity companion = companions.spawn(owner.getLocation(), owner, session.id, role);
        tag(companion, session);
        session.companionId = companion.getUniqueId();
        session.supportRole = role;
        return true;
    }

    private void spawnFieldAmbush(MissionSession session, Location center) {
        int count = Math.max(1, 1 + session.contract.risk() / 2 + session.members.size() - 1);
        session.remaining = count;
        double radius = plugin.getConfig().getDouble("missions.field-ambush.spawn-radius", 7.0);
        double health = session.members.size() == 1 ? 0.85 : 1.05;
        double damage = session.members.size() == 1 ? 0.90 : 1.05;
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2.0 * index / count;
            Location spawn = ground(center.clone().add(Math.cos(angle) * radius, 0,
                    Math.sin(angle) * radius));
            tag(species.spawnZaochi(spawn, MissionBalance.regularHealth(health),
                    MissionBalance.regularDamage(damage)), session);
        }
        updateAnchor(session);
        forEachOnline(session, member -> member.sendMessage(EvilIslandPlugin.message(
                "鑿齒追兵逼近；非戰鬥任務中的追兵不掉落妖質。", NamedTextColor.RED)));
    }

    private void completeSupportDuty(MissionSession session) {
        if (session.supportRole != null && npcRoster != null) {
            npcRoster.completeMission(session.supportRole);
        }
    }

    private int scaledObjectiveAmount(MissionContract contract, int players) {
        double amountMultiplier = plugin.getConfig().getDouble("missions.gather.amount-multiplier", 1.0);
        int base = Math.max(1, (int) Math.ceil(contract.objectiveAmount()
                * Math.max(0.5, Math.min(2.0, amountMultiplier))));
        return MissionBalance.sharedObjectiveAmount(base, players,
                plugin.getConfig().getDouble("missions.gather.duo-multiplier", 1.5));
    }

    private void rewardMembers(MissionSession session, SpeciesType type, int count) {
        int purity = type == SpeciesType.XINGTIAN ? 3 : 1;
        for (UUID memberId : session.members) {
            Player member = Bukkit.getPlayer(memberId);
            if (type == SpeciesType.ZAOCHI && !session.fullRewards) {
                if (member != null && member.isOnline()) {
                    for (int index = 0; index < count; index++) profiles.recordZaochiKill(member);
                }
                continue;
            }
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

    private void rewardBonusRemains(MissionSession session, int count) {
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

    private void submitGathering(Player player, MissionSession session) {
        Material material = Material.matchMaterial(session.contract.objectiveMaterial());
        if (material == null) {
            player.sendMessage(EvilIslandPlugin.message("任務物資設定錯誤，請通知管理員。", NamedTextColor.RED));
            return;
        }
        List<Player> online = new ArrayList<>();
        for (UUID memberId : session.members) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null || !member.isOnline()) {
                player.sendMessage(EvilIslandPlugin.message("全體隊員必須在線才能共同繳交物資。"));
                return;
            }
            online.add(member);
        }
        int available = online.stream().mapToInt(member -> countMaterial(member, material)).sum();
        if (available < session.remaining) {
            player.sendMessage(EvilIslandPlugin.message("共同持有「" + session.contract.objectiveDisplay() + "」"
                    + available + "/" + session.remaining + "，物資尚未備齊。"));
            return;
        }
        int left = session.remaining;
        for (Player member : online) {
            left -= removeMaterial(member, material, left);
            if (left == 0) break;
        }
        completeFieldMission(session, "物資已由輕疾登記並送入新城庫房。");
    }

    private void completeFieldMission(MissionSession session, String result) {
        if (session.phase == MissionPhase.COMPLETE_PENDING) return;
        boolean firstCompletion = campaign.complete(session.contract);
        if (development != null) development.recordMission(session.contract, session.members, firstCompletion);
        for (UUID memberId : session.members) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                profiles.setObjective(member, ObjectiveStage.COMPLETE);
                int qiReward = plugin.getConfig().getInt("missions.field-qi-reward", 20);
                if (firstCompletion) profiles.addQi(member, qiReward);
                member.sendMessage(EvilIslandPlugin.message(result, NamedTextColor.GREEN));
                member.sendMessage(EvilIslandPlugin.message(firstCompletion
                        ? session.contract.metric().display() + "獲得提升；本隊恢復 " + qiReward + " 點炁息。"
                        : "今日城況已結算，本次不再產生額外成長。", NamedTextColor.GOLD));
            } else {
                session.pendingCompletion.add(memberId);
                if (firstCompletion) session.pendingQi.add(memberId);
            }
        }
        session.phase = MissionPhase.COMPLETE_PENDING;
        session.remaining = 0;
        updateAnchor(session);
        worldEvents.transition(session.id, WorldEventState.SUCCEEDED);
        if (telemetry != null) telemetry.succeed(session.id);
        completeSupportDuty(session);
        removeSessionActors(session);
        if (session.pendingCompletion.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> cleanupSession(session.id));
        }
    }

    private int countMaterial(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) total += stack.getAmount();
        }
        return total;
    }

    private int removeMaterial(Player player, Material material, int requested) {
        int removed = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && removed < requested; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() != material) continue;
            int amount = Math.min(stack.getAmount(), requested - removed);
            removed += amount;
            if (amount == stack.getAmount()) {
                player.getInventory().setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - amount);
            }
        }
        return removed;
    }

    private void restoreMember(Player player) {
        MissionSession session = sessionFor(player);
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
        } else if (session.phase == MissionPhase.BOSS_READY) {
            boolean ready = session.members.stream().allMatch(memberId -> {
                Player member = Bukkit.getPlayer(memberId);
                return member != null && member.isOnline() && profiles.transformations(member) > 0;
            });
            profiles.setObjective(player, ready ? ObjectiveStage.DEFEAT_XINGTIAN : ObjectiveStage.REFINE_REMAINS);
            if (ready) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (session.phase == MissionPhase.BOSS_READY && player.isOnline()) spawnXingtian(player);
                }, 20L);
            }
        } else if (session.phase == MissionPhase.BOSS) {
            profiles.setObjective(player, ObjectiveStage.DEFEAT_XINGTIAN);
        }
        if (session.pendingQi.remove(player.getUniqueId())) {
            int qiReward = plugin.getConfig().getInt("missions.field-qi-reward", 20);
            profiles.addQi(player, qiReward);
            player.sendMessage(EvilIslandPlugin.message("已補發任務結算的 " + qiReward + " 點炁息。",
                    NamedTextColor.GOLD));
        }
        updateAnchor(session);
        if (session.phase == MissionPhase.COMPLETE_PENDING && session.pendingCompletion.isEmpty()
                && session.pendingZaochi.isEmpty() && session.pendingXingtian.isEmpty()
                && session.pendingBonus.isEmpty() && session.pendingQi.isEmpty()) {
            cleanupSession(session.id);
        }
    }

    private void openAssemblyMenu(Player player) {
        MissionMenuHolder holder = new MissionMenuHolder(MenuType.ASSEMBLY, null);
        Inventory inventory = createInventory(holder, "東境任務編組");
        MissionContract contract = selectedContract(player);
        inventory.setItem(4, menuItem(Material.WRITABLE_BOOK, contract.display(), NamedTextColor.GOLD,
                contractLore(contract)));
        inventory.setItem(11, menuItem(Material.PLAYER_HEAD, "單人巡防", NamedTextColor.AQUA,
                List.of(soloSupportDescription(contract), "敵軍與共用目標依單人規模調整。")));
        Player partner = nearestPartner(player);
        inventory.setItem(15, menuItem(partner == null ? Material.GRAY_DYE : Material.TOTEM_OF_UNDYING,
                partner == null ? "雙人編組不可用" : "與「" + partner.getName() + "」雙人任務",
                partner == null ? NamedTextColor.GRAY : NamedTextColor.GREEN,
                List.of(partner == null ? "附近沒有進度相同的合格隊員。" : duoDescription(contract))));
        player.openInventory(inventory);
    }

    private void openContractMenu(Player player) {
        MissionMenuHolder holder = new MissionMenuHolder(MenuType.CONTRACT, null);
        Inventory inventory = createInventory(holder, "輕疾巡防公告");
        CampaignSnapshot state = campaign.state();
        inventory.setItem(4, menuItem(Material.RECOVERY_COMPASS, campaign.scheduleText(), NamedTextColor.AQUA,
                List.of(campaign.metricsText(), campaign.weeklyEventText(), campaign.activeModifierText(),
                        state.completedToday()
                        ? "今日城況獎勵已結算，重複出勤收益會遞減。"
                        : "今日首次完成任務會改變新城城況。")));
        int[] slots = {11, 13, 15};
        List<MissionContract> board = availableContracts(player);
        for (int index = 0; index < board.size(); index++) {
            MissionContract contract = board.get(index);
            Material icon = switch (contract.missionType()) {
                case PATROL -> Material.IRON_SWORD;
                case GATHER -> Material.BUNDLE;
                case SCOUT -> Material.SPYGLASS;
                case ESCORT -> Material.MINECART;
                case RESCUE -> Material.TOTEM_OF_UNDYING;
                case DEFENSE -> Material.SHIELD;
            };
            inventory.setItem(slots[index], menuItem(icon, contract.display(), NamedTextColor.GOLD,
                    contractLore(contract)));
        }
        if (npcRoster != null) {
            inventory.setItem(22, menuItem(Material.BREWING_STAND, "巡防員狀態與治療", NamedTextColor.AQUA,
                    List.of("撼山：" + npcRoster.statusText(NpcRole.HANSHAN),
                            "揚武：" + npcRoster.statusText(NpcRole.YANGWU),
                            "無跡：" + npcRoster.statusText(NpcRole.WUJI),
                            "鬥天：" + npcRoster.statusText(NpcRole.DOUTIAN))));
        }
        inventory.setItem(20, menuItem(state.weeklyResolved() ? Material.FILLED_MAP : Material.BELL,
                campaign.weeklyEventText(), state.weeklyResolved() ? NamedTextColor.GREEN : NamedTextColor.YELLOW,
                List.of(campaign.weeklyEventSummary(), state.weeklyResolved()
                        ? "本週方針已鎖定。" : "點擊召開本週部署會議。")));
        if (development != null) {
            inventory.setItem(24, menuItem(Material.STONECUTTER, "新城發展與遠征", NamedTextColor.LIGHT_PURPLE,
                    List.of("公共工程、地標探索、連續事件、勢力交涉與兵器研習。")));
        }
        player.openInventory(inventory);
    }

    private void openWeeklyMenu(Player player) {
        MissionMenuHolder holder = new MissionMenuHolder(MenuType.WEEKLY, null);
        Inventory inventory = createInventory(holder, "本週共同部署");
        CampaignSnapshot state = campaign.state();
        if (state.weeklyResolved()) {
            inventory.setItem(13, menuItem(Material.FILLED_MAP, state.weeklyStrategy().display(),
                    NamedTextColor.GREEN, List.of(state.weeklyStrategy().summary(), "本週方針不可重選。")));
        } else {
            inventory.setItem(10, menuItem(Material.STONE_BRICKS, CampaignStrategy.FORTIFY.display(),
                    NamedTextColor.AQUA, List.of(CampaignStrategy.FORTIFY.summary(), "城防 +6、供應 -2")));
            inventory.setItem(13, menuItem(Material.BREAD, CampaignStrategy.PROVISION.display(),
                    NamedTextColor.GOLD, List.of(CampaignStrategy.PROVISION.summary(), "供應 +5、民心 +2、城防 -1")));
            inventory.setItem(16, menuItem(Material.SPYGLASS, CampaignStrategy.RECON.display(),
                    NamedTextColor.GREEN, List.of(CampaignStrategy.RECON.summary(), "情報 +6、城防 +1、供應 -2")));
        }
        inventory.setItem(22, menuItem(Material.ARROW, "返回任務公告", NamedTextColor.GREEN, List.of()));
        player.openInventory(inventory);
    }

    private void openWeeklyConfirmation(Player player, CampaignStrategy strategy) {
        MissionMenuHolder holder = new MissionMenuHolder(MenuType.WEEKLY_CONFIRM, null);
        Inventory inventory = createInventory(holder, "確認本週共同方針");
        inventory.setItem(13, menuItem(Material.WRITABLE_BOOK, strategy.display(), NamedTextColor.GOLD,
                List.of(strategy.summary(), "確認後全服本週不可重選。")));
        inventory.setItem(11, menuItem(Material.ARROW, "返回重選", NamedTextColor.GREEN, List.of()));
        inventory.setItem(15, menuItem(Material.LIME_CONCRETE, "確認部署", NamedTextColor.YELLOW,
                List.of("立即套用城況取捨並記入本輪策略。")));
        player.openInventory(inventory);
    }

    private MissionContract selectedContract(Player player) {
        MissionContract selected = selectedContracts.get(player.getUniqueId());
        List<MissionContract> board = availableContracts(player);
        return selected != null && board.contains(selected) ? selected : board.get(0);
    }

    private List<MissionContract> availableContracts(Player player) {
        return profiles.transformations(player) == 0 ? campaign.patrolBoard() : campaign.board();
    }

    private List<String> contractLore(MissionContract contract) {
        List<String> lore = new ArrayList<>();
        lore.add(contract.summary());
        lore.add("類型：" + contract.missionType().display() + "　影響：" + contract.metric().display()
                + " +" + contract.stateReward());
        lore.add("危險：" + "◆".repeat(contract.risk()) + "◇".repeat(4 - contract.risk()));
        if (contract.missionType() == MissionType.GATHER) {
            lore.add("基礎需求：" + contract.objectiveDisplay() + " " + contract.objectiveAmount());
        } else if (contract.missionType() == MissionType.DEFENSE) {
            lore.add("入口：" + contract.defenseEntrances() + "　波次：" + contract.defenseWaves());
            lore.add("工事可消耗鵝卵石修復；守城追兵不掉落妖質。");
        } else if (contract.missionType() != MissionType.PATROL) {
            lore.add("報酬不包含妖質或永久戰力。");
        } else {
            lore.add(contract.bonusRemains() == 0 ? "額外報酬：無" : "額外報酬：遺骸 " + contract.bonusRemains());
        }
        return lore;
    }

    private void openActiveMenu(Player player, MissionSession session) {
        MissionMenuHolder holder = new MissionMenuHolder(MenuType.ACTIVE, session.id);
        Inventory inventory = createInventory(holder, "目前任務編組");
        inventory.setItem(13, menuItem(Material.COMPASS, phaseDisplay(session), NamedTextColor.AQUA,
                List.of("任務：" + session.contract.display(), "隊員：" + displayMembers(session),
                        progressDisplay(session))));
        if (session.phase == MissionPhase.GATHER) {
            Material material = Material.matchMaterial(session.contract.objectiveMaterial());
            int held = material == null ? 0 : session.members.stream().map(Bukkit::getPlayer)
                    .filter(Objects::nonNull).mapToInt(member -> countMaterial(member, material)).sum();
            inventory.setItem(20, menuItem(Material.BUNDLE, "共同繳交物資", NamedTextColor.GREEN,
                    List.of(session.contract.objectiveDisplay() + " " + held + "/" + session.remaining,
                            "由隊員背包共同扣除。")));
        }
        inventory.setItem(22, menuItem(Material.BARRIER, "終止本次任務", NamedTextColor.RED,
                List.of("移除本次敵軍、觀測標與 NPC，保留角色既有成長。")));
        player.openInventory(inventory);
    }

    private void openRosterMenu(Player player) {
        if (npcRoster == null) return;
        MissionMenuHolder holder = new MissionMenuHolder(MenuType.ROSTER, null);
        Inventory inventory = createInventory(holder, "巡防員輪值表");
        inventory.setItem(10, rosterItem(NpcRole.HANSHAN, Material.SHIELD));
        inventory.setItem(12, rosterItem(NpcRole.YANGWU, Material.CROSSBOW));
        inventory.setItem(14, rosterItem(NpcRole.WUJI, Material.SPYGLASS));
        inventory.setItem(16, rosterItem(NpcRole.DOUTIAN, Material.IRON_AXE));
        inventory.setItem(22, menuItem(Material.ARROW, "返回任務公告", NamedTextColor.GREEN, List.of()));
        player.openInventory(inventory);
    }

    private ItemStack rosterItem(NpcRole role, Material material) {
        NpcRosterSnapshot state = npcRoster.state(role);
        List<String> lore = new ArrayList<>();
        lore.add(npcRoster.statusText(role));
        lore.add("點擊消耗治療物資，解除負傷並降低疲勞。");
        lore.add("負傷中或疲勞過高時不會參與新任務。");
        return menuItem(material, role.display(), state.available(System.currentTimeMillis(),
                plugin.getConfig().getInt("npc-roster.fatigue-limit", 80))
                ? NamedTextColor.GREEN : NamedTextColor.RED, lore);
    }

    private void treatNpc(Player player, NpcRole role) {
        NpcRosterSnapshot current = npcRoster.state(role);
        if (current.fatigue() == 0 && !current.injured(System.currentTimeMillis())) {
            player.sendMessage(EvilIslandPlugin.message(role.display() + "狀態良好，不需要治療。"));
            return;
        }
        Material material = Material.matchMaterial(plugin.getConfig().getString(
                "npc-roster.treatment-material", "HONEY_BOTTLE"));
        int amount = Math.max(1, plugin.getConfig().getInt("npc-roster.treatment-amount", 1));
        if (material == null || countMaterial(player, material) < amount) {
            player.sendMessage(EvilIslandPlugin.message("治療物資不足，需要 " + amount + " 個"
                    + (material == null ? "有效物資" : materialName(material)) + "。"));
            return;
        }
        removeMaterial(player, material, amount);
        npcRoster.treat(role);
        player.sendMessage(EvilIslandPlugin.message(role.display() + "已完成治療與休整。",
                NamedTextColor.GREEN));
        openRosterMenu(player);
    }

    private void openCancelConfirmation(Player player, UUID sessionId) {
        MissionMenuHolder holder = new MissionMenuHolder(MenuType.CANCEL, sessionId);
        Inventory inventory = createInventory(holder, "確認終止任務");
        inventory.setItem(11, menuItem(Material.LIME_CONCRETE, "返回編組", NamedTextColor.GREEN, List.of()));
        inventory.setItem(15, menuItem(Material.RED_CONCRETE, "確認終止", NamedTextColor.RED,
                List.of("全隊將返回可重新編組狀態。")));
        player.openInventory(inventory);
    }

    private void sendDuoInvite(Player leader, Player partner, MissionContract contract) {
        long expiresAt = System.currentTimeMillis()
                + plugin.getConfig().getLong("patrol-party.invite-timeout-ms", 15000L);
        pendingInvites.put(partner.getUniqueId(), new PendingInvite(leader.getUniqueId(), expiresAt, contract));
        MissionMenuHolder holder = new MissionMenuHolder(MenuType.INVITE, leader.getUniqueId());
        Inventory inventory = createInventory(holder, "雙人任務邀請");
        inventory.setItem(13, menuItem(Material.PLAYER_HEAD, leader.getName() + "邀請你加入任務",
                NamedTextColor.AQUA, List.of("任務：" + contract.display(), duoDescription(contract))));
        inventory.setItem(11, menuItem(Material.RED_CONCRETE, "婉拒", NamedTextColor.RED, List.of()));
        inventory.setItem(15, menuItem(Material.LIME_CONCRETE, "加入編組", NamedTextColor.GREEN, List.of()));
        leader.closeInventory();
        partner.openInventory(inventory);
        leader.sendMessage(EvilIslandPlugin.message("已向 " + partner.getName() + " 發出雙人任務邀請。"));
        partner.sendMessage(EvilIslandPlugin.message("請在編組介面確認是否加入。", NamedTextColor.YELLOW));
        long delay = Math.max(20L, (expiresAt - System.currentTimeMillis() + 49L) / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> pendingInvites.remove(partner.getUniqueId(),
                new PendingInvite(leader.getUniqueId(), expiresAt, contract)), delay);
    }

    private void cancelSession(UUID sessionId) {
        MissionSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        forEachOnline(session, member -> {
            profiles.setObjective(member, profiles.transformations(member) > 0
                    ? ObjectiveStage.COMPLETE : ObjectiveStage.REPORT_PATROL);
            member.sendMessage(EvilIslandPlugin.message("本次任務已終止，可重新向撼山巡防員編組。"));
        });
        session.phase = MissionPhase.COMPLETE_PENDING;
        worldEvents.transition(session.id, WorldEventState.FAILED);
        if (telemetry != null) telemetry.fail(session.id, "player_cancelled");
        if (session.supportRole != null && npcRoster != null) npcRoster.abortMission(session.supportRole);
        removeSessionActors(session);
        cleanupSession(session.id);
    }

    private void removeSessionActors(MissionSession session) {
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
        session.actorId = null;
        session.fortificationIds.clear();
        session.lastFortificationDamage.clear();
    }

    private void cleanupSession(UUID sessionId) {
        MissionSession session = sessions.remove(sessionId);
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

    private ArmorStand createAnchor(Location location, MissionSession session) {
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

    private ArmorStand createScoutMarker(Location location, MissionSession session) {
        ArmorStand marker = location.getWorld().spawn(location, ArmorStand.class);
        marker.setInvisible(true);
        marker.setSmall(true);
        marker.setGravity(false);
        marker.setInvulnerable(true);
        marker.setPersistent(true);
        marker.setGlowing(true);
        marker.customName(Component.text("輕疾觀測標", NamedTextColor.AQUA));
        marker.setCustomNameVisible(true);
        if (marker.getEquipment() != null) {
            marker.getEquipment().setHelmet(new ItemStack(Material.SOUL_LANTERN));
        }
        marker.getPersistentDataContainer().set(missionActorKey, PersistentDataType.STRING, "scout_marker");
        tag(marker, session);
        return marker;
    }

    private Villager createMissionNpc(Location location, MissionSession session, boolean downed) {
        Villager actor = location.getWorld().spawn(location, Villager.class);
        actor.setAdult();
        actor.setProfession(downed ? Villager.Profession.CARTOGRAPHER : Villager.Profession.LEATHERWORKER);
        actor.setPersistent(true);
        actor.setRemoveWhenFarAway(false);
        actor.setCanPickupItems(false);
        actor.setInvulnerable(true);
        actor.setAI(!downed);
        actor.setCollidable(false);
        actor.customName(Component.text(downed ? "失聯巡防員（倒地）" : "新城護送員",
                downed ? NamedTextColor.RED : NamedTextColor.GREEN));
        actor.setCustomNameVisible(true);
        actor.getPersistentDataContainer().set(missionActorKey, PersistentDataType.STRING,
                downed ? "rescue_survivor" : "escort_member");
        tag(actor, session);
        return actor;
    }

    private ArmorStand createFortification(Location location, MissionSession session, int entry, int health) {
        ArmorStand fortification = location.getWorld().spawn(location, ArmorStand.class);
        fortification.setInvisible(true);
        fortification.setSmall(false);
        fortification.setGravity(false);
        fortification.setInvulnerable(true);
        fortification.setPersistent(true);
        fortification.setRemoveWhenFarAway(false);
        fortification.setBasePlate(false);
        fortification.setCustomNameVisible(true);
        PersistentDataContainer data = fortification.getPersistentDataContainer();
        data.set(fortificationKey, PersistentDataType.BYTE, (byte) 1);
        data.set(fortificationEntryKey, PersistentDataType.INTEGER, entry);
        tag(fortification, session);
        setFortificationHealth(session, fortification, entry, health);
        return fortification;
    }

    private void ensureFortifications(MissionSession session, Location center) {
        for (int entry = 0; entry < session.contract.defenseEntrances(); entry++) {
            final int expectedEntry = entry;
            Entity existing = session.fortificationIds.stream().map(Bukkit::getEntity)
                    .filter(Objects::nonNull).filter(Entity::isValid)
                    .filter(entity -> fortificationEntry(entity) == expectedEntry).findFirst().orElse(null);
            if (existing != null) continue;
            Location location = defenseEntry(center, entry, true);
            Entity nearby = location.getWorld().getNearbyEntities(location, 3, 3, 3).stream()
                    .filter(this::isFortification).filter(entity -> session.id.equals(sessionId(entity)))
                    .filter(entity -> fortificationEntry(entity) == expectedEntry).findFirst().orElse(null);
            if (nearby != null) {
                session.fortificationIds.add(nearby.getUniqueId());
                continue;
            }
            int health = session.fortificationHealth.getOrDefault(entry, fortificationMaximumHealth());
            ArmorStand created = createFortification(location, session, entry, health);
            session.fortificationIds.add(created.getUniqueId());
        }
    }

    private void setFortificationHealth(MissionSession session, ArmorStand fortification, int entry, int requested) {
        int health = Math.max(0, Math.min(fortificationMaximumHealth(), requested));
        session.fortificationHealth.put(entry, health);
        fortification.getPersistentDataContainer().set(fortificationHealthKey, PersistentDataType.INTEGER, health);
        if (fortification.getEquipment() != null) {
            fortification.getEquipment().setHelmet(new ItemStack(health > 0
                    ? Material.COBBLESTONE_WALL : Material.CRACKED_STONE_BRICKS));
        }
        fortification.customName(Component.text(health > 0
                ? "防線工事 " + health + "/" + fortificationMaximumHealth()
                : "防線工事（損毀，右鍵修復）", health > 0 ? NamedTextColor.AQUA : NamedTextColor.RED));
    }

    private int fortificationHealth(Entity entity) {
        Integer value = entity.getPersistentDataContainer().get(fortificationHealthKey, PersistentDataType.INTEGER);
        return value == null ? 0 : Math.max(0, value);
    }

    private int fortificationEntry(Entity entity) {
        Integer value = entity.getPersistentDataContainer().get(fortificationEntryKey, PersistentDataType.INTEGER);
        return value == null ? -1 : value;
    }

    private int fortificationMaximumHealth() {
        int wallBonus = development == null ? 0
                : (development.functionalProjectLevel(tw.zack.evilisland.model.CityProject.WALLS) + 1) / 2;
        return Math.max(1, plugin.getConfig().getInt("missions.defense.fortification-health", 3)
                + campaign.fortificationDurabilityBonus() + wallBonus);
    }

    private Location defenseEntry(Location center, int entry, boolean fortification) {
        double distance = plugin.getConfig().getDouble(fortification
                ? "missions.defense.fortification-distance" : "missions.defense.entry-distance",
                fortification ? 11.0 : 24.0);
        int direction = Math.floorMod(entry, 4);
        double dx = direction == 0 ? distance : direction == 1 ? -distance : 0.0;
        double dz = direction == 2 ? distance : direction == 3 ? -distance : 0.0;
        return ground(center.clone().add(dx, 0, dz));
    }

    private Entity ensureMissionActor(MissionSession session, MissionPhase phase) {
        Entity actor = session.actorId == null ? null : Bukkit.getEntity(session.actorId);
        if (actor != null && actor.isValid()) return actor;
        if (actor == null && session.actorId != null) {
            return null;
        }
        session.actorId = null;
        Location expected = phase == MissionPhase.ESCORT ? guardPost(session) : missionTarget(session);
        if (expected == null || !expected.getChunk().isLoaded()) return null;
        for (Entity nearby : expected.getWorld().getNearbyEntities(expected, 12, 12, 12)) {
            if (isMissionActor(nearby) && session.id.equals(sessionId(nearby))) {
                session.actorId = nearby.getUniqueId();
                return nearby;
            }
        }
        if (phase == MissionPhase.SCOUT) {
            actor = createScoutMarker(expected, session);
        } else {
            boolean downed = phase == MissionPhase.RESCUE_SEARCH;
            actor = createMissionNpc(expected, session, downed);
            if (phase == MissionPhase.RESCUE_RETURN && actor instanceof Mob survivor) {
                survivor.setAI(true);
                survivor.customName(Component.text("失聯巡防員（已獲救）", NamedTextColor.GREEN));
            }
        }
        session.actorId = actor.getUniqueId();
        updateAnchor(session);
        return actor;
    }

    private void followMissionActor(Mob actor, Player leader) {
        double teleportDistance = plugin.getConfig().getDouble("missions.actor.teleport-distance", 24.0);
        double distance = actor.getLocation().distanceSquared(leader.getLocation());
        if (distance > teleportDistance * teleportDistance) {
            actor.teleport(ground(leader.getLocation().clone().add(-1.5, 0, -1.5)));
            return;
        }
        double followDistance = plugin.getConfig().getDouble("missions.actor.follow-distance", 4.5);
        if (distance > followDistance * followDistance) {
            actor.getPathfinder().moveTo(leader.getLocation(),
                    plugin.getConfig().getDouble("missions.actor.follow-speed", 1.08));
        }
    }

    private Player nearestMember(MissionSession session, Location location) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (UUID memberId : session.members) {
            Player player = Bukkit.getPlayer(memberId);
            if (player == null || !player.isOnline() || player.isDead()
                    || !player.getWorld().equals(location.getWorld())) continue;
            double distance = player.getLocation().distanceSquared(location);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private Location guardPost(MissionSession session) {
        Location post = daoFields.guardPost();
        World world = Bukkit.getWorld(session.world);
        if (post == null || world == null || !world.equals(post.getWorld())) return null;
        return ground(post.clone().add(3.0, 0, 0));
    }

    private Location defenseCenter(MissionSession session) {
        World world = Bukkit.getWorld(session.world);
        return world == null ? null : defenseCenter(world);
    }

    private Location defenseCenter(World world) {
        Location post = daoFields.guardPost();
        if (post == null || !world.equals(post.getWorld())) return null;
        return ground(post.clone());
    }

    private boolean isDefense(MissionSession session) {
        return session.contract.missionType() == MissionType.DEFENSE;
    }

    private String eventType(MissionSession session) {
        return isDefense(session) ? "city_defense" : "east_patrol";
    }

    private void recoverSessionEntity(Entity entity) {
        UUID id = sessionId(entity);
        MissionSession session = id == null ? null : sessions.get(id);
        if (session != null && companions.isCompanion(entity)) {
            session.companionId = entity.getUniqueId();
        } else if (session != null && isFortification(entity)) {
            if (session.phase == MissionPhase.COMPLETE_PENDING) {
                entity.remove();
            } else {
                session.fortificationIds.add(entity.getUniqueId());
                int entry = fortificationEntry(entity);
                if (entry >= 0) session.fortificationHealth.put(entry, fortificationHealth(entity));
            }
        } else if (session != null && isMissionActor(entity)) {
            if (session.phase == MissionPhase.COMPLETE_PENDING) entity.remove();
            else session.actorId = entity.getUniqueId();
        } else if (session == null && (isMissionActor(entity) || isFortification(entity))) {
            entity.remove();
        }
    }

    private void tag(Entity entity, MissionSession session) {
        entity.getPersistentDataContainer().set(sessionKey, PersistentDataType.STRING, session.id.toString());
    }

    private void updateAnchor(MissionSession session) {
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
        data.set(pendingQiKey, PersistentDataType.STRING, encodeMembers(session.pendingQi));
        data.set(contractKey, PersistentDataType.STRING, session.contract.id());
        data.set(fullRewardsKey, PersistentDataType.BYTE, session.fullRewards ? (byte) 1 : (byte) 0);
        if (session.supportRole == null) data.remove(supportRoleKey);
        else data.set(supportRoleKey, PersistentDataType.STRING, session.supportRole.id());
        if (session.actorId == null) data.remove(actorIdKey);
        else data.set(actorIdKey, PersistentDataType.STRING, session.actorId.toString());
        data.set(waveKey, PersistentDataType.INTEGER, session.wave);
        data.set(breachesKey, PersistentDataType.INTEGER, session.breaches);
        data.set(fortificationStateKey, PersistentDataType.STRING, encodeFortifications(session.fortificationHealth));
    }

    private void recoverAnchor(Entity anchor) {
        UUID id = sessionId(anchor);
        MissionPhase phase = MissionPhase.parse(anchor.getPersistentDataContainer().get(phaseKey, PersistentDataType.STRING));
        Set<UUID> members = decodeMembers(anchor.getPersistentDataContainer().get(membersKey, PersistentDataType.STRING));
        MissionContract contract = MissionContract.parse(
                anchor.getPersistentDataContainer().get(contractKey, PersistentDataType.STRING));
        if (id == null || phase == null || members.isEmpty() || anchor.getWorld() == null) {
            anchor.getChunk().setForceLoaded(false);
            anchor.remove();
            return;
        }
        MissionSession session = sessions.computeIfAbsent(id,
                ignored -> new MissionSession(id, anchor.getWorld().getUID(), members, phase,
                        contract == null ? MissionContract.EAST_CLEARANCE : contract));
        anchor.getChunk().setForceLoaded(true);
        session.anchorId = anchor.getUniqueId();
        session.phase = phase;
        Integer remaining = anchor.getPersistentDataContainer().get(remainingKey, PersistentDataType.INTEGER);
        session.remaining = remaining == null ? 0
                : phase == MissionPhase.ESCORT ? Math.max(-1, remaining) : Math.max(0, remaining);
        session.pendingZaochi.putAll(readCounts(anchor.getPersistentDataContainer(), pendingZaochiKey));
        session.pendingXingtian.putAll(readCounts(anchor.getPersistentDataContainer(), pendingXingtianKey));
        session.pendingBonus.putAll(readCounts(anchor.getPersistentDataContainer(), pendingBonusKey));
        session.pendingCompletion.addAll(decodeMembers(
                anchor.getPersistentDataContainer().get(pendingCompletionKey, PersistentDataType.STRING)));
        session.pendingQi.addAll(decodeMembers(
                anchor.getPersistentDataContainer().get(pendingQiKey, PersistentDataType.STRING)));
        Byte fullRewards = anchor.getPersistentDataContainer().get(fullRewardsKey, PersistentDataType.BYTE);
        session.fullRewards = fullRewards == null || fullRewards != 0;
        session.supportRole = NpcRole.parse(
                anchor.getPersistentDataContainer().get(supportRoleKey, PersistentDataType.STRING));
        session.actorId = parseUuid(anchor.getPersistentDataContainer().get(actorIdKey, PersistentDataType.STRING));
        Integer wave = anchor.getPersistentDataContainer().get(waveKey, PersistentDataType.INTEGER);
        Integer breaches = anchor.getPersistentDataContainer().get(breachesKey, PersistentDataType.INTEGER);
        session.wave = wave == null ? 0 : Math.max(0, wave);
        session.breaches = breaches == null ? 0 : Math.max(0, breaches);
        session.fortificationHealth.putAll(decodeFortifications(
                anchor.getPersistentDataContainer().get(fortificationStateKey, PersistentDataType.STRING)));
        members.forEach(memberId -> sessionByMember.put(memberId, id));
        if (telemetry != null && phase != MissionPhase.COMPLETE_PENDING) {
            telemetry.start(id, session.contract, members.size());
        }
    }

    private MissionSession sessionFor(Player player) {
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
        return parseUuid(value);
    }

    private UUID parseUuid(String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isAnchor(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(anchorKey, PersistentDataType.BYTE);
    }

    private boolean isMissionActor(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(missionActorKey, PersistentDataType.STRING);
    }

    private boolean isFortification(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(fortificationKey, PersistentDataType.BYTE);
    }

    private void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack remaining : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), remaining);
        }
    }

    private String displayMembers(MissionSession session) {
        List<String> names = new ArrayList<>();
        for (UUID memberId : session.members) {
            Player player = Bukkit.getPlayer(memberId);
            names.add(player == null ? memberId.toString().substring(0, 8) : player.getName());
        }
        return String.join("、", names);
    }

    private String phaseDisplay(MissionSession session) {
        return switch (session.phase) {
            case PATROL -> "鑿齒巡防進行中";
            case GATHER -> "物資採集進行中";
            case SCOUT -> "荒原偵察進行中";
            case ESCORT -> "東境護送進行中";
            case RESCUE_SEARCH -> "搜尋失聯人員";
            case RESCUE_RETURN -> "護送獲救者返城";
            case DEFENSE -> "東門多入口守城";
            case BOSS_READY -> "等待全隊完成易質";
            case BOSS -> "刑天迎擊進行中";
            case COMPLETE_PENDING -> "等待離線隊員結算";
        };
    }

    private String duoDescription(MissionContract contract) {
        return switch (contract.missionType()) {
            case PATROL -> "雙人模式會增加敵軍數量，但單人承受壓力不會翻倍。";
            case GATHER -> "全隊共享目標，需求為單人基準的 1.5 倍。";
            case SCOUT -> "任一隊員啟動觀測標即可讓全隊完成。";
            case ESCORT -> "護送員會跟隨最近隊員；雙人伏擊增加一名追兵。";
            case RESCUE -> "任一隊員可救起目標；追兵清除後共同返城。";
            case DEFENSE -> "雙人會增加各入口敵軍，但可分別維修與攔截不同方向。";
        };
    }

    private String soloSupportDescription(MissionContract contract) {
        return switch (contract.missionType()) {
            case PATROL, ESCORT -> "揚武可出勤時會提供遠程掩護。";
            case SCOUT, RESCUE -> "無跡可出勤時會跟隊追蹤地面足跡與記號。";
            case GATHER -> "採集任務不佔用巡防員輪值。";
            case DEFENSE -> "揚武可出勤時會在多入口攻勢中提供遠程掩護。";
        };
    }

    private String progressDisplay(MissionSession session) {
        return switch (session.phase) {
            case PATROL, BOSS -> "剩餘敵軍：" + session.remaining;
            case GATHER -> "需求：" + session.contract.objectiveDisplay() + " " + session.remaining;
            case SCOUT -> "目標：啟動荒原觀測標";
            case ESCORT -> session.remaining < 0 ? "目標：帶領護送員前往指定地點"
                    : "追兵：" + session.remaining + "・清除後繼續護送";
            case RESCUE_SEARCH -> "目標：找到失聯人員並右鍵救援";
            case RESCUE_RETURN -> "追兵：" + session.remaining + "・帶獲救者返回新城";
            case DEFENSE -> "波次：" + session.wave + "/" + session.contract.defenseWaves()
                    + "　敵軍：" + session.remaining + "　失守：" + session.breaches;
            case BOSS_READY -> "目標：完成易質後迎戰刑天";
            case COMPLETE_PENDING -> "目標：等待隊員結算";
        };
    }

    private String missionInstruction(MissionSession session) {
        return switch (session.contract.missionType()) {
            case PATROL -> "鑿齒小隊出現在東門外高道息區。";
            case GATHER -> "共同準備 " + session.remaining + " 個「" + session.contract.objectiveDisplay()
                    + "」，回到撼山巡防員處開啟任務介面繳交。";
            case SCOUT -> {
                Entity actor = Bukkit.getEntity(session.actorId);
                Location location = actor == null ? missionTarget(session) : actor.getLocation();
                if (location == null) yield "前往荒原尋找發光的輕疾觀測標。";
                yield "前往荒原座標 X " + location.getBlockX() + "、Z " + location.getBlockZ()
                        + "，右鍵啟動發光的輕疾觀測標。";
            }
            case ESCORT -> {
                Location location = missionTarget(session);
                if (location == null) yield "從巡防站帶領護送員前往東境。";
                yield "帶領護送員前往 X " + location.getBlockX() + "、Z " + location.getBlockZ()
                        + "；中途遇襲時必須先清除追兵。";
            }
            case RESCUE -> {
                if (session.phase == MissionPhase.RESCUE_RETURN) {
                    yield "清除剩餘 " + session.remaining + " 名追兵，帶獲救者返回新城巡防站。";
                }
                Location location = missionTarget(session);
                if (location == null) yield "前往荒原搜尋失聯巡防員。";
                yield "前往 X " + location.getBlockX() + "、Z " + location.getBlockZ()
                        + "，右鍵救起倒地的巡防員。";
            }
            case DEFENSE -> "留在東門巡防站阻止鑿齒突破中心；右鍵防線工事可消耗鵝卵石修復。";
        };
    }

    public String missionObjective(Player player) {
        MissionSession session = sessionFor(player);
        return session == null ? null : missionInstruction(session);
    }

    private Location missionTarget(MissionSession session) {
        World world = Bukkit.getWorld(session.world);
        Location center = world == null ? null : daoFields.patrolCenter(world);
        if (center == null) return null;
        return ground(center.clone().add(session.contract.targetOffsetX(), 0, session.contract.targetOffsetZ()));
    }

    private void forEachOnline(MissionSession session, java.util.function.Consumer<Player> action) {
        for (UUID memberId : session.members) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null && player.isOnline()) {
                action.accept(player);
            }
        }
    }

    private Inventory createInventory(MissionMenuHolder holder, String title) {
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

    private double square(double value) {
        return value * value;
    }

    private String materialName(Material material) {
        return material == Material.HONEY_BOTTLE ? "蜂蜜瓶" : material.name();
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

    private String encodeFortifications(Map<Integer, Integer> health) {
        return health.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + Math.max(0, entry.getValue()))
                .reduce((left, right) -> left + "," + right).orElse("");
    }

    private Map<Integer, Integer> decodeFortifications(String encoded) {
        Map<Integer, Integer> result = new HashMap<>();
        if (encoded == null || encoded.isBlank()) return result;
        for (String entry : encoded.split(",")) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) continue;
            try {
                result.put(Math.max(0, Integer.parseInt(parts[0])), Math.max(0, Integer.parseInt(parts[1])));
            } catch (NumberFormatException ignored) {
                // Ignore one corrupt fortification entry and preserve the remaining defense session.
            }
        }
        return result;
    }

    private enum MenuType {
        CONTRACT,
        ASSEMBLY,
        ACTIVE,
        CANCEL,
        INVITE,
        ROSTER,
        WEEKLY,
        WEEKLY_CONFIRM
    }

    private static final class MissionMenuHolder implements InventoryHolder {
        private final MenuType type;
        private final UUID sessionId;
        private Inventory inventory;

        private MissionMenuHolder(MenuType type, UUID sessionId) {
            this.type = type;
            this.sessionId = sessionId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class MissionSession {
        private final UUID id;
        private final UUID world;
        private final Set<UUID> members;
        private final Map<UUID, Integer> pendingZaochi = new HashMap<>();
        private final Map<UUID, Integer> pendingXingtian = new HashMap<>();
        private final Map<UUID, Integer> pendingBonus = new HashMap<>();
        private final Set<UUID> pendingCompletion = new HashSet<>();
        private final Set<UUID> pendingQi = new HashSet<>();
        private final Set<UUID> fortificationIds = new HashSet<>();
        private final Map<Integer, Integer> fortificationHealth = new HashMap<>();
        private final Map<UUID, Long> lastFortificationDamage = new HashMap<>();
        private final MissionContract contract;
        private MissionPhase phase;
        private UUID anchorId;
        private UUID companionId;
        private UUID actorId;
        private int remaining;
        private boolean fullRewards = true;
        private NpcRole supportRole;
        private int wave;
        private int breaches;
        private boolean wavePending;

        private MissionSession(UUID id, UUID world, Set<UUID> members, MissionPhase phase, MissionContract contract) {
            this.id = id;
            this.world = world;
            this.members = new HashSet<>(members);
            this.phase = phase;
            this.contract = contract;
        }
    }

    private record PendingInvite(UUID leaderId, long expiresAt, MissionContract contract) {
    }
}
