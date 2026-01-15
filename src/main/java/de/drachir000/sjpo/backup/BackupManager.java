package de.drachir000.sjpo.backup;

import de.drachir000.sjpo.config.InstanceConfig;
import de.drachir000.sjpo.core.ManagedInstance;
import de.drachir000.sjpo.notification.NotificationService;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Manages backup operations for instances
 */
public class BackupManager {
	
	private static final Logger logger = LoggerFactory.getLogger(BackupManager.class);
	
	private static final DateTimeFormatter TIMESTAMP_FORMAT =
			DateTimeFormatter.ofPattern("yyyy.MM.dd-HH:mm:ss");
	
	private final NotificationService notificationService;
	
	public BackupManager(NotificationService notificationService) {
		this.notificationService = notificationService;
	}
	
	/**
	 * Perform backup for an instance
	 */
	public boolean performBackup(ManagedInstance instance) {
		
		InstanceConfig config = instance.getConfig();
		InstanceConfig.BackupConfig backupConfig = config.getBackup();
		
		if (backupConfig == null || !backupConfig.isEnabled()) {
			logger.warn("Backup not configured for instance: {}", config.getId());
			return false;
		}
		
		logger.info("Starting backup for instance: {}", config.getId());
		
		try {
			
			// Create backup directory
			File backupDir = new File(backupConfig.getBackupDirectory());
			if (!backupDir.exists()) {
				backupDir.mkdirs();
			}
			
			// Generate backup filename
			String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
			String backupFilename = String.format("%s-%s.tar.gz", config.getId(), timestamp);
			File backupFile = new File(backupDir, backupFilename);
			
			// Create tar.gz archive
			long backupSize = createArchive(backupFile, config, backupConfig);
			
			// Cleanup old backups
			cleanupOldBackups(backupDir, config.getId(), backupConfig.getMaxBackups());
			
			logger.info("Backup completed: {} ({} MB)",
					backupFile.getAbsolutePath(), backupSize / (1024 * 1024));
			
			// Send notifications
			sendBackupNotification(config, backupFile, backupSize, true, null);
			
			return true;
			
		} catch (Exception e) {
			logger.error("Backup failed for instance {}: {}", config.getId(), e.getMessage(), e);
			sendBackupNotification(config, null, 0, false, e.getMessage());
			return false;
		}
		
	}
	
	/**
	 * Create tar.gz archive of specified paths
	 */
	private long createArchive(File outputFile, InstanceConfig config,
	                           InstanceConfig.BackupConfig backupConfig) throws IOException {
		
		List<Pattern> excludePatterns = compileExcludePatterns(backupConfig.getExcludePatterns());
		
		try (FileOutputStream fos = new FileOutputStream(outputFile);
		     GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(fos);
		     TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)) {
			
			taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
			taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
			
			// Add each include path to archive
			for (String includePath : backupConfig.getIncludePaths()) {
				
				File includeFile = new File(config.getWorkingDirectory(), includePath);
				
				if (!includeFile.exists()) {
					logger.warn("Backup path does not exist: {}", includeFile.getAbsolutePath());
					continue;
				}
				
				if (includeFile.isDirectory()) {
					addDirectoryToArchive(taos, includeFile, "", excludePatterns);
				} else {
					addFileToArchive(taos, includeFile, includeFile.getName());
				}
				
			}
			
			taos.finish();
			
		}
		
		return outputFile.length();
		
	}
	
	/**
	 * Add directory to tar archive recursively
	 */
	private void addDirectoryToArchive(TarArchiveOutputStream taos, File dir,
	                                   String parentPath, List<Pattern> excludePatterns)
			throws IOException {
		
		File[] files = dir.listFiles();
		if (files == null) return;
		
		for (File file : files) {
			
			String entryPath = parentPath.isEmpty() ? file.getName() :
					parentPath + "/" + file.getName();
			
			// Check if file should be excluded
			if (shouldExclude(file, excludePatterns)) {
				logger.debug("Excluding from backup: {}", entryPath);
				continue;
			}
			
			if (file.isDirectory()) {
				addDirectoryToArchive(taos, file, entryPath, excludePatterns);
			} else {
				addFileToArchive(taos, file, entryPath);
			}
			
		}
		
	}
	
	/**
	 * Add single file to tar archive
	 */
	private void addFileToArchive(TarArchiveOutputStream taos, File file, String entryPath)
			throws IOException {
		
		
		TarArchiveEntry entry = new TarArchiveEntry(file, entryPath);
		taos.putArchiveEntry(entry);
		
		try (FileInputStream fis = new FileInputStream(file)) {
			byte[] buffer = new byte[8192];
			int bytesRead;
			while ((bytesRead = fis.read(buffer)) != -1) {
				taos.write(buffer, 0, bytesRead);
			}
		}
		
		taos.closeArchiveEntry();
		
	}
	
	/**
	 * Compile exclude patterns to regex
	 */
	private List<Pattern> compileExcludePatterns(List<String> patterns) {
		
		List<Pattern> compiled = new ArrayList<>();
		
		for (String pattern : patterns) {
			// Convert glob pattern to regex
			String regex = pattern
					.replace(".", "\\.")
					.replace("*", ".*")
					.replace("?", ".");
			compiled.add(Pattern.compile(regex));
		}
		
		return compiled;
		
	}
	
	/**
	 * Check if file should be excluded
	 */
	private boolean shouldExclude(File file, List<Pattern> excludePatterns) {
		
		String filename = file.getName();
		
		for (Pattern pattern : excludePatterns) {
			if (pattern.matcher(filename).matches()) {
				return true;
			}
		}
		
		return false;
		
	}
	
	/**
	 * Cleanup old backups keeping only the most recent N backups
	 */
	private void cleanupOldBackups(File backupDir, String instanceId, int maxBackups) {
		
		File[] backupFiles = backupDir.listFiles((dir, name) ->
				name.startsWith(instanceId + "-") && name.endsWith(".tar.gz"));
		
		if (backupFiles == null || backupFiles.length <= maxBackups) {
			return;
		}
		
		// Sort by modification time (oldest first)
		Arrays.sort(backupFiles, (a, b) ->
				Long.compare(a.lastModified(), b.lastModified()));
		
		// Delete oldest backups
		int toDelete = backupFiles.length - maxBackups;
		for (int i = 0; i < toDelete; i++) {
			if (backupFiles[i].delete()) {
				logger.info("Deleted old backup: {}", backupFiles[i].getName());
			} else {
				logger.warn("Failed to delete old backup: {}", backupFiles[i].getName());
			}
		}
		
	}
	
	/**
	 * Send backup notification
	 */
	private void sendBackupNotification(InstanceConfig config, File backupFile,
	                                    long backupSize, boolean success, String error) {
		
		InstanceConfig.BackupConfig backupConfig = config.getBackup();
		if (backupConfig.getNotification() == null) {
			return;
		}
		
		String subject = success ?
				"Backup Successful: " + config.getName() :
				"Backup Failed: " + config.getName();
		
		StringBuilder message = new StringBuilder();
		message.append("Instance: ").append(config.getName()).append("\n");
		message.append("ID: ").append(config.getId()).append("\n");
		message.append("Status: ").append(success ? "SUCCESS" : "FAILED").append("\n");
		message.append("Time: ").append(LocalDateTime.now()).append("\n");
		
		if (success && backupFile != null) {
			message.append("File: ").append(backupFile.getName()).append("\n");
			message.append("Size: ").append(backupSize / (1024 * 1024)).append(" MB\n");
			message.append("Path: ").append(backupFile.getAbsolutePath()).append("\n");
		} else if (error != null) {
			message.append("Error: ").append(error).append("\n");
		}
		
		notificationService.sendNotification(
				backupConfig.getNotification(),
				subject,
				message.toString()
		);
		
	}
	
}
