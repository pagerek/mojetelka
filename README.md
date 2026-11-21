# Webshare TV (Netflix-style klient pro Android TV)

Moderní nativní aplikace pro **Android TV / Google TV** postavená nad **Webshare.cz VIP** s katalogem filmů a seriálů, chytrým seskupováním stovek výsledků vyhledávání podle **kvality × jazyka**, automatickým výběrem podle výchozího nastavení s **fallbackem** a hardwarově akcelerovaným přehrávačem **AndroidX Media3 (ExoPlayer)**.

## Hlavní funkce
- **Katalog filmů a seriálů ve stylu Netflixu:**
  - Přehledné řady (*České a slovenské filmové hity*, *Trendy a nejoblíbenější filmy*, *Populární seriály*, *Filmové novinky*, *Oblíbené české seriály*, *Pokračovat ve sledování*).
  - Velký **Hero banner** s pozadím, hodnocením, žánry a českým popisem.
  - Podpora jak bezklíčového katalogu ihned po instalaci, tak volitelného **TMDB v3 API klíče** (`cs-CZ`) v *Nastavení*.
- **Chytré vyhledávání a seskupování streamů z Webshare.cz (`StreamResolver`):**
  - Odfiltruje zaheslované soubory, archivy, ukázky (`sample` / `trailer`) a nesouvisející soubory.
  - Automaticky detekuje **Kvalitu** (`4K UHD`, `Full HD 1080p`, `HD 720p`, `SD`), **Jazyk** (`CZ / SK dabing`, `CZ titulky`, `Originál EN`), kodek (`HEVC / x265`), `HDR / Dolby Vision` a prostorový zvuk `5.1`.
  - Seskupí desítky až stovky výsledků do čistých kategorií **Jazyk × Kvalita** a v každé vybere nejlépe hodnocený stream.
- **Výchozí kvalita a jazyk v Nastavení + Automatický Fallback:**
  - V *Nastavení* zvolíš preferovanou kvalitu (např. `Full HD 1080p` nebo `4K UHD`) a preferovaný jazyk (`CZ/SK dabing`).
  - Po kliknutí na **Přehrát** aplikace automaticky spustí nejlepší stream odpovídající tvým preferencím. Pokud daná kombinace neexistuje, automaticky použije **fallback** na nejbližší dostupnou kvalitu nebo jazyk (a zobrazí o tom informaci v přehrávači).
  - Kdykoliv si můžeš jedním tlačítkem otevřít i ruční výběr skupin **Kvalita × Jazyk** nebo přepnout zdroj přímo během přehrávání.
- **Moderní přehrávač AndroidX Media3 (ExoPlayer):**
  - Přímé přehrávání VIP streamů z CDN Webshare.cz s podporou okamžitého přetáčení (`-15 s` / `+30 s` šipkami na ovladači).
  - Přepínání vestavěných **zvukových stop** a **titulků** přímo v přehrávači.
  - Automatické ukládání pozice rozkoukaných filmů a epizod (**Pokračovat ve sledování**) + tlačítko **Další díl** u seriálů.

---

## Sestavení APK balíčku

V projektu je připravené lokální prostředí v `.tools/` (JDK 17, Android SDK 34, `adb`, Gradle 8.7):

```bash
/Users/honza/Documents/VibeCoding/Televize/.tools/gradle/gradle-8.7/bin/gradle assembleDebug
```

Výsledný instalační soubor najdeš v:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Instalace do Android TV přes Wi-Fi / LAN (`adb`)

1. Na Android TV otevři **Nastavení → Předvolby zařízení → O zařízení**, sjeď na **Číslo sestavení** a 7× na něj klikni ovladačem, dokud se nezobrazí *„Jste vývojář“*.
2. Vrať se o úroveň zpět do **Možnosti pro vývojáře** a zapni **Ladění přes USB / Síťové ladění (ADB)**.
3. Zjisti IP adresu své televize (v *Nastavení → Síť a internet*).
4. Na Macu spusť:
   ```bash
   /Users/honza/Documents/VibeCoding/Televize/.tools/android-sdk/platform-tools/adb connect <IP_ADRESA_TV>:5555
   ```
   *(Na obrazovce TV potvrď dialog „Povolit ladění USB z tohoto počítače“.)*
5. Nainstaluj aplikaci:
   ```bash
   /Users/honza/Documents/VibeCoding/Televize/.tools/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
