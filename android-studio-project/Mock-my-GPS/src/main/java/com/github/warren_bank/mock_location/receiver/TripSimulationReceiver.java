package com.github.warren_bank.mock_location.receiver;

import com.github.warren_bank.mock_location.data_model.LocPoint;
import com.github.warren_bank.mock_location.data_model.SharedPrefs;
import com.github.warren_bank.mock_location.service.LocationService;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/** Explicit ADB entry point; the manifest restricts callers to holders of DUMP. */
public class TripSimulationReceiver extends BroadcastReceiver {
    public static final String ACTION_START  = "com.github.warren_bank.mock_location.trip.START";
    public static final String ACTION_STOP   = "com.github.warren_bank.mock_location.trip.STOP";
    public static final String ACTION_UPDATE = "com.github.warren_bank.mock_location.trip.UPDATE";
    public static final String EXTRA_ORIGIN = "origin";
    public static final String EXTRA_DESTINATION = "destination";
    public static final String EXTRA_DURATION = "duration_seconds";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                LocationService.doStop(context, true);
                result(Activity.RESULT_OK, "Stop requested");
                return;
            }
            if (!ACTION_START.equals(action) && !ACTION_UPDATE.equals(action)) {
                throw new IllegalArgumentException("Expected trip.START, trip.STOP or trip.UPDATE action");
            }

            // Validate the whole command before saving or changing the running trip.
            LocPoint origin = intent.hasExtra(EXTRA_ORIGIN)
                ? parsePoint(intent.getStringExtra(EXTRA_ORIGIN)) : SharedPrefs.getTripOrigin(context);
            LocPoint destination = intent.hasExtra(EXTRA_DESTINATION)
                ? parsePoint(intent.getStringExtra(EXTRA_DESTINATION)) : SharedPrefs.getTripDestination(context);
            int duration = SharedPrefs.getTripDuration(context);
            if (intent.hasExtra(EXTRA_DURATION)) {
                Object value = intent.getExtras().get(EXTRA_DURATION);
                if (!(value instanceof Integer)) {
                    throw new IllegalArgumentException("duration_seconds must be an integer (--ei)");
                }
                duration = (Integer) value;
            }
            if (duration <= 0) {
                throw new IllegalArgumentException("duration_seconds must be positive");
            }

            boolean start = ACTION_START.equals(action) || LocationService.isStarted();
            if (start) {
                Intent serviceIntent = LocationService.doStart(context, false, origin, destination, duration);
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(serviceIntent);
                }
                else {
                    context.startService(serviceIntent);
                }
            }

            SharedPreferences.Editor editor = SharedPrefs.getSharedPreferencesEditor(context);
            SharedPrefs.putTripOrigin(editor, context, origin, false);
            SharedPrefs.putTripDestination(editor, context, destination, false);
            SharedPrefs.putTripDuration(editor, context, duration, false);
            if (!editor.commit()) {
                throw new IllegalStateException("Could not save trip configuration");
            }
            LocationService.notifyStateChanged(context);
            result(Activity.RESULT_OK, start ? "Trip start/update requested" : "Trip configuration saved");
        }
        catch (Exception e) {
            Log.e("TripSimulationReceiver", "Trip command failed", e);
            result(Activity.RESULT_CANCELED, e.toString());
        }
    }

    private static LocPoint parsePoint(String text) {
        if (text == null || text.split(",", -1).length != 2) {
            throw new IllegalArgumentException("Coordinates must be latitude,longitude");
        }
        LocPoint point = new LocPoint(text);
        double lat = point.getLatitude();
        double lon = point.getLongitude();
        if (Double.isNaN(lat) || Double.isInfinite(lat) || Math.abs(lat) > 90 ||
            Double.isNaN(lon) || Double.isInfinite(lon) || Math.abs(lon) > 180) {
            throw new IllegalArgumentException("Latitude must be in [-90,90] and longitude in [-180,180]");
        }
        return point;
    }

    private void result(int code, String message) {
        if (isOrderedBroadcast()) {
            setResultCode(code);
            setResultData(message);
        }
    }
}
