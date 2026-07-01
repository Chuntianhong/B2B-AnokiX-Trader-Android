package com.anokix.traderapp.network.dto;

import com.google.gson.JsonElement;

/**
 * data block of GET /api/common/preferences. The {@code notifications} field is
 * polymorphic on the server: an empty array {@code []} when nothing is stored
 * (treat every toggle as ON by default) or an object
 * {@code {"orderAlerts":true,"deliveryUpdates":false,...}} once saved. It is kept
 * as a raw {@link JsonElement} so Gson never chokes on the array-vs-object shape;
 * the screen reads the flags defensively.
 */
public class PreferencesData {
    public Preferences preferences;

    public static class Preferences {
        public String language;
        public String currency;
        public String timezone;
        public String date_format;
        public JsonElement notifications;
    }
}
