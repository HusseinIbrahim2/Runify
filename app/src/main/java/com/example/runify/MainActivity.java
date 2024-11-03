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

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private boolean isPermissionGranted = false;
    private static final String EMERGENCY_PHONE_NUMBER = "your phone number here";

    private TextView stepCountText, distanceText,
            caloriesText, speedText, elevationText;
    private int stepCount = 0;

    private MaterialCardView statsCard;
    private FloatingActionButton resetFab;
    private Button shareButton, startTrackingButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        checkPermissionAndSetupSensors();
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
    }

    private void checkPermissionAndSetupSensors() {
        if ((ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED)) {
            isPermissionGranted = true;
            setupSensors();
        } else {
            requestPermission();
        }
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{android.Manifest.permission.ACTIVITY_RECOGNITION},
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

    private void setupSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
    }
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            if (stepCount == 0) {
                stepCount = (int) event.values[0];
            }
            int currentSteps = (int) event.values[0] - stepCount;
            stepCountText.setText(String.valueOf(currentSteps));
        }
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
        if (stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }
}

