package dev.portih;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class PortihClient implements ClientModInitializer {
    public static final String MOD_ID = "portihmod";

    public static KeyMapping openMenu;
    public static KeyMapping toggleHud;
    public static KeyMapping toggleSpin;
    public static KeyMapping autoSwap;

    @Override
    public void onInitializeClient() {
        Config.load();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));

        openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.portihmod.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_INSERT, category));
        toggleHud = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.portihmod.toggle_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        toggleSpin = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.portihmod.toggle_spin", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

        autoSwap = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.portihmod.autoswap", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, category));

        HudRenderCallback.EVENT.register(Hud::render);
        ClientReceiveMessageEvents.GAME.register(EventAlert::onMessage);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenu.consumeClick()) {
                if (client.screen == null) client.setScreen(new ConfigScreen(null));
            }
            while (toggleHud.consumeClick()) {
                Config.get().hudEnabled = !Config.get().hudEnabled;
                Config.save();
            }
            while (toggleSpin.consumeClick()) {
                Config.get().spin = !Config.get().spin;
                Config.save();
            }
            while (autoSwap.consumeClick()) AutoSwap.press(client);
            AutoSwap.tick(client);
            Locator.tick(client);
            HitTracker.update(client);
            VictorySound.tick(client);
        });
    }
}
