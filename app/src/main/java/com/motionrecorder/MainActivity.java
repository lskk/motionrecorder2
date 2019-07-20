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
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.common.collect.EvictingQueue;
import com.google.common.primitives.Floats;
import com.jakewharton.threetenabp.AndroidThreeTen;
import com.motionrecorder.signal.ChannelType;
import com.motionrecorder.signal.SignalChannel;
import com.motionrecorder.signal.SignalContact;
import com.motionrecorder.signal.SignalDoc;
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

public class MainActivity extends AppCompatActivity implements OnRequestPermissionsResultCallback, SensorEventListener {

    private static final String TAG = MainActivity.class.getName();
    private static final int MY_PERMISSIONS_ACCESS_FINE_LOCATION = 1;
    private SensorManager sensorManager;
    private Sensor linAccelSensor;
    private int targetSamplingRate = 50;
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
    private FusedLocationProviderClient fusedLocationClient;
    private Moshi moshi;
    private final String seedNetworkCode = "XX";
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;

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

        moshi = new Moshi.Builder().build();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // permission was granted, yay!
            Log.i(TAG, "Permission granted");
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

    @SuppressLint("MissingPermission")
    private void createSignalDocAndReset() {
        endTime = OffsetDateTime.now();
//                    Log.i(TAG, String.format("copy buffer %d ms. linAccelCount=%d, linAccelStart=%d",
//                            clipDurationSecs, linAccelCount, linAccelStart));
        Log.i(TAG, String.format("copy buffer %d s. gravity.size=%d, geomagnetic.size=%d, linAccel.size=%d, gyro.size=%d.",
                clipDurationSecs, gravityX.size(), geomagneticX.size(), linAccelX.size(), gyroX.size()));
        int sampleCount = clipDurationSecs * targetSamplingRate;

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
        float[] linAccelWorldZ = new float[sampleCount];
        float[] linAccelWorldN = new float[sampleCount];
        float[] linAccelWorldE = new float[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            SensorManager.getRotationMatrix(rotMat, incMat,
                    new float[] { gravityXClip[i], gravityYClip[i], gravityZClip[i] },
                    new float[] { geomagneticXClip[i], geomagneticYClip[i], geomagneticZClip[i] });
            final SimpleMatrix rotMatrix = new SimpleMatrix(4, 4, true, rotMat);
            final SimpleMatrix linAccelRot = rotMatrix.mult(new SimpleMatrix(4, 1, true,
                    new float[]{linAccelXClip[i], linAccelYClip[i], linAccelZClip[i], 1}));
            linAccelWorldE[i] = Math.round( (float) linAccelRot.get(0) * 100) / 100f;
            linAccelWorldN[i] = Math.round( (float) linAccelRot.get(1) * 100) / 100f;
            linAccelWorldZ[i] = Math.round( (float) linAccelRot.get(2) * 100) / 100f;
        }
        Log.i(TAG, String.format("linAccelWorldZ=%s", Floats.join(" ", linAccelWorldZ)));
        Log.i(TAG, String.format("linAccelWorldN=%s", Floats.join(" ", linAccelWorldN)));
        Log.i(TAG, String.format("linAccelWorldE=%s", Floats.join(" ", linAccelWorldE)));

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        fusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                final SignalDoc doc = new SignalDoc();
                doc.source = "Motion Recorder";
                final SignalNetwork network = new SignalNetwork();
                network.code = seedNetworkCode;
                final SignalStation station = new SignalStation();
                station.code = Settings.Secure.ANDROID_ID; // FIXME: input by user
                station.latitude = (float) location.getLatitude();
                station.longitude = (float) location.getLongitude();
                station.elevation = (float) location.getAltitude();
                final SignalOperator operator = new SignalOperator();
                final SignalContact contact = new SignalContact();
                contact.name = "TODO";
                operator.contacts.add(contact);
                station.operator = operator;

                // Gravity
                final SignalChannel channelSGA = new SignalChannel();
                channelSGA.code = "SGA";
                channelSGA.locationCode = "00";
                channelSGA.types.add(ChannelType.GEOPHYSICAL);
                channelSGA.types.add(ChannelType.TRIGGERED);
                channelSGA.sampleRate = targetSamplingRate;
                channelSGA.startDate = startTime.toString();
                channelSGA.endDate = endTime.toString();
                station.channels.add(channelSGA);
                final SignalChannel channelSGB = new SignalChannel();
                channelSGB.code = "SGB";
                channelSGB.locationCode = "00";
                channelSGB.types.add(ChannelType.GEOPHYSICAL);
                channelSGB.types.add(ChannelType.TRIGGERED);
                channelSGB.sampleRate = targetSamplingRate;
                channelSGB.startDate = startTime.toString();
                channelSGB.endDate = endTime.toString();
                station.channels.add(channelSGB);
                final SignalChannel channelSGC = new SignalChannel();
                channelSGC.code = "SGC";
                channelSGC.locationCode = "00";
                channelSGC.types.add(ChannelType.GEOPHYSICAL);
                channelSGC.types.add(ChannelType.TRIGGERED);
                channelSGC.sampleRate = targetSamplingRate;
                channelSGC.startDate = startTime.toString();
                channelSGC.endDate = endTime.toString();
                station.channels.add(channelSGC);

                // Geomagnetic
                final SignalChannel channelSFA = new SignalChannel();
                channelSFA.code = "SFA";
                channelSFA.locationCode = "00";
                channelSFA.types.add(ChannelType.GEOPHYSICAL);
                channelSFA.types.add(ChannelType.TRIGGERED);
                channelSFA.sampleRate = targetSamplingRate;
                channelSFA.startDate = startTime.toString();
                channelSFA.endDate = endTime.toString();
                station.channels.add(channelSFA);
                final SignalChannel channelSFB = new SignalChannel();
                channelSFB.code = "SFB";
                channelSFB.locationCode = "00";
                channelSFB.types.add(ChannelType.GEOPHYSICAL);
                channelSFB.types.add(ChannelType.TRIGGERED);
                channelSFB.sampleRate = targetSamplingRate;
                channelSFB.startDate = startTime.toString();
                channelSFB.endDate = endTime.toString();
                station.channels.add(channelSFB);
                final SignalChannel channelSFC = new SignalChannel();
                channelSFC.code = "SFC";
                channelSFC.locationCode = "00";
                channelSFC.types.add(ChannelType.GEOPHYSICAL);
                channelSFC.types.add(ChannelType.TRIGGERED);
                channelSFC.sampleRate = targetSamplingRate;
                channelSFC.startDate = startTime.toString();
                channelSFC.endDate = endTime.toString();
                station.channels.add(channelSFC);

                // Linear acceleration: SNA, SNB, SNC, SNZ, SNN, SNE
                final SignalChannel channelSNA = new SignalChannel();
                channelSNA.code = "SNA";
                channelSNA.locationCode = "00";
                channelSNA.types.add(ChannelType.GEOPHYSICAL);
                channelSNA.types.add(ChannelType.TRIGGERED);
                channelSNA.sampleRate = targetSamplingRate;
                channelSNA.startDate = startTime.toString();
                channelSNA.endDate = endTime.toString();
                station.channels.add(channelSNA);
                final SignalChannel channelSNB = new SignalChannel();
                channelSNB.code = "SNB";
                channelSNB.locationCode = "00";
                channelSNB.types.add(ChannelType.GEOPHYSICAL);
                channelSNB.types.add(ChannelType.TRIGGERED);
                channelSNB.sampleRate = targetSamplingRate;
                channelSNB.startDate = startTime.toString();
                channelSNB.endDate = endTime.toString();
                station.channels.add(channelSNB);
                final SignalChannel channelSNC = new SignalChannel();
                channelSNC.code = "SNC";
                channelSNC.locationCode = "00";
                channelSNC.types.add(ChannelType.GEOPHYSICAL);
                channelSNC.types.add(ChannelType.TRIGGERED);
                channelSNC.sampleRate = targetSamplingRate;
                channelSNC.startDate = startTime.toString();
                channelSNC.endDate = endTime.toString();
                station.channels.add(channelSNC);
                final SignalChannel channelSNZ = new SignalChannel();
                channelSNZ.code = "SNZ";
                channelSNZ.locationCode = "00";
                channelSNZ.types.add(ChannelType.GEOPHYSICAL);
                channelSNZ.types.add(ChannelType.TRIGGERED);
                channelSNZ.types.add(ChannelType.SYNTHESIZED);
                channelSNZ.sampleRate = targetSamplingRate;
                channelSNZ.startDate = startTime.toString();
                channelSNZ.endDate = endTime.toString();
                station.channels.add(channelSNZ);
                final SignalChannel channelSNN = new SignalChannel();
                channelSNN.code = "SNN";
                channelSNN.locationCode = "00";
                channelSNN.types.add(ChannelType.GEOPHYSICAL);
                channelSNN.types.add(ChannelType.TRIGGERED);
                channelSNN.types.add(ChannelType.SYNTHESIZED);
                channelSNN.sampleRate = targetSamplingRate;
                channelSNN.startDate = startTime.toString();
                channelSNN.endDate = endTime.toString();
                station.channels.add(channelSNN);
                final SignalChannel channelSNE = new SignalChannel();
                channelSNE.code = "SNE";
                channelSNE.locationCode = "00";
                channelSNE.types.add(ChannelType.GEOPHYSICAL);
                channelSNE.types.add(ChannelType.TRIGGERED);
                channelSNE.types.add(ChannelType.SYNTHESIZED);
                channelSNE.sampleRate = targetSamplingRate;
                channelSNE.startDate = startTime.toString();
                channelSNE.endDate = endTime.toString();
                station.channels.add(channelSNE);

                // Gyroscope
                final SignalChannel channelSJA = new SignalChannel();
                channelSJA.code = "SJA";
                channelSJA.locationCode = "00";
                channelSJA.types.add(ChannelType.GEOPHYSICAL);
                channelSJA.types.add(ChannelType.TRIGGERED);
                channelSJA.sampleRate = targetSamplingRate;
                channelSJA.startDate = startTime.toString();
                channelSJA.endDate = endTime.toString();
                station.channels.add(channelSJA);
                final SignalChannel channelSJB = new SignalChannel();
                channelSJB.code = "SJB";
                channelSJB.locationCode = "00";
                channelSJB.types.add(ChannelType.GEOPHYSICAL);
                channelSJB.types.add(ChannelType.TRIGGERED);
                channelSJB.sampleRate = targetSamplingRate;
                channelSJB.startDate = startTime.toString();
                channelSJB.endDate = endTime.toString();
                station.channels.add(channelSJB);
                final SignalChannel channelSJC = new SignalChannel();
                channelSJC.code = "SJC";
                channelSJC.locationCode = "00";
                channelSJC.types.add(ChannelType.GEOPHYSICAL);
                channelSJC.types.add(ChannelType.TRIGGERED);
                channelSJC.sampleRate = targetSamplingRate;
                channelSJC.startDate = startTime.toString();
                channelSJC.endDate = endTime.toString();
                station.channels.add(channelSJC);
                final SignalChannel channelSJZ = new SignalChannel();
                channelSJZ.code = "SJZ";
                channelSJZ.locationCode = "00";
                channelSJZ.types.add(ChannelType.GEOPHYSICAL);
                channelSJZ.types.add(ChannelType.TRIGGERED);
                channelSJZ.types.add(ChannelType.SYNTHESIZED);
                channelSJZ.sampleRate = targetSamplingRate;
                channelSJZ.startDate = startTime.toString();
                channelSJZ.endDate = endTime.toString();
                station.channels.add(channelSJZ);
                final SignalChannel channelSJN = new SignalChannel();
                channelSJN.code = "SJN";
                channelSJN.locationCode = "00";
                channelSJN.types.add(ChannelType.GEOPHYSICAL);
                channelSJN.types.add(ChannelType.TRIGGERED);
                channelSJN.types.add(ChannelType.SYNTHESIZED);
                channelSJN.sampleRate = targetSamplingRate;
                channelSJN.startDate = startTime.toString();
                channelSJN.endDate = endTime.toString();
                station.channels.add(channelSJN);
                final SignalChannel channelSJE = new SignalChannel();
                channelSJE.code = "SJE";
                channelSJE.locationCode = "00";
                channelSJE.types.add(ChannelType.GEOPHYSICAL);
                channelSJE.types.add(ChannelType.TRIGGERED);
                channelSJE.types.add(ChannelType.SYNTHESIZED);
                channelSJE.sampleRate = targetSamplingRate;
                channelSJE.startDate = startTime.toString();
                channelSJE.endDate = endTime.toString();
                station.channels.add(channelSJE);

                // TODO: Inclination matrix, Rotation matrix

                // TODO: Latitude, longitude, elevation

                // UTC timestamp start and end of trace

                // Device: brand/model/deviceCountry, System: systemName/systemVersion


                network.stations.add(station);
                doc.networks.add(network);

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

                final JsonAdapter<SignalDoc> signalDocAdapter = moshi.adapter(SignalDoc.class);
                final String json = signalDocAdapter.indent("  ").toJson(doc);
                Log.i(TAG, "SignalDoc: " + json);

                // Reset
                startTime = OffsetDateTime.now();
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
        super.onPause();
    }
}
