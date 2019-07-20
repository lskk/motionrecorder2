package com.motionrecorder.signal;

/**
 {
 "network": "XX",
 "station": "X001",
 "location": "",
 "channel": "SNZ",
 "startTime": "2003-05-29T02:13:22.043400Z",
 "endTime": "2003-05-29T02:18:20.693400Z",
 "samplingRate": 40.0,
 "delta": 0.025, // fractional seconds
 "npts": 11947, // number of sample points
 "calib": 1.0,
 "values": [
 // null not allowed
 ]
 }

 */
public class SignalTrace {
    public String network;
    public String station;
    public String location;
    public String channel;
    public String startTime;
    public String endTime;
    public int samplingRate;
    public float delta;
    public int npts;
    public float calib;
    public float[] values;
}
