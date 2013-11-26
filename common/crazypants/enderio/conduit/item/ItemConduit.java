package crazypants.enderio.conduit.item;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Icon;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import crazypants.enderio.ModObject;
import crazypants.enderio.conduit.AbstractConduit;
import crazypants.enderio.conduit.AbstractConduitNetwork;
import crazypants.enderio.conduit.ConduitUtil;
import crazypants.enderio.conduit.ConnectionMode;
import crazypants.enderio.conduit.IConduit;
import crazypants.enderio.conduit.IConduitBundle;
import crazypants.enderio.conduit.RaytraceResult;
import crazypants.enderio.conduit.geom.CollidableComponent;
import crazypants.render.IconUtil;
import crazypants.util.BlockCoord;

public class ItemConduit extends AbstractConduit implements IItemConduit {

  public static final String EXTERNAL_INTERFACE_GEOM = "ExternalInterface";

  public static final String ICON_KEY = "enderio:itemConduit";

  public static final String ICON_KEY_CORE = "enderio:itemConduitCore";

  public static final String ICON_KEY_CORE_ADV = "enderio:itemConduitCoreAdvanced";

  public static final String ICON_KEY_INPUT = "enderio:itemConduitInput";

  public static final String ICON_KEY_OUTPUT = "enderio:itemConduitOutput";

  public static final String ICON_KEY_IN_OUT = "enderio:itemConduitInOut";

  private static final String ICON_KEY_INPUT_ADV = "enderio:itemConduitInputAdvanced";

  private static final String ICON_KEY_OUTPUT_ADV = "enderio:itemConduitOutputAdvanced";

  private static final String ICON_KEY_IN_OUT_ADV = "enderio:itemConduitInOutAdvanced";

  public static final String ICON_KEY_ENDER = "enderio:ender_still";

  static final Map<String, Icon> ICONS = new HashMap<String, Icon>();

  public static void initIcons() {
    IconUtil.addIconProvider(new IconUtil.IIconProvider() {

      @Override
      public void registerIcons(IconRegister register) {
        ICONS.put(ICON_KEY, register.registerIcon(ICON_KEY));
        ICONS.put(ICON_KEY_CORE, register.registerIcon(ICON_KEY_CORE));
        ICONS.put(ICON_KEY_CORE_ADV, register.registerIcon(ICON_KEY_CORE_ADV));
        ICONS.put(ICON_KEY_INPUT, register.registerIcon(ICON_KEY_INPUT));
        ICONS.put(ICON_KEY_OUTPUT, register.registerIcon(ICON_KEY_OUTPUT));
        ICONS.put(ICON_KEY_IN_OUT, register.registerIcon(ICON_KEY_IN_OUT));

        ICONS.put(ICON_KEY_INPUT_ADV, register.registerIcon(ICON_KEY_INPUT_ADV));
        ICONS.put(ICON_KEY_OUTPUT_ADV, register.registerIcon(ICON_KEY_OUTPUT_ADV));
        ICONS.put(ICON_KEY_IN_OUT_ADV, register.registerIcon(ICON_KEY_IN_OUT_ADV));

        ICONS.put(ICON_KEY_ENDER, register.registerIcon(ICON_KEY_ENDER));
      }

      @Override
      public int getTextureType() {
        return 0;
      }

    });
  }

  ItemConduitNetwork network;

  int maxExtractedOnTick = 2;
  float extractRatePerTick = maxExtractedOnTick / 20f;
  long extractedAtLastTick = -1;

  protected final EnumMap<ForgeDirection, ItemFilter> inputFilters = new EnumMap<ForgeDirection, ItemFilter>(ForgeDirection.class);

  protected final EnumMap<ForgeDirection, ItemFilter> outputFilters = new EnumMap<ForgeDirection, ItemFilter>(ForgeDirection.class);

  private int metaData;

  public ItemConduit() {
    this(0);
  }

  public ItemConduit(int itemDamage) {
    this.metaData = itemDamage;
    updateFromMetadata();
  }

  private void updateFromMetadata() {
    if(metaData == 1) {
      maxExtractedOnTick = 64;
      extractRatePerTick = (4 * 64) / 20f; //4 stacks a second
    } else {
      maxExtractedOnTick = 1;
      extractRatePerTick = 0.25f; //four items a second
    }

  }

  @Override
  public boolean onBlockActivated(EntityPlayer player, RaytraceResult res, List<RaytraceResult> all) {
    if(ConduitUtil.isToolEquipped(player)) {
      if(!getBundle().getEntity().worldObj.isRemote) {
        if(res != null && res.component != null) {
          ForgeDirection connDir = res.component.dir;
          ForgeDirection faceHit = ForgeDirection.getOrientation(res.movingObjectPosition.sideHit);
          if(connDir == ForgeDirection.UNKNOWN || connDir == faceHit) {
            // Attempt to join networks
            return ConduitUtil.joinConduits(this, faceHit);
          } else if(externalConnections.contains(connDir)) {
            setConnectionMode(connDir, getNextConnectionMode(connDir));
            return true;
          } else if(containsConduitConnection(connDir)) {
            ConduitUtil.disconectConduits(this, connDir);
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public void setInputFilter(ForgeDirection dir, ItemFilter filter) {
    inputFilters.put(dir, filter);
  }

  @Override
  public void setOutputFilter(ForgeDirection dir, ItemFilter filter) {
    outputFilters.put(dir, filter);
  }

  @Override
  public ItemFilter getInputFilter(ForgeDirection dir) {
    ItemFilter res = inputFilters.get(dir);
    if(res == null) {
      if(metaData == 1) {
        res = new ItemFilter(10);
      } else {
        res = new ItemFilter(10);
      }
    }
    return res;
  }

  @Override
  public int getMetaData() {
    return metaData;
  }

  @Override
  public ItemFilter getOutputFilter(ForgeDirection dir) {
    ItemFilter res = outputFilters.get(dir);
    if(res == null) {
      if(metaData == 1) {
        res = new ItemFilter(10);
      } else {
        res = new ItemFilter(10);
      }
    }
    return res;
  }

  @Override
  public int getMaximumExtracted(int slot) {
    World world = getBundle().getEntity().worldObj;
    if(world == null) {
      return 0;
    }
    long curTick = world.getTotalWorldTime();
    int numTicksSinceExtract = (int) (curTick - extractedAtLastTick);
    int result = (int) (numTicksSinceExtract * extractRatePerTick);
    result = Math.min(result, maxExtractedOnTick);
    return result;
  }

  @Override
  public void itemsExtracted(int numInserted, int slot) {
    World world = getBundle().getEntity().worldObj;
    if(world != null) {
      extractedAtLastTick = world.getTotalWorldTime();
    } else {
      extractedAtLastTick = -1;
    }
  }

  @Override
  public void externalConnectionAdded(ForgeDirection direction) {
    super.externalConnectionAdded(direction);
    if(network != null) {
      TileEntity te = bundle.getEntity();
      network.inventoryAdded(this, direction, te.xCoord + direction.offsetX, te.yCoord + direction.offsetY, te.zCoord + direction.offsetZ,
          getExternalInventory(direction));
    }
  }

  @Override
  public IInventory getExternalInventory(ForgeDirection direction) {
    World world = getBundle().getWorld();
    if(world == null) {
      return null;
    }
    BlockCoord loc = getLocation().getLocation(direction);
    TileEntity te = world.getBlockTileEntity(loc.x, loc.y, loc.z);
    if(te instanceof IInventory && !(te instanceof IConduitBundle)) {
      return (IInventory) te;
    }
    return null;
  }

  @Override
  public void externalConnectionRemoved(ForgeDirection direction) {
    super.externalConnectionRemoved(direction);
    if(network != null) {
      TileEntity te = bundle.getEntity();
      network.inventoryRemoved(this, te.xCoord + direction.offsetX, te.yCoord + direction.offsetY, te.zCoord + direction.offsetZ);
    }
  }

  @Override
  public void setConnectionMode(ForgeDirection dir, ConnectionMode mode) {
    ConnectionMode oldVal = conectionModes.get(dir);
    if(oldVal == mode) {
      return;
    }
    super.setConnectionMode(dir, mode);
    if(network != null) {
      network.connectionModeChanged(this, mode);
    }
  }

  @Override
  public boolean canConnectToExternal(ForgeDirection direction, boolean ignoreDisabled) {
    return getExternalInventory(direction) != null;
  }

  @Override
  protected ConnectionMode getDefaultConnectionMode() {
    return ConnectionMode.OUTPUT;
  }

  @Override
  public Class<? extends IConduit> getBaseConduitType() {
    return IItemConduit.class;
  }

  @Override
  public ItemStack createItem() {
    ItemStack result = new ItemStack(ModObject.itemItemConduit.actualId, 1, 0);
    return result;
  }

  @Override
  public AbstractConduitNetwork<?> getNetwork() {
    return network;
  }

  @Override
  public boolean setNetwork(AbstractConduitNetwork<?> network) {
    this.network = (ItemConduitNetwork) network;
    return true;
  }

  @Override
  public Icon getTextureForInputMode() {
    return metaData == 1 ? ICONS.get(ICON_KEY_INPUT_ADV) : ICONS.get(ICON_KEY_INPUT);
  }

  @Override
  public Icon getTextureForOutputMode() {
    return metaData == 1 ? ICONS.get(ICON_KEY_OUTPUT_ADV) : ICONS.get(ICON_KEY_OUTPUT);
  }

  @Override
  public Icon getTextureForInOutMode() {
    return metaData == 1 ? ICONS.get(ICON_KEY_IN_OUT_ADV) : ICONS.get(ICON_KEY_IN_OUT);
  }

  @Override
  public Icon getEnderIcon() {
    return ICONS.get(ICON_KEY_ENDER);
  }

  public Icon getCoreIcon() {
    return metaData == 1 ? ICONS.get(ICON_KEY_CORE_ADV) : ICONS.get(ICON_KEY_CORE);
  }

  @Override
  public Icon getTextureForState(CollidableComponent component) {
    if(component.dir == ForgeDirection.UNKNOWN) {
      return getCoreIcon();
    }
    if(EXTERNAL_INTERFACE_GEOM.equals(component.data)) {
      return getCoreIcon();
    }
    return ICONS.get(ICON_KEY);
  }

  @Override
  public Icon getTransmitionTextureForState(CollidableComponent component) {
    return null;
  }

  @Override
  public void writeToNBT(NBTTagCompound nbtRoot) {
    super.writeToNBT(nbtRoot);

    nbtRoot.setShort("metaData", (short) metaData);

    for (Entry<ForgeDirection, ItemFilter> entry : inputFilters.entrySet()) {
      if(entry.getValue() != null) {
        ItemFilter f = entry.getValue();
        if(f.isValid()) {
          NBTTagCompound itemRoot = new NBTTagCompound();
          f.writeToNBT(itemRoot);
          nbtRoot.setTag("inFilts." + entry.getKey().name(), itemRoot);
        }
      }
    }

    for (Entry<ForgeDirection, ItemFilter> entry : outputFilters.entrySet()) {
      if(entry.getValue() != null) {
        ItemFilter f = entry.getValue();
        if(f.isValid()) {
          NBTTagCompound itemRoot = new NBTTagCompound();
          f.writeToNBT(itemRoot);
          nbtRoot.setTag("outFilts." + entry.getKey().name(), itemRoot);
        }
      }
    }

  }

  @Override
  public void readFromNBT(NBTTagCompound nbtRoot) {
    super.readFromNBT(nbtRoot);

    metaData = nbtRoot.getShort("metaData");

    for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
      String key = "inFilts." + dir.name();
      if(nbtRoot.hasKey(key)) {
        NBTTagCompound filterTag = (NBTTagCompound) nbtRoot.getTag(key);
        ItemFilter filter = new ItemFilter();
        filter.readFromNBT(filterTag);
        inputFilters.put(dir, filter);
      }
      key = "outFilts." + dir.name();
      if(nbtRoot.hasKey(key)) {
        NBTTagCompound filterTag = (NBTTagCompound) nbtRoot.getTag(key);
        ItemFilter filter = new ItemFilter();
        filter.readFromNBT(filterTag);
        outputFilters.put(dir, filter);
      }
    }

    updateFromMetadata();
  }

}
