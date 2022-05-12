package net.creeperhost.ftbbackups.forge;

import net.creeperhost.ftbbackups.FTBBackupsExpectPlatform;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public class FTBBackupsExpectPlatformImpl {
    /**
     * This is our actual method to {@link FTBBackupsExpectPlatform#getConfigDirectory()}.
     */
    public static Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }
}
