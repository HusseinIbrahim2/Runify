package com.example.runify;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.hardware.SensorEventListener;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.telephony.SmsManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements
        SensorEventListener,
        OnMapReadyCallback {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String EMERGENCY_PHONE_NUMBER = "your phone number here";
    private static final long SMS_COOLDOWN_PERIOD = 300000;
    private static final long LOCATION_UPDATE_INTERVAL = 5000;
    private static final float MIN_ACCURACY_THRESHOLD = 20.0f;
    private static final float MIN_DISTANCE_THRESHOLD = 2.0f;
    private static final long MIN_TIME_THRESHOLD = 1000;
    private static final float CALORIES_PER_STEP = 0.03f;
    private static final float MET_WALKING = 3.8f;
    private static final float MET_RUNNING = 7.0f;
    private static final float RUNNING_THRESHOLD = 6.5f;

    private float weight = 74.0f;
    private float height = 179.0f;
    private float stepLength;

    // UI elements
    private TextView stepCountText, distanceText, caloriesText, speedText, elevationText;
    private MaterialCardView statsCard;
    private FloatingActionButton resetFab;
    private Button shareButton, startTrackingButton;
    private MapView mapView;

    // Sensors and location
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    // State variables
    private boolean isPermissionGranted = false;
    private boolean isTracking = false;
    private boolean isProximityClose = false;
    private float initialStepCount = -1;
    private int currentStepCount = 0;
    private float totalDistance = 0;
    private float distanceFromSteps = 0;
    private float currentSpeed = 0;
    private float currentElevation = 0;
    private float totalElevationGain = 0;
    private float lastElevation = 0;
    private float[] lastKnownSpeeds = new float[5];
    private int speedIndex = 0;
    private long lastSmsSentTime = 0;
    private long lastLocationTimestamp = 0;
    private Location previousLocation = null;
    private List<LatLng> routePoints = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        stepLength = calculateStepLength(height);

        initializeViews();
        checkPermissionAndSetupSensors();
        setupLocationServices();

        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    private float calculateStepLength(float heightCm) {
        return (heightCm * 0.415f) / 100;
    }

    private void initializeViews() {
        stepCountText = findViewById(R.id.stepCountText);
        distanceText = findViewById(R.id.distanceText);
        caloriesText = findViewById(R.id.caloriesText);
        speedText = findViewById(R.id.speedText);
        elevationText = findViewById(R.id.elevationText);
        statsCard = findViewById(R.id.statsCard);
        resetFab = findViewById(R.id.resetFab);
        shareButton = findViewById(R.id.shareButton);
        startTrackingButton = findViewById(R.id.startTrackingButton);
        mapView = findViewById(R.id.mapView);

        startTrackingButton.setOnClickListener(v -> toggleTracking());
        resetFab.setOnClickListener(v -> resetTracker());
        shareButton.setOnClickListener(v -> shareProgress());
    }

    private void checkPermissionAndSetupSensors() {
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
                && (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)
                && (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                && (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            isPermissionGranted = true;
            setupSensors();
        } else {
            requestPermission();
        }
    }
    private void requestPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.ACTIVITY_RECOGNITION,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                PERMISSION_REQUEST_CODE

        );
    }

    private void setupSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        // Register proximity sensor
        Sensor proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (proximitySensor != null) {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(LOCATION_UPDATE_INTERVAL)
                .setFastestInterval(LOCATION_UPDATE_INTERVAL / 2)
                .setSmallestDisplacement(MIN_DISTANCE_THRESHOLD);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null || !isTracking) return;

                Location location = locationResult.getLastLocation();
                if (location != null && location.getAccuracy() <= MIN_ACCURACY_THRESHOLD) {
                    updateLocationData(location);
                }
            }
        };
    }

    private void updateLocationData(Location location) {
        if (location == null) return;

        LatLng currentPoint = new LatLng(location.getLatitude(), location.getLongitude());
        long currentTime = System.currentTimeMillis();

        if (previousLocation != null && lastLocationTimestamp != 0) {
            float timeDiff = (currentTime - lastLocationTimestamp) / 1000f; // seconds
            if (timeDiff < MIN_TIME_THRESHOLD / 1000f) return;

            float distance = location.distanceTo(previousLocation);
            if (isRealisticMovement(distance, timeDiff)) {
                totalDistance += distance / 1000f; // Convert to kilometers
                float instantSpeed = (distance / timeDiff) * 3.6f; // Convert to km/h
                updateSpeedAverage(instantSpeed);
                updateElevationData(location.getAltitude());
                routePoints.add(currentPoint);
                updateMap();
            }
        }

        previousLocation = location;
        lastLocationTimestamp = currentTime;
        updateStats();
    }

    private boolean isRealisticMovement(float distance, float timeDiff) {
        float speed = (distance / timeDiff) * 3.6f;
        return speed <= 25.0f;
    }

    private void updateSpeedAverage(float newSpeed) {
        lastKnownSpeeds[speedIndex] = newSpeed;
        speedIndex = (speedIndex + 1) % lastKnownSpeeds.length;

        float sum = 0;
        int count = 0;
        for (float speed : lastKnownSpeeds) {
            if (speed > 0) {
                sum += speed;
                count++;
            }
        }
        currentSpeed = count > 0 ? sum / count : 0;
    }

    private void updateElevationData(double newElevation) {
        if (lastElevation != 0) {
            float elevationDiff = (float) newElevation - lastElevation;
            if (Math.abs(elevationDiff) > 0.5) {
                if (elevationDiff > 0) {
                    totalElevationGain += elevationDiff;
                }
                currentElevation = (float) newElevation;
            }
        }
        lastElevation = (float) newElevation;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_STEP_COUNTER:
                if (initialStepCount < 0) {
                    initialStepCount = event.values[0];
                }
                currentStepCount = (int) (event.values[0] - initialStepCount);
                updateStepDistance();
                break;

            case Sensor.TYPE_PROXIMITY:
                float proximity = event.values[0];
                if (proximity == 0) {
                    if (!isProximityClose) {
                        isProximityClose = true;
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastSmsSentTime >= SMS_COOLDOWN_PERIOD) {
                            sendEmergencySMS();
                            lastSmsSentTime = currentTime;
                        }
                    }
                } else {
                    isProximityClose = false;
                }
                break;
        }
    }

    private void updateStepDistance() {
        distanceFromSteps = currentStepCount * stepLength / 1000;
        updateStats();
    }

    private float calculateCalories() {
        float calories = 0.0f;
        calories += currentStepCount * CALORIES_PER_STEP;
        calories += totalElevationGain * 0.1f * weight / 1000f;
        float hours = totalDistance / Math.max(currentSpeed, 1.0f);
        float met = currentSpeed > RUNNING_THRESHOLD ? MET_RUNNING : MET_WALKING;
        calories += weight * met * hours;

        return Float.isNaN(calories) || calories < 0 ? 0 : calories;
    }

    private void updateStats() {
        float finalDistance = Math.max(totalDistance, distanceFromSteps);

        String speedDisplay = currentSpeed < 0.5 ? "0.0" : String.format("%.1f", currentSpeed);
        stepCountText.setText(String.format("%,d", currentStepCount));
        distanceText.setText(String.format("%.2f km", finalDistance));
        caloriesText.setText(String.format("%.0f cal", calculateCalories()));
        speedText.setText(String.format("%s km/h", speedDisplay));
        elevationText.setText(String.format("%.1f m", currentElevation));
    }

    private void updateMap() {
        if (googleMap == null || routePoints.size() < 2) return;

        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(routePoints)
                .color(ContextCompat.getColor(this, R.color.black))
                .width(12);

        googleMap.clear();
        googleMap.addPolyline(polylineOptions);
        googleMap.animateCamera(CameraUpdateFactory.newLatLng(
                routePoints.get(routePoints.size() - 1)));
    }

    private void toggleTracking() {
        isTracking = !isTracking;
        startTrackingButton.setText(isTracking ? "Stop Tracking" : "Start Tracking");

        if (isTracking) {
            startLocationUpdates();
            if (stepSensor != null) {
                sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        } else {
            stopLocationUpdates();
            sensorManager.unregisterListener(this, stepSensor);
        }
    }


    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(LOCATION_UPDATE_INTERVAL);

        fusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback,
                Looper.getMainLooper());
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void resetTracker() {
        initialStepCount = -1;
        currentStepCount = 0;
        totalDistance = 0;
        distanceFromSteps = 0;
        currentSpeed = 0;
        currentElevation = 0;
        totalElevationGain = 0;
        lastElevation = 0;
        previousLocation = null;
        lastLocationTimestamp = 0;
        speedIndex = 0;
        for (int i = 0; i < lastKnownSpeeds.length; i++) {
            lastKnownSpeeds[i] = 0;
        }
        routePoints.clear();
        if (googleMap != null) {
            googleMap.clear();
        }
        updateStats();
    }

    private void shareProgress() {
        String shareText = String.format(
                "Today's Workout Summary:\n" +
                        "🚶 Steps: %d\n" +
                        "📏 Distance: %.2f km\n" +
                        "🔥 Calories: %.0f\n" +
                        "⚡ Speed: %.1f km/h\n" +
                        "📈 Elevation: %.1f m",
                currentStepCount,
                Math.max(totalDistance, distanceFromSteps),
                calculateCalories(),
                currentSpeed,
                currentElevation
        );

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(shareIntent, "Share your workout"));
    }

    private void sendEmergencySMS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                SmsManager smsManager = SmsManager.getDefault();
                String message = "Emergency! Possible fall detected. Last known location: ";

                if (previousLocation != null) {
                    message += "http://maps.google.com/maps?q=" +
                            previousLocation.getLatitude() + "," +
                            previousLocation.getLongitude();
                }

                smsManager.sendTextMessage(EMERGENCY_PHONE_NUMBER, null, message, null, null);
                Toast.makeText(this, "Emergency SMS sent", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Failed to send emergency SMS", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS},
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                isPermissionGranted = true;
                setupSensors();
                startLocationUpdates();
            } else {
                Toast.makeText(this,
                        "Required permissions denied. Some features won't work.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
            googleMap.getUiSettings().setMyLocationButtonEnabled(true);
        }

        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        googleMap.setMinZoomPreference(10);

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Handle accuracy changes if needed
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();

        if (isTracking) {
            startLocationUpdates();
            if (stepSensor != null) {
                sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
        Sensor proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (proximitySensor != null) {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        sensorManager.unregisterListener(this);
        stopLocationUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}