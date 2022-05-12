package net.creeperhost.ftbbackups.forge;

import dev.architectury.platform.forge.EventBuses;
import net.creeperhost.ftbbackups.FTBBackups;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(FTBBackups.MOD_ID)
public class FTBBackupsForge {
    public FTBBackupsForge() {
        // Submit our event bus to let architectury register our content on the right time
        EventBuses.registerModEventBus(FTBBackups.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        FTBBackups.init();
    }
}
