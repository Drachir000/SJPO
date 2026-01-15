package de.drachir000.sjpo.scheduler;

import de.drachir000.sjpo.backup.BackupManager;
import de.drachir000.sjpo.config.InstanceConfig;
import de.drachir000.sjpo.core.ManagedInstance;
import de.drachir000.sjpo.core.ProcessOrchestrator;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Manages scheduled tasks for instances using Quartz Scheduler
 */
public class SchedulerService {
	
	private static final Logger logger = LoggerFactory.getLogger(SchedulerService.class);
	
	private final Scheduler scheduler;
	private final ProcessOrchestrator orchestrator;
	private final BackupManager backupManager;
	
	public SchedulerService(ProcessOrchestrator orchestrator, BackupManager backupManager)
			throws SchedulerException {
		this.orchestrator = orchestrator;
		this.backupManager = backupManager;
		this.scheduler = StdSchedulerFactory.getDefaultScheduler();
	}
	
	/**
	 * Initialize and start scheduler
	 */
	public void start() throws SchedulerException {
		
		scheduler.start();
		logger.info("Scheduler service started");
		
		// Schedule tasks for all instances
		for (ManagedInstance instance : orchestrator.getAllInstances()) {
			scheduleInstanceTasks(instance);
		}
		
	}
	
	/**
	 * Stop scheduler
	 */
	public void stop() throws SchedulerException {
		scheduler.shutdown(true);
		logger.info("Scheduler service stopped");
	}
	
	/**
	 * Schedule all tasks for an instance
	 */
	public void scheduleInstanceTasks(ManagedInstance instance) throws SchedulerException {
		
		InstanceConfig config = instance.getConfig();
		
		// Schedule restarts
		scheduleRestarts(config);
		
		// Schedule backups
		scheduleBackups(config);
		
		// Schedule commands
		scheduleCommands(config);
		
	}
	
	/**
	 * Schedule restart tasks
	 */
	private void scheduleRestarts(InstanceConfig config) throws SchedulerException {
		
		List<String> schedules = config.getRestart().getScheduledRestarts();
		
		for (int i = 0; i < schedules.size(); i++) {
			
			String cronExpression = schedules.get(i);
			
			JobDetail job = JobBuilder.newJob(RestartJob.class)
					.withIdentity("restart-" + config.getId() + "-" + i, "restarts")
					.usingJobData("instanceId", config.getId())
					.build();
			
			Trigger trigger = TriggerBuilder.newTrigger()
					.withIdentity("restart-trigger-" + config.getId() + "-" + i, "restarts")
					.withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
					.build();
			
			scheduler.scheduleJob(job, trigger);
			logger.info("Scheduled restart for {} with cron: {}", config.getId(), cronExpression);
			
		}
		
	}
	
	/**
	 * Schedule backup tasks
	 */
	private void scheduleBackups(InstanceConfig config) throws SchedulerException {
		
		InstanceConfig.BackupConfig backupConfig = config.getBackup();
		
		if (backupConfig == null || !backupConfig.isEnabled()) {
			return;
		}
		
		List<String> schedules = backupConfig.getSchedules();
		
		for (int i = 0; i < schedules.size(); i++) {
			
			String cronExpression = schedules.get(i);
			
			JobDetail job = JobBuilder.newJob(BackupJob.class)
					.withIdentity("backup-" + config.getId() + "-" + i, "backups")
					.usingJobData("instanceId", config.getId())
					.build();
			
			Trigger trigger = TriggerBuilder.newTrigger()
					.withIdentity("backup-trigger-" + config.getId() + "-" + i, "backups")
					.withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
					.build();
			
			scheduler.scheduleJob(job, trigger);
			logger.info("Scheduled backup for {} with cron: {}", config.getId(), cronExpression);
			
		}
		
	}
	
	/**
	 * Schedule command tasks
	 */
	private void scheduleCommands(InstanceConfig config) throws SchedulerException {
		
		List<InstanceConfig.ScheduledCommand> commands = config.getScheduledCommands();
		
		for (int i = 0; i < commands.size(); i++) {
			
			InstanceConfig.ScheduledCommand cmd = commands.get(i);
			
			if (!cmd.isEnabled()) {
				continue;
			}
			
			JobDetail job = JobBuilder.newJob(CommandJob.class)
					.withIdentity("command-" + config.getId() + "-" + i, "commands")
					.usingJobData("instanceId", config.getId())
					.usingJobData("command", cmd.getCommand())
					.build();
			
			Trigger trigger = TriggerBuilder.newTrigger()
					.withIdentity("command-trigger-" + config.getId() + "-" + i, "commands")
					.withSchedule(CronScheduleBuilder.cronSchedule(cmd.getSchedule()))
					.build();
			
			scheduler.scheduleJob(job, trigger);
			logger.info("Scheduled command '{}' for {} with cron: {}",
					cmd.getName(), config.getId(), cmd.getSchedule());
			
		}
		
	}
	
	/**
	 * Job for scheduled restarts
	 */
	public static class RestartJob implements Job {
		
		@Override
		public void execute(JobExecutionContext context) {
			
			String instanceId = context.getJobDetail().getJobDataMap().getString("instanceId");
			
			try {
				
				ProcessOrchestrator orchestrator =
						(ProcessOrchestrator) context.getScheduler().getContext().get("orchestrator");
				
				logger.info("Executing scheduled restart for instance: {}", instanceId);
				orchestrator.restartInstance(instanceId);
				
			} catch (SchedulerException e) {
				logger.error("Error executing restart job: {}", e.getMessage());
			}
			
		}
		
	}
	
	/**
	 * Job for scheduled backups
	 */
	public static class BackupJob implements Job {
		
		@Override
		public void execute(JobExecutionContext context) {
			
			String instanceId = context.getJobDetail().getJobDataMap().getString("instanceId");
			
			try {
				
				ProcessOrchestrator orchestrator =
						(ProcessOrchestrator) context.getScheduler().getContext().get("orchestrator");
				BackupManager backupManager =
						(BackupManager) context.getScheduler().getContext().get("backupManager");
				
				ManagedInstance instance = orchestrator.getInstance(instanceId);
				if (instance != null) {
					logger.info("Executing scheduled backup for instance: {}", instanceId);
					backupManager.performBackup(instance);
				}
				
			} catch (SchedulerException e) {
				logger.error("Error executing backup job: {}", e.getMessage());
			}
			
		}
		
	}
	
	/**
	 * Job for scheduled commands
	 */
	public static class CommandJob implements Job {
		
		@Override
		public void execute(JobExecutionContext context) {
			
			String instanceId = context.getJobDetail().getJobDataMap().getString("instanceId");
			String command = context.getJobDetail().getJobDataMap().getString("command");
			
			logger.info("Executing scheduled command '{}' for instance: {}", command, instanceId);
			
			// Handle specific commands
			try {
				
				ProcessOrchestrator orchestrator =
						(ProcessOrchestrator) context.getScheduler().getContext().get("orchestrator");
				
				switch (command.toLowerCase()) {
					case "restart":
						orchestrator.restartInstance(instanceId);
						break;
					case "stop":
						orchestrator.stopInstance(instanceId);
						break;
					case "start":
						orchestrator.startInstance(instanceId);
						break;
					default:
						logger.warn("Unknown scheduled command: {}", command); // TODO allow custom commands in instance terminals
				}
				
			} catch (SchedulerException e) {
				logger.error("Error executing command job: {}", e.getMessage());
			}
			
		}
		
	}
	
	/**
	 * Set scheduler context data
	 */
	public void setContextData() throws SchedulerException {
		scheduler.getContext().put("orchestrator", orchestrator);
		scheduler.getContext().put("backupManager", backupManager);
	}
	
}
