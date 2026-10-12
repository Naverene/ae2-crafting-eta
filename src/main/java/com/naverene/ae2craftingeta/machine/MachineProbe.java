package com.naverene.ae2craftingeta.machine;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.naverene.ae2craftingeta.AE2CraftingEta;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;

/**
 * Looks at the GregTech machines an ME Interface feeds and reads their recipe progress. GregTech is reached by
 * reflection so the mod still loads without it.
 */
public final class MachineProbe {

    private static Class<?> gtTile;
    private static Class<?> gtHatch;
    private static Method getProgress;
    private static Method getMaxProgress;
    private static Method isActive;
    private static Method getMetaTileEntity;
    private static Method getMetaTileId;

    static {
        try {
            gtTile = Class.forName("gregtech.api.interfaces.tileentity.IGregTechTileEntity");
            getProgress = gtTile.getMethod("getProgress");
            getMaxProgress = gtTile.getMethod("getMaxProgress");
            isActive = gtTile.getMethod("isActive");
            getMetaTileEntity = gtTile.getMethod("getMetaTileEntity");
            getMetaTileId = gtTile.getMethod("getMetaTileID");
            gtHatch = Class.forName("gregtech.api.metatileentity.implementations.MTEHatch");
        } catch (ReflectiveOperationException | LinkageError e) {
            gtTile = null;
        }
    }

    private MachineProbe() {}

    /** The GregTech machines next to the interface behind this crafting medium. Empty if there are none. */
    public static List<MachineStatus> probe(ICraftingMedium medium) {
        if (gtTile == null || !(medium instanceof DualityInterface duality)
            || !(duality.getHost() instanceof IInterfaceHost host)) {
            return Collections.emptyList();
        }
        TileEntity self = host.getTileEntity();
        if (self == null || self.getWorldObj() == null) {
            return Collections.emptyList();
        }
        World world = self.getWorldObj();
        List<MachineStatus> found = new ArrayList<>(1);
        for (ForgeDirection side : host.getTargets()) {
            int x = self.xCoord + side.offsetX;
            int y = self.yCoord + side.offsetY;
            int z = self.zCoord + side.offsetZ;
            if (!world.blockExists(x, y, z)) {
                continue;
            }
            MachineStatus status = read(world.getTileEntity(x, y, z));
            if (status != null) {
                found.add(status);
            }
        }
        return found;
    }

    private static MachineStatus read(TileEntity tile) {
        if (!gtTile.isInstance(tile)) {
            return null;
        }
        try {
            Object meta = getMetaTileEntity.invoke(tile);
            // A multiblock's input bus never runs recipes itself, so it says nothing useful.
            if (meta == null || gtHatch.isInstance(meta)) {
                return null;
            }
            int id = (Integer) getMetaTileId.invoke(tile);
            return new MachineStatus(
                new ItemStack(tile.getBlockType(), 1, id),
                (Integer) getProgress.invoke(tile),
                (Integer) getMaxProgress.invoke(tile),
                (Boolean) isActive.invoke(tile));
        } catch (ReflectiveOperationException | RuntimeException e) {
            AE2CraftingEta.LOG.debug("Could not read machine progress", e);
            return null;
        }
    }
}
