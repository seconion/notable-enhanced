# Notable Enhanced

Notable Enhanced is an e-ink focused fork of [Notable](https://github.com/Ethran/Notable), tuned for handwritten notes, planning, and low-friction daily use on Android e-paper devices.

It keeps the original notebook editor, then adds practical extensions around self-hosted AI, calendar-driven notes, WebDAV sync, reminders, and performance work for slower monochrome hardware.

## Download

- Latest release: [v0.4.3 - E-Ink Optimization and Markdown Export](https://github.com/seconion/notable-enhanced/releases/tag/v0.4.3)
- Installable APK: [notable-enhanced-v0.4.3-debug.apk](https://github.com/seconion/notable-enhanced/releases/download/v0.4.3/app-debug.apk)

### Install

1. Download the APK to your device.
2. Open it and allow installation from unknown sources if Android asks.
3. Launch the app and grant the storage permissions it needs for notebooks, imports, and exports.

## What This Fork Adds

### E-Ink Focused Optimization

- Reduced duplicate editor observers and repeated background work across note sessions.
- Moved heavy thumbnail decoding off the UI thread for smoother library browsing.
- Debounced editor exit exports so leaving a note does not stack unnecessary work.
- Improved behavior for repeated open/close cycles on slower e-paper devices.

### Calendar and Daily Memo Workflow

- Open notes by date instead of hunting through folders.
- Keep a dedicated daily memo for each day.
- See notebook activity directly from the calendar view.
- Jump from a date into related notes without breaking the notebook flow.

### AI To-Do Capture

- Select handwriting with the lasso tool and convert it into reminders.
- Choose between **Gemini** for a hosted setup or **Ollama** for a self-hosted workflow.
- Push extracted tasks directly into the in-app reminder flow.

### AI Markdown Export

- Convert handwritten pages into Markdown with a local Ollama vision model.
- Trigger Markdown export manually from the editor menu.
- Auto-export Markdown on exit through WebDAV when enabled.
- Multi-page notebooks export one Markdown file per page instead of overwriting earlier pages.

### E-Ink Friendly Stats

- Track completed reminders over time.
- Use the mountain-climb progress view as a lightweight motivation loop.
- Review activity in layouts that are readable on monochrome devices.

### WebDAV Backup

- Export notebooks to PDF on exit and upload them automatically.
- Upload AI-generated Markdown to `Notable/Markdown/` on your WebDAV server.
- Keep remote copies aligned with the notebook workflow instead of running manual exports.

## Screenshots

<p float="left">
  <img src="Screenshots/ai_loop.png" width="45%" />
  <img src="Screenshots/todomemo.png" width="45%" />
</p>

<p float="left">
  <img src="Screenshots/stats.png" width="45%" />
  <img src="Screenshots/webdav.png" width="45%" />
</p>

## AI Setup

Go to **Settings** and choose the backend you want to use.

### Gemini

- Enter a Gemini API key.
- Best when you want the fastest setup and do not want to run local infrastructure.

### Ollama

- Enter your Ollama server URL and model name.
- Best when you want a self-hosted path with more control over privacy and cost.
- Required for AI Markdown export.

Example server setup:

```bash
curl -fsSL https://ollama.com/install.sh | sh
ollama pull minicpm-v
OLLAMA_HOST=0.0.0.0:11434 ollama serve
```

Recommended models:

- `minicpm-v`
- `llama3.2-vision`
- `llava`

## Markdown Export

With Ollama and WebDAV configured, you can:

- open a page and use `Export page to Markdown (AI)` from the editor menu
- enable `Auto-export on exit` in WebDAV settings
- choose `Markdown (AI) only` or `Both PDF and Markdown`

Markdown files are uploaded to:

```text
Notable/Markdown/
```

Notebook pages are exported as separate files when needed, for example:

```text
Project_Notes_p1.md
Project_Notes_p2.md
```

## Building

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

Outputs:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`

## Credits

- Original project: [Ethran/Notable](https://github.com/Ethran/Notable)
- Enhanced fork and feature work: [seconion/notable-enhanced](https://github.com/seconion/notable-enhanced)
