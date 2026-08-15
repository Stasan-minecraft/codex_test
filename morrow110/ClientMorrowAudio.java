package com.credesasq.morrow.client;

import com.credesasq.morrow.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side positional MORROW theme playback. */
public final class ClientMorrowAudio {
    private static final Map<UUID, ThemeLoop> THEMES = new ConcurrentHashMap<>();

    private ClientMorrowAudio() {
    }

    public static void setTheme(UUID ownerId, boolean playing) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ThemeLoop old = THEMES.remove(ownerId);
            if (old != null) old.requestStop();
            if (!playing || mc.level == null) return;

            ThemeLoop loop = new ThemeLoop(ownerId);
            THEMES.put(ownerId, loop);
            mc.getSoundManager().play(loop);
        });
    }

    public static boolean isThemePlaying(UUID ownerId) {
        return THEMES.containsKey(ownerId);
    }

    public static void stopAll() {
        THEMES.values().forEach(ThemeLoop::requestStop);
        THEMES.clear();
    }

    private static final class ThemeLoop extends AbstractTickableSoundInstance {
        private final UUID ownerId;

        private ThemeLoop(UUID ownerId) {
            super(ModSounds.MORROW_THEME.get(), SoundSource.RECORDS, RandomSource.create());
            this.ownerId = ownerId;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.72F;
            this.pitch = 1.0F;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            updatePosition();
        }

        private void requestStop() {
            stop();
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !THEMES.containsKey(ownerId)) {
                stop();
                return;
            }
            updatePosition();
        }

        private void updatePosition() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            Player player = mc.level.getPlayerByUUID(ownerId);
            if (player != null) {
                this.x = player.getX();
                this.y = player.getY() + 0.7D;
                this.z = player.getZ();
            } else if (mc.player != null) {
                this.x = mc.player.getX();
                this.y = mc.player.getY();
                this.z = mc.player.getZ();
            }
        }
    }
}
