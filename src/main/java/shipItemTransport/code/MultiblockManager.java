package shipItemTransport.code;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;
import java.util.stream.Collectors;

public class MultiblockManager extends SavedData {
    private final Map<String, MultiblockData> multiblocks = new HashMap<>();
    private final Map<BlockPos, String> blockToMultiblock = new HashMap<>();
    private final Map<String, Boolean> multiblockModes = new HashMap<>(); // true = import, false = export

    // Chest tracking - UPDATED: Support for multiple multiblocks per chest group
    private final Map<String, Set<BlockPos>> multiblockChests = new HashMap<>(); // Multiblock ID -> Set of primary chest positions
    private final Map<BlockPos, Set<String>> chestToMultiblocks = new HashMap<>(); // Primary chest -> Set of multiblock IDs (ONE-TO-MANY)
    private final Map<BlockPos, Set<BlockPos>> chestGroups = new HashMap<>(); // Primary chest -> all chest positions in group
    private final Map<BlockPos, BlockPos> chestToPrimary = new HashMap<>(); // Any chest -> primary chest

    private final Level level;

    public MultiblockManager(Level level) {
        this.level = level;
    }

    public static MultiblockManager get(Level level) {
        if (level.isClientSide) return null;
        return ((ServerLevel) level).getDataStorage().computeIfAbsent(
                tag -> load(tag, level),
                () -> new MultiblockManager(level),
                "ship_item_transport_multiblocks"
        );
    }
    public void validateAllChestConnections() {
        if (level.isClientSide) return;

        Logger.sendMessage("Validating all chest connections for " + multiblocks.size() + " multiblocks", false);

        for (String multiblockId : multiblocks.keySet()) {
            validateChestConnectionsAfterBlockRemoval(multiblockId);
        }
    }
    public static MultiblockManager load(CompoundTag tag, Level level) {
        if (level.isClientSide) return null;

        MultiblockManager manager = new MultiblockManager(level);

        // 1. Load all multiblocks
        ListTag multiblockList = tag.getList("Multiblocks", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < multiblockList.size(); i++) {
            CompoundTag multiblockTag = multiblockList.getCompound(i);
            String id = multiblockTag.getString("Id");
            boolean importMode = multiblockTag.getBoolean("ImportMode");

            // 2. Load all block positions for this multiblock
            ListTag blocksList = multiblockTag.getList("Blocks", CompoundTag.TAG_COMPOUND);
            Set<BlockPos> blocks = new HashSet<>();
            for (int j = 0; j < blocksList.size(); j++) {
                CompoundTag posTag = blocksList.getCompound(j);
                BlockPos pos = new BlockPos(
                        posTag.getInt("x"),
                        posTag.getInt("y"),
                        posTag.getInt("z")
                );
                blocks.add(pos);
                manager.blockToMultiblock.put(pos, id); // Rebuild block->multiblock mapping
            }

            manager.multiblocks.put(id, new MultiblockData(id, blocks));
            manager.multiblockModes.put(id, importMode);
            manager.multiblockChests.put(id, new HashSet<>()); // Initialize empty chest set
        }

        // 3. Load chest connections
        if (tag.contains("ChestConnections")) {
            ListTag chestConnectionsList = tag.getList("ChestConnections", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < chestConnectionsList.size(); i++) {
                CompoundTag connectionTag = chestConnectionsList.getCompound(i);
                BlockPos primaryChestPos = new BlockPos(
                        connectionTag.getInt("x"),
                        connectionTag.getInt("y"),
                        connectionTag.getInt("z")
                );

                // 4. Load which multiblocks this chest is connected to
                ListTag multiblockIdsList = connectionTag.getList("MultiblockIds", CompoundTag.TAG_STRING);
                Set<String> multiblockIds = new HashSet<>();
                for (int j = 0; j < multiblockIdsList.size(); j++) {
                    multiblockIds.add(multiblockIdsList.getString(j));
                }

                manager.chestToMultiblocks.put(primaryChestPos, multiblockIds);

                // 5. Rebuild multiblock->chests mapping
                for (String multiblockId : multiblockIds) {
                    manager.multiblockChests.computeIfAbsent(multiblockId, k -> new HashSet<>()).add(primaryChestPos);
                }
            }
        }

        // 6. Load chest groups
        if (tag.contains("ChestGroups")) {
            ListTag chestGroupsList = tag.getList("ChestGroups", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < chestGroupsList.size(); i++) {
                CompoundTag groupTag = chestGroupsList.getCompound(i);
                BlockPos primaryPos = new BlockPos(
                        groupTag.getInt("primaryX"),
                        groupTag.getInt("primaryY"),
                        groupTag.getInt("primaryZ")
                );

                ListTag membersList = groupTag.getList("members", CompoundTag.TAG_COMPOUND);
                Set<BlockPos> members = new HashSet<>();
                for (int j = 0; j < membersList.size(); j++) {
                    CompoundTag memberTag = membersList.getCompound(j);
                    BlockPos memberPos = new BlockPos(
                            memberTag.getInt("x"),
                            memberTag.getInt("y"),
                            memberTag.getInt("z")
                    );
                    members.add(memberPos);

                    // Rebuild chestToPrimary mapping
                    manager.chestToPrimary.put(memberPos, primaryPos);
                }

                manager.chestGroups.put(primaryPos, members);
            }
        }

        Logger.sendMessage("Loaded " + manager.multiblocks.size() + " multiblocks and " + manager.chestGroups.size() + " chest groups from save", false);

        // Validate chest connections after load
        manager.validateAllChestConnections();

        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (level.isClientSide) return tag;

        // 1. Save all multiblocks
        ListTag multiblockList = new ListTag();
        for (MultiblockData data : multiblocks.values()) {
            CompoundTag multiblockTag = new CompoundTag();
            multiblockTag.putString("Id", data.id);
            multiblockTag.putBoolean("ImportMode", multiblockModes.getOrDefault(data.id, true));

            // 2. Save all block positions in this multiblock
            ListTag blocksList = new ListTag();
            for (BlockPos pos : data.blocks) {
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("x", pos.getX());
                posTag.putInt("y", pos.getY());
                posTag.putInt("z", pos.getZ());
                blocksList.add(posTag);
            }
            multiblockTag.put("Blocks", blocksList);
            multiblockList.add(multiblockTag);
        }
        tag.put("Multiblocks", multiblockList);

        // 3. Save chest connections
        ListTag chestConnectionsList = new ListTag();
        for (Map.Entry<BlockPos, Set<String>> entry : chestToMultiblocks.entrySet()) {
            CompoundTag connectionTag = new CompoundTag();
            connectionTag.putInt("x", entry.getKey().getX());
            connectionTag.putInt("y", entry.getKey().getY());
            connectionTag.putInt("z", entry.getKey().getZ());

            // 4. Save which multiblocks this chest is connected to
            ListTag multiblockIdsList = new ListTag();
            for (String multiblockId : entry.getValue()) {
                multiblockIdsList.add(net.minecraft.nbt.StringTag.valueOf(multiblockId));
            }
            connectionTag.put("MultiblockIds", multiblockIdsList);
            chestConnectionsList.add(connectionTag);
        }
        tag.put("ChestConnections", chestConnectionsList);

        // 5. Save chest groups
        ListTag chestGroupsList = new ListTag();
        for (Map.Entry<BlockPos, Set<BlockPos>> entry : chestGroups.entrySet()) {
            CompoundTag groupTag = new CompoundTag();
            groupTag.putInt("primaryX", entry.getKey().getX());
            groupTag.putInt("primaryY", entry.getKey().getY());
            groupTag.putInt("primaryZ", entry.getKey().getZ());

            ListTag membersList = new ListTag();
            for (BlockPos member : entry.getValue()) {
                CompoundTag memberTag = new CompoundTag();
                memberTag.putInt("x", member.getX());
                memberTag.putInt("y", member.getY());
                memberTag.putInt("z", member.getZ());
                membersList.add(memberTag);
            }
            groupTag.put("members", membersList);
            chestGroupsList.add(groupTag);
        }
        tag.put("ChestGroups", chestGroupsList);

        Logger.sendMessage("Saved " + multiblockList.size() + " multiblocks and " + chestGroups.size() + " chest groups", false);
        return tag;
    }

    public String createMultiblock(Set<BlockPos> blocks) {
        if (level.isClientSide) return null;

        String id = UUID.randomUUID().toString();
        multiblocks.put(id, new MultiblockData(id, new HashSet<>(blocks)));
        multiblockModes.put(id, true); // Default to import mode
        multiblockChests.put(id, new HashSet<>()); // Initialize chest set

        for (BlockPos pos : blocks) {
            blockToMultiblock.put(pos, id);
        }

        setDirty();
        Logger.sendMessage("Created new multiblock " + id + " with " + blocks.size() + " blocks", false);
        return id;
    }

    public String createMultiblock(Set<BlockPos> blocks, boolean importMode) {
        if (level.isClientSide) return null;

        String id = UUID.randomUUID().toString();
        multiblocks.put(id, new MultiblockData(id, new HashSet<>(blocks)));
        multiblockModes.put(id, importMode); // Use specified mode
        multiblockChests.put(id, new HashSet<>()); // Initialize chest set

        for (BlockPos pos : blocks) {
            blockToMultiblock.put(pos, id);
        }

        setDirty();
        Logger.sendMessage("Created new multiblock " + id + " with " + blocks.size() + " blocks (mode: " + (importMode ? "import" : "export") + ")", false);
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

        // Remove all chest connections for this multiblock
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

        setDirty();
        Logger.sendMessage("Removed multiblock " + id, false);
    }

    public void updateMultiblock(String id, Set<BlockPos> blocks) {
        if (level.isClientSide) return;

        MultiblockData oldData = multiblocks.get(id);
        if (oldData != null) {
            // Remove old block mappings
            for (BlockPos pos : oldData.blocks) {
                blockToMultiblock.remove(pos);
            }

            // Add new block mappings
            for (BlockPos pos : blocks) {
                blockToMultiblock.put(pos, id);
            }

            multiblocks.put(id, new MultiblockData(id, new HashSet<>(blocks)));
            setDirty();

            // SEND BLOCK COUNT SYNC TO ALL PLAYERS VIEWING THIS MULTIBLOCK
            sendBlockCountSync(id, blocks.size());

            Logger.sendMessage("Updated multiblock " + id + " with " + blocks.size() + " blocks", false);
        }
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
        return multiblockModes.getOrDefault(multiblockId, true); // Default to import mode
    }

    public void toggleMultiblockMode(String multiblockId) {
        boolean currentMode = multiblockModes.getOrDefault(multiblockId, true);
        boolean newMode = !currentMode;
        multiblockModes.put(multiblockId, newMode);
        setDirty();

        // Update all block entities in the multiblock
        Set<BlockPos> blocks = getMultiblockBlocks(multiblockId);
        for (BlockPos pos : blocks) {
            updateBlockEntityMode(pos, newMode);
        }

        Logger.sendMessage("Multiblock " + multiblockId + " toggled to " + (newMode ? "import" : "export") + " mode", false);
    }

    private void updateBlockEntityMode(BlockPos pos, boolean importMode) {
        if (level.getBlockEntity(pos) instanceof ShipItemTransportBlockEntity blockEntity) {
            blockEntity.setChanged();
            blockEntity.markForClientUpdate();
        }
    }

    // UPDATED: Improved chest connection handling for multiple multiblocks
    public void handleChestNearMultiblock(BlockPos chestPos, BlockPos multiblockPos) {
        String multiblockId = getMultiblockForBlock(multiblockPos);
        if (multiblockId == null) {
            Logger.sendMessage("No multiblock found for block at " + multiblockPos, false);
            return;
        }

        Logger.sendMessage("Handling chest at " + chestPos + " near multiblock " + multiblockId, false);

        // Get or create chest group (handles single/double chests)
        BlockPos primaryChestPos = getOrCreateChestGroup(chestPos);

        // Check if ANY chest in the group is connected to this multiblock
        if (isAnyChestInGroupConnectedToMultiblock(primaryChestPos, multiblockId)) {
            // Add connection if not already present
            if (!multiblockChests.get(multiblockId).contains(primaryChestPos)) {
                multiblockChests.get(multiblockId).add(primaryChestPos);
                chestToMultiblocks.computeIfAbsent(primaryChestPos, k -> new HashSet<>()).add(multiblockId);

                setDirty();

                // Send GUI update
                sendChestCountSync(multiblockId, multiblockChests.get(multiblockId).size());
                Logger.sendMessage("Added chest group (primary: " + primaryChestPos + ") to multiblock " + multiblockId + ". Total chests: " + multiblockChests.get(multiblockId).size(), false);
            }
        }
    }

    // NEW METHOD: Check if any chest in a group is connected to a multiblock
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
        // Check if this was a chest position we were tracking
        if (chestToPrimary.containsKey(possibleChestPos)) {
            Logger.sendMessage("Possible chest removal at " + possibleChestPos, false);
            handleChestRemoved(possibleChestPos);
        }
    }

    private void handleChestRemoved(BlockPos chestPos) {
        // Find which primary chest group this belongs to
        BlockPos primaryPos = chestToPrimary.get(chestPos);
        if (primaryPos == null) {
            Logger.sendMessage("No primary chest found for removed chest at " + chestPos, false);
            return;
        }

        // Get ALL multiblocks this chest group is connected to
        Set<String> connectedMultiblocks = chestToMultiblocks.get(primaryPos);
        if (connectedMultiblocks == null) {
            Logger.sendMessage("No multiblocks found for primary chest at " + primaryPos, false);
            // Still need to update chest group tracking
            updateChestGroupAfterBreak(chestPos);
            return;
        }

        Logger.sendMessage("Processing chest removal at " + chestPos + " from multiblocks: " + connectedMultiblocks, false);

        // Check if any chest in the group is still connected to ANY of the multiblocks
        boolean anyConnected = false;
        BlockPos newPrimaryCandidate = null;

        for (String multiblockId : connectedMultiblocks) {
            if (isAnyChestInGroupConnectedToMultiblock(primaryPos, multiblockId)) {
                anyConnected = true;
                break;
            }
        }

        if (!anyConnected) {
            // Remove chest group from ALL multiblocks
            for (String multiblockId : connectedMultiblocks) {
                multiblockChests.get(multiblockId).remove(primaryPos);
                Logger.sendMessage("Removed chest group (primary: " + primaryPos + ") from multiblock " + multiblockId, false);
            }
            chestToMultiblocks.remove(primaryPos);
            setDirty();

            // Send GUI updates to all affected multiblocks
            for (String multiblockId : connectedMultiblocks) {
                sendChestCountSync(multiblockId, multiblockChests.get(multiblockId).size());
            }
        }

        // Update chest group tracking
        updateChestGroupAfterBreak(chestPos);
    }

    private BlockPos getOrCreateChestGroup(BlockPos chestPos) {
        // Step 1: Check if this exact chest is already in a group
        BlockPos existingPrimary = chestToPrimary.get(chestPos);
        if (existingPrimary != null) {
            return existingPrimary;
        }

        // Step 2: Find all connected chests (single or double chest)
        Set<BlockPos> connectedChests = ChestHelper.findConnectedChests(level, chestPos);

        // Step 3: Check if ANY chest in the connected group already belongs to an existing group
        BlockPos targetPrimary = null;
        for (BlockPos connectedChest : connectedChests) {
            BlockPos primaryForConnected = chestToPrimary.get(connectedChest);
            if (primaryForConnected != null && chestGroups.containsKey(primaryForConnected)) {
                targetPrimary = primaryForConnected;
                break;
            }
        }

        // Step 4: If we found an existing group, join it
        if (targetPrimary != null) {
            // Merge all chests into the existing group
            Set<BlockPos> existingGroup = chestGroups.get(targetPrimary);
            Set<BlockPos> mergedGroup = new HashSet<>(existingGroup);
            mergedGroup.addAll(connectedChests);

            // Update the group with all chests
            chestGroups.put(targetPrimary, mergedGroup);
            for (BlockPos pos : mergedGroup) {
                chestToPrimary.put(pos, targetPrimary);
            }

            Logger.sendMessage("MERGED chest group - Input: " + chestPos +
                    ", Joined existing primary: " + targetPrimary +
                    ", All members: " + mergedGroup, false);
            return targetPrimary;
        }

        // Step 5: No existing group found - create a new one
        BlockPos primaryPos;
        if (connectedChests.size() == 1) {
            primaryPos = chestPos;
        } else {
            primaryPos = determineConsistentPrimaryPosition(connectedChests);
        }

        // Register the new group
        registerChestGroup(primaryPos, connectedChests);

        Logger.sendMessage("Created NEW chest group - Input: " + chestPos +
                ", Primary: " + primaryPos +
                ", All members: " + connectedChests, false);

        return primaryPos;
    }

    // Consistent primary chest determination
    private BlockPos determineConsistentPrimaryPosition(Set<BlockPos> chestGroup) {
        // Always use the chest with the smallest coordinates to ensure consistency
        // This prevents the "left vs right" issue
        return chestGroup.stream()
                .min((pos1, pos2) -> {
                    // Compare by X, then Z, then Y
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

        // Remove the broken chest from the group
        chestGroup.remove(brokenChestPos);
        chestToPrimary.remove(brokenChestPos);

        if (chestGroup.isEmpty()) {
            // Group is empty, remove it entirely
            chestGroups.remove(primaryPos);

            // Also remove from multiblock connections
            Set<String> connectedMultiblocks = chestToMultiblocks.remove(primaryPos);
            if (connectedMultiblocks != null) {
                for (String multiblockId : connectedMultiblocks) {
                    multiblockChests.get(multiblockId).remove(primaryPos);
                }
            }
        } else {
            // Recalculate primary position using consistent logic
            BlockPos newPrimary = determineConsistentPrimaryPosition(chestGroup);
            if (!newPrimary.equals(primaryPos)) {
                // Update to new primary
                updatePrimaryChestPositionForGroup(primaryPos, newPrimary, chestGroup);
            }

            // Validate connections for all multiblocks after chest removal
            validateChestConnectionsForGroup(newPrimary);
        }
    }

    // NEW METHOD: Validate connections for all multiblocks connected to a chest group
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

        // Remove disconnected multiblocks
        for (String multiblockId : multiblocksToRemove) {
            connectedMultiblocks.remove(multiblockId);
            multiblockChests.get(multiblockId).remove(primaryChestPos);
            Logger.sendMessage("Removed disconnected multiblock " + multiblockId + " from chest group " + primaryChestPos, false);
        }

        if (needsUpdate) {
            setDirty();

            // Send GUI updates
            for (String multiblockId : multiblocksToRemove) {
                sendChestCountSync(multiblockId, multiblockChests.get(multiblockId).size());
            }
        }
    }

    // Helper method for updating primary chest position for a group
    private void updatePrimaryChestPositionForGroup(BlockPos oldPrimary, BlockPos newPrimary, Set<BlockPos> chestGroup) {
        // Update multiblock connections if this chest group was connected to multiblocks
        Set<String> multiblockIds = chestToMultiblocks.get(oldPrimary);
        if (multiblockIds != null) {
            // Update all multiblocks that were connected to the old primary
            for (String multiblockId : multiblockIds) {
                multiblockChests.get(multiblockId).remove(oldPrimary);
                multiblockChests.get(multiblockId).add(newPrimary);
            }
            // Move the multiblock mapping to the new primary
            chestToMultiblocks.remove(oldPrimary);
            chestToMultiblocks.put(newPrimary, multiblockIds);
        }

        // Update chest group tracking
        chestGroups.remove(oldPrimary);
        registerChestGroup(newPrimary, chestGroup);

        setDirty();

        Logger.sendMessage("Updated primary chest from " + oldPrimary + " to " + newPrimary + " for group: " + chestGroup, false);
    }

    private void updatePrimaryChestPosition(BlockPos oldPrimary, BlockPos newPrimary, String multiblockId) {
        Set<BlockPos> chestGroup = chestGroups.get(oldPrimary);
        if (chestGroup == null) return;

        // Update multiblock chest tracking for THIS multiblock
        multiblockChests.get(multiblockId).remove(oldPrimary);
        multiblockChests.get(multiblockId).add(newPrimary);

        // Update chest-to-multiblocks mapping
        Set<String> multiblockIds = chestToMultiblocks.get(oldPrimary);
        if (multiblockIds != null) {
            chestToMultiblocks.remove(oldPrimary);
            chestToMultiblocks.put(newPrimary, multiblockIds);
        }

        // Update chest group tracking
        chestGroups.remove(oldPrimary);
        registerChestGroup(newPrimary, chestGroup);

        setDirty();
    }

    // UPDATED: Improved connection checking for multiple multiblocks
    private boolean isChestConnectedToMultiblock(BlockPos chestPos, String multiblockId) {
        Set<BlockPos> multiblockBlocks = getMultiblockBlocks(multiblockId);

        for (BlockPos blockPos : multiblockBlocks) {
            for (Direction dir : ChestHelper.getValidConnectionDirections(level, blockPos)) {
                if (blockPos.relative(dir).equals(chestPos)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void validateChestConnectionsAfterBlockRemoval(String multiblockId) {
        Set<BlockPos> chestsToCheck = new HashSet<>(multiblockChests.get(multiblockId));
        boolean needsUpdate = false;

        for (BlockPos primaryChestPos : chestsToCheck) {
            if (!isAnyChestInGroupConnectedToMultiblock(primaryChestPos, multiblockId)) {
                // This chest group is no longer connected to THIS multiblock
                multiblockChests.get(multiblockId).remove(primaryChestPos);

                // Remove from chest-to-multiblocks mapping for this specific multiblock
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
            setDirty();
            sendChestCountSync(multiblockId, multiblockChests.get(multiblockId).size());
        }
    }

    private void scanForChestsInMultiblock(String multiblockId, Set<BlockPos> multiblockBlocks) {
        for (BlockPos blockPos : multiblockBlocks) {
            for (Direction dir : ChestHelper.getValidConnectionDirections(level, blockPos)) {
                BlockPos checkPos = blockPos.relative(dir);
                if (ChestHelper.isChest(level, checkPos)) {
                    handleChestNearMultiblock(checkPos, blockPos);
                }
            }
        }
    }

    // Rest of the existing methods remain the same...
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

        // Both must be ship transport blocks
        if (!(state1.getBlock() instanceof ShipItemTransportBlock) ||
                !(state2.getBlock() instanceof ShipItemTransportBlock)) {
            return false;
        }

        Direction facing1 = state1.getValue(ShipItemTransportBlock.FACING);
        Direction facing2 = state2.getValue(ShipItemTransportBlock.FACING);

        // Must face same direction
        if (facing1 != facing2) {
            return false;
        }

        // Check if on same plane based on facing direction
        return areOnSamePlane(pos1, pos2, facing1);
    }

    private boolean areOnSamePlane(BlockPos pos1, BlockPos pos2, Direction facing) {
        switch (facing) {
            case UP:
            case DOWN:
                // Same horizontal plane (same Y level)
                return pos1.getY() == pos2.getY();
            case NORTH:
            case SOUTH:
                // Same vertical plane along Z axis (same Z level)
                return pos1.getZ() == pos2.getZ();
            case EAST:
            case WEST:
                // Same vertical plane along X axis (same X level)
                return pos1.getX() == pos2.getX();
            default:
                return false;
        }
    }

    private void createSingleBlockMultiblock(BlockPos pos) {
        Set<BlockPos> singleBlock = Collections.singleton(pos);
        String newId = createMultiblock(singleBlock);
        updateBlockEntity(pos, newId, singleBlock);
        Logger.sendMessage("Created single-block multiblock " + newId + " at " + pos, false);
    }

    private void joinMultiblock(BlockPos newPos, String targetId) {
        Set<BlockPos> blocks = getMultiblockBlocks(targetId);
        blocks.add(newPos);
        updateMultiblock(targetId, blocks); // This will now automatically send sync
        updateBlockEntity(newPos, targetId, blocks);
        Logger.sendMessage("Added block at " + newPos + " to multiblock " + targetId, false);
    }

    // UPDATED: Simple chest handling for merging
    private void mergeMultiblocks(BlockPos newPos, Set<String> multiblockIds) {
        // Verify all multiblocks have the same facing direction
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

        // Find the largest multiblock to merge into (or just pick the first)
        String mainId = multiblockIds.iterator().next();
        Set<BlockPos> allMergedBlocks = new HashSet<>(getMultiblockBlocks(mainId));
        allMergedBlocks.add(newPos);

        // COLLECT ALL PRIMARY CHEST POSITIONS (HashSet automatically handles duplicates)
        Set<BlockPos> allPrimaryChests = new HashSet<>();
        for (String id : multiblockIds) {
            Set<BlockPos> chests = multiblockChests.get(id);
            if (chests != null) {
                allPrimaryChests.addAll(chests); // These are already primary positions
            }
        }

        // Add all blocks from other multiblocks
        for (String id : multiblockIds) {
            if (!id.equals(mainId)) {
                allMergedBlocks.addAll(getMultiblockBlocks(id));
                removeMultiblock(id); // Use normal removal
            }
        }

        // Update the main multiblock with ALL merged blocks
        updateMultiblock(mainId, allMergedBlocks);

        // ADD ALL UNIQUE PRIMARY CHESTS TO THE MERGED MULTIBLOCK
        for (BlockPos primaryChestPos : allPrimaryChests) {
            if (!multiblockChests.get(mainId).contains(primaryChestPos)) {
                multiblockChests.get(mainId).add(primaryChestPos);
                chestToMultiblocks.computeIfAbsent(primaryChestPos, k -> new HashSet<>()).add(mainId);
            }
        }

        setDirty();

        // CRITICAL FIX: Update ALL block entities in the merged multiblock, not just the new position
        for (BlockPos pos : allMergedBlocks) {
            updateBlockEntity(pos, mainId, allMergedBlocks);
        }

        // SEND GUI UPDATES TO ALL PLAYERS VIEWING ANY OF THE MERGED MULTIBLOCKS
        sendBlockCountSyncToAllMergedViewers(multiblockIds, mainId, allMergedBlocks.size());

        Logger.sendMessage("Merged " + multiblockIds.size() + " multiblocks into " + mainId +
                " with " + allMergedBlocks.size() + " blocks and " + allPrimaryChests.size() + " chests", false);
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

            // Check all 6 directions, but only follow valid connections
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
            // Force immediate update
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

    // NEW METHOD: Send block count sync to viewers of all merged multiblocks
    private void sendBlockCountSyncToAllMergedViewers(Set<String> mergedMultiblockIds, String newMultiblockId, int newBlockCount) {
        if (level instanceof ServerLevel serverLevel) {
            BlockCountSyncPacket syncPacket = new BlockCountSyncPacket(newBlockCount);

            for (ServerPlayer serverPlayer : serverLevel.players()) {
                // Check if player has a menu open for any of the merged multiblocks
                if (serverPlayer.containerMenu instanceof ShipItemTransportMenu menu &&
                        menu.getBlockEntity() != null) {

                    String viewedMultiblockId = menu.getBlockEntity().getMultiblockId();

                    // Send if viewing any of the multiblocks that were merged
                    if (mergedMultiblockIds.contains(viewedMultiblockId) ||
                            newMultiblockId.equals(viewedMultiblockId)) {
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), syncPacket);
                    }
                }
            }
        }
    }

    // NEW METHOD: Send block count sync to viewers of a specific block position
    private void sendBlockCountSyncToViewers(BlockPos blockPos, int blockCount) {
        if (level instanceof ServerLevel serverLevel) {
            BlockCountSyncPacket syncPacket = new BlockCountSyncPacket(blockCount);

            for (ServerPlayer serverPlayer : serverLevel.players()) {
                // Check if player has a menu open for this specific block
                if (serverPlayer.containerMenu instanceof ShipItemTransportMenu menu &&
                        menu.getBlockEntity() != null &&
                        menu.getBlockEntity().getBlockPos().equals(blockPos)) {

                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), syncPacket);
                }
            }
        }
    }

    // UPDATED METHOD: Send block count sync for multiblock updates
    private void sendBlockCountSync(String multiblockId, int blockCount) {
        if (level instanceof ServerLevel serverLevel) {
            BlockCountSyncPacket syncPacket = new BlockCountSyncPacket(blockCount);
            for (ServerPlayer serverPlayer : serverLevel.players()) {
                // Check if player has a menu open for this multiblock
                if (serverPlayer.containerMenu instanceof ShipItemTransportMenu menu &&
                        menu.getBlockEntity() != null) {

                    String viewedMultiblockId = menu.getBlockEntity().getMultiblockId();

                    // Send if viewing the same multiblock
                    if (multiblockId.equals(viewedMultiblockId)) {
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), syncPacket);
                    }
                }
            }
        }
    }

    // ADD CHEST SYNC METHOD
    private void sendChestCountSync(String multiblockId, int chestCount) {
        if (level instanceof ServerLevel serverLevel) {
            ChestCountSyncPacket syncPacket = new ChestCountSyncPacket(chestCount);
            for (ServerPlayer serverPlayer : serverLevel.players()) {
                // Check if player has a menu open for this multiblock
                if (serverPlayer.containerMenu instanceof ShipItemTransportMenu menu &&
                        menu.getBlockEntity() != null) {

                    String viewedMultiblockId = menu.getBlockEntity().getMultiblockId();

                    // Send if viewing the same multiblock
                    if (multiblockId.equals(viewedMultiblockId)) {
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), syncPacket);
                    }
                }
            }
        }
    }
    public void onBlockRemoved(BlockPos removedPos) {
        if (level.isClientSide) return;

        String multiblockId = blockToMultiblock.get(removedPos);
        if (multiblockId == null) return;

        Logger.sendMessage("Processing block removal at " + removedPos + " from multiblock " + multiblockId, false);

        // Remove the block from tracking
        blockToMultiblock.remove(removedPos);

        // Get remaining blocks
        Set<BlockPos> remainingBlocks = getMultiblockBlocks(multiblockId);
        remainingBlocks.remove(removedPos);

        if (remainingBlocks.isEmpty()) {
            // No blocks left - remove the multiblock
            removeMultiblock(multiblockId);
        } else {
            // Check if the multiblock is still connected (respecting direction and plane constraints)
            List<Set<BlockPos>> connectedComponents = findConnectedComponents(remainingBlocks);

            if (connectedComponents.size() == 1) {
                // Still connected - just update the multiblock
                updateMultiblock(multiblockId, remainingBlocks);

                // Check chest connections after block removal
                validateChestConnectionsAfterBlockRemoval(multiblockId);
            } else {
                // Split into multiple multiblocks - PRESERVE THE MODE SETTING
                boolean originalMode = getMultiblockMode(multiblockId);
                removeMultiblock(multiblockId);

                for (Set<BlockPos> component : connectedComponents) {
                    if (!component.isEmpty()) {
                        // Create new multiblock with the ORIGINAL mode setting
                        String newMultiblockId = createMultiblock(component, originalMode);

                        // Send GUI updates for all blocks in this new component
                        for (BlockPos pos : component) {
                            updateBlockEntity(pos, newMultiblockId, component);
                            sendBlockCountSyncToViewers(pos, component.size());
                        }

                        // Scan for chests connected to the new multiblock
                        scanForChestsInMultiblock(newMultiblockId, component);
                    }
                }
                Logger.sendMessage("Split multiblock into " + connectedComponents.size() + " components (preserved mode: " + (originalMode ? "import" : "export") + ")", false);
            }
        }
    }
    public void onBlockPlaced(BlockPos newPos) {
        if (level.isClientSide) return;

        Logger.sendMessage("Processing block placement at " + newPos, false);

        // First, validate that this block isn't already in a multiblock (shouldn't happen, but safety check)
        if (blockToMultiblock.containsKey(newPos)) {
            Logger.sendMessage("WARNING: Block at " + newPos + " already in multiblock " + blockToMultiblock.get(newPos), false);
            return;
        }

        // Find all adjacent multiblocks that can connect (same direction & plane)
        Set<String> adjacentMultiblocks = findAdjacentMultiblocks(newPos);

        if (adjacentMultiblocks.isEmpty()) {
            // No valid neighbors - create single-block multiblock
            createSingleBlockMultiblock(newPos);
        } else if (adjacentMultiblocks.size() == 1) {
            // One valid neighbor - join that multiblock
            String targetId = adjacentMultiblocks.iterator().next();
            joinMultiblock(newPos, targetId);
        } else {
            // Multiple adjacent multiblocks - merge them all if they're compatible
            mergeMultiblocks(newPos, adjacentMultiblocks);
        }
    }
    // Data class for persistent multiblock storage
    private static class MultiblockData {
        public final String id;
        public final Set<BlockPos> blocks;

        public MultiblockData(String id, Set<BlockPos> blocks) {
            this.id = id;
            this.blocks = blocks;
        }
    }
}