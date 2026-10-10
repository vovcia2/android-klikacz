# ClickLog

Aplikacja na Androida (CAT S75, Android 12+) rejestrująca naciśnięcia sprzętowego
przycisku programowalnego (SOS) z dokładnym znacznikiem czasu. Specyfikacja: [SPEC.md](SPEC.md).

## Budowanie

Wymagany JDK 17+ i Android SDK (platforma 35). W Android Studio: *Open* → katalog repo.
Z linii poleceń:

```
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # testy jednostkowe (ClickProcessor, PathSelector, CSV)
```

## Wersjonowanie i CI

Wersja jest wyliczana automatycznie w `app/build.gradle.kts`:

- `versionCode` = liczba commitów osiągalnych z `HEAD` (`git rev-list --count HEAD`),
- `versionName` = `<app.versionBase>.<versionCode>` (np. `0.1.42`; `app.versionBase` w
  `gradle.properties` – podbij ręcznie przy nowej linii wydań), albo `X.Y.Z` gdy `HEAD`
  ma tag `vX.Y.Z`,
- plik APK nazywa się `ClickLog-<versionName>-<wariant>.apk`; wersja i SHA commita są
  widoczne na dole ekranu Ustawienia.

Workflow [`.github/workflows/build.yml`](.github/workflows/build.yml) na każdy push, PR
i ręczne uruchomienie: testy jednostkowe → lint → `assembleDebug`; APK i raporty trafiają
do artefaktów joba. Push na domyślną gałąź lub tag `v*` publikuje dodatkowo GitHub Release
`v<versionName>` z załączonym APK.

## Jak to działa

Dwie ścieżki przechwytywania, obie zaimplementowane za wspólnym interfejsem `ClickSource`:

| Ścieżka | Klasa | Mechanizm |
|---|---|---|
| A `KEY_EVENT` | `ClickAccessibilityService` → `KeyEventClickSource` | `AccessibilityService.onKeyEvent` z `canRequestFilterKeyEvents`; działa w tle i przy zgaszonym ekranie, znacznik czasu z `KeyEvent.eventTime`; zdarzenie z zapamiętanym keycode jest konsumowane |
| B `LAUNCH_INTENT` | `LaunchReceiverActivity` → `IntentClickSource` | system uruchamia niewidoczną aktywność; czas pobierany w pierwszej linii `onCreate` |

`ClickProcessor` (czysty Kotlin, testowany jednostkowo) robi debounce (domyślnie 150 ms) i zapisuje
kliknięcia sekwencyjnie przez kolejkę na wątku IO do Room (`clicks`). `ClickLogService` (FGS,
`specialUse`) pokazuje licznik w trwałej notyfikacji, utrzymuje proces i `MediaSession`.

### Dwa wpisy na liście aplikacji

Aplikacja ma dwie ikony: **ClickLog** (główny ekran) i **ClickLog – przycisk**
(`LaunchReceiverActivity`). Systemowy wybór aplikacji dla *Programmable Key* zwykle pokazuje
aktywności z kategorii LAUNCHER – do przycisku należy przypisać **ClickLog – przycisk**.
Jeśli vendor pozwala wybrać tylko „ClickLog”, kreator i tak wykryje uruchomienie (`MainActivity`
loguje każdy Intent, a przy ścieżce B uruchomienie z zapamiętanym referrerem liczy się jako klik).

## Kreator i diagnostyka

Przy pierwszym uruchomieniu startuje `SetupWizard` (5 kroków, §3.1 specyfikacji). Podczas
testu przycisku aplikacja nasłuchuje jednocześnie na:

- `MainActivity.dispatchKeyEvent` (kanał `ACTIVITY_KEY`),
- `AccessibilityService.onKeyEvent` (`ACCESSIBILITY_KEY`, z flagą `screen_on`),
- `LaunchReceiverActivity` i `MainActivity.onCreate/onNewIntent` (`LAUNCH_INTENT`, `MAIN_INTENT`),
- dynamiczny `BroadcastReceiver` na akcje z `VendorActions.kt` (`VENDOR_BROADCAST`),
- `MediaSession` w usłudze (`MEDIA_BUTTON`).

Wszystko trafia do tabeli `diagnostic_log`. Regułę wyboru ścieżki (§3.3) implementuje
`PathSelector.select()`. Ekran *Diagnostyka* (z ustawień) pokazuje log na żywo, eksportuje go
przez share sheet i czyści.

## Struktura

```
app/src/main/java/com/klikacz/clicklog/
  clicks/        ClickSource, KeyEventClickSource, IntentClickSource, ClickProcessor,
                 ClickRepository, ClickPipeline, ClickConfigProvider
  diagnostics/   SetupWizardScreen/ViewModel, DiagnosticLogger, VendorActions,
                 VendorBroadcastListener, PathSelector, DiagnosticScreen/ViewModel
  service/       ClickLogService (FGS), ClickAccessibilityService, BootReceiver,
                 LaunchReceiverActivity
  data/          AppDatabase, ClickDao, DiagnosticDao, entities, Settings (DataStore)
  ui/            MainScreen, EventsScreen, SettingsScreen, Navigation, theme
  export/        CsvExporter
  di/            AppModule (Hilt)
```

## Eksport

- `clicks.csv` – kolumny `id, session_id, wall_time_iso, wall_time_ms, uptime_ms, source`;
  przez `ACTION_CREATE_DOCUMENT` („Zapisz plik”) lub share sheet („Udostępnij”).
- `diagnostic_log.txt` – TSV z pełnym logiem diagnostycznym, przez share sheet.

Ręczna checklista testów na urządzeniu: [TESTING.md](TESTING.md).
