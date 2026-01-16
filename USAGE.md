# SJPO Usage Guide

## Getting Started

### Installation

1. **Build the project:**
   ```bash
   mvn clean package
   ```

2. **Run SJPO:**
   ```bash
   # Linux/Mac
   ./start.sh
   
   # Windows
   start.bat
   ```

### First Time Setup

When you first run SJPO, it will:

1. Create an `instances/` directory
2. Generate an `example.json` configuration file
3. Display an empty instance list in the UI

## Creating Your First Instance

### Step 1: Create Configuration File

Create a new file in the `instances/` directory, e.g., `instances/my-app.json`:

```json
{
	"id": "my-app",
	"name": "My Application",
	"enabled": true,
	"javaPath": "java",
	"workingDirectory": "./apps/my-app",
	"jarFile": "app.jar",
	"vmArguments": [
		"-Xmx1G"
	],
	"programArguments": [],
	"restart": {
		"autoRestart": true,
		"maxRestartsInWindow": 5,
		"restartWindowMinutes": 10,
		"cooldownSeconds": 5
	}
}
```

### Step 2: Prepare Application

Ensure your application files are in place:

```bash
mkdir -p apps/my-app
cp /path/to/your/app.jar apps/my-app/
```

### Step 3: Restart SJPO

Stop SJPO (press Q in the UI) and start it again. Your new instance will appear and start automatically if
`enabled: true`.

## UI Navigation Guide

### Main Screen

```
═══ SJPO - Java Process Orchestrator ═══                Press Q to quit
System: CPU: 15.2% | Memory: 4096/16384 MB (25.0%) | Instances: 2 running / 3 total
═══ Instances ═══
┌────────────┬────────────────┬─────────┬─────┬────────┬─────┬────────┬──────────┐
│ ID         │ Name           │ Status  │ PID │ Uptime │ CPU%│ RAM(MB)│ Restarts │
├────────────┼────────────────┼─────────┼─────┼────────┼─────┼────────┼──────────┤
│ my-app     │ My Application │ RUNNING │ 1234│ 2h 15m │ 5.2 │ 512    │ 0        │
│ api-server │ API Server     │ RUNNING │ 5678│ 45m 3s │ 12.1│ 1024   │ 1        │
│ worker     │ Worker Service │ STOPPED │ -   │ -      │ -   │ -      │ 3        │
└────────────┴────────────────┴─────────┴─────┴────────┴─────┴────────┴──────────┘

Use arrow keys to select instance, Enter to show menu, Q to quit
```

### Keyboard Controls

| Key   | Action                        |
|-------|-------------------------------|
| ↑↓    | Navigate instance list        |
| Enter | Show instance menu            |
| Q     | Quit SJPO (with confirmation) |

### Instance Status Colors

- **RUNNING**: Instance is running normally
- **STOPPED**: Instance is not running
- **STARTING**: Instance is in the process of starting
- **STOPPING**: Instance is shutting down
- **CRASHED**: Instance terminated with error code
- **LIMIT_REACHED**: Too many restarts, auto-restart disabled
- **PERM_STOPPED**: Instance stopped permanently (auto-restart disabled)

## Common Tasks

### Starting an Instance

1. Navigate to the stopped instance using arrow keys
2. Press Enter
3. Select "Start"

### Restarting an Instance

1. Navigate to the running instance
2. Press Enter
3. Select "Restart"

The instance will shut down gracefully and restart after the cooldown period.

### Stopping an Instance Temporarily

1. Navigate to the running instance
2. Press Enter
3. Select "Stop"

Auto-restart will still trigger if the instance crashes later.

### Stopping an Instance Permanently

1. Navigate to the instance
2. Press Enter
3. Select "Stop Permanently"

This disables auto-restart. The instance will not restart even if it crashes.

### Resuming a Permanently Stopped Instance

1. Navigate to the permanently stopped instance (status: PERM_STOPPED)
2. Press Enter
3. Select "Resume"

This re-enables the instance but doesn't start it automatically.

### Force Killing an Instance

If an instance won't stop gracefully:

1. Navigate to the instance
2. Press Enter
3. Select "Kill"

This forcefully terminates the process.

### Viewing Console Output

1. Navigate to the instance
2. Press Enter
3. Select "View Console"

This shows the last 500 lines of console output. Press Close to return.

### Creating a Manual Backup

If backups are configured:

1. Navigate to the instance
2. Press Enter
3. Select "Backup Now"

The backup runs in the background, and you'll see a notification when complete.

## Configuration Examples

### Basic Web Server

```json
{
	"id": "web-server",
	"name": "Web Server",
	"enabled": true,
	"javaPath": "java",
	"workingDirectory": "./apps/web",
	"jarFile": "server.jar",
	"vmArguments": [
		"-Xmx512M",
		"-Xms512M"
	],
	"programArguments": [
		"--port=8080"
	],
	"restart": {
		"autoRestart": true,
		"maxRestartsInWindow": 3,
		"restartWindowMinutes": 5,
		"cooldownSeconds": 3
	}
}
```

### Database with Backups

```json
{
	"id": "database",
	"name": "Database Server",
	"enabled": true,
	"javaPath": "/usr/lib/jvm/java-21/bin/java",
	"workingDirectory": "./apps/db",
	"jarFile": "database.jar",
	"vmArguments": [
		"-Xmx4G",
		"-Xms4G",
		"-XX:+UseG1GC"
	],
	"restart": {
		"autoRestart": true,
		"scheduledRestarts": [
			"0 0 4 * * SUN"
		],
		"maxRestartsInWindow": 2,
		"restartWindowMinutes": 15
	},
	"backup": {
		"enabled": true,
		"backupDirectory": "./backups/database",
		"includePaths": [
			"data",
			"config"
		],
		"excludePatterns": [
			"*.tmp",
			"*.lock"
		],
		"schedules": [
			"0 0 2 * * ?"
		],
		"maxBackups": 30,
		"notification": {
			"ntfy": {
				"enabled": true,
				"server": "https://ntfy.sh",
				"topic": "my-database-backups",
				"priority": "high"
			}
		}
	}
}
```

### Microservice with Scheduled Tasks

```json
{
	"id": "worker",
	"name": "Worker Service",
	"enabled": true,
	"javaPath": "java",
	"workingDirectory": "./apps/worker",
	"jarFile": "worker.jar",
	"vmArguments": [
		"-Xmx1G"
	],
	"environmentVariables": {
		"ENV": "production",
		"LOG_LEVEL": "INFO"
	},
	"restart": {
		"autoRestart": true,
		"maxRestartsInWindow": 5,
		"restartWindowMinutes": 10
	},
	"scheduledCommands": [
		{
			"name": "Nightly Restart",
			"command": "restart",
			"schedule": "0 0 3 * * ?",
			"enabled": true
		}
	]
}
```

### Game Server with Email Notifications

```json
{
	"id": "game-server",
	"name": "Game Server",
	"enabled": true,
	"javaPath": "java",
	"workingDirectory": "./servers/game",
	"jarFile": "gameserver.jar",
	"vmArguments": [
		"-Xmx6G",
		"-Xms6G",
		"-XX:+UseG1GC",
		"-XX:MaxGCPauseMillis=200"
	],
	"programArguments": [
		"--online-mode=true"
	],
	"restart": {
		"autoRestart": true,
		"scheduledRestarts": [
			"0 0 5 * * ?"
		],
		"maxRestartsInWindow": 3,
		"restartWindowMinutes": 20,
		"cooldownSeconds": 30
	},
	"backup": {
		"enabled": true,
		"backupDirectory": "./backups/game",
		"includePaths": [
			"world",
			"plugins",
			"config.yml"
		],
		"excludePatterns": [
			"*.log",
			"cache/*"
		],
		"schedules": [
			"0 0 2 * * ?",
			"0 0 14 * * ?"
		],
		"maxBackups": 14,
		"notification": {
			"smtp": {
				"enabled": true,
				"host": "smtp.gmail.com",
				"port": 587,
				"username": "your-email@gmail.com",
				"password": "your-app-password",
				"useTls": true,
				"from": "game-server@example.com",
				"to": [
					"admin@example.com"
				],
				"subject": "Game Server Backup"
			}
		}
	}
}
```

## Advanced Features

### Using Main Class Instead of JAR

```json
{
	"id": "my-app",
	"mainClass": "com.example.Main",
	"vmArguments": [
		"-cp",
		"lib/*:app.jar"
	]
}
```

### Custom Java Path

```json
{
	"id": "my-app",
	"javaPath": "/opt/jdk-21/bin/java"
}
```

### Environment Variables

```json
{
	"id": "my-app",
	"environmentVariables": {
		"DATABASE_URL": "jdbc:postgresql://localhost:5432/mydb",
		"API_KEY": "secret-key-123",
		"LOG_LEVEL": "DEBUG"
	}
}
```

### Multiple Scheduled Backups

```json
{
	"backup": {
		"schedules": [
			"0 0 2 * * ?",
			// 2 AM daily
			"0 0 14 * * ?",
			// 2 PM daily
			"0 0 20 * * ?"
			// 8 PM daily
		]
	}
}
```

## Monitoring

### Understanding Metrics

- **CPU%**: Percentage of CPU used by the process
    - On Linux: Actual CPU usage from `ps`
    - On Windows: May show 0.0 (limitation of `wmic`)

- **RAM (MB)**: Memory used by the process in megabytes
    - Shows resident memory (actual RAM used)
    - Does not include swapped memory

- **Uptime**: How long the instance has been running
    - Format: `Xh Ym` (hours and minutes) or `Ym Zs` (minutes and seconds)

- **Restarts**: Number of restarts in the current time window

### System Statistics

The top bar shows:

- **System CPU**: Overall CPU usage across all cores
- **System Memory**: Total system memory usage
- **Instance counts**: Running vs. total instances

## Backup Management

### Backup File Naming

Backups are named: `{instance-id}-{timestamp}.tar.gz`

Example: `my-app-2025.01.16-12:37:22.tar.gz`

### Backup Rotation

SJPO automatically deletes old backups based on `maxBackups`:

- Keeps the N most recent backups
- Deletes older backups automatically
- Based on file modification time

### Include/Exclude Patterns

Include paths are relative to `workingDirectory`:

```json
{
	"includePaths": [
		"data",
		// Include entire data directory
		"config/app.yml"
		// Include specific file
	]
}
```

Exclude patterns use glob syntax:

```json
{
	"excludePatterns": [
		"*.log",
		// Exclude all .log files
		"*.tmp",
		// Exclude all .tmp files
		"cache/*",
		// Exclude everything in cache directory
		"temp*"
		// Exclude files starting with "temp"
	]
}
```

### Backup Notifications

#### SMTP (Email)

Requires valid SMTP credentials:

```json
{
	"smtp": {
		"enabled": true,
		"host": "smtp.gmail.com",
		"port": 587,
		"username": "your-email@gmail.com",
		"password": "your-app-password",
		"useTls": true,
		"from": "sjpo@example.com",
		"to": [
			"admin@example.com",
			"backup-notifications@example.com"
		]
	}
}
```

For Gmail, use an [App Password](https://support.google.com/accounts/answer/185833).

#### Ntfy (Push Notifications)

Free, easy push notifications:

```json
{
	"ntfy": {
		"enabled": true,
		"server": "https://ntfy.sh",
		"topic": "my-unique-topic-name",
		"priority": "high"
	}
}
```

1. Choose a unique topic name
2. Subscribe on your phone using the ntfy app
3. Receive push notifications for backups

## Troubleshooting

### Instance shows CRASHED repeatedly

**Cause**: Application is crashing immediately after start

**Solution**:

1. Check console output (View Console)
2. Review application logs
3. Verify configuration (paths, arguments)
4. Check if ports are already in use

### Restart limit reached

**Cause**: Too many crashes in the time window

**Solution**:

1. Fix the underlying issue causing crashes
2. Manually start the instance (resets restart count)
3. Adjust `maxRestartsInWindow` or `restartWindowMinutes` if needed

### Backup fails

**Cause**: Missing paths, permissions, or disk space

**Solution**:

1. Check `sjpo.log` for error details
2. Verify `includePaths` exist
3. Check disk space
4. Ensure backup directory is writable

### No CPU/RAM stats shown

**Linux**: Ensure `ps` command is available
**Windows**: Ensure `wmic` is available (comes with Windows)

### Instance won't stop

**Solution**:

1. Wait up to 60 seconds for graceful shutdown
2. Use "Kill" option if needed
3. Check if application is hung (use external tools)

## Performance Tips

1. **Set appropriate heap sizes**: Use `-Xmx` and `-Xms` based on application needs
2. **Use G1GC for large heaps**: `-XX:+UseG1GC` for heaps > 4GB
3. **(TODO) Adjust monitoring interval**: Lower `updateIntervalMs` for more frequent updates (uses more CPU)
4. **Limit backup frequency**: Don't schedule backups too frequently
5. **Use exclude patterns**: Exclude unnecessary files from backups

## Cron Expression Reference

```
┌───────────── second (0-59)
│ ┌───────────── minute (0-59)
│ │ ┌───────────── hour (0-23)
│ │ │ ┌───────────── day of month (1-31)
│ │ │ │ ┌───────────── month (1-12 or JAN-DEC)
│ │ │ │ │ ┌───────────── day of week (0-6 or SUN-SAT)
│ │ │ │ │ │
* * * * * *
```

**Examples**:

- `0 0 3 * * ?` - 3 AM daily
- `0 */30 * * * ?` - Every 30 minutes
- `0 0 */4 * * ?` - Every 4 hours
- `0 0 9-17 * * MON-FRI` - Every hour from 9 AM to 5 PM, Monday to Friday
- `0 0 0 1 * ?` - First day of each month at midnight

## Best Practices

1. **Start with auto-restart enabled**: Ensures high availability
2. **Set reasonable restart limits**: Prevents infinite restart loops
3. **Use scheduled restarts for long-running services**: Prevents memory leaks
4. **Configure backups for important data**: Automate data protection
5. **Test configurations**: Start with `enabled: false` and test manually
6. **Monitor logs**: Check `sjpo.log` regularly
7. **Use environment-specific configs**: Different settings for dev/prod
8. **Document your instances**: Use descriptive names and IDs

## Getting Help

1. Check `sjpo.log` for detailed error messages
2. Review this usage guide and README.md
3. Verify Java version (25+ recommended)
4. Test configuration with minimal settings first
5. Check file permissions and paths
