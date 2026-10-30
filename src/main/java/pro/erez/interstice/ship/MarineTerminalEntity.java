package pro.erez.interstice.ship;

import appeng.api.config.*;
import appeng.api.networking.GridFlags;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.*;
import appeng.api.util.*;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.menu.*;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;

/** Real AE2 terminal node and native menu; all contents come from the grid service. */
public final class MarineTerminalEntity extends AENetworkedBlockEntity implements ITerminalHost,IActionHost,IEnergySource {
    private static final MEStorage EMPTY=()->Component.literal("Offline NE01 ME network");
    private final IConfigManager config=IConfigManager.builder((Runnable)this::setChanged)
        .registerSetting(Settings.SORT_BY,SortOrder.NAME).registerSetting(Settings.VIEW_MODE,ViewItems.ALL)
        .registerSetting(Settings.SORT_DIRECTION,SortDir.ASCENDING).build();
    public MarineTerminalEntity(BlockPos p,BlockState s){super(MarineShips.TERMINAL_ENTITY.get(),p,s);getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(1.0);}
    @Override protected Item getItemFromBlockEntity(){return MarineMe.item("terminal");}
    @Override public MEStorage getInventory(){var g=getMainNode().getGrid();return g==null?EMPTY:g.getStorageService().getInventory();}
    @Override public ILinkStatus getLinkStatus(){return ILinkStatus.ofManagedNode(getMainNode());}
    @Override public appeng.api.networking.IGridNode getActionableNode(){return getMainNode().getNode();}
    @Override public IConfigManager getConfigManager(){return config;}
    @Override public double extractAEPower(double amount,Actionable mode,PowerMultiplier multiplier){var g=getMainNode().getGrid();return g==null?0:g.getEnergyService().extractAEPower(amount,mode,multiplier);}
    public void open(Player p){MenuOpener.open(MEStorageMenu.TYPE,p,MenuLocators.forBlockEntity(this));}
    @Override public void returnToMainMenu(Player p,ISubMenu menu){open(p);}
    @Override public ItemStack getMainMenuIcon(){return new ItemStack(MarineMe.item("terminal"));}
    @Override public void loadTag(CompoundTag tag,HolderLookup.Provider provider){super.loadTag(tag,provider);config.readFromNBT(tag,provider);}
    @Override public void saveAdditional(CompoundTag tag,HolderLookup.Provider provider){super.saveAdditional(tag,provider);config.writeToNBT(tag,provider);}
}
