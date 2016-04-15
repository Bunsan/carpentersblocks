package com.carpentersblocks.tileentity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import com.carpentersblocks.block.BlockCoverable;
import com.carpentersblocks.util.BlockProperties;
import com.carpentersblocks.util.handler.DesignHandler;
import com.carpentersblocks.util.protection.IProtected;
import com.carpentersblocks.util.protection.ProtectedObject;
import com.carpentersblocks.util.registry.FeatureRegistry;

public class TEBase extends TileEntity implements IProtected {

    protected static final String TAG_ATTR          = "cbAttribute";
    protected static final String TAG_ATTR_LIST     = "cbAttrList";
    protected static final String TAG_METADATA      = "cbMetadata";
    protected static final String TAG_OWNER         = "cbOwner";
    protected static final String TAG_CHISEL_DESIGN = "cbChiselDesign";
    protected static final String TAG_DESIGN        = "cbDesign";

    public static final byte[]  ATTR_COVER        = {  0,  1,  2,  3,  4,  5,  6 };
    public static final byte[]  ATTR_DYE          = {  7,  8,  9, 10, 11, 12, 13 };
    public static final byte[]  ATTR_OVERLAY      = { 14, 15, 16, 17, 18, 19, 20 };
    public static final byte    ATTR_ILLUMINATOR  = 21;
    public static final byte    ATTR_PLANT        = 22;
    public static final byte    ATTR_SOIL         = 23;
    public static final byte    ATTR_FERTILIZER   = 24;
    public static final byte    ATTR_UPGRADE      = 25;

    /** Map holding all block attributes. */
    protected Map<Byte, ItemStack> cbAttrMap = new HashMap<Byte, ItemStack>();

    /** Chisel design for each side and base block. */
    protected String[] cbChiselDesign = { "", "", "", "", "", "", "" };

    /** Holds specific block information like facing, states, etc. */
    protected int cbMetadata;

    /** Design name. */
    protected String cbDesign = "";

    /** Owner of tile entity. */
    protected String cbOwner = "";

    /** Indicates lighting calculations are underway. **/
    protected static boolean calcLighting = false;

    /** Holds last stored metadata. **/
    private int tempMetadata;

    /** The most recent light value of block. **/
    public int lightValue;

    
    /** Comment **/
    @Override
    public void readFromNBT(NBTTagCompound nbt)
    {
        super.readFromNBT(nbt);
        // These next lines are to enforce the cbAttrList, id short value to be what is represented in cbAttrList texture string.  This allows for compatibility with adding portability to worlds that may have different block id mappings.   
        NBTTagList nbttaglist1 = nbt.getTagList(TAG_ATTR_LIST, 10); //sets up the nbt tag "cbAttrList" to be read and written to.
        for (int idx = 0; idx < nbttaglist1.tagCount(); ++idx) {
            NBTTagCompound nbt1a = nbttaglist1.getCompoundTagAt(idx); 
            
            if (nbt1a.getShort("cbAttribute") < 7 ){ //if cbAttribute is 0 through 6 then it is not one of the special textures and its id isn't a block or glowstone dust. 
            		
            	if (Block.blockRegistry.containsId(nbt1a.getShort("id"))){ // if the id can't be cast as a block, then it skips this
            			if ((Block.blockRegistry.getNameForObject(Block.getBlockById((int)nbt1a.getShort("id")))) != (nbt1a.getString("Texture"))&& (nbt1a.getString("Texture"))!= ""){
        	        	//compares the registry name for the id number stored in cbAttrList with the texture name stored in cbAttrList.
            				if (Block.blockRegistry.containsKey(nbt1a.getString("Texture"))){ //if the texture name isn't a known block
            					nbt1a.setShort("id",(short)Block.getIdFromBlock((Block.getBlockFromName(nbt1a.getString("Texture")))));
            					//sets the id number to the id number of the block represented by the Texture tag in cbAttrList.
            				}
            				else {
            					nbt.removeTag(TAG_ATTR_LIST); //if the Texture block represented in the tag isn't a known block, then it clears the all the texture data in the block.
            					System.out.println("Texture Block Does Not Exist!  Removing all textures from block");
            				}
        	

            			}
            	}
            	else{
            		if (Block.blockRegistry.containsKey(nbt1a.getString("Texture"))){ // if the texture is a known block but the id isn't.
            			nbt1a.setShort("id",(short)Block.getIdFromBlock((Block.getBlockFromName(nbt1a.getString("Texture")))));
            			//sets the id to the known texture block's id.  
            		}
            		else {
            			nbt.removeTag(TAG_ATTR_LIST); //if the id number isn't a block and the texture isn't a known block then it clears all the texture data cbAttList.
            			System.out.println("Texture Block Does Not Exist! Removing all textures from block");
            		}
            	
            	}
            }
            else{
            	//  This means that the texture is one of the "special texture" such as grass, wheat and mycellium tops.  It also includes glowstone dust (cbAttribute=21)
            }
        }
        cbAttrMap.clear();
        if (nbt.hasKey("owner")) {        	    	
            MigrationHelper.updateMappingsOnRead(this, nbt);
        } else {
            NBTTagList nbttaglist = nbt.getTagList(TAG_ATTR_LIST, 10);
            for (int idx = 0; idx < nbttaglist.tagCount(); ++idx) {
                NBTTagCompound nbt1 = nbttaglist.getCompoundTagAt(idx);
                ItemStack tempStack = ItemStack.loadItemStackFromNBT(nbt1);
                tempStack.stackSize = 1; // All ItemStacks pre-3.2.7 DEV R3 stored original stack sizes, reduce them here.
                byte attrId = (byte) (nbt1.getByte(TAG_ATTR) & 255);
                cbAttrMap.put(attrId, tempStack);
            }

            for (int idx = 0; idx < 7; ++idx) {
                cbChiselDesign[idx] = nbt.getString(TAG_CHISEL_DESIGN + "_" + idx);
            }

            // Handle 3.3.7 structure changes
            convertDataToInt(nbt);

            cbDesign = nbt.getString(TAG_DESIGN);
            cbOwner = nbt.getString(TAG_OWNER);
        }

        // Block either loaded or changed, update lighting and render state
        updateWorldAndLighting();
    }

    /**
     * Handles data conversion from short to int for update 3.3.7.
     *
     * @param nbt the {@link NBTTagCompound}
     * @return <code>true</code> if data was converted
     */
    private boolean convertDataToInt(NBTTagCompound nbt)
    {
        // 3.3.7 DEV converted cbMetadata to integer
        if (nbt.getTag(TAG_METADATA) instanceof NBTTagShort) {
            cbMetadata = nbt.getShort(TAG_METADATA);
            return true;
        } else {
            cbMetadata = nbt.getInteger(TAG_METADATA);
            return false;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt)
    {
        super.writeToNBT(nbt);

        NBTTagList itemstack_list = new NBTTagList();
        Iterator iterator = cbAttrMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry entry = (Map.Entry) iterator.next();
            NBTTagCompound nbt1 = new NBTTagCompound();
            nbt1.setByte(TAG_ATTR, (Byte) entry.getKey());
            ((ItemStack)entry.getValue()).writeToNBT(nbt1);
            itemstack_list.appendTag(nbt1);
            if ((Block.blockRegistry.getNameForObject(Block.getBlockById((int)nbt1.getShort("id"))) != (nbt1.getString("Texture")))&& ((nbt1.getString("Texture"))!= "")&& nbt1.getShort("cbAttribute")!= 20){
            	//if the id and the texture don't match and "Texture" has been initialized as something, then it sets the id to what the "texture" represents.
            	nbt1.setShort("id", (short)Block.getIdFromBlock((Block.getBlockFromName(nbt1.getString("Texture")))));
            }

            if (((nbt1.getString("Texture")) == "")&& (nbt1.getShort("cbAttribute")!= 20)){ //if the "Texture" is empty, then it sets the "Texture" to represent what the id is.
            	nbt1.setString("Texture",Block.blockRegistry.getNameForObject(Block.getBlockFromItem(((ItemStack)entry.getValue()).getItem())));
            	}
            	//unless bad values are hacked in through editing nbt in game, this part should stay stable since the rest of the program doesn't allow non blocks, 
            	//with only a few exceptions to be placed into a block for texturing.  consequently, the id should be from an actual block and the block texture an actual block in the current game.
        	}
        
        nbt.setTag(TAG_ATTR_LIST, itemstack_list);
        
        for (int idx = 0; idx < 7; ++idx) {
            nbt.setString(TAG_CHISEL_DESIGN + "_" + idx, cbChiselDesign[idx]);
        }

        nbt.setInteger(TAG_METADATA, cbMetadata);
        nbt.setString(TAG_DESIGN, cbDesign);
        nbt.setString(TAG_OWNER, cbOwner);
    }

    @Override
    /**
     * Overridden in a sign to provide the text.
     */
    public Packet getDescriptionPacket()
    {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    @Override
    /**
     * Called when you receive a TileEntityData packet for the location this
     * TileEntity is currently in. On the client, the NetworkManager will always
     * be the remote server. On the server, it will be whomever is responsible for
     * sending the packet.
     *
     * @param net The NetworkManager the packet originated from
     * @param pkt The data packet
     */
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt)
    {
        readFromNBT(pkt.func_148857_g());
    }

    /**
     * Called from Chunk.setBlockIDWithMetadata, determines if this tile entity should be re-created when the ID, or Metadata changes.
     * Use with caution as this will leave straggler TileEntities, or create conflicts with other TileEntities if not used properly.
     *
     * @param oldID The old ID of the block
     * @param newID The new ID of the block (May be the same)
     * @param oldMeta The old metadata of the block
     * @param newMeta The new metadata of the block (May be the same)
     * @param world Current world
     * @param x X Position
     * @param y Y Position
     * @param z Z Position
     * @return True to remove the old tile entity, false to keep it in tact {and create a new one if the new values specify to}
     */
    @Override
    public boolean shouldRefresh(Block oldBlock, Block newBlock, int oldMeta, int newMeta, World world, int x, int y, int z)
    {
        /*
         * This is a curious method.
         *
         * Essentially, when doing most block logic server-side, changes
         * to blocks will momentarily "flash" to their default state
         * when rendering client-side.  This is most noticeable when adding
         * or removing covers for the first time.
         *
         * Making the tile entity refresh only when the block is first created
         * is not only reasonable, but fixes this behavior.
         */
        return oldBlock != newBlock;
    }

    /**
     * Copies owner from TEBase object.
     */
    public void copyOwner(final TEBase TE)
    {
        cbOwner = TE.getOwner();
        markDirty();
    }

    /**
     * Sets owner of tile entity.
     */
    @Override
    public void setOwner(ProtectedObject obj)
    {
        cbOwner = obj.toString();
        markDirty();
    }

    @Override
    public String getOwner()
    {
        return cbOwner;
    }

    @Override
    /**
     * Determines if this TileEntity requires update calls.
     * @return True if you want updateEntity() to be called, false if not
     */
    public boolean canUpdate()
    {
        return false;
    }

    public boolean hasAttribute(byte attrId)
    {
        return cbAttrMap.containsKey(attrId);
    }

    public ItemStack getAttribute(byte attrId)
    {
        return cbAttrMap.get(attrId);
    }

    public ItemStack getAttributeForDrop(byte attrId)
    {
        ItemStack itemStack = cbAttrMap.get(attrId);

        // If cover, check for rotation and restore default metadata
        if (attrId <= ATTR_COVER[6]) {
            setDefaultMetadata(itemStack);
        }

        return itemStack;
    }

    /**
     * Will restore cover to default state before returning {@link ItemStack}.
     * <p>
     * Corrects log rotation, among other things.
     *
     * @param  rand a {@link Random} reference
     * @param  itemStack the {@link ItemStack}
     * @return the cover {@link ItemStack} in it's default state
     */
    private ItemStack setDefaultMetadata(ItemStack itemStack)
    {
        Block block = BlockProperties.toBlock(itemStack);

        // Correct rotation metadata before dropping block
        if (BlockProperties.blockRotates(itemStack) || block instanceof BlockDirectional)
        {
            int dmgDrop = block.damageDropped(itemStack.getItemDamage());
            Item itemDrop = block.getItemDropped(itemStack.getItemDamage(), getWorldObj().rand, /* Fortune */ 0);

            /* Check if block drops itself, and, if so, correct the damage value to the block's default. */

            if (itemDrop != null && itemDrop.equals(itemStack.getItem()) && dmgDrop != itemStack.getItemDamage()) {
                itemStack.setItemDamage(dmgDrop);
            }
        }

        return itemStack;
    }

    public void addAttribute(byte attrId, ItemStack itemStack)
    {
        if (hasAttribute(attrId) || itemStack == null) {
            return;
        }

        // Reduce stack size to 1 and save attribute
        ItemStack reducedStack = ItemStack.copyItemStack(itemStack);
        reducedStack.stackSize = 1;
        cbAttrMap.put(attrId, reducedStack);

        // Produce world events if specific attributes are set
        World world = getWorldObj();
        Block block = BlockProperties.toBlock(itemStack);

        if (attrId < 7) {
            if (attrId == ATTR_COVER[6]) {
                int metadata = itemStack.getItemDamage();
                world.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, metadata, 0);
            }
            world.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, block);
        } else if (attrId == ATTR_PLANT | attrId == ATTR_SOIL) {
            world.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, block);
        }

        if (attrId == ATTR_FERTILIZER) {
            /* Play sound when fertilizing plants.. though I've never heard it before. */
            getWorldObj().playAuxSFX(2005, xCoord, yCoord, zCoord, 0);
        }

        updateWorldAndLighting();
        markDirty();
    }

    /**
     * Will remove the attribute from map once block drop is complete.
     * <p>
     * Should only be called externally by {@link BlockCoverable#onBlockEventReceived}.
     *
     * @param attrId
     */
    public void onAttrDropped(byte attrId)
    {
        cbAttrMap.remove(attrId);
        updateWorldAndLighting();
        markDirty();
    }

    /**
     * Initiates block drop event, which will remove attribute from tile entity.
     *
     * @param  attrId the attribute ID
     */
    public void createBlockDropEvent(byte attrId)
    {
        getWorldObj().addBlockEvent(xCoord, yCoord, zCoord, getBlockType(), BlockCoverable.EVENT_ID_DROP_ATTR, attrId);
    }

    public void removeAttributes(int side)
    {
        createBlockDropEvent(ATTR_COVER[side]);
        createBlockDropEvent(ATTR_DYE[side]);
        createBlockDropEvent(ATTR_OVERLAY[side]);

        if (side == 6) {
            createBlockDropEvent(ATTR_ILLUMINATOR);
        }
    }

    /**
     * Returns whether block has pattern.
     */
    public boolean hasChiselDesign(int side)
    {
        return DesignHandler.listChisel.contains(getChiselDesign(side));
    }

    /**
     * Returns pattern.
     */
    public String getChiselDesign(int side)
    {
        return cbChiselDesign[side];
    }

    /**
     * Sets pattern.
     */
    public boolean setChiselDesign(int side, String iconName)
    {
        if (!cbChiselDesign.equals(iconName)) {
            cbChiselDesign[side] = iconName;
            getWorldObj().markBlockForUpdate(xCoord, yCoord, zCoord);
            markDirty();
            return true;
        }

        return false;
    }

    public void removeChiselDesign(int side)
    {
        if (!cbChiselDesign.equals("")) {
            cbChiselDesign[side] = "";
            getWorldObj().markBlockForUpdate(xCoord, yCoord, zCoord);
            markDirty();
        }
    }

    /**
     * Gets block-specific data.
     *
     * @return the data
     */
    public int getData()
    {
        return cbMetadata;
    }

    /**
     * Sets block-specific data.
     */
    public boolean setData(int data)
    {
        if (data != getData()) {
            cbMetadata = data;
            getWorldObj().markBlockForUpdate(xCoord, yCoord, zCoord);
            markDirty();
            return true;
        }

        return false;
    }

    public boolean hasDesign()
    {
        return DesignHandler.getListForType(getBlockDesignType()).contains(cbDesign);
    }

    public String getDesign()
    {
        return cbDesign;
    }

    public boolean setDesign(String name)
    {
        if (!cbDesign.equals(name)) {
            cbDesign = name;
            getWorldObj().markBlockForUpdate(xCoord, yCoord, zCoord);
            markDirty();
            return true;
        }

        return false;
    }

    public boolean removeDesign()
    {
        return setDesign("");
    }

    public String getBlockDesignType()
    {
        String name = getBlockType().getUnlocalizedName();
        return name.substring(new String("tile.blockCarpenters").length()).toLowerCase();
    }

    public boolean setNextDesign()
    {
        return setDesign(DesignHandler.getNext(getBlockDesignType(), cbDesign));
    }

    public boolean setPrevDesign()
    {
        return setDesign(DesignHandler.getPrev(getBlockDesignType(), cbDesign));
    }

    /**
     * Sets block metadata without causing a render update.
     * <p>
     * As part of mimicking a cover block, the metadata must be changed
     * to better represent the cover properties.
     * <p>
     * This is normally followed up by calling {@link setMetadataFromCover}.
     */
    public void setMetadata(int metadata)
    {
        tempMetadata = getWorldObj().getBlockMetadata(xCoord, yCoord, zCoord);
        getWorldObj().setBlockMetadataWithNotify(xCoord, yCoord, zCoord, metadata, 4);
    }

    /**
     * Restores default metadata for block from base cover.
     */
    public void restoreMetadata()
    {
        getWorldObj().setBlockMetadataWithNotify(xCoord, yCoord, zCoord, tempMetadata, 4);
    }

    /////////////////////////////////////////////////////////////
    // Code below implemented strictly for light updates
    /////////////////////////////////////////////////////////////

    /**
     * Returns the current block light value. This is the only method
     * that will grab the tile entity to calculate lighting, which
     * is a very expensive operation to call while rendering, as it is
     * called often.
     *
     * @param  blockAccess the {@link IBlockAccess} object
     * @param  x the x coordinate
     * @param  y the y coordinate
     * @param  z the z coordinate
     * @return a light value from 0 to 15
     */
    protected int getDynamicLightValue()
    {
        int value = 0;

        if (FeatureRegistry.enableIllumination && hasAttribute(ATTR_ILLUMINATOR)) {
            return 15;
        } else {
            // Find greatest light output from attributes
            calcLighting = true;
            Iterator it = cbAttrMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry)it.next();
                ItemStack itemStack = BlockProperties.getCallableItemStack((ItemStack)pair.getValue());
                Block block = BlockProperties.toBlock(itemStack);

                if (block != Blocks.air) {

                    // Determine metadata-sensitive light value (usually recursive, and not useful)
                    setMetadata(itemStack.getItemDamage());
                    int sensitiveLight = block.getLightValue(getWorldObj(), xCoord, yCoord, zCoord);
                    restoreMetadata();

                    if (sensitiveLight > 0) {
                        value = Math.max(value, sensitiveLight);
                    } else {
                        // Grab default light value for block
                        value = Math.max(value, block.getLightValue());
                    }

                }
            }
            calcLighting = false;
        }

        return value;
    }

    /**
     * Performs world update and refreshes lighting.
     */
    private void updateWorldAndLighting()
    {
        World world = getWorldObj();
        if (world != null)
        {
            lightValue = getDynamicLightValue();
            world.func_147451_t(xCoord, yCoord, zCoord); // Updates block lightmap, should help with spawns
            world.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

}
