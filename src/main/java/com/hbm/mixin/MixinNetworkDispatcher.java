package com.hbm.mixin;

import com.hbm.core.FMLNetworkHook;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetworkDispatcher.class, remap = false)
public abstract class MixinNetworkDispatcher {
    @Inject(method = "write", at = @At("HEAD"), cancellable = true, remap = false)
    private void hbm$write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise, CallbackInfo ci) {
        FMLNetworkHook.networkDispatcherWrite((NetworkDispatcher) (Object) this, ctx, msg, promise);
        ci.cancel();
    }
}
