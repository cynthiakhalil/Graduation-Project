package com.vuforia.samples.VuforiaSamples.app.ImageTargets;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class LocationDatabaseHandler extends SQLiteOpenHelper {
    // If you change the database schema, you must increment the database version.
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "DeviceDatabase.db";

    public LocationDatabaseHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(LocationDatabaseContract.SQL_CREATE_DEVICES_TABLE);
        InsertValues(sqLiteDatabase);
    }

    private void InsertValues(SQLiteDatabase sqLiteDatabase){

        ContentValues values = new ContentValues();
        values.put(LocationDatabaseContract.Locations.COLUMN_NAME, "Bliss");
        values.put(LocationDatabaseContract.Locations.COLUMN_LAT, 33.899505);
        values.put(LocationDatabaseContract.Locations.COLUMN_LNG, 35.47904833);

        sqLiteDatabase.insert(
                LocationDatabaseContract.Locations.TABLE_NAME,
                null,
                values);

        values.clear();
        values.put(LocationDatabaseContract.Locations.COLUMN_NAME, "Fisk");
        values.put(LocationDatabaseContract.Locations.COLUMN_LAT, 33.89949);
        values.put(LocationDatabaseContract.Locations.COLUMN_LNG, 35.4799033);

        sqLiteDatabase.insert(
                LocationDatabaseContract.Locations.TABLE_NAME,
                null,
                values);

        /*values.clear();
        values.put(LocationDatabaseContract.Locations.COLUMN_NAME, "Green Oval");
        values.put(LocationDatabaseContract.Locations.COLUMN_LAT, 33.899595);
        values.put(LocationDatabaseContract.Locations.COLUMN_LNG, 35.47965);

        sqLiteDatabase.insert(
                LocationDatabaseContract.Locations.TABLE_NAME,
                null,
                values);*/

        values.clear();
        values.put(LocationDatabaseContract.Locations.COLUMN_NAME, "Issam Fares");
        values.put(LocationDatabaseContract.Locations.COLUMN_LAT, 33.899955);
        values.put(LocationDatabaseContract.Locations.COLUMN_LNG, 35.47977333);

        sqLiteDatabase.insert(
                LocationDatabaseContract.Locations.TABLE_NAME,
                null,
                values);

        values.clear();
        values.put(LocationDatabaseContract.Locations.COLUMN_NAME, "Nicely");
        values.put(LocationDatabaseContract.Locations.COLUMN_LAT, 33.8996365);
        values.put(LocationDatabaseContract.Locations.COLUMN_LNG, 35.4800879);

        sqLiteDatabase.insert(
                LocationDatabaseContract.Locations.TABLE_NAME,
                null,
                values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply to discard the data and start over
        sqLiteDatabase.execSQL(LocationDatabaseContract.SQL_DELETE_DEVICES_TABLE);
        onCreate(sqLiteDatabase);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
