package com.example.runify;

import android.Manifest;
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
    private boolean isPermissionGranted = false;
    private static final String EMERGENCY_PHONE_NUMBER = "your phone number here";
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private GoogleMap googleMap;
    private MapView mapView;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private TextView stepCountText, distanceText,
            caloriesText, speedText, elevationText;
    private int stepCount = 0;
    private Location lastLocation = null;
    private long lastLocationTimestamp = 0;
    private boolean isProximityClose = false;
    private long lastSmsSentTime = 0;
    private boolean isTracking = false;
    private static final long SMS_COOLDOWN_PERIOD = 300000; // 5 minutes in milliseconds
    private static final long LOCATION_UPDATE_INTERVAL = 5000;
    private float[] lastKnownSpeeds = new float[5]; // Rolling average of last 5 speed readings
    private int speedIndex = 0;
    private float currentSpeed = 0;
    private float totalDistance = 0;
    private float totalElevationGain = 0;
    private float currentElevation = 0;
    private float lastElevation = 0;
    private static final float CALORIES_PER_STEP = 0.04f;
    private float weight = 70.0f;
    private static final float MET_WALKING = 3.5f;
    private static final float MET_RUNNING = 8.0f;
    private static final float RUNNING_THRESHOLD = 6.5f;
    private List<LatLng> routePoints = new ArrayList<>();
    private MaterialCardView statsCard;
    private FloatingActionButton resetFab;
    private Button shareButton, startTrackingButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        checkPermissionAndSetupSensors();
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    private void checkPermissionAndSetupSensors() {
        if ((ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
                && (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)
                && (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                && (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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
            } else {
                Toast.makeText(this, "Permissions denied. Step counting or SMS won't work.", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null || !isTracking) return;

                Location location = locationResult.getLastLocation();
                updateLocationData(location);
            }
        };
    }
    private void updateLocationData(Location location) {
        if (location == null) return;

        LatLng currentPoint = new LatLng(location.getLatitude(), location.getLongitude());
        long currentTime = System.currentTimeMillis();
        if (lastLocation != null && lastLocationTimestamp != 0) {
            float timeDiff = (currentTime - lastLocationTimestamp) / 1000f;
            float distance = location.distanceTo(lastLocation);
            if (isRealisticMovement(distance, timeDiff)) {
                totalDistance += distance / 1000f;
                float currentSpeed = (distance / timeDiff) * 3.6f;
                updateSpeedAverage(currentSpeed);
                updateElevationData(location.getAltitude());
                routePoints.add(currentPoint);
                updateMap();
            }
        }
        lastLocation = location;
        lastLocationTimestamp = currentTime;
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
    }

    private void setupSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
    }
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_STEP_COUNTER:
                if (stepCount == 0) {
                    stepCount = (int) event.values[0];
                }
                int currentSteps = (int) event.values[0] - stepCount;
                stepCountText.setText(String.valueOf(currentSteps));
                break;
            case Sensor.TYPE_PROXIMITY:
                float proximity = event.values[0];

                if (proximity == 0) {
                    if (!isProximityClose) {
                        isProximityClose = true;

                        long currentTimes = System.currentTimeMillis();
                        if (currentTimes - lastSmsSentTime >= SMS_COOLDOWN_PERIOD) {
                            sendEmergencySMS();
                            lastSmsSentTime = currentTimes;
                        }
                    }
                } else {
                    isProximityClose = false;
                    lastSmsSentTime=0;
                }
                break;
        }
    }
    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
        }
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.setMinZoomPreference(15);
    }
    private void sendEmergencySMS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            SmsManager smsManager = SmsManager.getDefault();
            String message = "Emergency! Possible fall detected. Please check on the user.";
            smsManager.sendTextMessage(EMERGENCY_PHONE_NUMBER, null, message, null, null);
            Toast.makeText(this, "Emergency SMS sent", Toast.LENGTH_SHORT).show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, PERMISSION_REQUEST_CODE);
        }
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
    private float calculateCalories() {
        float calories = 0.0f;
        calories += stepCount * CALORIES_PER_STEP;
        calories += totalElevationGain * 0.17f * weight / 1000f;
        float hours = totalDistance / currentSpeed;
        float met = currentSpeed > RUNNING_THRESHOLD ? MET_RUNNING : MET_WALKING;
        calories += weight * met * hours;
        return Float.isNaN(calories) || calories < 0 ? 0 : calories;
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
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
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
    }
}