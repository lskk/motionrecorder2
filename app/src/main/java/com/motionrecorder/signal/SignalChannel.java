package com.motionrecorder.signal;

import java.util.HashSet;
import java.util.Set;

public class SignalChannel {
    public String code;
    public String startDate;
    public String endDate;
    public String description;
    public String locationCode;
    public Float latitude;
    public Float longitude;
    public Float elevation;
    public Float depth;
    public Set<ChannelType> types = new HashSet<>();
    public Integer sampleRate;
    public String storageFormat;
    public Float clockDrift;
    public String calibrationUnits;
    public SignalEquipment sensor;
    public String preAmplifier;
    public String dataLogger;
    public String equipment;
}
