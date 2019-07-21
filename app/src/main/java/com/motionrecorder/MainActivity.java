package com.motionrecorder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.common.collect.EvictingQueue;
import com.google.common.primitives.Floats;
import com.jakewharton.threetenabp.AndroidThreeTen;
import com.motionrecorder.signal.ChannelType;
import com.motionrecorder.signal.SignalChannel;
import com.motionrecorder.signal.SignalContact;
import com.motionrecorder.signal.SignalDoc;
import com.motionrecorder.signal.SignalEquipment;
import com.motionrecorder.signal.SignalNetwork;
import com.motionrecorder.signal.SignalOperator;
import com.motionrecorder.signal.SignalStation;
import com.motionrecorder.signal.SignalTrace;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import org.ejml.simple.SimpleMatrix;
import org.threeten.bp.OffsetDateTime;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity
        implements OnRequestPermissionsResultCallback, SensorEventListener {

    private static final String TAG = MainActivity.class.getName();
    private static final int MY_PERMISSIONS_ACCESS_FINE_LOCATION = 1;
    private SensorManager sensorManager;
    private Sensor linAccelSensor;
    private int targetSamplingRate = 50;
    private int locationSamplingRate = 4;
    private int clipDurationSecs = 2;
    private EvictingQueue<Float> linAccelX;
    private EvictingQueue<Float> linAccelY;
    private EvictingQueue<Float> linAccelZ;
    //    private int linAccelStart = 0;
//    private int linAccelCur = 0;
//    private int linAccelCount = 0;
    private Timer flushBufferTimer;
    private Sensor gravitySensor;
    private Sensor gyroscopeSensor;
    private Sensor geomagneticSensor;
    private EvictingQueue<Float> gravityX;
    private EvictingQueue<Float> gravityY;
    private EvictingQueue<Float> gravityZ;
    private EvictingQueue<Float> gyroX;
    private EvictingQueue<Float> gyroY;
    private EvictingQueue<Float> gyroZ;
    private EvictingQueue<Float> geomagneticX;
    private EvictingQueue<Float> geomagneticY;
    private EvictingQueue<Float> geomagneticZ;
    private EvictingQueue<Float> latBuf;
    private EvictingQueue<Float> lonBuf;
    private EvictingQueue<Float> altBuf;
    private FusedLocationProviderClient fusedLocationClient;
    private Moshi moshi;
    private final String seedNetworkCode = "XX";
    private OffsetDateTime startTime;
    private LocationCallback locationCallback;
    private LocationManager locationManager;
    private Location lastLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidThreeTen.init(this);

        setContentView(R.layout.activity_main);

//        ReLinker.loadLibrary(this, "openblas");
//        ReLinker.loadLibrary(this, "jniopenblas");
//        ReLinker.loadLibrary(this, "jniopenblas_nolapack");
//        ReLinker.loadLibrary(this, "nd4jcpu");
//        ReLinker.loadLibrary(this, "jnind4jcpu");

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission granted");
            preparePrivilegedObjects();
        } else {
            // Show rationale and request permission.
            // No explanation needed; request the permission
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_ACCESS_FINE_LOCATION);

            // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
            // app-defined int constant. The callback method gets the
            // result of the request.
        }

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        geomagneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        linAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        gravityX = EvictingQueue.<Float>create(targetSamplingRate * clipDurationSecs * 2);
        gravityY = EvictingQueue.<Float>create(targetSamplingRate * clipDurationSecs * 2);
        gravityZ = EvictingQueue.<Float>create(targetSamplingRate * clipDurationSecs * 2);
        geomagneticX = EvictingQueue.<Float>create(targetSamplingRate * clipDurationSecs * 2);
        geomagneticY = EvictingQueue.<Float>create(targetSamplingRate * clipDurationSecs * 2);
        geomagneticZ = EvictingQueue.<Float>create(targetSamplingRate * clipDurationSecs * 2);
//        linAccelX = Nd4j.zeros(DataType.FLOAT, targetSamplingRate * 2); //new float[targetSamplingRate * 2];
//        linAccelY = Nd4j.zeros(DataType.FLOAT, targetSamplingRate * 2); // new float[targetSamplingRate * 2];
//        linAccelZ = Nd4j.zeros(DataType.FLOAT, targetSamplingRate * 2); // new float[targetSamplingRate * 2];
//        linAccelX = new float[targetSamplingRate * 2];
//        linAccelY = new float[targetSamplingRate * 2];
//        linAccelZ = new float[targetSamplingRate * 2];
        linAccelX = EvictingQueue.<Float>create(targetSamplingRate * clipDurationSecs * 2);
        linAccelY = EvictingQueue.<Float>create(targetSamplingRate * clipDurationSecs * 2);
        linAccelZ = EvictingQueue.<Float>create(targetSamplingRate * clipDurationSecs * 2);
        gyroX = EvictingQueue.<Float>create(targetSamplingRate * clipDurationSecs * 2);
        gyroY = EvictingQueue.<Float>create(targetSamplingRate * clipDurationSecs * 2);
        gyroZ = EvictingQueue.<Float>create(targetSamplingRate * clipDurationSecs * 2);
        latBuf = EvictingQueue.<Float>create(locationSamplingRate * clipDurationSecs * 2);
        lonBuf = EvictingQueue.<Float>create(locationSamplingRate * clipDurationSecs * 2);
        altBuf = EvictingQueue.<Float>create(locationSamplingRate * clipDurationSecs * 2);

        moshi = new Moshi.Builder().build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                final Location location = locationResult.getLastLocation();
                if (location != null) {
                    latBuf.add((float) location.getLatitude());
                    lonBuf.add((float) location.getLongitude());
                    altBuf.add((float) location.getAltitude());
                }
            }

            @Override
            public void onLocationAvailability(LocationAvailability locationAvailability) {
                super.onLocationAvailability(locationAvailability);
            }
        };
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // permission was granted, yay!
            Log.i(TAG, "Permission granted");
            preparePrivilegedObjects();
        } else {
            // permission denied, boo! Disable the
            // functionality that depends on this permission.
            Log.w(TAG, "Permission denied");
            alertPermissionRequired();
        }
    }

    private void alertPermissionRequired() {
        new AlertDialog.Builder(this)
                .setTitle("Permission denied")
                .setMessage("Please allow permission")
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .show();
    }

    /**
     * Prepare objects that require user permissions, after permissions have been granted.
     */
    private void preparePrivilegedObjects() {
        if (fusedLocationClient == null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        }
        if (locationManager == null) {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        }
    }

    @SuppressLint("MissingPermission")
    private void createSignalDocAndReset() {
        final OffsetDateTime endTime = startTime.plusSeconds(clipDurationSecs);
//                    Log.i(TAG, String.format("copy buffer %d ms. linAccelCount=%d, linAccelStart=%d",
//                            clipDurationSecs, linAccelCount, linAccelStart));
        Log.i(TAG, String.format("copy buffer %d s. gravity.size=%d, geomagnetic.size=%d, linAccel.size=%d, gyro.size=%d.",
                clipDurationSecs, gravityX.size(), geomagneticX.size(), linAccelX.size(), gyroX.size()));
        final int sampleCount = clipDurationSecs * targetSamplingRate;

        final float[] gravityXClip = SignalUtils.upsample(Floats.toArray(gravityX), sampleCount);
        gravityX.clear();
        final float[] gravityYClip = SignalUtils.upsample(Floats.toArray(gravityY), sampleCount);
        gravityY.clear();
        final float[] gravityZClip = SignalUtils.upsample(Floats.toArray(gravityZ), sampleCount);
        gravityZ.clear();
        final float[] geomagneticXClip = SignalUtils.upsample(Floats.toArray(geomagneticX), sampleCount);
        geomagneticX.clear();
        final float[] geomagneticYClip = SignalUtils.upsample(Floats.toArray(geomagneticY), sampleCount);
        geomagneticY.clear();
        final float[] geomagneticZClip = SignalUtils.upsample(Floats.toArray(geomagneticZ), sampleCount);
        geomagneticZ.clear();
        final float[] linAccelXClip = SignalUtils.upsample(Floats.toArray(linAccelX), sampleCount);
        linAccelX.clear();
        final float[] linAccelYClip = SignalUtils.upsample(Floats.toArray(linAccelY), sampleCount);
        linAccelY.clear();
        final float[] linAccelZClip = SignalUtils.upsample(Floats.toArray(linAccelZ), sampleCount);
        linAccelZ.clear();
        final float[] gyroXClip = SignalUtils.upsample(Floats.toArray(gyroX), sampleCount);
        gyroX.clear();
        final float[] gyroYClip = SignalUtils.upsample(Floats.toArray(gyroY), sampleCount);
        gyroY.clear();
        final float[] gyroZClip = SignalUtils.upsample(Floats.toArray(gyroZ), sampleCount);
        gyroZ.clear();

        Log.i(TAG, String.format("gravityXClip=%s", Floats.join(" ", gravityXClip)));
        Log.i(TAG, String.format("geomagneticXClip=%s", Floats.join(" ", geomagneticXClip)));
        Log.i(TAG, String.format("linAccelXClip=%s", Floats.join(" ", linAccelXClip)));
        Log.i(TAG, String.format("gyroXClip=%s", Floats.join(" ", gyroXClip)));

        // rotate to ZNE
        float[] rotMat = new float[16];
        float[] incMat = new float[16];
        final float[] linAccelWorldZ = new float[sampleCount];
        final float[] linAccelWorldN = new float[sampleCount];
        final float[] linAccelWorldE = new float[sampleCount];
        final float[] gyroWorldZ = new float[sampleCount];
        final float[] gyroWorldN = new float[sampleCount];
        final float[] gyroWorldE = new float[sampleCount];
        final SimpleMatrix[] incMatrices = new SimpleMatrix[sampleCount];
        final SimpleMatrix[] rotMatrices = new SimpleMatrix[sampleCount];
        for (int sampleIdx = 0; sampleIdx < sampleCount; sampleIdx++) {
            SensorManager.getRotationMatrix(rotMat, incMat,
                    new float[]{gravityXClip[sampleIdx], gravityYClip[sampleIdx], gravityZClip[sampleIdx]},
                    new float[]{geomagneticXClip[sampleIdx], geomagneticYClip[sampleIdx], geomagneticZClip[sampleIdx]});
            incMatrices[sampleIdx] = new SimpleMatrix(4, 4, true, incMat);
            rotMatrices[sampleIdx] = new SimpleMatrix(4, 4, true, rotMat);
            final SimpleMatrix linAccelRot = rotMatrices[sampleIdx].mult(new SimpleMatrix(4, 1, true,
                    new float[]{linAccelXClip[sampleIdx], linAccelYClip[sampleIdx], linAccelZClip[sampleIdx], 1}));
            linAccelWorldE[sampleIdx] = (float) linAccelRot.get(0);
            linAccelWorldN[sampleIdx] = (float) linAccelRot.get(1);
            linAccelWorldZ[sampleIdx] = (float) linAccelRot.get(2);
            final SimpleMatrix gyroRot = rotMatrices[sampleIdx].mult(new SimpleMatrix(4, 1, true,
                    new float[]{gyroXClip[sampleIdx], gyroYClip[sampleIdx], gyroZClip[sampleIdx], 1}));
            gyroWorldE[sampleIdx] = (float) gyroRot.get(0);
            gyroWorldN[sampleIdx] = (float) gyroRot.get(1);
            gyroWorldZ[sampleIdx] = (float) gyroRot.get(2);
        }
        Log.i(TAG, String.format("linAccelWorldZ=%s", Floats.join(" ", linAccelWorldZ)));
        Log.i(TAG, String.format("linAccelWorldN=%s", Floats.join(" ", linAccelWorldN)));
        Log.i(TAG, String.format("linAccelWorldE=%s", Floats.join(" ", linAccelWorldE)));

        fusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location == null) {
                    Log.e(TAG, "FusedLocationProviderClient lastLocation is null");
                    throw new RuntimeException("FusedLocationProviderClient lastLocation is null");
                }

                final int locationSampleCount = clipDurationSecs * locationSamplingRate;
                if (latBuf.isEmpty()) {
                    // we don't get any location updates yet, so get it from lastLocation instead
                    latBuf.add((float) location.getLatitude());
                    lonBuf.add((float) location.getLongitude());
                    altBuf.add((float) location.getAltitude());
                }
                final float[] latBufArr = Floats.toArray(latBuf);
                Log.i(TAG, String.format("latBuf.size=%d latBuf=%s", latBufArr.length, Floats.join(" ", latBufArr)));
                final float[] latClip = SignalUtils.upsample(latBufArr, locationSampleCount);
                latBuf.clear();
                final float[] lonClip = SignalUtils.upsample(Floats.toArray(lonBuf), locationSampleCount);
                lonBuf.clear();
                final float[] altitudeClip = SignalUtils.upsample(Floats.toArray(altBuf), locationSampleCount);
                altBuf.clear();
                Log.i(TAG, String.format("latClip=%s lonClip=%s eleClip-%s",
                        Floats.join(" ", latClip), Floats.join(" ", lonClip), Floats.join(" ", altitudeClip)));

                final SignalDoc doc = new SignalDoc();
                doc.source = "Motion Recorder";
                final SignalNetwork network = new SignalNetwork();
                network.code = seedNetworkCode;
                final SignalStation station = new SignalStation();
                station.code = Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
                station.latitude = (float) location.getLatitude();
                station.longitude = (float) location.getLongitude();
                station.elevation = (float) location.getAltitude();
                // Device: brand/model/deviceCountry, System: systemName/systemVersion
                final SignalEquipment equipment = new SignalEquipment();
                equipment.type = Build.HARDWARE;
                equipment.manufacturer = Build.MANUFACTURER;
                equipment.vendor = Build.BRAND;
                equipment.model = Build.MODEL;
                equipment.serialNumber = Build.FINGERPRINT;
                equipment.description = Build.VERSION.CODENAME + "/" + Build.VERSION.RELEASE;
                station.equipment = equipment;
                final SignalOperator operator = new SignalOperator();
                final SignalContact contact = new SignalContact();
                contact.name = "TODO";
                operator.contacts.add(contact);
                station.operator = operator;

                // Gravity
                final SignalEquipment sensorSG = new SignalEquipment();
                sensorSG.type = gravitySensor.getStringType();
                sensorSG.vendor = gravitySensor.getVendor();
                sensorSG.model = gravitySensor.getName();
                final SignalChannel channelSGA = new SignalChannel();
                channelSGA.code = "SGA";
                channelSGA.description = "Gravity (X)";
                channelSGA.locationCode = "00";
                channelSGA.types.add(ChannelType.GEOPHYSICAL);
                channelSGA.types.add(ChannelType.TRIGGERED);
                channelSGA.latitude = station.latitude;
                channelSGA.longitude = station.longitude;
                channelSGA.elevation = station.elevation;
                channelSGA.sampleRate = targetSamplingRate;
                channelSGA.startDate = startTime.toString();
                channelSGA.endDate = endTime.toString();
                channelSGA.sensor = sensorSG;
                station.channels.add(channelSGA);
                final SignalChannel channelSGB = new SignalChannel();
                channelSGB.code = "SGB";
                channelSGB.description = "Gravity (Y)";
                channelSGB.locationCode = "00";
                channelSGB.types.add(ChannelType.GEOPHYSICAL);
                channelSGB.types.add(ChannelType.TRIGGERED);
                channelSGB.latitude = station.latitude;
                channelSGB.longitude = station.longitude;
                channelSGB.elevation = station.elevation;
                channelSGB.sampleRate = targetSamplingRate;
                channelSGB.startDate = startTime.toString();
                channelSGB.endDate = endTime.toString();
                channelSGA.sensor = sensorSG;
                station.channels.add(channelSGB);
                final SignalChannel channelSGC = new SignalChannel();
                channelSGC.code = "SGC";
                channelSGC.description = "Gravity (Z)";
                channelSGC.locationCode = "00";
                channelSGC.types.add(ChannelType.GEOPHYSICAL);
                channelSGC.types.add(ChannelType.TRIGGERED);
                channelSGC.latitude = station.latitude;
                channelSGC.longitude = station.longitude;
                channelSGC.elevation = station.elevation;
                channelSGC.sampleRate = targetSamplingRate;
                channelSGC.startDate = startTime.toString();
                channelSGC.endDate = endTime.toString();
                channelSGA.sensor = sensorSG;
                station.channels.add(channelSGC);
                // Traces - gravity
                final SignalTrace traceSGA = new SignalTrace();
                traceSGA.network = network.code;
                traceSGA.station = station.code;
                traceSGA.location = channelSGA.locationCode;
                traceSGA.channel = channelSGA.code;
                traceSGA.startTime = channelSGA.startDate;
                traceSGA.endTime = channelSGA.endDate;
                traceSGA.samplingRate = channelSGA.sampleRate;
                traceSGA.delta = 1f / traceSGA.samplingRate;
                traceSGA.values = gravityXClip;
                traceSGA.calib = 1f;
                traceSGA.npts = traceSGA.values.length;
                doc.traces.add(traceSGA);
                final SignalTrace traceSGB = new SignalTrace();
                traceSGB.network = network.code;
                traceSGB.station = station.code;
                traceSGB.location = channelSGB.locationCode;
                traceSGB.channel = channelSGB.code;
                traceSGB.startTime = channelSGB.startDate;
                traceSGB.endTime = channelSGB.endDate;
                traceSGB.samplingRate = channelSGB.sampleRate;
                traceSGB.delta = 1f / traceSGB.samplingRate;
                traceSGB.values = gravityYClip;
                traceSGB.calib = 1f;
                traceSGB.npts = traceSGB.values.length;
                doc.traces.add(traceSGB);
                final SignalTrace traceSGC = new SignalTrace();
                traceSGC.network = network.code;
                traceSGC.station = station.code;
                traceSGC.location = channelSGC.locationCode;
                traceSGC.channel = channelSGC.code;
                traceSGC.startTime = channelSGC.startDate;
                traceSGC.endTime = channelSGC.endDate;
                traceSGC.samplingRate = channelSGC.sampleRate;
                traceSGC.delta = 1f / traceSGC.samplingRate;
                traceSGC.values = gravityZClip;
                traceSGC.calib = 1f;
                traceSGC.npts = traceSGC.values.length;
                doc.traces.add(traceSGC);

                // Geomagnetic
                final SignalEquipment sensorSF = new SignalEquipment();
                sensorSF.type = geomagneticSensor.getStringType();
                sensorSF.vendor = geomagneticSensor.getVendor();
                sensorSF.model = geomagneticSensor.getName();
                final SignalChannel channelSFA = new SignalChannel();
                channelSFA.code = "SFA";
                channelSFA.description = "Geomagnetic field (X)";
                channelSFA.locationCode = "00";
                channelSFA.types.add(ChannelType.GEOPHYSICAL);
                channelSFA.types.add(ChannelType.TRIGGERED);
                channelSFA.latitude = station.latitude;
                channelSFA.longitude = station.longitude;
                channelSFA.elevation = station.elevation;
                channelSFA.sampleRate = targetSamplingRate;
                channelSFA.startDate = startTime.toString();
                channelSFA.endDate = endTime.toString();
                channelSFA.sensor = sensorSF;
                station.channels.add(channelSFA);
                final SignalChannel channelSFB = new SignalChannel();
                channelSFB.code = "SFB";
                channelSFB.description = "Geomagnetic field (Y)";
                channelSFB.locationCode = "00";
                channelSFB.types.add(ChannelType.GEOPHYSICAL);
                channelSFB.types.add(ChannelType.TRIGGERED);
                channelSFB.latitude = station.latitude;
                channelSFB.longitude = station.longitude;
                channelSFB.elevation = station.elevation;
                channelSFB.sampleRate = targetSamplingRate;
                channelSFB.startDate = startTime.toString();
                channelSFB.endDate = endTime.toString();
                channelSFB.sensor = sensorSF;
                station.channels.add(channelSFB);
                final SignalChannel channelSFC = new SignalChannel();
                channelSFC.code = "SFC";
                channelSFC.description = "Geomagnetic field (Z)";
                channelSFC.locationCode = "00";
                channelSFC.types.add(ChannelType.GEOPHYSICAL);
                channelSFC.types.add(ChannelType.TRIGGERED);
                channelSFC.latitude = station.latitude;
                channelSFC.longitude = station.longitude;
                channelSFC.elevation = station.elevation;
                channelSFC.sampleRate = targetSamplingRate;
                channelSFC.startDate = startTime.toString();
                channelSFC.endDate = endTime.toString();
                channelSFC.sensor = sensorSF;
                station.channels.add(channelSFC);
                // Traces - geomagnetic
                final SignalTrace traceSFA = new SignalTrace();
                traceSFA.network = network.code;
                traceSFA.station = station.code;
                traceSFA.location = channelSFA.locationCode;
                traceSFA.channel = channelSFA.code;
                traceSFA.startTime = channelSFA.startDate;
                traceSFA.endTime = channelSFA.endDate;
                traceSFA.samplingRate = channelSFA.sampleRate;
                traceSFA.delta = 1f / traceSFA.samplingRate;
                traceSFA.values = geomagneticZClip;
                traceSFA.calib = 1f;
                traceSFA.npts = traceSFA.values.length;
                doc.traces.add(traceSFA);
                final SignalTrace traceSFB = new SignalTrace();
                traceSFB.network = network.code;
                traceSFB.station = station.code;
                traceSFB.location = channelSFB.locationCode;
                traceSFB.channel = channelSFB.code;
                traceSFB.startTime = channelSFB.startDate;
                traceSFB.endTime = channelSFB.endDate;
                traceSFB.samplingRate = channelSFB.sampleRate;
                traceSFB.delta = 1f / traceSFB.samplingRate;
                traceSFB.values = geomagneticZClip;
                traceSFB.calib = 1f;
                traceSFB.npts = traceSFB.values.length;
                doc.traces.add(traceSFB);
                final SignalTrace traceSFC = new SignalTrace();
                traceSFC.network = network.code;
                traceSFC.station = station.code;
                traceSFC.location = channelSFC.locationCode;
                traceSFC.channel = channelSFC.code;
                traceSFC.startTime = channelSFC.startDate;
                traceSFC.endTime = channelSFC.endDate;
                traceSFC.samplingRate = channelSFC.sampleRate;
                traceSFC.delta = 1f / traceSFC.samplingRate;
                traceSFC.values = geomagneticZClip;
                traceSFC.calib = 1f;
                traceSFC.npts = traceSFC.values.length;
                doc.traces.add(traceSFC);

                // Linear acceleration: SNA, SNB, SNC, SNZ, SNN, SNE
                final SignalEquipment sensorSNLinear = new SignalEquipment();
                sensorSNLinear.type = linAccelSensor.getStringType();
                sensorSNLinear.vendor = linAccelSensor.getVendor();
                sensorSNLinear.model = linAccelSensor.getName();
                final SignalChannel channelSNA = new SignalChannel();
                channelSNA.code = "SNA";
                channelSNA.description = "Linear acceleration (X)";
                channelSNA.locationCode = "00";
                channelSNA.types.add(ChannelType.GEOPHYSICAL);
                channelSNA.types.add(ChannelType.TRIGGERED);
                channelSNA.latitude = station.latitude;
                channelSNA.longitude = station.longitude;
                channelSNA.elevation = station.elevation;
                channelSNA.sampleRate = targetSamplingRate;
                channelSNA.startDate = startTime.toString();
                channelSNA.endDate = endTime.toString();
                channelSNA.sensor = sensorSNLinear;
                station.channels.add(channelSNA);
                final SignalChannel channelSNB = new SignalChannel();
                channelSNB.code = "SNB";
                channelSNB.description = "Linear acceleration (Y)";
                channelSNB.locationCode = "00";
                channelSNB.types.add(ChannelType.GEOPHYSICAL);
                channelSNB.types.add(ChannelType.TRIGGERED);
                channelSNB.latitude = station.latitude;
                channelSNB.longitude = station.longitude;
                channelSNB.elevation = station.elevation;
                channelSNB.sampleRate = targetSamplingRate;
                channelSNB.startDate = startTime.toString();
                channelSNB.endDate = endTime.toString();
                channelSNB.sensor = sensorSNLinear;
                station.channels.add(channelSNB);
                final SignalChannel channelSNC = new SignalChannel();
                channelSNC.code = "SNC";
                channelSNC.description = "Linear acceleration (Z)";
                channelSNC.locationCode = "00";
                channelSNC.types.add(ChannelType.GEOPHYSICAL);
                channelSNC.types.add(ChannelType.TRIGGERED);
                channelSNC.latitude = station.latitude;
                channelSNC.longitude = station.longitude;
                channelSNC.elevation = station.elevation;
                channelSNC.sampleRate = targetSamplingRate;
                channelSNC.startDate = startTime.toString();
                channelSNC.endDate = endTime.toString();
                channelSNC.sensor = sensorSNLinear;
                station.channels.add(channelSNC);
                final SignalChannel channelSNZ = new SignalChannel();
                channelSNZ.code = "SNZ";
                channelSNZ.description = "Linear acceleration (Vertical)";
                channelSNZ.locationCode = "00";
                channelSNZ.types.add(ChannelType.GEOPHYSICAL);
                channelSNZ.types.add(ChannelType.TRIGGERED);
                channelSNZ.types.add(ChannelType.SYNTHESIZED);
                channelSNZ.latitude = station.latitude;
                channelSNZ.longitude = station.longitude;
                channelSNZ.elevation = station.elevation;
                channelSNZ.sampleRate = targetSamplingRate;
                channelSNZ.startDate = startTime.toString();
                channelSNZ.endDate = endTime.toString();
                channelSNZ.sensor = sensorSNLinear;
                station.channels.add(channelSNZ);
                final SignalChannel channelSNN = new SignalChannel();
                channelSNN.code = "SNN";
                channelSNN.description = "Linear acceleration (North-South)";
                channelSNN.locationCode = "00";
                channelSNN.types.add(ChannelType.GEOPHYSICAL);
                channelSNN.types.add(ChannelType.TRIGGERED);
                channelSNN.types.add(ChannelType.SYNTHESIZED);
                channelSNN.latitude = station.latitude;
                channelSNN.longitude = station.longitude;
                channelSNN.elevation = station.elevation;
                channelSNN.sampleRate = targetSamplingRate;
                channelSNN.startDate = startTime.toString();
                channelSNN.endDate = endTime.toString();
                channelSNN.sensor = sensorSNLinear;
                station.channels.add(channelSNN);
                final SignalChannel channelSNE = new SignalChannel();
                channelSNE.code = "SNE";
                channelSNE.description = "Linear acceleration (East-West)";
                channelSNE.locationCode = "00";
                channelSNE.types.add(ChannelType.GEOPHYSICAL);
                channelSNE.types.add(ChannelType.TRIGGERED);
                channelSNE.types.add(ChannelType.SYNTHESIZED);
                channelSNE.latitude = station.latitude;
                channelSNE.longitude = station.longitude;
                channelSNE.elevation = station.elevation;
                channelSNE.sampleRate = targetSamplingRate;
                channelSNE.startDate = startTime.toString();
                channelSNE.endDate = endTime.toString();
                channelSNE.sensor = sensorSNLinear;
                station.channels.add(channelSNE);
                // Traces - linear acceleration
                final SignalTrace traceSNA = new SignalTrace();
                traceSNA.network = network.code;
                traceSNA.station = station.code;
                traceSNA.location = channelSNA.locationCode;
                traceSNA.channel = channelSNA.code;
                traceSNA.startTime = channelSNA.startDate;
                traceSNA.endTime = channelSNA.endDate;
                traceSNA.samplingRate = channelSNA.sampleRate;
                traceSNA.delta = 1f / traceSNA.samplingRate;
                traceSNA.values = linAccelXClip;
                traceSNA.calib = 1f;
                traceSNA.npts = traceSNA.values.length;
                doc.traces.add(traceSNA);
                final SignalTrace traceSNB = new SignalTrace();
                traceSNB.network = network.code;
                traceSNB.station = station.code;
                traceSNB.location = channelSNB.locationCode;
                traceSNB.channel = channelSNB.code;
                traceSNB.startTime = channelSNB.startDate;
                traceSNB.endTime = channelSNB.endDate;
                traceSNB.samplingRate = channelSNB.sampleRate;
                traceSNB.delta = 1f / traceSNB.samplingRate;
                traceSNB.values = linAccelYClip;
                traceSNB.calib = 1f;
                traceSNB.npts = traceSNB.values.length;
                doc.traces.add(traceSNB);
                final SignalTrace traceSNC = new SignalTrace();
                traceSNC.network = network.code;
                traceSNC.station = station.code;
                traceSNC.location = channelSNC.locationCode;
                traceSNC.channel = channelSNC.code;
                traceSNC.startTime = channelSNC.startDate;
                traceSNC.endTime = channelSNC.endDate;
                traceSNC.samplingRate = channelSNC.sampleRate;
                traceSNC.delta = 1f / traceSNC.samplingRate;
                traceSNC.values = linAccelZClip;
                traceSNC.calib = 1f;
                traceSNC.npts = traceSNC.values.length;
                doc.traces.add(traceSNC);
                final SignalTrace traceSNZ = new SignalTrace();
                traceSNZ.network = network.code;
                traceSNZ.station = station.code;
                traceSNZ.location = channelSNZ.locationCode;
                traceSNZ.channel = channelSNZ.code;
                traceSNZ.startTime = channelSNZ.startDate;
                traceSNZ.endTime = channelSNZ.endDate;
                traceSNZ.samplingRate = channelSNZ.sampleRate;
                traceSNZ.delta = 1f / traceSNZ.samplingRate;
                traceSNZ.values = linAccelWorldZ;
                traceSNZ.calib = 1f;
                traceSNZ.npts = traceSNZ.values.length;
                doc.traces.add(traceSNZ);
                final SignalTrace traceSNN = new SignalTrace();
                traceSNN.network = network.code;
                traceSNN.station = station.code;
                traceSNN.location = channelSNN.locationCode;
                traceSNN.channel = channelSNN.code;
                traceSNN.startTime = channelSNN.startDate;
                traceSNN.endTime = channelSNN.endDate;
                traceSNN.samplingRate = channelSNN.sampleRate;
                traceSNN.delta = 1f / traceSNN.samplingRate;
                traceSNN.values = linAccelWorldN;
                traceSNN.calib = 1f;
                traceSNN.npts = traceSNN.values.length;
                doc.traces.add(traceSNN);
                final SignalTrace traceSNE = new SignalTrace();
                traceSNE.network = network.code;
                traceSNE.station = station.code;
                traceSNE.location = channelSNE.locationCode;
                traceSNE.channel = channelSNE.code;
                traceSNE.startTime = channelSNE.startDate;
                traceSNE.endTime = channelSNE.endDate;
                traceSNE.samplingRate = channelSNE.sampleRate;
                traceSNE.delta = 1f / traceSNE.samplingRate;
                traceSNE.values = linAccelWorldE;
                traceSNE.calib = 1f;
                traceSNE.npts = traceSNE.values.length;
                doc.traces.add(traceSNE);

                // Gyroscope
                final SignalEquipment sensorSJ = new SignalEquipment();
                sensorSJ.type = gyroscopeSensor.getStringType();
                sensorSJ.vendor = gyroscopeSensor.getVendor();
                sensorSJ.model = gyroscopeSensor.getName();
                final SignalChannel channelSJA = new SignalChannel();
                channelSJA.code = "SJA";
                channelSJA.description = "Rotation rate (X)";
                channelSJA.locationCode = "00";
                channelSJA.types.add(ChannelType.GEOPHYSICAL);
                channelSJA.types.add(ChannelType.TRIGGERED);
                channelSJA.latitude = station.latitude;
                channelSJA.longitude = station.longitude;
                channelSJA.elevation = station.elevation;
                channelSJA.sampleRate = targetSamplingRate;
                channelSJA.startDate = startTime.toString();
                channelSJA.endDate = endTime.toString();
                channelSJA.sensor = sensorSJ;
                station.channels.add(channelSJA);
                final SignalChannel channelSJB = new SignalChannel();
                channelSJB.code = "SJB";
                channelSJB.description = "Rotation rate (Y)";
                channelSJB.locationCode = "00";
                channelSJB.types.add(ChannelType.GEOPHYSICAL);
                channelSJB.types.add(ChannelType.TRIGGERED);
                channelSJB.latitude = station.latitude;
                channelSJB.longitude = station.longitude;
                channelSJB.elevation = station.elevation;
                channelSJB.sampleRate = targetSamplingRate;
                channelSJB.startDate = startTime.toString();
                channelSJB.endDate = endTime.toString();
                channelSJB.sensor = sensorSJ;
                station.channels.add(channelSJB);
                final SignalChannel channelSJC = new SignalChannel();
                channelSJC.code = "SJC";
                channelSJC.description = "Rotation rate (Z)";
                channelSJC.locationCode = "00";
                channelSJC.types.add(ChannelType.GEOPHYSICAL);
                channelSJC.types.add(ChannelType.TRIGGERED);
                channelSJC.latitude = station.latitude;
                channelSJC.longitude = station.longitude;
                channelSJC.elevation = station.elevation;
                channelSJC.sampleRate = targetSamplingRate;
                channelSJC.startDate = startTime.toString();
                channelSJC.endDate = endTime.toString();
                channelSJC.sensor = sensorSJ;
                station.channels.add(channelSJC);
                final SignalChannel channelSJZ = new SignalChannel();
                channelSJZ.code = "SJZ";
                channelSJZ.description = "Rotation rate (Vertical)";
                channelSJZ.locationCode = "00";
                channelSJZ.types.add(ChannelType.GEOPHYSICAL);
                channelSJZ.types.add(ChannelType.TRIGGERED);
                channelSJZ.types.add(ChannelType.SYNTHESIZED);
                channelSJZ.latitude = station.latitude;
                channelSJZ.longitude = station.longitude;
                channelSJZ.elevation = station.elevation;
                channelSJZ.sampleRate = targetSamplingRate;
                channelSJZ.startDate = startTime.toString();
                channelSJZ.endDate = endTime.toString();
                channelSJZ.sensor = sensorSJ;
                station.channels.add(channelSJZ);
                final SignalChannel channelSJN = new SignalChannel();
                channelSJN.code = "SJN";
                channelSJN.description = "Rotation rate (North-South)";
                channelSJN.locationCode = "00";
                channelSJN.types.add(ChannelType.GEOPHYSICAL);
                channelSJN.types.add(ChannelType.TRIGGERED);
                channelSJN.types.add(ChannelType.SYNTHESIZED);
                channelSJN.latitude = station.latitude;
                channelSJN.longitude = station.longitude;
                channelSJN.elevation = station.elevation;
                channelSJN.sampleRate = targetSamplingRate;
                channelSJN.startDate = startTime.toString();
                channelSJN.endDate = endTime.toString();
                channelSJN.sensor = sensorSJ;
                station.channels.add(channelSJN);
                final SignalChannel channelSJE = new SignalChannel();
                channelSJE.code = "SJE";
                channelSJE.description = "Rotation rate (East-West)";
                channelSJE.locationCode = "00";
                channelSJE.types.add(ChannelType.GEOPHYSICAL);
                channelSJE.types.add(ChannelType.TRIGGERED);
                channelSJE.types.add(ChannelType.SYNTHESIZED);
                channelSJE.latitude = station.latitude;
                channelSJE.longitude = station.longitude;
                channelSJE.elevation = station.elevation;
                channelSJE.sampleRate = targetSamplingRate;
                channelSJE.startDate = startTime.toString();
                channelSJE.endDate = endTime.toString();
                channelSJE.sensor = sensorSJ;
                station.channels.add(channelSJE);
                // Traces - gyroscope/rotation rate
                final SignalTrace traceSJA = new SignalTrace();
                traceSJA.network = network.code;
                traceSJA.station = station.code;
                traceSJA.location = channelSJA.locationCode;
                traceSJA.channel = channelSJA.code;
                traceSJA.startTime = channelSJA.startDate;
                traceSJA.endTime = channelSJA.endDate;
                traceSJA.samplingRate = channelSJA.sampleRate;
                traceSJA.delta = 1f / traceSJA.samplingRate;
                traceSJA.values = gyroXClip;
                traceSJA.calib = 1f;
                traceSJA.npts = traceSJA.values.length;
                doc.traces.add(traceSJA);
                final SignalTrace traceSJB = new SignalTrace();
                traceSJB.network = network.code;
                traceSJB.station = station.code;
                traceSJB.location = channelSJB.locationCode;
                traceSJB.channel = channelSJB.code;
                traceSJB.startTime = channelSJB.startDate;
                traceSJB.endTime = channelSJB.endDate;
                traceSJB.samplingRate = channelSJB.sampleRate;
                traceSJB.delta = 1f / traceSJB.samplingRate;
                traceSJB.values = gyroYClip;
                traceSJB.calib = 1f;
                traceSJB.npts = traceSJB.values.length;
                doc.traces.add(traceSJB);
                final SignalTrace traceSJC = new SignalTrace();
                traceSJC.network = network.code;
                traceSJC.station = station.code;
                traceSJC.location = channelSJC.locationCode;
                traceSJC.channel = channelSJC.code;
                traceSJC.startTime = channelSJC.startDate;
                traceSJC.endTime = channelSJC.endDate;
                traceSJC.samplingRate = channelSJC.sampleRate;
                traceSJC.delta = 1f / traceSJC.samplingRate;
                traceSJC.values = gyroZClip;
                traceSJC.calib = 1f;
                traceSJC.npts = traceSJC.values.length;
                doc.traces.add(traceSJC);
                final SignalTrace traceSJZ = new SignalTrace();
                traceSJZ.network = network.code;
                traceSJZ.station = station.code;
                traceSJZ.location = channelSJZ.locationCode;
                traceSJZ.channel = channelSJZ.code;
                traceSJZ.startTime = channelSJZ.startDate;
                traceSJZ.endTime = channelSJZ.endDate;
                traceSJZ.samplingRate = channelSJZ.sampleRate;
                traceSJZ.delta = 1f / traceSJZ.samplingRate;
                traceSJZ.values = gyroWorldZ;
                traceSJZ.calib = 1f;
                traceSJZ.npts = traceSJZ.values.length;
                doc.traces.add(traceSJZ);
                final SignalTrace traceSJN = new SignalTrace();
                traceSJN.network = network.code;
                traceSJN.station = station.code;
                traceSJN.location = channelSJN.locationCode;
                traceSJN.channel = channelSJN.code;
                traceSJN.startTime = channelSJN.startDate;
                traceSJN.endTime = channelSJN.endDate;
                traceSJN.samplingRate = channelSJN.sampleRate;
                traceSJN.delta = 1f / traceSJN.samplingRate;
                traceSJN.values = gyroWorldN;
                traceSJN.calib = 1f;
                traceSJN.npts = traceSJN.values.length;
                doc.traces.add(traceSJN);
                final SignalTrace traceSJE = new SignalTrace();
                traceSJE.network = network.code;
                traceSJE.station = station.code;
                traceSJE.location = channelSJE.locationCode;
                traceSJE.channel = channelSJE.code;
                traceSJE.startTime = channelSJE.startDate;
                traceSJE.endTime = channelSJE.endDate;
                traceSJE.samplingRate = channelSJE.sampleRate;
                traceSJE.delta = 1f / traceSJE.samplingRate;
                traceSJE.values = gyroWorldE;
                traceSJE.calib = 1f;
                traceSJE.npts = traceSJE.values.length;
                doc.traces.add(traceSJE);

                // Inclination matrix, Rotation matrix
                String[] matrixCodes = new String[] { "0", "1", "2", "4", "5", "6", "8", "9", "A" };
                // Inclination matrix
                for (int chanIdx = 0; chanIdx < matrixCodes.length; chanIdx++) {
                    final SignalChannel channelIM_SX = new SignalChannel();
                    channelIM_SX.code = "SX" + matrixCodes[chanIdx];
                    channelIM_SX.description = "Inclination matrix";
                    channelIM_SX.locationCode = "IM";
                    channelIM_SX.types.add(ChannelType.WEATHER);
                    channelIM_SX.types.add(ChannelType.TRIGGERED);
                    channelIM_SX.types.add(ChannelType.SYNTHESIZED);
                    channelIM_SX.latitude = station.latitude;
                    channelIM_SX.longitude = station.longitude;
                    channelIM_SX.elevation = station.elevation;
                    channelIM_SX.sampleRate = targetSamplingRate;
                    channelIM_SX.startDate = startTime.toString();
                    channelIM_SX.endDate = endTime.toString();
                    station.channels.add(channelIM_SX);
                }
                // Rotation matrix
                for (int chanIdx = 0; chanIdx < matrixCodes.length; chanIdx++) {
                    final SignalChannel channelRM_SX = new SignalChannel();
                    channelRM_SX.code = "SX" + matrixCodes[chanIdx];
                    channelRM_SX.description = "Rotation matrix";
                    channelRM_SX.locationCode = "RM";
                    channelRM_SX.types.add(ChannelType.WEATHER);
                    channelRM_SX.types.add(ChannelType.TRIGGERED);
                    channelRM_SX.types.add(ChannelType.SYNTHESIZED);
                    channelRM_SX.latitude = station.latitude;
                    channelRM_SX.longitude = station.longitude;
                    channelRM_SX.elevation = station.elevation;
                    channelRM_SX.sampleRate = targetSamplingRate;
                    channelRM_SX.startDate = startTime.toString();
                    channelRM_SX.endDate = endTime.toString();
                    station.channels.add(channelRM_SX);
                }
                // Traces - inclination matrix
                int[] matrixRows = new int[] { 0, 0, 0, 1, 1, 1, 2, 2, 2 };
                int[] matrixCols = new int[] { 0, 1, 2, 0, 1, 2, 0, 1, 2 };
                for (int chanIdx = 0; chanIdx < matrixCodes.length; chanIdx++) {
                    final SignalTrace traceIM_SX = new SignalTrace();
                    traceIM_SX.network = network.code;
                    traceIM_SX.station = station.code;
                    traceIM_SX.location = "IM";
                    traceIM_SX.channel = "SX" + matrixCodes[chanIdx];
                    traceIM_SX.startTime = startTime.toString();
                    traceIM_SX.endTime = endTime.toString();
                    traceIM_SX.samplingRate = targetSamplingRate;
                    traceIM_SX.delta = 1f / traceIM_SX.samplingRate;
                    traceIM_SX.values = new float[sampleCount];
                    for (int sampleIdx = 0; sampleIdx < sampleCount; sampleIdx++) {
                        traceIM_SX.values[sampleIdx] = (float) incMatrices[sampleIdx].get(matrixRows[chanIdx], matrixCols[chanIdx]);
                    }
                    traceIM_SX.calib = 1f;
                    traceIM_SX.npts = traceIM_SX.values.length;
                    doc.traces.add(traceIM_SX);
                }
                // Traces - rotation matrix
                for (int chanIdx = 0; chanIdx < matrixCodes.length; chanIdx++) {
                    final SignalTrace traceRM_SX = new SignalTrace();
                    traceRM_SX.network = network.code;
                    traceRM_SX.station = station.code;
                    traceRM_SX.location = "RM";
                    traceRM_SX.channel = "SX" + matrixCodes[chanIdx];
                    traceRM_SX.startTime = startTime.toString();
                    traceRM_SX.endTime = endTime.toString();
                    traceRM_SX.samplingRate = targetSamplingRate;
                    traceRM_SX.delta = 1f / traceRM_SX.samplingRate;
                    traceRM_SX.values = new float[sampleCount];
                    for (int sampleIdx = 0; sampleIdx < sampleCount; sampleIdx++) {
                        traceRM_SX.values[sampleIdx] = (float) rotMatrices[sampleIdx].get(matrixRows[chanIdx], matrixCols[chanIdx]);
                    }
                    traceRM_SX.calib = 1f;
                    traceRM_SX.npts = traceRM_SX.values.length;
                    doc.traces.add(traceRM_SX);
                }

                // Latitude, longitude, altitude
                final LocationProvider gpsProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER);
                final SignalEquipment gpsSensor = new SignalEquipment();
                gpsSensor.type = gpsProvider.getName();
                gpsSensor.accuracy = gpsProvider.getAccuracy();
                gpsSensor.powerRequirement = gpsProvider.getPowerRequirement();
                gpsSensor.hasMonetaryCost = gpsProvider.hasMonetaryCost();
                gpsSensor.requiresCell = gpsProvider.requiresCell();
                gpsSensor.requiresNetwork = gpsProvider.requiresNetwork();
                gpsSensor.requiresSatellite = gpsProvider.requiresSatellite();
                gpsSensor.supportsAltitude = gpsProvider.supportsAltitude();
                gpsSensor.supportsBearing = gpsProvider.supportsBearing();
                gpsSensor.supportsSpeed = gpsProvider.supportsSpeed();
                final SignalChannel channelMYZ = new SignalChannel();
                channelMYZ.code = "MYZ";
                channelMYZ.description = "Altitude";
                channelMYZ.locationCode = "00";
                channelMYZ.types.add(ChannelType.WEATHER);
                channelMYZ.types.add(ChannelType.TRIGGERED);
                channelMYZ.latitude = station.latitude;
                channelMYZ.longitude = station.longitude;
                channelMYZ.elevation = station.elevation;
                channelMYZ.sampleRate = locationSamplingRate;
                channelMYZ.startDate = startTime.toString();
                channelMYZ.endDate = endTime.toString();
                channelMYZ.sensor = gpsSensor;
                station.channels.add(channelMYZ);
                final SignalChannel channelMYN = new SignalChannel();
                channelMYN.code = "MYN";
                channelMYN.description = "Latitude";
                channelMYN.locationCode = "00";
                channelMYN.types.add(ChannelType.WEATHER);
                channelMYN.types.add(ChannelType.TRIGGERED);
                channelMYN.latitude = station.latitude;
                channelMYN.longitude = station.longitude;
                channelMYN.elevation = station.elevation;
                channelMYN.sampleRate = locationSamplingRate;
                channelMYN.startDate = startTime.toString();
                channelMYN.endDate = endTime.toString();
                channelMYN.sensor = gpsSensor;
                station.channels.add(channelMYN);
                final SignalChannel channelMYE = new SignalChannel();
                channelMYE.code = "MYE";
                channelMYE.description = "Longitude";
                channelMYE.locationCode = "00";
                channelMYE.types.add(ChannelType.WEATHER);
                channelMYE.types.add(ChannelType.TRIGGERED);
                channelMYE.latitude = station.latitude;
                channelMYE.longitude = station.longitude;
                channelMYE.elevation = station.elevation;
                channelMYE.sampleRate = locationSamplingRate;
                channelMYE.startDate = startTime.toString();
                channelMYE.endDate = endTime.toString();
                channelMYE.sensor = gpsSensor;
                station.channels.add(channelMYE);
                // Traces - Latitude, longitude, altitude
                final SignalTrace traceMYZ = new SignalTrace();
                traceMYZ.network = network.code;
                traceMYZ.station = station.code;
                traceMYZ.location = channelMYZ.locationCode;
                traceMYZ.channel = channelMYZ.code;
                traceMYZ.startTime = channelMYZ.startDate;
                traceMYZ.endTime = channelMYZ.endDate;
                traceMYZ.samplingRate = channelMYZ.sampleRate;
                traceMYZ.delta = 1f / traceMYZ.samplingRate;
                traceMYZ.values = altitudeClip;
                traceMYZ.calib = 1f;
                traceMYZ.npts = traceMYZ.values.length;
                doc.traces.add(traceMYZ);
                final SignalTrace traceMYN = new SignalTrace();
                traceMYN.network = network.code;
                traceMYN.station = station.code;
                traceMYN.location = channelMYN.locationCode;
                traceMYN.channel = channelMYN.code;
                traceMYN.startTime = channelMYN.startDate;
                traceMYN.endTime = channelMYN.endDate;
                traceMYN.samplingRate = channelMYN.sampleRate;
                traceMYN.delta = 1f / traceMYN.samplingRate;
                traceMYN.values = latClip;
                traceMYN.calib = 1f;
                traceMYN.npts = traceMYN.values.length;
                doc.traces.add(traceMYN);
                final SignalTrace traceMYE = new SignalTrace();
                traceMYE.network = network.code;
                traceMYE.station = station.code;
                traceMYE.location = channelMYE.locationCode;
                traceMYE.channel = channelMYE.code;
                traceMYE.startTime = channelMYE.startDate;
                traceMYE.endTime = channelMYE.endDate;
                traceMYE.samplingRate = channelMYE.sampleRate;
                traceMYE.delta = 1f / traceMYE.samplingRate;
                traceMYE.values = lonClip;
                traceMYE.calib = 1f;
                traceMYE.npts = traceMYE.values.length;
                doc.traces.add(traceMYE);

                network.stations.add(station);
                doc.networks.add(network);

                final JsonAdapter<SignalDoc> signalDocAdapter = moshi.adapter(SignalDoc.class);
                final String json = signalDocAdapter.indent("  ").toJson(doc);
                LogUtils.iLong(TAG, "SignalDoc: " + json);

                // Reset
                startTime = OffsetDateTime.now();
                updateLastLocation();
            }
        });
    }

    public void startClicked(View view) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            alertPermissionRequired();
            return;
        }

        startTime = OffsetDateTime.now();

        // sensors
        int samplingPeriodUs = 1000000 / targetSamplingRate;
        sensorManager.registerListener(this, gravitySensor, samplingPeriodUs);
        sensorManager.registerListener(this, geomagneticSensor, samplingPeriodUs);
        sensorManager.registerListener(this, linAccelSensor, samplingPeriodUs);
        sensorManager.registerListener(this, gyroscopeSensor, samplingPeriodUs);
        if (flushBufferTimer == null) {
            flushBufferTimer = new Timer();
            flushBufferTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    createSignalDocAndReset();
                }
            }, clipDurationSecs * 1000, clipDurationSecs * 1000);
        }

        // location
        updateLastLocation();
        final LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(1000 / locationSamplingRate);
        locationRequest.setFastestInterval(1000 / locationSamplingRate);
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    @SuppressLint("MissingPermission")
    private void updateLastLocation() {
        fusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location == null) {
                    Log.e(TAG, "FusedLocationProviderClient lastLocation is null");
                    return;
                }
                lastLocation = location;
            }
        });
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == gravitySensor) {
            gravityX.add(event.values[0]);
            gravityY.add(event.values[1]);
            gravityZ.add(event.values[2]);
        } else if (event.sensor == geomagneticSensor) {
            geomagneticX.add(event.values[0]);
            geomagneticY.add(event.values[1]);
            geomagneticZ.add(event.values[2]);
        } else if (event.sensor == linAccelSensor) {
//            linAccelX[linAccelCur] = event.values[0];
//            linAccelY[linAccelCur] = event.values[1];
//            linAccelZ[linAccelCur] = event.values[2];
//            linAccelX.putScalar(linAccelCur, event.values[0]);
//            linAccelY.putScalar(linAccelCur, event.values[1]);
//            linAccelZ.putScalar(linAccelCur, event.values[2]);
            linAccelX.add(event.values[0]);
            linAccelY.add(event.values[1]);
            linAccelZ.add(event.values[2]);
//            linAccelCount++;
//            linAccelCur++;
//            if (linAccelCur >= linAccelX.length) {
//                linAccelCur = 0;
//            }
        } else if (event.sensor == gyroscopeSensor) {
//            linAccelX[linAccelCur] = event.values[0];
//            linAccelY[linAccelCur] = event.values[1];
//            linAccelZ[linAccelCur] = event.values[2];
//            linAccelX.putScalar(linAccelCur, event.values[0]);
//            linAccelY.putScalar(linAccelCur, event.values[1]);
//            linAccelZ.putScalar(linAccelCur, event.values[2]);
            gyroX.add(event.values[0]);
            gyroY.add(event.values[1]);
            gyroZ.add(event.values[2]);
//            linAccelCount++;
//            linAccelCur++;
//            if (linAccelCur >= linAccelX.length) {
//                linAccelCur = 0;
//            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onPause() {
        sensorManager.unregisterListener(this);
        if (flushBufferTimer != null) {
            flushBufferTimer.cancel();
            flushBufferTimer = null;
        }
        if (fusedLocationClient != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        super.onPause();
    }
}
