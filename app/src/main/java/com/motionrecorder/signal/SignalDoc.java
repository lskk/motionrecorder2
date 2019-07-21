package com.motionrecorder.signal;

import java.util.ArrayList;
import java.util.List;

public class SignalDoc {
    public String source;
    public List<SignalNetwork> networks = new ArrayList<>();
    public List<SignalTrace> traces = new ArrayList<>();

    // Annotations
    public String motionLabel;
    public String quakeLabel;
    public String nonquakeLabel;
}
