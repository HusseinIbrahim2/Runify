# Runify 🏃‍♂️

Runify is a comprehensive Android fitness tracking application that helps users monitor their running and walking activities with real-time statistics, route tracking, and safety features.

## Features 🌟

- **Real-time Activity Tracking**
  - Step counting
  - Distance calculation
  - Speed monitoring
  - Elevation tracking
  - Calorie burn estimation

- **Interactive Map**
  - Real-time route visualization
  - Live location tracking
  - Route history display

- **Safety Features**
  - Fall detection using proximity sensor
  - Automatic emergency SMS with location
  - Customizable emergency contact

- **Stats & Analytics**
  - Detailed workout statistics
  - Progress monitoring
  - Performance metrics
  - Elevation gain tracking

- **Social Sharing**
  - Share workout summaries
  - Export activity data
  - Custom formatted activity reports

## Technical Requirements 🔧

- Android Studio Arctic Fox or newer
- Minimum SDK: Android 21 (Lollipop)
- Target SDK: Android 33 (or newer)
- Google Play Services (for Maps integration)
- Google Maps API Key

## Dependencies 📚

```gradle
dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.9.0'
    implementation 'com.google.android.gms:play-services-maps:18.1.0'
    implementation 'com.google.android.gms:play-services-location:21.0.1'
}
```

## Setup & Installation 🔨

1. Clone the repository:
```bash
git clone https://github.com/HusseinIbrahim2/runify.git
```

2. Add your Google Maps API key in `local.properties`:
```properties
MAPS_API_KEY=your_api_key_here
```

3. Update the emergency contact number in `MainActivity.java`:
```java
private static final String EMERGENCY_PHONE_NUMBER = "your_emergency_number";
```

4. Build and run the project in Android Studio

## Required Permissions 📱

The app requires the following permissions:
```xml
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
<uses-permission android:name="android.permission.SEND_SMS" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

## How It Works 🔄

### Activity Tracking
- Uses Android's Step Counter sensor for accurate step counting
- Calculates distance using step length based on user height
- Monitors speed using GPS location updates
- Tracks elevation changes for accurate calorie calculations

### Safety System
- Utilizes the proximity sensor for fall detection
- Automatically sends emergency SMS with location link
- Implements cooldown period to prevent false alarms

### Location Tracking
- Uses FusedLocationProviderClient for efficient location updates
- Implements accuracy thresholds for reliable tracking
- Optimizes battery usage with smart update intervals

## Contributing 🤝

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## Acknowledgments 👏

- Google Maps Platform for location services
- Android Sensor Framework
- Material Design Components

## Contact 📧

- [hussein.ibr3@gmail.com](mailto:hussein.ibr3@gmail.com)

[https://github.com/HusseinIbrahim2/runify](https://github.com/HusseinIbrahim2/runify)
