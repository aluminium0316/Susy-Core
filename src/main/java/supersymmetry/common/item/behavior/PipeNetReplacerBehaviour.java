package supersymmetry.common.item.behavior;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import org.jetbrains.annotations.NotNull;

import gregtech.api.items.toolitem.IGTTool;
import gregtech.api.items.toolitem.ToolHelper;
import gregtech.api.items.toolitem.behavior.IToolBehavior;
import gregtech.api.pipenet.block.BlockPipe;
import gregtech.api.pipenet.block.ItemBlockPipe;
import gregtech.api.pipenet.block.material.BlockMaterialPipe;
import gregtech.api.pipenet.block.material.IMaterialPipeTile;
import gregtech.api.pipenet.block.material.IMaterialPipeType;
import gregtech.api.pipenet.block.material.TileEntityMaterialPipeBase;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.unification.material.Material;
import gregtech.api.util.input.KeyBind;

public enum PipeNetReplacerBehaviour implements IToolBehavior {

    INSTANCE;

    private static void onActionDone(ItemStack stack, EntityPlayer player, World world, EnumHand hand, int walked) {
        IGTTool tool = ((IGTTool) stack.getItem());
        ToolHelper.damageItem(stack, player, walked);
        SoundEvent sound = tool.getSound();

        if (sound != null) {
            world.playSound(null, player.posX, player.posY, player.posZ,
                    sound, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
        player.swingArm(hand);

        ItemStack offhand = player.getItemStackFromSlot(EntityEquipmentSlot.OFFHAND);
        IItemHandler handler = player.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler == null) return;
        int slots = handler.getSlots();
        for (int i = 0; i < slots; i++) {
            if (handler.getStackInSlot(i).isItemEqual(offhand) && walked > 0) {
                ItemStack extracted = handler.extractItem(i, walked, false);
                walked -= extracted.getCount();
            }
        }
    }

    @Override
    public EnumActionResult onItemUseFirst(@NotNull EntityPlayer player,
                                           @NotNull World world,
                                           @NotNull BlockPos pos,
                                           @NotNull EnumFacing side,
                                           float hitX, float hitY, float hitZ,
                                           @NotNull EnumHand hand) {
        if (KeyBind.TOOL_AOE_CHANGE.isKeyDown(player) && hand == EnumHand.MAIN_HAND) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityMaterialPipeBase<?, ?>pipe) {

                var block = pipe.getPipeBlock();
                ItemStack toolStack = player.getHeldItem(hand);
                ItemStack offhand = player.getItemStackFromSlot(EntityEquipmentSlot.OFFHAND);

                if (!(offhand.getItem() instanceof ItemBlockPipe<?, ?>offhandPipe)) return EnumActionResult.FAIL;

                BlockPipe<?, ?, ?> replacementBlock = (BlockPipe<?, ?, ?>) (offhandPipe).getBlock();

                if (!(replacementBlock instanceof BlockMaterialPipe<?, ?, ?>replacement)) return EnumActionResult.FAIL;

                IMaterialPipeType<?> replacementType = replacement.getItemPipeType(null);

                if (!pipe.getPipeTypeClass().getName().equals(replacementBlock.getPipeTypeClass().getName()))
                    return EnumActionResult.FAIL;

                Material material = pipe.getPipeMaterial();
                IMaterialPipeType<?> type = pipe.getPipeType();

                if (material == replacement.getItemMaterial(offhand) && type == replacementType)
                    return EnumActionResult.FAIL;

                int maxWalks = 0;
                IItemHandler handler = player.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
                if (handler == null) return EnumActionResult.FAIL;
                int slots = handler.getSlots();
                for (int i = 0; i < slots; i++) {
                    ItemStack extracted = handler.extractItem(i, 0x7fffffff, true);
                    if (extracted.isItemEqual(offhand)) {
                        maxWalks += extracted.getCount();
                    }
                }

                if (maxWalks <= 0) return EnumActionResult.FAIL;

                final int[] replaceable = { maxWalks };

                int walkedBlocks = PipeOperationWalker.collectPipeNet(world, pos, pipe, null, new ITraverseOption() {

                    // Like FIND_ALL_CONNECTED but with same materials;
                    @Override
                    public List<EnumFacing> findNext(EnumFacing from, IPipeTile<?, ?> pipe) {
                        List<EnumFacing> ret = new ArrayList<>(5);
                        for (EnumFacing facing : EnumFacing.values()) {
                            if (facing == from) continue;
                            if (pipe.isConnected(facing) && pipe instanceof IMaterialPipeTile<?, ?>materialPipe &&
                                    materialPipe.getPipeMaterial() == material && materialPipe.getPipeType() == type) {
                                ret.add(facing);
                            }
                        }
                        return ret;
                    }

                    public void replaceBlock(TileEntityMaterialPipeBase<?, ?> pipe, EnumFacing from) {
                        if (replaceable[0] <= 0) return;
                        world.removeTileEntity(pipe.getPos());
                        offhandPipe.placeBlockAt(offhand, player, world, pipe.getPos(), from, 0, 0, 0,
                                replacementBlock.getStateForPlacement(world, pipe.getPos(), from, 0, 0, 0, 0, player,
                                        EnumHand.OFF_HAND));
                        if (!(world.getTileEntity(pipe.getPos()) instanceof TileEntityMaterialPipeBase replacementPipe))
                            return;
                        // if (!(replacementBlock.createNewTileEntity(pipe.supportsTicking()) instanceof
                        // TileEntityMaterialPipeBase replacementPipe)) return;
                        for (EnumFacing side : EnumFacing.VALUES) {
                            if (pipe.isConnected(side)) {
                                replacementPipe.setConnection(side, true, false);
                            }
                        }
                        replacementPipe.transferDataFrom(pipe); // covers, packets, frames
                        replacementPipe.setPaintingColor(-1);
                        replacementPipe.setPipeData(replacement, (Enum<?>) replacementType,
                                replacement.getItemMaterial(offhand));
                        // replacementPipe.blockedConnections = pipe.getBlockedConnections();
                        for (EnumFacing side : EnumFacing.VALUES) {
                            if (pipe.isFaceBlocked(side)) {
                                replacementPipe.setFaceBlocked(side, true);
                            }
                        }
                        replacementPipe.notifyBlockUpdate();
                        replacementPipe.markAsDirty();
                        replacementPipe.scheduleChunkForRenderUpdate();
                        replaceable[0] -= 1;
                    }

                    @Override
                    public void operate(EnumFacing from, IPipeTile<?, ?> self, IPipeTile<?, ?> other, boolean reverse) {
                        if (world.getTileEntity(self.pos()) instanceof TileEntityMaterialPipeBase<?, ?>pipe &&
                                pipe.getPipeMaterial() == material && pipe.getPipeType() == type)
                            replaceBlock(pipe, from.getOpposite());
                        if (world.getTileEntity(other.pos()) instanceof TileEntityMaterialPipeBase<?, ?>pipe &&
                                pipe.getPipeMaterial() == material && pipe.getPipeType() == type)
                            replaceBlock(pipe, from);
                    }
                }, maxWalks);

                onActionDone(toolStack, player, world, hand, maxWalks - replaceable[0]);
                if (!world.isRemote) {
                    ItemStack drops = pipe.getPipeBlock().getDropItem((IPipeTile) pipe);
                    drops.setCount(maxWalks - replaceable[0]);
                    InventoryHelper.spawnItemStack(world, player.posX, player.posY, player.posZ, drops);
                }

                return EnumActionResult.SUCCESS;
            }
        }
        return EnumActionResult.PASS;
    }

    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemStack, List<String> lines) {
        lines.add(I18n.format("item.susy.tool.behavior.pipeliner"));
    }
}
