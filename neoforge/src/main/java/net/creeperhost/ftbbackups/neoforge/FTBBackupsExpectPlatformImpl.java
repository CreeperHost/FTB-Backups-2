package net.creeperhost.ftbbackups.neoforge;

import net.creeperhost.ftbbackups.FTBBackupsExpectPlatform;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public class FTBBackupsExpectPlatformImpl {
    /**
     * This is our actual method to {@link FTBBackupsExpectPlatform#getConfigDirectory()}.
     */
    public static Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }
}
