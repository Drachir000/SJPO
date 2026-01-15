package de.drachir000.sjpo.config;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for a single managed Java instance
 */
@Getter
@Setter
public class InstanceConfig {
	
	private String id;
	private String name;
	private boolean enabled = true;
	
	// Java execution settings
	private String javaPath = "java";
	private String workingDirectory = ".";
	private String mainClass;
	private String jarFile;
	private List<String> vmArguments = new ArrayList<>();
	private List<String> programArguments = new ArrayList<>();
	private Map<String, String> environmentVariables = new HashMap<>();
	
	// Restart settings
	private RestartConfig restart = new RestartConfig();
	
	// Backup settings
	private BackupConfig backup;
	
	// Scheduled commands
	private List<ScheduledCommand> scheduledCommands = new ArrayList<>();
	
	// Monitoring settings
	private MonitoringConfig monitoring = new MonitoringConfig();
	
	/**
	 * Restart configuration
	 */
	@Setter
	@Getter
	public static class RestartConfig {
		private boolean autoRestart = true;
		private List<String> scheduledRestarts = new ArrayList<>(); // Cron expressions
		private int maxRestartsInWindow = 5;
		private long restartWindowMinutes = 10;
		private long cooldownSeconds = 5;
		
	}
	
	/**
	 * Backup configuration
	 */
	@Setter
	@Getter
	public static class BackupConfig {
		private boolean enabled = false;
		private String backupDirectory = "./backups";
		private List<String> includePaths = new ArrayList<>();
		private List<String> excludePatterns = new ArrayList<>();
		private List<String> schedules = new ArrayList<>(); // Cron expressions
		private int maxBackups = 10;
		private NotificationConfig notification;
		
	}
	
	/**
	 * Notification configuration for backups
	 */
	@Setter
	@Getter
	public static class NotificationConfig {
		private SmtpConfig smtp;
		private NtfyConfig ntfy;
		
	}
	
	/**
	 * SMTP configuration
	 */
	@Setter
	@Getter
	public static class SmtpConfig {
		private boolean enabled = false;
		private String host;
		private int port = 587;
		private String username;
		private String password;
		private boolean useTls = true;
		private String from;
		private List<String> to = new ArrayList<>();
		private String subject = "SJPO Backup Notification";
		
	}
	
	/**
	 * Ntfy configuration
	 */
	@Setter
	@Getter
	public static class NtfyConfig {
		private boolean enabled = false;
		private String server = "https://ntfy.sh";
		private String topic;
		private String token;
		private String priority = "default";
		
	}
	
	/**
	 * Scheduled command configuration
	 */
	@Setter
	@Getter
	public static class ScheduledCommand {
		private String name;
		private String command;
		private String schedule; // Cron expression
		private boolean enabled = true;
		
	}
	
	/**
	 * Monitoring configuration
	 */
	@Setter
	@Getter
	public static class MonitoringConfig {
		private boolean enabled = true;
		private long updateIntervalMs = 2000;
		
	}
	
}