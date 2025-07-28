package net.camotoy.bedrockskinutility.client.mixin;

import net.camotoy.bedrockskinutility.client.interfaces.BedrockPlayerInfo;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRendererDispatcherMixin {

    @SuppressWarnings("unchecked")
    @Inject(
            method = "getRenderer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/AbstractClientPlayer;getSkin()Lnet/minecraft/client/resources/PlayerSkin;"),
            cancellable = true
    )
    public <T extends Entity> void getRenderer(Entity entity, CallbackInfoReturnable<EntityRenderer<? super T, ?>> cir) {
        PlayerInfo playerListEntry = ((BedrockAbstractClientPlayerEntity) entity).bedrockskinutility$getPlayerListEntry();
        if (playerListEntry != null) {
            PlayerRenderer renderer = ((BedrockPlayerInfo) playerListEntry).bedrockskinutility$getModel();
            if (renderer != null) {
                cir.setReturnValue((EntityRenderer<? super T, ?>) renderer);
            }
        }
    }
}
