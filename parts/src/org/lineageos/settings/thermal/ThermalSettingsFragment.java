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
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.TextView;

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

    private static final int[] MODE_LABELS = {
            R.string.thermal_default,
            R.string.thermal_benchmark,
            R.string.thermal_browser,
            R.string.thermal_camera,
            R.string.thermal_dialer,
            R.string.thermal_gaming,
            R.string.thermal_streaming
    };

    private AllPackagesAdapter mAllPackagesAdapter;
    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSession;
    private ActivityFilter mActivityFilter;
    private RecyclerView mAppsRecyclerView;
    private ThermalUtils mThermalUtils;

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
        mAppsRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAppsRecyclerView.setAdapter(mAllPackagesAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().setTitle(getResources().getString(R.string.thermal_title));
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

    private int clampState(int state) {
        return Math.max(0, Math.min(state, MODE_LABELS.length - 1));
    }

    private void showModeDialog(ApplicationsState.AppEntry entry, int selectedState) {
        final String[] labels = new String[MODE_LABELS.length];
        for (int i = 0; i < MODE_LABELS.length; i++) {
            labels[i] = getString(MODE_LABELS[i]);
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.thermal_title)
                .setSingleChoiceItems(labels, selectedState, (dialog, which) -> {
                    if (which != selectedState) {
                        mThermalUtils.writePackage(entry.info.packageName, which);
                        mAllPackagesAdapter.notifyDataSetChanged();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private int getStateDrawable(int state) {
        switch (state) {
            case ThermalUtils.STATE_BENCHMARK:
                return R.drawable.ic_thermal_benchmark;
            case ThermalUtils.STATE_BROWSER:
                return R.drawable.ic_thermal_browser;
            case ThermalUtils.STATE_CAMERA:
                return R.drawable.ic_thermal_camera;
            case ThermalUtils.STATE_DIALER:
                return R.drawable.ic_thermal_dialer;
            case ThermalUtils.STATE_GAMING:
                return R.drawable.ic_thermal_gaming;
            case ThermalUtils.STATE_STREAMING:
                return R.drawable.ic_thermal_streaming;
            case ThermalUtils.STATE_DEFAULT:
            default:
                return R.drawable.ic_thermal_default;
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

            int packageState = clampState(
                    mThermalUtils.getStateForPackage(entry.info.packageName));
            holder.mode.setText(MODE_LABELS[packageState]);
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

            int stateIconDrawable = getStateDrawable(packageState);
            boolean hasTouchControls = stateIconDrawable == R.drawable.ic_thermal_gaming
                    || stateIconDrawable == R.drawable.ic_thermal_benchmark;
            holder.touchIcon.setVisibility(hasTouchControls ? View.VISIBLE : View.GONE);
            holder.stateIcon.setImageResource(stateIconDrawable);
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
