package de.drachir000.sjpo.monitor;

import de.drachir000.sjpo.core.ManagedInstance;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Monitors CPU and memory usage of managed instances
 */
public class ProcessMonitor {
	
	private static final Logger logger = LoggerFactory.getLogger(ProcessMonitor.class);
	
	private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
	
	private final Map<String, ProcessStats> statsMap = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
	
	/**
	 * Process statistics
	 */
	@Getter
	public static class ProcessStats {
		
		private double cpuPercent;
		private long memoryMB;
		private long lastUpdateTime;
		
		public void update(double cpu, long memory) {
			this.cpuPercent = cpu;
			this.memoryMB = memory;
			this.lastUpdateTime = System.currentTimeMillis();
		}
		
	}
	
	/**
	 * Start monitoring instances
	 */
	public void startMonitoring(Iterable<ManagedInstance> instances, long updateIntervalMs) {
		
		scheduler.scheduleAtFixedRate(() -> {
			for (ManagedInstance instance : instances) {
				if (instance.isAlive() && instance.getConfig().getMonitoring().isEnabled()) {
					updateStats(instance);
				}
			}
		}, 0, updateIntervalMs, TimeUnit.MILLISECONDS);
		
		logger.info("Process monitoring started");
		
	}
	
	/**
	 * Stop monitoring
	 */
	public void stopMonitoring() {
		
		scheduler.shutdown();
		try {
			scheduler.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			scheduler.shutdownNow();
		}
		
		logger.info("Process monitoring stopped");
		
	}
	
	/**
	 * Update statistics for an instance
	 */
	private void updateStats(ManagedInstance instance) {
		
		try {
			
			long pid = instance.getPid();
			if (pid <= 0) {
				return;
			}
			
			ProcessStats stats = getOrCreateStats(instance.getConfig().getId());
			
			if (IS_WINDOWS) {
				updateStatsWindows(pid, stats);
			} else {
				updateStatsLinux(pid, stats);
			}
			
		} catch (Exception e) {
			logger.debug("Failed to update stats for {}: {}",
					instance.getConfig().getId(), e.getMessage());
		}
		
	}
	
	/**
	 * Update stats on Linux using ps command
	 */
	private void updateStatsLinux(long pid, ProcessStats stats) {
		try {
			
			// Use ps to get CPU and memory
			Process proc = Runtime.getRuntime().exec(new String[]{
					"ps", "-p", String.valueOf(pid), "-o", "%cpu,rss", "--no-headers"
			});
			
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(proc.getInputStream()))) {
				
				String line = reader.readLine();
				
				if (line != null) {
					String[] parts = line.trim().split("\\s+");
					if (parts.length >= 2) {
						
						double cpu = Double.parseDouble(parts[0]);
						long memoryKB = Long.parseLong(parts[1]);
						long memoryMB = memoryKB / 1024;
						
						stats.update(cpu, memoryMB);
						
					}
				}
				
			}
			
			proc.waitFor(1, TimeUnit.SECONDS);
			
		} catch (Exception e) {
			logger.trace("Error reading Linux process stats: {}", e.getMessage());
		}
	}
	
	/**
	 * Update stats on Windows using wmic command
	 */
	private void updateStatsWindows(long pid, ProcessStats stats) {
		try {
			
			// Get memory using wmic
			Process memProc = Runtime.getRuntime().exec(new String[]{
					"wmic", "process", "where", "ProcessId=" + pid,
					"get", "WorkingSetSize", "/format:value"
			});
			
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(memProc.getInputStream()))) {
				
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.startsWith("WorkingSetSize=")) {
						
						long memoryBytes = Long.parseLong(line.substring(15));
						long memoryMB = memoryBytes / (1024 * 1024);
						
						// TODO cpu usage on windows
						stats.update(0.0, memoryMB);
						break;
						
					}
				}
				
			}
			
			memProc.waitFor(2, TimeUnit.SECONDS);
			
		} catch (Exception e) {
			logger.trace("Error reading Windows process stats: {}", e.getMessage());
		}
	}
	
	/**
	 * Get or create stats object for instance
	 */
	private ProcessStats getOrCreateStats(String instanceId) {
		return statsMap.computeIfAbsent(instanceId, k -> new ProcessStats());
	}
	
	/**
	 * Get stats for an instance
	 */
	public ProcessStats getStats(String instanceId) {
		return statsMap.get(instanceId);
	}
	
	/**
	 * Get system CPU load
	 */
	public double getSystemCpuLoad() {
		
		try {
			// Try to use com.sun.management.OperatingSystemMXBean if available
			if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
				return sunOsBean.getSystemCpuLoad() * 100;
			}
		} catch (Exception e) {
			logger.trace("Could not get system CPU load: {}", e.getMessage());
		}
		
		return -1.0;
		
	}
	
	/**
	 * Get system memory info
	 */
	public SystemMemoryInfo getSystemMemoryInfo() {
		
		try {
			if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
				
				long totalMemory = sunOsBean.getTotalPhysicalMemorySize() / (1024 * 1024);
				long freeMemory = sunOsBean.getFreePhysicalMemorySize() / (1024 * 1024);
				long usedMemory = totalMemory - freeMemory;
				
				return new SystemMemoryInfo(totalMemory, usedMemory, freeMemory);
				
			}
		} catch (Exception e) {
			logger.trace("Could not get system memory info: {}", e.getMessage());
		}
		
		return new SystemMemoryInfo(0, 0, 0);
		
	}
	
	/**
	 * System memory information
	 */
	public record SystemMemoryInfo(long totalMB, long usedMB, long freeMB) {
		public double getUsagePercent() {
			if (totalMB == 0) return 0.0;
			return (usedMB * 100.0) / totalMB;
		}
	}
	
}
