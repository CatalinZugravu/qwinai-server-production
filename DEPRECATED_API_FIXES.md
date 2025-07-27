# Deprecated API Migration Guide

## 🔴 Critical Fixes Needed

### 1. Replace onBackPressed() - URGENT
**Files affected**: 
- `BlockingUpdateActivity.kt:85`
- `SettingsActivity.kt:82` 
- `TextSelectionActivity.kt:307`

**Current (Deprecated)**:
```kotlin
override fun onBackPressed() {
    super.onBackPressed()
    // custom logic
}
```

**Fixed (Modern)**:
```kotlin
private val onBackPressedCallback = object : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
        // custom logic
        // Call finish() or navigate as needed
    }
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
}
```

### 2. Replace VIBRATOR_SERVICE - MEDIUM
**Files affected**: Multiple activities and utils

**Current (Deprecated)**:
```kotlin
val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
```

**Fixed (Modern)**:
```kotlin
val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
    vibratorManager.defaultVibrator
} else {
    @Suppress("DEPRECATION")
    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
}
```

### 3. Replace startActivityForResult() - MEDIUM
**Files affected**: 
- `MainActivity.kt:5296`
- `OCRCameraActivity.kt:100`

**Current (Deprecated)**:
```kotlin
startActivityForResult(intent, REQUEST_CODE)
```

**Fixed (Modern)**:
```kotlin
private val activityResultLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        // handle result
    }
}

// Use instead of startActivityForResult
activityResultLauncher.launch(intent)
```

### 4. Replace overridePendingTransition() - LOW
**Current (Deprecated)**:
```kotlin
overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
```

**Fixed (Modern)**:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    overrideActivityTransition(
        Activity.OVERRIDE_TRANSITION_OPEN,
        R.anim.fade_in,
        R.anim.fade_out
    )
} else {
    @Suppress("DEPRECATION")
    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
}
```

### 5. Update Ad SDK Versions - MEDIUM

**AppLovin SDK** - Update to latest version:
```kotlin
// In libs.versions.toml
applovin-sdk = "12.6.0" // Latest stable version
```

**IronSource SDK** - Use new API:
```kotlin
// Replace deprecated interfaces with new ones
// Check IronSource documentation for latest migration guide
```

## 🟡 Code Quality Improvements

### Fix Type Safety Issues
**Files with nullable issues**: Multiple Kotlin files

**Pattern to fix**:
```kotlin
// Instead of
someString.contains("text") // might crash if null

// Use
someString?.contains("text") ?: false
// or
someString.orEmpty().contains("text")
```

### Remove Dead Code
**Files with "always true" conditions**: 
- `ConversationsViewModel.kt:425`
- `MainActivity.kt:4055`
- `GooglePlayBillingProvider.kt:160`

**Action**: Review and remove redundant conditions

## 📅 Implementation Priority

1. **Week 1**: Fix onBackPressed() in all activities
2. **Week 2**: Update VIBRATOR_SERVICE usage
3. **Week 3**: Migrate startActivityForResult()
4. **Week 4**: Update Ad SDKs
5. **Week 5**: Fix type safety issues
6. **Week 6**: Clean up dead code

## 🧪 Testing Required

- Test on Android 14+ devices
- Test back navigation
- Test vibration functionality
- Test ad loading
- Test camera/file picker flows