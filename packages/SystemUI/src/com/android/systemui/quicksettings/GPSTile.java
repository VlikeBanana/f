/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.LocationController.LocationGpsStateChangeCallback;


public class GPSTile extends QuickSettingsTile implements LocationGpsStateChangeCallback {

    private boolean enabled = false;
    private boolean working = false;

    ContentResolver mContentResolver;
    public static GPSTile mInstance;

    public static QuickSettingsTile getInstance(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, final QuickSettingsController qsc, Handler handler, String id) {
        mInstance = null;
        mInstance = new GPSTile(context, inflater, container, qsc);
        return mInstance;
    }

    public GPSTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        mContentResolver = mContext.getContentResolver();
        LocationController controller = new LocationController(mContext);
        controller.addStateChangedCallback(this);

        enabled = Settings.Secure.isLocationProviderEnabled(mContentResolver, LocationManager.GPS_PROVIDER);

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                Settings.Secure.setLocationProviderEnabled(mContentResolver, LocationManager.GPS_PROVIDER, !enabled);
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                return true;
            }
        };
        qsc.registerAction(LocationManager.PROVIDERS_CHANGED_ACTION, this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        enabled = Settings.Secure.isLocationProviderEnabled(mContentResolver, LocationManager.GPS_PROVIDER);
        mLabel = mContext.getString(R.string.quick_settings_gps);
        setGenericLabel();
        applyGPSChanges();
    }

    @Override
    void onPostCreate() {
        setGenericLabel();
        applyGPSChanges();
        super.onPostCreate();
    }

    private void updateTileState() {
        if (enabled && working) {
            mDrawable = R.drawable.ic_qs_location;
        } else if (enabled) {
            mDrawable = R.drawable.ic_qs_gps_on;
        } else {
            mDrawable = R.drawable.ic_qs_gps_off;
        }
    }

    void applyGPSChanges() {
        updateTileState();
        updateQuickSettings();
    }

    @Override
    public void onLocationGpsStateChanged(boolean inUse, String description) {
        working = inUse;
        if (description != null) {
            mLabel = description;
        } else {
            setGenericLabel();
        }
        applyGPSChanges();
    }

    private void setGenericLabel() {
        mLabel = (enabled ? mContext.getString(R.string.quick_settings_gps) : mContext.getString(R.string.quick_settings_gps_off));
    }
}
