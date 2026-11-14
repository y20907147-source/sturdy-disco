package dev.portih.mixin;

import dev.portih.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererSwingMixin {

    @Shadow
    private float mainHandHeight;

    @Shadow
    private float oMainHandHeight;

    @Shadow
    private ItemStack mainHandItem;

    /** Меч не опускается после удара (пока перезаряжается кулдаун). */
    @Inject(method = "tick", at = @At("TAIL"))
    private void portih$noDip(CallbackInfo ci) {
        Config c = Config.get();
        if (!c.noDip) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        ItemStack held = player.getMainHandItem();
        if (!c.allItems && !held.is(ItemTags.SWORDS)) return;

        if (ItemStack.matches(this.mainHandItem, held)) {
            this.mainHandHeight = 1.0F;
            this.oMainHandHeight = 1.0F;
        }
    }

    /** Убирает ванильный наклон и толчок руки при взмахе. */
    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
    private void portih$noSwing(CallbackInfo ci) {
        Config c = Config.get();
        if (!c.noSwing) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        if (c.allItems || player.getMainHandItem().is(ItemTags.SWORDS)) {
            ci.cancel();
        }
    }
}
