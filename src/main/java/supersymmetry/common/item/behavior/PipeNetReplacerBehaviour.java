package supersymmetry.common.item.behavior;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import gregtech.api.cover.CoverRayTracer;
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
import gregtech.common.pipelike.cable.Insulation;
import gregtech.common.pipelike.fluidpipe.tile.TileEntityFluidPipe;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

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
    }

    @Override
    public EnumActionResult onItemUseFirst(@NotNull EntityPlayer player,
                                           @NotNull World world,
                                           @NotNull BlockPos pos,
                                           @NotNull EnumFacing side,
                                           float hitX, float hitY, float hitZ,
                                           @NotNull EnumHand hand) {
        if (KeyBind.TOOL_AOE_CHANGE.isKeyDown(player)) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityMaterialPipeBase<?, ?> pipe) {

                var block = pipe.getPipeBlock();
                ItemStack toolStack = player.getHeldItem(hand);
                ItemStack offhand = player.getItemStackFromSlot(EntityEquipmentSlot.OFFHAND);

                if (!(offhand.getItem() instanceof ItemBlockPipe<?, ?> offhandPipe)) return EnumActionResult.FAIL;

                BlockPipe<?, ?, ?> replacementBlock = (BlockPipe<?, ?, ?>) (offhandPipe).getBlock();

                if (!(replacementBlock instanceof BlockMaterialPipe<?, ?, ?> replacement)) return EnumActionResult.FAIL;

                IMaterialPipeType<?> replacementType = replacement.getItemPipeType(null);

                if (!pipe.getPipeTypeClass().getName().equals(replacementBlock.getPipeTypeClass().getName())) return EnumActionResult.FAIL;


//                CuboidRayTraceResult rayTraceResult = block.getServerCollisionRayTrace(player, pos, world);
//
//                if (rayTraceResult == null) return EnumActionResult.FAIL;
//
//                EnumFacing gridSide = CoverRayTracer.traceCoverSide(rayTraceResult);
//
//                if (gridSide == null) return EnumActionResult.FAIL;

                NBTTagCompound toolTag = ToolHelper.getToolTag(toolStack);
                int maxWalks = toolTag.getInteger(ToolHelper.MAX_DURABILITY_KEY) -
                        toolTag.getInteger(ToolHelper.DURABILITY_KEY);

                if (maxWalks <= 0) return EnumActionResult.FAIL;

                Material material = pipe.getPipeMaterial();

                // TODO: stackoverflow
                int walkedBlocks = PipeOperationWalker.collectPipeNet(world, pos, pipe, null, new ITraverseOption() {
                    @Override
                    public List<EnumFacing> findNext(EnumFacing from, IPipeTile<?, ?> pipe) {
                        List<EnumFacing> ret = new ArrayList<>(5);
                        for (EnumFacing facing : EnumFacing.values()) {
                            if (facing == from) continue;
                            if (pipe.isConnected(facing) && pipe instanceof IMaterialPipeTile<?, ?> materialPipe && materialPipe.getPipeMaterial() != material) {
                                ret.add(facing);
                            }
                        }
                        return ret;
                    }

                    public void replaceBlock(TileEntityMaterialPipeBase<?, ?> pipe, EnumFacing from) {
                        world.removeTileEntity(pipe.getPos());
                        offhandPipe.placeBlockAt(offhand, player, world, pipe.getPos(), from, 0, 0, 0, replacementBlock.getStateForPlacement(world, pipe.getPos(), from, 0, 0, 0, 0, player, EnumHand.OFF_HAND));
                        if (!(world.getTileEntity(pipe.getPos()) instanceof TileEntityMaterialPipeBase replacementPipe)) return;
//                        if (!(replacementBlock.createNewTileEntity(pipe.supportsTicking()) instanceof TileEntityMaterialPipeBase replacementPipe)) return;
                        replacementPipe.transferDataFrom(pipe);
                        replacementPipe.setPaintingColor(-1);
                        replacementPipe.setPipeData(replacement, (Enum<?>) replacementType, replacement.getItemMaterial(offhand));
//                        replacementPipe.blockedConnections = pipe.getBlockedConnections();
                        for (EnumFacing side : EnumFacing.VALUES) {
                            if (pipe.isFaceBlocked(side)) {
                                replacementPipe.setFaceBlocked(side, true);
                            }
                        }
                        replacementPipe.notifyBlockUpdate();
                        replacementPipe.scheduleChunkForRenderUpdate();
                    }

                    @Override
                    public void operate(EnumFacing from, IPipeTile<?, ?> self, IPipeTile<?, ?> other, boolean reverse) {
                            if (self instanceof TileEntityMaterialPipeBase<?, ?> pipe)
                                replaceBlock(pipe, from);
                            if (other instanceof TileEntityMaterialPipeBase<?, ?> pipe)
                                replaceBlock(pipe, from.getOpposite());
                    }
                }, maxWalks);

                onActionDone(toolStack, player, world, hand, MathHelper.ceil(MathHelper.sqrt(walkedBlocks)));

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
