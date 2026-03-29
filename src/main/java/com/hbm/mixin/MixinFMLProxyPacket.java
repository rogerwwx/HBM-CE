package com.hbm.mixin;

import com.hbm.core.FMLNetworkHook;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = FMLProxyPacket.class, remap = false)
public abstract class MixinFMLProxyPacket {
    @Inject(method = "toS3FPackets", at = @At("HEAD"), cancellable = true, remap = false)
    private void hbm$toS3FPackets(CallbackInfoReturnable<List<Packet<INetHandlerPlayClient>>> cir) {
        cir.setReturnValue(FMLNetworkHook.fmlProxyPacketToS3FPackets((FMLProxyPacket) (Object) this));
    }
}
