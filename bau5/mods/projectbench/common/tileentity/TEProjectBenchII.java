package bau5.mods.projectbench.common.tileentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.packet.Packet;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.oredict.OreDictionary;
import bau5.mods.projectbench.common.packets.PBPacketManager;
import bau5.mods.projectbench.common.recipes.PBRecipeSorter;
import bau5.mods.projectbench.common.recipes.RecipeCrafter;
import bau5.mods.projectbench.common.recipes.RecipeManager;
import bau5.mods.projectbench.common.recipes.RecipeManager.RecipeItem;
import cpw.mods.fml.common.network.PacketDispatcher;

/**
 * 
 * TEProjectBenchII
 *
 * @author _bau5
 * @license Lesser GNU Public License v3 (http://www.gnu.org/licenses/lgpl.html)
 * 
 */

public class TEProjectBenchII extends TileEntity implements IInventory, ISidedInventory
{
	public IInventory craftSupplyMatrix;
	private ItemStack[] inv;
	private boolean updateNeeded = false;
	private boolean shouldConsolidate = true;
	public boolean initSlots = false;
	public int inventoryStart;
	public int supplyMatrixSize;
	private byte directionFacing = 0;
	
	private int sync =  0;
	
	private ItemStack[] consolidatedStacks = null;
	private ArrayList<ItemStack> listToDisplay = new ArrayList();
	private ArrayList<RecipeItem> recipeList = new ArrayList<RecipeItem>();
	private RecipeCrafter theCrafter = new RecipeCrafter();
	private HashMap<ItemStack, ItemStack[]> recipeMap = null;
	private boolean networkIsModifying = false;;
	
	@Override
	public void onInventoryChanged() {
		if(updateNeeded){
			disperseListAcrossMatrix();
			if(!networkIsModifying && !worldObj.isRemote)
				PacketDispatcher.sendPacketToAllAround(xCoord, yCoord, zCoord, 15D, worldObj.getWorldInfo().getVanillaDimension(), getDescriptionPacket());
		}
		super.onInventoryChanged();
	}
	public TEProjectBenchII(){
		theCrafter.addTPBReference(this);
		craftSupplyMatrix = new InventoryBasic("pbIICraftingSupply", true, 18);
		inv = new ItemStack[45];
		inventoryStart = 27;
		supplyMatrixSize = 18;
	}
	
	@Override
	public void updateEntity() {
		super.updateEntity();
		sync++;
		if(sync == 20 && initSlots && !worldObj.isRemote){
			sendListClientSide();
		}
	}

	public void disperseListAcrossMatrix(){
		updateNeeded = false;
		ItemStack stack = null;
		if(listToDisplay.size() == 0)
			return;
		for(int i = 0; i < inventoryStart; i++){
			stack = (i < listToDisplay.size()) ? listToDisplay.get(i) : null;
			inv[i] = stack;
		}
	}
	
	public void scrambleMatrix(){
		ArrayList<ItemStack> temp = new ArrayList();
		if(listToDisplay.isEmpty())
			return;
		int targetSize = listToDisplay.size();
		int counter = 0;
		ItemStack stack;
		for(int i = 0; i < targetSize; i++){
			if(i == 0)
				stack = listToDisplay.get(targetSize / 2);
			else{
				if(i % 2 == 0){
					stack = listToDisplay.get(targetSize / 2 + counter); 
				}else{
					stack = listToDisplay.get(targetSize / 2 - counter++);
				}
				temp.add(stack);
			}
		}
		temp.add(listToDisplay.get(0));
		setListForDisplay(temp);
		updateRecipeMap();
	}
	
	public void removeResultFromDisplay(ItemStack resultToRemove){
		if(resultToRemove == null)	
				return;
		for(int i = 0; i < listToDisplay.size(); i++){
			if(listToDisplay.get(i).getItem().equals(resultToRemove.getItem()))
				listToDisplay.remove(i);
		}
		updateNeeded = true;
		ItemStack stack;
		for(int i = 0; i < inventoryStart; i++){
			stack = (i < listToDisplay.size()) ? listToDisplay.get(i) : null;
			inv[i] = stack;
		}
		updateRecipeMap();
	}
	
	public void clearMatrix(){
		for(int i = 0; i < inventoryStart; i++){
			inv[i] = null;
		}
	}
	
	public void setListForDisplay(ArrayList<ItemStack> list){
		listToDisplay = list;
		updateNeeded = true;
//		if(worldObj != null)
//			System.out.printf("List is being set for %s with %d entries.\n", worldObj.getClass().getSimpleName(), list.size());
	}

	public void setRecipeMap(HashMap<ItemStack, ItemStack[]> possibleRecipesMap) {
		recipeMap  = possibleRecipesMap;
	}
	public void updateRecipeMap(){
		if(!worldObj.isRemote)
			return;
		if(recipeMap == null)
			recipeMap = new HashMap<ItemStack, ItemStack[]>();
		HashMap<ItemStack, ItemStack[]> tempMap = new HashMap<ItemStack, ItemStack[]>();
		Set<Map.Entry<ItemStack, ItemStack[]>> entrySet = recipeMap.entrySet();
		for(ItemStack stack : listToDisplay){
			for(Entry ent : entrySet){
				if(OreDictionary.itemMatches((ItemStack)ent.getKey(), stack, false)){
					tempMap.put(stack, (ItemStack[])ent.getValue());
				}
			}
		}
		recipeMap = tempMap;
	}

	public ItemStack[] getStacksForResult(ItemStack stackInSlot) {
		if(recipeMap == null)
			return null;
		Set<Map.Entry<ItemStack, ItemStack[]>> entrySet = recipeMap.entrySet();
		for(Entry ent : entrySet){
			if(OreDictionary.itemMatches((ItemStack)ent.getKey(), stackInSlot, false)){
				return (ItemStack[]) ent.getValue();
			}
		}
		return null;
	}

	
	public ArrayList<ItemStack> getDisplayList(){
		return listToDisplay;
	}
	
	public void updateOutputRecipes(){
		recipeMap = RecipeManager.instance().getPossibleRecipesMap(consolidateItemStacks(true));
		if(recipeMap.size() == 0){
			setListForDisplay(new ArrayList<ItemStack>());
			return;
		}
		ArrayList<ItemStack> tempList = new ArrayList<ItemStack>();
		for(Entry ent : recipeMap.entrySet()){
			if(ent != null && ent.getKey() instanceof ItemStack)
				tempList.add((ItemStack)ent.getKey());
		}
		Collections.sort(tempList, new PBRecipeSorter());
		setListForDisplay(tempList);
		updateNeeded = true;
	}
	
	public void checkListAndInventory(ItemStack stack){
		String str = (worldObj.isRemote) ? "server" : "client";
		boolean flag = false, flag1 = false;
		if(stack == null)
			return;
		for(ItemStack is : inv){
			if(is == null)
				continue;
			if(is.getItem().equals(stack.getItem()))
				flag = true;
		}
		for(ItemStack si : listToDisplay){
			if(si.getItem().equals(stack.getItem()))
				flag1 = true;
		}
		if(flag)
			RecipeManager.print("" +stack +" appears in " +str +" inventory.");
		else
			RecipeManager.print("" +stack +" doesn't appear in " +str +" inventory.");
		if(flag1)
			RecipeManager.print("" +stack +" is in " +str +" list.");
		else
			RecipeManager.print("" +stack +" isn't in " +str +" list.");
	}

	public int consumeItems(ItemStack[] items, ItemStack resultStack, boolean max) {
		theCrafter.addInventoryReference(createInventoryReference());
		return theCrafter.consumeItems(items, consolidateItemStacks(false), resultStack, max);
	}
	
	public ItemStack[] consolidateItemStacks(boolean override){
		if(!override && !shouldConsolidate){
			return consolidatedStacks;
		}else{
			ItemStack[] stackArr = new ItemStack[supplyMatrixSize];
			for(int i = 0; i < supplyMatrixSize; i++){
				stackArr[i] = ItemStack.copyItemStack(getStackInSlot(i + inventoryStart));
			}
			return theCrafter.consolidateItemStacks(stackArr);
		}
	}
	
	public ItemStack[] createInventoryReference(){
		ItemStack[]	newArr = new ItemStack[supplyMatrixSize];
		for(int i = 0; i < supplyMatrixSize; i++){
			newArr[i] = inv[i + inventoryStart];
		}
		return newArr;
	}
	public void sendListClientSide(){
		PacketDispatcher.sendPacketToAllAround(xCoord, yCoord, zCoord, 15D, worldObj.provider.dimensionId, getDescriptionPacket());
	}
	
	@Override
	public Packet getDescriptionPacket() {
		return PBPacketManager.getMkIIPacket(this);
	}
	public int[] getInputStacksForPacket()
	{
		int[] craftingStacks = new int[(supplyMatrixSize * 3)];
		int index = 0;
		for(int i = 0; i < supplyMatrixSize; i++)
		{
			if(inv[i +inventoryStart] != null)
			{
				craftingStacks[index++] = inv[i +inventoryStart].itemID;
				craftingStacks[index++] = inv[i +inventoryStart].stackSize;
				craftingStacks[index++] = inv[i +inventoryStart].getItemDamage();
			} else
			{
				craftingStacks[index++] = 0;
				craftingStacks[index++] = 0;
				craftingStacks[index++] = 0;
			}
		}
		return craftingStacks;
	}
	public void buildResultFromPacket(int[] stacksData)
	{
		if(stacksData == null)
		{
			return;
		}
		if(stacksData.length != 0)
		{
			int index = 0;
			for(int i = 0; i < supplyMatrixSize; i++)
			{
				if(stacksData[index + 1] != 0)
				{
					ItemStack stack = new ItemStack(stacksData[index], stacksData[index+1], stacksData[index+2]);
					inv[i +inventoryStart] = stack;
				}
				else
				{
					inv[i +inventoryStart] = null;
				}
				index = index + 3;
			}
		}
		updateOutputRecipes();
	}
	
	@Override
	public int getSizeInventory() {
		return inv.length;
	}

	@Override
	public ItemStack getStackInSlot(int i) {
		ItemStack stack;
		if(i >= 0 && i < inventoryStart)
			if(listToDisplay.size() > i)
				stack = listToDisplay.get(i).copy();
			else return null;
		else
			stack = inv[i];
		return stack;
	}

	public ItemStack[] getInventory(){
		return inv.clone();
	}
	
	@Override
	public ItemStack decrStackSize(int slot, int amount) {
		ItemStack stack = inv[slot];
		if(slot < 27)
			return stack;
		if(stack != null)
		{
			if(stack.stackSize <= amount)
			{
				setInventorySlotContents(slot, null);
			} else
			{
				stack = stack.splitStack(amount);
				if(stack.stackSize == 0) 
				{
					setInventorySlotContents(slot, null);
				}
			}
		}
		shouldConsolidate = true;
		return stack;
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int slot) {
		ItemStack stack = getStackInSlot(slot);
		if(stack != null)
		{
			setInventorySlotContents(slot, null);
		}
		return stack;
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack stack) {		
		shouldConsolidate = true;
		inv[slot] = stack;
		if(stack != null && stack.stackSize > getInventoryStackLimit())
		{
			stack.stackSize = getInventoryStackLimit();
		}
		if(slot >= 27 && slot < 45 && !initSlots)
//			updateRecipeMap();
			updateOutputRecipes();
	}
	
	@Override
	public String getInvName() {
		return "Project Bench Mk. II";
	}

	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer player) 
	{
		return worldObj.getBlockTileEntity(xCoord, yCoord, zCoord) == this &&
				player.getDistanceSq(xCoord +0.5, yCoord +0.5, zCoord +0.5) < 64;
	}

	@Override
	public void openChest() { }

	@Override
	public void closeChest() { }


	@Override
	public void readFromNBT(NBTTagCompound tagCompound)
	{
		super.readFromNBT(tagCompound);
		setDirection(tagCompound.getByte("facing"));
		NBTTagList tagList = tagCompound.getTagList("Inventory");
		for(int i = 0; i < tagList.tagCount(); i++)
		{
			NBTTagCompound tag = (NBTTagCompound) tagList.tagAt(i);
			byte slot = tag.getByte("Slot");
			if(slot >= 0 && slot < inv.length)
			{
				inv[slot] = ItemStack.loadItemStackFromNBT(tag);
			}
		}
	}
	@Override
	public void writeToNBT(NBTTagCompound tagCompound)
	{
		super.writeToNBT(tagCompound);
		
		tagCompound.setByte("facing", directionFacing);
		NBTTagList itemList = new NBTTagList();	
		
		for(int i = 0; i < inv.length; i++)
		{
			ItemStack stack = inv[i];
			if(stack != null)
			{
				NBTTagCompound tag = new NBTTagCompound();	
				tag.setByte("Slot", (byte)i);
				stack.writeToNBT(tag);
				itemList.appendTag(tag);
			}
		}
		tagCompound.setTag("Inventory", itemList);
	}
	@Override
	public boolean isInvNameLocalized() {
		return false;
	}
	public void setDirection(byte dir) {
		directionFacing = dir;
	}
	public byte getDirection(){
		return directionFacing;
	}
	public void setNetworkModifying(boolean b) {
		networkIsModifying  = b;
	}
	@Override
	public int[] getAccessibleSlotsFromSide(int var1) {
		int[] chestArea = new int[18];
		for(int i = 0; i < chestArea.length; i++)
			chestArea[i] = 27+i;
		switch(var1){
		default: return chestArea;
		}
	}
	@Override
	public boolean canInsertItem(int i, ItemStack itemstack, int j) {
		if(i > 27)
			return true;
		return false;
	}
	@Override
	public boolean canExtractItem(int i, ItemStack itemstack, int j) {
		if(i > 27)
			return true;
		return false;
	}
	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemstack) {
		if(i > 27)
			return true;
		return false;
	}
}