package com.cpvpdesyncfix.mixin;

import com.cpvpdesyncfix.CPVPDesyncFix;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class EntityInterpolationMixin {
    @ModifyVariable(
            method = "updateTrackedPositionAndAngles(DDDFFI)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private int cpvp$adjustInterpolationStepsV1(int interpolationSteps, double x, double y, double z, float yaw, float pitch) {
        return CPVPDesyncFix.adjustInterpolationSteps((Entity) (Object) this, interpolationSteps, x, y, z);
    }

    @ModifyVariable(
            method = "updateTrackedPositionAndAngles(DDDFFIZ)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private int cpvp$adjustInterpolationStepsV2(int interpolationSteps, double x, double y, double z, float yaw, float pitch, boolean onGround) {
        return CPVPDesyncFix.adjustInterpolationSteps((Entity) (Object) this, interpolationSteps, x, y, z);
    }
}