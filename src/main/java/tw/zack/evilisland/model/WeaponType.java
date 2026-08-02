package tw.zack.evilisland.model;

import dev.zack.rpgengine.ContentDefinition;
import org.bukkit.Material;

import java.util.Locale;

public enum WeaponType implements ContentDefinition {
    SPEAR("spear", "制式長槍", "穿陣突刺", Material.TRIDENT),
    DUAL_BATONS("dual_batons", "撼山雙鐧", "雙鐧架勢", Material.IRON_PICKAXE),
    SABER("saber", "厚背戰刀", "破勢重斬", Material.IRON_AXE),
    SWORD("sword", "歲安長劍", "截勢反擊", Material.IRON_SWORD),
    DAGGERS("daggers", "斥候雙匕", "疾步切入", Material.SHEARS),
    SHIELD_BLADE("shield_blade", "城防刀盾", "沉身固守", Material.SHIELD);

    private final String id;
    private final String display;
    private final String technique;
    private final Material material;

    WeaponType(String id, String display, String technique, Material material) {
        this.id = id;
        this.display = display;
        this.technique = technique;
        this.material = material;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public String technique() {
        return technique;
    }

    public Material material() {
        return material;
    }

    public static WeaponType parse(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (WeaponType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
