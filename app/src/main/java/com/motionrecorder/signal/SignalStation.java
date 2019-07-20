package com.motionrecorder.signal;

import java.util.ArrayList;
import java.util.List;

public class SignalStation {
    public String code;
    public String startDate;
    public String endDate;
    public Float latitude;
    public Float longitude;
    public Float elevation;
    public String site;
    public SignalOperator operator;
    public String creationDate;
    public String terminationDate;
    public List<SignalChannel> channels = new ArrayList<>();
}
