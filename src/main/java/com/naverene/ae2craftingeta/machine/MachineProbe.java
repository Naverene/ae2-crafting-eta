package com.naverene.ae2craftingeta.machine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.naverene.ae2craftingeta.AE2CraftingEta;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.parts.automation.PartBaseExportBus;

/**
 * Looks at what a crafting medium feeds and reads its progress. That covers GregTech machines next to an ME Interface,
 * multiblocks reached through one of their input hatches, the machines behind a subnet the interface feeds, and buffer
 * inventories. GregTech is reached by reflection so the mod still loads without it.
 */
public final class MachineProbe {

    /** Enough to describe a subnet without a huge tooltip or a long scan. */
    private static final int MAX_RESULTS = 8;
    private static final int MAX_BUFFER_SLOTS = 256;

    private static Class<?> gtTile;
    private static Class<?> gtMetaTile;
    private static Class<?> gtHatch;
    private static Method getProgress;
    private static Method getMaxProgress;
    private static Method isActive;
    private static Method getMetaTileEntity;
    private static Method getMetaTileId;
    private static Method getBaseMetaTileEntity;
    private static Field hatchWatchers;
    private static Method wasShutdown;
    private static Method getLastShutDownReason;
    private static Method getDisplayString;

    static {
        try {
            gtTile = Class.forName("gregtech.api.interfaces.tileentity.IGregTechTileEntity");
            getProgress = gtTile.getMethod("getProgress");
            getMaxProgress = gtTile.getMethod("getMaxProgress");
            isActive = gtTile.getMethod("isActive");
            getMetaTileEntity = gtTile.getMethod("getMetaTileEntity");
            getMetaTileId = gtTile.getMethod("getMetaTileID");
            gtMetaTile = Class.forName("gregtech.api.interfaces.metatileentity.IMetaTileEntity");
            getBaseMetaTileEntity = gtMetaTile.getMethod("getBaseMetaTileEntity");
            gtHatch = Class.forName("gregtech.api.metatileentity.implementations.MTEHatch");
        } catch (ReflectiveOperationException | LinkageError e) {
            gtTile = null;
        }
        if (gtTile != null) {
            try {
                // The controllers a smart input hatch (crafting input hatches and buses among them) reports to.
                hatchWatchers = gtHatch.getDeclaredField("watchers");
                hatchWatchers.setAccessible(true);
            } catch (ReflectiveOperationException | LinkageError | SecurityException e) {
                hatchWatchers = null;
            }
            try {
                wasShutdown = gtTile.getMethod("wasShutdown");
                getLastShutDownReason = gtTile.getMethod("getLastShutDownReason");
                getDisplayString = Class.forName("gregtech.api.util.IMachineMessage")
                    .getMethod("getDisplayString");
            } catch (ReflectiveOperationException | LinkageError e) {
                // Older GregTech without shutdown reasons; progress still works.
                wasShutdown = null;
            }
        }
    }

    private MachineProbe() {}

    /** What the medium feeds, or an empty list if nothing readable is found. */
    public static List<MachineStatus> probe(ICraftingMedium medium) {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<MachineStatus> found = new ArrayList<>(1);
        if (medium instanceof DualityInterface duality && duality.getHost() instanceof IInterfaceHost host) {
            seen.add(host);
            // An interface facing a cable of its own network is not a subnet.
            IGrid home = gridOf(host);
            if (home != null) {
                seen.add(home);
            }
            lookAround(host.getTileEntity(), host.getTargets(), true, seen, found);
        } else if (gtMetaTile != null && gtMetaTile.isInstance(medium)) {
            // A GregTech hatch that takes patterns itself, such as the crafting input hatch.
            controllers(medium, seen, found);
        }
        return found;
    }

    private static void lookAround(TileEntity self, Set<ForgeDirection> sides, boolean followSubnets, Set<Object> seen,
        List<MachineStatus> found) {
        if (self == null || self.getWorldObj() == null) {
            return;
        }
        World world = self.getWorldObj();
        for (ForgeDirection side : sides) {
            if (found.size() >= MAX_RESULTS) {
                return;
            }
            int x = self.xCoord + side.offsetX;
            int y = self.yCoord + side.offsetY;
            int z = self.zCoord + side.offsetZ;
            if (world.blockExists(x, y, z)) {
                lookAt(world.getTileEntity(x, y, z), side, followSubnets, seen, found);
            }
        }
    }

    private static void lookAt(TileEntity tile, ForgeDirection side, boolean followSubnets, Set<Object> seen,
        List<MachineStatus> found) {
        if (tile == null || !seen.add(tile)) {
            return;
        }
        if (gtTile != null && gtTile.isInstance(tile)) {
            try {
                Object meta = getMetaTileEntity.invoke(tile);
                if (meta != null && gtHatch.isInstance(meta)) {
                    controllers(meta, seen, found);
                } else if (meta != null) {
                    add(found, read(tile));
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                AE2CraftingEta.LOG.debug("Could not read machine", e);
            }
            return;
        }
        IGrid subnet = subnetGrid(tile, side.getOpposite());
        if (subnet != null) {
            if (followSubnets && seen.add(subnet)) {
                lookIntoSubnet(subnet, seen, found);
            }
            return;
        }
        if (tile instanceof IInventory inventory && !(tile instanceof IGridHost)) {
            add(found, buffer(tile, inventory));
        }
    }

    /** The multiblock controllers a GregTech hatch belongs to, read like any machine. */
    private static void controllers(Object hatchMeta, Set<Object> seen, List<MachineStatus> found) {
        if (hatchWatchers == null || !gtHatch.isInstance(hatchMeta)) {
            return;
        }
        try {
            for (Object controller : new ArrayList<>((List<?>) hatchWatchers.get(hatchMeta))) {
                if (!gtMetaTile.isInstance(controller)) {
                    continue;
                }
                Object base = getBaseMetaTileEntity.invoke(controller);
                if (base instanceof TileEntity tile && seen.add(tile)) {
                    add(found, read(tile));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            AE2CraftingEta.LOG.debug("Could not find multiblock controller", e);
        }
    }

    private static IGrid gridOf(Object host) {
        IGridNode node = null;
        if (host instanceof IPart part) {
            node = part.getGridNode();
        } else if (host instanceof IGridHost gridHost) {
            node = gridHost.getGridNode(ForgeDirection.UNKNOWN);
        }
        return node == null ? null : node.getGrid();
    }

    /** The grid of the ME interface on the given face of this block, if it is one. */
    private static IGrid subnetGrid(TileEntity tile, ForgeDirection face) {
        if (tile instanceof IPartHost partHost) {
            IPart part = partHost.getPart(face);
            return part instanceof IInterfaceHost ? gridOf(part) : null;
        }
        return tile instanceof IInterfaceHost ? gridOf(tile) : null;
    }

    /** What the interfaces and export buses on a subnet feed. Subnets inside subnets are not followed. */
    private static void lookIntoSubnet(IGrid grid, Set<Object> seen, List<MachineStatus> found) {
        for (Class<? extends IGridHost> type : grid.getMachinesClasses()) {
            boolean isInterface = IInterfaceHost.class.isAssignableFrom(type);
            if (!isInterface && !PartBaseExportBus.class.isAssignableFrom(type)) {
                continue;
            }
            for (IGridNode node : grid.getMachines(type)) {
                if (found.size() >= MAX_RESULTS) {
                    return;
                }
                IGridHost machine = node.getMachine();
                if (!seen.add(machine)) {
                    continue;
                }
                if (machine instanceof IInterfaceHost host) {
                    lookAround(host.getTileEntity(), host.getTargets(), false, seen, found);
                } else if (machine instanceof PartBaseExportBus<?>bus && bus.getHost() != null) {
                    lookAround(
                        bus.getHost()
                            .getTile(),
                        EnumSet.of(bus.getSide()),
                        false,
                        seen,
                        found);
                }
            }
        }
    }

    private static void add(List<MachineStatus> found, MachineStatus status) {
        if (status != null && found.size() < MAX_RESULTS) {
            found.add(status);
        }
    }

    private static MachineStatus buffer(TileEntity tile, IInventory inventory) {
        int slots = Math.min(inventory.getSizeInventory(), MAX_BUFFER_SLOTS);
        if (slots <= 0) {
            return null;
        }
        int used = 0;
        for (int i = 0; i < slots; i++) {
            if (inventory.getStackInSlot(i) != null) {
                used++;
            }
        }
        ItemStack block = new ItemStack(tile.getBlockType(), 1, tile.getBlockMetadata());
        return MachineStatus.buffer(block, "buffer, " + used + " of " + slots + " slots used");
    }

    private static String shutdownReason(TileEntity tile) throws ReflectiveOperationException {
        if (wasShutdown == null || !(Boolean) wasShutdown.invoke(tile)) {
            return null;
        }
        Object reason = getLastShutDownReason.invoke(tile);
        Object text = reason == null ? null : getDisplayString.invoke(reason);
        return text == null || text.toString()
            .isEmpty() ? "shut down" : text.toString();
    }

    private static MachineStatus read(TileEntity tile) {
        try {
            int id = (Integer) getMetaTileId.invoke(tile);
            return new MachineStatus(
                new ItemStack(tile.getBlockType(), 1, id),
                (Integer) getProgress.invoke(tile),
                (Integer) getMaxProgress.invoke(tile),
                (Boolean) isActive.invoke(tile),
                shutdownReason(tile),
                null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            AE2CraftingEta.LOG.debug("Could not read machine progress", e);
            return null;
        }
    }
}
