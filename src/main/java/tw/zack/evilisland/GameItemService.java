package tw.zack.evilisland;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import tw.zack.evilisland.model.WeaponType;
import tw.zack.evilisland.model.SpeciesType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GameItemService {
    private static final String REMAINS = "remains";
    private static final String WEAPON = "weapon";
    private final NamespacedKey typeKey;
    private final NamespacedKey sourceKey;
    private final NamespacedKey purityKey;
    private final NamespacedKey weaponTypeKey;
    private final NamespacedKey ownerKey;

    public GameItemService(EvilIslandPlugin plugin) {
        typeKey = new NamespacedKey(plugin, "item_type");
        sourceKey = new NamespacedKey(plugin, "remains_source");
        purityKey = new NamespacedKey(plugin, "remains_purity");
        weaponTypeKey = new NamespacedKey(plugin, "weapon_type");
        ownerKey = new NamespacedKey(plugin, "weapon_owner");
    }

    public ItemStack createWeapon(WeaponType type, UUID owner) {
        ItemStack stack = new ItemStack(type.material());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(type.display(), NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("歲安軍團登記兵器", NamedTextColor.GRAY),
                Component.text("右鍵：" + type.technique(), NamedTextColor.YELLOW),
                Component.text("潛行右鍵：運用已定型炁訣", NamedTextColor.DARK_GRAY)
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, WEAPON);
        meta.getPersistentDataContainer().set(weaponTypeKey, PersistentDataType.STRING, type.id());
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
        stack.setItemMeta(meta);
        return stack;
    }

    public WeaponType weaponType(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!WEAPON.equals(meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING))) {
            return null;
        }
        return WeaponType.parse(meta.getPersistentDataContainer().get(weaponTypeKey, PersistentDataType.STRING));
    }

    public boolean isOwnedWeapon(ItemStack stack, UUID owner) {
        if (weaponType(stack) == null || !stack.hasItemMeta()) {
            return false;
        }
        String storedOwner = stack.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return owner.toString().equals(storedOwner);
    }

    public boolean hasOwnedWeapon(PlayerInventory inventory, UUID owner) {
        for (ItemStack stack : inventory.getContents()) {
            if (isOwnedWeapon(stack, owner)) {
                return true;
            }
        }
        return false;
    }

    public ItemStack createRemains(String source, int purity) {
        SpeciesType species = SpeciesType.parse(source);
        String display = species == SpeciesType.ZAOCHI ? "鑿齒遺骸"
                : species == SpeciesType.XINGTIAN ? "刑天遺骸"
                : (species == null ? "未知妖族" : species.display()) + "遺骸";
        ItemStack stack = new ItemStack(source.equals("xingtian") ? Material.LEATHER
                : species != null && species.elite() ? Material.PHANTOM_MEMBRANE : Material.RABBIT_HIDE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(display, NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("妖物死亡後留下的可煉化來源。", NamedTextColor.GRAY),
                Component.text("尚未成為妖質，需帶回新城煉化臺。", NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, REMAINS);
        meta.getPersistentDataContainer().set(sourceKey, PersistentDataType.STRING, source);
        meta.getPersistentDataContainer().set(purityKey, PersistentDataType.INTEGER, purity);
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isRemains(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        String type = stack.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return REMAINS.equals(type);
    }

    public int countRemains(PlayerInventory inventory) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (isRemains(stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    public int consumeAllRemains(PlayerInventory inventory) {
        int essence = 0;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (!isRemains(stack)) {
                continue;
            }
            Integer purity = stack.getItemMeta().getPersistentDataContainer().get(purityKey, PersistentDataType.INTEGER);
            essence += stack.getAmount() * (purity == null ? 1 : Math.max(1, purity));
            inventory.setItem(slot, null);
        }
        return essence;
    }

    public int removeAllRemains(PlayerInventory inventory) {
        int removed = 0;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (!isRemains(stack)) {
                continue;
            }
            removed += stack.getAmount();
            inventory.setItem(slot, null);
        }
        return removed;
    }

    public List<ItemStack> removeRemainsFromDrops(List<ItemStack> drops) {
        List<ItemStack> removed = new ArrayList<>();
        drops.removeIf(stack -> {
            if (isRemains(stack)) {
                removed.add(stack);
                return true;
            }
            return false;
        });
        return removed;
    }
}
