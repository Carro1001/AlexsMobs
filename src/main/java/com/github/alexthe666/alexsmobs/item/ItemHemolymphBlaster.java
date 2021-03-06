package com.github.alexthe666.alexsmobs.item;

import com.github.alexthe666.alexsmobs.entity.EntityHemolymph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.UseAction;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.HandSide;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.world.World;

import java.util.Random;
import java.util.function.Predicate;

public class ItemHemolymphBlaster extends Item {

    public static final Predicate<ItemStack> HEMOLYMPH = (stack) -> {
        return stack.getItem() == AMItemRegistry.HEMOLYMPH_SAC;
    };

    public ItemHemolymphBlaster(Item.Properties properties) {
        super(properties);
    }

    public int getUseDuration(ItemStack stack) {
        return isUsable(stack) ? Integer.MAX_VALUE : 0;
    }

    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    public static boolean isUsable(ItemStack stack) {
        return stack.getDamage() < stack.getMaxDamage() - 1;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, Hand handIn) {

        ItemStack itemstack = playerIn.getHeldItem(handIn);
        playerIn.setActiveHand(handIn);
        if(!isUsable(itemstack)){
            ItemStack ammo = findAmmo(playerIn);
            boolean flag = playerIn.isCreative();
            if(!ammo.isEmpty()){
                ammo.shrink(1);
                flag = true;
            }
            if(flag){
                itemstack.setDamage(0);
            }
        }
        return ActionResult.resultConsume(itemstack);
    }

    public ItemStack findAmmo(PlayerEntity entity) {
        if(entity.isCreative()){
            return ItemStack.EMPTY;
        }
        for(int i = 0; i < entity.inventory.getSizeInventory(); ++i) {
            ItemStack itemstack1 = entity.inventory.getStackInSlot(i);
            if (HEMOLYMPH.test(itemstack1)) {
                return itemstack1;
            }
        }
        return ItemStack.EMPTY;
    }

    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return !oldStack.isItemEqual(newStack);
    }

    public void onUse(World worldIn, LivingEntity livingEntityIn, ItemStack stack, int count) {
        if(isUsable(stack)) {
            if (count % 2 == 0) {
                boolean left = false;
                if (livingEntityIn.getActiveHand() == Hand.OFF_HAND && livingEntityIn.getPrimaryHand() == HandSide.RIGHT || livingEntityIn.getActiveHand() == Hand.MAIN_HAND && livingEntityIn.getPrimaryHand() == HandSide.LEFT) {
                    left = true;
                }
                EntityHemolymph blood = new EntityHemolymph(worldIn, livingEntityIn, !left);
                Vector3d vector3d = livingEntityIn.getLook(1.0F);
                Vector3f vector3f = new Vector3f(vector3d);
                Random rand = new Random();
                livingEntityIn.playSound(SoundEvents.BLOCK_LAVA_POP,1.0F, 0.5F + (rand.nextFloat() - rand.nextFloat()) * 0.2F);
                blood.shoot((double) vector3f.getX(), (double) vector3f.getY(), (double) vector3f.getZ(), 1F, 3);
                if (!worldIn.isRemote) {
                    worldIn.addEntity(blood);
                }
                stack.damageItem(1, livingEntityIn, (player) -> {
                    player.sendBreakAnimation(livingEntityIn.getActiveHand());
                });
            }
        }else{
            if(livingEntityIn instanceof PlayerEntity){
                ItemStack ammo = findAmmo((PlayerEntity)livingEntityIn);
                boolean flag = ((PlayerEntity) livingEntityIn).isCreative();
                if(!ammo.isEmpty()){
                    ammo.shrink(1);
                    flag = true;
                }
                if(flag){
                    ((PlayerEntity) livingEntityIn).getCooldownTracker().setCooldown(this, 20);
                    stack.setDamage(0);
                }
                livingEntityIn.resetActiveHand();
            }
        }
    }
}
