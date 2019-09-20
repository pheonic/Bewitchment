package com.bewitchment.common.block.tile.entity;

import com.bewitchment.Util;
import com.bewitchment.api.registry.Incense;
import com.bewitchment.api.registry.item.ItemFume;
import com.bewitchment.common.block.tile.entity.util.ModTileEntity;
import com.bewitchment.registry.ModObjects;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFlintAndSteel;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;

import static com.bewitchment.common.block.BlockBrazier.LIT;

public class TileEntityBrazier extends ModTileEntity implements ITickable {
	public ItemStackHandler handler;
	public Incense incense;
	private int litTime;
	
	public TileEntityBrazier() {
		this.handler = new ItemStackHandler(9);
		this.incense = null;
		this.litTime = 0;
	}

	private void getIncense() {
		Incense incense = GameRegistry.findRegistry(Incense.class).getValuesCollection().stream().filter(p -> p.matches(handler)).findFirst().orElse(null);
		if(incense != null) {
			this.incense = incense;
			this.litTime = 0;
		}
		clear(handler);
		markDirty();
	}

	public boolean interact(EntityPlayer player, EnumHand hand) {
		IBlockState state = world.getBlockState(pos);
		if (!state.getValue(LIT)) {
			if (!player.isSneaking()) {
				if (player.getHeldItem(hand).getItem() instanceof ItemFlintAndSteel && !isEmpty(handler)) {
					world.setBlockState(pos, state.withProperty(LIT, true));
					getIncense();
				} else {
					ItemStack itemStack = player.getHeldItem(hand);
					int slot = getFirstEmptySlot(handler);
					if (slot != -1) {
						handler.setStackInSlot(slot, new ItemStack(itemStack.getItem(), 1, itemStack.getItemDamage()));
						player.inventory.decrStackSize(player.inventory.currentItem, 1);
						markDirty();
						world.notifyBlockUpdate(pos, state, state, 3);
						return true;
					}
				}
			} else {
				int slot = getLastNonEmptySlot(handler);
				if (slot != -1) {
					ItemHandlerHelper.giveItemToPlayer(player, handler.getStackInSlot(slot));
					handler.setStackInSlot(slot, ItemStack.EMPTY);
					markDirty();
					world.notifyBlockUpdate(pos, state, state, 3);
					return true;
				}
			}
		} else if (player.getHeldItem(hand).getItem() instanceof ItemSpade) {
			stopBurning();
			return true;
		}
		return false;
	}
	
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		this.writeUpdateTag(tag);
		return super.writeToNBT(tag);
	}
	
	@Override
	public void readFromNBT(NBTTagCompound tag) {
		this.readUpdateTag(tag);
		super.readFromNBT(tag);
	}
	
	@Override
	public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
		if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
		return super.hasCapability(capability, facing);
	}
	
	@Nullable
	@Override
	public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
		if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return (T) this.handler;
		return super.getCapability(capability, facing);
	}
	
	@Override
	public void onDataPacket(NetworkManager manager, SPacketUpdateTileEntity packet) {
		NBTTagCompound tag = packet.getNbtCompound();
		readUpdateTag(tag);
	}
	
	@Override
	public SPacketUpdateTileEntity getUpdatePacket() {
		NBTTagCompound tag = new NBTTagCompound();
		this.writeUpdateTag(tag);
		return new SPacketUpdateTileEntity(pos, getBlockMetadata(), tag);
	}
	
	@Override
	public NBTTagCompound getUpdateTag() {
		NBTTagCompound tag = super.getUpdateTag();
		writeUpdateTag(tag);
		return tag;
	}
	
	private void writeUpdateTag(NBTTagCompound tag) {
		tag.setString("incense", incense == null ? "" : incense.getRegistryName().toString());
		tag.setInteger("time", this.litTime);
		tag.setTag("ItemStackHandler", this.handler.serializeNBT());
	}
	
	private void readUpdateTag(NBTTagCompound tag) {
		this.incense = tag.getString("incense").isEmpty() ? null : GameRegistry.findRegistry(Incense.class).getValue(new ResourceLocation(tag.getString("incense")));
		this.litTime = tag.getInteger("time");
		this.handler.deserializeNBT(tag.getCompoundTag("ItemStackHandler"));
	}

	@Override
	public void update() {
		if(this.incense != null) {
			if (world.getTotalWorldTime() % 20 == 0) {
				this.litTime++;
				if (this.litTime > incense.time) stopBurning();
			}
		}
	}

	private void stopBurning() {
		this.litTime = 0;
		this.incense = null;
		world.setBlockState(pos, world.getBlockState(pos).withProperty(LIT, false));
		markDirty();
	}
}
