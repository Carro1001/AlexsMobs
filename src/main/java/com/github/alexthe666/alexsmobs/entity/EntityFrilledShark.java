package com.github.alexthe666.alexsmobs.entity;

import com.github.alexthe666.alexsmobs.client.particle.AMParticleRegistry;
import com.github.alexthe666.alexsmobs.config.AMConfig;
import com.github.alexthe666.alexsmobs.effect.AMEffectRegistry;
import com.github.alexthe666.alexsmobs.entity.ai.AnimalAISwimBottom;
import com.github.alexthe666.alexsmobs.entity.ai.AquaticMoveController;
import com.github.alexthe666.alexsmobs.entity.ai.EntityAINearestTarget3D;
import com.github.alexthe666.alexsmobs.item.AMItemRegistry;
import com.github.alexthe666.citadel.animation.Animation;
import com.github.alexthe666.citadel.animation.AnimationHandler;
import com.github.alexthe666.citadel.animation.IAnimatedEntity;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.monster.DrownedEntity;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.entity.passive.WaterMobEntity;
import net.minecraft.entity.passive.fish.AbstractGroupFishEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.pathfinding.SwimmerPathNavigator;
import net.minecraft.potion.EffectInstance;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Random;

public class EntityFrilledShark extends WaterMobEntity implements IAnimatedEntity {

    public static final Animation ANIMATION_ATTACK = Animation.create(17);
    private static final DataParameter<Boolean> DEPRESSURIZED = EntityDataManager.createKey(EntityFrilledShark.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> FROM_BUCKET = EntityDataManager.createKey(EntityFrilledShark.class, DataSerializers.BOOLEAN);
    public float prevOnLandProgress;
    public float onLandProgress;
    private int animationTick;
    private Animation currentAnimation;

    protected EntityFrilledShark(EntityType type, World worldIn) {
        super(type, worldIn);
        this.moveController = new AquaticMoveController(this, 1F);
    }

    public static AttributeModifierMap.MutableAttribute bakeAttributes() {
        return MonsterEntity.func_234295_eP_().createMutableAttribute(Attributes.MAX_HEALTH, 20D).createMutableAttribute(Attributes.ARMOR, 0.0D).createMutableAttribute(Attributes.ATTACK_DAMAGE, 3.0D).createMutableAttribute(Attributes.MOVEMENT_SPEED, 0.2F);
    }

    protected void registerData() {
        super.registerData();
        this.dataManager.register(DEPRESSURIZED, false);
        this.dataManager.register(FROM_BUCKET, false);
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FindWaterGoal(this));
        this.goalSelector.addGoal(2, new AIMelee());
        this.goalSelector.addGoal(3, new AnimalAISwimBottom(this, 0.8F, 7));
        this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 0.8F, 3));
        this.goalSelector.addGoal(5, new LookRandomlyGoal(this));
        this.goalSelector.addGoal(6, new FollowBoatGoal(this));
        this.targetSelector.addGoal(1, (new HurtByTargetGoal(this)));
        this.targetSelector.addGoal(2, new EntityAINearestTarget3D(this, SquidEntity.class, 40, false, true, null));
        this.targetSelector.addGoal(2, new EntityAINearestTarget3D(this, EntityMimicOctopus.class, 70, false, true, null));
        this.targetSelector.addGoal(3, new EntityAINearestTarget3D(this, AbstractGroupFishEntity.class, 100, false, true, null));
        this.targetSelector.addGoal(4, new EntityAINearestTarget3D(this, DrownedEntity.class, 4, false, true, null));
    }

    public boolean canSpawn(IWorld worldIn, SpawnReason spawnReasonIn) {
        return AMEntityRegistry.rollSpawn(AMConfig.frilledSharkSpawnRolls, this.getRNG(), spawnReasonIn);
    }

    public static boolean canFrilledSharkSpawn(EntityType<EntityFrilledShark> entityType, IServerWorld iServerWorld, SpawnReason reason, BlockPos pos, Random random) {
        return reason == SpawnReason.SPAWNER || iServerWorld.getBlockState(pos).getMaterial() == Material.WATER && iServerWorld.getBlockState(pos.up()).getMaterial() == Material.WATER;
    }

    private boolean isFromBucket() {
        return this.dataManager.get(FROM_BUCKET);
    }

    public void setFromBucket(boolean p_203706_1_) {
        this.dataManager.set(FROM_BUCKET, p_203706_1_);
    }

    public void writeAdditional(CompoundNBT compound) {
        super.writeAdditional(compound);
        compound.putBoolean("FromBucket", this.isFromBucket());
        compound.putBoolean("Depressurized", this.isDepressurized());
    }

    public boolean preventDespawn() {
        return super.preventDespawn() || this.isFromBucket();
    }

    public boolean canDespawn(double p_213397_1_) {
        return !this.isFromBucket() && !this.hasCustomName();
    }

    public void readAdditional(CompoundNBT compound) {
        super.readAdditional(compound);
        this.setFromBucket(compound.getBoolean("FromBucket"));
        this.setDepressurized(compound.getBoolean("Depressurized"));
    }

    private void doInitialPosing(IWorld world) {
        BlockPos down = this.getPosition();
        while(!world.getFluidState(down).isEmpty() && down.getY() > 1){
            down = down.down();
        }
        this.setPosition(down.getX() + 0.5F, down.getY() + 1, down.getZ() + 0.5F);
    }

    @Nullable
    public ILivingEntityData onInitialSpawn(IServerWorld worldIn, DifficultyInstance difficultyIn, SpawnReason reason, @Nullable ILivingEntityData spawnDataIn, @Nullable CompoundNBT dataTag) {
        if (reason == SpawnReason.NATURAL) {
            doInitialPosing(worldIn);
        }
        return super.onInitialSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
    }

    public boolean isNotColliding(IWorldReader worldIn) {
        return worldIn.checkNoEntityCollision(this);
    }

    public boolean isDepressurized() {
        return this.dataManager.get(DEPRESSURIZED);
    }

    public void setDepressurized(boolean depressurized) {
        this.dataManager.set(DEPRESSURIZED, depressurized);
    }

    protected PathNavigator createNavigator(World worldIn) {
        return new SwimmerPathNavigator(this, worldIn);
    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_COD_DEATH;
    }

    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SoundEvents.ENTITY_COD_HURT;
    }

    protected ItemStack getFishBucket() {
        ItemStack stack = new ItemStack(AMItemRegistry.FRILLED_SHARK_BUCKET);
        CompoundNBT platTag = new CompoundNBT();
        this.writeAdditional(platTag);
        stack.getOrCreateTag().put("FrilledSharkData", platTag);
        if (this.hasCustomName()) {
            stack.setDisplayName(this.getCustomName());
        }
        return stack;
    }

    public ActionResultType getEntityInteractionResult(PlayerEntity p_230254_1_, Hand p_230254_2_) {
        ItemStack itemstack = p_230254_1_.getHeldItem(p_230254_2_);
        if (itemstack.getItem() == Items.WATER_BUCKET && this.isAlive()) {
            this.playSound(SoundEvents.ITEM_BUCKET_FILL_FISH, 1.0F, 1.0F);
            itemstack.shrink(1);
            ItemStack itemstack1 = this.getFishBucket();
            if (!this.world.isRemote) {
                CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayerEntity) p_230254_1_, itemstack1);
            }

            if (itemstack.isEmpty()) {
                p_230254_1_.setHeldItem(p_230254_2_, itemstack1);
            } else if (!p_230254_1_.inventory.addItemStackToInventory(itemstack1)) {
                p_230254_1_.dropItem(itemstack1, false);
            }

            this.remove();
            return ActionResultType.func_233537_a_(this.world.isRemote);
        } else {
            return super.getEntityInteractionResult(p_230254_1_, p_230254_2_);
        }
    }

    public void travel(Vector3d travelVector) {
        if (this.isServerWorld() && this.isInWater()) {
            this.moveRelative(this.getAIMoveSpeed(), travelVector);
            this.move(MoverType.SELF, this.getMotion());
            this.setMotion(this.getMotion().mul(0.9D, 0.6D, 0.9D));
            if (this.getAttackTarget() == null) {
                this.setMotion(this.getMotion().add(0.0D, -0.005D, 0.0D));
            }
        } else {
            super.travel(travelVector);
        }

    }

    @Override
    public void func_233629_a_(LivingEntity p_233629_1_, boolean p_233629_2_) {
        p_233629_1_.prevLimbSwingAmount = p_233629_1_.limbSwingAmount;
        double d0 = p_233629_1_.getPosX() - p_233629_1_.prevPosX;
        double d1 = p_233629_1_.getPosY() - p_233629_1_.prevPosY;
        double d2 = p_233629_1_.getPosZ() - p_233629_1_.prevPosZ;
        float f = MathHelper.sqrt(d0 * d0 + d1 * d1 + d2 * d2) * 8.0F;
        if (f > 1.0F) {
            f = 1.0F;
        }

        p_233629_1_.limbSwingAmount += (f - p_233629_1_.limbSwingAmount) * 0.4F;
        p_233629_1_.limbSwing += p_233629_1_.limbSwingAmount;
    }

    public void tick() {
        super.tick();
        this.prevOnLandProgress = onLandProgress;
        if (!this.isInWater() && onLandProgress < 5F) {
            onLandProgress++;
        }
        if (this.isInWater() && onLandProgress > 0F) {
            onLandProgress--;
        }
        if (this.isInWater()) {
            this.setMotion(this.getMotion().mul(1.0D, 0.8D, 1.0D));
        }
        boolean clear = hasClearance();
        if (this.isDepressurized() && clear) {
            this.setDepressurized(false);
        }
        if (!isDepressurized() && !clear) {
            this.setDepressurized(true);
        }
        if (!world.isRemote && this.getAttackTarget() != null && this.getAnimation() == ANIMATION_ATTACK && this.getAnimationTick() == 12) {
            float f1 = this.rotationYaw * ((float) Math.PI / 180F);
            this.setMotion(this.getMotion().add(-MathHelper.sin(f1) * 0.06F, 0.0D, MathHelper.cos(f1) * 0.06F));
            if (this.getAttackTarget().attackEntityFrom(DamageSource.causeMobDamage(this), (float) this.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue())){
                this.getAttackTarget().addPotionEffect(new EffectInstance(AMEffectRegistry.EXSANGUINATION, 60, 2));
                if(rand.nextInt(15) == 0 && this.getAttackTarget() instanceof SquidEntity){
                    this.entityDropItem(AMItemRegistry.SERRATED_SHARK_TOOTH);
                }
            }

        }
        AnimationHandler.INSTANCE.updateAnimations(this);
    }

    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (source.getTrueSource() instanceof DrownedEntity) {
            amount *= 0.5F;
        }
        return super.attackEntityFrom(source, amount);
    }

    private boolean hasClearance() {
        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();
        for (int l1 = 0; l1 < 10; ++l1) {
            BlockState blockstate = world.getBlockState(blockpos$mutable.setPos(this.getPosX(), this.getPosY() + l1, this.getPosZ()));
            if (!blockstate.getFluidState().isTagged(FluidTags.WATER)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public Animation getAnimation() {
        return currentAnimation;
    }

    @Override
    public void setAnimation(Animation animation) {
        currentAnimation = animation;
    }

    public boolean isKaiju() {
        String s = TextFormatting.getTextWithoutFormattingCodes(this.getName().getString());
        return s != null && (s.toLowerCase().contains("kamata kun") || s.toLowerCase().contains("kamata-kun"));
    }

    @Override
    public Animation[] getAnimations() {
        return new Animation[]{ANIMATION_ATTACK};
    }

    @Override
    public int getAnimationTick() {
        return animationTick;
    }

    @Override
    public void setAnimationTick(int tick) {
        animationTick = tick;
    }

    public boolean attackEntityAsMob(Entity entityIn) {
        if (this.getAnimation() == NO_ANIMATION) {
            this.setAnimation(ANIMATION_ATTACK);
        }
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    public void handleStatusUpdate(byte id) {
        if (id == 68) {
            double d2 = this.rand.nextGaussian() * 0.1D;
            double d0 = this.rand.nextGaussian() * 0.1D;
            double d1 = this.rand.nextGaussian() * 0.1D;
            float radius = this.getWidth() * 0.8F;
            float angle = (0.01745329251F * this.renderYawOffset);
            double extraX = radius * MathHelper.sin((float) (Math.PI + angle));
            double extraZ = radius * MathHelper.cos(angle);
            double x = this.getPosX() + extraX + d0;
            double y = this.getPosY() + this.getHeight() * 0.15F + d1;
            double z = this.getPosZ() + extraZ + d2;
            world.addParticle(AMParticleRegistry.TEETH_GLINT, x, y, z, this.getMotion().x, this.getMotion().y, this.getMotion().z);
        } else {
            super.handleStatusUpdate(id);
        }
    }

    private class AIMelee extends Goal {

        public AIMelee() {
            this.setMutexFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean shouldExecute() {
            return EntityFrilledShark.this.getAttackTarget() != null && EntityFrilledShark.this.getAttackTarget().isAlive();
        }

        public void tick() {
            LivingEntity target = EntityFrilledShark.this.getAttackTarget();
            double speed = 1.0F;
            boolean move = true;
            if (EntityFrilledShark.this.getDistance(target) < 10) {
                if (EntityFrilledShark.this.getDistance(target) < 1.9D) {
                    EntityFrilledShark.this.attackEntityAsMob(target);
                    speed = 0.8F;
                } else {
                    speed = 0.6F;
                    EntityFrilledShark.this.faceEntity(target, 70, 70);
                    if (target instanceof SquidEntity) {
                        Vector3d mouth = EntityFrilledShark.this.getPositionVec();
                        float squidSpeed = 0.07F;
                        ((SquidEntity) target).setMovementVector((float) (mouth.x - target.getPosX()) * squidSpeed, (float) (mouth.y - target.getPosYEye()) * squidSpeed, (float) (mouth.z - target.getPosZ()) * squidSpeed);
                        EntityFrilledShark.this.world.setEntityState(EntityFrilledShark.this, (byte) 68);
                    }
                }
            }
            if (target instanceof DrownedEntity || target instanceof PlayerEntity) {
                speed = 1.0F;
            }
            EntityFrilledShark.this.getNavigator().tryMoveToEntityLiving(target, speed);
        }
    }
}
