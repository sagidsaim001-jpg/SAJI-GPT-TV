# SAJI GPT TV

A lightweight Android TV wrapper that opens ChatGPT directly.

## Target
- Android TV / Android TV Box
- Android 13
- ARM32-compatible Android bytecode
- Designed for low-memory devices

## Features
- Opens ChatGPT directly at https://chatgpt.com/
- WebView with JavaScript, DOM storage and cookies
- Android TV remote input through the WebView
- Page Up / Page Down keys scroll the conversation
- Microphone permission bridge for ChatGPT voice where supported by the installed Android System WebView
- No Gemini or WhatsApp modules

## Build
GitHub Actions builds a debug APK on every push to `main` and on manual workflow dispatch. Download it from the workflow run's Artifacts section.

## Notes
ChatGPT sign-in and voice availability depend on OpenAI's website, account access, network connectivity and the device's Android System WebView version. The app does not bypass account requirements.
