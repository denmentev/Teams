# Teams Plugin

A Minecraft team management plugin for Paper/Spigot servers (API 1.21), built by **Denis496**.

## Overview

Teams allows players to form persistent groups with a role-based hierarchy (Owner, Manager, Member). Teams are stored in a MySQL (or SQLite) database and integrate with PlaceholderAPI and a custom ChatPlugin for rich team chat.

---

## Features

- **Team creation** with name validation (3–16 chars, alphanumeric/underscore/hyphen, no banned words)
- **Playtime requirement** — players must have a minimum playtime before creating a team (default: 5 hours, requires PlayerStats plugin)
- **Role hierarchy** — Owner > Manager > Member, each with specific permissions
- **Invite system** — invite online players with a 20-second expiry and cooldown between invites
- **Team chat** — prefix messages with `#` to send to team-only chat
- **Team chat placeholders** (via ChatPlugin integration):
  - `:team:` — your team name
  - `:item:` — item currently held
  - `:loc:` — your current coordinates
  - `:x1234:` — map marks (requires Marks plugin)
  - `@player` — mention a team member
- **Confirmation system** — destructive actions (kick, delete, promote to Owner) require click-to-confirm prompts
- **PlaceholderAPI support** — expose team data to other plugins
- **Admin commands** — reload config, delete any team

---

## Requirements

| Dependency | Type | Purpose |
|---|---|---|
| Paper/Spigot 1.21 | Required | Server platform |
| MySQL | Required (or SQLite) | Data persistence |
| PlayerStats | Soft | Playtime enforcement |
| PlaceholderAPI | Soft | Placeholder expansion |
| ChatPlugin (Denis496) | Soft | Team chat placeholders & mentions |

---

## Commands

| Command | Description | Permission |
|---|---|---|
| `/team create <name>` | Create a new team | `teams.use` |
| `/team invite <player>` | Invite a player to your team | `teams.use` (Manager+) |
| `/team accept` | Accept a pending invite | `teams.use` |
| `/team deny` | Decline a pending invite | `teams.use` |
| `/team kick <player>` | Kick a member from the team | `teams.use` (Manager+) |
| `/team promote <player>` | Promote Member → Manager or Manager → Owner | `teams.use` (Owner only) |
| `/team demote <player>` | Demote Manager → Member | `teams.use` (Owner only) |
| `/team leave` | Leave your team | `teams.use` |
| `/team delete [name]` | Delete your team (admin can specify any team) | `teams.use` / `teams.admin` |
| `/team list` | List all teams | `teams.use` |
| `/team info [name]` | Show team details and roster | `teams.use` |
| `/team help` | Show command help | `teams.use` |
| `/team chathelp` | Show team chat placeholder help | `teams.use` |
| `/team reload` | Reload plugin configuration | `teams.admin` |
| `#<message>` | Send a message to your team chat | `teams.use` |

Aliases: `/t`, `/teams`

---

## Permissions

| Permission | Default | Description |
|---|---|---|
| `teams.use` | Everyone | Access to all basic team commands |
| `teams.admin` | OP | Reload config, delete any team, bypass role restrictions |
| `teams.bypass.playtime` | OP | Skip playtime requirement for team creation |

---

## PlaceholderAPI Placeholders

Use these with any plugin that supports PlaceholderAPI (prefix: `%teams_...%`):

| Placeholder | Returns |
|---|---|
| `%teams_team_name%` | The player's team name (empty if none) |
| `%teams_role%` | Role display name (Owner / Manager / Member) |
| `%teams_role_formatted%` | Role with color and prefix formatting |
| `%teams_role_prefix%` | Role prefix symbol only |
| `%teams_members_count%` | Number of members in the team |
| `%teams_has_team%` | `true` / `false` |
| `%teams_team_description%` | Team description |
| `%teams_is_owner%` | `true` / `false` |
| `%teams_is_manager%` | `true` / `false` |
| `%teams_is_member%` | `true` / `false` |
| `%teams_team_created%` | Team creation date |

---

## Configuration

The main config file is `config.yml`. Key settings:

```yaml
database:
  type: "MySQL"   # MySQL or SQLite
  mysql:
    host: "..."
    port: 3306
    database: "..."
    username: "..."
    password: "..."

minimum-playtime-hours: 5   # Hours required to create a team
max-managers: 1             # Max managers per team
date-format: "dd MMM yyyy"
```

All messages, role colors/prefixes, and confirmation prompts are fully customizable in `config.yml`.

---

## Role Permissions

| Action | Member | Manager | Owner |
|---|---|---|---|
| Send team chat | Yes | Yes | Yes |
| Invite players | No | Yes | Yes |
| Kick members | No | Yes | Yes |
| Kick managers | No | No | Yes |
| Promote / Demote | No | No | Yes |
| Delete team | No | No | Yes |
| Transfer ownership | No | No | Yes |

---

## Installation

1. Place the `Teams.jar` in your server's `plugins/` folder.
2. Start the server once to generate `config.yml`.
3. Configure your database credentials in `plugins/Teams/config.yml`.
4. Restart the server.

---

## Building from Source

```bash
mvn clean package
```

The compiled jar will be in `target/`.
