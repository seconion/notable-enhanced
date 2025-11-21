# Notable - Enhanced Fork

This is a vibe-coded fork of [Notable](https://github.com/Ethran/Notable), a handwriting note-taking app for Android e-ink devices.

## New Features

### 🤖 AI To-Do Generation
Transform your handwritten notes into digital tasks instantly.
- **Lasso & Convert**: Select any handwriting with the Lasso tool and tap the **Bell Icon**.
- **Gemini Powered**: Uses **Google Gemini 2.0 Flash** for high-accuracy recognition.
- **Integrated Workflow**: Tasks appear immediately in your daily To-Do list.

<p float="left">
  <img src="Screenshots/ai_loop.png" width="45%" />
  <img src="Screenshots/todomemo.png" width="45%" /> 
</p>

### 🏔️ Gamified Stats
Stay motivated with visual progress tracking.
- **Mountain Climb**: Every completed task moves your character up the mountain. Reach the summit every 100 tasks!
- **Monthly Insights**: Track your productivity with an E-Ink optimized bar chart.
- **Reset Journey**: Start fresh whenever you want.

<p align="center">
  <img src="Screenshots/stats.png" width="80%" />
</p>

### ☁️ WebDAV Auto-Sync
Seamlessly backup your notes.
- **Auto-Upload**: PDFs are uploaded automatically when you exit a notebook.
- **Background Sync**: No manual export required.

---

## Setup Guide

### 1. Configure AI Features
Go to **Settings > AI Features** and enter your Gemini API Key.

<p align="center">
  <img src="Screenshots/ai_key.png" width="80%" />
</p>

### 2. Configure WebDAV
Go to **Settings > WebDAV** to set up your cloud storage.

<p align="center">
  <img src="Screenshots/webdav.png" width="80%" />
</p>

## Building

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Credits

Based on [Notable by Ethran](https://github.com/Ethran/Notable)

Enhancements vibe-coded with Claude Code.