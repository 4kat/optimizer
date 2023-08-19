package walksy.optimizer;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.Packet;
import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import walksy.optimizer.command.EnableOptimizerCommand;

import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class WalksyCrystalOptimizerMod implements ClientModInitializer {

    public static final Logger LOGGER = LogManager.getLogger("modid");
    public static MinecraftClient mc;

    /**
     * Listener for minecraft initialization, soon creating a variable containing
     * the Minecraft instance, allowing for several other functions & variables
     * within minecraft to be used. Also initializes the toggling command.
     */

    @Override
    public void onInitializeClient() {
        mc = MinecraftClient.getInstance();
        EnableOptimizerCommand command = new EnableOptimizerCommand();
        command.initializeToggleCommands();
    }

    /**
     * Minecraft automatically places crystals if you are holding down right click,
     * but not as fast as unmodified Walksy does by defualt. I have made it so the
     * optimizer portion of the mod does not work if you are just holding down right click.
     */

    public static int hitCount;

    public static int placeCount;
    public static int breakingBlockTick;

    /**
     * Main crystal placing function, does most of the optimizations.
     */

    public static void useOwnTicks() {
        //if (authorizePlayer(mc.player.getUuid()) == false) {
            //mc.stop();
        //}

        ItemStack mainHandStack = mc.player.getMainHandStack();

        if (mc.options.attackKey.isPressed()) {
            breakingBlockTick++;
        } else breakingBlockTick = 0;

        if (breakingBlockTick > 2)
            return;

        if (!mc.options.useKey.isPressed()) {
            hitCount = 0;
            placeCount = 0;
        }

        if (hitCount == 1)
            return;

        if (lookingAtSaidEntity()) {
            if (mc.options.attackKey.isPressed()) {
                if (hitCount >= 1) {
                    removeSaidEntity().kill();
                    removeSaidEntity().setRemoved(Entity.RemovalReason.KILLED);
                    removeSaidEntity().onRemoved();
                }
                hitCount++;
            }
        }
        if (!mainHandStack.isOf(Items.END_CRYSTAL)) {
            return;
        }

        if (mc.options.useKey.isPressed()
                && (isLookingAt(Blocks.OBSIDIAN, generalLookPos().getBlockPos())
                || isLookingAt(Blocks.BEDROCK, generalLookPos().getBlockPos()))) {
            // Stops from autoplacing
            if (placeCount == 0) {
                sendInteractBlockPacket(generalLookPos().getBlockPos(), generalLookPos().getSide());


                if (canPlaceCrystalServer(generalLookPos().getBlockPos())) {
                    mc.player.swingHand(mc.player.getActiveHand());
                }
            }
            placeCount++;
        }

        if (mc.options.useKey.isPressed()) {
            //ChatHud chat = mc.inGameHud.getChatHud();

            //chat.addMessage(Text.of("PLACING"));
        }
    }


    /**
     * UUIDs, used in the authorizePlayer function to
     * compare the users UUID against authorized UUIDs.
     */

    static UUID rye = UUID.fromString("49d706c1-c716-4e86-a1ea-6b7be2ff2b4f");
    static UUID aqua = UUID.fromString("b84ee574-7540-4fc2-88d1-9883f5441c95");
    static UUID fire = UUID.fromString("3666ab2a-0eec-46db-965d-2cdc2ccaac5f");
    static UUID sheep = UUID.fromString("77de8c1f-e0dc-499d-9987-379ad9f2c8fc");

    //private static boolean authorizePlayer(UUID player) {
    //    if (player != rye/* && player != aqua && player != fire && player != sheep */) {
    //        LOGGER.error("closing, unauthorized use");
    //        return false;
    //    } else {
    //        LOGGER.info("authorized");
    //        return true;
    //    }
    //}

    private static BlockState getBlockState(BlockPos pos) {
        return mc.world.getBlockState(pos);
    }

    private static boolean isLookingAt(Block block, BlockPos pos) {
        return getBlockState(pos).getBlock() == block;
    }


    private static BlockHitResult generalLookPos() {
        Vec3d camPos = mc.player.getEyePos();
        Vec3d clientLookVec = lookVec();
        return mc.world.raycast(new RaycastContext(camPos, camPos.add(clientLookVec.multiply(4.5)), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));

    }

    private static Entity removeSaidEntity() {
        Entity entity = null;
        if (mc.crosshairTarget instanceof EntityHitResult hit) {
            if (hit.getEntity() instanceof EndCrystalEntity crystalEntity) {
                entity = crystalEntity;
            } else if (hit.getEntity() instanceof SlimeEntity slimeEntity) {
                entity = slimeEntity;
            } else if (hit.getEntity() instanceof MagmaCubeEntity magmaCubeEntity) {
                entity = magmaCubeEntity;
            }
        }
        return entity;
    }

    private static boolean lookingAtSaidEntity() {
        return
                mc.crosshairTarget instanceof EntityHitResult entity && (entity.getEntity() instanceof EndCrystalEntity
                        || entity.getEntity() instanceof MagmaCubeEntity
                        || entity.getEntity() instanceof SlimeEntity);
    }

    private static Vec3d lookVec() {
        float f = (float) Math.PI / 180;
        float pi = (float) Math.PI;
        float f1 = MathHelper.cos(-mc.player.getYaw() * f - pi);
        float f2 = MathHelper.sin(-mc.player.getYaw() * f - pi);
        float f3 = -MathHelper.cos(-mc.player.getPitch() * f);
        float f4 = MathHelper.sin(-mc.player.getPitch() * f);
        return new Vec3d(f2 * f3, f4, f1 * f3).normalize();
    }

    private static ActionResult sendInteractBlockPacket(BlockPos pos, Direction dir) {
        Vec3d vec = new Vec3d(pos.getX(), pos.getY(), pos.getZ());
        return setPacket(vec,dir);
    }

    private static ActionResult setPacket(Vec3d vec3d, Direction dir) {
        Vec3i vec3i = new Vec3i((int) vec3d.x, (int) vec3d.y, (int) vec3d.z);
        BlockPos pos = new BlockPos(vec3i);
        BlockHitResult result = new BlockHitResult(vec3d, dir,pos,false);
        return mc.interactionManager.interactBlock(mc.player,mc.player.getActiveHand(),result);
    }

    private static int getPing() {
        if (mc.getNetworkHandler() == null) return 0;

        PlayerListEntry playerListEntry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        if (playerListEntry == null) return 0;
        return playerListEntry.getLatency();
    }

    private static boolean canPlaceCrystalServer(BlockPos block) {
        BlockState blockState = mc.world.getBlockState(block);
        if (!blockState.isOf(Blocks.OBSIDIAN) && !blockState.isOf(Blocks.BEDROCK))
            return false;
        BlockPos blockPos2 = block.up();
        if (!mc.world.isAir(blockPos2))
            return false;
        double d = blockPos2.getX();
        double e = blockPos2.getY();
        double f = blockPos2.getZ();
        List<Entity> list = mc.world.getOtherEntities((Entity)null, new Box(d, e, f, d + 1.0D, e + 2.0D, f + 1.0D));
        return list.isEmpty();
    }
}
