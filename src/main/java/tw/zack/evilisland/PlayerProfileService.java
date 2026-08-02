package tw.zack.evilisland;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import tw.zack.evilisland.model.Formula;
import tw.zack.evilisland.model.FormulaPath;
import tw.zack.evilisland.model.ObjectiveStage;
import tw.zack.evilisland.model.QiTendency;

public final class PlayerProfileService {
    private final NamespacedKey tendencyKey;
    private final NamespacedKey formulaKey;
    private final NamespacedKey secondaryFormulaKey;
    private final NamespacedKey formulaRatioKey;
    private final NamespacedKey qiKey;
    private final NamespacedKey essenceKey;
    private final NamespacedKey transformationsKey;
    private final NamespacedKey objectiveKey;
    private final NamespacedKey zaochiKillsKey;

    public PlayerProfileService(EvilIslandPlugin plugin) {
        tendencyKey = new NamespacedKey(plugin, "qi_tendency");
        formulaKey = new NamespacedKey(plugin, "formula");
        secondaryFormulaKey = new NamespacedKey(plugin, "formula_secondary");
        formulaRatioKey = new NamespacedKey(plugin, "formula_primary_percent");
        qiKey = new NamespacedKey(plugin, "qi");
        essenceKey = new NamespacedKey(plugin, "demon_essence");
        transformationsKey = new NamespacedKey(plugin, "transformations");
        objectiveKey = new NamespacedKey(plugin, "objective");
        zaochiKillsKey = new NamespacedKey(plugin, "zaochi_kills");
    }

    public boolean isEnlisted(Player player) {
        return isMeasured(player) && isFormulaLocked(player);
    }

    public boolean isMeasured(Player player) {
        return tendency(player) != null;
    }

    public boolean isFormulaLocked(Player player) {
        return formulaPath(player) != null;
    }

    public QiTendency measureTendency(Player player) {
        QiTendency existing = tendency(player);
        if (existing != null) {
            return existing;
        }
        long innateValue = player.getUniqueId().getMostSignificantBits()
                ^ Long.rotateLeft(player.getUniqueId().getLeastSignificantBits(), 17);
        QiTendency measured = (Long.bitCount(innateValue) & 1) == 0
                ? QiTendency.OUTWARD : QiTendency.INWARD;
        player.getPersistentDataContainer().set(tendencyKey, PersistentDataType.STRING, measured.id());
        setQi(player, maxQi(player));
        return measured;
    }

    public boolean lockFormula(Player player, FormulaPath path) {
        if (!isMeasured(player) || isFormulaLocked(player)) {
            return false;
        }
        PersistentDataContainer data = player.getPersistentDataContainer();
        data.set(formulaKey, PersistentDataType.STRING, path.primary().id());
        if (path.secondary() == null) {
            data.remove(secondaryFormulaKey);
            data.remove(formulaRatioKey);
        } else {
            data.set(secondaryFormulaKey, PersistentDataType.STRING, path.secondary().id());
            data.set(formulaRatioKey, PersistentDataType.INTEGER, path.primaryPercent());
        }
        data.set(essenceKey, PersistentDataType.INTEGER, 0);
        data.set(transformationsKey, PersistentDataType.INTEGER, 0);
        data.set(zaochiKillsKey, PersistentDataType.INTEGER, 0);
        setObjective(player, ObjectiveStage.REPORT_PATROL);
        setQi(player, maxQi(player));
        return true;
    }

    public void reset(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        data.remove(tendencyKey);
        data.remove(formulaKey);
        data.remove(secondaryFormulaKey);
        data.remove(formulaRatioKey);
        data.remove(qiKey);
        data.remove(essenceKey);
        data.remove(transformationsKey);
        data.remove(objectiveKey);
        data.remove(zaochiKillsKey);
    }

    public QiTendency tendency(Player player) {
        String value = player.getPersistentDataContainer().get(tendencyKey, PersistentDataType.STRING);
        return value == null ? null : QiTendency.parse(value);
    }

    public Formula formula(Player player) {
        FormulaPath path = formulaPath(player);
        return path == null ? null : path.dominant();
    }

    public FormulaPath formulaPath(Player player) {
        String value = player.getPersistentDataContainer().get(formulaKey, PersistentDataType.STRING);
        Formula primary = value == null ? null : Formula.parse(value);
        if (primary == null) {
            return null;
        }
        String secondaryValue = player.getPersistentDataContainer().get(secondaryFormulaKey, PersistentDataType.STRING);
        Formula secondary = secondaryValue == null ? null : Formula.parse(secondaryValue);
        if (secondary == null) {
            return FormulaPath.pure(primary);
        }
        Integer ratio = player.getPersistentDataContainer().get(formulaRatioKey, PersistentDataType.INTEGER);
        try {
            return FormulaPath.mixed(primary, secondary, ratio == null ? 50 : ratio);
        } catch (IllegalArgumentException ignored) {
            return FormulaPath.pure(primary);
        }
    }

    public int qi(Player player) {
        Integer value = player.getPersistentDataContainer().get(qiKey, PersistentDataType.INTEGER);
        return value == null ? maxQi(player) : Math.min(value, maxQi(player));
    }

    public void setQi(Player player, int value) {
        player.getPersistentDataContainer().set(qiKey, PersistentDataType.INTEGER, Math.max(0, Math.min(maxQi(player), value)));
    }

    public void addQi(Player player, int amount) {
        setQi(player, qi(player) + amount);
    }

    public boolean spendQi(Player player, int amount) {
        if (qi(player) < amount) {
            return false;
        }
        setQi(player, qi(player) - amount);
        return true;
    }

    public int maxQi(Player player) {
        QiTendency tendency = tendency(player);
        int base = tendency == QiTendency.OUTWARD ? 120 : 110;
        return base + transformations(player) * 20;
    }

    public int essence(Player player) {
        return integer(player, essenceKey);
    }

    public void addEssence(Player player, int amount) {
        player.getPersistentDataContainer().set(essenceKey, PersistentDataType.INTEGER, Math.max(0, essence(player) + amount));
    }

    public boolean spendEssence(Player player, int amount) {
        if (essence(player) < amount) {
            return false;
        }
        addEssence(player, -amount);
        return true;
    }

    public int transformations(Player player) {
        return integer(player, transformationsKey);
    }

    public void setTransformations(Player player, int value) {
        player.getPersistentDataContainer().set(transformationsKey, PersistentDataType.INTEGER, Math.max(0, value));
        setQi(player, maxQi(player));
    }

    public ObjectiveStage objective(Player player) {
        return ObjectiveStage.fromId(integer(player, objectiveKey));
    }

    public void setObjective(Player player, ObjectiveStage stage) {
        player.getPersistentDataContainer().set(objectiveKey, PersistentDataType.INTEGER, stage.id());
    }

    public int zaochiKills(Player player) {
        return integer(player, zaochiKillsKey);
    }

    public void recordZaochiKill(Player player) {
        player.getPersistentDataContainer().set(zaochiKillsKey, PersistentDataType.INTEGER, zaochiKills(player) + 1);
    }

    private int integer(Player player, NamespacedKey key) {
        Integer value = player.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return value == null ? 0 : value;
    }
}
