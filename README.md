# BusyBee - Universal Calendar Sync Engine

> **⚠️ Experimental** - This project is under active development. Use with care in production.

Automated calendar synchronization that mirrors "busy" time slots across multiple calendars to prevent double-booking.

## Features

- Full-mesh synchronization across Google Calendars and CalDAV
- Privacy-focused: synced events show only "Busy" status
- Automatic token refresh for long-running background operation
- Interactive configuration wizard
- Event lifecycle management (create, update, delete)
- Infinite loop prevention with sync markers
- Configurable sync interval

## Limitations

- **Timed events only**: Only events with specific start/end times are synced. All-day and multi-day events are not currently supported.

## Unfinished / Experimental Features

- **Daemon mode** (`java -jar busybee.jar run`) - Has not been tested
- **Email alerts** - Experimental, may be removed in a future release

## Requirements

- JVM 21+

## Quick Start

### 1. Build

```bash
./gradlew fatJar
```

### 2. Configure

```bash
java -jar busybee.jar configure
```

The wizard will guide you through:
1. Select "Google Calendar (OAuth)"
2. Enter OAuth Client ID/Secret (see below)
3. Browser opens for authorization
4. Copy authorization code
5. Done! Tokens saved automatically

### 3. Run

```bash
java -jar busybee.jar sync   # Run once
java -jar busybee.jar run    # Run as daemon (untested)
```

## Getting OAuth Credentials (One-time)

1. Go to [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
2. Create OAuth 2.0 Client ID (Desktop app)
3. Note your **Client ID** and **Client Secret**
4. In OAuth consent screen → Test users → Add your email

That's it! The configure wizard handles the rest automatically.

## Generic CalDAV

For Infomaniak, IceWarp, or other CalDAV providers:

```bash
java -jar busybee.jar configure
```

Select "Generic CalDAV" and provide URL, username, password.

## Systemd Setup (Linux) - Untested

```bash
sudo cp src/scripts/busybee.service /etc/systemd/system/
sudo cp src/scripts/busybee.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now busybee.timer
```

## Configuration Options

| Option | Description | Default |
|--------|-------------|---------|
| `intervalMinutes` | Sync interval | 15 |
| `prefix` | Sync event prefix | "BB" |

## How It Works

1. Fetches events from all configured calendars
2. Identifies "original" events (not synced)
3. Creates "[BB-<shortcut>] <summary>" placeholder events in all other calendars
4. On original event update: updates all synced blocks
5. On original event delete: removes all synced blocks

## Security

- Tokens stored in local `tokens/` directory (add to `.gitignore`)
- Synced events contain no sensitive details
- Run behind firewall/VPN for production use
