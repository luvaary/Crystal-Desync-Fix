package com.cpvpdesyncfix.mixin;

import com.cpvpdesyncfix.CPVPDesyncFix;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientAttackMixin {
    @Inject(method = "doAttack", at = @At("HEAD"))
    private void cpvp$onAttackStart(CallbackInfoReturnable<Boolean> cir) {
        CPVPDesyncFix.onLocalAttack((MinecraftClient) (Object) this);
    }
}