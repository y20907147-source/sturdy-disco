package dev.portih.mixin;

import dev.portih.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

    @Unique
    private boolean portih$zeroed;
    @Unique
    private float portih$savedAnim;
    @Unique
    private float portih$savedOAnim;

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

    /**
     * Главное исправление "меч идёт вперёд": ванильный толчок руки вперёд/вбок при ударе
     * считается прямо в renderArmWithItem из прогресса взмаха (attackAnim) и НЕ входит
     * в applyItemArmAttackTransform. Поэтому на время отрисовки рук обнуляем прогресс взмаха
     * (и восстанавливаем после), тогда и наклон, и толчок пропадают.
     */
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void portih$zeroSwing(CallbackInfo ci) {
        portih$zeroed = false;
        Config c = Config.get();
        if (!c.noSwing) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!c.allItems && !player.getMainHandItem().is(ItemTags.SWORDS)) return;

        portih$savedAnim = player.attackAnim;
        portih$savedOAnim = player.oAttackAnim;
        player.attackAnim = 0.0F;
        player.oAttackAnim = 0.0F;
        portih$zeroed = true;
    }

    @Inject(method = "renderHandsWithItems", at = @At("RETURN"))
    private void portih$restoreSwing(CallbackInfo ci) {
        if (!portih$zeroed) return;
        portih$zeroed = false;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        player.attackAnim = portih$savedAnim;
        player.oAttackAnim = portih$savedOAnim;
    }

    /** Запасной вариант: убирает ванильный наклон руки при взмахе. */
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
