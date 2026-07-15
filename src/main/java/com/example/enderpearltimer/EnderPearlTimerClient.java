package com.example.enderpearltimer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class EnderPearlTimerClient implements ClientModInitializer {

    public static final String MOD_ID = "enderpearltimer";

    // Y-Offset relativ zum unteren Bildschirmrand.
    // -22 = Hoehe der Hotbar, -29 ca. XP-Leiste, -39/-49 = Herzen/Hunger-Bereich.
    // Bei Bedarf anpassen, falls z.B. eine andere GUI-Skalierung genutzt wird.
    private static final int Y_OFFSET_FROM_BOTTOM = 49;

    private static KeyBinding toggleTimerKey;
    // Standardmaessig sichtbar; per Hotkey umschaltbar. Taste ist nicht
    // vorbelegt (GLFW_KEY_UNKNOWN) -> vom Spieler in Optionen > Steuerung
    // frei einstellbar, wie gewuenscht.
    private static boolean timerVisible = true;

    @Override
    public void onInitializeClient() {

        toggleTimerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.enderpearltimer.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                new KeyBinding.Category(Identifier.of(MOD_ID, "enderpearltimer"))
        ));

        // Erkennt jede Enderperle, die neu in die Welt geladen wird,
        // und prueft, ob der Besitzer der eigene Spieler ist.
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) {
                return;
            }
            if (entity instanceof EnderPearlEntity pearl && pearl.getOwner() == client.player) {
                PearlTracker.startTracking(pearl);
            }
        });

        // Wenn die getrackte Perle aus der Welt entfernt wird (= eingeschlagen),
        // Timer beenden.
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (PearlTracker.isTracking(entity)) {
                PearlTracker.stopTracking();
            }
        });

        // Jeden Client-Tick die Restzeit neu berechnen und Hotkey abfragen.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PearlTracker.tick(client);
            while (toggleTimerKey.wasPressed()) {
                timerVisible = !timerVisible;
            }
        });

        // HUD-Element registrieren. addLast() rendert nach allen anderen
        // HUD-Elementen, die Position wird unten manuell berechnet.
        HudElementRegistry.addLast(
                Identifier.of(MOD_ID, "pearl_timer"),
                EnderPearlTimerClient::renderTimer
        );
    }

    private static void renderTimer(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.options.hudHidden || !timerVisible) {
            return;
        }

        Text text;
        if (PearlTracker.isShowingBubbleColumnFlash()) {
            // Kurzer Hinweis, danach verschwindet der Timer komplett
            // (siehe PearlTracker.tick()).
            text = Text.literal("Blasensäule");
        } else {
            Float secondsRemaining = PearlTracker.getSecondsRemaining();
            if (secondsRemaining == null) {
                return;
            }
            float clamped = Math.max(0.0f, secondsRemaining);
            text = Text.literal(String.format("%.1fs", clamped));
        }

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        int textWidth = client.textRenderer.getWidth(text);
        int x = (screenWidth - textWidth) / 2;
        int y = screenHeight - Y_OFFSET_FROM_BOTTOM;

        context.drawTextWithShadow(client.textRenderer, text, x, y, 0xFFFFFF);
    }
}
