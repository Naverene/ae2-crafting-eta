package com.naverene.ae2craftingeta.client;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import com.naverene.ae2craftingeta.AE2CraftingEta;
import com.naverene.ae2craftingeta.data.TimingStats;
import com.naverene.ae2craftingeta.data.TimingTable;

import appeng.api.storage.data.IAEStack;

/**
 * Timings the client uses. When the server has this mod, they are the server's, synced on login. When it doesn't,
 * the client learns its own from what AE2 shows it and keeps them in .minecraft/ae2craftingeta, one file per server.
 */
public final class ClientTimings {

    private static volatile boolean serverHasMod;
    private static final TimingTable SERVER = new TimingTable();
    private static final TimingTable LOCAL = new TimingTable();
    private static File localFile;
    private static boolean localDirty;

    private ClientTimings() {}

    public static boolean serverHasMod() {
        return serverHasMod;
    }

    /** Server timings arrived, which also tells us the server runs this mod. */
    public static synchronized void onServerStats(Map<IAEStack<?>, TimingStats> stats, boolean replaceAll) {
        serverHasMod = true;
        SERVER.putAll(stats, replaceAll);
    }

    public static synchronized TimingStats find(IAEStack<?> stack) {
        return (serverHasMod ? SERVER : LOCAL).find(stack);
    }

    // Timings the client learns itself, only while the server lacks the mod. AE2 may deliver screen updates off the
    // render thread, so all access goes through these synchronized methods.

    public static synchronized void recordLatency(IAEStack<?> stack, long ticks) {
        if (!serverHasMod && LOCAL.recordLatency(stack, ticks) != null) {
            localDirty = true;
        }
    }

    public static synchronized void recordRate(IAEStack<?> stack, double itemsPerTick) {
        if (!serverHasMod && LOCAL.recordRate(stack, itemsPerTick) != null) {
            localDirty = true;
        }
    }

    public static synchronized void recordJob(IAEStack<?> stack, long amount, long millis) {
        if (!serverHasMod && LOCAL.recordJob(stack, amount, millis) != null) {
            localDirty = true;
        }
    }

    public static synchronized void onConnect() {
        serverHasMod = false;
        SERVER.putAll(Collections.emptyMap(), true);
        localFile = fileForCurrentServer();
        LOCAL.putAll(Collections.emptyMap(), true);
        if (localFile != null && localFile.isFile()) {
            try {
                NBTTagCompound tag = CompressedStreamTools.read(localFile);
                if (tag != null) {
                    LOCAL.readFromNBT(tag);
                }
            } catch (Exception e) {
                AE2CraftingEta.LOG.warn("Could not read learned crafting times from {}", localFile, e);
            }
        }
        localDirty = false;
    }

    public static synchronized void saveLocal() {
        if (!localDirty || localFile == null) {
            return;
        }
        try {
            File dir = localFile.getParentFile();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return;
            }
            NBTTagCompound tag = new NBTTagCompound();
            LOCAL.writeToNBT(tag);
            CompressedStreamTools.safeWrite(tag, localFile);
            localDirty = false;
        } catch (Exception e) {
            AE2CraftingEta.LOG.warn("Could not save learned crafting times to {}", localFile, e);
        }
    }

    private static File fileForCurrentServer() {
        Minecraft mc = Minecraft.getMinecraft();
        ServerData server = mc.func_147104_D();
        if (server == null || server.serverIP == null) {
            // Singleplayer: the built-in server runs the mod, so nothing is learned client-side.
            return null;
        }
        String name = server.serverIP.toLowerCase()
            .replaceAll("[^a-z0-9._-]", "_");
        return new File(new File(mc.mcDataDir, AE2CraftingEta.MODID), name + ".dat");
    }
}
