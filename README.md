# Çorba Kasesi

GameMaker oyunlarını Android'de çalıştıran, tek bir oyun paketi için özelleştirilmiş bir uygulama.
Açık kaynak [Butterscotch](https://github.com/ButterscotchRunner/Butterscotch) GameMaker runtime'ının Android arayüzü üzerine kurulu.

**İndir:** https://mercimekcik.github.io/dev.mercimekcik.corbakasesi/

## Klasörler

| Klasör | İçerik |
|---|---|
| `android/` | Android uygulaması (Kotlin + Jetpack Compose, runtime'ı APK içinden okuyan küçük bir C katmanı) |
| `android/butterscotch-patches/` | Butterscotch'a uygulanan değişiklikler |
| `docs/` | İndirme sitesi (GitHub Pages) |

## Derleme

Gerekenler: Android Studio ya da Android SDK + NDK, JDK 17+.

1. Butterscotch'u `android` klasörünün yanına indir ve yamaları uygula:

   ```sh
   git clone https://github.com/ButterscotchRunner/Butterscotch
   cd Butterscotch
   git checkout b9f16f3c40aaccd87dbfc0b846194b472cc075b8
   git apply ../android/butterscotch-patches/*.patch
   cd ..
   ```

   Başka bir yerdeyse derlerken `-Pbutterscotch.dir=/yol/Butterscotch` ver.

2. Oyunu `android/app/src/main/assets/game/` içine koy (`data.win` ve yanındaki dosyalar). İkinci bir oyun
   `assets/game1/`, üçüncüsü `assets/game2/` ... Ana ekrandaki logo ve açıklama `assets/launcher/<klasör>/`
   içindeki `logo.png` ve `info.json`'dan gelir. Oyun dosyaları bu repoya dahil değildir.

3. Derle:

   ```sh
   cd android
   ./gradlew assembleRelease
   ```

   Release imzası için `android/keystore.properties` gerekir (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`).
   Bu dosya ve keystore repoya eklenmez.

## Sürüm yayınlama

Sürüm adı derleme tarihidir (`2026.10.17`). GitHub'da etiketi `v2026.10.17` olan bir Release oluşturup APK'yi ekle;
uygulama açılışta yeni sürümü görür, indirme sitesi de en son Release'teki APK'yi gösterir.

## Lisans

Uygulamanın kaynak kodu, Butterscotch gibi, [GNU AGPL-3.0](LICENSE) ile lisanslıdır.
Uygulamayla dağıtılan oyunlar bu lisansa dahil değildir, kendi sahiplerine aittir.
