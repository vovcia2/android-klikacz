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

### Podpis debug

`keystore/debug.keystore` (standardowe hasło `android`) jest w repozytorium, żeby każdy build –
lokalny i z CI – miał ten sam podpis i instalował się jako aktualizacja bez odinstalowania
(które kasuje bazę, ustawienia i włączoną usługę dostępności). Pierwsza instalacja APK z tym
kluczem po wersji z innym podpisem wymaga jeszcze jednorazowego odinstalowania.

## Jak to działa

Trzy ścieżki przechwytywania za wspólnym interfejsem `ClickSource`:

| Ścieżka | Klasa | Mechanizm |
|---|---|---|
| A `KEY_EVENT` | `ClickAccessibilityService` → `KeyEventClickSource` | `AccessibilityService.onKeyEvent` z `canRequestFilterKeyEvents`; działa w tle i przy zgaszonym ekranie, znacznik czasu z `KeyEvent.eventTime`; zdarzenie z zapamiętanym keycode jest konsumowane |
| B `LAUNCH_INTENT` | `LaunchReceiverActivity` → `IntentClickSource` | system uruchamia niewidoczną aktywność; czas pobierany w pierwszej linii `onCreate` |
| C `VENDOR_BROADCAST` | `SamsungHardKeyReceiver` → `BroadcastClickSource` | Samsung XCover / górny przycisk: `HARD_KEY_REPORT` (meta-data `HARD_KEY_PRESS` w manifeście czyni aplikację wybieralną w Ustawienia → Funkcje zaawansowane → Przycisk XCover / Górny przycisk); działa przy zgaszonym ekranie, bez usługi dostępności |

**Obie ścieżki działają jednocześnie.** Na CAT S75 systemowa obsługa przycisku (`com.bullitt.wave`)
dostarcza gest różnie zależnie od stanu ekranu: pojedyncze naciśnięcie to surowy keycode 291
(`gpio_keys`), podwójne naciśnięcie przy włączonym ekranie to jeden syntetyczny keycode 141
(`KEYCODE_F11`, urządzenie `ff_key`), a przy zgaszonym ekranie to uruchomienie przypisanej
aplikacji. Kreator uczy się keycode gestu, który użytkownik faktycznie przypisał. Dlatego uruchomienia `LaunchReceiverActivity` są liczone zawsze, a ścieżka
A dodatkowo przechwytuje `KeyEvent` przy włączonym ekranie. `ClickProcessor` (czysty Kotlin,
testowany jednostkowo) robi debounce (domyślnie 150 ms), łączy to samo naciśnięcie widziane
z obu źródeł (okno 600 ms, ustawienie „Okno łączenia źródeł”) i zapisuje kliknięcia
sekwencyjnie przez kolejkę na wątku IO do Room (`clicks`). `ClickLogService` (FGS,
`specialUse`) pokazuje licznik w trwałej notyfikacji, utrzymuje proces i `MediaSession`.

### Dwa wpisy na liście aplikacji

Aplikacja ma dwie ikony: **ClickLog** (główny ekran) i **ClickLog – button**
(`LaunchReceiverActivity`). Systemowy wybór aplikacji dla *Programmable Key* zwykle pokazuje
aktywności z kategorii LAUNCHER – do przycisku należy przypisać **ClickLog – button**.
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

## Lokalizacja

Każde kliknięcie dostaje przybliżoną pozycję (kolumny `latitude`, `longitude`,
`location_accuracy_m`, `location_age_ms`, `location_provider`): natychmiast ostatnia znana
pozycja z `LocationManager` (fused/network/gps), a potem w ciągu 30 s wynik
`getCurrentLocation` w trybie `QUALITY_LOW_POWER`. Wymaga uprawnienia lokalizacji; usługa
pierwszoplanowa dostaje wtedy typ `location`, żeby działało w tle. Opcjonalnie „cały czas”
(`ACCESS_BACKGROUND_LOCATION`). Wyłączane w ustawieniach. Brak Play Services.

## Eksport

- `clicks.csv` – kolumny `id, session_id, wall_time_iso, wall_time_ms, uptime_ms, source, latitude, longitude, location_accuracy_m, location_age_ms, location_provider`;
  przez `ACTION_CREATE_DOCUMENT` („Zapisz plik”) lub share sheet („Udostępnij”).
- `diagnostic_log.txt` – TSV z pełnym logiem diagnostycznym, przez share sheet.

Ręczna checklista testów na urządzeniu: [TESTING.md](TESTING.md).
