package com.hbm.tileentity.network;

import com.hbm.handler.threading.PacketThreading;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.packet.toclient.BufPacket;
import com.hbm.tileentity.IBufPacketReceiver;
import com.hbm.util.BufferUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint;
import org.jetbrains.annotations.NotNull;

public class TileEntityRadioTorchBase extends TileEntity implements IBufPacketReceiver, ITickable, IControlReceiver {

	/** channel we're broadcasting on/listening to */
	public String channel = "";
	/** previous redstone state for input/output, needed for state change detection */
	public int lastState = 0;
	/** last update tick, needed for receivers listening for changes */
	public long lastUpdate;
	/** switches state change mode to tick-based polling */
	public boolean polling = false;
	/** switches redstone passthrough to custom signal mapping */
	public boolean customMap = false;
	/** custom mapping */
	public String[] mapping = new String[16];

	@Override
	public void update() {

		if(!world.isRemote) {
			networkPackNT(50);
		}
	}

	@Override
	public void readFromNBT(@NotNull NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.polling = nbt.getBoolean("isPolling");
		this.customMap = nbt.getBoolean("hasMapping");
		this.lastState = nbt.getInteger("lastPower");
		this.lastUpdate = nbt.getLong("lastTime");
		this.channel = nbt.getString("channel");
		for(int i = 0; i < 16; i++) {
			this.mapping[i] = nbt.getString("mapping" + i);
		}
	}

	@Override
	public @NotNull NBTTagCompound writeToNBT(NBTTagCompound nbt) {
		nbt.setBoolean("isPolling", polling);
		nbt.setBoolean("hasMapping", customMap);
		nbt.setInteger("lastPower", lastState);
		nbt.setLong("lastTime", lastUpdate);
		if(channel != null)
			nbt.setString("channel", channel);
		for(int i = 0; i < 16; i++) {
			if(mapping[i] != null) {
				nbt.setString("mapping" + i, mapping[i]);
			}
		}
		return super.writeToNBT(nbt);
	}

	public void networkPackNT(int range) {
		if (!world.isRemote)
			PacketThreading.createAllAroundThreadedPacket(new BufPacket(pos.getX(), pos.getY(), pos.getZ(), this), new TargetPoint(this.world.provider.getDimension(), pos.getX(), pos.getY(), pos.getZ(), range));
	}

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return new Vec3d(pos.getX() - player.posX, pos.getY() - player.posY, pos.getZ() - player.posZ).length() < 16;
	}

	@Override
	public void receiveControl(NBTTagCompound data) {
		if(data.hasKey("isPolling")) 
			this.polling = data.getBoolean("isPolling");
		if(data.hasKey("hasMapping")) 
			this.customMap = data.getBoolean("hasMapping");
		if(data.hasKey("channel")) 
			this.channel = data.getString("channel");
		for(int i = 0; i < 16; i++) {
			if(data.hasKey("mapping" + i)) {
				this.mapping[i] = data.getString("mapping" + i);
			}
		}
		
		this.markDirty();
	}

	@Override
	public void serialize(ByteBuf buf) {
		buf.writeBoolean(polling);
		buf.writeBoolean(customMap);
        buf.writeBoolean(channel != null);
		if(channel != null)
			BufferUtil.writeString(buf, channel);

		for(int i = 0; i < 16; i++) {
            buf.writeBoolean(mapping[i] != null);
            if (mapping[i] != null)
                BufferUtil.writeString(buf, mapping[i]);
        }
	}

	@Override
	public void deserialize(ByteBuf buf) {
		this.polling = buf.readBoolean();
		this.customMap = buf.readBoolean();

        if(buf.readBoolean()) {
            this.channel = BufferUtil.readString(buf);
        }

        for (int i = 0; i < 16; i++) {
            if (buf.readBoolean()) {
                this.mapping[i] = BufferUtil.readString(buf);
            }
        }
	}
}
