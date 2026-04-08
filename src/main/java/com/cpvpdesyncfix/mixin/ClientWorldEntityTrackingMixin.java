package com.cpvpdesyncfix.mixin;

import com.cpvpdesyncfix.CPVPDesyncFix;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class ClientWorldEntityTrackingMixin {
    @Inject(method = "tickEntities", at = @At("TAIL"))
    private void cpvp$onClientWorldEntityTick(CallbackInfo ci) {
        CPVPDesyncFix.onClientWorldEntityTick((ClientWorld) (Object) this);
    }
}