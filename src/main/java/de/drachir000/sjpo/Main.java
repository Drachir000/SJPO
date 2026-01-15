package de.drachir000.sjpo;

import de.drachir000.sjpo.backup.BackupManager;
import de.drachir000.sjpo.core.ProcessOrchestrator;
import de.drachir000.sjpo.monitor.ProcessMonitor;
import de.drachir000.sjpo.notification.NotificationService;
import de.drachir000.sjpo.scheduler.SchedulerService;
import de.drachir000.sjpo.ui.MainWindow;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Main entry point for SJPO
 */
public class Main {
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    private static ProcessOrchestrator orchestrator;
    private static ProcessMonitor processMonitor;
    private static SchedulerService schedulerService;
    
    public static void main(String[] args) {
        
        logger.info("╔══════════════════════════════════════════════════════════════╗");
        logger.info("║  Simple Java Process Orchestrator (SJPO) v1.0.0              ║");
        logger.info("║  Starting up...                                              ║");
        logger.info("╚══════════════════════════════════════════════════════════════╝");
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received, stopping SJPO...");
            shutdown();
        }));
        
        try {
            
            // Initialize components
            orchestrator = new ProcessOrchestrator();
            NotificationService notificationService = new NotificationService();
            BackupManager backupManager = new BackupManager(notificationService);
            processMonitor = new ProcessMonitor();
            
            // Initialize orchestrator
            orchestrator.initialize();
            
            // Start process monitoring
            processMonitor.startMonitoring(orchestrator.getAllInstances(), 2000);
            
            // Initialize and start scheduler
            schedulerService = new SchedulerService(orchestrator, backupManager);
            schedulerService.setContextData();
            schedulerService.start();
            
            logger.info("All components initialized successfully");
            logger.info("Starting Terminal UI...");
            
            // Start UI (blocking call)
            MainWindow mainWindow = new MainWindow(orchestrator, processMonitor, backupManager);
            mainWindow.start();
            
            // UI closed, shutdown
            logger.info("UI closed, initiating shutdown...");
            shutdown();
            
        } catch (IOException e) {
            logger.error("Failed to initialize Terminal UI: {}", e.getMessage(), e);
            System.exit(1);
        } catch (SchedulerException e) {
            logger.error("Failed to initialize Scheduler: {}", e.getMessage(), e);
            System.exit(1);
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage(), e);
            System.exit(1);
        }
        
    }
    
    /**
     * Shutdown all components gracefully
     */
    private static void shutdown() {
        
        logger.info("Shutting down SJPO...");
        
        try {
            
            // Stop scheduler
            if (schedulerService != null) {
                logger.info("Stopping scheduler...");
                schedulerService.stop();
            }
            
            // Stop monitoring
            if (processMonitor != null) {
                logger.info("Stopping process monitor...");
                processMonitor.stopMonitoring();
            }
            
            // Stop orchestrator (stops all instances)
            if (orchestrator != null) {
                logger.info("Stopping process orchestrator...");
                orchestrator.shutdown();
            }
            
            logger.info("╔══════════════════════════════════════════════════════════════╗");
            logger.info("║  SJPO shut down successfully. Goodbye!                       ║");
            logger.info("╚══════════════════════════════════════════════════════════════╝");
            
        } catch (Exception e) {
            logger.error("Error during shutdown: {}", e.getMessage(), e);
        }
        
    }
    
}
