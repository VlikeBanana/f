/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.systemui.statusbar;

import android.service.notification.StatusBarNotification;
import android.content.res.Configuration;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.statusbar.StatusBarIconList;
import com.android.internal.widget.SizeAdaptiveLayout;
import com.android.systemui.R;
import com.android.systemui.SearchPanelView;
import com.android.systemui.SystemUI;
import com.android.systemui.statusbar.TransparencyManager;
import com.android.systemui.recent.RecentTasksLoader;
import com.android.systemui.recent.RecentsActivity;
import com.android.systemui.recent.TaskDescription;
import com.android.systemui.statusbar.halo.Halo;
import com.android.systemui.statusbar.phone.Ticker;
import com.android.systemui.statusbar.pie.PieLayout;
import com.android.systemui.statusbar.AppSidebar;
import com.android.systemui.statusbar.policy.NotificationRowLayout;
import com.android.systemui.statusbar.policy.activedisplay.ActiveDisplayView;
import com.android.systemui.statusbar.policy.PieController;
import com.android.systemui.statusbar.policy.PieController.Position;
import com.android.systemui.statusbar.tablet.StatusBarPanel;
import com.android.systemui.statusbar.WidgetView;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManagerNative;
import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.app.Notification;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Slog;
import android.view.Display;
import android.view.Gravity;
import android.view.IWindowManager;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.FrameLayout;
import android.widget.ImageButton; 
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import com.android.systemui.statusbar.halo.Halo;
import com.android.systemui.statusbar.phone.Ticker;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.List;

public abstract class BaseStatusBar extends SystemUI implements
        CommandQueue.Callbacks {
    public static final String TAG = "StatusBar";
    public static final boolean DEBUG = false;
    public static final boolean DEBUG_INPUT = false;
    public static final boolean MULTIUSER_DEBUG = false;

    protected static final int MSG_TOGGLE_RECENTS_PANEL = 1020;
    protected static final int MSG_CLOSE_RECENTS_PANEL = 1021;
    protected static final int MSG_PRELOAD_RECENT_APPS = 1022;
    protected static final int MSG_CANCEL_PRELOAD_RECENT_APPS = 1023;
    protected static final int MSG_OPEN_SEARCH_PANEL = 1024;
    protected static final int MSG_CLOSE_SEARCH_PANEL = 1025;
    protected static final int MSG_SHOW_INTRUDER = 1026;
    protected static final int MSG_HIDE_INTRUDER = 1027;
    protected static final int MSG_TOGGLE_WIDGETS = 1028;
    protected static final int MSG_TOGGLE_SCREENSHOT = 1029;
    protected static final int MSG_TOGGLE_LAST_APP = 1030;
    protected static final int MSG_TOGGLE_KILL_APP = 1031;

    protected static final boolean ENABLE_INTRUDERS = false;

    private WidgetView mWidgetView;

    private boolean mPieShowTrigger = false;
    private boolean mForceBottomTrigger = false;
    private float mPieTriggerThickness;
    private float mPieTriggerHeight;
    private int mPieTriggerGravityLeftRight;

    private boolean mPieImeIsShowing = false;

    private final Object mScreenshotLock = new Object();
    private ServiceConnection mScreenshotConnection = null;

    // Should match the value in PhoneWindowManager
    public static final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";

    public static final int EXPANDED_LEAVE_ALONE = -10000;
    public static final int EXPANDED_FULL_OPEN = -10001;

    private static final boolean CLOSE_PANEL_WHEN_EMPTIED = true;
    private static final int COLLAPSE_AFTER_DISMISS_DELAY = 200;
    private static final int COLLAPSE_AFTER_REMOVE_DELAY = 400;

    protected CommandQueue mCommandQueue;
    protected IStatusBarService mBarService;
    protected H mHandler = createHandler();
    public TransparencyManager mTransparencyManager;

    // all notifications
    protected NotificationData mNotificationData = new NotificationData();
    protected NotificationRowLayout mPile;

    protected StatusBarNotification mCurrentlyIntrudingNotification;

    // used to notify status bar for suppressing notification LED
    protected boolean mPanelSlightlyVisible;

    // Search panel
    protected SearchPanelView mSearchPanelView;

    protected PopupMenu mNotificationBlamePopup;

    protected int mCurrentUserId = 0;

    protected FrameLayout mStatusBarContainer;

    private Runnable mPanelCollapseRunnable = new Runnable() {
        @Override
        public void run() {
            animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
        }
    };

    // App sidebar
    protected AppSidebar mAppSidebar;
    protected int mSidebarPosition;

    // Halo
    protected Halo mHalo = null;
    protected Ticker mTicker;
    protected boolean mHaloEnabled;
    protected boolean mHaloActive;
    protected boolean mHaloTaskerActive = false;
    protected ImageView mHaloButton;
    protected boolean mHaloButtonVisible = true;

    /**
     * An interface for navigation key bars to allow status bars to signal which keys are
     * currently of interest to the user.<br>
     * See {@link NavigationBarView} in Phone UI for an example.
     */
    public interface NavigationBarCallback {
        /**
         * @param hints flags from StatusBarManager (NAVIGATION_HINT...) to indicate which key is
         * available for navigation
         * @see StatusBarManager
         */
        public abstract void setNavigationIconHints(int hints);
        /**
         * @param showMenu {@code true} when an menu key should be displayed by the navigation bar.
         */
        public abstract void setMenuVisibility(boolean showMenu);
        /**
         * @param disabledFlags flags from View (STATUS_BAR_DISABLE_...) to indicate which key
         * is currently disabled on the navigation bar.
         * {@see View}
         */
        public void setDisabledFlags(int disabledFlags);
    };
    private ArrayList<NavigationBarCallback> mNavigationCallbacks =
            new ArrayList<NavigationBarCallback>();

    // Pie Control
    protected PieController mPieController;
    protected PieLayout mPieContainer;
    private int mPieTriggerSlots;
    public int mPieTriggerMask = Position.LEFT.FLAG
            | Position.BOTTOM.FLAG
            | Position.RIGHT.FLAG
            | Position.TOP.FLAG;
    private boolean mForceDisableBottomAndTopTrigger = false;
    private View[] mPieTrigger = new View[Position.values().length];
    private PieSettingsObserver mSettingsObserver;

    private View.OnTouchListener mPieTriggerOnTouchHandler = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            final int action = event.getAction();
            final PieController.Tracker tracker = (PieController.Tracker)v.getTag();

            if (tracker == null) {
                if (DEBUG_INPUT) {
                    Slog.v(TAG, "Pie trigger onTouch: action: " + action + ", ("
                            + event.getAxisValue(MotionEvent.AXIS_X) + ","
                            + event.getAxisValue(MotionEvent.AXIS_Y) + ") position: NULL returning: false");
                }
                return false;
            }

            if (!mPieController.isShowing()) {
                if (event.getPointerCount() > 1) {
                    if (DEBUG_INPUT) {
                        Slog.v(TAG, "Pie trigger onTouch: action: " + action
                                + ", (to many pointers) position: " + tracker.position.name()
                                + " returning: false");
                    }
                    return false;
                }

                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        tracker.start(event);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (tracker.move(event)) {
                            if (DEBUG) {
                                Slog.v(TAG, "Pie control activated on: ("
                                        + event.getAxisValue(MotionEvent.AXIS_X) + ","
                                        + event.getAxisValue(MotionEvent.AXIS_Y) + ") with position: "
                                        + tracker.position.name());
                            }
                            // set the snap points depending on current trigger and mask
                            mPieContainer.setSnapPoints(mPieTriggerMask & ~mPieTriggerSlots);
                            // send the activation to the controller
                            mPieController.activateFromTrigger(v, event, tracker.position);
                            // forward a spoofed ACTION_DOWN event
                            MotionEvent echo = event.copy();
                            echo.setAction(MotionEvent.ACTION_DOWN);
                            return mPieContainer.onTouch(v, echo);
                        }
                        break;
                    default:
                        // whatever it was, we are giving up on this one
                        tracker.active = false;
                        break;
                }
            } else {
                if (DEBUG_INPUT) {
                    Slog.v(TAG, "Pie trigger onTouch: action: " + action + ", ("
                            + event.getAxisValue(MotionEvent.AXIS_X) + ","
                            + event.getAxisValue(MotionEvent.AXIS_Y)
                            + ") position: " + tracker.position.name() + " delegating");
                }
                return mPieContainer.onTouch(v, event);
            }
            if (DEBUG_INPUT) {
                Slog.v(TAG, "Pie trigger onTouch: action: " + action + ", ("
                        + event.getAxisValue(MotionEvent.AXIS_X) + ","
                        + event.getAxisValue(MotionEvent.AXIS_Y) + ") position: "
                        + tracker.position.name() + " returning: " + tracker.active);
            }
            return tracker.active;
        }

    };

    protected int mLayoutDirection;
    private Locale mLocale;

    // UI-specific methods

    /**
     * Create all windows necessary for the status bar (including navigation, overlay panels, etc)
     * and add them to the window manager.
     */
    protected abstract void createAndAddWindows();

    protected WindowManager mWindowManager;
    protected IWindowManager mWindowManagerService;
    protected abstract void refreshLayout(int layoutDirection);

    protected Display mDisplay;

    private boolean mDeviceProvisioned = false;

    public IStatusBarService getService() {
        return mBarService;
    }

    public NotificationData getNotificationData() {
        return mNotificationData;
    } 

    protected ActiveDisplayView mActiveDisplayView;

    public IStatusBarService getStatusBarService() {
        return mBarService;
    }

    public boolean isDeviceProvisioned() {
        return mDeviceProvisioned;
    }

    public Ticker getTicker() {
        return mTicker;
    }

    private ContentObserver mProvisioningObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            final boolean provisioned = 0 != Settings.Global.getInt(
                    mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0);
            if (provisioned != mDeviceProvisioned) {
                mDeviceProvisioned = provisioned;
                updateNotificationIcons();
            }
        }
    };
        //0: normal; 1: never expand; 2: always expand; 3: revert to old
        int notificationsBehaviour = 0;
        private ContentObserver SettingsObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            notificationsBehaviour = Settings.System.getInt(
                    mContext.getContentResolver(), Settings.System.NOTIFICATIONS_BEHAVIOUR, 0);
        }
    };

    private RemoteViews.OnClickHandler mOnClickHandler = new RemoteViews.OnClickHandler() {
        @Override
        public boolean onClickHandler(View view, PendingIntent pendingIntent, Intent fillInIntent) {
            if (DEBUG) {
                Slog.v(TAG, "Notification click handler invoked for intent: " + pendingIntent);
            }
            final boolean isActivity = pendingIntent.isActivity();
            if (isActivity) {
                try {
                    // The intent we are sending is for the application, which
                    // won't have permission to immediately start an activity after
                    // the user switches to home.  We know it is safe to do at this
                    // point, so make sure new activity switches are now allowed.
                    ActivityManagerNative.getDefault().resumeAppSwitches();
                    // Also, notifications can be launched from the lock screen,
                    // so dismiss the lock screen when the activity starts.
                    ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
                } catch (RemoteException e) {
                }
            }

            boolean handled = super.onClickHandler(view, pendingIntent, fillInIntent);

            if (isActivity && handled) {
                // close the shade if it was open
                animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
                visibilityChanged(false);
            }
            return handled;
        }
    };

    public void start() {
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mWindowManagerService = WindowManagerGlobal.getWindowManagerService();
        mDisplay = mWindowManager.getDefaultDisplay();

        mProvisioningObserver.onChange(false); // set up
        SettingsObserver.onChange(false);
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED), true,
                mProvisioningObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.NOTIFICATIONS_BEHAVIOUR), true,
                SettingsObserver);

        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));

        mStatusBarContainer = new FrameLayout(mContext);

        mLocale = mContext.getResources().getConfiguration().locale;
        mLayoutDirection = TextUtils.getLayoutDirectionFromLocale(mLocale);

        // Connect in to the status bar manager service
        StatusBarIconList iconList = new StatusBarIconList();
        ArrayList<IBinder> notificationKeys = new ArrayList<IBinder>();
        ArrayList<StatusBarNotification> notifications = new ArrayList<StatusBarNotification>();
        mCommandQueue = new CommandQueue(this, iconList);

        int[] switches = new int[7];
        ArrayList<IBinder> binders = new ArrayList<IBinder>();
        try {
            mBarService.registerStatusBar(mCommandQueue, iconList, notificationKeys, notifications,
                    switches, binders);
        } catch (RemoteException ex) {
            // If the system process isn't there we're doomed anyway.
        }

        mHaloEnabled = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HALO_ENABLED, 0) == 1;
        mHaloActive = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HALO_ACTIVE, 0) == 1;

        if (mTransparencyManager == null) {
            mTransparencyManager = new TransparencyManager(mContext);
        }

        mWidgetView = new WidgetView(mContext,null);

        createAndAddWindows();

        disable(switches[0]);
        setSystemUiVisibility(switches[1], 0xffffffff);
        topAppWindowChanged(switches[2] != 0);
        // StatusBarManagerService has a back up of IME token and it's restored here.
        setImeWindowStatus(binders.get(0), switches[3], switches[4]);
        setHardKeyboardStatus(switches[5] != 0, switches[6] != 0);

        // Set up the initial icon state
        int N = iconList.size();
        int viewIndex = 0;
        for (int i=0; i<N; i++) {
            StatusBarIcon icon = iconList.getIcon(i);
            if (icon != null) {
                addIcon(iconList.getSlot(i), i, viewIndex, icon);
                viewIndex++;
            }
        }

        // Set up the initial notification state
        N = notificationKeys.size();
        if (N == notifications.size()) {
            for (int i=0; i<N; i++) {
                addNotification(notificationKeys.get(i), notifications.get(i));
            }
        } else {
            Log.wtf(TAG, "Notification list length mismatch: keys=" + N
                    + " notifications=" + notifications.size());
        }

        if (DEBUG) {
            Slog.d(TAG, String.format(
                    "init: icons=%d disabled=0x%08x lights=0x%08x menu=0x%08x imeButton=0x%08x",
                   iconList.size(),
                   switches[0],
                   switches[1],
                   switches[2],
                   switches[3]
                   ));
        }

        mCurrentUserId = ActivityManager.getCurrentUser();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                    mCurrentUserId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                    if (true) Slog.v(TAG, "userId " + mCurrentUserId + " is in the house");
                    userSwitched(mCurrentUserId);
                }
            }
        }, filter);

        mSettingsObserver = new PieSettingsObserver(new Handler());

	SidebarObserver observer = new SidebarObserver(mHandler);
        observer.observe();

        // this calls attachPie() implicitly
        mSettingsObserver.onChange(true);
        mSettingsObserver.observe();

        // Listen for HALO enabled switch
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.HALO_ENABLED), false, new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                updateHalo();
            }});

        // Listen for HALO state  
        mContext.getContentResolver().registerContentObserver(  
                Settings.System.getUriFor(Settings.System.HALO_ACTIVE), false, new ContentObserver(new Handler()) {  
            @Override  
            public void onChange(boolean selfChange) {  
                updateHalo();  
            }});  
  
        updateHalo(); 

	    mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.HALO_SIZE), false, new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {                
                restartHalo();
            }});

            updateHalo();
    }

    public void setHaloTaskerActive(boolean haloTaskerActive, boolean updateNotificationIcons) {
        mHaloTaskerActive = haloTaskerActive;
        if (updateNotificationIcons) {
            updateNotificationIcons();
        }
    }

    protected void updateHaloButton() {
        if (!mHaloEnabled) {
            mHaloButtonVisible = false;
        } else {
            mHaloButtonVisible = true;
        }
        if (mHaloButton != null) {
            mHaloButton.setVisibility(mHaloButtonVisible && !mHaloActive ? View.VISIBLE : View.GONE);
        }
    }

    public void restartHalo() {
        if (mHalo != null) {
            mHalo.cleanUp();
            mWindowManager.removeView(mHalo);
            mHalo = null;
        }
        updateNotificationIcons();
        updateHalo();
    }

    public void updateHalo() {
        mHaloEnabled = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HALO_ENABLED, 0) == 1;
        mHaloActive = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HALO_ACTIVE, 0) == 1;

        updateHaloButton();

        if (!mHaloEnabled) {
            mHaloActive = false;
        }

        if (mHaloActive) {
            if (mHalo == null) {
                LayoutInflater inflater = (LayoutInflater) mContext
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE); 
                mHalo = (Halo)inflater.inflate(R.layout.halo_trigger, null);
                mHalo.setLayerType (View.LAYER_TYPE_HARDWARE, null);
                WindowManager.LayoutParams params = mHalo.getWMParams();
                mWindowManager.addView(mHalo,params);
                mHalo.setStatusBar(this);
            }
        } else {
            if (mHalo != null) {
                mHalo.cleanUp();
                mWindowManager.removeView(mHalo);
                mHalo = null;
            }
        } 
        
        // this calls attachPie() implicitly
        mSettingsObserver.onChange(true);
        mSettingsObserver.observe();

        mLocale = mContext.getResources().getConfiguration().locale;
    }

    public void userSwitched(int newUserId) {
        // should be overridden
    }

    public boolean notificationIsForCurrentUser(StatusBarNotification n) {
        final int thisUserId = mCurrentUserId;
        final int notificationUserId = n.getUserId();
        if (DEBUG && MULTIUSER_DEBUG) {
            Slog.v(TAG, String.format("%s: current userid: %d, notification userid: %d",
                    n, thisUserId, notificationUserId));
        }
        return notificationUserId == UserHandle.USER_ALL
                || thisUserId == notificationUserId;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        final Locale newLocale = mContext.getResources().getConfiguration().locale;
        if (! newLocale.equals(mLocale)) {
            mLocale = newLocale;
            mLayoutDirection = TextUtils.getLayoutDirectionFromLocale(mLocale);
            refreshLayout(mLayoutDirection);
        }

        if (DEBUG) Slog.d(TAG, "Configuration changed! Update pie triggers");
        attachPie();
    }

    protected View updateNotificationVetoButton(View row, StatusBarNotification n) {
        View vetoButton = row.findViewById(R.id.veto);
        if (n.isClearable()) {
            final String _pkg = n.getPackageName();
            final String _tag = n.getTag();
            final int _id = n.getId();
            vetoButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        // Accessibility feedback
                        v.announceForAccessibility(
                                mContext.getString(R.string.accessibility_notification_dismissed));
                        try {
                            mBarService.onNotificationClear(_pkg, _tag, _id);

                        } catch (RemoteException ex) {
                            // system process is dead if we're here.
                        }
                    }
                });
            vetoButton.setVisibility(View.VISIBLE);
        } else {
            vetoButton.setVisibility(View.GONE);
        }
        vetoButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return vetoButton;
    }


    protected void applyLegacyRowBackground(StatusBarNotification sbn, View content) {
        if (sbn.getNotification().contentView.getLayoutId() !=
                com.android.internal.R.layout.notification_template_base) {
            int version = 0;
            try {
                ApplicationInfo info = mContext.getPackageManager().getApplicationInfo(sbn.getPackageName(), 0);
                version = info.targetSdkVersion;
            } catch (NameNotFoundException ex) {
                Slog.e(TAG, "Failed looking up ApplicationInfo for " + sbn.getPackageName(), ex);
            }
            if (version > 0 && version < Build.VERSION_CODES.GINGERBREAD) {
                content.setBackgroundResource(R.drawable.notification_row_legacy_bg);
            } else {
                content.setBackgroundResource(com.android.internal.R.drawable.notification_bg);
            }
        }
    }

    private void startApplicationDetailsActivity(String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null));
        intent.setComponent(intent.resolveActivity(mContext.getPackageManager()));
        TaskStackBuilder.create(mContext).addNextIntentWithParentStack(intent).startActivities(
                null, UserHandle.CURRENT);
    }

    private void launchFloating(PendingIntent pIntent) {
        Intent overlay = new Intent();
        overlay.addFlags(Intent.FLAG_FLOATING_WINDOW | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            ActivityManagerNative.getDefault().resumeAppSwitches();
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
        }
        try {
            pIntent.send(mContext, 0, overlay);
        } catch (PendingIntent.CanceledException e) {
            // the stack trace isn't very helpful here.  Just log the exception message.
            Slog.w(TAG, "Sending contentIntent failed: " + e);
        }
    }

    protected View.OnLongClickListener getNotificationLongClicker() {
        return new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                NotificationData.Entry  entry = (NotificationData.Entry) v.getTag();
                StatusBarNotification sbn = entry.notification;

                final String packageNameF = sbn.getPackageName();
                final PendingIntent contentIntent = sbn.getNotification().contentIntent;
                boolean expanded = Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.EXPANDED_DESKTOP_STATE, 0) == 1;

                if (packageNameF == null) return false;
                if (v.getWindowToken() == null) return false;

                //Long click menu broken on PIE mode...pop up menu is useless (auto-launch on long click)
                if (expanded) launchFloating(contentIntent);

                mNotificationBlamePopup = new PopupMenu(mContext, v);
                mNotificationBlamePopup.getMenuInflater().inflate(
                        R.menu.notification_popup_menu,
                        mNotificationBlamePopup.getMenu());
                mNotificationBlamePopup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == R.id.notification_inspect_item) {
                            startApplicationDetailsActivity(packageNameF);
                            animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
                        } else if (item.getItemId() == R.id.notification_floating_item) {
                            launchFloating(contentIntent);
                            animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
                        } else {
                            return false;
                        }
                        return true;
                    }
                });
                mNotificationBlamePopup.show();

                return true;
            }
        };
    }

    public void dismissPopups() {
        if (mNotificationBlamePopup != null) {
            mNotificationBlamePopup.dismiss();
            mNotificationBlamePopup = null;
        }
    }

    public void dismissIntruder() {
        // pass
    }

    @Override
    public void toggleRecentApps() {
        int msg = MSG_TOGGLE_RECENTS_PANEL;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void toggleWidgets() {
        int msg = MSG_TOGGLE_WIDGETS;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void toggleScreenshot() {
        int msg = MSG_TOGGLE_SCREENSHOT;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void toggleLastApp() {
        int msg = MSG_TOGGLE_LAST_APP;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void toggleKillApp() {
        int msg = MSG_TOGGLE_KILL_APP;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void preloadRecentApps() {
        int msg = MSG_PRELOAD_RECENT_APPS;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void cancelPreloadRecentApps() {
        int msg = MSG_CANCEL_PRELOAD_RECENT_APPS;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void showSearchPanel() {
        int msg = MSG_OPEN_SEARCH_PANEL;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void hideSearchPanel() {
        int msg = MSG_CLOSE_SEARCH_PANEL;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    protected abstract WindowManager.LayoutParams getRecentsLayoutParams(
            LayoutParams layoutParams);

    protected abstract WindowManager.LayoutParams getSearchLayoutParams(
            LayoutParams layoutParams);

    protected void updateSearchPanel() {
        // Search Panel
        boolean visible = false;
        if (mSearchPanelView != null) {
            visible = mSearchPanelView.isShowing();
            mWindowManager.removeView(mSearchPanelView);
        }

        // Provide SearchPanel with a temporary parent to allow layout params to work.
        LinearLayout tmpRoot = new LinearLayout(mContext);

         boolean navbarCanMove = Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.NAVIGATION_BAR_CAN_MOVE, 1, UserHandle.USER_CURRENT) == 1;

         if (screenLayout() != Configuration.SCREENLAYOUT_SIZE_LARGE && !isScreenPortrait() && !navbarCanMove) {
                mSearchPanelView = (SearchPanelView) LayoutInflater.from(mContext).inflate(
                         R.layout.status_bar_search_panel_real_landscape, tmpRoot, false);
         } else {
                mSearchPanelView = (SearchPanelView) LayoutInflater.from(mContext).inflate(
                         R.layout.status_bar_search_panel, tmpRoot, false);
         }

        mSearchPanelView.setOnTouchListener(
                 new TouchOutsideListener(MSG_CLOSE_SEARCH_PANEL, mSearchPanelView));
        mSearchPanelView.setVisibility(View.GONE);

        WindowManager.LayoutParams lp = getSearchLayoutParams(mSearchPanelView.getLayoutParams());

        mWindowManager.addView(mSearchPanelView, lp);
        mSearchPanelView.setBar(this);
        if (visible) {
            mSearchPanelView.show(true, false);
        }
    }

    protected H createHandler() {
         return new H();
    }

    static int screenLayout() {
        final int screenSize = Resources.getSystem().getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_MASK;
        return screenSize;
    }

    protected boolean isScreenPortrait() {
        return mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    static void sendCloseSystemWindows(Context context, String reason) {
        if (ActivityManagerNative.isSystemReady()) {
            try {
                ActivityManagerNative.getDefault().closeSystemDialogs(reason);
            } catch (RemoteException e) {
            }
        }
    }

    protected abstract View getStatusBarView();

    protected void toggleRecentsActivity() {
        try {

            TaskDescription firstTask = RecentTasksLoader.getInstance(mContext).getFirstTask();

            Intent intent = new Intent(RecentsActivity.TOGGLE_RECENTS_INTENT);
            intent.setClassName("com.android.systemui",
                    "com.android.systemui.recent.RecentsActivity");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

            if (firstTask == null) {
                if (RecentsActivity.forceOpaqueBackground(mContext)) {
                    ActivityOptions opts = ActivityOptions.makeCustomAnimation(mContext,
                            R.anim.recents_launch_from_launcher_enter,
                            R.anim.recents_launch_from_launcher_exit);
                    mContext.startActivityAsUser(intent, opts.toBundle(), new UserHandle(
                            UserHandle.USER_CURRENT));
                } else {
                    // The correct window animation will be applied via the activity's style
                    mContext.startActivityAsUser(intent, new UserHandle(
                            UserHandle.USER_CURRENT));
                }

            } else {
                Bitmap first = firstTask.getThumbnail();
                final Resources res = mContext.getResources();

                float thumbWidth = res
                        .getDimensionPixelSize(R.dimen.status_bar_recents_thumbnail_width);
                float thumbHeight = res
                        .getDimensionPixelSize(R.dimen.status_bar_recents_thumbnail_height);
                if (first == null) {
                    throw new RuntimeException("Recents thumbnail is null");
                }
                if (first.getWidth() != thumbWidth || first.getHeight() != thumbHeight) {
                    first = Bitmap.createScaledBitmap(first, (int) thumbWidth, (int) thumbHeight,
                            true);
                    if (first == null) {
                        throw new RuntimeException("Recents thumbnail is null");
                    }
                }


                DisplayMetrics dm = new DisplayMetrics();
                mDisplay.getMetrics(dm);
                // calculate it here, but consider moving it elsewhere
                // first, determine which orientation you're in.
                // todo: move the system_bar layouts to sw600dp ?
                final Configuration config = res.getConfiguration();
                int x, y;

                if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    float appLabelLeftMargin = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_app_label_left_margin);
                    float appLabelWidth = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_app_label_width);
                    float thumbLeftMargin = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_thumbnail_left_margin);
                    float thumbBgPadding = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_thumbnail_bg_padding);

                    float width = appLabelLeftMargin +
                            +appLabelWidth
                            + thumbLeftMargin
                            + thumbWidth
                            + 2 * thumbBgPadding;

                    x = (int) ((dm.widthPixels - width) / 2f + appLabelLeftMargin + appLabelWidth
                            + thumbBgPadding + thumbLeftMargin);
                    y = (int) (dm.heightPixels
                            - res.getDimensionPixelSize(R.dimen.status_bar_recents_thumbnail_height) - thumbBgPadding);
                    if (mLayoutDirection == View.LAYOUT_DIRECTION_RTL) {
                        x = dm.widthPixels - x - res
                                .getDimensionPixelSize(R.dimen.status_bar_recents_thumbnail_width);
                    }

                } else { // if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    float thumbTopMargin = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_thumbnail_top_margin);
                    float thumbBgPadding = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_thumbnail_bg_padding);
                    float textPadding = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_text_description_padding);
                    float labelTextSize = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_app_label_text_size);
                    Paint p = new Paint();
                    p.setTextSize(labelTextSize);
                    float labelTextHeight = p.getFontMetricsInt().bottom
                            - p.getFontMetricsInt().top;
                    float descriptionTextSize = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_app_description_text_size);
                    p.setTextSize(descriptionTextSize);
                    float descriptionTextHeight = p.getFontMetricsInt().bottom
                            - p.getFontMetricsInt().top;

                    float statusBarHeight = res
                            .getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height);
                    float recentsItemTopPadding = statusBarHeight;

                    float height = thumbTopMargin
                            + thumbHeight
                            + 2 * thumbBgPadding + textPadding + labelTextHeight
                            + recentsItemTopPadding + textPadding + descriptionTextHeight;
                    float recentsItemRightPadding = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_item_padding);
                    float recentsScrollViewRightPadding = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_right_glow_margin);
                    x = (int) (dm.widthPixels - res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_thumbnail_width)
                            - thumbBgPadding - recentsItemRightPadding - recentsScrollViewRightPadding);
                    y = (int) ((dm.heightPixels - statusBarHeight - height) / 2f + thumbTopMargin
                            + recentsItemTopPadding + thumbBgPadding + statusBarHeight);
                }

                ActivityOptions opts = ActivityOptions.makeThumbnailScaleDownAnimation(
                        getStatusBarView(),
                        first, x, y,
                        new ActivityOptions.OnAnimationStartedListener() {
                            public void onAnimationStarted() {
                                Intent intent = new Intent(RecentsActivity.WINDOW_ANIMATION_START_INTENT);
                                intent.setPackage("com.android.systemui");
                                mContext.sendBroadcastAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
                            }
                        });
                intent.putExtra(RecentsActivity.WAITING_FOR_WINDOW_ANIMATION_PARAM, true);
                mContext.startActivityAsUser(intent, opts.toBundle(), new UserHandle(
                        UserHandle.USER_CURRENT));
            }
            return;
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Failed to launch RecentAppsIntent", e);
        }
    }

    protected View.OnTouchListener mRecentsPreloadOnTouchListener = new View.OnTouchListener() {
        // additional optimization when we have software system buttons - start loading the recent
        // tasks on touch down
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction() & MotionEvent.ACTION_MASK;
            if (action == MotionEvent.ACTION_DOWN) {
                preloadRecentTasksList();
            } else if (action == MotionEvent.ACTION_CANCEL) {
                cancelPreloadingRecentTasksList();
            } else if (action == MotionEvent.ACTION_UP) {
                if (!v.isPressed()) {
                    cancelPreloadingRecentTasksList();
                }

            }
            return false;
        }
    };

    protected void preloadRecentTasksList() {
        if (DEBUG) Slog.d(TAG, "preloading recents");
        Intent intent = new Intent(RecentsActivity.PRELOAD_INTENT);
        intent.setClassName("com.android.systemui",
                "com.android.systemui.recent.RecentsPreloadReceiver");
        mContext.sendBroadcastAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));

        RecentTasksLoader.getInstance(mContext).preloadFirstTask();
    }

    protected void cancelPreloadingRecentTasksList() {
        if (DEBUG) Slog.d(TAG, "cancel preloading recents");
        Intent intent = new Intent(RecentsActivity.CANCEL_PRELOAD_INTENT);
        intent.setClassName("com.android.systemui",
                "com.android.systemui.recent.RecentsPreloadReceiver");
        mContext.sendBroadcastAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));

        RecentTasksLoader.getInstance(mContext).cancelPreloadingFirstTask();
    }

    protected class H extends Handler {
        public void handleMessage(Message m) {
            Intent intent;
            switch (m.what) {
             case MSG_TOGGLE_RECENTS_PANEL:
                 if (DEBUG) Slog.d(TAG, "toggle recents panel");
                // additional optimization when we get an outside call
                // for recents app switcher - start loading the recent
                // tasks first before toggle the recentsactvity
                 Timer timer = new Timer();
                 preloadRecentTasksList();
                 timer.schedule( new TimerTask(){
                     public void run() {
                         cancelPreloadingRecentTasksList();
                         toggleRecentsActivity();
                     }
                 }, 50);
                 break;
             case MSG_CLOSE_RECENTS_PANEL:
                 if (DEBUG) Slog.d(TAG, "closing recents panel");
                 intent = new Intent(RecentsActivity.CLOSE_RECENTS_INTENT);
                 intent.setPackage("com.android.systemui");
                 mContext.sendBroadcastAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
                 break;
             case MSG_PRELOAD_RECENT_APPS:
                  preloadRecentTasksList();
                  break;
             case MSG_CANCEL_PRELOAD_RECENT_APPS:
                  cancelPreloadingRecentTasksList();
                  break;
             case MSG_TOGGLE_WIDGETS:
                 if (DEBUG) Slog.d(TAG, "toggle navbar widgets");
                 Intent toggleWidgets = new Intent(
                 WidgetView.WidgetReceiver.ACTION_TOGGLE_WIDGETS);
                 mContext.sendBroadcast(toggleWidgets);
                 break;
             case MSG_TOGGLE_SCREENSHOT:
                 if (DEBUG) Slog.d(TAG, "toggle screenshot");
                 takeScreenshot();
                 break;
             case MSG_TOGGLE_LAST_APP:
                 if (DEBUG) Slog.d(TAG, "toggle last app");
                 getLastApp();
                 break;
             case MSG_TOGGLE_KILL_APP:
                 if (DEBUG) Slog.d(TAG, "toggle kill app");
                 mHandler.post(mKillTask);
                 break;
             case MSG_OPEN_SEARCH_PANEL:
                 if (DEBUG) Slog.d(TAG, "opening search panel");
                 if (mSearchPanelView != null) {
                     mSearchPanelView.show(true, true);

                     View bottom = mPieTrigger[Position.BOTTOM.INDEX];
                     if (bottom != null) {
                         WindowManager.LayoutParams lp =
                                 (android.view.WindowManager.LayoutParams) bottom.getLayoutParams();
                         lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                         lp.flags |= WindowManager.LayoutParams.FLAG_SLIPPERY;
                         mWindowManager.updateViewLayout(bottom, lp);
                     }
                 }
                 break;
             case MSG_CLOSE_SEARCH_PANEL:
                 if (DEBUG) Slog.d(TAG, "closing search panel");
                 if (mSearchPanelView != null) {
                     mSearchPanelView.show(false, true);

                     View bottom = mPieTrigger[Position.BOTTOM.INDEX];
                     if (bottom != null) {
                         WindowManager.LayoutParams lp =
                                 (android.view.WindowManager.LayoutParams) bottom.getLayoutParams();
                         lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                         lp.flags &= ~WindowManager.LayoutParams.FLAG_SLIPPERY;
                         mWindowManager.updateViewLayout(bottom, lp);
                     }
                 }
                 break;
            }
        }
    }

    public class TouchOutsideListener implements View.OnTouchListener {
        private int mMsg;
        private StatusBarPanel mPanel;

        public TouchOutsideListener(int msg, StatusBarPanel panel) {
            mMsg = msg;
            mPanel = panel;
        }

        public boolean onTouch(View v, MotionEvent ev) {
            final int action = ev.getAction();
            if (action == MotionEvent.ACTION_OUTSIDE
                || (action == MotionEvent.ACTION_DOWN
                    && !mPanel.isInContentArea((int)ev.getX(), (int)ev.getY()))) {
                mHandler.removeMessages(mMsg);
                mHandler.sendEmptyMessage(mMsg);
                return true;
            }
            return false;
        }
    }

    protected void workAroundBadLayerDrawableOpacity(View v) {
    }

    protected  boolean inflateViews(NotificationData.Entry entry, ViewGroup parent) {
        int minHeight =
                mContext.getResources().getDimensionPixelSize(R.dimen.notification_min_height);
        int maxHeight =
                mContext.getResources().getDimensionPixelSize(R.dimen.notification_max_height);
        StatusBarNotification sbn = entry.notification;
        RemoteViews oneU = sbn.getNotification().contentView;
        RemoteViews large = sbn.getNotification().bigContentView;
        if (oneU == null) {
            return false;
        }

        // create the row view
        LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        View row = inflater.inflate(R.layout.status_bar_notification_row, parent, false);

        // for blaming (see SwipeHelper.setLongPressListener)
        row.setTag(entry);

        workAroundBadLayerDrawableOpacity(row);
        View vetoButton = updateNotificationVetoButton(row, sbn);
        vetoButton.setContentDescription(mContext.getString(
                R.string.accessibility_remove_notification));

        // NB: the large icon is now handled entirely by the template

        // bind the click event to the content area
        ViewGroup content = (ViewGroup)row.findViewById(R.id.content);
        ViewGroup adaptive = (ViewGroup)row.findViewById(R.id.adaptive);

        content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        PendingIntent contentIntent = sbn.getNotification().contentIntent;
        if (contentIntent != null) {
            final View.OnClickListener listener = new NotificationClicker(contentIntent,
                    sbn.getPackageName(), sbn.getTag(), sbn.getId());
            content.setOnClickListener(listener);
        } else {
            content.setOnClickListener(null);
        }

        // TODO(cwren) normalize variable names with those in updateNotification
        View expandedOneU = null;
        View expandedLarge = null;
        try {
            expandedOneU = oneU.apply(mContext, adaptive, mOnClickHandler);
            if (large != null) {
                expandedLarge = large.apply(mContext, adaptive, mOnClickHandler);
            }
        }
        catch (RuntimeException e) {
            final String ident = sbn.getPackageName() + "/0x" + Integer.toHexString(sbn.getId());
            Slog.e(TAG, "couldn't inflate view for notification " + ident, e);
            return false;
        }

        if (expandedOneU != null) {
            SizeAdaptiveLayout.LayoutParams params =
                    new SizeAdaptiveLayout.LayoutParams(expandedOneU.getLayoutParams());
            params.minHeight = minHeight;
            params.maxHeight = minHeight;
            adaptive.addView(expandedOneU, params);
        }
        if (expandedLarge != null && notificationsBehaviour != 3) {
            SizeAdaptiveLayout.LayoutParams params =
                    new SizeAdaptiveLayout.LayoutParams(expandedLarge.getLayoutParams());
            params.minHeight = minHeight+1;
            params.maxHeight = maxHeight;
            adaptive.addView(expandedLarge, params);
        }
        row.setDrawingCacheEnabled(true);

        applyLegacyRowBackground(sbn, content);

        row.setTag(R.id.expandable_tag, Boolean.valueOf(large != null));

        if (MULTIUSER_DEBUG) {
            TextView debug = (TextView) row.findViewById(R.id.debug_info);
            if (debug != null) {
                debug.setVisibility(View.VISIBLE);
                debug.setText("U " + entry.notification.getUserId());
            }
        }
        entry.row = row;
        entry.content = content;
        entry.expanded = expandedOneU;
        if (notificationsBehaviour != 3) {
            entry.setLargeView(expandedLarge);
        }
        return true;
    }

    public NotificationClicker makeClicker(PendingIntent intent, String pkg, String tag, int id) {
        return new NotificationClicker(intent, pkg, tag, id);
    }

    public class NotificationClicker implements View.OnClickListener {
        public PendingIntent mIntent;
        public String mPkg;
        public String mTag;
        public int mId;
        public boolean mFloat;

        NotificationClicker(PendingIntent intent, String pkg, String tag, int id) {
            mIntent = intent;
            mPkg = pkg;
            mTag = tag;
            mId = id;
        }

        public void makeFloating(boolean floating) {
            mFloat = floating;
        }

        public void onClick(View v) {
            try {
                // The intent we are sending is for the application, which
                // won't have permission to immediately start an activity after
                // the user switches to home.  We know it is safe to do at this
                // point, so make sure new activity switches are now allowed.
                ActivityManagerNative.getDefault().resumeAppSwitches();
                // Also, notifications can be launched from the lock screen,
                // so dismiss the lock screen when the activity starts.
                ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
            } catch (RemoteException e) {
            }

            if (mIntent != null) {

                if (mFloat && !"android".equals(mPkg) && !"com.paranoid.halo".equals(mPkg)) {
                    Intent transparent = new Intent(mContext, com.android.systemui.Transparent.class);
                    transparent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_FLOATING_WINDOW);
                    mContext.startActivity(transparent);
                }

                int[] pos = new int[2];
                v.getLocationOnScreen(pos);
                Intent overlay = new Intent();
                if (mFloat) overlay.addFlags(Intent.FLAG_FLOATING_WINDOW | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                overlay.setSourceBounds(
                        new Rect(pos[0], pos[1], pos[0]+v.getWidth(), pos[1]+v.getHeight()));
                try {
                    mIntent.send(mContext, 0, overlay);
                } catch (PendingIntent.CanceledException e) {
                    // the stack trace isn't very helpful here.  Just log the exception message.
                    Slog.w(TAG, "Sending contentIntent failed: " + e);
                }
            }

            try {
                mBarService.onNotificationClick(mPkg, mTag, mId);
            } catch (RemoteException ex) {
                // system process is dead if we're here.
            }

            // close the shade if it was open
            animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
            visibilityChanged(false);

            // If this click was on the intruder alert, hide that instead
//            mHandler.sendEmptyMessage(MSG_HIDE_INTRUDER);
        }
    }
    /**
     * The LEDs are turned o)ff when the notification panel is shown, even just a little bit.
     * This was added last-minute and is inconsistent with the way the rest of the notifications
     * are handled, because the notification isn't really cancelled.  The lights are just
     * turned off.  If any other notifications happen, the lights will turn back on.  Steve says
     * this is what he wants. (see bug 1131461)
     */
    protected void visibilityChanged(boolean visible) {
        if (mPanelSlightlyVisible != visible) {
            mPanelSlightlyVisible = visible;
            try {
                mBarService.onPanelRevealed();
            } catch (RemoteException ex) {
                // Won't fail unless the world has ended.
            }
        }
    }

    /**
     * Cancel this notification and tell the StatusBarManagerService / NotificationManagerService
     * about the failure.
     *
     * WARNING: this will call back into us.  Don't hold any locks.
     */
    void handleNotificationError(IBinder key, StatusBarNotification n, String message) {
        removeNotification(key);
        try {
            mBarService.onNotificationError(n.getPackageName(), n.getTag(), n.getId(), n.getUid(), n.getInitialPid(), message);
        } catch (RemoteException ex) {
            // The end is nigh.
        }
    }

    protected StatusBarNotification removeNotificationViews(IBinder key) {
        NotificationData.Entry entry = mNotificationData.remove(key);
        if (entry == null) {
            Slog.w(TAG, "removeNotification for unknown key: " + key);
            return null;
        }
        // Remove the expanded view.
        ViewGroup rowParent = (ViewGroup)entry.row.getParent();
        if (rowParent != null) rowParent.removeView(entry.row);
        updateExpansionStates();
        updateNotificationIcons();

        if (CLOSE_PANEL_WHEN_EMPTIED && isNotificationPanelFullyVisible()) {
            if (entry.userDismissed() && !mNotificationData.hasClearableItems()) {
                mHandler.removeCallbacks(mPanelCollapseRunnable);
                mHandler.postDelayed(mPanelCollapseRunnable, COLLAPSE_AFTER_DISMISS_DELAY);
            } else if (mNotificationData.size() == 0) {
                mHandler.removeCallbacks(mPanelCollapseRunnable);
                mHandler.postDelayed(mPanelCollapseRunnable, COLLAPSE_AFTER_REMOVE_DELAY);
            }
        }

        return entry.notification;
    }

    public void prepareHaloNotification(NotificationData.Entry entry, StatusBarNotification notification, boolean update) {

	Notification notif = notification.getNotification();

	// Get the remote view
        try {

            if (!update) {
                ViewGroup mainView = (ViewGroup)notif.contentView.apply(mContext, null, mOnClickHandler);

                if (mainView instanceof FrameLayout) {
                    entry.haloContent = mainView.getChildAt(1);
                    mainView.removeViewAt(1);
                } else {
                    entry.haloContent = mainView;
                }
            } else {
                notif.contentView.reapply(mContext, entry.haloContent, mOnClickHandler);
            }

        } catch (Exception e) {
            // Non uniform content?
            android.util.Log.d("PARANOID", "   Non uniform content?");
        } 

        // Construct the round icon
        final float haloSize = Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.HALO_SIZE, 1.0f);
        int iconSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_bubble_size) * haloSize);
        int smallIconSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.status_bar_icon_size) * haloSize);
        int largeIconWidth = notif.largeIcon != null ? (int)(notif.largeIcon.getWidth() * haloSize) : 0;
        int largeIconHeight = notif.largeIcon != null ? (int)(notif.largeIcon.getHeight() * haloSize) : 0;        
        Bitmap roundIcon = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(roundIcon);
        canvas.drawARGB(0, 0, 0, 0);

        // If we have a bona fide avatar here stretching at least over half the size of our
        // halo-bubble, we'll use that one and cut it round
        if (notif.largeIcon != null && largeIconWidth >= iconSize / 2) {          
            Paint smoothingPaint = new Paint();
            smoothingPaint.setAntiAlias(true);
            smoothingPaint.setFilterBitmap(true);
            smoothingPaint.setDither(true);
            canvas.drawCircle(iconSize / 2, iconSize / 2, iconSize / 2.3f, smoothingPaint);
            smoothingPaint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));

            final int newWidth = iconSize;
            final int newHeight = iconSize * largeIconWidth / largeIconHeight;
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(notif.largeIcon, newWidth, newHeight, true);
            canvas.drawBitmap(scaledBitmap, null, new Rect(0, 0,
                    iconSize, iconSize), smoothingPaint);
        } else {
            try {
                Drawable icon = StatusBarIconView.getIcon(mContext,
                    new StatusBarIcon(notification.getPackageName(), notification.getUser(), notif.icon,
                    notif.iconLevel, 0, notif.tickerText)); 
                if (icon == null) icon = mContext.getPackageManager().getApplicationIcon(notification.getPackageName());
                int margin = (iconSize - smallIconSize) / 2;
                icon.setBounds(margin, margin, iconSize - margin, iconSize - margin);
                icon.draw(canvas);
            } catch (Exception e) {
                // NameNotFoundException
            }
        }        
        entry.roundIcon = roundIcon; 
    }

    protected StatusBarIconView addNotificationViews(IBinder key,
            StatusBarNotification notification) {
        if (DEBUG) {
            Slog.d(TAG, "addNotificationViews(key=" + key + ", notification=" + notification);
        }
        // Construct the icon.
        final StatusBarIconView iconView = new StatusBarIconView(mContext,
                notification.getPackageName() + "/0x" + Integer.toHexString(notification.getId()),
                notification.getNotification());
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        final StatusBarIcon ic = new StatusBarIcon(notification.getPackageName(),
                notification.getUser(),
                    notification.getNotification().icon,
                    notification.getNotification().iconLevel,
                    notification.getNotification().number,
                    notification.getNotification().tickerText);
        if (!iconView.set(ic)) {
            handleNotificationError(key, notification, "Couldn't create icon: " + ic);
            return null;
        }

	NotificationData.Entry entry = new NotificationData.Entry(key, notification, iconView);
        prepareHaloNotification(entry, notification, false); 
        entry.hide = entry.notification.getPackageName().equals("com.paranoid.halo");

        final PendingIntent contentIntent = notification.getNotification().contentIntent;
        if (contentIntent != null) {
            entry.floatingIntent = makeClicker(contentIntent,
                    notification.getPackageName(), notification.getTag(), notification.getId());
            entry.floatingIntent.makeFloating(true);
        }

        // Construct the expanded view.
        if (!inflateViews(entry, mPile)) {
            handleNotificationError(key, notification, "Couldn't expand RemoteViews for: "
                    + notification);
            return null;
        }

        // Add the expanded view and icon.
        int pos = mNotificationData.add(entry);
        if (DEBUG) {
            Slog.d(TAG, "addNotificationViews: added at " + pos);
        }
        updateExpansionStates();
        updateNotificationIcons();
        mHandler.removeCallbacks(mPanelCollapseRunnable);

        return iconView;
    }

    protected boolean expandView(NotificationData.Entry entry, boolean expand) {
        int rowHeight = mContext.getResources().getDimensionPixelSize(R.dimen.notification_row_min_height);
        ViewGroup.LayoutParams lp = entry.row.getLayoutParams();
        if (entry.expandable() && notificationsBehaviour != 3 && notificationsBehaviour != 1
                && (expand || notificationsBehaviour == 2)) {
            if (DEBUG) Slog.d(TAG, "setting expanded row height to WRAP_CONTENT");
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        } else {
            if (DEBUG) Slog.d(TAG, "setting collapsed row height to " + rowHeight);
            lp.height = rowHeight;
        }
        entry.row.setLayoutParams(lp);
        if (entry.hide) entry.row.setVisibility(View.GONE);
        return expand;
    }

    protected void updateExpansionStates() {
        int N = mNotificationData.size();
        for (int i = 0; i < N; i++) {
            NotificationData.Entry entry = mNotificationData.get(i);
            if (!entry.userLocked()) {
                if (i == (N-1)) {
                    if (DEBUG) Slog.d(TAG, "expanding top notification at " + i);
                    expandView(entry, true);
                } else {
                    if (!entry.userExpanded()) {
                        if (DEBUG) Slog.d(TAG, "collapsing notification at " + i);
                        expandView(entry, false);
                    } else {
                        if (DEBUG) Slog.d(TAG, "ignoring user-modified notification at " + i);
                    }
                }
            } else {
                if (DEBUG) Slog.d(TAG, "ignoring notification being held by user at " + i);
            }
        }
    }

    protected abstract void haltTicker();
    protected abstract void setAreThereNotifications();
    protected abstract void updateNotificationIcons();
    protected abstract void tick(IBinder key, StatusBarNotification n, boolean firstTime);
    protected abstract void updateExpandedViewPos(int expandedPosition);
    protected abstract int getExpandedViewMaxHeight();
    protected abstract boolean isNotificationPanelFullyVisible();
    protected abstract boolean shouldDisableNavbarGestures();

    protected boolean isTopNotification(ViewGroup parent, NotificationData.Entry entry) {
        return parent != null && parent.indexOfChild(entry.row) == 0;
    }

    public void updateNotification(IBinder key, StatusBarNotification notification) {
        if (DEBUG) Slog.d(TAG, "updateNotification(" + key + " -> " + notification + ")");

        final NotificationData.Entry oldEntry = mNotificationData.findByKey(key);
        if (oldEntry == null) {
            Slog.w(TAG, "updateNotification for unknown key: " + key);
            return;
        }

        final StatusBarNotification oldNotification = oldEntry.notification;

        // XXX: modify when we do something more intelligent with the two content views
        final RemoteViews oldContentView = oldNotification.getNotification().contentView;
        final RemoteViews contentView = notification.getNotification().contentView;
        final RemoteViews oldBigContentView = oldNotification.getNotification().bigContentView;
        final RemoteViews bigContentView = notification.getNotification().bigContentView;

        if (DEBUG) {
            Slog.d(TAG, "old notification: when=" + oldNotification.getNotification().when
                    + " ongoing=" + oldNotification.isOngoing()
                    + " expanded=" + oldEntry.expanded
                    + " contentView=" + oldContentView
                    + " bigContentView=" + oldBigContentView
                    + " rowParent=" + oldEntry.row.getParent());
            Slog.d(TAG, "new notification: when=" + notification.getNotification().when
                    + " ongoing=" + oldNotification.isOngoing()
                    + " contentView=" + contentView
                    + " bigContentView=" + bigContentView);
        }

        // Can we just reapply the RemoteViews in place?  If when didn't change, the order
        // didn't change.

        // 1U is never null
        boolean contentsUnchanged = oldEntry.expanded != null
                && contentView.getPackage() != null
                && oldContentView.getPackage() != null
                && oldContentView.getPackage().equals(contentView.getPackage())
                && oldContentView.getLayoutId() == contentView.getLayoutId();
        // large view may be null
        boolean bigContentsUnchanged =
                (oldEntry.getLargeView() == null && bigContentView == null)
                || ((oldEntry.getLargeView() != null && bigContentView != null)
                    && bigContentView.getPackage() != null
                    && oldBigContentView.getPackage() != null
                    && oldBigContentView.getPackage().equals(bigContentView.getPackage())
                    && oldBigContentView.getLayoutId() == bigContentView.getLayoutId());
        ViewGroup rowParent = (ViewGroup) oldEntry.row.getParent();
        boolean orderUnchanged = notification.getNotification().when== oldNotification.getNotification().when
                && notification.getScore() == oldNotification.getScore();
                // score now encompasses/supersedes isOngoing()

        boolean updateTicker = notification.getNotification().tickerText != null
                && !TextUtils.equals(notification.getNotification().tickerText,
                        oldEntry.notification.getNotification().tickerText) ||
                mHaloActive;
 
        boolean isTopAnyway = isTopNotification(rowParent, oldEntry);
        if (contentsUnchanged && bigContentsUnchanged && (orderUnchanged || isTopAnyway)) {
            if (DEBUG) Slog.d(TAG, "reusing notification for key: " + key);
            oldEntry.notification = notification;
            try {
                // Reapply the RemoteViews
                contentView.reapply(mContext, oldEntry.expanded, mOnClickHandler);
                if (bigContentView != null && oldEntry.getLargeView() != null) {
                    bigContentView.reapply(mContext, oldEntry.getLargeView(), mOnClickHandler);
                }
                // update the contentIntent and floatingIntent
                final PendingIntent contentIntent = notification.getNotification().contentIntent;

                if (contentIntent != null) {
                    final View.OnClickListener listener = makeClicker(contentIntent,
                            notification.getPackageName(), notification.getTag(), notification.getId());
                    oldEntry.content.setOnClickListener(listener);
                    oldEntry.floatingIntent = makeClicker(contentIntent,
                            notification.getPackageName(), notification.getTag(), notification.getId());
                    oldEntry.floatingIntent.makeFloating(true);
                } else {
                    oldEntry.content.setOnClickListener(null);
                    oldEntry.floatingIntent = null;
                }
		// Update the roundIcon
                prepareHaloNotification(oldEntry, notification, true);  
                // Update the icon.
                final StatusBarIcon ic = new StatusBarIcon(notification.getPackageName(),
                        notification.getUser(),
                        notification.getNotification().icon, notification.getNotification().iconLevel,
                        notification.getNotification().number,
                        notification.getNotification().tickerText);
                if (!oldEntry.icon.set(ic)) {
                    handleNotificationError(key, notification, "Couldn't update icon: " + ic);
                    return;
                }
                updateExpansionStates();
            }
            catch (RuntimeException e) {
                // It failed to add cleanly.  Log, and remove the view from the panel.
                Slog.w(TAG, "Couldn't reapply views for package " + contentView.getPackage(), e);
                removeNotificationViews(key);
                addNotificationViews(key, notification);
            }
        } else {
            if (DEBUG) Slog.d(TAG, "not reusing notification for key: " + key);
            if (DEBUG) Slog.d(TAG, "contents was " + (contentsUnchanged ? "unchanged" : "changed"));
            if (DEBUG) Slog.d(TAG, "order was " + (orderUnchanged ? "unchanged" : "changed"));
            if (DEBUG) Slog.d(TAG, "notification is " + (isTopAnyway ? "top" : "not top"));
            final boolean wasExpanded = oldEntry.userExpanded();
            removeNotificationViews(key);
            addNotificationViews(key, notification);
            if (wasExpanded) {
                final NotificationData.Entry newEntry = mNotificationData.findByKey(key);
                expandView(newEntry, true);
                newEntry.setUserExpanded(true);
            }
        }

        // Update the veto button accordingly (and as a result, whether this row is
        // swipe-dismissable)
        updateNotificationVetoButton(oldEntry.row, notification);

        // Is this for you?
        boolean isForCurrentUser = notificationIsForCurrentUser(notification);
        if (DEBUG) Slog.d(TAG, "notification is " + (isForCurrentUser ? "" : "not ") + "for you");

        // Restart the ticker if it's still running
        if (updateTicker && isForCurrentUser) {
            haltTicker();
            tick(key, notification, false);
        }

        // Recalculate the position of the sliding windows and the titles.
        setAreThereNotifications();
        updateExpandedViewPos(EXPANDED_LEAVE_ALONE);

        // See if we need to update the intruder.
        if (ENABLE_INTRUDERS && oldNotification == mCurrentlyIntrudingNotification) {
            if (DEBUG) Slog.d(TAG, "updating the current intruder:" + notification);
            // XXX: this is a hack for Alarms. The real implementation will need to *update*
            // the intruder.
            if (notification.getNotification().fullScreenIntent == null) { // TODO(dsandler): consistent logic with add()
                if (DEBUG) Slog.d(TAG, "no longer intrudes!");
                mHandler.sendEmptyMessage(MSG_HIDE_INTRUDER);
            }
        }

	// Update halo
        if (mHalo != null) mHalo.update(); 
    }

    // Q: What kinds of notifications should show during setup?
    // A: Almost none! Only things coming from the system (package is "android") that also
    // have special "kind" tags marking them as relevant for setup (see below).
    protected boolean showNotificationEvenIfUnprovisioned(StatusBarNotification sbn) {
        if ("android".equals(sbn.getPackageName())) {
            if (sbn.getNotification().kind != null) {
                for (String aKind : sbn.getNotification().kind) {
                    // IME switcher, created by InputMethodManagerService
                    if ("android.system.imeswitcher".equals(aKind)) return true;
                    // OTA availability & errors, created by SystemUpdateService
                    if ("android.system.update".equals(aKind)) return true;
                }
            }
        }
        return false;
    }

    public boolean inKeyguardRestrictedInputMode() {
        KeyguardManager km = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        return km.inKeyguardRestrictedInputMode();
    }

    class SidebarObserver extends ContentObserver {
        SidebarObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.APP_SIDEBAR_POSITION), false, this);
            update();
        }

        @Override
       public void onChange(boolean selfChange) {
            update();
        }

        public void update() {
            ContentResolver resolver = mContext.getContentResolver();
            int sidebarPosition = Settings.System.getInt(
                    resolver, Settings.System.APP_SIDEBAR_POSITION, AppSidebar.SIDEBAR_POSITION_LEFT);
            if (sidebarPosition != mSidebarPosition) {
                mSidebarPosition = sidebarPosition;
               mWindowManager.updateViewLayout(mAppSidebar, getAppSidebarLayoutParams(sidebarPosition));
            }
        }
    }

    protected void addSidebarView() {
        mAppSidebar = (AppSidebar)View.inflate(mContext, R.layout.app_sidebar, null);
        mWindowManager.addView(mAppSidebar, getAppSidebarLayoutParams(mSidebarPosition));
    }

    protected void removeSidebarView() {
        if (mAppSidebar != null)
            mWindowManager.removeView(mAppSidebar);
    }

    protected WindowManager.LayoutParams getAppSidebarLayoutParams(int position) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL,
                0
                | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP;// | Gravity.FILL_VERTICAL;
        lp.gravity |= position == AppSidebar.SIDEBAR_POSITION_LEFT ? Gravity.LEFT : Gravity.RIGHT;
        lp.setTitle("AppSidebar");

        return lp;
    }

    public void addNavigationBarCallback(NavigationBarCallback callback) {
        mNavigationCallbacks.add(callback);
    }

    protected void propagateNavigationIconHints(int hints) {
        for (NavigationBarCallback callback : mNavigationCallbacks) {
            callback.setNavigationIconHints(hints);
        }
    }

    protected void propagateMenuVisibility(boolean showMenu) {
        for (NavigationBarCallback callback : mNavigationCallbacks) {
            callback.setMenuVisibility(showMenu);
        }
    }

    protected void propagateDisabledFlags(int disabledFlags) {
        for (NavigationBarCallback callback : mNavigationCallbacks) {
            callback.setDisabledFlags(disabledFlags);
        }
    }

    // Pie Controls

    private final class PieSettingsObserver extends ContentObserver {
        PieSettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_CONTROLS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_GRAVITY), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_TRIGGER_GRAVITY_LEFT_RIGHT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_TRIGGER_THICKNESS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_TRIGGER_HEIGHT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_TRIGGER_SHOW), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_SOFTKEYBOARD_IS_SHOWING), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.EXPANDED_DESKTOP_STATE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.EXPANDED_DESKTOP_MODE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_BAR_SHOW), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_BAR_CAN_MOVE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_BAR_HEIGHT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_BAR_HEIGHT_LANDSCAPE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_BAR_WIDTH), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HIDE_STATUSBAR), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            mPieTriggerSlots = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.PIE_GRAVITY, Position.LEFT.FLAG);
            mPieShowTrigger = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.PIE_TRIGGER_SHOW, 0) == 1;
            mPieTriggerThickness = Settings.System.getFloat(mContext.getContentResolver(),
                    Settings.System.PIE_TRIGGER_THICKNESS,
                    mContext.getResources().getDimension(R.dimen.pie_trigger_thickness));
            mPieTriggerHeight = Settings.System.getFloat(mContext.getContentResolver(),
                    Settings.System.PIE_TRIGGER_HEIGHT,
                    0.7f);
            mPieTriggerGravityLeftRight = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.PIE_TRIGGER_GRAVITY_LEFT_RIGHT,
                    Gravity.CENTER_VERTICAL);
            mPieImeIsShowing = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.PIE_SOFTKEYBOARD_IS_SHOWING, 0) == 1
                    && Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.PIE_ADJUST_TRIGGER_FOR_IME, 1) == 1;
            attachPie();
        }
    }

    private boolean isPieEnabled() {
        boolean expanded = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.EXPANDED_DESKTOP_STATE, 0) == 1;
        int pie = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_CONTROLS, 0);

        return (pie == 1 && expanded) || pie == 2;
    }

    private void attachPie() {
        if (isPieEnabled()) {
            // Create our container, if it does not exist already
            if (mPieContainer == null) {
                mPieContainer = new PieLayout(mContext, mTransparencyManager);
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        PixelFormat.TRANSLUCENT);
                // This title is for debugging only. See: dumpsys window
                lp.setTitle("PieControlPanel");
                lp.windowAnimations = android.R.style.Animation;
                lp.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_BEHIND;

                mWindowManager.addView(mPieContainer, lp);
                // once we need a pie controller, we create one and keep it forever ...
                if (mPieController == null) {
                    mPieController = new PieController(mContext);
                    mPieController.attachStatusBar(this);
                    addNavigationBarCallback(mPieController);
                }
                mPieController.attachContainer(mPieContainer);
            }

            // add or update pie triggers
            setupTriggers(false);
            refreshPieTriggers();

            if (DEBUG) {
                Slog.d(TAG, "AttachPie with trigger position flags: "
                        + mPieTriggerSlots + " masked: " + (mPieTriggerSlots & mPieTriggerMask));
            }

        } else {
            for (int i = 0; i < mPieTrigger.length; i++) {
                if (mPieTrigger[i] != null) {
                    mWindowManager.removeView(mPieTrigger[i]);
                    mPieTrigger[i] = null;
                }
            }
            // detach from the pie container and unregister observers and receivers
            if (mPieController != null) {
                mPieController.detachContainer();
                mPieContainer = null;
            }
        }
    }

    public void keyguardTriggers(boolean forceBottomTrigger) {
        if (isPieEnabled()) {
            mForceBottomTrigger = forceBottomTrigger;
            setupTriggers(false);
        }
    }

    public void recreatePie() {
        if (isPieEnabled()) {
            mPieController.constructSlices();
        }
    }

    public void setupTriggers(boolean forceDisableBottomAndTopTrigger) {
            if (!isPieEnabled()) {
                return;
            }
            boolean bottomTriggerEnabled = false;
            boolean topTriggerEnabled = false;
            boolean leftTriggerEnabled = false;
            boolean rightTriggerEnabled = false;

            // get expanded desktop values
            int expandedMode = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.EXPANDED_DESKTOP_MODE, 0);
            boolean expanded = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.EXPANDED_DESKTOP_STATE, 0) == 1;

            // get statusbar auto hide value
            boolean autoHideStatusBar = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.HIDE_STATUSBAR, 0) == 1;

            // get navigation bar values
            final int showByDefault = mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_showNavigationBar) ? 1 : 0;
            boolean hasNavigationBar = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.NAVIGATION_BAR_SHOW, showByDefault) == 1;
            boolean navBarCanMove = Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.NAVIGATION_BAR_CAN_MOVE, 1, UserHandle.USER_CURRENT) == 1
                        && screenLayout() != Configuration.SCREENLAYOUT_SIZE_LARGE
                        && screenLayout() != Configuration.SCREENLAYOUT_SIZE_XLARGE;
            boolean navigationBarHeight = Settings.System.getInt(mContext.getContentResolver(),
                                Settings.System.NAVIGATION_BAR_HEIGHT,
                                mContext.getResources().getDimensionPixelSize(
                                                com.android.internal.R.dimen.navigation_bar_height)) != 0;
            boolean navigationBarHeightLandscape = Settings.System.getInt(mContext.getContentResolver(),
                                Settings.System.NAVIGATION_BAR_HEIGHT_LANDSCAPE,
                                mContext.getResources().getDimensionPixelSize(
                                                com.android.internal.R.dimen.navigation_bar_height_landscape)) != 0;
            boolean navigationBarWidth = Settings.System.getInt(mContext.getContentResolver(),
                                Settings.System.NAVIGATION_BAR_WIDTH,
                                mContext.getResources().getDimensionPixelSize(
                                                com.android.internal.R.dimen.navigation_bar_width)) != 0;

            // disable on phones in landscape right trigger for navbar
            boolean disableRightTriggerForNavbar =
                    !isScreenPortrait()
                    && hasNavigationBar
                    && ((expandedMode == 2 && expanded) || !expanded)
                    && navBarCanMove
                    && navigationBarWidth;

            // take in account the navbar dimensions
            hasNavigationBar = (hasNavigationBar && isScreenPortrait() && navigationBarHeight)
                                || (hasNavigationBar && !isScreenPortrait() && !navBarCanMove
                                    && navigationBarHeightLandscape);

            // let's set the triggers
            if (!forceDisableBottomAndTopTrigger && (mForceBottomTrigger
                    && (!hasNavigationBar
                        || disableRightTriggerForNavbar
                        || (expandedMode == 1 || expandedMode == 3) && expanded))) {
                bottomTriggerEnabled = true;
            } else if (mForceBottomTrigger && hasNavigationBar) {
                //do nothing all triggers are disabled and exit
            } else if ((!expanded && hasNavigationBar && !autoHideStatusBar)
                || forceDisableBottomAndTopTrigger) {
                leftTriggerEnabled = true;
                rightTriggerEnabled = true;
            } else if ((!expanded && !hasNavigationBar && !autoHideStatusBar)
                || (expandedMode == 1 && expanded && !autoHideStatusBar)) {
                leftTriggerEnabled = true;
                rightTriggerEnabled = true;
                bottomTriggerEnabled = true;
            } else if (expandedMode == 2 && expanded && hasNavigationBar
                        || !expanded && hasNavigationBar && autoHideStatusBar) {
                leftTriggerEnabled = true;
                rightTriggerEnabled = true;
                topTriggerEnabled = true;
            } else {
                leftTriggerEnabled = true;
                rightTriggerEnabled = true;
                bottomTriggerEnabled = true;
                topTriggerEnabled = true;
            }
            if (disableRightTriggerForNavbar) {
                    rightTriggerEnabled = false;
            }
            if (mPieImeIsShowing) {
                    bottomTriggerEnabled = false;
            }

            int newMask;
            newMask  = leftTriggerEnabled ? Position.LEFT.FLAG : 0;
            newMask |= bottomTriggerEnabled ? Position.BOTTOM.FLAG : 0;
            newMask |= rightTriggerEnabled ? Position.RIGHT.FLAG : 0;
            newMask |= topTriggerEnabled ? Position.TOP.FLAG : 0;

            updatePieTriggerMask(newMask, forceDisableBottomAndTopTrigger);
    }

    private void updatePieTriggerMask(int newMask, boolean forceDisableBottomAndTopTrigger) {
        int oldState = mPieTriggerSlots & mPieTriggerMask;
        boolean oldForceDisableBottomAndTopTrigger = mForceDisableBottomAndTopTrigger;
        mPieTriggerMask = newMask;
        mForceDisableBottomAndTopTrigger = forceDisableBottomAndTopTrigger;

        // first we check, if it would make a change
        if ((mPieTriggerSlots & mPieTriggerMask) != oldState
                || mForceDisableBottomAndTopTrigger != oldForceDisableBottomAndTopTrigger) {
            refreshPieTriggers();
        }
    }

    // This should only be called, when is is clear that the pie controls are active
    private void refreshPieTriggers() {
        for (Position g : Position.values()) {
            View trigger = mPieTrigger[g.INDEX];
            if (trigger == null && (mPieTriggerSlots & mPieTriggerMask & g.FLAG) != 0) {
                trigger = new View(mContext);
                trigger.setClickable(false);
                trigger.setLongClickable(false);
                trigger.setTag(mPieController.buildTracker(g));
                trigger.setOnTouchListener(mPieTriggerOnTouchHandler);

                if (DEBUG) {
                    Slog.d(TAG, "addPieTrigger on " + g.INDEX
                            + " with position: " + g + " : " + trigger.toString());
                }
                showTrigger(trigger, DEBUG || mPieShowTrigger);
                mWindowManager.addView(trigger, getPieTriggerLayoutParams(g));
                mPieTrigger[g.INDEX] = trigger;
            } else if (trigger != null && (mPieTriggerSlots & mPieTriggerMask & g.FLAG) == 0) {
                mWindowManager.removeView(trigger);
                mPieTrigger[g.INDEX] = null;
            } else if (trigger != null) {
                showTrigger(trigger, DEBUG || mPieShowTrigger);
                mWindowManager.updateViewLayout(trigger, getPieTriggerLayoutParams(g));
            }
        }
    }

    private void showTrigger(View trigger, boolean show) {
        if (show) {
            trigger.setVisibility(View.VISIBLE);
            trigger.setBackgroundColor(0x77ff0000);
        } else {
            trigger.setBackgroundColor(0x00000000);
        }
    }

    private WindowManager.LayoutParams getPieTriggerLayoutParams(Position position) {
        final Resources res = mContext.getResources();

        float pieTriggerHeight = mPieTriggerHeight;
        final float pieImePortraitMinHeight = 0.52f;
        final float pieImeLandscapeMinHeight = 0.32f;
        if (mPieImeIsShowing && isScreenPortrait()
                && !mForceDisableBottomAndTopTrigger
                && pieTriggerHeight > pieImePortraitMinHeight) {
            pieTriggerHeight = pieImePortraitMinHeight;
        } else if (mPieImeIsShowing && !isScreenPortrait()
                && !mForceDisableBottomAndTopTrigger
                && pieTriggerHeight > pieImeLandscapeMinHeight) {
            pieTriggerHeight = pieImeLandscapeMinHeight;
        }

        int width = (int) (res.getDisplayMetrics().widthPixels * 0.9f);
        int height = (int) (res.getDisplayMetrics().heightPixels * pieTriggerHeight);
        int triggerThickness = (int) ((mPieTriggerThickness * res.getDisplayMetrics().density) + 0.5);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                (position == Position.TOP || position == Position.BOTTOM
                        ? width : triggerThickness),
                (position == Position.LEFT || position == Position.RIGHT
                        ? height : triggerThickness),
                WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
        // This title is for debugging only. See: dumpsys window
        lp.setTitle("PieTrigger" + position.name());
        if (position == Position.LEFT || position == Position.RIGHT) {
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        } else {
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        }
        if (mPieImeIsShowing && !mForceDisableBottomAndTopTrigger
                    && (position == Position.LEFT || position == Position.RIGHT)) {
            lp.gravity = position.ANDROID_GRAVITY | Gravity.TOP;
        } else if (position == Position.LEFT || position == Position.RIGHT) {
            lp.gravity = position.ANDROID_GRAVITY | mPieTriggerGravityLeftRight;
        } else {
            lp.gravity = position.ANDROID_GRAVITY;
        }
        return lp;
    }

    Runnable mKillTask = new Runnable() {
        public void run() {
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            String defaultHomePackage = "com.android.launcher";
            intent.addCategory(Intent.CATEGORY_HOME);
            final ResolveInfo res = mContext.getPackageManager().resolveActivity(intent, 0);
            if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
                defaultHomePackage = res.activityInfo.packageName;
            }
            boolean targetKilled = false;
            final ActivityManager am = (ActivityManager) mContext
                    .getSystemService(Activity.ACTIVITY_SERVICE);
            List<RunningAppProcessInfo> apps = am.getRunningAppProcesses();
            for (RunningAppProcessInfo appInfo : apps) {
                int uid = appInfo.uid;
                // Make sure it's a foreground user application (not system,
                // root, phone, etc.)
                if (uid >= Process.FIRST_APPLICATION_UID && uid <= Process.LAST_APPLICATION_UID
                        && appInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    if (appInfo.pkgList != null && (appInfo.pkgList.length > 0)) {
                        for (String pkg : appInfo.pkgList) {
                            if (!pkg.equals("com.android.systemui") && !pkg.equals(defaultHomePackage)) {
                                am.forceStopPackage(pkg);
                                targetKilled = true;
                                break;
                            }
                        }
                    } else {
                        Process.killProcess(appInfo.pid);
                        targetKilled = true;
                    }
                }
                if (targetKilled) {
                    Toast.makeText(mContext, R.string.app_killed_message, Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        }
    };

    final Runnable mScreenshotTimeout = new Runnable() {
        @Override
        public void run() {
            synchronized (mScreenshotLock) {
                if (mScreenshotConnection != null) {
                    mContext.unbindService(mScreenshotConnection);
                    mScreenshotConnection = null;
                }
            }
        }
    };

    private void takeScreenshot() {
        synchronized (mScreenshotLock) {
            if (mScreenshotConnection != null) {
                return;
            }
            ComponentName cn = new ComponentName("com.android.systemui",
                    "com.android.systemui.screenshot.TakeScreenshotService");
            Intent intent = new Intent();
            intent.setComponent(cn);
            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    synchronized (mScreenshotLock) {
                        if (mScreenshotConnection != this) {
                            return;
                        }
                        Messenger messenger = new Messenger(service);
                        Message msg = Message.obtain(null, 1);
                        final ServiceConnection myConn = this;
                        Handler h = new Handler(HDL.getLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                synchronized (mScreenshotLock) {
                                    if (mScreenshotConnection == myConn) {
                                        mContext.unbindService(mScreenshotConnection);
                                        mScreenshotConnection = null;
                                        HDL.removeCallbacks(mScreenshotTimeout);
                                    }
                                }
                            }
                        };
                        msg.replyTo = new Messenger(h);
                        msg.arg1 = msg.arg2 = 0;

                        /*
                         * remove for the time being if (mStatusBar != null &&
                         * mStatusBar.isVisibleLw()) msg.arg1 = 1; if
                         * (mNavigationBar != null &&
                         * mNavigationBar.isVisibleLw()) msg.arg2 = 1;
                         */

                        /* wait for the dialog box to close */
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                        }

                        /* take the screenshot */
                        try {
                            messenger.send(msg);
                        } catch (RemoteException e) {
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                }
            };
            if (mContext.bindService(intent, conn, mContext.BIND_AUTO_CREATE)) {
                mScreenshotConnection = conn;
                HDL.postDelayed(mScreenshotTimeout, 10000);
            }
        }
    }

    private void getLastApp() {
        int lastAppId = 0;
        int looper = 1;
        String packageName;
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        final ActivityManager am = (ActivityManager) mContext
                .getSystemService(Activity.ACTIVITY_SERVICE);
        String defaultHomePackage = "com.android.launcher";
        intent.addCategory(Intent.CATEGORY_HOME);
        final ResolveInfo res = mContext.getPackageManager().resolveActivity(intent, 0);
        if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
            defaultHomePackage = res.activityInfo.packageName;
        }
        List <ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(5);
        // lets get enough tasks to find something to switch to
        // Note, we'll only get as many as the system currently has - up to 5
        while ((lastAppId == 0) && (looper < tasks.size())) {
            packageName = tasks.get(looper).topActivity.getPackageName();
            if (!packageName.equals(defaultHomePackage) && !packageName.equals("com.android.systemui")) {
                lastAppId = tasks.get(looper).id;
            }
            looper++;
        }
        if (lastAppId != 0) {
            am.moveTaskToFront(lastAppId, am.MOVE_TASK_NO_USER_ACTION);
        }
    }

    private Handler HDL = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {

            }
        }
    };

    protected void addActiveDisplayView() {
        mActiveDisplayView = (ActiveDisplayView)View.inflate(mContext, R.layout.active_display, null);
        mWindowManager.addView(mActiveDisplayView, getActiveDisplayViewLayoutParams());
        mActiveDisplayView.setStatusBar(this);
    }

    protected void removeActiveDisplayView() {
        if (mActiveDisplayView != null)
            mWindowManager.removeView(mActiveDisplayView);
    }

    protected WindowManager.LayoutParams getActiveDisplayViewLayoutParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                0
                        | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.FILL_VERTICAL | Gravity.FILL_HORIZONTAL;
        lp.setTitle("ActiveDisplayView");

        return lp;
    }
}
