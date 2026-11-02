package dev.spotlight;

import dev.spotlight.ui.ClientChestMenu;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;


public abstract class LocalContainerScreen extends AbstractContainerScreen<ClientChestMenu> {
    protected LocalContainerScreen(ClientChestMenu menu, Component title) {
        super(menu, menu.getActor().getInventory(), title, 176, 222);
    }

    @Override
    protected final void slotClicked(Slot slot, int slotId, int button, ContainerInput input) {
        localSlotClicked(slot == null ? slotId : slot.index, button, input);
    }

    protected abstract void localSlotClicked(int slotId, int button, ContainerInput input);
}
