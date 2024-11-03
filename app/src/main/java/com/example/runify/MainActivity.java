package com.example.runify;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.hardware.SensorEventListener;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

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

    private TextView stepCountText, distanceText,
            caloriesText, speedText, elevationText;
    private int stepCount = 0;
    private boolean isProximityClose = false;
    private long lastSmsSentTime = 0;
    private static final long SMS_COOLDOWN_PERIOD = 300000; // 5 minutes in milliseconds

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