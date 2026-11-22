# ⛏️ MinersWorldCoin Android Miner

* Total downloads ![Total Downloads](https://img.shields.io/github/downloads/Miners-World-Coin-MWC/MinersWorldCoin-Android-Miner/total) 
* 1.9.5 Downloads ![Downloads for 1.9.5](./195.svg?raw=true)

> Note: Antivirus engines may detect this application as a "crypto miner".
> This is expected behavior because the application intentionally performs
> cryptocurrency mining when the user enables it.
> 
> Mining only starts when the user presses the "Start Mining" button.
> The application does not perform any hidden background mining.

Mining uses CPU resources and may:
* • drain battery
* • increase device temperature
* • reduce device lifespan

A lightweight, high-performance Android CPU miner built for:

* **MinersWorldCoin (MWC)**
* **AdventureCoin (ADVC)**

Designed for efficiency, stability, and real-world device compatibility, this miner automatically adapts to your hardware to maximize performance without manual configuration.

## Installation
Follow our easy to follow installation guide  [here](https://github.com/Miners-World-Coin-MWC/MinersWorldCoin-Android-Miner/blob/master/INSTALL.md)
---

## 🚀 Features

### 🔹 Multi-Coin Support

Mine:

* MWC (MinersWorldCoin)
* ADVC (AdventureCoin)

Switch algorithms seamlessly inside the app.

---

### 🔹 Intelligent Thread Detection

The miner automatically:

* Detects the number of CPU cores
* Assigns optimal thread count
* Maximizes hash rate per device
* Prevents over-allocation of system resources

No manual tuning required.

---

### 🔹 Optimized Native Mining Engine

Powered by a native C/C++ backend:

* Low overhead
* Efficient hashing
* Stable long-session mining
* Designed for ARM Android devices

---

### 🔹 Device Compatibility Improvements

✔ Locked vertical orientation
✔ UI scaling fixes across device sizes
✔ Optimized logo placement
✔ Double back button safety to prevent accidental exit

Works across a wide range of Android devices.

---

### 🔹 Sugarmaker Miner Integration

Integrated **Sugarmaker** mining core for improved efficiency and stability on supported algorithms.

---

## 📊 Why This Miner?

Unlike generic mobile miners, this project is:

* Purpose-built for MWC & ADVC
* Optimized for real Android hardware
* Community-driven
* Open-source and transparent

No hidden background services.
No bundled trackers.
No unnecessary permissions.

Just pure CPU mining.

---

## ⚙️ How It Works

1. Select coin (MWC or ADVC)
2. Enter your wallet address
3. Start mining
4. The app auto-configures threads based on your device
5. Shares are submitted to the configured mining pool

That’s it.

---

## 📦 Download

Download the latest release APK from the Releases page:

➡ `androidMinerMWC.apk`

You can track total downloads above via the badge.

---

## 🛠 Built With

* Android (Java/Kotlin frontend)
* Native C/C++ mining backend (NDK)
* Yespower-based algorithm variants
* ARM-optimized hashing

### Building from source

Requirements: JDK 17, Android SDK platform 35, NDK `25.1.8937393`, CMake `3.22.1`.

For a signed local release build, create `app/keystore.properties` (gitignored):

```properties
storeFile=keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Then run:

```bash
./gradlew assembleRelease testReleaseUnitTest
```

The signed APK is written to `app/build/outputs/apk/release/app-release.apk`.

GitHub Actions produces the same signed release on every push and pull request
(`.github/workflows/android-build.yml`). It expects these repository secrets:
`ANDROID_KEYSTORE_BASE64` (base64 of the JKS file), `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`.

---

## ⚠️ Disclaimer

Mining uses CPU resources and will:

* Increase battery usage
* Generate heat
* Reduce battery lifespan if used continuously

Use responsibly.

---

## 🤝 Contributing

Pull requests are welcome.

If you’d like to:

* Improve performance
* Add new algorithm support
* Improve UI/UX
* Expand multi-coin functionality

Fork it, build it, submit it.

---

## 🌍 Community

* Official MWC & ADVC communities
* Pool operators
* Developers
* Mobile miners

This project exists to strengthen the ecosystem.


TODO:
- [x] Force app to have vertical orientation
- [x] Fix scaling issues with other devices
- [x] Suggestion: Adding a double back button
- [x] Fix Logo positioning on smaller and larger devices
- [x] Add Sugarmaker miner
