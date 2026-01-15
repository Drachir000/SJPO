package de.drachir000.sjpo.core;

import de.drachir000.sjpo.config.ConfigManager;
import de.drachir000.sjpo.config.InstanceConfig;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main orchestrator for managing all Java process instances
 */
public class ProcessOrchestrator {
	
	private static final Logger logger = LoggerFactory.getLogger(ProcessOrchestrator.class);
	
	private final ConfigManager configManager;
	private final Map<String, ManagedInstance> instances = new ConcurrentHashMap<>();
	/**
	 * -- GETTER --
	 * Check if orchestrator is running
	 */
	@Getter
	private volatile boolean running = false;
	
	public ProcessOrchestrator() {
		this.configManager = new ConfigManager();
	}
	
	/**
	 * Initialize and load all configurations
	 */
	public void initialize() {
		
		logger.info("Initializing Process Orchestrator...");
		
		List<InstanceConfig> configs = configManager.loadAllConfigs();
		logger.info("Loaded {} instance configuration(s)", configs.size());
		
		for (InstanceConfig config : configs) {
			
			ManagedInstance instance = new ManagedInstance(config);
			instances.put(config.getId(), instance);
			
			if (config.isEnabled()) {
				logger.info("Auto-starting enabled instance: {}", config.getId());
				instance.start();
			} else {
				logger.info("Instance {} is disabled, not starting", config.getId());
			}
			
		}
		
		running = true;
		logger.info("Process Orchestrator initialized successfully");
		
	}
	
	/**
	 * Shutdown all instances gracefully
	 */
	public void shutdown() {
		
		logger.info("Shutting down Process Orchestrator...");
		running = false;
		
		for (ManagedInstance instance : instances.values()) {
			if (instance.isAlive()) {
				logger.info("Stopping instance: {}", instance.getConfig().getId());
				instance.stop();
			}
		}
		
		logger.info("Process Orchestrator shut down");
		
	}
	
	/**
	 * Get instance by ID
	 */
	public ManagedInstance getInstance(String id) {
		return instances.get(id);
	}
	
	/**
	 * Get all instances
	 */
	public Collection<ManagedInstance> getAllInstances() {
		return instances.values();
	}
	
	/**
	 * Start an instance
	 */
	public boolean startInstance(String id) {
		
		ManagedInstance instance = instances.get(id);
		if (instance == null) {
			logger.error("Instance not found: {}", id);
			return false;
		}
		
		return instance.start();
		
	}
	
	/**
	 * Stop an instance
	 */
	public boolean stopInstance(String id) {
		
		ManagedInstance instance = instances.get(id);
		if (instance == null) {
			logger.error("Instance not found: {}", id);
			return false;
		}
		
		return instance.stop();
		
	}
	
	/**
	 * Restart an instance
	 */
	public boolean restartInstance(String id) {
		
		ManagedInstance instance = instances.get(id);
		if (instance == null) {
			logger.error("Instance not found: {}", id);
			return false;
		}
		
		return instance.restart();
		
	}
	
	/**
	 * Kill an instance forcefully
	 */
	public void killInstance(String id) {
		
		ManagedInstance instance = instances.get(id);
		if (instance == null) {
			logger.error("Instance not found: {}", id);
			return;
		}
		
		instance.kill();
		
	}
	
	/**
	 * Stop an instance permanently
	 */
	public void stopInstancePermanently(String id) {
		
		ManagedInstance instance = instances.get(id);
		if (instance == null) {
			logger.error("Instance not found: {}", id);
			return;
		}
		
		instance.stopPermanently();
		
	}
	
	/**
	 * Resume a permanently stopped instance
	 */
	public void resumeInstance(String id) {
		
		ManagedInstance instance = instances.get(id);
		if (instance == null) {
			logger.error("Instance not found: {}", id);
			return;
		}
		
		instance.resume();
		
	}
	
	/**
	 * Get count of running instances
	 */
	public int getRunningCount() {
		return (int) instances.values().stream()
				.filter(ManagedInstance::isAlive)
				.count();
	}
	
	/**
	 * Get count of total instances
	 */
	public int getTotalCount() {
		return instances.size();
	}
	
}
