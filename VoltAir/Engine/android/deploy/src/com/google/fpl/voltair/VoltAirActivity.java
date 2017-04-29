/*
 * Copyright (C) 2014 Google Inc.
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

// TODO: Remove VoltAir references in this file.
package com.google.fpl.voltair;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Toast;

import com.google.fpl.voltair.R;
import com.google.fpl.utils.SoundManager;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.android.gms.appstate.AppStateManager;
import com.google.android.gms.appstate.AppStateStatusCodes;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.achievement.Achievements;

import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.qtproject.qt5.android.bindings.QtActivity;

/**
 * @brief Subclass of QtActivity to provide Android-specific functionality.
 *
 * This class is responsible for:
 *   - Managing the activity lifecyle events
 *   - Capturing @c InputEvent%s
 *   - Notifying the native C++ application of relevant events
 *   - Providing hooks for Google Play Games Services (GPGS) sign-in, achievements, and cloud sync
 *   - Providing hooks for Google Analytics (GA)
 *   - Exposing Android-specific SoundManager APIs to native code for gapless playback of background
 *     music (BGM)
 */
public class VoltAirActivity extends QtActivity implements InputManager.InputDeviceListener {
    private static final String LOG_TAG = VoltAirActivity.class.getName();
    private static final String VOLTAIR_PREFS = "VoltAirPreferences";
    // Request code when invoking Activities whose result we don't care about.
    private static final int RC_UNUSED = 5001;
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private AudioManager mAudioManager = null;
    private InputManager mInputManager = null;
    private boolean mSignInFailed = false;
    private boolean mSyncing = false;
    // Data to save to cloud when connection is established
    private String mBufferedCloudData = null;
    private SoundManager mSoundManager = null;
    private Tracker mTracker = null;

    /**
     * @brief Called when the activity is starting.
     *
     * Initializes services for BGM, GPGS, and GA.
     * @param savedInstanceState Bundle that contains the data that was most recently supplied by
     * the activity through @c onSaveInstanceState()
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        mInputManager = (InputManager) getSystemService(INPUT_SERVICE);

        mSoundManager = new SoundManager();

        mTracker = GoogleAnalytics.getInstance(this).newTracker(R.xml.voltair_tracker);

        onApplicationCreate();
    }

    /**
     * @brief Called after onCreate() or @c onRestart() when the activity is being displayed to the
     * user.
     * @note This may automatically kick off a GPGS sign-in flow. See
     * GooglePlayServicesHelper#onStart for more information.
     */
    @Override
    public void onStart() {
        super.onStart();

        mInputManager.registerInputDeviceListener(this, null);

        // Restore preferences.
        SharedPreferences settings = getSharedPreferences(VOLTAIR_PREFS, Context.MODE_PRIVATE);

        mSoundManager.onStart(this);

        onApplicationStart();
    }

    /**
     * @brief Called when the activity has returned to the foreground (although not necessarily
     * having window focus) and is ready for user interaction.
     */
    @Override
    public void onResume() {
        super.onResume();
        onApplicationResume();
    }

    /**
     * @brief Called when the activity is going into the background, but has not yet been killed.
     */
    @Override
    public void onPause() {
        super.onPause();
        onApplicationPause();
    }

    /**
     * @brief Called when the activity is no longer visible to the user.
     */
    @Override
    public void onStop() {
        super.onStop();

        mInputManager.unregisterInputDeviceListener(this);

        // Save preferences.
        saveBufferedAchievements();

        mSoundManager.onStop();

        onApplicationStop();
    }

    /**
     * @brief Called when the activity is finishing or being killed by the system.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        onApplicationDestroy();
    }

    /**
     * @brief Called when app state data has been loaded successfully.
     * @param result Result retrieved from @c AppStateManager.StateResult with the loaded data
     */
    public void onStateLoaded(AppStateManager.StateLoadedResult result) {
        int statusCode = result.getStatus().getStatusCode();
        byte[] localByteData = result.getLocalData();
        String localData = localByteData != null ? new String(localByteData, UTF_8) : null;

        switch (statusCode) {
        case AppStateStatusCodes.STATUS_OK:
            Log.i(LOG_TAG, "Status = OK");
            // Data was successfully loaded from the cloud: merge with local data.
            break;
        case AppStateStatusCodes.STATUS_STATE_KEY_NOT_FOUND:
            Log.i(LOG_TAG, "Status = STATE KEY NOT FOUND");
            // Key not found means there is no saved data.
            break;
        case AppStateStatusCodes.STATUS_NETWORK_ERROR_NO_DATA:
            Log.i(LOG_TAG, "Status = NETWORK ERROR NO DATA");
            // Can't reach cloud, and we have no local state.
            // TODO: Warn user that they may not see their existing progress, but any new progress
            // won't be lost.
            break;
        case AppStateStatusCodes.STATUS_NETWORK_ERROR_STALE_DATA:
            Log.i(LOG_TAG, "Status = NETWORK ERROR STALE DATA");
            // Can't reach cloud, but we have locally cached data.
            break;
        case AppStateStatusCodes.STATUS_CLIENT_RECONNECT_REQUIRED:
            Log.i(LOG_TAG, "Status = CLIENT RECONNECT REQUIRED");
            break;
        default:
            Log.i(LOG_TAG, "Status = ERROR");
            // TODO: Notify user of error.
            break;
        }

        onCloudDataLoaded(statusCode, localData);
        mSyncing = false;
    }

    /**
     * @brief Called to process (and possibly intercept) generic motion events.
     * @param event Generic @c MotionEvent to handle
     * @returns @c true if @p event was handled
     */
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (isGamepadEvent(event)) {
            if (onGamepadMotionEvent(event)) {
                return true;
            }
        } else if (isTouchNavigationEvent(event)) {
            if (onTouchNavigationMotionEvent(event)) {
                return true;
            }
        }
        return super.dispatchGenericMotionEvent(event);
    }

    /**
     * @brief Called to process (and possibly intercept) key events.
     * @param event @c KeyEvent to handle
     * @returns @c true if @p event was handled
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            // Since QtActivity does not handle volume (throwing the events on the floor) and we
            // cannot call super.super, we must handle managing of the volume here
            // TODO: Figure out how to get the volume Ui slide to show up without permanently
            // breaking immersive mode.
            switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE, 0 /* No flags */);
                break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER, 0 /* No flags */);
                break;
            }
        }

        // QtActivity (i.e. super) will convert all key events to QKeyEvents and *always* return
        // true saying it accepted the event -- even on Gamepad key events it doesn't understand.
        // This is annoying, and unfortunately means that we must always let controllers take a look
        // at the event even if QtActivity understood it and accepted it for use (e.g. in the UI).
        // However, we must be careful with events that are successfully translated (e.g. Keyboard
        // key events) so as to not spawn two separate controllers (one here with the Android
        // KeyEvent, and the other one in "InputArea" with the translated QKeyEvent).
        if (isGamepadEvent(event)) {
            if (onGamepadKeyEvent(event)) {
                return true;
            }
        } else if (isTouchNavigationEvent(event)) {
            if (onTouchNavigationKeyEvent(event)) {
                return true;
            }
        } else if (isKeyboardEvent(event)) {
            if (onKeyboardKeyEvent(event)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * @brief Called whenever an input device has been added to the system.
     * @param deviceId Id of the input device that was added
     */
    @Override
    public void onInputDeviceAdded(int deviceId) {
        // Do nothing. We only care about when a device disconnects as we lazily initialize.
    }

    /**
     * @brief Called whenever the properties of an input device have changed since they were last
     * queried.
     * @param deviceId Id of the input device that has changed
     */
    @Override
    public void onInputDeviceChanged(int deviceId) {
        // Do nothing. We only care about when a device disconnects as we lazily initialize.
    }

    /**
     * @brief Called whenever an input device has been removed from the system.
     * @param deviceId Id of the input device that was removed
     */
    @Override
    public void onInputDeviceRemoved(int deviceId) {
        onControllerDisconnect(deviceId);
    }

    /**
     * @brief Returns the hardware deviceId of the touch screen input device, or -1 if none exists.
     * @note If multiple touch screen devices are present, this returns the id of the first one
     * discovered.
     */
    public int getTouchScreenDeviceId() {
        for (int deviceId : mInputManager.getInputDeviceIds()) {
            InputDevice device = mInputManager.getInputDevice(deviceId);
            if (isSourceType(device, InputDevice.SOURCE_TOUCHSCREEN)) {
                return device.getId();
            }
        }
        return -1;
    }

    /**
     * @brief Returns a reference to the Java-based SoundManager used for achieving gapless BGM
     * playback.
     */
    public SoundManager getSoundManager() {
        return mSoundManager;
    }

    /**
     * @brief Returns the version string of the Android application.
     */
    public String getVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0 /*no flags*/).versionName;
        } catch (NameNotFoundException e) {
            Log.e(LOG_TAG, e.getMessage());
            return null;
        }
    }

    /**
     * @brief Sets the Google Analytics screen name and optionally sends a screen view hit.
     * @param screenName Name of screen to be set
     * @param sendScreenView @c true if a screen view hit should be sent
     */
    public void setTrackerScreenName(String screenName, boolean sendScreenView) {
        mTracker.setScreenName(screenName);
        if (sendScreenView) {
            mTracker.send(new HitBuilders.AppViewBuilder().build());
        }
    }

    /**
     * @brief Sends an event hit to Google Analytics.
     * @param category Category in which the event will be filed
     * @param action Action associated with the event
     */
    public void sendTrackerEvent(String category, String action) {
        mTracker.send(new HitBuilders.EventBuilder()
                .setCategory(category)
                .setAction(action)
                .build());
    }

    /**
     * @brief Sends an event hit to Google Analytics.
     * @param category Category in which the event will be filed
     * @param action Action associated with the event
     * @param label Descriptive label used for further differentiation of categorical actions
     */
    public void sendTrackerEvent(String category, String action, String label) {
        mTracker.send(new HitBuilders.EventBuilder()
                .setCategory(category)
                .setAction(action)
                .setLabel(label)
                .build());
    }

    /**
     * @brief Sends an event hit to Google Analytics.
     * @param category Category in which the event will be filed
     * @param action Action associated with the event
     * @param label Descriptive label used for further differentiation of categorical actions
     * @param value Value to be logged with the event
     */
    public void sendTrackerEvent(String category, String action, String label, long value) {
        mTracker.send(new HitBuilders.EventBuilder()
                .setCategory(category)
                .setAction(action)
                .setLabel(label)
                .setValue(value)
                .build());
    }

    /**
     * @brief Sends an event hit to Google Analytics.
     * @param category Category in which the event will be filed
     * @param action Action associated with the event
     * @param value Value to be logged with the event
     */
    public void sendTrackerEvent(String category, String action, long value) {
        mTracker.send(new HitBuilders.EventBuilder()
                .setCategory(category)
                .setAction(action)
                .setValue(value)
                .build());
    }

    /**
     * @brief Native callback for onCreate() lifecycle event.
     */
    public native void onApplicationCreate();
    /**
     * @brief Native callback for onStart() lifecycle event.
     */
    public native void onApplicationStart();
    /**
     * @brief Native callback for onResume() lifecycle event.
     */
    public native void onApplicationResume();
    /**
     * @brief Native callback for onPause() lifecycle event.
     */
    public native void onApplicationPause();
    /**
     * @brief Native callback for onStop() lifecycle event.
     */
    public native void onApplicationStop();
    /**
     * @brief Native callback for onDestroy() lifecycle event.
     */
    public native void onApplicationDestroy();
    /**
     * @brief Native callback for onInputDeviceRemoved().
     */
    public native void onControllerDisconnect(int deviceId);
    /**
     * @brief Native callback for dispatchGenericMotionEvent() of gamepad related @c MotionEvent%s.
     */
    public native boolean onGamepadMotionEvent(MotionEvent e);
    /**
     * @brief Native callback for dispatchGenericMotionEvent() of touch navigation related @c
     * MotionEvent%s.
     */
    public native boolean onTouchNavigationMotionEvent(MotionEvent e);
    /**
     * @brief Native callback for dispatchKeyEvent() of gamepad related @c KeyEvent%s.
     */
    public native boolean onGamepadKeyEvent(KeyEvent e);
    /**
     * @brief Native callback for dispatchKeyEvent() of touch navigation related @c KeyEvent%s.
     */
    public native boolean onTouchNavigationKeyEvent(KeyEvent e);
    /**
     * @brief Native callback for dispatchKeyEvent() of keyboard related @c KeyEvent%s.
     */
    public native boolean onKeyboardKeyEvent(KeyEvent e);
    /**
     * @brief Native callback for onSignInSucceeded() and onSignInFailed().
     * @param signedIntoCloud @c true if notifying of sign-in success
     */
    public native void onSignedIntoCloudChanged(boolean signedIntoCloud);
    /**
     * @brief Native callback for onStateLoaded().
     * @param statusCode Status code indicating load result and possible errors
     * @param data String encoded loaded save game data or null if loading error occurred
     */
    public native void onCloudDataLoaded(int statusCode, String data);
    /**
     * @brief Native callback for onStateConflict().
     * @param localData String encoded local save game data that is in conflict
     * @param serverData String encoded cloud save game data that is in conflict
     */
    public native String onCloudDataConflict(String localData, String serverData);

    private static boolean isSourceType(InputDevice device, int querySource) {
        if (device == null) {
            return false;
        } else {
            return (device.getSources() & querySource) == querySource;
        }
    }
    private static boolean isFromSource(InputEvent event, int querySource) {
        return isSourceType(event.getDevice(), querySource);
    }
    private static boolean isTouchNavigationEvent(InputEvent event) {
        return isFromSource(event, InputDevice.SOURCE_TOUCH_NAVIGATION)
                && isFromSource(event, InputDevice.SOURCE_KEYBOARD);
    }
    private static boolean isGamepadEvent(MotionEvent event) {
        return (isFromSource(event, InputDevice.SOURCE_JOYSTICK)
                || isFromSource(event, InputDevice.SOURCE_GAMEPAD))
               && event.getActionMasked() == MotionEvent.ACTION_MOVE;
    }
    private static boolean isGamepadEvent(KeyEvent event) {
        return isFromSource(event, InputDevice.SOURCE_JOYSTICK)
                || isFromSource(event, InputDevice.SOURCE_GAMEPAD);
    }
    private static boolean isKeyboardEvent(KeyEvent event) {
        return isFromSource(event, InputDevice.SOURCE_KEYBOARD);
    }

    private String getAchievementId(String name) {
        int id = getResources().getIdentifier(name, "string", getPackageName());
        return getString(id);
    }

    private String getAchievementTitle(String name) {
        int id = getResources().getIdentifier(String.format("%s_title", name), "string",
                getPackageName());
        return getString(id);
    }

    // TODO: Move this into C++ and use the styled custom toast.
    private void showAchievementToast(final String prefix, final String achievementName) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast toast = Toast.makeText(VoltAirActivity.this, String.format("%s: %s",
                        prefix, getAchievementTitle(achievementName)), Toast.LENGTH_LONG);
                toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
                toast.show();
            }});
    }

    private void saveBufferedAchievements() {
    }
}
