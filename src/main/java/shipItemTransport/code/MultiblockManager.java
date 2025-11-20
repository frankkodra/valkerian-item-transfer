package shipItemTransport.code;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.apigame.world.ShipWorldCore;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.joml.primitives.AABBic;

import java.util.*;
import java.util.stream.Collectors;

public class MultiblockManager {
    private final Map<String, MultiblockData> multiblocks = new HashMap<>();
    private final Map<BlockPos, String> blockToMultiblock = new HashMap<>();
    private final Map<String, Boolean> multiblockModes = new HashMap<>(); // true = import, false = export

    // Chest tracking
    private final Map<String, Set<BlockPos>> multiblockChests = new HashMap<>();
    private final Map<BlockPos, Set<String>> chestToMultiblocks = new HashMap<>();
    private final Map<BlockPos, Set<BlockPos>> chestGroups = new HashMap<>();
    private final Map<BlockPos, BlockPos> chestToPrimary = new HashMap<>();
    private static Map<Level, MultiblockManager> multiblockManagers = new WeakHashMap<Level, MultiblockManager>();
    private final Level level;

    // Transfer system
    private final Map<String, MultiblockTransferData> transferDataCache = new HashMap<>();
    private int transferTickCounter = 0;

    public MultiblockManager(Level level) {
        this.level = level;
    }

    public static MultiblockManager get(Level level) {
        if (multiblockManagers.containsKey(level)) {
            return multiblockManagers.get(level);
        }
        MultiblockManager manager = new MultiblockManager(level);
        multiblockManagers.put(level, manager);
        return manager;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            for (MultiblockManager manager : multiblockManagers.values()) {
                manager.tick();
            }
        }
    }

    public void tick() {
        if (level.isClientSide) return;

        transferTickCounter++;
        if (transferTickCounter >= 50) {
            transferTickCounter = 0;
            runItemTransfers();
        }
    }

    // Transfer data class
    private static class MultiblockTransferData {
        public final String multiblockId;
        public final Set<BlockPos> blocks;
        public final boolean isOnShip;
        public final Long shipId;
        public final Direction worldFacing;
        public final AABB extendedOBB;
        public final boolean isImportMode;
        public final int blockCount;
        public final Set<BlockPos> chests;
        public final Vec3 center;

        public MultiblockTransferData(String multiblockId, Set<BlockPos> blocks, boolean isOnShip,
                                      Long shipId, Direction worldFacing, AABB extendedOBB,
                                      boolean isImportMode, int blockCount, Set<BlockPos> chests, Vec3 center) {
            this.multiblockId = multiblockId;
            this.blocks = blocks;
            this.isOnShip = isOnShip;
            this.shipId = shipId;
            this.worldFacing = worldFacing;
            this.extendedOBB = extendedOBB;
            this.isImportMode = isImportMode;
            this.blockCount = blockCount;
            this.chests = chests;
            this.center = center;
        }
    }

    // Data class for persistent multiblock storage
    private static class MultiblockData {
        public final String id;
        public final Set<BlockPos> blocks;
        public final boolean isOnShip;
        public final Long shipId; // null if on ground

        public MultiblockData(String id, Set<BlockPos> blocks, boolean isOnShip, Long shipId) {
            this.id = id;
            this.blocks = blocks;
            this.isOnShip = isOnShip;
            this.shipId = shipId;
        }
    }

    // Transfer system implementation
    private void runItemTransfers() {
        Logger.sendMessage("=== Starting transfer cycle ===", true);

        // Pre-calculate transfer data for all multiblocks
        updateTransferDataCache();

        // Get all multiblocks for iteration
        List<MultiblockTransferData> allMultiblocks = new ArrayList<>(transferDataCache.values());
        Logger.sendMessage("Multiblocks in cache: " + allMultiblocks.size(), true);

        int pairsChecked = 0;
        int transfersExecuted = 0;

        // Check each pair
        for (int i = 0; i < allMultiblocks.size(); i++) {
            for (int j = i + 1; j < allMultiblocks.size(); j++) {
                pairsChecked++;
                MultiblockTransferData data1 = allMultiblocks.get(i);
                MultiblockTransferData data2 = allMultiblocks.get(j);

                if (shouldTransferBetween(data1, data2)) {
                    transfersExecuted++;
                    executeItemTransfer(data1, data2);
                }
            }
        }

        Logger.sendMessage(String.format("Pairs checked: %d, Transfers executed: %d", pairsChecked, transfersExecuted), true);
        Logger.sendMessage("=== Transfer cycle complete ===", true);
    }

    private void updateTransferDataCache() {
        transferDataCache.clear();

        for (Map.Entry<String, MultiblockData> entry : multiblocks.entrySet()) {
            String multiblockId = entry.getKey();
            MultiblockData data = entry.getValue();

            // Calculate world-facing direction
            Direction worldFacing = calculateWorldFacing(data);

            // Calculate extended OBB
            AABB extendedOBB = calculateExtendedOBB(data, worldFacing);

            // FIXED: Calculate center point in WORLD coordinates
            Vec3 center = calculateWorldCenter(data, extendedOBB);

            // Get other data
            boolean isImportMode = getMultiblockMode(multiblockId);
            int blockCount = data.blocks.size();
            Set<BlockPos> chests = multiblockChests.getOrDefault(multiblockId, new HashSet<>());

            MultiblockTransferData transferData = new MultiblockTransferData(
                    multiblockId, data.blocks, data.isOnShip, data.shipId,
                    worldFacing, extendedOBB, isImportMode, blockCount, chests, center
            );

            transferDataCache.put(multiblockId, transferData);

            Logger.sendMessage(String.format("Multiblock %s: blocks=%d, facing=%s, mode=%s, onShip=%s, chests=%d, center=%s",
                    multiblockId, blockCount, worldFacing, isImportMode ? "IMPORT" : "EXPORT",
                    data.isOnShip, chests.size(), center.toString()), true);
        }
    }

    private Vec3 calculateWorldCenter(MultiblockData data, AABB localOBB) {
        if (!data.isOnShip || data.shipId == null) {
            // Ground multiblock - center is just the midpoint of the local OBB
            return new Vec3(
                    localOBB.minX + (localOBB.maxX - localOBB.minX) / 2,
                    localOBB.minY + (localOBB.maxY - localOBB.minY) / 2,
                    localOBB.minZ + (localOBB.maxZ - localOBB.minZ) / 2
            );
        }

        // Ship multiblock - transform center from local to world coordinates
        Ship ship = findShipById(data.shipId);
        if (ship == null) {
            // Fallback to local center if ship not found
            return new Vec3(
                    localOBB.minX + (localOBB.maxX - localOBB.minX) / 2,
                    localOBB.minY + (localOBB.maxY - localOBB.minY) / 2,
                    localOBB.minZ + (localOBB.maxZ - localOBB.minZ) / 2
            );
        }

        // Calculate local center
        double centerX = localOBB.minX + (localOBB.maxX - localOBB.minX) / 2;
        double centerY = localOBB.minY + (localOBB.maxY - localOBB.minY) / 2;
        double centerZ = localOBB.minZ + (localOBB.maxZ - localOBB.minZ) / 2;

        // Transform to world coordinates
        Vector3d localCenter = new Vector3d(centerX, centerY, centerZ);
        Vector3d worldCenter = new Vector3d(localCenter);
        ship.getTransform().getShipToWorld().transformPosition(worldCenter);

        Logger.sendMessage(String.format("Ship center transform: local(%.2f, %.2f, %.2f) -> world(%.2f, %.2f, %.2f)",
                centerX, centerY, centerZ, worldCenter.x, worldCenter.y, worldCenter.z), true);

        return new Vec3(worldCenter.x, worldCenter.y, worldCenter.z);
    }



    private Direction calculateWorldFacing(MultiblockData data) {
        Logger.sendMessage("=== CALCULATING WORLD FACING ===", true);

        if (!data.isOnShip || data.shipId == null) {
            // Ground multiblock - use first block's facing
            BlockPos firstPos = data.blocks.iterator().next();
            BlockState state = level.getBlockState(firstPos);
            Direction facing = Direction.NORTH;
            if (state.hasProperty(ShipItemTransportBlock.FACING)) {
                facing = state.getValue(ShipItemTransportBlock.FACING);
            }
            Logger.sendMessage("Ground multiblock - Using local facing: " + facing, true);
            return facing;
        }

        // Ship multiblock - calculate world-facing from ship rotation
        Ship ship = findShipById(data.shipId);
        if (ship == null) {
            Logger.sendMessage("Ship not found for ID: " + data.shipId + " - using local facing", true);
            BlockPos firstPos = data.blocks.iterator().next();
            BlockState state = level.getBlockState(firstPos);
            Direction facing = Direction.NORTH;
            if (state.hasProperty(ShipItemTransportBlock.FACING)) {
                facing = state.getValue(ShipItemTransportBlock.FACING);
            }
            return facing;
        }

        // Get local facing from first block
        BlockPos firstPos = data.blocks.iterator().next();
        BlockState state = level.getBlockState(firstPos);
        Direction localFacing = Direction.NORTH;
        if (state.hasProperty(ShipItemTransportBlock.FACING)) {
            localFacing = state.getValue(ShipItemTransportBlock.FACING);
        }

        Logger.sendMessage("Ship multiblock - Local facing: " + localFacing, true);

        // Transform local facing to world space using ship transformation
        Vector3d localFacingVector = getDirectionVector(localFacing);
        Logger.sendMessage("Local facing vector: " +
                String.format("(%.2f, %.2f, %.2f)", localFacingVector.x, localFacingVector.y, localFacingVector.z), true);

        Vector3d worldFacingVector = transformLocalToWorld(ship, localFacingVector);
        Logger.sendMessage("World facing vector: " +
                String.format("(%.2f, %.2f, %.2f)", worldFacingVector.x, worldFacingVector.y, worldFacingVector.z), true);

        // Convert world vector back to closest Direction
        Direction worldFacing = getClosestDirection(worldFacingVector);

        Logger.sendMessage("Final world facing: " + worldFacing, true);

        // Debug: Show ship transform info
        try {
            var transform = ship.getTransform();
            Vector3d shipPos = (Vector3d) transform.getPositionInWorld();
            Logger.sendMessage("Ship position: " +
                    String.format("(%.2f, %.2f, %.2f)", shipPos.x, shipPos.y, shipPos.z), true);

            // Test transformation of basic vectors
            Vector3d testNorth = new Vector3d(0, 0, -1);
            Vector3d testNorthWorld = new Vector3d(testNorth);
            transform.getShipToWorld().transformDirection(testNorthWorld);
            Logger.sendMessage("Test NORTH in world: " +
                    String.format("(%.2f, %.2f, %.2f) -> %s",
                            testNorth.x, testNorth.y, testNorth.z,
                            getClosestDirection(testNorthWorld)), true);

            Vector3d testEast = new Vector3d(1, 0, 0);
            Vector3d testEastWorld = new Vector3d(testEast);
            transform.getShipToWorld().transformDirection(testEastWorld);
            Logger.sendMessage("Test EAST in world: " +
                    String.format("(%.2f, %.2f, %.2f) -> %s",
                            testEast.x, testEast.y, testEast.z,
                            getClosestDirection(testEastWorld)), true);

        } catch (Exception e) {
            Logger.sendMessage("Error getting ship transform: " + e.getMessage(), true);
        }

        Logger.sendMessage("=== END WORLD FACING CALCULATION ===", true);

        return worldFacing;
    }

    private Vector3d getDirectionVector(Direction direction) {
        return new Vector3d(
                direction.getStepX(),
                direction.getStepY(),
                direction.getStepZ()
        );
    }

    private Vector3d transformLocalToWorld(Ship ship, Vector3d localVector) {
        Vector3d worldVector = new Vector3d(localVector);
        ship.getTransform().getShipToWorld().transformDirection(worldVector);
        return worldVector.normalize();
    }

    private Direction getClosestDirection(Vector3d vector) {
        // Find the cardinal direction that most closely matches the vector
        double maxDot = -Double.MAX_VALUE;
        Direction closestDirection = Direction.NORTH;

        for (Direction dir : Direction.values()) {
            Vector3d dirVector = getDirectionVector(dir);
            double dot = vector.dot(dirVector);

            if (dot > maxDot) {
                maxDot = dot;
                closestDirection = dir;
            }
        }

        return closestDirection;
    }

    private AABB calculateExtendedOBB(MultiblockData data, Direction worldFacing) {
        Logger.sendMessage("=== CALCULATING EXTENDED OBB ===", true);
        Logger.sendMessage("World facing for OBB: " + worldFacing, true);

        // Calculate base AABB from all blocks in LOCAL coordinates
        AABB baseAABB = null;
        for (BlockPos pos : data.blocks) {
            AABB blockAABB;

            if (data.isOnShip && data.shipId != null) {
                // For ship multiblocks, use the block position directly (it's already in ship-local coordinates)
                blockAABB = new AABB(pos);
            } else {
                // For ground multiblocks, use world coordinates
                blockAABB = new AABB(pos);
            }

            if (baseAABB == null) {
                baseAABB = blockAABB;
            } else {
                baseAABB = baseAABB.minmax(blockAABB);
            }
        }

        if (baseAABB == null) {
            Logger.sendMessage("No blocks found for OBB calculation", true);
            return new AABB(0, 0, 0, 0, 0, 0);
        }

        Logger.sendMessage("Base OBB: " + baseAABB.toString(), true);

        // Extend by 1 block in the facing direction
        AABB extendedOBB = baseAABB.expandTowards(
                worldFacing.getStepX(),
                worldFacing.getStepY(),
                worldFacing.getStepZ()
        );

        Logger.sendMessage("Extended OBB: " + extendedOBB.toString(), true);
        Logger.sendMessage("=== END OBB CALCULATION ===", true);

        return extendedOBB;
    }

    private Ship findShipById(Long shipId) {
        if (level.isClientSide) return null;
        ShipWorldCore shipWorld = VSGameUtilsKt.getShipObjectWorld(level);
        if (shipWorld == null) return null;
        return shipWorld.getAllShips().getById(shipId);
    }

    private boolean shouldTransferBetween(MultiblockTransferData data1, MultiblockTransferData data2) {
        Logger.sendMessage("=== TRANSFER CHECK BETWEEN " + data1.multiblockId + " AND " + data2.multiblockId + " ===", true);

        // Debug: Show facing information
        Logger.sendMessage("Multiblock 1 - OnShip: " + data1.isOnShip + ", Facing: " + data1.worldFacing + ", Mode: " + (data1.isImportMode ? "IMPORT" : "EXPORT"), true);
        Logger.sendMessage("Multiblock 2 - OnShip: " + data2.isOnShip + ", Facing: " + data2.worldFacing + ", Mode: " + (data2.isImportMode ? "IMPORT" : "EXPORT"), true);

        // 1. Quick distance check
        if (!areMultiblocksClose(data1, data2)) {
            Logger.sendMessage("  ❌ Failed: Too far apart", true);
            return false;
        }
        Logger.sendMessage("  ✅ Passed: Distance check", true);

        // 2. Ship/ground filter
        if (!shouldTransferBasedOnLocation(data1, data2)) {
            Logger.sendMessage("  ❌ Failed: Location filter", true);
            return false;
        }
        Logger.sendMessage("  ✅ Passed: Location filter", true);

        // 3. Mode compatibility
        if (!shouldTransferBasedOnMode(data1, data2)) {
            Logger.sendMessage("  ❌ Failed: Mode compatibility", true);
            return false;
        }
        Logger.sendMessage("  ✅ Passed: Mode compatibility", true);

        // 4. OBB intersection - WITH DETAILED DEBUGGING
        Logger.sendMessage("=== OBB INTERSECTION CHECK ===", true);
        Logger.sendMessage("OBB1 (Ship): " + data1.extendedOBB.toString(), true);
        Logger.sendMessage("OBB2 (Ground): " + data2.extendedOBB.toString(), true);

        // Check if we need to transform ship OBB to world space
        AABB obb1World = data1.extendedOBB;
        AABB obb2World = data2.extendedOBB;

        if (data1.isOnShip && data1.shipId != null) {
            // Transform ship OBB to world coordinates
            obb1World = transformOBBToWorld(data1);
            Logger.sendMessage("Transformed OBB1 to world: " + obb1World.toString(), true);
        }
        if (data2.isOnShip && data2.shipId != null) {
            // Transform ship OBB to world coordinates
            obb2World = transformOBBToWorld(data2);
            Logger.sendMessage("Transformed OBB2 to world: " + obb1World.toString(), true);
        }
        Logger.sendMessage("=== OBB INTERSECTION CHECK after the transform to world ===", true);
        Logger.sendMessage("OBB1 (Ship): " + obb1World.toString(), true);
        Logger.sendMessage("OBB2 (Ground): " + obb2World.toString(), true);

        boolean intersects = obb1World.intersects(obb2World);
        Logger.sendMessage("OBB Intersection result: " + intersects, true);

        // Manual intersection check for debugging
        manualOBBIntersectionCheck(obb1World, obb2World);

        if (!intersects) {
            Logger.sendMessage("  ❌ Failed: OBB intersection", true);
            return false;
        }
        Logger.sendMessage("  ✅ Passed: OBB intersection", true);

        // 5. Facing direction compatibility
        if (!areFacingDirectionsCompatible(data1.worldFacing, data2.worldFacing)) {
            Logger.sendMessage("  ❌ Failed: Facing directions " + data1.worldFacing + " vs " + data2.worldFacing, true);
            return false;
        }
        Logger.sendMessage("  ✅ Passed: Facing directions " + data1.worldFacing + " vs " + data2.worldFacing, true);

        Logger.sendMessage("  ✅ ALL CHECKS PASSED - Transfer approved!", true);
        Logger.sendMessage("=== END TRANSFER CHECK ===", true);
        return true;
    }

    private AABB transformOBBToWorld(MultiblockTransferData shipData) {

        if (!shipData.isOnShip || shipData.shipId == null) {
            return shipData.extendedOBB;
        }

        Ship ship = findShipById(shipData.shipId);
        if (ship == null) {
            return shipData.extendedOBB;
        }

        // Transform all corners of the OBB from local to world coordinates
        Vector3d min = new Vector3d(shipData.extendedOBB.minX, shipData.extendedOBB.minY, shipData.extendedOBB.minZ);
        Vector3d max = new Vector3d(shipData.extendedOBB.maxX, shipData.extendedOBB.maxY, shipData.extendedOBB.maxZ);

        ship.getTransform().getShipToWorld().transformPosition(min);
        ship.getTransform().getShipToWorld().transformPosition(max);

        // Create new AABB with transformed coordinates
        return new AABB(
                Math.min(min.x, max.x), Math.min(min.y, max.y), Math.min(min.z, max.z),
                Math.max(min.x, max.x), Math.max(min.y, max.y), Math.max(min.z, max.z)
        );
    }


    private void manualOBBIntersectionCheck(AABB obb1, AABB obb2) {
        Logger.sendMessage("=== MANUAL OBB INTERSECTION CHECK ===", true);
        Logger.sendMessage("OBB1 bounds - X: " + obb1.minX + " to " + obb1.maxX +
                ", Y: " + obb1.minY + " to " + obb1.maxY +
                ", Z: " + obb1.minZ + " to " + obb1.maxZ, true);
        Logger.sendMessage("OBB2 bounds - X: " + obb2.minX + " to " + obb2.maxX +
                ", Y: " + obb2.minY + " to " + obb2.maxY +
                ", Z: " + obb2.minZ + " to " + obb2.maxZ, true);

        // Check each axis for separation
        boolean xOverlap = obb1.maxX >= obb2.minX && obb1.minX <= obb2.maxX;
        boolean yOverlap = obb1.maxY >= obb2.minY && obb1.minY <= obb2.maxY;
        boolean zOverlap = obb1.maxZ >= obb2.minZ && obb1.minZ <= obb2.maxZ;

        Logger.sendMessage("X-axis overlap: " + xOverlap, true);
        Logger.sendMessage("Y-axis overlap: " + yOverlap, true);
        Logger.sendMessage("Z-axis overlap: " + zOverlap, true);
        Logger.sendMessage("All axes overlap: " + (xOverlap && yOverlap && zOverlap), true);

        if (!xOverlap) {
            double gap = Math.max(obb2.minX - obb1.maxX, obb1.minX - obb2.maxX);
            Logger.sendMessage("X-axis gap: " + gap, true);
        }
        if (!yOverlap) {
            double gap = Math.max(obb2.minY - obb1.maxY, obb1.minY - obb2.maxY);
            Logger.sendMessage("Y-axis gap: " + gap, true);
        }
        if (!zOverlap) {
            double gap = Math.max(obb2.minZ - obb1.maxZ, obb1.minZ - obb2.maxZ);
            Logger.sendMessage("Z-axis gap: " + gap, true);
        }
        Logger.sendMessage("=== END MANUAL CHECK ===", true);
    }

    private boolean areMultiblocksClose(MultiblockTransferData data1, MultiblockTransferData data2) {
        double distance = data1.center.distanceTo(data2.center);
        double maxReasonableDistance = 50.0;
        boolean result = distance <= maxReasonableDistance;
        Logger.sendMessage(String.format("  Distance: %.1f, Max: %.1f, Result: %s", distance, maxReasonableDistance, result ? "CLOSE" : "FAR"), true);
        return result;
    }

    private boolean shouldTransferBasedOnLocation(MultiblockTransferData data1, MultiblockTransferData data2) {
        // Both on ground - no transfer
        if (!data1.isOnShip && !data2.isOnShip) {
            Logger.sendMessage("  Both on ground - NO TRANSFER", true);
            return false;
        }

        // Both on same ship - no transfer
        if (data1.isOnShip && data2.isOnShip &&
                data1.shipId != null && data1.shipId.equals(data2.shipId)) {
            Logger.sendMessage("  Both on same ship - NO TRANSFER", true);
            return false;
        }

        Logger.sendMessage("  Valid location combination", true);
        return true;
    }

    private boolean shouldTransferBasedOnMode(MultiblockTransferData data1, MultiblockTransferData data2) {
        // One must be import and the other export
        boolean result = data1.isImportMode != data2.isImportMode;
        Logger.sendMessage(String.format("  Modes: %s vs %s, Result: %s",
                data1.isImportMode ? "IMPORT" : "EXPORT",
                data2.isImportMode ? "IMPORT" : "EXPORT",
                result ? "COMPATIBLE" : "INCOMPATIBLE"), true);
        return result;
    }

    private boolean areFacingDirectionsCompatible(Direction facing1, Direction facing2) {
        // Must be opposite directions
        boolean result = facing1 == facing2.getOpposite();
        Logger.sendMessage(String.format("  Facings: %s vs %s, Result: %s",
                facing1, facing2, result ? "OPPOSITE" : "NOT OPPOSITE"), true);
        return result;
    }

    private void executeItemTransfer(MultiblockTransferData data1, MultiblockTransferData data2) {
        // Determine which is exporter and which is importer
        MultiblockTransferData exporter = data1.isImportMode ? data2 : data1;
        MultiblockTransferData importer = data1.isImportMode ? data1 : data2;

        // Calculate alignment percentage
        float alignmentPercent = calculateAlignmentPercent(exporter, importer);

        // Calculate transfer amount
        int transferAmount = calculateTransferAmount(exporter, importer, alignmentPercent);

        Logger.sendMessage(String.format("TRANSFER: %s -> %s, Alignment: %.1f%%, Amount: %d",
                exporter.multiblockId, importer.multiblockId, alignmentPercent, transferAmount), true);

        if (transferAmount > 0) {
            performItemTransfer(exporter, importer, transferAmount);
        }
    }

    private float calculateAlignmentPercent(MultiblockTransferData exporter, MultiblockTransferData importer) {
        // Get proper OBB coordinates - transform ship multiblocks to world coordinates
        AABB exporterAABB = getWorldOBB(exporter);
        AABB importerAABB = getWorldOBB(importer);

        Direction facing = exporter.worldFacing;

        float alignment;

        if (facing.getAxis().isHorizontal()) {
            // Horizontal transfer - check Y alignment
            double overlapY = Math.min(exporterAABB.maxY, importerAABB.maxY) -
                    Math.max(exporterAABB.minY, importerAABB.minY);
            double exporterHeight = exporterAABB.getYsize();
            double importerHeight = importerAABB.getYsize();
            double minHeight = Math.min(exporterHeight, importerHeight);

            if (minHeight <= 0 || overlapY <= 0) {
                alignment = 0;
            } else {
                alignment = (float) Math.max(0, Math.min(100, (overlapY / minHeight) * 100.0));
            }

            Logger.sendMessage(String.format("  Horizontal alignment: overlapY=%.3f, minHeight=%.3f, alignment=%.1f%%",
                    overlapY, minHeight, alignment), true);
        } else {
            // Vertical transfer - check XZ alignment
            double overlapX = Math.min(exporterAABB.maxX, importerAABB.maxX) -
                    Math.max(exporterAABB.minX, importerAABB.minX);
            double overlapZ = Math.min(exporterAABB.maxZ, importerAABB.maxZ) -
                    Math.max(exporterAABB.minZ, importerAABB.minZ);

            // Only proceed if there's positive overlap
            if (overlapX <= 0 || overlapZ <= 0) {
                alignment = 0;
                Logger.sendMessage(String.format("  Vertical alignment: NO OVERLAP (overlapX=%.3f, overlapZ=%.3f)",
                        overlapX, overlapZ), true);
            } else {
                double exporterArea = exporterAABB.getXsize() * exporterAABB.getZsize();
                double importerArea = importerAABB.getXsize() * importerAABB.getZsize();
                double minArea = Math.min(exporterArea, importerArea);

                if (minArea <= 0) {
                    alignment = 0;
                } else {
                    double overlapArea = overlapX * overlapZ;
                    alignment = (float) Math.max(0, Math.min(100, (overlapArea / minArea) * 100.0));
                }

                Logger.sendMessage(String.format("  Vertical alignment: overlapX=%.3f, overlapZ=%.3f, overlapArea=%.3f, minArea=%.3f, alignment=%.1f%%",
                        overlapX, overlapZ, overlapX * overlapZ, minArea, alignment), true);
            }
        }

        return alignment;
    }

    // Helper method to get OBB in world coordinates
    private AABB getWorldOBB(MultiblockTransferData data) {
        if (data.isOnShip && data.shipId != null) {
            // Transform ship OBB to world coordinates
            return transformOBBToWorld(data);
        } else {
            // Ground multiblock - use OBB as-is (already in world coordinates)
            return data.extendedOBB;
        }
    }

    private int calculateTransferAmount(MultiblockTransferData exporter, MultiblockTransferData importer, float alignmentPercent) {
        // Use the configured transfer rate
        int baseTransferRate = Config.ITEM_TRANSFER_RATE;

        // Apply multiblock size scaling (each block adds to capacity)
        int blockBonus = Math.min(exporter.blockCount, importer.blockCount) * baseTransferRate;

        // Total transfer per cycle


        // Apply alignment efficiency
        int actualTransfer = (int) (blockBonus * (alignmentPercent / 100.0));

        // Minimum transfer if there's any alignment
        if (alignmentPercent > 0 && actualTransfer < baseTransferRate) {
            actualTransfer = baseTransferRate;
        }

        // Maximum cap to prevent huge transfers
        int maxTransfer = baseTransferRate * 10; // 10x base rate as maximum
        actualTransfer = Math.min(actualTransfer, maxTransfer);

        Logger.sendMessage(String.format("  Transfer calc: baseRate=%d, blockBonus=%d, aligned=%d",
                baseTransferRate, blockBonus, actualTransfer), true);

        return actualTransfer;
    }
    private void performItemTransfer(MultiblockTransferData exporter, MultiblockTransferData importer, int transferAmount) {
        if (exporter.chests.isEmpty() || importer.chests.isEmpty()) {
            Logger.sendMessage("  ❌ No chests available for transfer", true);
            return;
        }

        // Get all item handlers
        List<IItemHandler> exporterHandlers = getItemHandlers(exporter.chests);
        List<IItemHandler> importerHandlers = getItemHandlers(importer.chests);

        if (exporterHandlers.isEmpty() || importerHandlers.isEmpty()) {
            Logger.sendMessage("  ❌ No item handlers available", true);
            return;
        }

        Logger.sendMessage(String.format("  Chests: exporter=%d, importer=%d",
                exporterHandlers.size(), importerHandlers.size()), true);

        // Simple item transfer implementation
        int remainingTransfer = transferAmount;
        int totalTransferred = 0;

        for (IItemHandler exporterHandler : exporterHandlers) {
            if (remainingTransfer <= 0) break;

            for (int slot = 0; slot < exporterHandler.getSlots(); slot++) {
                if (remainingTransfer <= 0) break;

                ItemStack extracted = exporterHandler.extractItem(slot, remainingTransfer, true);
                if (!extracted.isEmpty()) {
                    // Try to insert into any importer handler
                    ItemStack remaining = extracted.copy();
                    for (IItemHandler importerHandler : importerHandlers) {
                        if (remaining.isEmpty()) break;

                        for (int importerSlot = 0; importerSlot < importerHandler.getSlots(); importerSlot++) {
                            remaining = importerHandler.insertItem(importerSlot, remaining, false);
                            if (remaining.isEmpty()) break;
                        }
                    }

                    int actuallyTransferred = extracted.getCount() - remaining.getCount();
                    if (actuallyTransferred > 0) {
                        // Actually extract the items
                        exporterHandler.extractItem(slot, actuallyTransferred, false);
                        remainingTransfer -= actuallyTransferred;
                        totalTransferred += actuallyTransferred;
                        Logger.sendMessage(String.format("  Transferred %d items", actuallyTransferred), true);
                    }
                }
            }
        }

        Logger.sendMessage(String.format("  ✅ Transfer complete: %d/%d items moved", totalTransferred, transferAmount), true);
    }

    private List<IItemHandler> getItemHandlers(Set<BlockPos> chestPositions) {
        List<IItemHandler> handlers = new ArrayList<>();

        for (BlockPos pos : chestPositions) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                IItemHandler handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
                if (handler != null) {
                    handlers.add(handler);
                }
            }
        }

        return handlers;
    }

    // Existing multiblock management methods
    public String recreateMultiblock(Set<BlockPos> blocks, boolean importMode, String multiblockId) {
        if (level.isClientSide) return null;

        BlockPos firstBlock = blocks.iterator().next();
        boolean isOnShip = isBlockOnShip(firstBlock);
        Long shipId = isOnShip ? getShipIdForBlock(firstBlock) : null;

        String id = multiblockId;
        multiblocks.put(id, new MultiblockData(id, new HashSet<>(blocks), isOnShip, shipId));
        multiblockModes.put(id, importMode);
        multiblockChests.put(id, new HashSet<>());

        for (BlockPos pos : blocks) {
            blockToMultiblock.put(pos, id);
        }

        Logger.sendMessage("Created new multiblock " + id + " with " + blocks.size() +
                " blocks (on ship: " + isOnShip + ", shipId: " + shipId + ")", false);
        return id;
    }

    public String createMultiblock(Set<BlockPos> blocks, boolean importMode) {
        if (level.isClientSide) return null;

        BlockPos firstBlock = blocks.iterator().next();
        boolean isOnShip = isBlockOnShip(firstBlock);
        Long shipId = isOnShip ? getShipIdForBlock(firstBlock) : null;

        String id = UUID.randomUUID().toString();
        multiblocks.put(id, new MultiblockData(id, new HashSet<>(blocks), isOnShip, shipId));
        multiblockModes.put(id, importMode);
        multiblockChests.put(id, new HashSet<>());

        for (BlockPos pos : blocks) {
            blockToMultiblock.put(pos, id);
        }

        Logger.sendMessage("Created new multiblock " + id + " with " + blocks.size() +
                " blocks (on ship: " + isOnShip + ", shipId: " + shipId + ")", false);
        return id;
    }

    public void removeMultiblock(String id) {
        if (level.isClientSide) return;
        MultiblockData data = multiblocks.remove(id);
        if (data != null) {
            for (BlockPos pos : data.blocks) {
                blockToMultiblock.remove(pos);
            }
        }
        multiblockModes.remove(id);
        Set<BlockPos> chests = multiblockChests.remove(id);
        if (chests != null) {
            for (BlockPos chestPos : chests) {
                Set<String> multiblockIds = chestToMultiblocks.get(chestPos);
                if (multiblockIds != null) {
                    multiblockIds.remove(id);
                    if (multiblockIds.isEmpty()) {
                        chestToMultiblocks.remove(chestPos);
                    }
                }
            }
        }

        Logger.sendMessage("Removed multiblock " + id, false);
    }

    public void updateMultiblock(String id, Set<BlockPos> blocks) {
        if (level.isClientSide) return;
        MultiblockData oldData = multiblocks.get(id);
        if (oldData != null) {
            for (BlockPos pos : oldData.blocks) {
                blockToMultiblock.remove(pos);
            }
        }

        for (BlockPos pos : blocks) {
            blockToMultiblock.put(pos, id);
        }

        boolean isOnShip = oldData != null ? oldData.isOnShip : isBlockOnShip(blocks.iterator().next());
        Long shipId = oldData != null ? oldData.shipId : getShipIdForBlock(blocks.iterator().next());

        multiblocks.put(id, new MultiblockData(id, new HashSet<>(blocks), isOnShip, shipId));

        sendBlockCountSync(id, blocks.size());

        Logger.sendMessage("Updated multiblock " + id + " with " + blocks.size() +
                " blocks (on ship: " + isOnShip + ")", false);
    }

    public String getMultiblockForBlock(BlockPos pos) {
        if (level.isClientSide) return null;
        return blockToMultiblock.get(pos);
    }

    public Set<BlockPos> getMultiblockBlocks(String id) {
        if (level.isClientSide) return Collections.emptySet();
        MultiblockData data = multiblocks.get(id);
        return data != null ? new HashSet<>(data.blocks) : Collections.emptySet();
    }

    public int getMultiblockChestCount(String id) {
        if (level.isClientSide) return 0;
        Set<BlockPos> chests = multiblockChests.get(id);
        return chests != null ? chests.size() : 0;
    }

    public boolean getMultiblockMode(String multiblockId) {
        return multiblockModes.getOrDefault(multiblockId, true);
    }

    public void toggleMultiblockMode(String multiblockId) {
        boolean currentMode = multiblockModes.getOrDefault(multiblockId, true);
        boolean newMode = !currentMode;
        multiblockModes.put(multiblockId, newMode);

        // Update ALL block entities in the multiblock
        Set<BlockPos> blocks = getMultiblockBlocks(multiblockId);
        for (BlockPos pos : blocks) {
            updateBlockEntityMode(pos, newMode);
        }

        Logger.sendMessage("Multiblock " + multiblockId + " toggled to " + (newMode ? "import" : "export") + " mode", false);
    }

    private void updateBlockEntityMode(BlockPos pos, boolean importMode) {
        if (level.getBlockEntity(pos) instanceof ShipItemTransportBlockEntity blockEntity) {
            // Update the block entity's local mode
            blockEntity.setImportMode(importMode);
            blockEntity.setChanged();

            // Force client update
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
        }
    }

    public void handleChestNearMultiblock(BlockPos chestPos, BlockPos multiblockPos) {
        String multiblockId = getMultiblockForBlock(multiblockPos);
        if (multiblockId == null) {
            Logger.sendMessage("No multiblock found for block at " + multiblockPos, false);
            return;
        }

        Logger.sendMessage("Handling chest at " + chestPos + " near multiblock " + multiblockId, false);

        BlockPos primaryChestPos = getOrCreateChestGroup(chestPos);

        if (isAnyChestInGroupConnectedToMultiblock(primaryChestPos, multiblockId)) {
            if (!multiblockChests.get(multiblockId).contains(primaryChestPos)) {
                multiblockChests.get(multiblockId).add(primaryChestPos);
                chestToMultiblocks.computeIfAbsent(primaryChestPos, k -> new HashSet<>()).add(multiblockId);

                sendChestCountSync(multiblockId, multiblockChests.get(multiblockId).size());
                Logger.sendMessage("Added chest group (primary: " + primaryChestPos + ") to multiblock " + multiblockId + ". Total chests: " + multiblockChests.get(multiblockId).size(), false);
            }
        }
    }

    private boolean isAnyChestInGroupConnectedToMultiblock(BlockPos primaryChestPos, String multiblockId) {
        Set<BlockPos> chestGroup = chestGroups.get(primaryChestPos);
        if (chestGroup == null) return false;
        Set<BlockPos> multiblockBlocks = getMultiblockBlocks(multiblockId);
        for (BlockPos chestPos : chestGroup) {
            for (BlockPos multiblockBlockPos : multiblockBlocks) {
                for (Direction dir : ChestHelper.getValidConnectionDirections(level, multiblockBlockPos)) {
                    if (multiblockBlockPos.relative(dir).equals(chestPos)) {
                        Logger.sendMessage("Chest at " + chestPos + " is connected to multiblock " + multiblockId + " via block at " + multiblockBlockPos, false);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void handlePossibleChestRemoval(BlockPos possibleChestPos) {
        if (chestToPrimary.containsKey(possibleChestPos)) {
            Logger.sendMessage("Possible chest removal at " + possibleChestPos, false);
            handleChestRemoved(possibleChestPos);
        }
    }

    private void handleChestRemoved(BlockPos chestPos) {
        BlockPos primaryPos = chestToPrimary.get(chestPos);
        if (primaryPos == null) {
            Logger.sendMessage("No primary chest found for removed chest at " + chestPos, false);
            return;
        }

        Set<String> connectedMultiblocks = chestToMultiblocks.get(primaryPos);
        if (connectedMultiblocks == null) {
            Logger.sendMessage("No multiblocks found for primary chest at " + primaryPos, false);
            updateChestGroupAfterBreak(chestPos);
            return;
        }

        Logger.sendMessage("Processing chest removal at " + chestPos + " from multiblocks: " + connectedMultiblocks, false);

        boolean anyConnected = false;
        for (String multiblockId : connectedMultiblocks) {
            if (isAnyChestInGroupConnectedToMultiblock(primaryPos, multiblockId)) {
                anyConnected = true;
                break;
            }
        }

        if (!anyConnected) {
            for (String multiblockId : connectedMultiblocks) {
                multiblockChests.get(multiblockId).remove(primaryPos);
                Logger.sendMessage("Removed chest group (primary: " + primaryPos + ") from multiblock " + multiblockId, false);
            }
            chestToMultiblocks.remove(primaryPos);

            for (String multiblockId : connectedMultiblocks) {
                sendChestCountSync(multiblockId, multiblockChests.get(multiblockId).size());
            }
        }

        updateChestGroupAfterBreak(chestPos);
    }

    private BlockPos getOrCreateChestGroup(BlockPos chestPos) {
        BlockPos existingPrimary = chestToPrimary.get(chestPos);
        if (existingPrimary != null) {
            return existingPrimary;
        }

        Set<BlockPos> connectedChests = ChestHelper.findConnectedChests(level, chestPos);
        BlockPos targetPrimary = null;
        for (BlockPos connectedChest : connectedChests) {
            BlockPos primaryForConnected = chestToPrimary.get(connectedChest);
            if (primaryForConnected != null && chestGroups.containsKey(primaryForConnected)) {
                targetPrimary = primaryForConnected;
                break;
            }
        }

        if (targetPrimary != null) {
            Set<BlockPos> existingGroup = chestGroups.get(targetPrimary);
            Set<BlockPos> mergedGroup = new HashSet<>(existingGroup);
            mergedGroup.addAll(connectedChests);
            chestGroups.put(targetPrimary, mergedGroup);
            for (BlockPos pos : mergedGroup) {
                chestToPrimary.put(pos, targetPrimary);
            }
            Logger.sendMessage("MERGED chest group - Input: " + chestPos + ", Joined existing primary: " + targetPrimary + ", All members: " + mergedGroup, false);
            return targetPrimary;
        }

        BlockPos primaryPos;
        if (connectedChests.size() == 1) {
            primaryPos = chestPos;
        } else {
            primaryPos = determineConsistentPrimaryPosition(connectedChests);
        }

        registerChestGroup(primaryPos, connectedChests);
        Logger.sendMessage("Created NEW chest group - Input: " + chestPos + ", Primary: " + primaryPos + ", All members: " + connectedChests, false);
        return primaryPos;
    }

    private BlockPos determineConsistentPrimaryPosition(Set<BlockPos> chestGroup) {
        return chestGroup.stream()
                .min((pos1, pos2) -> {
                    int xCompare = Integer.compare(pos1.getX(), pos2.getX());
                    if (xCompare != 0) return xCompare;
                    int zCompare = Integer.compare(pos1.getZ(), pos2.getZ());
                    if (zCompare != 0) return zCompare;
                    return Integer.compare(pos1.getY(), pos2.getY());
                })
                .orElse(chestGroup.iterator().next());
    }

    private void registerChestGroup(BlockPos primaryPos, Set<BlockPos> chestGroup) {
        chestGroups.put(primaryPos, chestGroup);
        for (BlockPos pos : chestGroup) {
            chestToPrimary.put(pos, primaryPos);
        }
    }

    private void updateChestGroupAfterBreak(BlockPos brokenChestPos) {
        BlockPos primaryPos = chestToPrimary.get(brokenChestPos);
        if (primaryPos == null) return;
        Set<BlockPos> chestGroup = chestGroups.get(primaryPos);
        if (chestGroup == null) return;

        chestGroup.remove(brokenChestPos);
        chestToPrimary.remove(brokenChestPos);

        if (chestGroup.isEmpty()) {
            chestGroups.remove(primaryPos);
            Set<String> connectedMultiblocks = chestToMultiblocks.remove(primaryPos);
            if (connectedMultiblocks != null) {
                for (String multiblockId : connectedMultiblocks) {
                    multiblockChests.get(multiblockId).remove(primaryPos);
                }
            }
        } else {
            BlockPos newPrimary = determineConsistentPrimaryPosition(chestGroup);
            if (!newPrimary.equals(primaryPos)) {
                updatePrimaryChestPositionForGroup(primaryPos, newPrimary, chestGroup);
            }
            validateChestConnectionsForGroup(newPrimary);
        }
    }

    private void validateChestConnectionsForGroup(BlockPos primaryChestPos) {
        Set<String> connectedMultiblocks = chestToMultiblocks.get(primaryChestPos);
        if (connectedMultiblocks == null) return;
        boolean needsUpdate = false;
        Set<String> multiblocksToRemove = new HashSet<>();
        for (String multiblockId : connectedMultiblocks) {
            if (!isAnyChestInGroupConnectedToMultiblock(primaryChestPos, multiblockId)) {
                multiblocksToRemove.add(multiblockId);
                needsUpdate = true;
            }
        }
        for (String multiblockId : multiblocksToRemove) {
            connectedMultiblocks.remove(multiblockId);
            multiblockChests.get(multiblockId).remove(primaryChestPos);
            Logger.sendMessage("Removed disconnected multiblock " + multiblockId + " from chest group " + primaryChestPos, false);
        }
        if (needsUpdate) {
            for (String multiblockId : multiblocksToRemove) {
                sendChestCountSync(multiblockId, multiblockChests.get(multiblockId).size());
            }
        }
    }

    private void updatePrimaryChestPositionForGroup(BlockPos oldPrimary, BlockPos newPrimary, Set<BlockPos> chestGroup) {
        Set<String> multiblockIds = chestToMultiblocks.get(oldPrimary);
        if (multiblockIds != null) {
            for (String multiblockId : multiblockIds) {
                multiblockChests.get(multiblockId).remove(oldPrimary);
                multiblockChests.get(multiblockId).add(newPrimary);
            }
            chestToMultiblocks.remove(oldPrimary);
            chestToMultiblocks.put(newPrimary, multiblockIds);
        }
        chestGroups.remove(oldPrimary);
        registerChestGroup(newPrimary, chestGroup);

        Logger.sendMessage("Updated primary chest from " + oldPrimary + " to " + newPrimary + " for group: " + chestGroup, false);
    }

    private void validateChestConnectionsAfterBlockRemoval(String multiblockId) {
        Set<BlockPos> chestsToCheck = new HashSet<>(multiblockChests.get(multiblockId));
        boolean needsUpdate = false;
        for (BlockPos primaryChestPos : chestsToCheck) {
            if (!isAnyChestInGroupConnectedToMultiblock(primaryChestPos, multiblockId)) {
                multiblockChests.get(multiblockId).remove(primaryChestPos);
                Set<String> multiblockIds = chestToMultiblocks.get(primaryChestPos);
                if (multiblockIds != null) {
                    multiblockIds.remove(multiblockId);
                    if (multiblockIds.isEmpty()) {
                        chestToMultiblocks.remove(primaryChestPos);
                    }
                }
                needsUpdate = true;
                Logger.sendMessage("Removed disconnected chest group (primary: " + primaryChestPos + ") from multiblock " + multiblockId, false);
            }
        }
        if (needsUpdate) {
            sendChestCountSync(multiblockId, multiblockChests.get(multiblockId).size());
        }
    }

    public void onBlockPlaced(BlockPos newPos) {
        if (level.isClientSide) return;
        Logger.sendMessage("Processing block placement at " + newPos, true);
        if (blockToMultiblock.containsKey(newPos)) {
            Logger.sendMessage("WARNING: Block at " + newPos + " already in multiblock " + blockToMultiblock.get(newPos), true);
            return;
        }
        Set<String> adjacentMultiblocks = findAdjacentMultiblocks(newPos);
        if (adjacentMultiblocks.isEmpty()) {
            createSingleBlockMultiblock(newPos);
        } else if (adjacentMultiblocks.size() == 1) {
            String targetId = adjacentMultiblocks.iterator().next();
            joinMultiblock(newPos, targetId);
        } else {
            mergeMultiblocks(newPos, adjacentMultiblocks);
        }
    }

    private void createSingleBlockMultiblock(BlockPos pos) {
        Set<BlockPos> singleBlock = Collections.singleton(pos);
        boolean isOnShip = isBlockOnShip(pos);
        Long shipId = isOnShip ? getShipIdForBlock(pos) : null;
        String newId = createMultiblock(singleBlock, true, isOnShip, shipId);
        updateBlockEntity(pos, newId, singleBlock);
        Logger.sendMessage("Created single-block multiblock " + newId + " at " + pos + " (on ship: " + isOnShip + ")", false);
    }

    private void joinMultiblock(BlockPos newPos, String targetId) {
        MultiblockData targetData = multiblocks.get(targetId);
        if (targetData == null) return;
        Set<BlockPos> blocks = new HashSet<>(targetData.blocks);
        blocks.add(newPos);
        updateMultiblock(targetId, blocks, targetData.isOnShip, targetData.shipId);
        updateBlockEntity(newPos, targetId, blocks);
        Logger.sendMessage("Added block at " + newPos + " to multiblock " + targetId + " (inherited ship status: " + targetData.isOnShip + ")", false);
    }

    private void mergeMultiblocks(BlockPos newPos, Set<String> multiblockIds) {
        Direction requiredFacing = level.getBlockState(newPos).getValue(ShipItemTransportBlock.FACING);
        for (String id : multiblockIds) {
            Set<BlockPos> blocks = getMultiblockBlocks(id);
            if (!blocks.isEmpty()) {
                BlockPos samplePos = blocks.iterator().next();
                Direction sampleFacing = level.getBlockState(samplePos).getValue(ShipItemTransportBlock.FACING);
                if (sampleFacing != requiredFacing) {
                    Logger.sendMessage("Cannot merge multiblocks with different facing directions", false);
                    return;
                }
            }
        }

        String mainId = multiblockIds.iterator().next();
        MultiblockData mainData = multiblocks.get(mainId);
        Set<BlockPos> allMergedBlocks = new HashSet<>(getMultiblockBlocks(mainId));
        allMergedBlocks.add(newPos);

        boolean mergedIsOnShip = mainData.isOnShip;
        Long mergedShipId = mainData.shipId;
        for (String id : multiblockIds) {
            MultiblockData data = multiblocks.get(id);
            if (data != null && data.isOnShip) {
                mergedIsOnShip = true;
                mergedShipId = data.shipId;
                break;
            }
        }

        Set<BlockPos> allPrimaryChests = new HashSet<>();
        for (String id : multiblockIds) {
            Set<BlockPos> chests = multiblockChests.get(id);
            if (chests != null) {
                allPrimaryChests.addAll(chests);
            }
        }

        for (String id : multiblockIds) {
            if (!id.equals(mainId)) {
                allMergedBlocks.addAll(getMultiblockBlocks(id));
                removeMultiblock(id);
            }
        }

        updateMultiblock(mainId, allMergedBlocks, mergedIsOnShip, mergedShipId);

        for (BlockPos primaryChestPos : allPrimaryChests) {
            if (!multiblockChests.get(mainId).contains(primaryChestPos)) {
                multiblockChests.get(mainId).add(primaryChestPos);
                chestToMultiblocks.computeIfAbsent(primaryChestPos, k -> new HashSet<>()).add(mainId);
            }
        }

        for (BlockPos pos : allMergedBlocks) {
            updateBlockEntity(pos, mainId, allMergedBlocks);
        }

        sendBlockCountSyncToAllMergedViewers(multiblockIds, mainId, allMergedBlocks.size());

        Logger.sendMessage("Merged " + multiblockIds.size() + " multiblocks into " + mainId + " with " + allMergedBlocks.size() + " blocks" + " (on ship: " + mergedIsOnShip + ", shipId: " + mergedShipId + ")", false);
    }

    private void updateMultiblock(String id, Set<BlockPos> blocks, boolean isOnShip, Long shipId) {
        if (level.isClientSide) return;

        MultiblockData oldData = multiblocks.get(id);

        if (oldData != null) {
            for (BlockPos pos : oldData.blocks) {
                blockToMultiblock.remove(pos);
            }
        }

        for (BlockPos pos : blocks) {
            blockToMultiblock.put(pos, id);
        }

        multiblocks.put(id, new MultiblockData(id, new HashSet<>(blocks), isOnShip, shipId));

        sendBlockCountSync(id, blocks.size());

        Logger.sendMessage("Updated multiblock " + id + " with " + blocks.size() +
                " blocks (on ship: " + isOnShip + ", shipId: " + shipId + ")", false);
    }

    public void onBlockRemoved(BlockPos removedPos) {
        if (level.isClientSide) return;
        String multiblockId = blockToMultiblock.get(removedPos);
        if (multiblockId == null) return;
        Logger.sendMessage("Processing block removal at " + removedPos + " from multiblock " + multiblockId, false);
        blockToMultiblock.remove(removedPos);
        Set<BlockPos> remainingBlocks = getMultiblockBlocks(multiblockId);
        remainingBlocks.remove(removedPos);
        if (remainingBlocks.isEmpty()) {
            removeMultiblock(multiblockId);
        } else {
            List<Set<BlockPos>> connectedComponents = findConnectedComponents(remainingBlocks);
            if (connectedComponents.size() == 1) {
                MultiblockData oldData = multiblocks.get(multiblockId);
                updateMultiblock(multiblockId, remainingBlocks, oldData.isOnShip, oldData.shipId);
                validateChestConnectionsAfterBlockRemoval(multiblockId);
            } else {
                MultiblockData originalData = multiblocks.get(multiblockId);
                boolean originalMode = getMultiblockMode(multiblockId);
                boolean originalIsOnShip = originalData.isOnShip;
                Long originalShipId = originalData.shipId;
                removeMultiblock(multiblockId);
                for (Set<BlockPos> component : connectedComponents) {
                    if (!component.isEmpty()) {
                        String newMultiblockId = createMultiblock(component, originalMode, originalIsOnShip, originalShipId);
                        for (BlockPos pos : component) {
                            updateBlockEntity(pos, newMultiblockId, component);
                            sendBlockCountSyncToViewers(pos, component.size());
                        }
                    }
                }
                Logger.sendMessage("Split multiblock into " + connectedComponents.size() + " components (preserved ship status: " + originalIsOnShip + ")", false);
            }
        }
    }

    private Set<String> findAdjacentMultiblocks(BlockPos pos) {
        if (level.isClientSide) return Collections.emptySet();
        Set<String> multiblocks = new HashSet<>();
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            if (canConnect(pos, neighborPos, level)) {
                String multiblockId = blockToMultiblock.get(neighborPos);
                if (multiblockId != null && isValidBlock(neighborPos)) {
                    multiblocks.add(multiblockId);
                }
            }
        }
        return multiblocks;
    }

    private boolean canConnect(BlockPos pos1, BlockPos pos2, Level level) {
        BlockState state1 = level.getBlockState(pos1);
        BlockState state2 = level.getBlockState(pos2);
        if (!(state1.getBlock() instanceof ShipItemTransportBlock) ||
                !(state2.getBlock() instanceof ShipItemTransportBlock)) {
            return false;
        }
        Direction facing1 = state1.getValue(ShipItemTransportBlock.FACING);
        Direction facing2 = state2.getValue(ShipItemTransportBlock.FACING);
        if (facing1 != facing2) {
            return false;
        }
        return areOnSamePlane(pos1, pos2, facing1);
    }

    private boolean areOnSamePlane(BlockPos pos1, BlockPos pos2, Direction facing) {
        switch (facing) {
            case UP:
            case DOWN:
                return pos1.getY() == pos2.getY();
            case NORTH:
            case SOUTH:
                return pos1.getZ() == pos2.getZ();
            case EAST:
            case WEST:
                return pos1.getX() == pos2.getX();
            default:
                return false;
        }
    }

    private List<Set<BlockPos>> findConnectedComponents(Set<BlockPos> blocks) {
        List<Set<BlockPos>> components = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos start : blocks) {
            if (!visited.contains(start)) {
                Set<BlockPos> component = new HashSet<>();
                floodFill(start, blocks, component, visited);
                components.add(component);
            }
        }
        return components;
    }

    private void floodFill(BlockPos start, Set<BlockPos> allBlocks, Set<BlockPos> component, Set<BlockPos> visited) {
        Stack<BlockPos> stack = new Stack<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            BlockPos current = stack.pop();
            if (visited.contains(current)) continue;
            visited.add(current);
            component.add(current);
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (allBlocks.contains(neighbor) &&
                        !visited.contains(neighbor) &&
                        canConnect(current, neighbor, level)) {
                    stack.push(neighbor);
                }
            }
        }
    }

    private void updateBlockEntity(BlockPos pos, String multiblockId, Set<BlockPos> blocks) {
        if (level.getBlockEntity(pos) instanceof ShipItemTransportBlockEntity blockEntity) {
            blockEntity.setMultiblock(multiblockId, blocks);
            blockEntity.setChanged();
            if (level instanceof ServerLevel) {
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
        }
    }

    private boolean isValidBlock(BlockPos pos) {
        return level.isLoaded(pos) &&
                level.getBlockState(pos).getBlock() instanceof ShipItemTransportBlock &&
                level.getBlockEntity(pos) instanceof ShipItemTransportBlockEntity;
    }

    // Network sync methods
    private void sendBlockCountSyncToAllMergedViewers(Set<String> mergedMultiblockIds, String newMultiblockId, int newBlockCount) {
        if (level instanceof ServerLevel serverLevel) {
            BlockCountSyncPacket syncPacket = new BlockCountSyncPacket(newBlockCount);
            for (ServerPlayer serverPlayer : serverLevel.players()) {
                if (serverPlayer.containerMenu instanceof ShipItemTransportMenu menu &&
                        menu.getBlockEntity() != null) {
                    String viewedMultiblockId = menu.getBlockEntity().getMultiblockId();
                    if (mergedMultiblockIds.contains(viewedMultiblockId) ||
                            newMultiblockId.equals(viewedMultiblockId)) {
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), syncPacket);
                    }
                }
            }
        }
    }

    private void sendBlockCountSyncToViewers(BlockPos blockPos, int blockCount) {
        if (level instanceof ServerLevel serverLevel) {
            BlockCountSyncPacket syncPacket = new BlockCountSyncPacket(blockCount);
            for (ServerPlayer serverPlayer : serverLevel.players()) {
                if (serverPlayer.containerMenu instanceof ShipItemTransportMenu menu &&
                        menu.getBlockEntity() != null &&
                        menu.getBlockEntity().getBlockPos().equals(blockPos)) {
                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), syncPacket);
                }
            }
        }
    }

    private void sendBlockCountSync(String multiblockId, int blockCount) {
        if (level instanceof ServerLevel serverLevel) {
            BlockCountSyncPacket syncPacket = new BlockCountSyncPacket(blockCount);
            for (ServerPlayer serverPlayer : serverLevel.players()) {
                if (serverPlayer.containerMenu instanceof ShipItemTransportMenu menu &&
                        menu.getBlockEntity() != null) {
                    String viewedMultiblockId = menu.getBlockEntity().getMultiblockId();
                    if (multiblockId.equals(viewedMultiblockId)) {
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), syncPacket);
                    }
                }
            }
        }
    }

    private void sendChestCountSync(String multiblockId, int chestCount) {
        if (level instanceof ServerLevel serverLevel) {
            ChestCountSyncPacket syncPacket = new ChestCountSyncPacket(chestCount);
            for (ServerPlayer serverPlayer : serverLevel.players()) {
                if (serverPlayer.containerMenu instanceof ShipItemTransportMenu menu &&
                        menu.getBlockEntity() != null) {
                    String viewedMultiblockId = menu.getBlockEntity().getMultiblockId();
                    if (multiblockId.equals(viewedMultiblockId)) {
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), syncPacket);
                    }
                }
            }
        }
    }

    private void sendShipInfoSync(String multiblockId, boolean isOnShip, long shipId) {
        if (level instanceof ServerLevel serverLevel) {
            ShipInfoSyncPacket syncPacket = new ShipInfoSyncPacket(isOnShip, shipId);
            for (ServerPlayer serverPlayer : serverLevel.players()) {
                if (serverPlayer.containerMenu instanceof ShipItemTransportMenu menu &&
                        menu.getBlockEntity() != null) {
                    String viewedMultiblockId = menu.getBlockEntity().getMultiblockId();
                    if (multiblockId.equals(viewedMultiblockId)) {
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), syncPacket);
                    }
                }
            }
        }
    }

    public void sendInitialShipInfoSync(ServerPlayer player, String multiblockId) {
        if (level.isClientSide) return;
        MultiblockData data = multiblocks.get(multiblockId);
        if (data != null) {
            long shipId = data.shipId != null ? data.shipId : -1;
            ShipInfoSyncPacket syncPacket = new ShipInfoSyncPacket(data.isOnShip, shipId);
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), syncPacket);
        }
    }

    private String createMultiblock(Set<BlockPos> blocks, boolean importMode, boolean isOnShip, Long shipId) {
        if (level.isClientSide) return null;
        String id = UUID.randomUUID().toString();
        multiblocks.put(id, new MultiblockData(id, new HashSet<>(blocks), isOnShip, shipId));
        multiblockModes.put(id, importMode);
        multiblockChests.put(id, new HashSet<>());
        for (BlockPos pos : blocks) {
            blockToMultiblock.put(pos, id);
        }

        Logger.sendMessage("Created new multiblock " + id + " with " + blocks.size() + " blocks (on ship: " + isOnShip + ", shipId: " + shipId + ")", false);
        return id;
    }

    // Ship detection methods
    private Ship findShipForBlock(BlockPos worldPos) {
        if (level.isClientSide) return null;
        ShipWorldCore shipWorld = VSGameUtilsKt.getShipObjectWorld(level);
        if (shipWorld == null) return null;
        for (Ship ship : shipWorld.getLoadedShips()) {
            AABBic shipAABB = ship.getShipAABB();
            if (shipAABB == null) continue;
            if (worldPos.getX() >= shipAABB.minX() && worldPos.getX() <= shipAABB.maxX() &&
                    worldPos.getY() >= shipAABB.minY() && worldPos.getY() <= shipAABB.maxY() &&
                    worldPos.getZ() >= shipAABB.minZ() && worldPos.getZ() <= shipAABB.maxZ()) {
                return ship;
            }
        }
        return null;
    }

    private boolean isBlockOnShip(BlockPos worldPos) {
        return findShipForBlock(worldPos) != null;
    }

    private Long getShipIdForBlock(BlockPos worldPos) {
        Ship ship = findShipForBlock(worldPos);
        return ship != null ? ship.getId() : null;
    }

    // Public methods to access ship status
    public boolean isMultiblockOnShip(String multiblockId) {
        if (level.isClientSide) return false;
        MultiblockData data = multiblocks.get(multiblockId);
        return data != null && data.isOnShip;
    }

    public Long getMultiblockShipId(String multiblockId) {
        if (level.isClientSide) return null;
        MultiblockData data = multiblocks.get(multiblockId);
        return data.shipId != null ? data.shipId : -1;
    }

    public String getMultiblockShipInfo(String multiblockId) {
        if (level.isClientSide) return "Ground";
        MultiblockData data = multiblocks.get(multiblockId);
        if (data == null || !data.isOnShip || data.shipId == null) {
            return "Ground";
        }
        ShipWorldCore shipWorld = VSGameUtilsKt.getShipObjectWorld(level);
        if (shipWorld != null) {
            Ship ship = shipWorld.getAllShips().getById(data.shipId);
            if (ship != null) {
                return "Ship " + data.shipId;
            }
        }
        return "Ship " + data.shipId;
    }
}