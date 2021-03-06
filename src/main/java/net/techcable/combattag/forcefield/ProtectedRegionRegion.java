package net.techcable.combattag.forcefield;

import java.util.Collection;
import java.util.HashSet;

import com.sk89q.worldedit.BlockVector;
import net.techcable.combattag.concurrent.BlockPos;
import org.bukkit.World;

import com.sk89q.worldedit.BlockVector2D;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import net.techcable.combattag.forcefield.BorderFinder.Region;

import lombok.*;

@RequiredArgsConstructor
@Getter
public class ProtectedRegionRegion implements BorderFinder.Region {
    private final World world;
    private final ProtectedRegion region;
    
    @Override
    public boolean contains(BlockPos point) {
        return contains(point.getX(), point.getY(), point.getZ());
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return region.contains(x, y, z);
    }
    
    @Override
    public Collection<BlockPos> getPoints() {
        HashSet<BlockPos> result = new HashSet<>();
        for (BlockVector2D blockVector : region.getPoints()) {
            result.add(new BlockPos(blockVector.getBlockX(), region.getMinimumPoint().getBlockY(), blockVector.getBlockZ(), getWorld()));
        }
        return result;
    }

    @Override
    public BlockPos getMin() {
        BlockVector min = getRegion().getMinimumPoint();
        return new BlockPos(min.getBlockX(), min.getBlockY(), min.getBlockZ(), getWorld());
    }

    @Override
    public BlockPos getMax() {
        BlockVector max = getRegion().getMaximumPoint();
        return new BlockPos(max.getBlockX(), max.getBlockY(), max.getBlockZ(), getWorld());
    }

    @Override
    public boolean equals(Object otherObj) {
        if (otherObj == this) return true;
        if (otherObj == null) return false;
        if (!(otherObj instanceof ProtectedRegionRegion)) return false;
        ProtectedRegionRegion other = (ProtectedRegionRegion) otherObj;
        if (getRegion() == null) {
            if (other.getRegion() != null) return false;
        }
        return getRegion().equals(other.getRegion());
    }
    
    @Override
    public int hashCode() {
        if (getRegion() == null) return 0;
        return getRegion().hashCode();
    }
}