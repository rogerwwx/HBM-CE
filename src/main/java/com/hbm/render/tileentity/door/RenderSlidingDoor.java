package com.hbm.render.tileentity.door;

import java.nio.DoubleBuffer;

import com.hbm.interfaces.IDoor.DoorState;
import org.lwjgl.opengl.GL11;

import com.hbm.main.ResourceManager;
import com.hbm.tileentity.TileEntityDoorGeneric;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;

public class RenderSlidingDoor implements IRenderDoors {
	
	public static final RenderSlidingDoor INSTANCE = new RenderSlidingDoor();

	@Override
	public void render(TileEntityDoorGeneric door, DoubleBuffer buf) {
		
		Minecraft.getMinecraft().getTextureManager().bindTexture(ResourceManager.pheo_sliding_door_tex);
		
		double maxOpen = 0.95;
		double open = 0;
		if(door.state == DoorState.OPEN) open = maxOpen;
		
		if(door.currentAnimation != null) {
			open = IRenderDoors.getRelevantTransformation("DOOR", door.currentAnimation)[1] * maxOpen;
		}

		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glTranslated(0.53125, 0.001, 0.5);
		ResourceManager.pheo_sliding_door.renderPart("Frame");
		
		GL11.glPushMatrix();
		GL11.glTranslated(0, 0, MathHelper.clamp(open, 0, maxOpen));
		ResourceManager.pheo_sliding_door.renderPart("Left");
		GL11.glPopMatrix();
		
		GL11.glPushMatrix();
		GL11.glTranslated(0, 0, -MathHelper.clamp(open, 0, maxOpen));
		ResourceManager.pheo_sliding_door.renderPart("Right");
		GL11.glPopMatrix();
	}
}
