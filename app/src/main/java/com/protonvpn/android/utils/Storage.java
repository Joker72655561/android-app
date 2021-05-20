/*
 * Copyright (c) 2017 Proton Technologies AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.protonvpn.android.utils;

import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import kotlin.jvm.functions.Function0;
import me.proton.core.network.domain.client.ClientId;

public final class Storage {

    private final static Gson GSON =
        new GsonBuilder()
            .enableComplexMapKeySerialization()
            .registerTypeAdapter(DateTime.class, (JsonSerializer<DateTime>) (json, typeOfSrc, context) ->
                new JsonPrimitive(ISODateTimeFormat.dateTime().print(json)))
            .registerTypeAdapter(DateTime.class, (JsonDeserializer<DateTime>) (json, typeOfT, context) ->
                ISODateTimeFormat.dateTime().parseDateTime(json.getAsString()))
            .registerTypeAdapter(ClientId.class, new ClientIdGsonSerializer())
            .create();

    private static SharedPreferences preferences;

    private Storage() {
    }

    public static void setPreferences(SharedPreferences preferences) {
        Storage.preferences = preferences;
    }

    public static void saveBoolean(String key, boolean value) {
        preferences.edit().putBoolean(key, value).apply();
    }

    public static boolean getBoolean(String key, Boolean defaultValue) {
        return preferences.getBoolean(key, defaultValue);
    }

    public static boolean getBoolean(String key) {
        return preferences.getBoolean(key, false);
    }

    public static void saveInt(String key, int value) {
        preferences.edit().putInt(key, value).apply();
    }

    public static int getInt(String key) {
        try {
            return preferences.getInt(key, 0);
        }
        catch (ClassCastException e) {
            return 0;
        }
    }

    public static void saveLong(String key, long value) {
        preferences.edit().putLong(key, value).apply();
    }

    public static long getLong(String key, long defValue) {
        return preferences.getLong(key, defValue);
    }

    public static void saveString(String key, String value) {
        if (!Objects.equals(getString(key, null), value)) {
            preferences.edit().putString(key, value).apply();
        }
    }

    public static String getString(String key, String defValue) {
        return preferences.getString(key, defValue);
    }

    public static void save(@Nullable Object data) {
        if (data != null) {
            String key = data.getClass().getName();
            preferences.edit().putString(key, GSON.toJson(data)).apply();
        }
    }

    public static <T> void save(@Nullable T data, Class<T> as) {
        if (data != null) {
            preferences.edit().putString(as.getName(), GSON.toJson(data)).apply();
        }
    }

    public static <T> T toObject(Class<T> objClass, @NonNull String json) {
        return GSON.fromJson(json, objClass);
    }

    @Nullable
    public static <T> T load(Class<T> objClass) {
        return load(objClass, objClass);
    }

    @Nullable
    public static <K,V extends K> V load(Class<K> keyClass, Class<V> objClass) {
        String key = keyClass.getName();
        if (!preferences.contains(key)) {
            return null;
        }

        V fromJson;
        try {
            String json = preferences.getString(key, null);
            fromJson = GSON.fromJson(json, objClass);
        }
        catch (Exception e) {
            return null;
        }
        return fromJson;
    }

    public static <T> void delete(Class<T> objClass) {

        String key = objClass.getName();
        preferences.edit().remove(key).apply();
    }

    @Deprecated // use load() with lambda defaultValue
    public static <T> T load(Class<T> objClass, T defaultValue) {

        String key = objClass.getName();
        if (key.equals("com.protonvpn.android.models.config.UserData") && !preferences.contains(key)) {
            key = "com.protonvpn.android.models.config.UserPreferences";
        }
        String json = preferences.getString(key, null);
        if (!preferences.contains(key) || json == null) {
            return defaultValue;
        }

        return GSON.fromJson(json, objClass);
    }

    public static <T> T load(Class<T> objClass, Function0<T> defaultValue) {
        T value = load(objClass);
        if (value == null) {
            value = defaultValue.invoke();
            save(value);
        }
        return value;
    }

    public static void clearAllPreferences() {

        preferences.edit().clear().apply();
    }

}