package com.motionrecorder.signal;

public class SignalEquipment {
    public String type;
    public String description;
    public String manufacturer;
    public String vendor;
    public String model;
    public String serialNumber;
    public String installationDate;
    public String removalDate;
    public String calibrationDate;

    // GPS
    public Integer accuracy;
    public Integer powerRequirement;
    public Boolean hasMonetaryCost;
    public Boolean requiresCell;
    public Boolean requiresNetwork;
    public Boolean requiresSatellite;
    public Boolean supportsAltitude;
    public Boolean supportsBearing;
    public Boolean supportsSpeed;

}
