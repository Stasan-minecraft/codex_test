package ua.credesasq.autocraft.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.credesasq.autocraft.client.AutoCraftManager;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void autocraft$tick(CallbackInfo ci) {
        AutoCraftManager.INSTANCE.tick((MinecraftClient) (Object) this);
    }
}
