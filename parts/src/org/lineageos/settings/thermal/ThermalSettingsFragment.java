/**
 * Copyright (C) 2020 The LineageOS Project
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.settings.thermal;

import android.annotation.Nullable;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settingslib.applications.ApplicationsState;

import org.lineageos.settings.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ThermalSettingsFragment extends Fragment
        implements ApplicationsState.Callbacks {

    private AllPackagesAdapter mAllPackagesAdapter;
    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSession;
    private ActivityFilter mActivityFilter;
    private RecyclerView mAppsRecyclerView;
    private TextView mBaseProfile;
    private RadioGroup mRegionGroup;
    private RadioButton mRegionGlobal;
    private RadioButton mRegionIndia;
    private ThermalUtils mThermalUtils;
    private boolean mBindingRegion;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mApplicationsState = ApplicationsState.getInstance(getActivity().getApplication());
        mSession = mApplicationsState.newSession(this);
        mActivityFilter = new ActivityFilter(getActivity().getPackageManager());
        mAllPackagesAdapter = new AllPackagesAdapter(getActivity());
        mThermalUtils = new ThermalUtils(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.thermal_layout, container, false);
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAppsRecyclerView = view.findViewById(R.id.thermal_rv_view);
        mBaseProfile = view.findViewById(R.id.thermal_base_profile);
        mRegionGroup = view.findViewById(R.id.thermal_region_group);
        mRegionGlobal = view.findViewById(R.id.thermal_region_global);
        mRegionIndia = view.findViewById(R.id.thermal_region_india);

        mAppsRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAppsRecyclerView.setAdapter(mAllPackagesAdapter);

        mBaseProfile.setOnClickListener(v -> showBaseProfileDialog());
        mRegionGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (mBindingRegion) return;
            int region = checkedId == R.id.thermal_region_india
                    ? ThermalProfiles.REGION_INDIA : ThermalProfiles.REGION_GLOBAL;
            if (!mThermalUtils.setSelectedRegion(region)) {
                Toast.makeText(requireContext(), R.string.parts_apply_failed,
                        Toast.LENGTH_SHORT).show();
            }
            refreshHeader();
            mAllPackagesAdapter.notifyDataSetChanged();
        });
        refreshHeader();
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().setTitle(getResources().getString(R.string.thermal_title));
        refreshHeader();
        mSession.onResume();
        rebuild();
    }

    @Override
    public void onPause() {
        mSession.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (mAppsRecyclerView != null) {
            mAppsRecyclerView.setAdapter(null);
            mAppsRecyclerView = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        mSession.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onPackageListChanged() {
        mActivityFilter.updateLauncherInfoList();
        rebuild();
    }

    @Override
    public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> entries) {
        if (entries != null && isAdded()) {
            handleAppEntries(entries);
            mAllPackagesAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onLoadEntriesCompleted() {
        rebuild();
    }

    @Override
    public void onAllSizesComputed() {
    }

    @Override
    public void onLauncherInfoChanged() {
    }

    @Override
    public void onPackageIconChanged() {
    }

    @Override
    public void onPackageSizeChanged(String packageName) {
    }

    @Override
    public void onRunningStateChanged(boolean running) {
    }

    private void handleAppEntries(List<ApplicationsState.AppEntry> entries) {
        final ArrayList<String> sections = new ArrayList<String>();
        final ArrayList<Integer> positions = new ArrayList<Integer>();
        final PackageManager pm = getActivity().getPackageManager();
        String lastSectionIndex = null;
        int offset = 0;

        for (int i = 0; i < entries.size(); i++) {
            final ApplicationInfo info = entries.get(i).info;
            final String label = info.loadLabel(pm).toString();
            final String sectionIndex;

            if (!info.enabled) {
                sectionIndex = "--"; // XXX
            } else if (TextUtils.isEmpty(label)) {
                sectionIndex = "";
            } else {
                sectionIndex = label.substring(0, 1).toUpperCase();
            }

            if (lastSectionIndex == null
                    || !TextUtils.equals(sectionIndex, lastSectionIndex)) {
                sections.add(sectionIndex);
                positions.add(offset);
                lastSectionIndex = sectionIndex;
            }

            offset++;
        }

        mAllPackagesAdapter.setEntries(entries, sections, positions);
    }

    private void rebuild() {
        mSession.rebuild(mActivityFilter, ApplicationsState.ALPHA_COMPARATOR);
    }

    private static final int STATE_UNUSED = -1;

    private void refreshHeader() {
        if (!isAdded() || mBaseProfile == null) return;
        int region = ThermalUtils.getSelectedRegion();
        mBindingRegion = true;
        mRegionGlobal.setChecked(region == ThermalProfiles.REGION_GLOBAL);
        mRegionIndia.setChecked(region == ThermalProfiles.REGION_INDIA);
        mRegionIndia.setEnabled(ThermalUtils.isIndiaMapAvailable());
        mBindingRegion = false;

        ThermalProfiles.Profile base =
                ThermalProfiles.findBySconfig(region, mThermalUtils.getBaseSconfig());
        mBaseProfile.setText(base == null ? R.string.thermal_normal : base.titleRes);
    }

    private void showBaseProfileDialog() {
        ThermalProfileAdapter adapter = new ThermalProfileAdapter(
                requireContext(), false, STATE_UNUSED, mThermalUtils.getBaseSconfig());
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.thermal_system_profile_title)
                .setAdapter(adapter, (dialog, which) -> {
                    ThermalProfiles.Profile profile = adapter.getProfile(which);
                    if (profile != null && mThermalUtils.setBaseSconfig(profile.sconfig)) {
                        refreshHeader();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showModeDialog(ApplicationsState.AppEntry entry, int selectedState) {
        ThermalProfileAdapter adapter =
                new ThermalProfileAdapter(requireContext(), true, selectedState, -1);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.thermal_profile_dialog_title)
                .setAdapter(adapter, (dialog, which) -> {
                    int state = adapter.getStorageState(which);
                    if (state != selectedState) {
                        mThermalUtils.writePackage(entry.info.packageName, state);
                        mAllPackagesAdapter.notifyDataSetChanged();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static class ThermalProfileAdapter extends BaseAdapter {
        private final Context mContext;
        private final ThermalProfiles.Profile[] mProfiles;
        private final boolean mIncludeSystemDefault;
        private final int mSelectedState;
        private final int mSelectedSconfig;

        private ThermalProfileAdapter(Context context, boolean includeSystemDefault,
                int selectedState, int selectedSconfig) {
            mContext = context;
            mProfiles = ThermalProfiles.getProfiles(ThermalUtils.getSelectedRegion());
            mIncludeSystemDefault = includeSystemDefault;
            mSelectedState = selectedState;
            mSelectedSconfig = selectedSconfig;
        }

        @Override public int getCount() {
            return mProfiles.length + (mIncludeSystemDefault ? 1 : 0);
        }
        @Override public Object getItem(int position) { return getProfile(position); }
        @Override public long getItemId(int position) {
            ThermalProfiles.Profile profile = getProfile(position);
            return profile == null ? 0 : profile.sconfig;
        }

        private ThermalProfiles.Profile getProfile(int position) {
            int index = position - (mIncludeSystemDefault ? 1 : 0);
            return index >= 0 && index < mProfiles.length ? mProfiles[index] : null;
        }

        private int getStorageState(int position) {
            ThermalProfiles.Profile profile = getProfile(position);
            return profile == null ? ThermalUtils.STATE_DEFAULT : profile.storageState;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(mContext)
                        .inflate(R.layout.thermal_profile_dialog_item, parent, false);
            }
            TextView title = view.findViewById(R.id.profile_title);
            TextView summary = view.findViewById(R.id.profile_summary);
            RadioButton radio = view.findViewById(R.id.profile_radio);
            ThermalProfiles.Profile profile = getProfile(position);
            if (profile == null) {
                title.setText(R.string.thermal_system_default);
                summary.setText(R.string.thermal_system_default_summary);
                radio.setChecked(mSelectedState == ThermalUtils.STATE_DEFAULT);
            } else {
                title.setText(profile.titleRes);
                summary.setText(profile.summaryRes);
                radio.setChecked(mIncludeSystemDefault
                        ? profile.storageState == mSelectedState
                        : profile.sconfig == mSelectedSconfig);
            }
            return view;
        }
    }

    private class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView mode;
        private final ImageView icon;
        private final ImageView stateIcon;
        private final ImageView touchIcon;

        private ViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.app_name);
            mode = view.findViewById(R.id.app_mode);
            icon = view.findViewById(R.id.app_icon);
            stateIcon = view.findViewById(R.id.state);
            touchIcon = view.findViewById(R.id.touch);
        }
    }

    private class AllPackagesAdapter extends RecyclerView.Adapter<ViewHolder>
            implements SectionIndexer {

        private List<ApplicationsState.AppEntry> mEntries = new ArrayList<>();
        private String[] mSections;
        private int[] mPositions;

        private AllPackagesAdapter(Context context) {
            mActivityFilter = new ActivityFilter(context.getPackageManager());
        }

        @Override
        public int getItemCount() {
            return mEntries.size();
        }

        @Override
        public long getItemId(int position) {
            return mEntries.get(position).id;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.thermal_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ApplicationsState.AppEntry entry = mEntries.get(position);
            if (entry == null) {
                return;
            }

            holder.title.setText(entry.label);
            mApplicationsState.ensureIcon(entry);
            holder.icon.setImageDrawable(entry.icon);

            int packageState =
                    mThermalUtils.getStateForPackage(entry.info.packageName);
            ThermalProfiles.Profile profile = ThermalProfiles.findByStorageState(
                    ThermalUtils.getSelectedRegion(), packageState);
            holder.mode.setText(profile == null
                    ? R.string.thermal_system_default : profile.titleRes);
            holder.mode.setOnClickListener(v -> showModeDialog(entry, packageState));
            holder.title.setOnClickListener(v -> holder.mode.performClick());

            holder.touchIcon.setOnClickListener(v -> {
                TouchSettingsFragment touchSettingsFragment = new TouchSettingsFragment();
                Bundle bundle = new Bundle();
                bundle.putString("appName", entry.label);
                bundle.putString("packageName", entry.info.packageName);
                touchSettingsFragment.setArguments(bundle);
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                                touchSettingsFragment, "touchSettingsFragment")
                        .addToBackStack(null)
                        .commit();
            });

            holder.touchIcon.setVisibility(View.VISIBLE);
            holder.stateIcon.setImageResource(profile == null
                    ? R.drawable.ic_thermal_default : profile.iconRes);
        }

        private void setEntries(List<ApplicationsState.AppEntry> entries,
                List<String> sections, List<Integer> positions) {
            mEntries = entries;
            mSections = sections.toArray(new String[sections.size()]);
            mPositions = new int[positions.size()];
            for (int i = 0; i < positions.size(); i++) {
                mPositions[i] = positions.get(i);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getPositionForSection(int section) {
            if (section < 0 || section >= mSections.length) {
                return -1;
            }

            return mPositions[section];
        }

        @Override
        public int getSectionForPosition(int position) {
            if (position < 0 || position >= getItemCount()) {
                return -1;
            }

            final int index = Arrays.binarySearch(mPositions, position);
            return index >= 0 ? index : -index - 2;
        }

        @Override
        public Object[] getSections() {
            return mSections;
        }
    }

    private class ActivityFilter implements ApplicationsState.AppFilter {

        private final PackageManager mPackageManager;
        private final List<String> mLauncherResolveInfoList = new ArrayList<String>();

        private ActivityFilter(PackageManager packageManager) {
            mPackageManager = packageManager;
            updateLauncherInfoList();
        }

        public void updateLauncherInfoList() {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolveInfoList = mPackageManager.queryIntentActivities(i, 0);

            synchronized (mLauncherResolveInfoList) {
                mLauncherResolveInfoList.clear();
                for (ResolveInfo ri : resolveInfoList) {
                    mLauncherResolveInfoList.add(ri.activityInfo.packageName);
                }
            }
        }

        @Override
        public void init() {
        }

        @Override
        public boolean filterApp(ApplicationsState.AppEntry entry) {
            synchronized (mLauncherResolveInfoList) {
                return mLauncherResolveInfoList.contains(entry.info.packageName);
            }
        }
    }
}
