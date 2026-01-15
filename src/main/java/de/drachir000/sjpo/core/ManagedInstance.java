package de.drachir000.sjpo.core;

import de.drachir000.sjpo.config.InstanceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a managed Java instance with its process and state
 */
public class ManagedInstance {
	
	private static final Logger logger = LoggerFactory.getLogger(ManagedInstance.class);
	private static final int MAX_CONSOLE_LINES = 1000;
	
	@lombok.Getter
	private final InstanceConfig config;
	private final AtomicReference<Process> process = new AtomicReference<>();
	private final AtomicReference<InstanceState> state = new AtomicReference<>(InstanceState.STOPPED);
	private final AtomicBoolean permanentlyStopped = new AtomicBoolean(false);
	
	private final Deque<Instant> restartHistory = new ConcurrentLinkedDeque<>();
	private final Deque<String> consoleOutput = new ConcurrentLinkedDeque<>();
	
	@lombok.Getter
	private Instant startTime;
	private Instant lastRestartAttempt;
	@lombok.Getter
	private int exitCode = -1;
	@lombok.Getter
	private long pid = -1;
	
	public enum InstanceState {
		STOPPED,
		STARTING,
		RUNNING,
		STOPPING,
		CRASHED,
		RESTART_LIMIT_REACHED
	}
	
	public ManagedInstance(InstanceConfig config) {
		this.config = config;
	}
	
	/**
	 * Start the Java process
	 */
	public synchronized boolean start() {
		
		if (permanentlyStopped.get()) {
			logger.warn("Cannot start permanently stopped instance: {}", config.getId());
			return false;
		}
		
		if (state.get() == InstanceState.RUNNING || state.get() == InstanceState.STARTING) {
			logger.warn("Instance already running or starting: {}", config.getId());
			return false;
		}
		
		setState(InstanceState.STARTING);
		
		try {
			
			List<String> command = buildCommand();
			ProcessBuilder builder = new ProcessBuilder(command);
			
			// Set working directory
			File workDir = new File(config.getWorkingDirectory());
			if (!workDir.exists()) {
				workDir.mkdirs();
			}
			builder.directory(workDir);
			
			// Set environment variables
			if (config.getEnvironmentVariables() != null) {
				builder.environment().putAll(config.getEnvironmentVariables());
			}
			
			// Redirect error stream to output
			builder.redirectErrorStream(true);
			
			Process proc = builder.start();
			process.set(proc);
			
			// Get PID if available
			try {
				pid = proc.pid();
			} catch (Exception e) {
				logger.debug("Could not get PID for instance {}", config.getId());
			}
			
			startTime = Instant.now();
			setState(InstanceState.RUNNING);
			
			// Start console reader thread
			startConsoleReader(proc);
			
			logger.info("Started instance: {} (PID: {})", config.getId(), pid);
			return true;
			
		} catch (IOException e) {
			logger.error("Failed to start instance {}: {}", config.getId(), e.getMessage());
			setState(InstanceState.STOPPED);
			return false;
		}
		
	}
	
	/**
	 * Stop the process gracefully
	 */
	public synchronized boolean stop() {
		
		Process proc = process.get();
		if (proc == null || !proc.isAlive()) {
			setState(InstanceState.STOPPED);
			return true;
		}
		
		setState(InstanceState.STOPPING);
		logger.info("Stopping instance: {}", config.getId());
		
		proc.destroy();
		
		// Wait for graceful shutdown
		try {
			
			boolean terminated = proc.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
			
			if (!terminated) {
				
				logger.warn("Instance {} did not stop gracefully, forcing termination",
						config.getId());
				
				proc.destroyForcibly();
				proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
				
			}
			
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.error("Interrupted while stopping instance {}", config.getId());
		}
		
		setState(InstanceState.STOPPED);
		return true;
		
	}
	
	/**
	 * Force kill the process
	 */
	public synchronized void kill() {
		
		Process proc = process.get();
		
		if (proc != null && proc.isAlive()) {
			logger.warn("Force killing instance: {}", config.getId());
			proc.destroyForcibly();
		}
		
		setState(InstanceState.STOPPED);
		
	}
	
	/**
	 * Restart the instance
	 */
	public boolean restart() {
		
		logger.info("Restarting instance: {}", config.getId());
		recordRestart();
		
		if (shouldPreventRestart()) {
			logger.error("Restart limit reached for instance: {}", config.getId());
			setState(InstanceState.RESTART_LIMIT_REACHED);
			return false;
		}
		
		stop();
		
		// Cooldown period
		long cooldownMs = config.getRestart().getCooldownSeconds() * 1000;
		if (cooldownMs > 0) {
			try {
				Thread.sleep(cooldownMs);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		
		return start();
		
	}
	
	/**
	 * Stop the instance permanently (disable auto-restart)
	 */
	public void stopPermanently() {
		permanentlyStopped.set(true);
		stop();
		logger.info("Instance {} stopped permanently", config.getId());
	}
	
	/**
	 * Resume a permanently stopped instance
	 */
	public void resume() {
		permanentlyStopped.set(false);
		logger.info("Instance {} resumed, can be started again", config.getId());
	}
	
	/**
	 * Check if process is alive
	 */
	public boolean isAlive() {
		Process proc = process.get();
		return proc != null && proc.isAlive();
	}
	
	/**
	 * Build the command to execute the Java process
	 */
	private List<String> buildCommand() {
		
		List<String> command = new ArrayList<>();
		command.add(config.getJavaPath());
		
		// Add VM arguments
		command.addAll(config.getVmArguments());
		
		// Add JAR or main class
		if (config.getJarFile() != null && !config.getJarFile().isBlank()) {
			command.add("-jar");
			command.add(config.getJarFile());
		} else if (config.getMainClass() != null && !config.getMainClass().isBlank()) {
			command.add(config.getMainClass());
		} else {
			throw new IllegalStateException("Neither jarFile nor mainClass specified");
		}
		
		// Add program arguments
		command.addAll(config.getProgramArguments());
		
		return command;
		
	}
	
	/**
	 * Start thread to read console output
	 */
	private void startConsoleReader(Process proc) {
		
		Thread reader = new Thread(() -> {
			
			try (BufferedReader br = new BufferedReader(
					new InputStreamReader(proc.getInputStream()))) {
				
				String line;
				
				while ((line = br.readLine()) != null) {
					addConsoleOutput(line);
				}
				
			} catch (IOException e) {
				if (proc.isAlive()) {
					logger.error("Error reading console output for {}: {}",
							config.getId(), e.getMessage());
				}
			}
			
			// Process has terminated
			try {
				
				exitCode = proc.waitFor();
				logger.info("Instance {} terminated with exit code {}",
						config.getId(), exitCode);
				
				if (exitCode != 0) {
					setState(InstanceState.CRASHED);
				} else {
					setState(InstanceState.STOPPED);
				}
				
				// Handle auto-restart
				if (!permanentlyStopped.get() && config.getRestart().isAutoRestart()) {
					handleAutoRestart();
				}
				
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			
		}, "ConsoleReader-" + config.getId());
		
		reader.setDaemon(true);
		reader.start();
		
	}
	
	/**
	 * Handle automatic restart after crash
	 */
	private void handleAutoRestart() {
		
		recordRestart();
		
		if (shouldPreventRestart()) {
			logger.error("Instance {} has reached restart limit, auto-restart disabled",
					config.getId());
			setState(InstanceState.RESTART_LIMIT_REACHED);
			return;
		}
		
		logger.info("Auto-restarting instance: {}", config.getId());
		
		// Cooldown before restart
		long cooldownMs = config.getRestart().getCooldownSeconds() * 1000;
		if (cooldownMs > 0) {
			try {
				Thread.sleep(cooldownMs);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
		
		start();
		
	}
	
	/**
	 * Record a restart attempt
	 */
	private void recordRestart() {
		
		lastRestartAttempt = Instant.now();
		restartHistory.addLast(lastRestartAttempt);
		
		// Clean old history outside the window
		long windowMs = config.getRestart().getRestartWindowMinutes() * 60 * 1000;
		Instant cutoff = Instant.now().minusMillis(windowMs);
		
		while (!restartHistory.isEmpty() && restartHistory.peekFirst().isBefore(cutoff)) {
			restartHistory.pollFirst();
		}
		
	}
	
	/**
	 * Check if restart should be prevented due to too many restarts
	 */
	private boolean shouldPreventRestart() {
		int maxRestarts = config.getRestart().getMaxRestartsInWindow();
		return restartHistory.size() >= maxRestarts;
	}
	
	/**
	 * Add output to console buffer
	 */
	private void addConsoleOutput(String line) {
		
		consoleOutput.addLast(line);
		
		while (consoleOutput.size() > MAX_CONSOLE_LINES) {
			consoleOutput.pollFirst();
		}
		
	}
	
	/**
	 * Get recent console output
	 */
	public List<String> getConsoleOutput(int lines) {
		
		List<String> output = new ArrayList<>(consoleOutput);
		
		if (lines > 0 && output.size() > lines) {
			return output.subList(output.size() - lines, output.size());
		}
		
		return output;
		
	}
	
	/**
	 * Get uptime duration
	 */
	public Duration getUptime() {
		
		if (startTime == null || !isAlive()) {
			return Duration.ZERO;
		}
		
		return Duration.between(startTime, Instant.now());
		
	}
	
	// Atomic Getters/Setters
	
	public InstanceState getState() {
		return state.get();
	}
	
	public boolean isPermanentlyStopped() {
		return permanentlyStopped.get();
	}
	
	public int getRestartCount() {
		return restartHistory.size();
	}
	
	private void setState(InstanceState newState) {
		state.set(newState);
	}
	
}
