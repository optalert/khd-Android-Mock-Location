package com.github.warren_bank.mock_location.service.looper;

// copied from:
//   https://github.com/mcastillof/FakeTraveler/blob/v1.6/app/src/main/java/cl/coders/faketraveler/MockLocationProvider.java

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.atomic.AtomicReference;

public class MockLocationProvider {
    String providerName;
    Context ctx;
    private AtomicReference<Location> previousMockLocation;

    /**
     * Class constructor
     *
     * @param name provider
     * @param ctx  context
     * @return Void
     */
    public MockLocationProvider(String name, Context ctx) {
        this.providerName = name;
        this.ctx = ctx;
        this.previousMockLocation = new AtomicReference<>();

        int powerUsage = 0;
        int accuracy   = 5;

        if (Build.VERSION.SDK_INT >= 30) {
            powerUsage = 1;
            accuracy   = 2;
        }

        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        startup(lm, powerUsage, accuracy, /* maxRetryCount= */ 3, /* currentRetryCount= */ 0);
    }

    private void startup(LocationManager lm, int powerUsage, int accuracy, int maxRetryCount, int currentRetryCount) {
        if (currentRetryCount < maxRetryCount) {
            try {
                shutdown();
                lm.addTestProvider(providerName, false, false, false, false, false, true, true, powerUsage, accuracy);
                lm.setTestProviderEnabled(providerName, true);
            }
            catch(Exception e) {
                startup(lm, powerUsage, accuracy, maxRetryCount, (currentRetryCount + 1));
            }
        }
        else {
            throw new SecurityException("Not allowed to perform MOCK_LOCATION");
        }
    }

    /**
     * Pushes the location in the system (mock). This is where the magic gets done.
     *
     * @param lat latitude
     * @param lon longitude
     * @return Void
     */
    public void pushLocation(double lat, double lon) {
        LocationManager lm    = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        Location mockLocation = MockLocationProvider.getLocation(previousMockLocation, providerName, lat, lon);

        lm.setTestProviderLocation(providerName, mockLocation);
    }

    protected static Location getLocation(AtomicReference<Location> previousMockLocation, double lat, double lon) {
        return MockLocationProvider.getLocation(previousMockLocation, /* providerName= */ null, lat, lon);
    }

    protected static Location getLocation(AtomicReference<Location> previousMockLocation, double lat, double lon, int advanceTimeMillis) {
        return MockLocationProvider.getLocation(previousMockLocation, /* providerName= */ null, lat, lon, advanceTimeMillis);
    }

    protected static Location getLocation(AtomicReference<Location> previousMockLocation, String providerName, double lat, double lon) {
        return MockLocationProvider.getLocation(previousMockLocation, providerName, lat, lon, /* advanceTimeMillis= */ 0);
    }

    protected static Location getLocation(AtomicReference<Location> previousMockLocation, String providerName, double lat, double lon, int advanceTimeMillis) {
//        Log.d("Kolya",
//                "hello provider=" + providerName +
//                        " lat=" + lat +
//                        " lon=" + lon +
//                        " advanceTime=" + advanceTimeMillis + "ms");
        long time = System.currentTimeMillis() + advanceTimeMillis;
        Location previousLocation = (previousMockLocation != null) ? previousMockLocation.get() : null;

        Location mockLocation = new Location(providerName);
        mockLocation.setLatitude(lat);
        mockLocation.setLongitude(lon);
        mockLocation.setAltitude(3F);
        mockLocation.setTime(time);

        mockLocation.setSpeed(0.01F);
        if (previousLocation == null || (time - previousLocation.getTime() >= 1000) ) {
//            Log.d("Kolya", "prev location is null or older than 1 second");
            mockLocation.setSpeed(0.01F);
        } else {
//            Log.d("Kolya", "calculating speed");
            float speed = (float) calculateSpeed(previousLocation.getLatitude(), previousLocation.getLongitude(), previousLocation.getTime(), lat, lon, time);
//            Log.d("Kolya", "speed = " + speed);
            mockLocation.setSpeed( speed );
        }

        mockLocation.setBearing(1F);
        mockLocation.setAccuracy(3F);
        if (Build.VERSION.SDK_INT >= 26) {
            mockLocation.setBearingAccuracyDegrees(0.1F);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            mockLocation.setVerticalAccuracyMeters(0.1F);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            mockLocation.setSpeedAccuracyMetersPerSecond(0.01F);
        }
        if (Build.VERSION.SDK_INT >= 17) {
            mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }

        if (previousMockLocation != null) {
            previousMockLocation.set(mockLocation);
        }
        return mockLocation;
    }

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000.0; // Earth radius (m)

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    public static double calculateSpeed(
            double lat1, double lon1, long time1,
            double lat2, double lon2, long time2
    ) {
        double distance = distanceMeters(lat1, lon1, lat2, lon2); // meters
        double timeSec = (time2 - time1) / 1000.0;                // seconds

        if (timeSec <= 0) return 0;

        return distance / timeSec;  // speed in m/s
    }

    /**
     * Removes the provider
     *
     * @return Void
     */
    public void shutdown() {
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            lm.removeTestProvider(providerName);
        }
        catch(Exception e) {}
    }
}
