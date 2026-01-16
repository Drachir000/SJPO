<img align="center" src="sjpo_banner.png" width="1000" alt="SJPO Banner">

![GitHub License](https://img.shields.io/github/license/Drachir000/SJPO)
![GitHub Release](https://img.shields.io/github/v/release/Drachir000/SJPO)
![GitHub Checks](https://img.shields.io/github/check-runs/Drachir000/SJPO/master)

A powerful, terminal-based orchestration tool for managing multiple Java processes with automatic restart, monitoring,
scheduled backups, and more.

## Features

- 🚀 **Process Management**: Start, stop, restart, and kill multiple Java instances
- 📊 **Real-time Monitoring**: CPU and RAM usage tracking for each instance
- 🔄 **Auto-restart**: Configurable automatic restart with crash detection
- ⏰ **Scheduling**: Schedule restarts, backups, and commands using cron expressions
- 💾 **Backups**: Automatic tar.gz backups with include/exclude patterns
- 📧 **Notifications**: SMTP and ntfy.sh backup notifications
- 🖥️ **Terminal UI**: Beautiful, keyboard-driven interface using Lanterna
- 🛡️ **Error-proof**: Restart limits to prevent infinite restart loops
- 🪟 **Cross-platform**: Works on Linux and Windows

## Requirements

- Java 25 or higher
- Maven 3.6+

## Quick Start

### 1. Build the Project (or download a release)

```bash
mvn clean package
```

This creates `target/SJPO.jar`

### 2. Run SJPO

Use this command or one of the launcher scripts:

```bash
java -jar SJPO.jar
```

On first run, SJPO creates an `instances/` directory with an example configuration file.

### 3. Configure Instances

Create JSON configuration files in the `instances/` directory. Each file represents one managed instance.

## Configuration

### Instance Configuration Example

```json
{
	"id": "my-app",
	"name": "My Java Application",
	"enabled": true,
	"javaPath": "java",
	"workingDirectory": "./apps/my-app",
	"jarFile": "application.jar",
	"vmArguments": [
		"-Xmx2G",
		"-Xms512M",
		"-XX:+UseG1GC"
	],
	"programArguments": [
		"--port=8080",
		"--config=config.yml"
	],
	"environmentVariables": {
		"ENV": "production"
	},
	"restart": {
		"autoRestart": true,
		"scheduledRestarts": [
			"0 0 3 * * ?"
		],
		"maxRestartsInWindow": 5,
		"restartWindowMinutes": 10,
		"cooldownSeconds": 5
	},
	"backup": {
		"enabled": true,
		"backupDirectory": "./backups/my-app",
		"includePaths": [
			"./apps/my-app/data",
			"./apps/my-app/config"
		],
		"excludePatterns": [
			"*.tmp",
			"*.log"
		],
		"schedules": [
			"0 0 2 * * ?"
		],
		"maxBackups": 7,
		"notification": {
			"smtp": {
				"enabled": true,
				"host": "smtp.example.com",
				"port": 587,
				"username": "user@example.com",
				"password": "password",
				"useTls": true,
				"from": "sjpo@example.com",
				"to": [
					"admin@example.com"
				],
				"subject": "SJPO Backup Notification"
			},
			"ntfy": {
				"enabled": true,
				"server": "https://ntfy.sh",
				"topic": "sjpo-backups",
				"token": "",
				"priority": "default"
			}
		}
	},
	"scheduledCommands": [
		{
			"name": "Daily Restart",
			"command": "restart",
			"schedule": "0 0 4 * * ?",
			"enabled": true
		}
	],
	"monitoring": {
		"enabled": true,
		"updateIntervalMs": 2000
	}
}
```

### Configuration Fields

#### Basic Settings

- `id`: Unique identifier (required)
- `name`: Display name
- `enabled`: Whether to auto-start on SJPO startup
- `javaPath`: Path to Java executable (default: "java")
- `workingDirectory`: Working directory for the process
- `jarFile`: JAR file to execute (use this OR mainClass)
- `mainClass`: Main class to execute (use this OR jarFile)
- `vmArguments`: JVM arguments (e.g., `-Xmx2G`)
- `programArguments`: Application arguments
- `environmentVariables`: Environment variables to set

#### Restart Configuration

- `autoRestart`: Enable automatic restart on crash
- `scheduledRestarts`: Cron expressions for scheduled restarts
- `maxRestartsInWindow`: Max restarts allowed in time window
- `restartWindowMinutes`: Time window for restart limiting
- `cooldownSeconds`: Delay before restart attempt

#### Backup Configuration

- `enabled`: Enable backup feature
- `backupDirectory`: Directory to store backups
- `includePaths`: Paths to include in backup (relative to workingDirectory)
- `excludePatterns`: Glob patterns to exclude (e.g., `*.log`, `*.tmp`)
- `schedules`: Cron expressions for scheduled backups
- `maxBackups`: Maximum number of backups to keep

#### Notification Configuration

SMTP and ntfy notifications for backup events.

#### Scheduled Commands

Execute commands at specific times:

- `restart`: Restart the instance
- `stop`: Stop the instance
- `start`: Start the instance

#### Monitoring

- `enabled`: Enable CPU/RAM monitoring
- `updateIntervalMs`: Monitoring update interval

### Cron Expression Examples

```
0 0 3 * * ?     - Every day at 3 AM
0 */30 * * * ?  - Every 30 minutes
0 0 */6 * * ?   - Every 6 hours
0 0 0 * * MON   - Every Monday at midnight
0 0 12 1 * ?    - First day of every month at noon
```

## Terminal UI Usage

### Navigation

- **Arrow Keys**: Navigate instance list
- **Enter**: Show instance action menu
- **Q**: Quit SJPO (with confirmation)

### Instance Actions

- **Start**: Start a stopped instance
- **Stop**: Gracefully stop a running instance
- **Restart**: Restart an instance
- **Kill**: Force kill an instance
- **Stop Permanently**: Stop and disable auto-restart
- **Resume**: Re-enable a permanently stopped instance
- **Backup Now**: Trigger immediate backup
- **View Console**: View recent console output

### Display Information

The UI shows:

- System CPU and memory usage
- Instance status (RUNNING, STOPPED, CRASHED, etc.)
- Process ID (PID)
- Uptime
- CPU and memory usage per instance
- Restart count

## Architecture

### Core Components

1. **ProcessOrchestrator**: Manages all instances
2. **ManagedInstance**: Represents a single Java process
3. **ConfigManager**: Loads and saves configurations
4. **ProcessMonitor**: Tracks CPU/RAM usage
5. **BackupManager**: Handles tar.gz backups
6. **SchedulerService**: Manages scheduled tasks (Quartz)
7. **NotificationService**: Sends SMTP/ntfy notifications
8. **MainWindow**: Terminal UI (Lanterna)

### State Management

Instances can be in these states:

- `STOPPED`: Not running
- `STARTING`: Currently starting
- `RUNNING`: Running normally
- `STOPPING`: Gracefully shutting down
- `CRASHED`: Exited with non-zero code
- `RESTART_LIMIT_REACHED`: Too many restarts, auto-restart disabled

### Restart Protection

SJPO prevents infinite restart loops by:

- Tracking restart attempts in a time window
- Disabling auto-restart after exceeding limit
- Configurable cooldown period between restarts

## Platform-Specific Notes

### Linux

- Uses `ps` command for process monitoring
- Supports all features

### Windows

- Uses `wmic` command for process monitoring
- CPU monitoring may be less accurate
- Supports all features

## Logging

Logs are written to `sjpo.log` in the current directory. Log level can be configured in
`src/main/resources/simplelogger.properties`.

## Examples

### Example 1: Minecraft Server

```json
{
	"id": "minecraft",
	"name": "Minecraft Server",
	"enabled": true,
	"javaPath": "java",
	"workingDirectory": "./servers/minecraft",
	"jarFile": "server.jar",
	"vmArguments": [
		"-Xmx4G",
		"-Xms4G",
		"-XX:+UseG1GC"
	],
	"programArguments": [
		"nogui"
	],
	"restart": {
		"autoRestart": true,
		"scheduledRestarts": [
			"0 0 4 * * ?"
		],
		"maxRestartsInWindow": 3,
		"restartWindowMinutes": 15,
		"cooldownSeconds": 10
	},
	"backup": {
		"enabled": true,
		"backupDirectory": "./backups/minecraft",
		"includePaths": [
			"world",
			"world_nether",
			"world_the_end"
		],
		"excludePatterns": [
			"*.tmp"
		],
		"schedules": [
			"0 0 2 * * ?"
		],
		"maxBackups": 14
	}
}
```

### Example 2: Spring Boot Application

```json
{
	"id": "spring-app",
	"name": "Spring Boot API",
	"enabled": true,
	"javaPath": "/usr/lib/jvm/java-21/bin/java",
	"workingDirectory": "./apps/api",
	"jarFile": "api-1.0.0.jar",
	"vmArguments": [
		"-Xmx1G",
		"-Dspring.profiles.active=prod"
	],
	"restart": {
		"autoRestart": true,
		"maxRestartsInWindow": 5,
		"restartWindowMinutes": 5
	},
	"monitoring": {
		"enabled": true,
		"updateIntervalMs": 1000
	}
}
```

## Troubleshooting

### Instance won't start

- Check `sjpo.log` for error messages
- Verify `javaPath` is correct
- Ensure `jarFile` or `mainClass` exists
- Check working directory permissions

### Monitoring shows 0% CPU or 0 MB RAM

- On Linux: Ensure `ps` command is available
- On Windows: Ensure `wmic` command is available
- Check process is actually running (PID shown)

### Backups failing

- Verify `includePaths` exist relative to `workingDirectory`
- Check disk space
- Ensure backup directory is writable
- Review `sjpo.log` for specific errors

### Notifications not working

- SMTP: Verify credentials and network access
- ntfy: Verify server URL and topic
- Check firewall settings

## Building from Source

```bash
# Clone the repository
git clone https://github.com/Drachir000/SJPO.git
cd SJPO

# Build
mvn clean package

# Run
java -jar target/SJPO.jar
```

## Development

### Project Structure

```
sjpo/
├── src/main/java/de/drachir000/sjpo/
│   ├── Main.java
│   ├── config/           # Configuration classes
│   ├── core/             # Process management
│   ├── monitor/          # CPU/RAM monitoring
│   ├── backup/           # Backup functionality
│   ├── scheduler/        # Scheduling with Quartz
│   ├── notification/     # SMTP and ntfy
│   └── ui/               # Terminal UI with Lanterna
├── instances/            # Instance configurations (created at runtime)
└── pom.xml
```

## License

This project is under the [MIT](LICENSE) License.

## Contributing

Contributions welcome! Please:

1. Test thoroughly on both Linux and Windows
2. Follow existing code style
3. Update documentation
4. Add logging for debugging

## Support

For issues or questions:

- Check `sjpo.log` for errors
- Review configuration syntax
- Verify Java version compatibility

---

**SJPO** - Making Java process management simple and reliable! 🚀