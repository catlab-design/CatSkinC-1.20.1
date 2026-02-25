package com.sammy.catskincRemake.mixin.client;

import com.sammy.catskincRemake.client.SkinManagerClient;
import com.sammy.catskincRemake.client.SkinOverrideStore;
import com.sammy.catskincRemake.client.SkinTextureFactory;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin121 {
    @Inject(
            method = "getSkinTextures()Lnet/minecraft/client/util/SkinTextures;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void catskincRemake$overrideSkinTextures(CallbackInfoReturnable<Object> cir) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        UUID uuid = self.getUuid();
        if (uuid == null) {
            return;
        }

        Identifier textureId = null;
        Boolean slim = null;

        SkinOverrideStore.Entry entry = SkinOverrideStore.get(uuid);
        if (entry != null) {
            textureId = entry.texture;
            slim = entry.slim;
        } else {
            textureId = SkinManagerClient.getCached(uuid);
            slim = SkinManagerClient.isSlimOrNull(uuid);
            if (textureId == null) {
                SkinManagerClient.ensureFetch(uuid);
                return;
            }
        }

        Object updated = SkinTextureFactory.withTextureAndModel(cir.getReturnValue(), textureId, slim);
        if (updated != null) {
            cir.setReturnValue(updated);
        }
    }
}

