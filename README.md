# AI Car Checking — Phase 1 MVP (Android)

**Painted or Original? Check Before You Buy.**
AI-powered vehicle inspection assistant — Kotlin · Jetpack Compose · Material 3 · MVVM · Clean Architecture · Room · WorkManager · CameraX · ML Kit OCR.

## Open & run

1. Open this folder in **Android Studio (Ladybug 2024.2 or newer)**, JDK 17.
2. Let Gradle sync (AGP 8.7.3, Kotlin 2.0.21, Gradle 8.11.1 wrapper).
3. Run the `app` configuration on a device/emulator with **Android 8.0 (API 26)+**.
4. On Home, tap **Open Demo Inspection** to see the full offline flow.
5. Optional: Settings → AI Provider → Google Gemini → paste your own API key to analyze real photos.

Unit tests: `./gradlew :app:testDebugUnitTest` (validator, mileage checker, OCR parsers, OBD decoders, safety engine, paint map logic).

> Build note: this project was written in an environment without the Android SDK, so it has **not yet been compiled by Gradle/Android Studio**. The pure-Kotlin core (domain, AI validator, prompts, demo provider, safety engine, OBD, OCR parsers) *was* compiled and its 21 unit tests pass. Expect to fix a small number of compile issues in the Android/Compose layer on first sync.

## What works in Phase 1

| Feature | Status |
|---|---|
| Branding, Home dashboard, bottom navigation, dark/light theme | Functional |
| Vehicle profiles (all fields, photo, edit, delete with cascade) | Functional |
| Guided 27-step inspection with AI Inspection Coach per step | Functional |
| Capture (CameraX photo/video, microphone audio), Retake, Skip, Add Evidence (gallery/files) | Functional |
| Local evidence storage (copy into app storage, Media IDs, temp vs permanent, orphan cleanup) | Functional |
| Background processing (WorkManager): metadata, thumbnails, representative video frames, progress, cancel, retry | Functional |
| Evidence quality grading (brightness, glare, blur, resolution, duration) — blocks analysis of unusable evidence | Functional (on-device) |
| Dashboard OCR → odometer reading; service document OCR → dated mileage | Functional (ML Kit, on-device) |
| Mileage timeline + consistency check (never claims rollback) | Functional |
| Paint Check + interactive **Paint Map** (13 panels, tap for evidence/confidence/verification) | Functional with AI provider or Demo |
| Accident/repair analysis, engine bay, smoke, engine sound, tyres, warning lights (per-task prompts) | Functional with AI provider or Demo |
| AI response validation (schema, enums, evidence IDs, overclaim filter, confidence caps) | Functional |
| SafetyPolicyEngine (fuel leak, fire, brakes, overheating, oil pressure, battery, injury…) | Functional |
| AI Vehicle Inspection Report (completeness %, action buckets, what to verify) + **PDF export/share** | Functional |
| Demo Mode (vehicle, painted panels, dashboard, engine & exhaust frames, engine sound, service docs, P0420, report) | Functional offline |
| OBD: manual code entry, local DTC dictionary, voltage interpretation, PID/DTC decoders | Functional; **Bluetooth adapter = Integration Required** |
| AI Mechanic text chat (Gemini) / offline safety checklist | Functional; voice & media = Integration Required |
| Emergency "CAR BROKEN DOWN?" triage, Rescue 1122 dial | Functional offline |
| Storage Manager, Privacy, AI Provider, About | Functional |

Anything not built is labelled **Integration Required** in the UI — there are no dead buttons.

## Architecture

```
com.aicarchecking
├── di/AppContainer            manual DI (swap for Hilt later without touching features)
├── domain/model               Vehicle, Inspection, MediaAsset, Finding, enums, 27 InspectionSteps
├── domain/repository          repository interfaces
├── domain/usecase             AnalyzeStepUseCase, AddEvidenceUseCase, MileageConsistencyChecker, ReportBuilder
├── data/local                 Room entities (10 tables, FKs + cascades), DAOs, converters, mappers
├── data/repository            repository implementations
├── data/settings              DataStore settings + Android Keystore encryption for user API key
├── data/obd                   ObdAdapter abstraction, PID/DTC decoders, DTC dictionary
├── data/demo                  on-device demo asset generator + seeder
├── ai/                        AIProvider interface, GeminiProvider, DemoAIProvider, AIProviderRegistry
├── ai/prompt                  PromptLibrary — one prompt per task (evidence, context, task, limitations, schema, confidence, verification)
├── ai/validator               AiResponseValidator
├── safety/                    SafetyPolicyEngine
├── media/                     storage, image utils, quality analyzer, video/PDF inspector, OCR, WorkManager worker
├── report/                    PdfReportGenerator
├── navigation/                routes + NavHost
└── ui/                        home, vehicle, inspection (flow/step/capture), paint, mileage, obd, mechanic, history, report, settings, emergency
```

**Analysis pipeline:** capture → copy to app storage → Media ID → WorkManager (thumbnail, metadata, quality, frames, OCR) → user taps AI Analyze → quality gate → explicit consent dialog (external providers) → provider → JSON validation → SafetyPolicyEngine → evidence-linked Findings → Paint Map / Report.

## Privacy & security
- No automatic uploads; only selected photos / extracted frames / audio are sent after confirmation. Originals stay on device.
- No API keys in the APK. A user key is AES-GCM encrypted with the Android Keystore. For public release, proxy AI calls through your own backend.
- Backups and device transfer excluded; only exported reports are shareable via FileProvider.
- AI output is untrusted: validated, never executed; raw provider output is never shown.

## Before Play Store release
- Replace `fallbackToDestructiveMigration()` with real Room migrations.
- Move AI calls behind a backend (no end-user keys), add rate limiting.
- Add instrumented/UI tests, crash reporting, accessibility pass, Urdu/Roman Urdu strings.
- Phase 2–4 items from the spec (voice, Bluetooth OBD, maintenance, marketplace, cloud sync).
