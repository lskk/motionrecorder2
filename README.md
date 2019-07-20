# Motion Recorder

Motion Recorder for accelerometer, gyroscope, and magnetometer.

Used for waveform/motion analysis, generate training data set for pattern recognition and machine learning, and seismic research, especially in LSKK and PPTIK Institut Teknologi Bandung (ITB). Initially we want to save to miniSEED format (to make it easier to analyze using SRC Waves or SeisGram2K), however as there is currently no miniSEED library for JavaScript, we decided to save in custom JSON format instead, which can be both saved to files and inserted into MongoDB directly.

Future plan: Server-side converter using Python/ObsPy to export the JSON waveform data to miniSEED format.

Full source code is available at https://github.com/lskk/motionrecorder

## Recorded data

See https://docs.google.com/document/d/1lE8SS3uIRIxJxEm98KTxcWM2psWegYzmmmsFIv1Pu-k/edit#

## Sampling rate

Initially the target sampling rate is 40 Hz.

However, using `react-native-sensors` the actual rate is about 50% of the target sampling rate (with stable use):

* `asus/ASUS_Z01RD/GB` (Zenfone 5Z) accelerometer+gyroscope+magnetometer: ~20 Hz with interval 25ms, ~40 Hz with interval 13ms. ~80 Hz with target 160 Hz except magnetometer stuck at ~75 Hz.
* Samsung A50: accelerometer 53-66 Hz, gyroscope 54-65 Hz, magnetometer 50-61 Hz

Sensor models:

* Zenfone 5Z: ICM20626 accelerometer, ICM20626 gyroscope, ak0991x magnetometer

For Zenfone 5Z, target of 55 Hz consistently achieves ~40 Hz with 3 sensors, interpolation/resampling (both upsampling and downsampling) will be necessary.

WARNING: react-native-svg-charts rendering is significantly reducing sensor performance. BarChart is fastest, followed by LineChart-curveLinear, then LineChart-curveStep.

## TODOs

* [x] Resample
* [x] Gravitation vector.
* [ ] GNSS

## miniSEED channels

1. `SNZ`: Short-period 10-80 Hz, Accelerometer, Vertical.
2. `SNN`: Short-period 10-80 Hz, Accelerometer, North-South.
3. `SNE`: Short-period 10-80 Hz, Accelerometer, East-West.
7. `SFZ`: Short-period 10-80 Hz, Magnetometer, Vertical.
8. `SFN`: Short-period 10-80 Hz, Magnetometer, North-South.
9. `SFE`: Short-period 10-80 Hz, Magnetometer, East-West.

Metadata

Blockette 1000 - Data Only (MiniSEED) Blockette. Data records by themselves do not contain enough information by
themselves to allow time series data to be plotted or even simply analyzed. With the addition of a small amount of additional information these limitations are removed. Blockette 1000 is the Data Only (MiniSEED) Blockette that will allow SEED data records to be self-sufficient in terms of defining a time series.

    stats = {'network': 'BW', 'station': 'RJOB', 'location': '',
            'channel': 'WLZ', 'npts': len(data), 'sampling_rate': 0.1,
            'mseed': {'dataquality': 'D'}}

Network Station(4-chars) Location(2-digit, optional)

e.g. "XX:X001::SNZ"

To get actual units, divide the counts by gain.

## Upload Key

From `Google Drive/PSN_Tsunami/sysadmin`, copy the `quakezone.keystore` (upload key) file into the `android/app` directory in your project folder.

The App Signing Key is managed by Google Play Console.

## References

1. [IRIS - Data Formats: SAC, SEED, miniSEED](https://ds.iris.edu/ds/nodes/dmc/data/formats/)
2. [Anything to MiniSEED](https://docs.obspy.org/tutorial/code_snippets/anything_to_miniseed.html)
