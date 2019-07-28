package com.bgsoftware.wildchests.nms;

import com.bgsoftware.wildchests.api.objects.chests.Chest;
import net.minecraft.server.v1_13_R1.BlockPosition;
import net.minecraft.server.v1_13_R1.ChatComponentText;
import net.minecraft.server.v1_13_R1.Chunk;
import net.minecraft.server.v1_13_R1.Container;
import net.minecraft.server.v1_13_R1.EntityPlayer;
import net.minecraft.server.v1_13_R1.IInventory;
import net.minecraft.server.v1_13_R1.ItemStack;
import net.minecraft.server.v1_13_R1.NBTCompressedStreamTools;
import net.minecraft.server.v1_13_R1.NBTTagCompound;
import net.minecraft.server.v1_13_R1.NBTTagList;
import net.minecraft.server.v1_13_R1.PacketPlayOutOpenWindow;
import net.minecraft.server.v1_13_R1.TileEntity;
import net.minecraft.server.v1_13_R1.TileEntityChest;
import net.minecraft.server.v1_13_R1.TileEntityHopper;
import net.minecraft.server.v1_13_R1.World;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_13_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_13_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_13_R1.inventory.CraftContainer;
import org.bukkit.craftbukkit.v1_13_R1.inventory.CraftInventory;
import org.bukkit.craftbukkit.v1_13_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_13_R1.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings({"unused", "ConstantConditions"})
public final class NMSAdapter_v1_13_R1 implements NMSAdapter {

    @Override
    public String getVersion() {
        return "v1_13_R1";
    }

    @Override
    public void playChestAction(Location location, boolean open) {
        World world = ((CraftWorld) location.getWorld()).getHandle();
        BlockPosition blockPosition = new BlockPosition(location.getX(), location.getY(), location.getZ());
        TileEntityChest tileChest = (TileEntityChest) world.getTileEntity(blockPosition);
        world.playBlockAction(blockPosition, tileChest.getBlock().getBlock(), 1, open ? 1 : 0);
    }

    @Override
    public int getHopperTransfer(org.bukkit.World world) {
        return ((CraftWorld) world).getHandle().spigotConfig.hopperTransfer;
    }

    @Override
    public int getHopperAmount(org.bukkit.World world) {
        return ((CraftWorld) world).getHandle().spigotConfig.hopperAmount;
    }

    @Override
    public void refreshHopperInventory(Player player, Inventory inventory) {
        EntityPlayer entityPlayer = ((CraftPlayer) player).getHandle();
        Container container = new CraftContainer(inventory, entityPlayer, entityPlayer.nextContainerCounter());
        String title = container.getBukkitView().getTitle();
        int size = container.getBukkitView().getTopInventory().getSize();
        entityPlayer.playerConnection.sendPacket(new PacketPlayOutOpenWindow(container.windowId, "minecraft:hopper", new ChatComponentText(title), size));
        entityPlayer.activeContainer = container;
        entityPlayer.activeContainer.addSlotListener(entityPlayer);
    }

    @Override
    public void setDesignItem(org.bukkit.inventory.ItemStack itemStack) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        ItemStack nmsItem = CraftItemStack.asNMSCopy(itemStack);
        itemMeta.setDisplayName(ChatColor.RESET + nmsItem.getName().getString());
        itemStack.setItemMeta(itemMeta);
        itemStack.setAmount(1);
    }

    @Override
    public void setTitle(Inventory bukkitInventory, String title) {
        try {
            IInventory inventory = ((CraftInventory) bukkitInventory).getInventory();

            Optional<Class<?>> optionalClass = Arrays.stream(bukkitInventory.getClass().getDeclaredClasses())
                    .filter(clazz -> clazz.getName().contains("MinecraftInventory")).findFirst();

            if(optionalClass.isPresent()){
                Class minecraftInventory = optionalClass.get();
                Field titleField = minecraftInventory.getDeclaredField("title");
                titleField.setAccessible(true);
                titleField.set(inventory, CraftChatMessage.fromStringOrNull(title));
                titleField.setAccessible(false);
            }else{
                TileEntityHopper tileEntityHopper = (TileEntityHopper) inventory;
                tileEntityHopper.setCustomName(CraftChatMessage.fromStringOrNull(title));
            }
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    @Override
    public String serialize(org.bukkit.inventory.ItemStack itemStack) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataOutput dataOutput = new DataOutputStream(outputStream);

        NBTTagCompound tagCompound = new NBTTagCompound();

        ItemStack nmsItem = CraftItemStack.asNMSCopy(itemStack);

        if(nmsItem != null)
            nmsItem.save(tagCompound);

        try {
            NBTCompressedStreamTools.a(tagCompound, dataOutput);
        }catch(Exception ex){
            return null;
        }

        return new BigInteger(1, outputStream.toByteArray()).toString(32);
    }

    @Override
    public String serialize(Inventory[] inventories) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataOutput dataOutput = new DataOutputStream(outputStream);

        NBTTagCompound tagCompound = new NBTTagCompound();
        tagCompound.setInt("Length", inventories.length);

        for(int slot = 0; slot < inventories.length; slot++) {
            NBTTagCompound inventoryCompound = new NBTTagCompound();
            serialize(inventories[slot], inventoryCompound);
            tagCompound.set(slot + "", inventoryCompound);
        }

        try {
            NBTCompressedStreamTools.a(tagCompound, dataOutput);
        }catch(Exception ex){
            return null;
        }

        return new BigInteger(1, outputStream.toByteArray()).toString(32);
    }

    @Override
    public Inventory[] deserialze(String serialized) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(new BigInteger(serialized, 32).toByteArray());
        List<Inventory> inventories = new ArrayList<>();

        try {
            NBTTagCompound tagCompound = NBTCompressedStreamTools.a(new DataInputStream(inputStream));
            int length = tagCompound.getInt("Length");
            inventories = new ArrayList<>(length);

            for(int i = 0; i < length; i++){
                if(tagCompound.hasKey(i + "")) {
                    NBTTagCompound nbtTagCompound = tagCompound.getCompound(i + "");
                    inventories.add(i, deserialize(nbtTagCompound));
                }
            }

        }catch(Exception ignored){}

        return inventories.toArray(new Inventory[0]);
    }

    @Override
    public org.bukkit.inventory.ItemStack deserialzeItem(String serialized) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(new BigInteger(serialized, 32).toByteArray());

        try {
            NBTTagCompound nbtTagCompoundRoot = NBTCompressedStreamTools.a(new DataInputStream(inputStream));

            ItemStack nmsItem = ItemStack.a(nbtTagCompoundRoot);

            return CraftItemStack.asBukkitCopy(nmsItem);
        }catch(Exception ex){
            return null;
        }
    }

    @Override
    public void updateTileEntity(Chest chest) {
        Location loc = chest.getLocation();
        World world = ((CraftWorld) loc.getWorld()).getHandle();
        BlockPosition blockPosition = new BlockPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        Chunk chunk = world.getChunkAtWorldCoords(blockPosition);
        TileEntityWildChest tileEntityWildChest = new TileEntityWildChest(chest, world, blockPosition);
        world.capturedTileEntities.put(blockPosition.h(), tileEntityWildChest);
        chunk.tileEntities.put(blockPosition.h(), tileEntityWildChest);
    }

    private void serialize(Inventory inventory, NBTTagCompound tagCompound){
        NBTTagList itemsList = new NBTTagList();
        org.bukkit.inventory.ItemStack[] items = inventory.getContents();

        for(int i = 0; i < items.length; ++i) {
            if (items[i] != null) {
                NBTTagCompound nbtTagCompound = new NBTTagCompound();
                nbtTagCompound.setByte("Slot", (byte) i);
                CraftItemStack.asNMSCopy(items[i]).save(nbtTagCompound);
                itemsList.add(nbtTagCompound);
            }
        }

        tagCompound.setInt("Size", inventory.getSize());
        tagCompound.set("Items", itemsList);
    }

    private Inventory deserialize(NBTTagCompound tagCompound){
        Inventory inventory = Bukkit.createInventory(null, tagCompound.getInt("Size"));
        NBTTagList itemsList = tagCompound.getList("Items", 10);

        for(int i = 0; i < itemsList.size(); i++){
            NBTTagCompound nbtTagCompound = itemsList.getCompound(i);
            inventory.setItem(nbtTagCompound.getByte("Slot"), CraftItemStack.asBukkitCopy(ItemStack.a(nbtTagCompound)));
        }

        return inventory;
    }

    private class TileEntityWildChest extends TileEntityChest{

        private TileEntityChest tileEntityChest = new TileEntityChest();
        private Chest chest;

        TileEntityWildChest(Chest chest, World world, BlockPosition blockPosition){
            this.chest = chest;
            this.world = world;
            updateTile(this, world, blockPosition);
            updateTile(tileEntityChest, world, blockPosition);
        }

        @Override
        public void update() {
            List<org.bukkit.inventory.ItemStack> bukkitItems = new ArrayList<>();
            getContents().stream().filter(Objects::nonNull)
                    .forEach(itemStack -> bukkitItems.add(CraftItemStack.asBukkitCopy(itemStack)));
            chest.addItems(bukkitItems.toArray(new org.bukkit.inventory.ItemStack[0]));
            getContents().clear();
            super.update();
        }

        @Override
        public NBTTagCompound save(NBTTagCompound nbttagcompound) {
            return tileEntityChest.save(nbttagcompound);
        }

        @Override
        public NBTTagCompound aa_() {
            return save(new NBTTagCompound());
        }

        private void updateTile(TileEntity tileEntity, World world, BlockPosition blockPosition){
            tileEntity.setWorld(world);
            tileEntity.setPosition(blockPosition);
        }

    }

}
