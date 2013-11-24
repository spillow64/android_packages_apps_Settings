/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.settings;


import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.security.KeyStore;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.util.BlacklistUtils;
import com.android.internal.util.slim.DeviceUtils;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Gesture lock pattern settings.
 */
public class SecuritySettings extends RestrictedSettingsFragment
        implements OnPreferenceChangeListener, DialogInterface.OnClickListener {
    static final String TAG = "SecuritySettings";

    // Lock Settings
    private static final String KEY_UNLOCK_SET_OR_CHANGE = "unlock_set_or_change";
    private static final String KEY_BIOMETRIC_WEAK_IMPROVE_MATCHING =
            "biometric_weak_improve_matching";
    private static final String KEY_BIOMETRIC_WEAK_LIVELINESS = "biometric_weak_liveliness";
    private static final String KEY_LOCK_ENABLED = "lockenabled";
    private static final String KEY_VISIBLE_PATTERN = "visiblepattern";
    private static final String KEY_SECURITY_CATEGORY = "security_category";
    private static final String KEY_DEVICE_ADMIN_CATEGORY = "device_admin_category";
    private static final String KEY_LOCK_AFTER_TIMEOUT = "lock_after_timeout";
    private static final String KEY_OWNER_INFO_SETTINGS = "owner_info_settings";
    private static final String KEY_LOCKSCREEN_ROTATION = "lockscreen_rotation";
    private static final String KEY_ALWAYS_BATTERY_PREF = "lockscreen_battery_status";
    private static final String KEY_ENABLE_WIDGETS = "keyguard_enable_widgets";
    private static final String KEY_INTERFACE_SETTINGS = "lock_screen_settings";
    private static final String KEY_TARGET_SETTINGS = "lockscreen_targets";
    private static final String KEY_SHAKE_EVENTS = "shake_events";
    private static final String LOCKSCREEN_QUICK_UNLOCK_CONTROL = "quick_unlock_control";
    private static final String LOCK_NUMPAD_RANDOM = "lock_numpad_random";
    private static final String KEY_SHAKE_TO_SECURE = "shake_to_secure_mode";
    private static final String KEY_SHAKE_AUTO_TIMEOUT = "shake_auto_timeout";
    private static final String LOCK_BEFORE_UNLOCK = "lock_before_unlock";
    private static final String KEY_ADVANCED_REBOOT = "advanced_reboot";
    private static final String MENU_UNLOCK_PREF = "menu_unlock";

    private static final int SET_OR_CHANGE_LOCK_METHOD_REQUEST = 123;
    private static final int CONFIRM_EXISTING_FOR_BIOMETRIC_WEAK_IMPROVE_REQUEST = 124;
    private static final int CONFIRM_EXISTING_FOR_BIOMETRIC_WEAK_LIVELINESS_OFF = 125;
    private static final int CONFIRM_EXISTING_FOR_TEMPORARY_INSECURE = 126;
    private static final int DLG_SHAKE_WARN = 0;

    // Masks for checking presence of hardware keys.
    // Must match values in frameworks/base/core/res/res/values/config.xml
    private static final int KEY_MASK_MENU = 0x04;

    // Misc Settings
    private static final String KEY_SIM_LOCK = "sim_lock";
    private static final String KEY_SHOW_PASSWORD = "show_password";
    private static final String KEY_CREDENTIAL_STORAGE_TYPE = "credential_storage_type";
    private static final String KEY_RESET_CREDENTIALS = "reset_credentials";
    private static final String KEY_CREDENTIALS_INSTALL = "credentials_install";
    private static final String KEY_TOGGLE_INSTALL_APPLICATIONS = "toggle_install_applications";
    private static final String KEY_TOGGLE_VERIFY_APPLICATIONS = "toggle_verify_applications";
    private static final String KEY_POWER_INSTANTLY_LOCKS = "power_button_instantly_locks";
    private static final String KEY_CREDENTIALS_MANAGER = "credentials_management";
    private static final String KEY_NOTIFICATION_ACCESS = "manage_notification_access";
    private static final String PACKAGE_MIME_TYPE = "application/vnd.android.package-archive";

    // C-RoM Additions
    private static final String KEY_APP_SECURITY_CATEGORY = "app_security";
    private static final String KEY_BLACKLIST = "blacklist";
    private static final String KEY_SMS_SECURITY_CHECK_PREF = "sms_security_check_limit";

    private PackageManager mPM;
    private DevicePolicyManager mDPM;

    private PreferenceGroup mSecurityCategory;

    private ChooseLockSettingsHelper mChooseLockSettingsHelper;
    private LockPatternUtils mLockPatternUtils;
    private ListPreference mLockAfter;

    private CheckBoxPreference mBiometricWeakLiveliness;
    private CheckBoxPreference mVisiblePattern;

    private CheckBoxPreference mShowPassword;

    private KeyStore mKeyStore;
    private Preference mResetCredentials;

    private CheckBoxPreference mToggleAppInstallation;
    private DialogInterface mWarnInstallApps;
    private CheckBoxPreference mToggleVerifyApps;
    private CheckBoxPreference mPowerButtonInstantlyLocks;
    private Preference mEnableKeyguardWidgets;
    private ListPreference mAdvancedReboot;

    private CheckBoxPreference mQuickUnlockScreen;
    private ListPreference mLockNumpadRandom;
    private ListPreference mShakeToSecure;
    private ListPreference mShakeTimer;
    private CheckBoxPreference mLockBeforeUnlock;
    private CheckBoxPreference mMenuUnlock;
    private ListPreference mLockscreenRotation;
    private CheckBoxPreference mBatteryStatus;

    private Preference mNotificationAccess;
    private Preference mLockInterface;
    private Preference mLockTargets;
    private Preference mShakeEvents;

    // needed for menu unlock
    private Resources keyguardResource;
    private boolean mMenuUnlockDefault;

    private int mShakeTypeChosen = -1;

    private boolean mIsPrimary;

    // CyanogenMod Additions
    private PreferenceScreen mBlacklist;
    private ListPreference mSmsSecurityCheck;

    public SecuritySettings() {
        super(null /* Don't ask for restrictions pin on creation. */);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mLockPatternUtils = new LockPatternUtils(getActivity());

        mPM = getActivity().getPackageManager();
        mDPM = (DevicePolicyManager)getSystemService(Context.DEVICE_POLICY_SERVICE);

        Resources keyguardResources = null;
        try {
            keyguardResources = mPM.getResourcesForApplication("com.android.keyguard");
        } catch (Exception e) {
            e.printStackTrace();
        }
        mMenuUnlockDefault = keyguardResources != null
            ? keyguardResources.getBoolean(keyguardResources.getIdentifier(
            "com.android.keyguard:bool/config_disableMenuKeyInLockScreen", null, null)) : false;

        mChooseLockSettingsHelper = new ChooseLockSettingsHelper(getActivity());
    }

    private PreferenceScreen createPreferenceHierarchy() {
        PreferenceScreen root = getPreferenceScreen();
        if (root != null) {
            root.removeAll();
        }
        addPreferencesFromResource(R.xml.security_settings);
        root = getPreferenceScreen();

        // Add package manager to check if features are available
        PackageManager pm = getPackageManager();

        // App security settings
        addPreferencesFromResource(R.xml.security_settings_app_crom);
        mBlacklist = (PreferenceScreen) root.findPreference(KEY_BLACKLIST);

        // Add options for lock/unlock screen
        int resid = 0;
        if (!mLockPatternUtils.isSecure()) {
            // if there are multiple users, disable "None" setting
            UserManager mUm = (UserManager) getSystemService(Context.USER_SERVICE);
            List<UserInfo> users = mUm.getUsers(true);
            final boolean singleUser = users.size() == 1;

            if (singleUser && mLockPatternUtils.isLockScreenDisabled()) {
                resid = R.xml.security_settings_lockscreen;
            } else {
                resid = R.xml.security_settings_chooser;
            }
        } else if (mLockPatternUtils.usingBiometricWeak() &&
                mLockPatternUtils.isBiometricWeakInstalled()) {
            resid = R.xml.security_settings_biometric_weak;
        } else {
            switch (mLockPatternUtils.getKeyguardStoredPasswordQuality()) {
                case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
                    resid = R.xml.security_settings_pattern;
                    break;
                case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
                    resid = R.xml.security_settings_pin;
                    break;
                case DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC:
                case DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC:
                case DevicePolicyManager.PASSWORD_QUALITY_COMPLEX:
                    resid = R.xml.security_settings_password;
                    break;
            }
        }
        addPreferencesFromResource(resid);

        // Add options for device encryption
        mIsPrimary = UserHandle.myUserId() == UserHandle.USER_OWNER;

        if (!mIsPrimary) {
            // Rename owner info settings
            Preference ownerInfoPref = findPreference(KEY_OWNER_INFO_SETTINGS);
            if (ownerInfoPref != null) {
                if (UserManager.get(getActivity()).isLinkedUser()) {
                    ownerInfoPref.setTitle(R.string.profile_info_settings_title);
                } else {
                    ownerInfoPref.setTitle(R.string.user_info_settings_title);
                }
            }
        }

        if (mIsPrimary) {
            switch (mDPM.getStorageEncryptionStatus()) {
            case DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE:
                // The device is currently encrypted.
                addPreferencesFromResource(R.xml.security_settings_encrypted);
                break;
            case DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE:
                // This device supports encryption but isn't encrypted.
                addPreferencesFromResource(R.xml.security_settings_unencrypted);
                break;
            }
        }

        // lock after preference
        mLockAfter = (ListPreference) root.findPreference(KEY_LOCK_AFTER_TIMEOUT);
        if (mLockAfter != null) {
            setupLockAfterPreference();
            updateLockAfterPreferenceSummary();
        }

        // biometric weak liveliness
        mBiometricWeakLiveliness =
                (CheckBoxPreference) root.findPreference(KEY_BIOMETRIC_WEAK_LIVELINESS);

        // visible pattern
        mVisiblePattern = (CheckBoxPreference) root.findPreference(KEY_VISIBLE_PATTERN);

        // lock instantly on power key press
        mPowerButtonInstantlyLocks = (CheckBoxPreference) root.findPreference(
                KEY_POWER_INSTANTLY_LOCKS);

        mSecurityCategory = (PreferenceGroup)
                root.findPreference(KEY_SECURITY_CATEGORY);
        if (mSecurityCategory != null) {
            mLockInterface = findPreference(KEY_INTERFACE_SETTINGS);
            mLockTargets = findPreference(KEY_TARGET_SETTINGS);
            mShakeEvents = findPreference(KEY_SHAKE_EVENTS);
            shouldEnableTargets();
        }

        // don't display visible pattern if biometric and backup is not pattern
        if (resid == R.xml.security_settings_biometric_weak &&
                mLockPatternUtils.getKeyguardStoredPasswordQuality() !=
                DevicePolicyManager.PASSWORD_QUALITY_SOMETHING) {
            if (mSecurityCategory != null && mVisiblePattern != null) {
                mSecurityCategory.removePreference(root.findPreference(KEY_VISIBLE_PATTERN));
            }
        }

        // Quick Unlock Screen Control
        mQuickUnlockScreen = (CheckBoxPreference) root
                .findPreference(LOCKSCREEN_QUICK_UNLOCK_CONTROL);
        if (mQuickUnlockScreen != null) {
            mQuickUnlockScreen.setChecked(Settings.System.getInt(getContentResolver(),
                    Settings.System.LOCKSCREEN_QUICK_UNLOCK_CONTROL, 0) == 1);
        }

        // Lock Numpad Random
        mLockNumpadRandom = (ListPreference) root.findPreference(LOCK_NUMPAD_RANDOM);
        if (mLockNumpadRandom != null) {
            mLockNumpadRandom.setValue(String.valueOf(
                    Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.LOCK_NUMPAD_RANDOM, 0)));
            mLockNumpadRandom.setSummary(mLockNumpadRandom.getEntry());
            mLockNumpadRandom.setOnPreferenceChangeListener(this);
        }

        // Shake to secure
        // Don't show if device admin requires security
        boolean shakeEnabled = mLockPatternUtils.getRequestedMinimumPasswordLength()
                == DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
        final int shakeSecure = Settings.Secure.getInt(
                getContentResolver(),
                Settings.Secure.LOCK_SHAKE_TEMP_SECURE, 0);
        mShakeToSecure = (ListPreference) root
                .findPreference(KEY_SHAKE_TO_SECURE);
        if (mShakeToSecure != null) {
            mShakeToSecure.setValue(String.valueOf(shakeSecure));
            mShakeToSecure.setOnPreferenceChangeListener(this);
            if (!shakeEnabled) {
                mSecurityCategory.removePreference(mShakeToSecure);
            }
        }

        mShakeTimer = (ListPreference) root.findPreference(KEY_SHAKE_AUTO_TIMEOUT);
        if (mShakeTimer != null) {
            long shakeTimer = Settings.Secure.getLongForUser(getContentResolver(),
                    Settings.Secure.LOCK_SHAKE_SECURE_TIMER, 0,
                    UserHandle.USER_CURRENT);
            mShakeTimer.setValue(String.valueOf(shakeTimer));
            updateShakeTimerPreferenceSummary();
            mShakeTimer.setOnPreferenceChangeListener(this);
            if (!shakeEnabled) {
                mSecurityCategory.removePreference(mShakeTimer);
            } else {
                mShakeTimer.setEnabled(shakeSecure != 0);
            }
        }

        // Lock before Unlock
        mLockBeforeUnlock = (CheckBoxPreference) root
                .findPreference(LOCK_BEFORE_UNLOCK);
        if (mLockBeforeUnlock != null) {
            mLockBeforeUnlock.setChecked(
                    Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.LOCK_BEFORE_UNLOCK, 0) == 1);
            mLockBeforeUnlock.setOnPreferenceChangeListener(this);
        }

        // Append the rest of the settings
        addPreferencesFromResource(R.xml.security_settings_misc);

        // Do not display SIM lock for devices without an Icc card
        TelephonyManager tm = TelephonyManager.getDefault();
        if (!mIsPrimary || !tm.hasIccCard()) {
            root.removePreference(root.findPreference(KEY_SIM_LOCK));
        } else {
            // Disable SIM lock if sim card is missing or unknown
            if ((TelephonyManager.getDefault().getSimState() ==
                                 TelephonyManager.SIM_STATE_ABSENT) ||
                (TelephonyManager.getDefault().getSimState() ==
                                 TelephonyManager.SIM_STATE_UNKNOWN)) {
                root.findPreference(KEY_SIM_LOCK).setEnabled(false);
            }
        }

        // Link to widget settings showing summary about the actual status
        // and remove them on low memory devices
        mEnableKeyguardWidgets = root.findPreference(KEY_ENABLE_WIDGETS);
        if (mEnableKeyguardWidgets != null) {
            if (ActivityManager.isLowRamDeviceStatic()
                    || mLockPatternUtils.isLockScreenDisabled()) {
                // Widgets take a lot of RAM, so disable them on low-memory devices
                if (mSecurityCategory != null) {
                    mSecurityCategory.removePreference(root.findPreference(KEY_ENABLE_WIDGETS));
                    mEnableKeyguardWidgets = null;
                }
            } else {
                final boolean disabled = (0 != (mDPM.getKeyguardDisabledFeatures(null)
                        & DevicePolicyManager.KEYGUARD_DISABLE_WIDGETS_ALL));
                if (disabled) {
                    mEnableKeyguardWidgets.setSummary(
                            R.string.security_enable_widgets_disabled_summary);
                }
                mEnableKeyguardWidgets.setEnabled(!disabled);
            }
        }

        mLockscreenRotation = (ListPreference) root.findPreference(KEY_LOCKSCREEN_ROTATION);
        if (mLockscreenRotation != null) {
            boolean defaultVal = !DeviceUtils.isPhone(getActivity());
            int userVal = Settings.System.getIntForUser(getContentResolver(),
                    Settings.System.LOCKSCREEN_ROTATION_ENABLED, defaultVal ? 1 : 0,
                    UserHandle.USER_CURRENT);
            mLockscreenRotation.setValue(String.valueOf(userVal));
            if (userVal == 0) {
                mLockscreenRotation.setSummary(mLockscreenRotation.getEntry());
            } else {
                mLockscreenRotation.setSummary(mLockscreenRotation.getEntry()
                        + " " + getResources().getString(
                        R.string.lockscreen_rotation_summary_extra));
            }
            mLockscreenRotation.setOnPreferenceChangeListener(this);
        }

        mBatteryStatus = (CheckBoxPreference) root.findPreference(KEY_ALWAYS_BATTERY_PREF);

        // Menu Unlock
        mMenuUnlock = (CheckBoxPreference) root.findPreference(MENU_UNLOCK_PREF);
        if (mMenuUnlock != null) {
            int deviceKeys = getResources().getInteger(
                    com.android.internal.R.integer.config_deviceHardwareKeys);
            boolean hasMenuKey = (deviceKeys & KEY_MASK_MENU) != 0;
            if (hasMenuKey) {
                boolean settingsEnabled = Settings.System.getIntForUser(
                        getContentResolver(),
                        Settings.System.MENU_UNLOCK_SCREEN, mMenuUnlockDefault ? 0 : 1,
                        UserHandle.USER_CURRENT) == 1;
                mMenuUnlock.setChecked(settingsEnabled);
                mMenuUnlock.setOnPreferenceChangeListener(this);
            } else {
                mSecurityCategory.removePreference(mMenuUnlock);
            }
        }

        // Show password
        mShowPassword = (CheckBoxPreference) root.findPreference(KEY_SHOW_PASSWORD);
        mResetCredentials = root.findPreference(KEY_RESET_CREDENTIALS);

        // Credential storage
        final UserManager um = (UserManager) getActivity().getSystemService(Context.USER_SERVICE);
        mKeyStore = KeyStore.getInstance(); // needs to be initialized for onResume()
        if (!um.hasUserRestriction(UserManager.DISALLOW_CONFIG_CREDENTIALS)) {
            Preference credentialStorageType = root.findPreference(KEY_CREDENTIAL_STORAGE_TYPE);

            final int storageSummaryRes =
                mKeyStore.isHardwareBacked() ? R.string.credential_storage_type_hardware
                        : R.string.credential_storage_type_software;
            credentialStorageType.setSummary(storageSummaryRes);

        } else {
            removePreference(KEY_CREDENTIALS_MANAGER);
        }

        // Application install
        PreferenceGroup deviceAdminCategory= (PreferenceGroup)
                root.findPreference(KEY_DEVICE_ADMIN_CATEGORY);
        mToggleAppInstallation = (CheckBoxPreference) findPreference(
                KEY_TOGGLE_INSTALL_APPLICATIONS);
        mToggleAppInstallation.setChecked(isNonMarketAppsAllowed());

        // Side loading of apps.
        mToggleAppInstallation.setEnabled(mIsPrimary);

        // Package verification, only visible to primary user and if enabled
        mToggleVerifyApps = (CheckBoxPreference) findPreference(KEY_TOGGLE_VERIFY_APPLICATIONS);
        if (mIsPrimary && showVerifierSetting()) {
            if (isVerifierInstalled()) {
                mToggleVerifyApps.setChecked(isVerifyAppsEnabled());
            } else {
                mToggleVerifyApps.setChecked(false);
                mToggleVerifyApps.setEnabled(false);
            }
        } else {
            if (deviceAdminCategory != null) {
                deviceAdminCategory.removePreference(mToggleVerifyApps);
            } else {
                mToggleVerifyApps.setEnabled(false);
            }
        }

        mAdvancedReboot = (ListPreference) root.findPreference(KEY_ADVANCED_REBOOT);
        mAdvancedReboot.setValue(String.valueOf(Settings.Secure.getInt(
                getContentResolver(), Settings.Secure.ADVANCED_REBOOT, 0)));
        mAdvancedReboot.setSummary(mAdvancedReboot.getEntry());
        mAdvancedReboot.setOnPreferenceChangeListener(this);

        mSmsSecurityCheck = (ListPreference) root.findPreference(KEY_SMS_SECURITY_CHECK_PREF);

        // Determine options based on device telephony support
        if (pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            mSmsSecurityCheck = (ListPreference) root.findPreference(KEY_SMS_SECURITY_CHECK_PREF);
            mSmsSecurityCheck.setOnPreferenceChangeListener(this);
            int smsSecurityCheck = Integer.valueOf(mSmsSecurityCheck.getValue());
            updateSmsSecuritySummary(smsSecurityCheck);
        } else {
            // No telephony, remove dependent options
            PreferenceGroup appCategory = (PreferenceGroup)
                    root.findPreference(KEY_APP_SECURITY_CATEGORY);
            appCategory.removePreference(mBlacklist);
            appCategory.removePreference(mSmsSecurityCheck);
        }

        mNotificationAccess = findPreference(KEY_NOTIFICATION_ACCESS);
        if (mNotificationAccess != null) {
            final int total = NotificationAccessSettings.getListenersCount(mPM);
            if (total == 0) {
                if (deviceAdminCategory != null) {
                    deviceAdminCategory.removePreference(mNotificationAccess);
                }
            } else {
                final int n = getNumEnabledNotificationListeners();
                if (n == 0) {
                    mNotificationAccess.setSummary(getResources().getString(
                            R.string.manage_notification_access_summary_zero));
                } else {
                    mNotificationAccess.setSummary(String.format(getResources().getQuantityString(
                            R.plurals.manage_notification_access_summary_nonzero,
                            n, n)));
                }
            }
        }

        if (shouldBePinProtected(RESTRICTIONS_PIN_SET)) {
            protectByRestrictions(mToggleAppInstallation);
            protectByRestrictions(mToggleVerifyApps);
            protectByRestrictions(mResetCredentials);
            protectByRestrictions(root.findPreference(KEY_CREDENTIALS_INSTALL));
        }
        return root;
    }

    private int getNumEnabledNotificationListeners() {
        final String flat = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_NOTIFICATION_LISTENERS);
        if (flat == null || "".equals(flat)) return 0;
        final String[] components = flat.split(":");
        return components.length;
    }

    private boolean isNonMarketAppsAllowed() {
        return Settings.Global.getInt(getContentResolver(),
                                      Settings.Global.INSTALL_NON_MARKET_APPS, 0) > 0;
    }

    private LockPatternUtils lockPatternUtils() {
        if (mLockPatternUtils == null) {
            mLockPatternUtils = new LockPatternUtils(getActivity());
        }
        return mLockPatternUtils;
    }

    private void shouldEnableTargets() {
        final int shakeSecureMode = Settings.Secure.getInt(
                getContentResolver(),
                Settings.Secure.LOCK_SHAKE_TEMP_SECURE, 0);
        final boolean shakeToSecure = shakeSecureMode != 0;
        final boolean lockBeforeUnlock = Settings.Secure.getInt(
                getContentResolver(),
                Settings.Secure.LOCK_BEFORE_UNLOCK, 0) == 1;

        final boolean shouldEnableTargets = (shakeToSecure || lockBeforeUnlock)
                || !lockPatternUtils().isSecure();
        if (mLockInterface != null && mLockTargets != null && mShakeEvents != null) {
            if (!DeviceUtils.isPhone(getActivity())) {
                // Nothing for tablets and large screen devices
                mSecurityCategory.removePreference(mLockInterface);
            } else {
                mSecurityCategory.removePreference(mLockTargets);
            }
            mShakeEvents.setEnabled(shakeSecureMode != 1 || !lockPatternUtils().isSecure());
            mLockInterface.setEnabled(shouldEnableTargets);
            mLockTargets.setEnabled(shouldEnableTargets);
        }
    }

    private void setNonMarketAppsAllowed(boolean enabled) {
        final UserManager um = (UserManager) getActivity().getSystemService(Context.USER_SERVICE);
        if (um.hasUserRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)) {
            return;
        }
        // Change the system setting
        Settings.Global.putInt(getContentResolver(), Settings.Global.INSTALL_NON_MARKET_APPS,
                                enabled ? 1 : 0);
    }

    private boolean isVerifyAppsEnabled() {
        return Settings.Global.getInt(getContentResolver(),
                                      Settings.Global.PACKAGE_VERIFIER_ENABLE, 1) > 0;
    }

    private boolean isVerifierInstalled() {
        final PackageManager pm = getPackageManager();
        final Intent verification = new Intent(Intent.ACTION_PACKAGE_NEEDS_VERIFICATION);
        verification.setType(PACKAGE_MIME_TYPE);
        verification.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        final List<ResolveInfo> receivers = pm.queryBroadcastReceivers(verification, 0);
        return (receivers.size() > 0) ? true : false;
    }

    private boolean showVerifierSetting() {
        return Settings.Global.getInt(getContentResolver(),
                                      Settings.Global.PACKAGE_VERIFIER_SETTING_VISIBLE, 1) > 0;
    }

    private void warnAppInstallation() {
        // TODO: DialogFragment?
        mWarnInstallApps = new AlertDialog.Builder(getActivity()).setTitle(
                getResources().getString(R.string.error_title))
                .setIcon(com.android.internal.R.drawable.ic_dialog_alert)
                .setMessage(getResources().getString(R.string.install_all_warning))
                .setPositiveButton(android.R.string.yes, this)
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (dialog == mWarnInstallApps && which == DialogInterface.BUTTON_POSITIVE) {
            setNonMarketAppsAllowed(true);
            if (mToggleAppInstallation != null) {
                mToggleAppInstallation.setChecked(true);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mWarnInstallApps != null) {
            mWarnInstallApps.dismiss();
        }
    }

    private void updateSmsSecuritySummary(int selection) {
        String message = getString(R.string.sms_security_check_limit_summary, selection);
        mSmsSecurityCheck.setSummary(message);
    }

    private void setupLockAfterPreference() {
        // Compatible with pre-Froyo
        long currentTimeout = Settings.Secure.getLong(getContentResolver(),
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT, 5000);
        mLockAfter.setValue(String.valueOf(currentTimeout));
        mLockAfter.setOnPreferenceChangeListener(this);
        final long adminTimeout = (mDPM != null ? mDPM.getMaximumTimeToLock(null) : 0);
        final long displayTimeout = Math.max(0,
                Settings.System.getInt(getContentResolver(), SCREEN_OFF_TIMEOUT, 0));
        if (adminTimeout > 0) {
            // This setting is a slave to display timeout when a device policy is enforced.
            // As such, maxLockTimeout = adminTimeout - displayTimeout.
            // If there isn't enough time, shows "immediately" setting.
            disableUnusableTimeouts(Math.max(0, adminTimeout - displayTimeout));
        }
    }

    private void updateLockAfterPreferenceSummary() {
        // Update summary message with current value
        long currentTimeout = Settings.Secure.getLong(getContentResolver(),
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT, 5000);
        final CharSequence[] entries = mLockAfter.getEntries();
        final CharSequence[] values = mLockAfter.getEntryValues();
        int best = 0;
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.valueOf(values[i].toString());
            if (currentTimeout >= timeout) {
                best = i;
            }
        }
        mLockAfter.setSummary(getString(R.string.lock_after_timeout_summary, entries[best]));
    }

    private void updateShakeTimerPreferenceSummary() {
        // Update summary message with current value
        long shakeTimer = Settings.Secure.getLongForUser(getContentResolver(),
                Settings.Secure.LOCK_SHAKE_SECURE_TIMER, 0,
                UserHandle.USER_CURRENT);
        final CharSequence[] entries = mShakeTimer.getEntries();
        final CharSequence[] values = mShakeTimer.getEntryValues();
        int best = 0;
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.valueOf(values[i].toString());
            if (shakeTimer >= timeout) {
                best = i;
            }
        }
        mShakeTimer.setSummary(entries[best]);
    }

    private void disableUnusableTimeouts(long maxTimeout) {
        final CharSequence[] entries = mLockAfter.getEntries();
        final CharSequence[] values = mLockAfter.getEntryValues();
        ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.valueOf(values[i].toString());
            if (timeout <= maxTimeout) {
                revisedEntries.add(entries[i]);
                revisedValues.add(values[i]);
            }
        }
        if (revisedEntries.size() != entries.length || revisedValues.size() != values.length) {
            mLockAfter.setEntries(
                    revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            mLockAfter.setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));
            final int userPreference = Integer.valueOf(mLockAfter.getValue());
            if (userPreference <= maxTimeout) {
                mLockAfter.setValue(String.valueOf(userPreference));
            } else {
                // There will be no highlighted selection since nothing in the list matches
                // maxTimeout. The user can still select anything less than maxTimeout.
                // TODO: maybe append maxTimeout to the list and mark selected.
            }
        }
        mLockAfter.setEnabled(revisedEntries.size() > 0);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Make sure we reload the preference hierarchy since some of these settings
        // depend on others...
        createPreferenceHierarchy();

        final LockPatternUtils lockPatternUtils = mChooseLockSettingsHelper.utils();
        if (mBiometricWeakLiveliness != null) {
            mBiometricWeakLiveliness.setChecked(
                    lockPatternUtils.isBiometricWeakLivelinessEnabled());
        }
        if (mVisiblePattern != null) {
            mVisiblePattern.setChecked(lockPatternUtils.isVisiblePatternEnabled());
        }
        if (mPowerButtonInstantlyLocks != null) {
            mPowerButtonInstantlyLocks.setChecked(lockPatternUtils.getPowerButtonInstantlyLocks());
        }

        if (mShowPassword != null) {
            mShowPassword.setChecked(Settings.System.getInt(getContentResolver(),
                    Settings.System.TEXT_SHOW_PASSWORD, 1) != 0);
        }

        if (mResetCredentials != null) {
            mResetCredentials.setEnabled(!mKeyStore.isEmpty());
        }

        if (mEnableKeyguardWidgets != null) {
            if (!lockPatternUtils.getWidgetsEnabled()) {
                mEnableKeyguardWidgets.setSummary(R.string.disabled);
            } else {
                mEnableKeyguardWidgets.setSummary(R.string.enabled);
            }
        }

        if (mBatteryStatus != null) {
            mBatteryStatus.setChecked(Settings.System.getIntForUser(getContentResolver(),
                    Settings.System.LOCKSCREEN_ALWAYS_SHOW_BATTERY, 0,
                    UserHandle.USER_CURRENT) != 0);
            mBatteryStatus.setOnPreferenceChangeListener(this);
        }

        updateBlacklistSummary();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (ensurePinRestrictedPreference(preference)) {
            return true;
        }
        final String key = preference.getKey();

        final LockPatternUtils lockPatternUtils = mChooseLockSettingsHelper.utils();
        if (KEY_UNLOCK_SET_OR_CHANGE.equals(key)) {
            startFragment(this, "com.android.settings.ChooseLockGeneric$ChooseLockGenericFragment",
                    SET_OR_CHANGE_LOCK_METHOD_REQUEST, null);
        } else if (KEY_BIOMETRIC_WEAK_IMPROVE_MATCHING.equals(key)) {
            ChooseLockSettingsHelper helper =
                    new ChooseLockSettingsHelper(this.getActivity(), this);
            if (!helper.launchConfirmationActivity(
                    CONFIRM_EXISTING_FOR_BIOMETRIC_WEAK_IMPROVE_REQUEST, null, null)) {
                // If this returns false, it means no password confirmation is required, so
                // go ahead and start improve.
                // Note: currently a backup is required for biometric_weak so this code path
                // can't be reached, but is here in case things change in the future
                startBiometricWeakImprove();
            }
        } else if (KEY_BIOMETRIC_WEAK_LIVELINESS.equals(key)) {
            if (isToggled(preference)) {
                lockPatternUtils.setBiometricWeakLivelinessEnabled(true);
            } else {
                // In this case the user has just unchecked the checkbox, but this action requires
                // them to confirm their password.  We need to re-check the checkbox until
                // they've confirmed their password
                mBiometricWeakLiveliness.setChecked(true);
                ChooseLockSettingsHelper helper =
                        new ChooseLockSettingsHelper(this.getActivity(), this);
                if (!helper.launchConfirmationActivity(
                                CONFIRM_EXISTING_FOR_BIOMETRIC_WEAK_LIVELINESS_OFF, null, null)) {
                    // If this returns false, it means no password confirmation is required, so
                    // go ahead and uncheck it here.
                    // Note: currently a backup is required for biometric_weak so this code path
                    // can't be reached, but is here in case things change in the future
                    lockPatternUtils.setBiometricWeakLivelinessEnabled(false);
                    mBiometricWeakLiveliness.setChecked(false);
                }
            }
        } else if (KEY_LOCK_ENABLED.equals(key)) {
            lockPatternUtils.setLockPatternEnabled(isToggled(preference));
        } else if (KEY_VISIBLE_PATTERN.equals(key)) {
            lockPatternUtils.setVisiblePatternEnabled(isToggled(preference));
        } else if (KEY_POWER_INSTANTLY_LOCKS.equals(key)) {
            lockPatternUtils.setPowerButtonInstantlyLocks(isToggled(preference));
        } else if (preference == mShowPassword) {
            Settings.System.putInt(getContentResolver(), Settings.System.TEXT_SHOW_PASSWORD,
                    mShowPassword.isChecked() ? 1 : 0);
        } else if (preference == mToggleAppInstallation) {
            if (mToggleAppInstallation.isChecked()) {
                mToggleAppInstallation.setChecked(false);
                warnAppInstallation();
            } else {
                setNonMarketAppsAllowed(false);
            }
        } else if (KEY_TOGGLE_VERIFY_APPLICATIONS.equals(key)) {
            Settings.Global.putInt(getContentResolver(), Settings.Global.PACKAGE_VERIFIER_ENABLE,
                    mToggleVerifyApps.isChecked() ? 1 : 0);
        } else if (preference == mQuickUnlockScreen) {
            Settings.System.putInt(getActivity().getApplicationContext().getContentResolver(),
                    Settings.System.LOCKSCREEN_QUICK_UNLOCK_CONTROL,
                    isToggled(preference) ? 1 : 0);
        } else {
            // If we didn't handle it, let preferences handle it.
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }

        return true;
    }

    private boolean isToggled(Preference pref) {
        return ((CheckBoxPreference) pref).isChecked();
    }

    /**
     * see confirmPatternThenDisableAndClear
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CONFIRM_EXISTING_FOR_BIOMETRIC_WEAK_IMPROVE_REQUEST &&
                resultCode == Activity.RESULT_OK) {
            startBiometricWeakImprove();
            return;
        } else if (requestCode == CONFIRM_EXISTING_FOR_BIOMETRIC_WEAK_LIVELINESS_OFF &&
                resultCode == Activity.RESULT_OK) {
            final LockPatternUtils lockPatternUtils = mChooseLockSettingsHelper.utils();
            lockPatternUtils.setBiometricWeakLivelinessEnabled(false);
            // Setting the mBiometricWeakLiveliness checked value to false is handled when onResume
            // is called by grabbing the value from lockPatternUtils.  We can't set it here
            // because mBiometricWeakLiveliness could be null
        } else if (requestCode == CONFIRM_EXISTING_FOR_TEMPORARY_INSECURE &&
                resultCode == Activity.RESULT_OK) {
            // Enable shake to secure
            if (mShakeTypeChosen != -1) {
                Settings.Secure.putInt(getContentResolver(),
                        Settings.Secure.LOCK_SHAKE_TEMP_SECURE, mShakeTypeChosen);
                if (mShakeToSecure != null && mShakeTimer != null) {
                    mShakeToSecure.setValue(String.valueOf(mShakeTypeChosen));
                    mShakeTimer.setEnabled(true);
                    shouldEnableTargets();
                }
            }
            return;
        }
        createPreferenceHierarchy();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        if (preference == mLockAfter) {
            int timeout = Integer.parseInt((String) value);
            try {
                Settings.Secure.putInt(getContentResolver(),
                        Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT, timeout);
            } catch (NumberFormatException e) {
                Log.e("SecuritySettings", "could not persist lockAfter timeout setting", e);
            }
            updateLockAfterPreferenceSummary();
        } else if (preference == mLockNumpadRandom) {
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.LOCK_NUMPAD_RANDOM,
                    Integer.valueOf((String) value));
            mLockNumpadRandom.setValue(String.valueOf(value));
            mLockNumpadRandom.setSummary(mLockNumpadRandom.getEntry());
        } else if (preference == mShakeToSecure) {
            int userVal = Integer.parseInt((String) value);
            if (userVal != 0) {
                mShakeTypeChosen = userVal;
                showDialogInner(DLG_SHAKE_WARN);
            } else {
                Settings.Secure.putInt(getContentResolver(),
                        Settings.Secure.LOCK_SHAKE_TEMP_SECURE, 0);
                mShakeToSecure.setValue(String.valueOf(0));
                mShakeTimer.setEnabled(false);
                shouldEnableTargets();
            }
        } else if (preference == mShakeTimer) {
            int shakeTime = Integer.parseInt((String) value);
            try {
                Settings.Secure.putInt(getContentResolver(),
                        Settings.Secure.LOCK_SHAKE_SECURE_TIMER, shakeTime);
            } catch (NumberFormatException e) {
                Log.e("SecuritySettings", "could not persist lockAfter timeout setting", e);
            }
            updateShakeTimerPreferenceSummary();
        } else if (preference == mLockBeforeUnlock) {
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.LOCK_BEFORE_UNLOCK,
                    ((Boolean) value) ? 1 : 0);
            shouldEnableTargets();
        } else if (preference == mAdvancedReboot) {
            Settings.Secure.putInt(getContentResolver(), Settings.Secure.ADVANCED_REBOOT,
                    Integer.valueOf((String) value));
            mAdvancedReboot.setValue(String.valueOf(value));
            mAdvancedReboot.setSummary(mAdvancedReboot.getEntry());
        } else if (preference == mLockscreenRotation) {
            int userVal = Integer.valueOf((String) value);
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.LOCKSCREEN_ROTATION_ENABLED,
                    userVal, UserHandle.USER_CURRENT);
            mLockscreenRotation.setValue(String.valueOf(value));
            if (userVal == 0) {
                mLockscreenRotation.setSummary(mLockscreenRotation.getEntry());
            } else {
                mLockscreenRotation.setSummary(mLockscreenRotation.getEntry()
                        + " " + getResources().getString(
                        R.string.lockscreen_rotation_summary_extra));
            }
        } else if (preference == mBatteryStatus) {
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.LOCKSCREEN_ALWAYS_SHOW_BATTERY,
                    ((Boolean) value) ? 1 : 0, UserHandle.USER_CURRENT);
        } else if (preference == mMenuUnlock) {
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.MENU_UNLOCK_SCREEN,
                    ((Boolean) value) ? 1 : 0, UserHandle.USER_CURRENT);
        } else if (preference == mSmsSecurityCheck) {
            int smsSecurityCheck = Integer.valueOf((String) value);
            Settings.Global.putInt(getContentResolver(),
                    Settings.Global.SMS_OUTGOING_CHECK_MAX_COUNT, smsSecurityCheck);
            updateSmsSecuritySummary(smsSecurityCheck);
        }
        return true;
    }

    @Override
    protected int getHelpResource() {
        return R.string.help_url_security;
    }

    public void startBiometricWeakImprove(){
        Intent intent = new Intent();
        intent.setClassName("com.android.facelock", "com.android.facelock.AddToSetup");
        startActivity(intent);
    }

    private void updateBlacklistSummary() {
        if (mBlacklist != null) {
            if (BlacklistUtils.isBlacklistEnabled(getActivity())) {
                mBlacklist.setSummary(R.string.blacklist_summary);
            } else {
                mBlacklist.setSummary(R.string.blacklist_summary_disabled);
            }
        }
    }

    private void showDialogInner(int id) {
        DialogFragment newFragment = MyAlertDialogFragment.newInstance(id);
        newFragment.setTargetFragment(this, 0);
        newFragment.show(getFragmentManager(), "dialog " + id);
    }


    public static class MyAlertDialogFragment extends DialogFragment {

        public static MyAlertDialogFragment newInstance(int id) {
            MyAlertDialogFragment frag = new MyAlertDialogFragment();
            Bundle args = new Bundle();
            args.putInt("id", id);
            frag.setArguments(args);
            return frag;
        }

        SecuritySettings getOwner() {
            return (SecuritySettings) getTargetFragment();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int id = getArguments().getInt("id");
            switch (id) {
                case DLG_SHAKE_WARN:
                    return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.shake_to_secure_dlg_title)
                    .setMessage(R.string.shake_to_secure_dlg_message)
                    .setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            disableShakeLock();
                        }
                    })
                    .setPositiveButton(R.string.dlg_ok,
                        new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            ChooseLockSettingsHelper helper =
                                    new ChooseLockSettingsHelper(
                                    getOwner().getActivity(), getOwner());
                            if (!helper.launchConfirmationActivity(
                                    getOwner().CONFIRM_EXISTING_FOR_TEMPORARY_INSECURE,
                                    null, null)) {
                                // We just want the return data here
                                // this boolean may return something useful one day.
                            }
                        }
                    })
                    .create();
            }
            throw new IllegalArgumentException("unknown id " + id);
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            int id = getArguments().getInt("id");
            switch (id) {
                case DLG_SHAKE_WARN:
                    disableShakeLock();
                    break;
                default:
                    // N/A at the moment
            }
        }

        private void disableShakeLock() {
            if (getOwner().mShakeToSecure != null
                    && getOwner().mShakeTimer != null) {
                Settings.Secure.putInt(getActivity().getContentResolver(),
                        Settings.Secure.LOCK_SHAKE_TEMP_SECURE, 0);
                getOwner().mShakeToSecure.setValue(String.valueOf(0));
                getOwner().mShakeTimer.setEnabled(false);
            }
        }
    }
}
