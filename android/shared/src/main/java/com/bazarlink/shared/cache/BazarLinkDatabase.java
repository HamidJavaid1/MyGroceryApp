package com.bazarlink.shared.cache;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {CachedProduct.class, CachedOrder.class}, version = 1, exportSchema = true)
public abstract class BazarLinkDatabase extends RoomDatabase {
    private static volatile BazarLinkDatabase instance;

    public static BazarLinkDatabase get(Context context) {
        if (instance == null) {
            synchronized (BazarLinkDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context, BazarLinkDatabase.class, "bazarlink.db").build();
                }
            }
        }
        return instance;
    }
}

@Entity
class CachedProduct {
    @PrimaryKey public long id;
    public String json;
    public long cachedAt;
}

@Entity
class CachedOrder {
    @PrimaryKey public long id;
    public String json;
    public long cachedAt;
}
