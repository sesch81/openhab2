package org.openhab.binding.helios.ws.rest;

import com.google.gson.JsonObject;

public class RESTEvent {
    public long id;
    public long utcTime;
    public long upTime;
    public String event;
    public JsonObject params;

    RESTEvent() {
    }
}
