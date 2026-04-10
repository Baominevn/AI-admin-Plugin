## DESCRIPTION:
AIAdmin is a server management plugin for Minecraft 1.21.x, focusing on three main goals:
- Helping staff manage servers faster with AI assistants.
- Monitoring and analyzing players exhibiting unusual behavior.
- Consolidating multiple tools for checking, alerting, and handling issues into one easy-to-configure system.

## HOW IT WORKS?
- Admins can enable `adminmode` by command `/ai adminmode on` to allow Grox to communicate across the entire server on behalf of staff.
- A suspicion score system accumulates suspicion levels from multiple sources.
- A dashboard/GUI displays a list of suspicious players categorized by `Low / Medium / High`.
- A bot mannequin acts as a surveillance camera, capable of follow, look, walk, jump, hit, and providing insights to staff.
- Automatic alerts, kicks, or tempbans based on configurable thresholds.
- Integrated OpenAI-compatible API, usable with Groq or similar endpoints.
- Supports logging into MySQL and plugin hooks such as LiteBans, TAB, and PlaceholderAPI.

AIAdmin does not completely replace anti-cheat. This plugin retrieves data from:
- Anti-cheat alerts via console.
- Reports from players or staff.
- Live observation using a bot camera mannequin.
=> The plugin then compiles these signals into points of suspicion to make it easier for staff to make decisions.

## COMMANDS:
**Member Command**
- Chat with AI: `ai <content>`.
- `/ai lang english|vietnam`

**Admin Command**
- `/ai help`
- `/ai reload`
- `/ai scan`
- `/ai adminmode on|off|status`
- `/ai addsus <player> <amount>`
- `/ai thongbao <chat>` or `/ai announce <chat>`
- `/ai check <player>`
- `/ai checkgui <player>`
- `/ai observes <player>` or `/ai watch <player>`
- `/ai flag <player> <action> <amount>`
- `/ai dashboard`
- `/ai use config english|vietnam`
- `/ai kick <player> <reason>`
- `/ai termban <player> <reason> <time>`
- `/ai ban <player> <reason>`
- `/ai bot help`
- `/ai choose bot <name>`
- `/ai createbot <name>`
- `/ai bot list`
- `/ai bot remove`
- `/ai bot setup <option> <on|off|value>`
- `/ai bot status`
- `/ai bot action add <action>`

## PERMISSION:
- `aiadmin.admin`

## PLACEHOLDER:
- `%aiadmin_sustime%`
- `%aiadmin_report%`
- `%aiadmin_kick%`
- `%aiadmin_termban%`
- `%aiadmin_bots%`
- `%aiadmin_check%`
- `%aiadmin_check_last24hour%`

## IMPORTANT NOTE:
- The AI ​​chat feature only works when you configure a valid API key yourself.
- If you enable a third-party endpoint like Groq, the prompt will be sent to that service.
- Automated actions such as kick or tempban should be thoroughly tested and fine-tuned in the configuration by staff before use on a real server.
- The plugin prioritizes staff support in reducing false positives; it does not guarantee 100% accurate hack detection.
