package net.creeperhost.ftbbackups.neoforge;

import net.creeperhost.ftbbackups.FTBBackups;
import net.neoforged.fml.IExtensionPoint;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;

@Mod (FTBBackups.MOD_ID)
public class FTBBackupsNeoForge {
    public FTBBackupsNeoForge() {
        // Submit our event bus to let architectury register our content on the right time
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY, (a, b) -> true));
        FTBBackups.init();
    }
}
