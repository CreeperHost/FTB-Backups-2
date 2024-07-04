package net.creeperhost.ftbbackups.neoforge;

import net.creeperhost.ftbbackups.FTBBackups;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod (FTBBackups.MOD_ID)
public class FTBBackupsNeoForge {
    public FTBBackupsNeoForge(IEventBus iEventBus) {
        // Submit our event bus to let architectury register our content on the right time
//        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY, (a, b) -> true));
        FTBBackups.init();
    }
}
