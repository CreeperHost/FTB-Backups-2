package net.creeperhost.ftbbackups.fabric;

import net.creeperhost.ftbbackups.FTBBackupsExpectPlatform;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class FTBBackupsExpectPlatformImpl {
    /**
     * This is our actual method to {@link FTBBackupsExpectPlatform#getConfigDirectory()}.
     */
    public static Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
