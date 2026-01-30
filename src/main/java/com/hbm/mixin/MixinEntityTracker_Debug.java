package com.hbm.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.apache.logging.log4j.LogManager;

@Mixin(EntityTrackerEntry.class)
public abstract class MixinEntityTracker_Debug {

    @Shadow @Final private Entity trackedEntity;
    @Shadow @Final private java.util.Set<EntityPlayerMP> trackingPlayers;

    @Inject(method = "sendDestroyEntityPacketToTrackedPlayers", at = @At("HEAD"))
    private void onSendDestroyPacket(CallbackInfo ci) {
        for (EntityPlayerMP player : trackingPlayers) {
            boolean wwj = trackedEntity.getEntityData().getBoolean("wwj");

            Exception e = new Exception("SEND_DESTROY STACK");

            LogManager.getLogger("UNTRACK_DEBUG")
                    .error(
                            "sendDestroyEntityPacketToTrackedPlayers called for player={} wwj={}",
                            player.getName(),
                            wwj,
                            e
                    );
        }
    }
}