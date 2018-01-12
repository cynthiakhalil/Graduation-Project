package com.vuforia.samples.VuforiaSamples.app.ImageTargets;

/**
 * Created by Elie on 10/12/16.
 */

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Binder;
import android.os.Bundle;

public class LocationService implements LocationListener, SensorEventListener {
    /*------ Instance Variables ------*/

    //Location Manager Constants
    public static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 13; // 20 meters
    public static final long MIN_TIME_BW_UPDATES = 1000 * 10; // 1 minute
    public static final long FAST_INTERVAL_CEILING_IN_MILLISECONDS = 1000 * 1;
    // Define a request code to send to Google Play services
    public final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    private boolean canGetLocation;
    private double latitude;
    private double longitude;
    private Location currentBestLocation;
    private LocationManager locationManager;
    private SensorManager sensorManager;


    Context mContext;

    public LocationService(Context mContext) {
        // location service started
        this.mContext = mContext;
        locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
        boolean isGpsProviderEnabled = isGpsProviderEnabled();
        boolean isNetworkProviderEnabled = isNetworkProviderEnabled();
        canGetLocation = isGpsProviderEnabled || isNetworkProviderEnabled;
        if (canGetLocation) {
            if (isGpsProviderEnabled) {
                currentBestLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if ((isGpsProviderEnabled && (currentBestLocation == null || currentBestLocation.getLatitude() == 0) && isNetworkProviderEnabled)
                    ||
                    (!isGpsProviderEnabled && isNetworkProviderEnabled)) {
                currentBestLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            //if(currentBestLocation != null)
            //SnatchApp.updateUserLocation(currentBestLocation.getLatitude(), currentBestLocation.getLongitude());
        }
        sensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
    }


    public void destroy() {
        locationManager.removeUpdates(this);
    }

    /**
     *
     * Get Latitude
     *
     * @return double the current latitude<br>
     * 0.0 if failed to get the location
     */
    public double getLatitude() {
        if (currentBestLocation != null) {
            latitude = currentBestLocation.getLatitude();
        }
        return latitude;
    }

    public Location getLocation() {
        return currentBestLocation;
    }

    /**
     * Get Longitude
     *
     * @return double the current Longitude<br>
     * 0.0 if failed to get the location
     */
    public double getLongitude() {
        if (currentBestLocation != null) {
            longitude = currentBestLocation.getLongitude();
        }
        return longitude;
    }

    /**
     * Check if best network provider
     *
     * @return boolean true if location services are enabled false otherwise
     */
    public boolean canGetLocation() {
        return canGetLocation;
    }

    /**
     * Determines if the gps provider is enabled
     *
     * @return true if gps provider is enabled; false otherwise
     */
    public boolean isGpsProviderEnabled() {
        boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        return isGPSEnabled;
    }

    /**
     * Determines if the network provider is enabled
     *
     * @return true if network provider is enabled; false otherwise
     */
    public boolean isNetworkProviderEnabled() {
        boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        return isNetworkEnabled;
    }


    /**
     * Determines if the two Given Providers are the same
     *
     * @param provider1 - the first provider
     * @param provider2 - the second provider
     * @return true if the providers are the same; false otherwise
     */
    public boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }


    /**
     * Determine whether the given <code>location</code> is the better than the
     * <code>currentBestLocation</code>
     *
     * @param location            - the new location
     * @param currentBestLocation - the current location
     * @return true if the given location is better than the
     * currentBestLocation; false otherwise
     */
    private boolean isBetterLocation(Location location) {
        if (currentBestLocation == null ||
                (location != null &&
                        location.getLatitude() != 0
                        && currentBestLocation.getLatitude() == 0)
                ) {
            return true;
        }

        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > MIN_TIME_BW_UPDATES;
        boolean isSignificantlyOlder = timeDelta < -MIN_TIME_BW_UPDATES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than MIN_TIME_BW_UPDATES since the current location, use
        // the new location because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
        } else if (isSignificantlyOlder) {
            // If the new location is more than MIN_TIME_BW_UPDATES older, it must be worse
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(), currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and
        // accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void onLocationChanged(Location location) {
        if (location != null && location.getLatitude() != 0 && isBetterLocation(location)) {
            canGetLocation = true;
            currentBestLocation = location;
            //Toast.makeText(mContext, location.getLatitude() + " " + location.getLongitude(), Toast.LENGTH_LONG).show();
            //SnatchApp.updateUserLocation(location.getLatitude(), location.getLongitude());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onProviderDisabled(String provider) {
        if (!isGpsProviderEnabled() && !isNetworkProviderEnabled()) {
            canGetLocation = false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onProviderEnabled(String arg0) {
        if (isGpsProviderEnabled() || isNetworkProviderEnabled()) {
            canGetLocation = true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
		/* This is called when the GPS status alters */
        switch (status) {
            case LocationProvider.OUT_OF_SERVICE:
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                onProviderDisabled(provider);
                break;
            case LocationProvider.AVAILABLE:
                onProviderEnabled(provider);
                break;
        }
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    public void unregister() {
        locationManager.removeUpdates(this);
    }

    /**
     * Location to return onBind
     */
    public class LocationBinder extends Binder {
        public LocationService getService() {
            return LocationService.this;
        }
    }

}