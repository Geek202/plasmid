package xyz.nucleoid.plasmid.mixin.game.event;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nucleoid.plasmid.Plasmid;
import xyz.nucleoid.plasmid.game.ManagedGameSpace;
import xyz.nucleoid.plasmid.game.event.PlaceBlockListener;

@Mixin(BlockItem.class)
public class BlockItemMixin {
    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;Lnet/minecraft/block/BlockState;)Z", at = @At("HEAD"), cancellable = true)
    private void onPlace(ItemPlacementContext context, BlockState state, CallbackInfoReturnable<Boolean> ci) {
        PlayerEntity player = context.getPlayer();
        if (!(player instanceof ServerPlayerEntity)) {
            return;
        }

        ManagedGameSpace gameSpace = ManagedGameSpace.forWorld(player.world);
        if (gameSpace != null) {
            try {
                PlaceBlockListener invoker = gameSpace.invoker(PlaceBlockListener.EVENT);
                ActionResult result = invoker.onPlace(((ServerPlayerEntity) player), context.getBlockPos(), state, context);
                if (result == ActionResult.FAIL) {
                    // notify the client that this action did not go through
                    int slot = context.getHand() == Hand.MAIN_HAND ? player.inventory.selectedSlot : 40;
                    ItemStack stack = context.getStack();
                    ((ServerPlayerEntity) player).networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(-2, slot, stack));

                    ci.setReturnValue(false);
                }
            } catch (Throwable t) {
                Plasmid.LOGGER.error("An unexpected exception occurred while dispatching block place event", t);
                gameSpace.reportError(t, "Placing block");
            }
        }
    }
}
