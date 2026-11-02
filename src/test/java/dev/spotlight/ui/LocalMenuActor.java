package dev.spotlight.ui;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.server.level.ServerLevel;
import sun.misc.Unsafe;


public final class LocalMenuActor extends Player {
    private LocalMenuActor() { super(null, new GameProfile(UUID.randomUUID(), "local-test")); }
    @Override public GameType gameMode() { return GameType.SURVIVAL; }
    @Override public void updateTutorialInventoryAction(ItemStack held, ItemStack slot, ClickAction action) {}
    public static final class TestLevel extends ServerLevel {
        private TestLevel() { super(null, null, null, null, null, null, false, 0, null, false, null); }
        @Override public FeatureFlagSet enabledFeatures() { return FeatureFlags.VANILLA_SET; }
    }
    public static Player create() throws Exception {
        Field access = Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        Unsafe unsafe = (Unsafe) access.get(null);
        LocalMenuActor player = (LocalMenuActor) unsafe.allocateInstance(LocalMenuActor.class);
        Field inventory = Player.class.getDeclaredField("inventory");
        unsafe.putObject(player, unsafe.objectFieldOffset(inventory), new Inventory(player, new EntityEquipment()));
        Field level = Entity.class.getDeclaredField("level");
        unsafe.putObject(player, unsafe.objectFieldOffset(level), unsafe.allocateInstance(TestLevel.class));
        return player;
    }
}
