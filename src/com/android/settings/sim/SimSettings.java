/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.settings.sim;

import com.android.settings.R;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.telephony.SubInfoRecord;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telecomm.PhoneAccount;
import android.telephony.CellInfo;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.RestrictedSettingsFragment;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.notification.DropDownPreference;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;
import com.android.settings.search.SearchIndexableRaw;

import java.util.ArrayList;
import java.util.List;

public class SimSettings extends RestrictedSettingsFragment implements Indexable {
    private static final String TAG = "SimSettings";

    private static final String DISALLOW_CONFIG_SIM = "no_config_sim";
    private static final String SIM_CARD_CATEGORY = "sim_cards";
    private static final String KEY_CELLULAR_DATA = "sim_cellular_data";
    private static final String KEY_CALLS = "sim_calls";
    private static final String KEY_SMS = "sim_sms";
    private static final String KEY_ACTIVITIES = "activities";

    /**
     * By UX design we have use only one Subscription Information(SubInfo) record per SIM slot.
     * mAvalableSubInfos is the list of SubInfos we present to the user.
     * mSubInfoList is the list of all SubInfos.
     */
    private List<SubInfoRecord> mAvailableSubInfos = null;
    private List<SubInfoRecord> mSubInfoList = null;

    private SubInfoRecord mCellularData = null;
    private SubInfoRecord mCalls = null;
    private SubInfoRecord mSMS = null;

    /**
     * Return whether or not the user should have a SIM Cards option in Settings.
     * TODO: Change back to returning true if count is greater than one after testing.
     * TODO: See bug 16533525.
     */
    public static boolean showSimCardScreen(Context context) {
        final TelephonyManager tm =
            (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        return tm.getSimCount() > 0;
    }

    public SimSettings() {
        super(DISALLOW_CONFIG_SIM);
    }

    @Override
    public void onCreate(final Bundle bundle) {
        super.onCreate(bundle);

        if (mSubInfoList == null) {
            mSubInfoList = SubscriptionManager.getActivatedSubInfoList(getActivity());
        }

        createPreferences();
        updateAllOptions();
    }

    private void createPreferences() {
        final TelephonyManager tm =
            (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);

        addPreferencesFromResource(R.xml.sim_settings);

        final PreferenceCategory simCards = (PreferenceCategory)findPreference(SIM_CARD_CATEGORY);

        final int numSlots = tm.getSimCount();
        mAvailableSubInfos = new ArrayList<SubInfoRecord>(numSlots);
        for (int i = 0; i < numSlots; ++i) {
            final SubInfoRecord sir = findRecordBySlotId(i);
            simCards.addPreference(new SimPreference(getActivity(), sir, i));
            mAvailableSubInfos.add(sir);
        }

        updateActivitesCategory();
    }

    private void updateAllOptions() {
        updateSimSlotValues();
        updateActivitesCategory();
    }

    private void updateSimSlotValues() {
        SubscriptionManager.getAllSubInfoList(getActivity());
        final PreferenceCategory simCards = (PreferenceCategory)findPreference(SIM_CARD_CATEGORY);
        final PreferenceScreen prefScreen = getPreferenceScreen();

        final int prefSize = prefScreen.getPreferenceCount();
        for (int i = 0; i < prefSize; ++i) {
            Preference pref = prefScreen.getPreference(i);
            if (pref instanceof SimPreference) {
                ((SimPreference)pref).update();
            }
        }
    }

    private void updateActivitesCategory() {
        createDropDown((DropDownPreference) findPreference(KEY_CELLULAR_DATA));
        createDropDown((DropDownPreference) findPreference(KEY_CALLS));
        createDropDown((DropDownPreference) findPreference(KEY_SMS));

        updateCellularDataValues();
        updateCallValues();
        updateSmsValues();
    }

    /**
     * finds a record with subId.
     * Since the number of SIMs are few, an array is fine.
     */
    private SubInfoRecord findRecordBySubId(final long subId) {
        final int availableSubInfoLength = mAvailableSubInfos.size();

        for (int i = 0; i < availableSubInfoLength; ++i) {
            final SubInfoRecord sir = mAvailableSubInfos.get(i);
            if (sir != null && sir.mSubId == subId) {
                return sir;
            }
        }

        return null;
    }

    /**
     * finds a record with slotId.
     * Since the number of SIMs are few, an array is fine.
     */
    private SubInfoRecord findRecordBySlotId(final int slotId) {
        if (mSubInfoList != null){
            final int availableSubInfoLength = mSubInfoList.size();

            for (int i = 0; i < availableSubInfoLength; ++i) {
                final SubInfoRecord sir = mSubInfoList.get(i);
                if (sir.mSlotId == slotId) {
                    //Right now we take the first subscription on a SIM.
                    return sir;
                }
            }
        }

        return null;
    }

    private void updateSmsValues() {
        final DropDownPreference simPref = (DropDownPreference) findPreference(KEY_SMS);
        final SubInfoRecord sir = findRecordBySubId(SubscriptionManager.getPreferredSmsSubId());
        if (sir != null) {
            simPref.setSelectedItem(sir.mSlotId + 1);
        }
    }

    private void updateCellularDataValues() {
        final DropDownPreference simPref = (DropDownPreference) findPreference(KEY_CELLULAR_DATA);
        final SubInfoRecord sir = findRecordBySubId(SubscriptionManager.getDefaultDataSubId());
        if (sir != null) {
            simPref.setSelectedItem(sir.mSlotId);
        }
    }

    private void updateCallValues() {
        final DropDownPreference simPref = (DropDownPreference) findPreference(KEY_CALLS);
        final SubInfoRecord sir = findRecordBySubId(SubscriptionManager.getDefaultVoiceSubId());
        if (sir != null) {
            simPref.setSelectedItem(sir.mSlotId + 1);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAllOptions();
    }

    @Override
    public boolean onPreferenceTreeClick(final PreferenceScreen preferenceScreen,
            final Preference preference) {
        if (preference instanceof SimPreference) {
            ((SimPreference)preference).createEditDialog((SimPreference)preference);
        }

        return true;
    }

    public void createDropDown(DropDownPreference preference) {
        final DropDownPreference simPref = preference;
        final String keyPref = simPref.getKey();
        final boolean askFirst = keyPref.equals(KEY_CALLS) || keyPref.equals(KEY_SMS);

        simPref.clearItems();

        if (askFirst) {
            simPref.addItem(getResources().getString(
                    R.string.sim_calls_ask_first_prefs_title), null);
        }

        final int subAvailableSize = mAvailableSubInfos.size();
        for (int i = 0; i < subAvailableSize; ++i) {
            final SubInfoRecord sir = mAvailableSubInfos.get(i);
            if(sir != null){
                simPref.addItem(sir.mDisplayName, sir);
            }
        }

        simPref.setCallback(new DropDownPreference.Callback() {
            @Override
            public boolean onItemSelected(int pos, Object value) {
                final long subId = value == null ? 0 : ((SubInfoRecord)value).mSubId;

                if (simPref.getKey().equals(KEY_CELLULAR_DATA)) {
                    SubscriptionManager.setDefaultDataSubId(subId);
                } else if (simPref.getKey().equals(KEY_CALLS)) {
                    SubscriptionManager.setDefaultVoiceSubId(subId);
                } else if (simPref.getKey().equals(KEY_SMS)) {
                    // TODO: uncomment once implemented. Bug: 16520931
                    // SubscriptionManager.setDefaultSMSSubId(subId);
                }

                updateAllOptions();

                return true;
            }
        });
    }

    private void setActivity(Preference preference, SubInfoRecord sir) {
        final String key = preference.getKey();

        if (key.equals(KEY_CELLULAR_DATA)) {
            mCellularData = sir;
        } else if (key.equals(KEY_CALLS)) {
            mCalls = sir;
        } else if (key.equals(KEY_SMS)) {
            mSMS = sir;
        }

        updateActivitesCategory();
    }

    private class SimPreference extends Preference{
        private SubInfoRecord mSubInfoRecord;
        private int mSlotId;

        public SimPreference(Context context, SubInfoRecord subInfoRecord, int slotId) {
            super(context);

            mSubInfoRecord = subInfoRecord;
            mSlotId = slotId;
            setKey("sim" + mSlotId);
            update();
        }

        public void update() {
            final Resources res = getResources();

            setTitle(res.getString(R.string.sim_card_number_title, mSlotId + 1));
            if (mSubInfoRecord != null) {
                setSummary(res.getString(R.string.sim_settings_summary,
                            mSubInfoRecord.mDisplayName, mSubInfoRecord.mNumber));
                setEnabled(true);
            } else {
                setSummary(R.string.sim_slot_empty);
                setFragment(null);
                setEnabled(false);
            }
        }

        public void createEditDialog(SimPreference simPref) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            final View dialogLayout = getActivity().getLayoutInflater().inflate(
                    R.layout.multi_sim_dialog, null);
            builder.setView(dialogLayout);

            EditText nameText = (EditText)dialogLayout.findViewById(R.id.sim_name);
            nameText.setText(mSubInfoRecord.mDisplayName);

            TextView numberView = (TextView)dialogLayout.findViewById(R.id.number);
            numberView.setText(mSubInfoRecord.mNumber);

            TextView carrierView = (TextView)dialogLayout.findViewById(R.id.carrier);
            carrierView.setText(mSubInfoRecord.mDisplayName);

            builder.setTitle(R.string.sim_editor_title);

            builder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                    final EditText nameText = (EditText)dialogLayout.findViewById(R.id.sim_name);
                    final Spinner displayNumbers =
                        (Spinner)dialogLayout.findViewById(R.id.display_numbers);

                    SubscriptionManager.setDisplayNumberFormat(getActivity(),
                        displayNumbers.getSelectedItemPosition() == 0
                            ? SubscriptionManager.DISPLAY_NUMBER_LAST
                            : SubscriptionManager.DISPLAY_NUMBER_FIRST, mSubInfoRecord.mSubId);

                    mSubInfoRecord.mDisplayName = nameText.getText().toString();
                    SubscriptionManager.setDisplayName(getActivity(), mSubInfoRecord.mDisplayName,
                        mSubInfoRecord.mSubId);

                    updateAllOptions();
                }
            });

            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                }
            });

            builder.create().show();
        }
    }
}
