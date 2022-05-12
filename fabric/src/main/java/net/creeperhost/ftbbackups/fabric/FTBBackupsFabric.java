package net.creeperhost.ftbbackups.fabric;

import net.creeperhost.ftbbackups.FTBBackups;
import net.fabricmc.api.ModInitializer;

public class FTBBackupsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FTBBackups.init();
    }
}
