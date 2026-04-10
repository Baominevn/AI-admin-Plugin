# AIAdmin English Guide

## Part 1. Usage and config setup

### 1. Install the plugin
1. Put `AIAdmin.jar` into the server `plugins` folder.
2. Start the server once so AIAdmin can generate all config files.
3. Stop the server and review the generated files before enabling advanced features.

### 2. Language config folders
AIAdmin includes two config sets:
- `english/`
- `vietnamese/`

Each folder contains its own copies of:
- `config.yml`
- `aichat.yml`
- `option.yml`
- `rule.yml`
- `liteban.yml`
- `database.yml`
- `learning.yml`
- `setting_plugin.yml`
- `bot/`

Only one config set should be active at a time.
Use `use-config: true` in exactly one folder, and `false` in the other folder.

You can also switch the active config set in-game:
```text
/ai use config english
/ai use config vietnam
```

### 3. Personal language
Players can switch their own AI chat language without changing the whole server config:
```text
/ai lang english
/ai lang vietnam
```

### 4. OpenAI / Groq chat setup
Edit the active `config.yml` and review these keys:
- `openai.enabled`
- `openai.api_key`
- `openai.endpoint`
- `openai.model`
- `openai.max_output_tokens`
- `openai.max_reply_chars`
- `openai.system_prompt`

Example for Groq-compatible OpenAI format:
```yml
openai:
  enabled: true
  api_key: "YOUR_API_KEY"
  endpoint: "https://api.groq.com/openai/v1/responses"
  model: "llama-3.3-70b-versatile"
```

### 5. Plugin integrations
Use `setting_plugin.yml` to decide which integrations are active.
Examples:
- `liteban: true` to use LiteBans commands
- `placeholder: true` to register PlaceholderAPI placeholders when PlaceholderAPI is installed
- `tab: true` to allow TAB-related integration logic

If an integration is disabled, AIAdmin falls back to its internal behavior when possible.

### 6. Bot setup
The bot settings are stored in:
- `bot/bot.yml`
- `bot/bot_body.yml`
- `bot/bot_rule.yml`

Use these files to tune:
- follow / look / walk / jump behavior
- invulnerability
- AI observation behavior
- mannequin body settings
- trigger tier and observation timing

### 7. Recommended first test
After setup, test in this order:
1. `ai hello`
2. `/ai lang english`
3. `/ai scan`
4. `/ai check <player>`
5. `/ai observe <player> on`
6. `/ai bot help`

## Part 2. Commands and meaning

### Member commands

#### `ai <message>`
Send a direct message to AIAdmin. Only the player who called the AI receives the reply.

Example:
```text
ai what are the server rules
ai how do I play here
```

#### `/ai lang english`
Switch your personal AI replies to English.

#### `/ai lang vietnam`
Switch your personal AI replies to Vietnamese.

#### `/ai help`
Show the basic help section for regular players.

### Admin commands

#### `/ai scan`
Run an immediate player scan.

#### `/ai dashboard`
Open the suspicious-player dashboard GUI.

#### `/ai check <player> [gui|observe]`
Analyze a player in chat.
- `gui` opens the detailed GUI.
- `observe` immediately starts observation.

#### `/ai checkgui <player>`
Open the detailed check GUI directly.

#### `/ai suspicion <player>`
Show suspicion score, tier, alerts, and the last suspicious location.

#### `/ai addsus <player> <amount>`
Add suspicion points manually.

#### `/ai flag <player> <type> [points] [details]`
Register an alert manually and start observation.

Example:
```text
/ai flag Steve speed 8 strange movement
```

#### `/ai observe <player> <on/off>`
Start or stop AI observation for a target player.

Examples:
```text
/ai observe Steve on
/ai observe Steve off
```

#### `/ai kick <player> [reason]`
Kick an online player.

#### `/ai termban <player> <reason> <time>`
Apply a temp-ban.
Time examples: `30m`, `12h`, `1d`, `3d`, `7d`.

Examples:
```text
/ai termban Steve cheating 1d
/ai termban Steve 12h suspicious combat
```

#### `/ai ban <player> [reason]`
Ban a player through LiteBans if enabled, otherwise use the internal fallback.

#### `/ai thongbao <message>`
Ask the AI to rewrite and send a server-wide announcement.

Example:
```text
/ai thongbao server will restart in 5 minutes
```

#### `/ai admode <on|off|status>`
Toggle or inspect public admin relay mode.
When enabled, admin AI relay can talk publicly through Grox.

#### `/ai use config <english/vietnam>`
Switch the active global config set.

#### `/ai createbot <name>`
Create a bot body / mannequin.

#### `/ai choose bot <name>`
Select which bot you want to configure.

#### `/ai bot help`
Show all bot-related commands.

#### `/ai bot list`
List all current bots and their locations.

#### `/ai bot remove`
Remove the currently selected bot.

#### `/ai bot status`
Show the selected bot's current setup.

#### `/ai bot setup <key> <value>`
Change a bot setting in-game.

Examples:
```text
/ai bot setup follow true
/ai bot setup look true
/ai bot setup invulnerable true
```

#### `/ai bot action add move <x1> <y1> <z1> <x2> <y2> <z2>`
Add a movement action path to the selected bot.

### Notes
- `/ai` and `/aiadmin` can both be used as command roots if they are mapped that way on the server.
- Regular players cannot use admin moderation commands.
- Placeholder support is optional. If PlaceholderAPI is not installed, the plugin still works normally.
- LiteBans support is optional. If LiteBans is disabled or missing, AIAdmin uses its own fallback behavior where available.
