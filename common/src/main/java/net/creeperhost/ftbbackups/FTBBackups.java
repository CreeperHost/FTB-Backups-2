package net.creeperhost.ftbbackups;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.platform.Platform;
import net.creeperhost.ftbbackups.commands.BackupCommand;
import net.creeperhost.ftbbackups.config.Config;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class FTBBackups {
    public static final String MOD_ID = "ftbbackups2";
    public static Logger LOGGER = LogManager.getLogger();
    public static Path configFile = Platform.getConfigFolder().resolve(MOD_ID + ".json");

    public static ScheduledExecutorService configWatcherExecutorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("FTB Backups Config Watcher %d").build());
    public static ScheduledExecutorService backupCleanerWatcherExecutorService = Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder().setNameFormat("FTB Backups scheduled executor %d").build());
    public static ExecutorService backupExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("FTB Backups backup thread %d").build());

    public static MinecraftServer minecraftServer;
    public static Scheduler scheduler;

    public static boolean isShutdown = false;

    public static void init() {
        Config.init(configFile.toFile());
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> dispatcher.register(BackupCommand.register()));
        LifecycleEvent.SERVER_STARTED.register(FTBBackups::serverStartedEvent);
        LifecycleEvent.SERVER_STOPPING.register(instance -> killOutThreads());
        LifecycleEvent.SERVER_LEVEL_SAVE.register(FTBBackups::serverSaveEvent);
        Runtime.getRuntime().addShutdownHook(new Thread(FTBBackups::killOutThreads));
    }

    private static void serverSaveEvent(ServerLevel serverLevel) {
        if(serverLevel == null || serverLevel.isClientSide) return;
        ServerPlayer player = serverLevel.getRandomPlayer();
        if(player != null ) {
            BackupHandler.isDirty = true;
        }
    }

    private static void serverStartedEvent(MinecraftServer minecraftServer) {
        FTBBackups.minecraftServer = minecraftServer;
        BackupHandler.init(minecraftServer);
        isShutdown = false;
        if (Config.cached().enabled) {
            if (!CronExpression.isValidExpression(Config.cached().backup_cron)) {
                FTBBackups.LOGGER.error("backup_cron is invalid, restoring default value");
                Config.cached().backup_cron = "0 */30 * * * ?";
                Config.saveConfig();
            }
            try {
                JobDetail jobDetail = JobBuilder.newJob(BackupJob.class).withIdentity(MOD_ID).build();
                Properties properties = new Properties();
                properties.put("org.quartz.scheduler.instanceName", MOD_ID);
                properties.put("org.quartz.threadPool.threadCount", "1");
                SchedulerFactory schedulerFactory = new StdSchedulerFactory(properties);
                scheduler = schedulerFactory.getScheduler();
                CronTrigger trigger = TriggerBuilder.newTrigger()
                        .withIdentity(MOD_ID)
                        .withSchedule(CronScheduleBuilder.cronSchedule(Config.cached().backup_cron))
                        .build();

                scheduler.start();
                scheduler.scheduleJob(jobDetail, trigger);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void killOutThreads()
    {
        try
        {
            int shutdownCount = 0;
            FTBBackups.isShutdown = true;

            while(BackupHandler.isRunning())
            {
                if(shutdownCount > 120) break;
                //Let's hold up shutting down if we're mid-backup I guess... But limit it to waiting 2 minutes.
                try {
                    if (shutdownCount % 10 == 0) FTBBackups.LOGGER.info("Backup in progress, Waiting for it to finish before shutting down.");
                    Thread.sleep(1000);
                    shutdownCount++;
                } catch (InterruptedException ignored) {}
            }

            if(scheduler != null && !scheduler.isShutdown())
            {
                scheduler.clear();
                LOGGER.info("Shutting down scheduler thread");
                scheduler.shutdown(false);
            }
            Config.watcher.get().close();
            if(!FTBBackups.configWatcherExecutorService.isShutdown())
            {
                LOGGER.info("Shutting down the config watcher executor");
                FTBBackups.configWatcherExecutorService.shutdownNow();
            }
            if(!FTBBackups.backupCleanerWatcherExecutorService.isShutdown())
            {
                LOGGER.info("Shutting down backup cleaning executor");
                FTBBackups.backupCleanerWatcherExecutorService.shutdownNow();
            }
            if(!FTBBackups.backupExecutor.isShutdown())
            {
                LOGGER.info("Shutting down backup executor");
                FTBBackups.backupExecutor.shutdownNow();
            }
            BackupHandler.backupRunning.set(false);
            LOGGER.info("=========Checking everything is shut down============");
            LOGGER.info("Scheduler Shutdown:{}", scheduler.isShutdown());
            LOGGER.info("Config watcher Shutdown:{}", FTBBackups.configWatcherExecutorService.isShutdown());
            LOGGER.info("Cleaner watcher Shutdown:{}", FTBBackups.backupCleanerWatcherExecutorService.isShutdown());
            LOGGER.info("Backup Executor Shutdown:{}", FTBBackups.backupExecutor.isShutdown());
            LOGGER.info("========Shutdown completed, FTB Backups has now finished shutting down==========");

        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static class BackupJob implements Job {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            if (FTBBackups.minecraftServer != null) {
                FTBBackups.LOGGER.info("Attempting to create an automatic backup");
                BackupHandler.createBackup(FTBBackups.minecraftServer);
            }
        }
    }
}
