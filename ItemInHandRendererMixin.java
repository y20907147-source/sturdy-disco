package dev.portih.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.portih.Config;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    @Unique
    private boolean portih$pushed;

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void portih$head(CallbackInfo ci,
                             @Local(argsOnly = true) ItemStack stack,
                             @Local(argsOnly = true) ItemDisplayContext context,
                             @Local(argsOnly = true) PoseStack pose) {
        portih$pushed = false;
        Config c = Config.get();

        if (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                && context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) return;
        if (!c.allItems && !stack.is(ItemTags.SWORDS)) return;
        if (!c.smallSword && !c.spin) return;

        pose.pushPose();
        portih$pushed = true;

        if (c.smallSword) {
            float s = c.swordScale;
            pose.scale(s, s, s);
        }
        if (c.spin) {
            float angle = ((System.currentTimeMillis() % 3_600_000L) / 1000f) * c.spinSpeed * 360f % 360f;
            Axis axis = switch (c.spinAxis) {
                case 1 -> Axis.ZP;
                case 2 -> Axis.XP;
                default -> Axis.YP;
            };
            pose.mulPose(axis.rotationDegrees(angle));
        }
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void portih$tail(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose) {
        if (portih$pushed) {
            pose.popPose();
            portih$pushed = false;
        }
    }
}
