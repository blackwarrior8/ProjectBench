package bau5.mods.projectbench.client;

import net.minecraft.world.World;
import bau5.mods.projectbench.common.CommonProxy;
import bau5.mods.projectbench.common.EntityCraftingFrame;
import bau5.mods.projectbench.common.EntityCraftingFrameII;
import bau5.mods.projectbench.common.tileentity.TEProjectBenchII;
import bau5.mods.projectbench.common.tileentity.TileEntityProjectBench;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;

/**
 * ClientProxy
 * 
 * @author _bau5
 * @license Lesser GNU Public License v3 (http://www.gnu.org/licenses/lgpl.html)
 * 
 */

public class ClientProxy extends CommonProxy {

	@Override
	public void registerRenderInformation() {
		ClientRegistry.bindTileEntitySpecialRenderer(
				TileEntityProjectBench.class, new TEProjectBenchRenderer());
		ClientRegistry.bindTileEntitySpecialRenderer(
				TEProjectBenchII.class, new TEProjectBenchIIRenderer());
		RenderingRegistry.registerEntityRenderingHandler(EntityCraftingFrame.class, new RenderCraftingFrame());
		RenderingRegistry.registerEntityRenderingHandler(EntityCraftingFrameII.class, new RenderCraftingFrameII());
	}

	@Override
	public World getClientSideWorld() {
		return FMLClientHandler.instance().getClient().theWorld;
	}
}