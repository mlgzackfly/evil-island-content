package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import tw.zack.evilisland.world.WorldAtlasService;
import tw.zack.evilisland.world.WorldLandmark;
import tw.zack.evilisland.world.LandmarkDetailService;
import tw.zack.evilisland.world.WorldQualityAuditService;
import tw.zack.evilisland.model.SpeciesType;
import tw.zack.evilisland.model.WeaponType;
import tw.zack.evilisland.model.NpcRole;
import tw.zack.evilisland.persistence.DatabaseManager;
import tw.zack.evilisland.persistence.CampaignRepository;
import tw.zack.evilisland.persistence.PlayerProfileRepository;
import tw.zack.evilisland.persistence.WorldEventRepository;
import tw.zack.evilisland.persistence.NpcRosterRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class EvilIslandPlugin extends JavaPlugin implements TabExecutor {
    private static final Component PREFIX = Component.text("[噩盡島] ", NamedTextColor.DARK_AQUA);

    private PlayerProfileService profiles;
    private DaoFieldService daoFields;
    private GameItemService items;
    private WeaponService weapons;
    private SpeciesService species;
    private CompanionService companions;
    private EncounterService encounters;
    private ProgressionService progression;
    private CharacterCreationService characterCreation;
    private CombatService combat;
    private WorldAtlasService atlas;
    private LandmarkDetailService landmarkDetails;
    private WorldQualityAuditService worldAudit;
    private DatabaseManager database;
    private WorldEventService worldEvents;
    private CampaignService campaign;
    private NpcRosterService npcRoster;

    public static Component message(String text) {
        return PREFIX.append(Component.text(text));
    }

    public static Component message(String text, NamedTextColor color) {
        return PREFIX.append(Component.text(text, color));
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        database = new DatabaseManager(getDataFolder().toPath(),
                getConfig().getInt("database.backup-retention", 10), getLogger());
        database.initialize();
        PlayerProfileRepository profileRepository = new PlayerProfileRepository(database);
        worldEvents = new WorldEventService(this, database, new WorldEventRepository(database));
        worldEvents.load();
        campaign = new CampaignService(this, database, new CampaignRepository(database));
        campaign.load();
        npcRoster = new NpcRosterService(this, database, new NpcRosterRepository(database));
        npcRoster.load();

        atlas = new WorldAtlasService(this);
        atlas.loadWorlds();
        landmarkDetails = new LandmarkDetailService(this, atlas);
        landmarkDetails.scheduleUpgrade();
        worldAudit = new WorldQualityAuditService(this, atlas, landmarkDetails);
        worldAudit.schedule();
        profiles = new PlayerProfileService(this, database, profileRepository);
        daoFields = new DaoFieldService(this, atlas);
        items = new GameItemService(this);
        weapons = new WeaponService(this, profiles, items);
        companions = new CompanionService(this);
        species = new SpeciesService(this, profiles);
        species.setCompanionResolver(companions::isCombatReady);
        encounters = new EncounterService(this, profiles, daoFields, items, species, weapons, companions,
                worldEvents, campaign, npcRoster);
        species.setEncounterTargetResolver(encounters::canTarget);
        species.setEncounterGroupResolver(encounters::sameEncounter);
        companions.setEnemyResolver(encounters::isEncounterEnemy);
        companions.setInjuryListener(npcRoster::injure);
        weapons.setEnemyResolver(encounters::isEncounterEnemy);
        progression = new ProgressionService(this, profiles, daoFields, items, encounters);
        characterCreation = new CharacterCreationService(this, profiles, daoFields);
        combat = new CombatService(this, profiles, daoFields, encounters, items);

        Objects.requireNonNull(getCommand("evil")).setExecutor(this);
        Objects.requireNonNull(getCommand("evil")).setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(profiles, this);
        Bukkit.getPluginManager().registerEvents(encounters, this);
        Bukkit.getPluginManager().registerEvents(species, this);
        Bukkit.getPluginManager().registerEvents(companions, this);
        Bukkit.getPluginManager().registerEvents(weapons, this);
        Bukkit.getPluginManager().registerEvents(characterCreation, this);
        Bukkit.getPluginManager().registerEvents(progression, this);
        Bukkit.getPluginManager().registerEvents(combat, this);
        Bukkit.getPluginManager().registerEvents(atlas, this);
        Bukkit.getScheduler().runTaskTimer(this, combat::tickPlayers, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, species::tick, 40L, 5L);
        Bukkit.getScheduler().runTaskTimer(this, companions::tick, 45L, 5L);
        Bukkit.getScheduler().runTaskTimer(this, encounters::tick, 50L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, campaign::tickDay, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, profiles::flushDirty,
                getConfig().getLong("database.autosave-ticks", 100L),
                getConfig().getLong("database.autosave-ticks", 100L));
        species.recover(atlas.mainWorld());
        companions.recover(atlas.mainWorld());
        encounters.recover(atlas.mainWorld());

        if (daoFields.isConfigured()) {
            encounters.setupGuard();
        } else {
            getLogger().warning("New City is not configured. Run /evil admin setup in game before starting patrols.");
        }
        getLogger().info("EvilIsland persistent world enabled.");
    }

    @Override
    public void onDisable() {
        if (combat != null) {
            combat.clearRuntimeState();
        }
        if (weapons != null) {
            weapons.clearRuntimeState();
        }
        if (species != null) {
            species.clearRuntimeState();
        }
        if (companions != null) {
            companions.clearRuntimeState();
        }
        if (encounters != null) {
            encounters.clearRuntimeState();
        }
        if (profiles != null) {
            profiles.flushAll();
        }
        if (worldEvents != null) {
            worldEvents.flushAll();
        }
        if (campaign != null) {
            campaign.flush();
        }
        if (npcRoster != null) {
            npcRoster.flush();
        }
        if (database != null) {
            database.close();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (subcommand.equals("status")) {
            status(sender);
        } else if (subcommand.equals("guide")) {
            guide(sender);
        } else if (subcommand.equals("admin")) {
            admin(sender, args);
        } else {
            sendHelp(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("help", "status", "guide", "admin"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return filter(args[1], List.of("atlas", "setup", "spawn", "reset", "reload", "selftest"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("atlas")) {
            List<String> ids = new ArrayList<>();
            for (WorldLandmark landmark : WorldLandmark.values()) ids.add(landmark.id());
            ids.add("palace-realm");
            return filter(args[2], ids);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("spawn")) {
            return filter(args[2], List.of("zaochi", "xingtian"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("reset")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(args[2], names);
        }
        return Collections.emptyList();
    }

    private void status(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (!profiles.isEnlisted(player)) {
            player.sendMessage(message(profiles.isMeasured(player)
                    ? "先天傾向已測定，炁訣尚未定型；請前往聚炁鏡庭。"
                    : "尚未接受炁息測定；請前往新城聚炁鏡庭。"));
            return;
        }
        DaoFieldService.Reading reading = daoFields.reading(player.getLocation());
        player.sendMessage(message("巡防員狀態", NamedTextColor.AQUA));
        player.sendMessage(Component.text("傾向：" + profiles.tendency(player).display()
                + "　定型：" + profiles.formulaPath(player).display(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("炁息：" + profiles.qi(player) + "/" + profiles.maxQi(player)
                + "　所在地道息：" + reading.dao() + "（" + reading.region() + "）", NamedTextColor.GRAY));
        player.sendMessage(Component.text("妖質：" + profiles.essence(player) + "　易質："
                + (profiles.transformations(player) == 0 ? "未進行" : "第一階段"), NamedTextColor.GRAY));
        player.sendMessage(Component.text("目標：" + progression.objectiveText(player), NamedTextColor.YELLOW));
        player.sendMessage(Component.text(campaign.scheduleText() + "　" + campaign.metricsText(), NamedTextColor.GRAY));
    }

    private void guide(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        player.sendMessage(message(progression.objectiveText(player), NamedTextColor.YELLOW));
        if (!profiles.isMeasured(player)) {
            player.sendMessage(Component.text("前往新城聚炁鏡庭，右鍵中央聚炁鏡接受測定。", NamedTextColor.GRAY));
        } else if (!profiles.isFormulaLocked(player)) {
            player.sendMessage(Component.text("再次右鍵聚炁鏡，選擇並確認不可逆的存想定型。", NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("戰鬥時潛行右鍵運用" + profiles.formulaPath(player).display()
                    + "；右鍵煉化臺與聚炁鏡處理成長。向撼山巡防員報到可開始巡防。", NamedTextColor.GRAY));
        }
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("evilisland.admin")) {
            sender.sendMessage(message("你沒有新城管理權限。", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(message("/evil admin <atlas|setup|spawn|reset|reload|selftest>"));
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("reload")) {
            reloadConfig();
            sender.sendMessage(message("設定已重新載入。"));
            return;
        }
        if (action.equals("selftest")) {
            runDomainSelfTest(sender);
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (action.equals("atlas")) {
            if (args.length < 3) {
                player.sendMessage(message("/evil admin atlas <地標>"));
                return;
            }
            if (args[2].equalsIgnoreCase("palace-realm") && atlas.palaceRealm() != null) {
                player.teleport(new org.bukkit.Location(atlas.palaceRealm(), 0.5, 93, 0.5));
                player.sendMessage(message("已抵達龍宮內層領域。"));
                return;
            }
            WorldLandmark landmark = WorldLandmark.parse(args[2]);
            if (landmark == null) {
                player.sendMessage(message("未知地標。"));
                return;
            }
            player.teleport(atlas.landmarkLocation(landmark));
            player.sendMessage(message("已抵達" + landmark.display() + "。"));
        } else if (action.equals("setup")) {
            daoFields.setupNewCity(player);
            encounters.setupGuard();
            player.sendMessage(message("已在目前位置建立新城測試點：東側為煉化臺，西側為聚炁鏡。", NamedTextColor.GREEN));
        } else if (action.equals("spawn")) {
            String type = args.length >= 3 ? args[2] : "zaochi";
            encounters.spawnForAdmin(player, type);
            player.sendMessage(message("已生成測試敵人：" + type + "。"));
        } else if (action.equals("reset")) {
            Player target = args.length >= 3 ? Bukkit.getPlayer(args[2]) : player;
            if (target == null) {
                player.sendMessage(message("找不到指定玩家。"));
                return;
            }
            profiles.reset(target);
            target.sendMessage(message("巡防測試資料已重設。"));
        } else {
            player.sendMessage(message("/evil admin <atlas|setup|spawn|reset|reload|selftest>"));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(message("噩盡島世界", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("角色測定、炁訣定型與巡防均透過新城場景互動完成。", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/evil status　/evil guide", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("管理員：/evil admin <atlas|setup|spawn|reset|reload|selftest>", NamedTextColor.GRAY));
    }

    private void runDomainSelfTest(CommandSender sender) {
        int weaponChecks = 0;
        java.util.UUID owner = new java.util.UUID(0L, 1L);
        for (WeaponType type : WeaponType.values()) {
            org.bukkit.inventory.ItemStack stack = items.createWeapon(type, owner);
            if (items.weaponType(stack) == type && items.isOwnedWeapon(stack, owner)) {
                weaponChecks++;
            }
        }

        org.bukkit.Location center = daoFields.patrolCenter(atlas.mainWorld());
        int speciesChecks = 0;
        int companionChecks = 0;
        int patrolChecks = 0;
        int sceneChecks = 0;
        int databaseChecks = 0;
        int worldEventChecks = 0;
        int campaignChecks = campaign.runSelfTest();
        int rosterChecks = npcRoster.runSelfTest();
        if (center != null) {
            LivingEntity zaochi = species.spawnZaochi(center.clone().add(0, 1, 0));
            LivingEntity xingtian = species.spawnXingtian(center.clone().add(3, 1, 0));
            java.util.UUID testSession = new java.util.UUID(0L, 2L);
            LivingEntity companion = companions.spawn(center.clone().add(6, 1, 0), owner, testSession);
            LivingEntity scout = companions.spawn(center.clone().add(7, 1, 0), owner, testSession, NpcRole.WUJI);
            if (species.type(zaochi) == SpeciesType.ZAOCHI) speciesChecks++;
            if (species.type(xingtian) == SpeciesType.XINGTIAN) speciesChecks++;
            if (companions.isCompanion(companion) && testSession.equals(companions.sessionId(companion))) {
                companionChecks++;
            }
            if (companions.role(scout) == NpcRole.WUJI && testSession.equals(companions.sessionId(scout))) {
                companionChecks++;
            }
            zaochi.remove();
            xingtian.remove();
            companions.remove(companion.getUniqueId());
            companions.remove(scout.getUniqueId());
            patrolChecks = encounters.runPersistenceSelfTest(center.clone().add(9, 1, 0));
            sceneChecks = encounters.runSceneSelfTest(center.clone().add(10, 1, 0));
            worldEventChecks = worldEvents.runPersistenceSelfTest(center.clone().add(12, 1, 0));
        }
        try {
            if (database.schemaVersion() == 4) {
                databaseChecks++;
            }
        } catch (java.sql.SQLException exception) {
            getLogger().log(java.util.logging.Level.SEVERE, "領域自檢無法讀取資料庫 schema", exception);
        }
        NamedTextColor color = weaponChecks == WeaponType.values().length
                && speciesChecks == SpeciesType.values().length && companionChecks == 2
                && patrolChecks == 12 && databaseChecks == 1 && worldEventChecks == 3 && campaignChecks == 8
                && rosterChecks == 3 && sceneChecks == 9
                ? NamedTextColor.GREEN : NamedTextColor.RED;
        sender.sendMessage(message("領域自檢：武器識別 " + weaponChecks + "/" + WeaponType.values().length
                + "，妖族生成識別 " + speciesChecks + "/" + SpeciesType.values().length
                + "，NPC 實體識別 " + companionChecks + "/2，任務資料欄位 " + patrolChecks + "/12"
                + "，任務場景實體 " + sceneChecks + "/9"
                + "，資料庫 schema " + databaseChecks + "/1，事件持久化流程 " + worldEventChecks + "/3"
                + "，長期內容規則 " + campaignChecks + "/8，NPC 輪值狀態 " + rosterChecks + "/3。", color));
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(message("此指令需要由遊戲內玩家執行。"));
        return null;
    }

    private List<String> filter(String prefix, List<String> values) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                matches.add(value);
            }
        }
        return matches;
    }
}
