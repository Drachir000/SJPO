package de.drachir000.sjpo.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.ActionListDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import de.drachir000.sjpo.backup.BackupManager;
import de.drachir000.sjpo.core.ManagedInstance;
import de.drachir000.sjpo.core.ProcessOrchestrator;
import de.drachir000.sjpo.monitor.ProcessMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main window for the Terminal UI
 */
public class MainWindow {
	
	private static final Logger logger = LoggerFactory.getLogger(MainWindow.class);
	
	private final ProcessOrchestrator orchestrator;
	private final ProcessMonitor processMonitor;
	private final BackupManager backupManager;
	
	private MultiWindowTextGUI gui;
	private BasicWindow mainWindow;
	private Panel mainPanel;
	private Table<String> instanceTable;
	private Label statusLabel;
	private Label systemStatsLabel;
	
	private final AtomicBoolean running = new AtomicBoolean(true);
	private final ScheduledExecutorService updateExecutor = Executors.newSingleThreadScheduledExecutor();
	
	public MainWindow(ProcessOrchestrator orchestrator, ProcessMonitor processMonitor,
	                  BackupManager backupManager) {
		this.orchestrator = orchestrator;
		this.processMonitor = processMonitor;
		this.backupManager = backupManager;
	}
	
	/**
	 * Initialize and display the UI (blocks thread)
	 */
	public void start() throws IOException {
		
		// Create terminal
		DefaultTerminalFactory terminalFactory = new DefaultTerminalFactory();
		terminalFactory.setInitialTerminalSize(new TerminalSize(120, 40));
		Terminal terminal = terminalFactory.createTerminal();
		
		Screen screen = new TerminalScreen(terminal);
		screen.startScreen();
		
		// Create GUI
		gui = new MultiWindowTextGUI(screen, new DefaultWindowManager(),
				new EmptySpace(TextColor.ANSI.BLUE));
		
		// Build main window
		buildMainWindow();
		
		// Start update task
		updateExecutor.scheduleAtFixedRate(this::updateDisplay, 0, 1, TimeUnit.SECONDS);
		
		// Display window
		gui.addWindowAndWait(mainWindow);
		
		// Cleanup
		running.set(false);
		updateExecutor.shutdown();
		screen.stopScreen();
		
	}
	
	/**
	 * Build the main window UI
	 */
	private void buildMainWindow() {
		
		mainWindow = new BasicWindow("SJPO - Simple Java Process Orchestrator");
		mainWindow.setHints(List.of(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS));
		
		mainPanel = new Panel(new BorderLayout());
		
		// Header
		Panel headerPanel = new Panel(new GridLayout(2));
		headerPanel.addComponent(new Label("═══ SJPO - Java Process Orchestrator ═══")
				.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT));
		headerPanel.addComponent(new Label("Press Q to quit")
				.setForegroundColor(TextColor.ANSI.YELLOW));
		
		mainPanel.addComponent(headerPanel, BorderLayout.Location.TOP);
		
		// System stats
		systemStatsLabel = new Label("");
		mainPanel.addComponent(systemStatsLabel, BorderLayout.Location.TOP);
		
		// Instance table
		instanceTable = new Table<>("ID", "Name", "Status", "PID", "Uptime",
				"CPU%", "RAM(MB)", "Restarts");
		instanceTable.setSelectAction(() -> {
			int selectedRow = instanceTable.getSelectedRow();
			if (selectedRow >= 0) {
				String instanceId = instanceTable.getTableModel().getRow(selectedRow).get(0);
				showInstanceMenu(instanceId);
			}
		});
		
		Panel tablePanel = new Panel(new LinearLayout(Direction.VERTICAL));
		tablePanel.addComponent(new Label("═══ Instances ═══")
				.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT));
		tablePanel.addComponent(instanceTable.withBorder(Borders.singleLine()));
		
		mainPanel.addComponent(tablePanel, BorderLayout.Location.CENTER);
		
		// Status bar
		Panel statusPanel = new Panel(new GridLayout(1));
		statusLabel = new Label("");
		statusPanel.addComponent(statusLabel);
		
		mainPanel.addComponent(statusPanel, BorderLayout.Location.BOTTOM);
		
		mainWindow.setComponent(mainPanel);
		
		// Handle window close
		mainWindow.addWindowListener(new WindowListenerAdapter() {
			@Override
			public void onUnhandledInput(Window basePane, KeyStroke keyStroke,
			                             AtomicBoolean hasBeenHandled) {
				if (keyStroke.getCharacter() != null &&
						(keyStroke.getCharacter() == 'q' || keyStroke.getCharacter() == 'Q')) {
					confirmExit();
					hasBeenHandled.set(true);
				}
			}
		});
		
	}
	
	/**
	 * Update display with current data
	 */
	private void updateDisplay() {
		
		if (!running.get()) return;
		
		try {
			gui.getGUIThread().invokeLater(() -> {
				updateSystemStats();
				updateInstanceTable();
				updateStatusBar();
			});
		} catch (Exception e) {
			logger.error("Error updating display: {}", e.getMessage());
		}
		
	}
	
	/**
	 * Update system statistics
	 */
	private void updateSystemStats() {
		
		ProcessMonitor.SystemMemoryInfo memInfo = processMonitor.getSystemMemoryInfo();
		double cpuLoad = processMonitor.getSystemCpuLoad();
		
		String stats = String.format("System: CPU: %.1f%% | Memory: %d/%d MB (%.1f%%) | " +
						"Instances: %d running / %d total",
				cpuLoad >= 0 ? cpuLoad : 0.0,
				memInfo.usedMB(), memInfo.totalMB(), memInfo.getUsagePercent(),
				orchestrator.getRunningCount(), orchestrator.getTotalCount());
		
		systemStatsLabel.setText(stats);
		
	}
	
	/**
	 * Update instance table
	 */
	private void updateInstanceTable() {
		
		instanceTable.getTableModel().clear();
		
		for (ManagedInstance instance : orchestrator.getAllInstances()) {
			
			List<String> row = new ArrayList<>();
			row.add(instance.getConfig().getId());
			row.add(instance.getConfig().getName());
			row.add(getStatusString(instance));
			row.add(instance.getPid() > 0 ? String.valueOf(instance.getPid()) : "-");
			row.add(formatDuration(instance.getUptime()));
			
			ProcessMonitor.ProcessStats stats = processMonitor.getStats(instance.getConfig().getId());
			if (stats != null && instance.isAlive()) {
				row.add(String.format("%.1f", stats.getCpuPercent()));
				row.add(String.valueOf(stats.getMemoryMB()));
			} else {
				row.add("-");
				row.add("-");
			}
			
			row.add(String.valueOf(instance.getRestartCount()));
			
			instanceTable.getTableModel().addRow(row);
			
		}
		
	}
	
	/**
	 * Update status bar
	 */
	private void updateStatusBar() {
		statusLabel.setText("Use arrow keys to select instance, Enter to show menu, Q to quit");
	}
	
	/**
	 * Show instance action menu
	 */
	private void showInstanceMenu(String instanceId) {
		
		ManagedInstance instance = orchestrator.getInstance(instanceId);
		if (instance == null) return;
		
		List<String> actions = new ArrayList<>();
		
		if (instance.isPermanentlyStopped()) {
			actions.add("Resume");
		} else {
			if (instance.isAlive()) {
				actions.add("Stop");
				actions.add("Restart");
				actions.add("Kill");
			} else {
				actions.add("Start");
			}
			actions.add("Stop Permanently");
		}
		
		if (instance.getConfig().getBackup() != null &&
				instance.getConfig().getBackup().isEnabled()) {
			actions.add("Backup Now");
		}
		
		actions.add("View Console");
		actions.add("Cancel");
		
		ActionListDialogBuilder builder = new ActionListDialogBuilder()
				.setTitle("Instance: " + instance.getConfig().getName())
				.setDescription("Choose an action:");
		
		for (String action : actions) {
			builder.addAction(action, () -> handleInstanceAction(instanceId, action));
		}
		
		builder.build().showDialog(gui);
		
	}
	
	/**
	 * Handle instance action
	 */
	private void handleInstanceAction(String instanceId, String action) {
		
		ManagedInstance instance = orchestrator.getInstance(instanceId);
		
		switch (action) {
			case "Start":
				orchestrator.startInstance(instanceId);
				showMessage("Started instance: " + instance.getConfig().getName());
				break;
			case "Stop":
				orchestrator.stopInstance(instanceId);
				showMessage("Stopped instance: " + instance.getConfig().getName());
				break;
			case "Restart":
				orchestrator.restartInstance(instanceId);
				showMessage("Restarted instance: " + instance.getConfig().getName());
				break;
			case "Kill":
				orchestrator.killInstance(instanceId);
				showMessage("Killed instance: " + instance.getConfig().getName());
				break;
			case "Stop Permanently":
				orchestrator.stopInstancePermanently(instanceId);
				showMessage("Permanently stopped instance: " + instance.getConfig().getName());
				break;
			case "Resume":
				orchestrator.resumeInstance(instanceId);
				showMessage("Resumed instance: " + instance.getConfig().getName());
				break;
			case "Backup Now":
				performBackup(instance);
				break;
			case "View Console":
				showConsoleWindow(instance);
				break;
		}
		
	}
	
	/**
	 * Perform backup with progress dialog
	 */
	private void performBackup(ManagedInstance instance) {
		new Thread(() -> {
			
			gui.getGUIThread().invokeLater(() ->
					showMessage("Starting backup for " + instance.getConfig().getName() + "..."));
			
			boolean success = backupManager.performBackup(instance);
			
			gui.getGUIThread().invokeLater(() -> {
				if (success) {
					showMessage("Backup completed successfully!");
				} else {
					showMessage("Backup failed! Check logs for details.");
				}
			});
			
		}).start();
	}
	
	/**
	 * Show console output window
	 */
	private void showConsoleWindow(ManagedInstance instance) {
		
		BasicWindow consoleWindow = new BasicWindow("Console: " + instance.getConfig().getName());
		consoleWindow.setHints(List.of(Window.Hint.CENTERED));
		
		Panel consolePanel = new Panel(new BorderLayout());
		
		TextBox consoleText = new TextBox(new TerminalSize(100, 30));
		consoleText.setReadOnly(true);
		
		List<String> consoleOutput = instance.getConsoleOutput(500);
		consoleText.setText(String.join("\n", consoleOutput));
		
		consolePanel.addComponent(consoleText.withBorder(Borders.singleLine()),
				BorderLayout.Location.CENTER);
		
		Button closeButton = new Button("Close", consoleWindow::close);
		consolePanel.addComponent(closeButton, BorderLayout.Location.BOTTOM);
		
		consoleWindow.setComponent(consolePanel);
		gui.addWindow(consoleWindow);
		
	}
	
	/**
	 * Show message dialog
	 */
	private void showMessage(String message) {
		new MessageDialogBuilder()
				.setTitle("Info")
				.setText(message)
				.addButton(MessageDialogButton.OK)
				.build()
				.showDialog(gui);
	}
	
	/**
	 * Confirm exit
	 */
	private void confirmExit() {
		
		MessageDialogButton result = new MessageDialogBuilder()
				.setTitle("Confirm Exit")
				.setText("Are you sure you want to exit SJPO?")
				.addButton(MessageDialogButton.Yes)
				.addButton(MessageDialogButton.No)
				.build()
				.showDialog(gui);
		
		if (result == MessageDialogButton.Yes) {
			mainWindow.close();
		}
		
	}
	
	/**
	 * Get status string for instance
	 */
	private String getStatusString(ManagedInstance instance) {
		
		if (instance.isPermanentlyStopped()) {
			return "PERM_STOPPED";
		}
		
		return switch (instance.getState()) {
			case RUNNING -> "RUNNING";
			case STOPPED -> "STOPPED";
			case STARTING -> "STARTING";
			case STOPPING -> "STOPPING";
			case CRASHED -> "CRASHED";
			case RESTART_LIMIT_REACHED -> "LIMIT_REACHED";
		};
		
	}
	
	/**
	 * Format duration for display
	 */
	private String formatDuration(Duration duration) {
		
		if (duration.isZero()) return "-";
		
		long hours = duration.toHours();
		long minutes = duration.toMinutesPart();
		long seconds = duration.toSecondsPart();
		
		if (hours > 0) {
			return String.format("%dh %dm", hours, minutes);
		} else if (minutes > 0) {
			return String.format("%dm %ds", minutes, seconds);
		} else {
			return String.format("%ds", seconds);
		}
		
	}
	
}