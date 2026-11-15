package dev.portih;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Автосвап: по кнопке меняет предмет в левой руке между двумя выбранными, плюс авто-тотем. */
public final class AutoSwap {
    public static final String[] NAMES = {"Тотем", "Золотое яблоко", "Зач. яблоко", "Щит", "Жемчуг"};

    private static long lastMs = 0L;

    private AutoSwap() {}

    private static Item item(int i) {
        return switch (Math.floorMod(i, NAMES.length)) {
            case 0 -> Items.TOTEM_OF_UNDYING;
            case 1 -> Items.GOLDEN_APPLE;
            case 2 -> Items.ENCHANTED_GOLDEN_APPLE;
            case 3 -> Items.SHIELD;
            default -> Items.ENDER_PEARL;
        };
    }

    /** Нажата кнопка автосвапа. */
    public static void press(Minecraft mc) {
        Config c = Config.get();
        if (!c.autoSwap || mc.player == null || mc.gameMode == null || mc.screen != null) return;
        long now = System.currentTimeMillis();
        if (now - lastMs < 120L) return;

        Item a = item(c.swapA);
        Item b = item(c.swapB);
        ItemStack off = mc.player.getOffhandItem();
        Item want = off.is(a) ? b : a;
        if (swapIn(mc, want)) lastMs = now;
    }

    /** Каждый тик: если левая рука пуста (тотем сработал), подставляем новый тотем. */
    public static void tick(Minecraft mc) {
        Config c = Config.get();
        if (!c.autoTotem || mc.player == null || mc.gameMode == null || mc.screen != null) return;
        if (!mc.player.getOffhandItem().isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastMs < 250L) return;
        if (swapIn(mc, Items.TOTEM_OF_UNDYING)) lastMs = now;
    }

    private static boolean swapIn(Minecraft mc, Item it) {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).is(it)) {
                int slot = i < 9 ? 36 + i : i;
                mc.gameMode.handleInventoryMouseClick(
                        mc.player.inventoryMenu.containerId, slot, 40, ClickType.SWAP, mc.player);
                return true;
            }
        }
        return false;
    }
}
