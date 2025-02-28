package org.dynmap.fabric_1_16_4;

import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkManager;
import net.minecraft.world.chunk.ChunkStatus;
import org.dynmap.DynmapChunk;
import org.dynmap.Log;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericMapChunkCache;

import java.util.*;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class FabricMapChunkCache extends GenericMapChunkCache {
    private World w;
    private ServerChunkManager cps;

    /**
     * Construct empty cache
     */
    public FabricMapChunkCache(DynmapPlugin plugin) {
    	super(plugin.sscache);
    }

    public void setChunks(FabricWorld dw, List<DynmapChunk> chunks) {
        this.w = dw.getWorld();
        if (dw.isLoaded()) {
            /* Check if world's provider is ServerChunkManager */
            ChunkManager cp = this.w.getChunkManager();

            if (cp instanceof ServerChunkManager) {
                cps = (ServerChunkManager) cp;
            } else {
                Log.severe("Error: world " + dw.getName() + " has unsupported chunk provider");
            }
        } 
        super.setChunks(dw, chunks);
    }

    private CompoundTag readChunk(int x, int z) {
        try {
            ThreadedAnvilChunkStorage acl = cps.threadedAnvilChunkStorage;

            ChunkPos coord = new ChunkPos(x, z);
            CompoundTag rslt = acl.getNbt(coord);
            if (!isLitChunk(rslt)) {
                rslt = null;
            }
            return rslt;
        } catch (Exception exc) {
            Log.severe(String.format("Error reading chunk: %s,%d,%d", dw.getName(), x, z), exc);
            return null;
        }
    }

	private boolean isLitChunk(CompoundTag nbt) {
		if ((nbt != null) && nbt.contains("Level")) {
    		nbt = nbt.getCompound("Level");
    	}
        if (nbt != null) {
            String stat = nbt.getString("Status");
			ChunkStatus cs = ChunkStatus.byId(stat);
            if ((stat != null) && cs.isAtLeast(ChunkStatus.LIGHT)) {	// ChunkStatus.LIGHT
            	return true;
            }
        }
        return false;
	}

	// Load generic chunk from existing and already loaded chunk
	protected GenericChunk getLoadedChunk(DynmapChunk chunk) {
		GenericChunk gc = null;
        if (cps.isChunkLoaded(chunk.x, chunk.z)) {
            CompoundTag nbt = null;
            try {
                nbt = ChunkSerializer.serialize((ServerWorld) w, cps.getWorldChunk(chunk.x, chunk.z, false));
            } catch (NullPointerException e) {
                // TODO: find out why this is happening and why it only seems to happen since 1.16.2
                Log.severe("ChunkSerializer.serialize threw a NullPointerException", e);
            }
			if (isLitChunk(nbt)) {
				gc = parseChunkFromNBT(new NBT.NBTCompound(nbt));
			}
		}
		return gc;
	}
	
	// Load generic chunk from unloaded chunk
	protected GenericChunk loadChunk(DynmapChunk chunk) {
		GenericChunk gc = null;
        CompoundTag nbt = readChunk(chunk.x, chunk.z);
		// If read was good
		if (isLitChunk(nbt)) {
			gc = parseChunkFromNBT(new NBT.NBTCompound(nbt));
		}
		return gc;
	}
}

