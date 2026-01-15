package de.drachir000.sjpo.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Manages loading and saving of instance configurations
 */
public class ConfigManager {
	
	private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
	
	private static final String CONFIG_DIR = "./instances";
	private static final String CONFIG_EXTENSION = ".json";
	
	private final Gson gson;
	
	public ConfigManager() {
		this.gson = new GsonBuilder()
				.setPrettyPrinting()
				.create();
	}
	
	/**
	 * Load all instance configurations from the instances directory
	 */
	public List<InstanceConfig> loadAllConfigs() {
		
		List<InstanceConfig> configs = new ArrayList<>();
		File configDir = new File(CONFIG_DIR);
		
		if (!configDir.exists()) {
			
			logger.info("Creating instances directory: {}", CONFIG_DIR);
			configDir.mkdirs();
			
			createExampleConfig();
			
			return configs;
			
		}
		
		try (Stream<Path> paths = Files.walk(configDir.toPath(), 1)) {
			paths.filter(Files::isRegularFile)
					.filter(p -> p.toString().endsWith(CONFIG_EXTENSION))
					.forEach(path -> {
						try {
							
							InstanceConfig config = loadConfig(path.toFile());
							
							if (config != null) {
								configs.add(config);
								logger.info("Loaded configuration: {} ({})",
										config.getName(), config.getId());
							}
							
						} catch (Exception e) {
							logger.error("Failed to load config from {}: {}",
									path, e.getMessage());
						}
					});
		} catch (IOException e) {
			logger.error("Failed to scan config directory: {}", e.getMessage());
		}
		
		return configs;
		
	}
	
	/**
	 * Load a specific configuration file
	 */
	public InstanceConfig loadConfig(File file) {
		
		try (FileReader reader = new FileReader(file)) {
			
			InstanceConfig config = gson.fromJson(reader, InstanceConfig.class);
			
			// Validate configuration
			if (config.getId() == null || config.getId().isBlank()) {
				logger.error("Configuration {} missing required field: id", file.getName());
				return null;
			}
			
			if (config.getName() == null || config.getName().isBlank()) {
				config.setName(config.getId());
			}
			
			return config;
			
		} catch (IOException e) {
			logger.error("Failed to read config file {}: {}", file, e.getMessage());
			return null;
		}
		
	}
	
	/**
	 * Save configuration to file
	 */
	public void saveConfig(InstanceConfig config) throws IOException {
		
		File configDir = new File(CONFIG_DIR);
		if (!configDir.exists()) {
			configDir.mkdirs();
		}
		
		File configFile = new File(configDir, config.getId() + CONFIG_EXTENSION);
		try (FileWriter writer = new FileWriter(configFile)) {
			gson.toJson(config, writer);
			logger.info("Saved configuration: {}", config.getId());
		}
		
	}
	
	/**
	 * Create an example configuration file
	 */
	private void createExampleConfig() {
		
		InstanceConfig example = new InstanceConfig();
		example.setId("example");
		example.setName("Example Java Application");
		example.setEnabled(false);
		example.setJavaPath("java");
		example.setWorkingDirectory("./apps/example");
		example.setJarFile("application.jar");
		
		// VM arguments
		example.getVmArguments().add("-Xmx2G");
		example.getVmArguments().add("-Xms512M");
		example.getVmArguments().add("-XX:+UseG1GC");
		
		// Program arguments
		example.getProgramArguments().add("--port=8080");
		example.getProgramArguments().add("--config=config.yml");
		
		// Restart configuration
		var restart = example.getRestart();
		restart.setAutoRestart(true);
		restart.setMaxRestartsInWindow(5);
		restart.setRestartWindowMinutes(10);
		restart.setCooldownSeconds(5);
		restart.getScheduledRestarts().add("0 0 3 * * ?"); // 3 AM daily
		
		// Backup configuration
		var backup = new InstanceConfig.BackupConfig();
		backup.setEnabled(false);
		backup.setBackupDirectory("./backups/example");
		backup.getIncludePaths().add("./apps/example/data");
		backup.getIncludePaths().add("./apps/example/config");
		backup.getExcludePatterns().add("*.tmp");
		backup.getExcludePatterns().add("*.log");
		backup.getSchedules().add("0 0 2 * * ?"); // 2 AM daily
		backup.setMaxBackups(7);
		
		// SMTP notification
		var notification = new InstanceConfig.NotificationConfig();
		var smtp = new InstanceConfig.SmtpConfig();
		smtp.setEnabled(false);
		smtp.setHost("smtp.example.com");
		smtp.setPort(587);
		smtp.setUsername("user@example.com");
		smtp.setPassword("password");
		smtp.setFrom("sjpo@example.com");
		smtp.getTo().add("admin@example.com");
		notification.setSmtp(smtp);
		
		// Ntfy notification
		var ntfy = new InstanceConfig.NtfyConfig();
		ntfy.setEnabled(false);
		ntfy.setServer("https://ntfy.sh");
		ntfy.setTopic("sjpo-backups");
		ntfy.setPriority("default");
		notification.setNtfy(ntfy);
		
		backup.setNotification(notification);
		example.setBackup(backup);
		
		// Scheduled command
		var scheduledCmd = new InstanceConfig.ScheduledCommand();
		scheduledCmd.setName("Daily Status");
		scheduledCmd.setCommand("status");
		scheduledCmd.setSchedule("0 0 9 * * ?"); // 9 AM daily
		scheduledCmd.setEnabled(false);
		example.getScheduledCommands().add(scheduledCmd);
		
		try {
			saveConfig(example);
			logger.info("Created example configuration file");
		} catch (IOException e) {
			logger.error("Failed to create example config: {}", e.getMessage());
		}
		
	}
	
	/**
	 * Get the configuration directory path
	 */
	public static String getConfigDirectory() {
		return CONFIG_DIR;
	}
	
}