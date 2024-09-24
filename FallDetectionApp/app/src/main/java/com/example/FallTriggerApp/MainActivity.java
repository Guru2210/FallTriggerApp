package com.example.FallTriggerApp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private TextView xText, yText, zText;
    private TextView gyroXText, gyroYText, gyroZText;
    private EditText phoneNumberInput;
    private Button saveButton,settingBtn;
    private SensorManager sensorManager;
    private Sensor accelerometerSensor, gyroscopeSensor;
    private boolean isAccelerometerSensorAvailable, isGyroscopeSensorAvailable, isItFirstTime;
    private float currentX, currentY, currentZ, lastX, lastY, lastZ;
    private float shakeThreshold = 45f;
    float xDifference, yDifference, zDifference;
    private Vibrator vibrator;

    private static final String PREFS_NAME = "FallDetectionAppPrefs";
    private static final String PHONE_NUMBER_KEY = "PhoneNumber";

    private float[] accel = new float[3];
    private float[] gyro = new float[3];
    private float[] magnet = new float[3];
    private float[] rotationMatrix = new float[9];
    private float[] accMagOrientation = new float[3];
    private float[] gyroOrientation = new float[3];
    private float[] gyroMatrix = new float[9];
    private float[] fusedOrientation = new float[3];
    private boolean initState = true;
    private static final float EPSILON = 0.000000001f;
    private static final float NS2S = 1.0f / 1000000000.0f;
    private static final float FILTER_COEFFICIENT = 0.98f;
    private float timestamp;
    private Timer fuseTimer = new Timer();

    private LocationManager locationManager;
    private LocationListener locationListener;
    private double latitude, longitude;
    private String smsSendEnabled;
    private Integer sensitivity;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_main);

        View mainView = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        xText = findViewById(R.id.x_value);
        yText = findViewById(R.id.y_value);
        zText = findViewById(R.id.z_value);
        gyroXText = findViewById(R.id.gyro_x_value);
        gyroYText = findViewById(R.id.gyro_y_value);
        gyroZText = findViewById(R.id.gyro_z_value);
        phoneNumberInput = findViewById(R.id.phoneNumberInput);
        saveButton = findViewById(R.id.saveButton);
        settingBtn=findViewById(R.id.settings);

        Bundle bundle = getIntent().getExtras();
        smsSendEnabled=bundle!= null?bundle.getString("switch","OFF"):"OFF";
        sensitivity=bundle!= null?bundle.getInt("sensitivity",50):50;

        settingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent=new Intent(MainActivity.this,SettingsActivity.class);
                startActivity(intent);
            }
        });

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            isAccelerometerSensorAvailable = true;
        } else {
            xText.setText("Accelerometer sensor is not available");
            isAccelerometerSensorAvailable = false;
        }

        if (sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
            gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            isGyroscopeSensorAvailable = true;
        } else {
            gyroXText.setText("Gyroscope sensor is not available");
            isGyroscopeSensorAvailable = false;
        }

        // Load saved phone number
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedPhoneNumber = prefs.getString(PHONE_NUMBER_KEY, "");
        phoneNumberInput.setText(savedPhoneNumber);

        // Save phone number
        saveButton.setOnClickListener(v -> {
            String phoneNumber = phoneNumberInput.getText().toString();
            if (!phoneNumber.isEmpty()) {
                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                editor.putString(PHONE_NUMBER_KEY, phoneNumber);
                editor.apply();
                Toast.makeText(MainActivity.this, "Phone number saved", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Please enter a phone number", Toast.LENGTH_SHORT).show();
            }
        });

        // Initialize location manager and listener
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        } else {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0, locationListener);
        }

        fuseTimer.scheduleAtFixedRate(new calculateFusedOrientationTask(), 1000, 30);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        switch (sensorEvent.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(sensorEvent.values, 0, accel, 0, 3);
                calculateAccMagOrientation();
                break;
            case Sensor.TYPE_GYROSCOPE:
                gyroFunction(sensorEvent);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(sensorEvent.values, 0, magnet, 0, 3);
                break;
        }

        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            xText.setText(String.format("X: %.2f", sensorEvent.values[0]) + "m/s2");
            yText.setText(String.format("Y: %.2f", sensorEvent.values[1]) + "m/s2");
            zText.setText(String.format("Z: %.2f", sensorEvent.values[2]) + "m/s2");

            currentX = sensorEvent.values[0];
            currentY = sensorEvent.values[1];
            currentZ = sensorEvent.values[2];

            if (isItFirstTime) {
                xDifference = Math.abs(lastX - currentX);
                yDifference = Math.abs(lastY - currentY);
                zDifference = Math.abs(lastZ - currentZ);
                float newShake=40f;
                shakeThreshold=(float)(100-sensitivity)/100*newShake;
                double finalAcceleration=Math.sqrt(xDifference*xDifference+yDifference*yDifference+zDifference*zDifference);
                if (finalAcceleration > shakeThreshold) {
                    vibrator.vibrate(2000);
                    if(smsSendEnabled.equals("ON")){
                        sendFallAlert();
                    }
                }
            }

            lastX = currentX;
            lastY = currentY;
            lastZ = currentZ;
            isItFirstTime = true;
        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroXText.setText(String.format("X: %.2f", sensorEvent.values[0]) + "rad/s");
            gyroYText.setText(String.format("Y: %.2f", sensorEvent.values[1]) + "rad/s");
            gyroZText.setText(String.format("Z: %.2f", sensorEvent.values[2]) + "rad/s");
        }
    }

    private void sendFallAlert() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String phoneNumber = prefs.getString(PHONE_NUMBER_KEY, "");
        if (!phoneNumber.isEmpty()) {
            SmsManager smsManager = SmsManager.getDefault();
            String message = "Fall detected! ";
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Toast.makeText(this, "Fall alert sent", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Phone number not set", Toast.LENGTH_SHORT).show();
        }
    }

    private void calculateAccMagOrientation() {
        if (SensorManager.getRotationMatrix(rotationMatrix, null, accel, magnet)) {
            SensorManager.getOrientation(rotationMatrix, accMagOrientation);
        }
    }

    private void getRotationVectorFromGyro(float[] gyroValues, float[] deltaRotationVector, float timeFactor) {
        float[] normValues = new float[3];
        float omegaMagnitude = (float) Math.sqrt(gyroValues[0] * gyroValues[0] + gyroValues[1] * gyroValues[1] + gyroValues[2] * gyroValues[2]);

        if (omegaMagnitude > EPSILON) {
            normValues[0] = gyroValues[0] / omegaMagnitude;
            normValues[1] = gyroValues[1] / omegaMagnitude;
            normValues[2] = gyroValues[2] / omegaMagnitude;
        }

        float thetaOverTwo = omegaMagnitude * timeFactor;
        float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
        float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);
        deltaRotationVector[0] = sinThetaOverTwo * normValues[0];
        deltaRotationVector[1] = sinThetaOverTwo * normValues[1];
        deltaRotationVector[2] = sinThetaOverTwo * normValues[2];
        deltaRotationVector[3] = cosThetaOverTwo;
    }

    public void gyroFunction(SensorEvent event) {
        if (accMagOrientation == null) return;

        if (initState) {
            gyroMatrix = getRotationMatrixFromOrientation(accMagOrientation);
            initState = false;
        }

        float[] deltaVector = new float[4];
        if (timestamp != 0) {
            final float dT = (event.timestamp - timestamp) * NS2S;
            System.arraycopy(event.values, 0, gyro, 0, 3);
            getRotationVectorFromGyro(gyro, deltaVector, dT / 2.0f);
        }

        timestamp = event.timestamp;
        float[] deltaMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(deltaMatrix, deltaVector);
        gyroMatrix = matrixMultiplication(gyroMatrix, deltaMatrix);
        SensorManager.getOrientation(gyroMatrix, gyroOrientation);
    }

    private float[] getRotationMatrixFromOrientation(float[] o) {
        float[] xM = new float[9];
        float[] yM = new float[9];
        float[] zM = new float[9];

        float sinX = (float) Math.sin(o[1]);
        float cosX = (float) Math.cos(o[1]);
        float sinY = (float) Math.sin(o[2]);
        float cosY = (float) Math.cos(o[2]);
        float sinZ = (float) Math.sin(o[0]);
        float cosZ = (float) Math.cos(o[0]);

        xM[0] = 1.0f;
        xM[1] = 0.0f;
        xM[2] = 0.0f;
        xM[3] = 0.0f;
        xM[4] = cosX;
        xM[5] = sinX;
        xM[6] = 0.0f;
        xM[7] = -sinX;
        xM[8] = cosX;

        yM[0] = cosY;
        yM[1] = 0.0f;
        yM[2] = sinY;
        yM[3] = 0.0f;
        yM[4] = 1.0f;
        yM[5] = 0.0f;
        yM[6] = -sinY;
        yM[7] = 0.0f;
        yM[8] = cosY;

        zM[0] = cosZ;
        zM[1] = sinZ;
        zM[2] = 0.0f;
        zM[3] = -sinZ;
        zM[4] = cosZ;
        zM[5] = 0.0f;
        zM[6] = 0.0f;
        zM[7] = 0.0f;
        zM[8] = 1.0f;

        float[] resultMatrix = matrixMultiplication(xM, yM);
        resultMatrix = matrixMultiplication(zM, resultMatrix);
        return resultMatrix;
    }

    private float[] matrixMultiplication(float[] A, float[] B) {
        float[] result = new float[9];
        result[0] = A[0] * B[0] + A[1] * B[3] + A[2] * B[6];
        result[1] = A[0] * B[1] + A[1] * B[4] + A[2] * B[7];
        result[2] = A[0] * B[2] + A[1] * B[5] + A[2] * B[8];

        result[3] = A[3] * B[0] + A[4] * B[3] + A[5] * B[6];
        result[4] = A[3] * B[1] + A[4] * B[4] + A[5] * B[7];
        result[5] = A[3] * B[2] + A[4] * B[5] + A[5] * B[8];

        result[6] = A[6] * B[0] + A[7] * B[3] + A[8] * B[6];
        result[7] = A[6] * B[1] + A[7] * B[4] + A[8] * B[7];
        result[8] = A[6] * B[2] + A[7] * B[5] + A[8] * B[8];

        return result;
    }

    class calculateFusedOrientationTask extends TimerTask {
        public void run() {
            float oneMinusCoeff = 1.0f - FILTER_COEFFICIENT;
            fusedOrientation[0] = FILTER_COEFFICIENT * gyroOrientation[0] + oneMinusCoeff * accMagOrientation[0];
            fusedOrientation[1] = FILTER_COEFFICIENT * gyroOrientation[1] + oneMinusCoeff * accMagOrientation[1];
            fusedOrientation[2] = FILTER_COEFFICIENT * gyroOrientation[2] + oneMinusCoeff * accMagOrientation[2];

            double SMV = Math.sqrt(accel[0] * accel[0] + accel[1] * accel[1] + accel[2] * accel[2]);
            if (SMV > 25) {
                float degreeFloat = (float) (fusedOrientation[1] * 180 / Math.PI);
                float degreeFloat2 = (float) (fusedOrientation[2] * 180 / Math.PI);
                degreeFloat = Math.abs(degreeFloat);
                degreeFloat2 = Math.abs(degreeFloat2);

                if (degreeFloat > 30 || degreeFloat2 > 30) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            vibrator.vibrate(2000);
                            if(smsSendEnabled.equals("ON")){
                                sendFallAlert();
                            }
                        }
                    });
                }
            }

            gyroMatrix = getRotationMatrixFromOrientation(fusedOrientation);
            System.arraycopy(fusedOrientation, 0, gyroOrientation, 0, 3);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isAccelerometerSensorAvailable)
            sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);

        if (isGyroscopeSensorAvailable)
            sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (isAccelerometerSensorAvailable)
            sensorManager.unregisterListener(this, accelerometerSensor);

        if (isGyroscopeSensorAvailable)
            sensorManager.unregisterListener(this, gyroscopeSensor);
    }
}
