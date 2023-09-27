package net.creeperhost.ftbbackups;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.platform.Platform;
import net.creeperhost.ftbbackups.commands.BackupCommand;
import net.creeperhost.ftbbackups.config.Config;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FTBBackups {
    public static final String MOD_ID = "ftbbackups2";
    public static Logger LOGGER = LogManager.getLogger();
    public static Path configFile = Platform.getConfigFolder().resolve(MOD_ID + ".json");

    public static final ScheduledExecutorService configWatcherExecutorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
            .setDaemon(true)
            .setUncaughtExceptionHandler((t, e) -> FTBBackups.LOGGER.error("An error occurred running watcher task", e))
            .setNameFormat("FTB Backups Config Watcher %d")
            .build()
    );
    public static final ScheduledExecutorService backupCleanerExecutorService = Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder()
            .setDaemon(true)
            .setUncaughtExceptionHandler((t, e) -> FTBBackups.LOGGER.error("An error occurred running cleaner task", e))
            .setNameFormat("FTB Backups scheduled executor %d")
            .build()
    );
    public static final ExecutorService backupExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
            .setDaemon(true)
            .setUncaughtExceptionHandler((t, e) -> FTBBackups.LOGGER.error("An error occurred running backup task", e))
            .setNameFormat("FTB Backups backup thread %d")
            .build()
    );

    public static MinecraftServer minecraftServer;
    public static Scheduler scheduler;

    public static boolean isShutdown = false;

    public static void init() {
        Config.init(configFile.toFile());
        if (!Config.cached().enabled) return; //If not enabled then just don't do anything!
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> dispatcher.register(BackupCommand.register()));
        LifecycleEvent.SERVER_STARTED.register(FTBBackups::serverStartedEvent);
        LifecycleEvent.SERVER_STOPPING.register(instance -> onShutdown());
        TickEvent.SERVER_PRE.register(FTBBackups::onServerTickPre);
        //Add this once on startup, so we don't need to mess with stopping and starting the executor, BackupHandler#clean won't do anything if backups are not active.
        FTBBackups.backupCleanerExecutorService.scheduleAtFixedRate(BackupHandler::clean, 0, 30, TimeUnit.SECONDS);

        if (!CronExpression.isValidExpression(Config.cached().backup_cron)) {
            FTBBackups.LOGGER.error("backup_cron is invalid, restoring default value");
            Config.cached().backup_cron = "0 */30 * * * ?";
            Config.saveConfig();
        }
        try {
            JobDetail jobDetail = JobBuilder.newJob(BackupJob.class).withIdentity(MOD_ID).build();
            Properties properties = new Properties();
            properties.put(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, MOD_ID);
            properties.put("org.quartz.threadPool.threadCount", "1");
            //Let's also make this a daemon, so we can just leave the scheduler running.
            properties.put("org.quartz.threadPool.makeThreadsDaemons", "true");
            properties.put(StdSchedulerFactory.PROP_SCHED_MAKE_SCHEDULER_THREAD_DAEMON, "true");
            SchedulerFactory schedulerFactory = new StdSchedulerFactory(properties);
            scheduler = schedulerFactory.getScheduler();
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(MOD_ID)
                    .withSchedule(CronScheduleBuilder.cronSchedule(Config.cached().backup_cron))
                    .build();

            scheduler.start();
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (Exception e) {
            LOGGER.error("An error occurred while, starting backup scheduler", e);
        }
    }

    private static void onServerTickPre(MinecraftServer server) {
        if (server == null) return;
        PlayerList list = server.getPlayerList();
        if (list == null) return;
        BackupHandler.isDirty |= list.getPlayerCount() > 0;
    }

    private static void serverStartedEvent(MinecraftServer minecraftServer) {
        FTBBackups.minecraftServer = minecraftServer;
        BackupHandler.init(minecraftServer);
        isShutdown = false;
    }

    public static void onShutdown() {
        if (FTBBackups.isShutdown) return;
        try {
            int shutdownCount = 0;
            FTBBackups.isShutdown = true;
            while (BackupHandler.isRunning()) {
                if (shutdownCount > 120) break;
                //Let's hold up shutting down if we're mid-backup I guess... But limit it to waiting 2 minutes.
                try {
                    if (shutdownCount % 10 == 0) FTBBackups.LOGGER.info("Backup in progress, Waiting for it to finish before shutting down.");
                    Thread.sleep(1000);
                    shutdownCount++;
                } catch (InterruptedException ignored) {
                }
            }

            if (Config.watcher.get() != null) Config.watcher.get().close();
            BackupHandler.backupRunning.set(false);

            //We don't need to shut down the executors if they use daemon threads!

            LOGGER.info("Shutdown Complete");
        } catch (Exception e) {
            LOGGER.error("An error occurred during shutdown process", e);
        }
    }

    public static class BackupJob implements Job {
        @Override
        public void execute(JobExecutionContext context) {
            if (FTBBackups.minecraftServer != null && !FTBBackups.isShutdown) {
                FTBBackups.LOGGER.info("Attempting to create an automatic backup");
                BackupHandler.createBackup(FTBBackups.minecraftServer);
            }
        }
    }
}
