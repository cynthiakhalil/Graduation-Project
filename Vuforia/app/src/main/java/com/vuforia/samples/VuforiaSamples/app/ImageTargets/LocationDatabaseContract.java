package com.vuforia.samples.VuforiaSamples.app.ImageTargets;

import android.provider.BaseColumns;

public class LocationDatabaseContract {

    // To prevent someone from accidentally instantiating the contract class,
    // give it an empty constructor.
    public LocationDatabaseContract() {}

    /* Inner class that defines the table contents */
    public static abstract class Locations implements BaseColumns {
        public static final String TABLE_NAME = "locations";
        public static final String COLUMN_NAME = "name";
        public static final String COLUMN_LAT = "lat";
        public static final String COLUMN_LNG = "lng";
    }

    public static final String SQL_DELETE_DEVICES_TABLE =
            "DROP TABLE IF EXISTS " + Locations.TABLE_NAME;

    public static final String SQL_CLEAR_DEVICES_TABLE =
            "DELETE FROM " + Locations.TABLE_NAME;

    private static final String TEXT_TYPE = " TEXT";
    private static final String COMMA_SEP = ",";
    private static final String REAL_TYPE = " REAL";

    public static final String SQL_CREATE_DEVICES_TABLE =
            "CREATE TABLE " + Locations.TABLE_NAME + " (" +
                    Locations._ID + " INTEGER PRIMARY KEY," +
                    Locations.COLUMN_NAME + TEXT_TYPE + COMMA_SEP +
                    Locations.COLUMN_LAT + REAL_TYPE + COMMA_SEP +
                    Locations.COLUMN_LNG + REAL_TYPE +
                    " )";
}
