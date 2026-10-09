#!/usr/bin/env python3
"""Execute TouchSettingsFragment and its real backend in isolated Java UI/HAL fixtures."""
import argparse
import ast
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--jdk", type=Path, required=True)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
fixture = ast.parse((repo / "tests/test_alioth_touch_defaults.py").read_text())
files = ast.literal_eval(next(node.value for node in fixture.body
                             if isinstance(node, ast.Assign)
                             and any(isinstance(target, ast.Name) and target.id == "files"
                                     for target in node.targets)))
files["android/content/SharedPreferences.java"] = files["android/content/SharedPreferences.java"].replace(
    "public void apply(){}", "public Editor remove(String k){values.remove(k);return this;} public void apply(){}")
files["org/lineageos/settings/touchsampling/TouchSamplingUtils.java"] = files[
    "org/lineageos/settings/touchsampling/TouchSamplingUtils.java"].replace(
    "public static boolean isEnabled(Context c){return false;}",
    "public static boolean enabled; public static boolean isEnabled(Context c){return enabled;}")
hal_path = "vendor/xiaomi/hardware/touchfeature/V1_0/ITouchFeature.java"
files[hal_path] = files[hal_path].replace(
    "public int getTouchModeMinValue(int m) throws RemoteException{return m>=2&&m<=5?1:0;}",
    "public static boolean invalid; public int getTouchModeMinValue(int m) throws RemoteException{if(invalid)return -1;return m>=2&&m<=6?1:0;}")
files[hal_path] = files[hal_path].replace(
    "public int resetTouchMode", "public int getTouchModeDefValue(int m) throws RemoteException{return m==7?2:3;} public int resetTouchMode")
files.update({
"android/R.java": "package android; public class R {public static class string {public static final int ok=1,cancel=2;}}",
"android/app/Activity.java": "package android.app; public class Activity {public void setTitle(int id){}}",
"android/os/Bundle.java": "package android.os; public class Bundle {public String getString(String k,String d){return k.equals(\"packageName\")?\"app\":k.equals(\"appName\")?\"Fixture app\":d;}}",
"android/content/res/Resources.java": "package android.content.res; public class Resources {public CharSequence[] getTextArray(int id){return new String[]{\"Manual\",\"1\",\"2\",\"3\"};} public String[] getStringArray(int id){return new String[]{\"0\",\"1\",\"2\",\"3\"};}}",
"android/app/AlertDialog.java": "package android.app; import android.content.*; public class AlertDialog {public static class Builder {public Builder(Context c){} public Builder setTitle(int i){return this;}public Builder setMessage(int i){return this;}public Builder setPositiveButton(int i,Listener l){return this;}public Builder setNegativeButton(int i,Listener l){return this;} public void show(){} }public interface Listener{void onClick(Object d,int w);}}",
"androidx/preference/Preference.java": """package androidx.preference; import android.content.Intent;
public class Preference {private String key;public boolean visible=true,enabled=true,selectable;public Intent intent;public int title,summary;
 public Preference(String k){key=k;}public String getKey(){return key;}public void setVisible(boolean b){visible=b;}public void setEnabled(boolean b){enabled=b;}
 public void setPersistent(boolean b){}public void setOnPreferenceChangeListener(OnPreferenceChangeListener l){}public void setOnPreferenceClickListener(OnPreferenceClickListener l){}
 public void setSelectable(boolean b){selectable=b;}public void setIntent(Intent i){intent=i;}public void setTitle(int i){title=i;}public void setSummary(int i){summary=i;}public void setSummary(String s){}
 public interface OnPreferenceChangeListener{boolean onPreferenceChange(Preference p,Object v);}public interface OnPreferenceClickListener{boolean onPreferenceClick(Preference p);}}
""",
"androidx/preference/ListPreference.java": "package androidx.preference; public class ListPreference extends Preference {public String value;public CharSequence[] entries,entryValues;public ListPreference(String k){super(k);}public void setValue(String s){value=s;}public void setEntries(CharSequence[] s){entries=s;}public void setEntryValues(CharSequence[] s){entryValues=s;}public void setSummaryProvider(Object o){}public static class SimpleSummaryProvider{public static Object getInstance(){return null;}}}",
"com/android/settingslib/widget/MainSwitchPreference.java": "package com.android.settingslib.widget; import androidx.preference.Preference; public class MainSwitchPreference extends Preference {public boolean checked;public MainSwitchPreference(String k){super(k);}public void setChecked(boolean b){checked=b;}}",
"com/android/settingslib/widget/SliderPreference.java": "package com.android.settingslib.widget; import androidx.preference.Preference; public class SliderPreference extends Preference {public static final int HAPTIC_FEEDBACK_MODE_ON_TICKS=1;public int max,value;public SliderPreference(String k,int m){super(k);max=m;}public void setMax(int m){max=m;}public int getMax(){return max;}public void setValue(int v){value=v;}public void setHapticFeedbackMode(int m){}}",
"com/android/settingslib/widget/SettingsBasePreferenceFragment.java": """package com.android.settingslib.widget;
import android.os.Bundle;import android.content.*;import android.content.res.Resources;import android.app.Activity;import androidx.preference.Preference;import java.util.*;
public class SettingsBasePreferenceFragment {public static Map<String,Preference> prefs=new HashMap<>();
 public void onCreatePreferences(Bundle b,String key){}public void onResume(){}public void addPreferencesFromResource(int id){}
 public Context requireContext(){return new Context();}public Bundle requireArguments(){return new Bundle();}public Activity requireActivity(){return new Activity();}
 public Resources getResources(){return new Resources();}@SuppressWarnings(\"unchecked\") public <T extends Preference> T findPreference(String key){return (T)prefs.get(key);}}
""",
"org/lineageos/settings/touchsampling/TouchSamplingSettingsActivity.java": "package org.lineageos.settings.touchsampling; public class TouchSamplingSettingsActivity {}",
})
source = repo / "parts/src/org/lineageos/settings/thermal"
refs = {}
for kind, name in re.findall(r"(?<!android\.)R\.(\w+)\.(\w+)", (source / "TouchSettingsFragment.java").read_text()):
    refs.setdefault(kind, set()).add(name)
files["org/lineageos/settings/R.java"] = "package org.lineageos.settings; public class R {" + "".join(
    "public static class " + kind + " {" + "".join(f"public static final int {name}={i};" for i,name in enumerate(sorted(names),1)) + "}"
    for kind,names in refs.items()) + "}"
android = "{http://schemas.android.com/apk/res/android}"
setup = []
for node in ET.parse(repo / "parts/res/xml/touch_settings.xml").iter():
    key = node.get(android + "key")
    if not key:
        continue
    if node.tag.endswith("SliderPreference"):
        maximum = node.get(android + "max", "3")
        maximum = "3" if maximum.startswith("@") else maximum
        expression = f'new SliderPreference("{key}",{maximum})'
    elif node.tag.endswith("MainSwitchPreference"):
        expression = f'new MainSwitchPreference("{key}")'
    elif node.tag == "ListPreference":
        expression = f'new ListPreference("{key}")'
    else:
        expression = f'new Preference("{key}")'
    setup.append(f'SettingsBasePreferenceFragment.prefs.put("{key}",{expression});')
files["org/lineageos/settings/thermal/Test.java"] = """package org.lineageos.settings.thermal;
import android.content.*;import android.os.*;import androidx.preference.*;import com.android.settingslib.widget.*;
import org.lineageos.settings.touchsampling.TouchSamplingUtils;import vendor.xiaomi.hardware.touchfeature.V1_0.ITouchFeature;
public class Test {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static Preference pref(String key){return SettingsBasePreferenceFragment.prefs.get(key);}
 static TouchSettingsFragment setup(String saved){
  Build.DEVICE="alioth";ITouchFeature.invalid=false;ITouchFeature.instance=new ITouchFeature();TouchSamplingUtils.enabled=false;
  PreferenceManager.prefs=new SharedPreferences();if(saved!=null)PreferenceManager.prefs.edit().putString("app",saved).apply();
  SettingsBasePreferenceFragment.prefs.clear();
""" + "\n".join(setup) + """
  TouchSettingsFragment f=new TouchSettingsFragment();f.onCreatePreferences(new Bundle(),null);f.onResume();return f;
 }
 static void change(TouchSettingsFragment f,String key,Object value){check(f.onPreferenceChange(pref(key),value),"valid selection rejected: "+key);}
 public static void main(String[] args) throws Exception {
  TouchSettingsFragment f=setup(null);
  check(!pref("touch_manual_group").visible&&!pref("touch_mode_group").visible,"off state showed inactive sliders");
  check(((SliderPreference)pref("touch_response")).getMax()==5,"HAL response range missing");
  change(f,"touch_game_mode",true);
  check(pref("touch_manual_group").visible&&pref("touch_advanced_group").visible,"manual groups hidden");
  check(PreferenceManager.prefs.getString("app","").equals("1,0,0,2"),"new profile did not use HAL edge default");
  change(f,"touch_response",4);change(f,"touch_aim",5);change(f,"touch_expert","2");
  check(!pref("touch_manual_group").visible&&!pref("touch_advanced_group").visible,"preset showed overridden sliders");
  check(pref("touch_edge_group").visible,"preset hid independent edge filtering");
  change(f,"touch_resistant",1);change(f,"touch_expert","0");f.onResume();
  check(((SliderPreference)pref("touch_response")).value==4&&((SliderPreference)pref("touch_aim")).value==5,"preset switch lost manual tuning");
  check(((SliderPreference)pref("touch_resistant")).value==1,"preset switch lost edge filtering");
  check(!f.onPreferenceChange(pref("touch_expert"),"4"),"invalid preset accepted");
  check(!f.onPreferenceChange(pref("touch_response"),6),"out-of-range level accepted");
  TouchSamplingUtils.enabled=true;f.onResume();
  check(!pref("touch_manual_group").visible&&!pref("touch_edge_group").visible,"global override did not pause groups");
  check(pref("touch_status").selectable&&pref("touch_status").intent!=null,"global status did not offer navigation");
  TouchSamplingUtils.enabled=false;f.onResume();
  java.lang.reflect.Method reset=TouchSettingsFragment.class.getDeclaredMethod("resetTuning");reset.setAccessible(true);reset.invoke(f);
  check(PreferenceManager.prefs.getString("app","").equals("1,0,0,2"),"reset did not preserve enable/default edge");
  check(((SliderPreference)pref("touch_aim")).value==0&&pref("touch_manual_group").visible,"reset left secondary tuning or preset");
  change(f,"touch_game_mode",false);check(!pref("touch_edge_group").visible,"disable left controls active");
  f=setup("1,5,4,0");PreferenceManager.prefs.edit().putInt(AliothTouchProfile.key("app","touch_expert"),3).apply();f.onResume();
  check(((ListPreference)pref("touch_expert")).value.equals("3"),"old integer preset not migrated to list");
  check(((SliderPreference)pref("touch_resistant")).value==0,"existing edge-off setting changed");
  ITouchFeature.invalid=true;f.onResume();check(!pref("touch_game_mode").enabled&&!pref("touch_mode_group").visible,"invalid HAL ranges allowed tuning");
  ITouchFeature.invalid=false;f.onResume();check(pref("touch_game_mode").enabled,"HAL retry stayed disabled");
  PreferenceManager.prefs.values.put("app",42);f.onResume();check(!((MainSwitchPreference)pref("touch_game_mode")).checked,"bad saved preference crashed/activated tuning");
  f.showHelp();
  System.out.println("PASS: off/manual/preset/global states, HAL limits, saved settings, reset, invalid inputs and HAL recovery");
 }
}"""
with tempfile.TemporaryDirectory(prefix="touch-ui-test-") as tmp:
    root = Path(tmp)
    for name, content in files.items():
        dest = root / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(content)
    for name in ["AliothTouchProfile.java", "ThermalUtils.java", "Constants.java", "TouchSettingsFragment.java"]:
        shutil.copy(source / name, root / "org/lineageos/settings/thermal" / name)
    subprocess.run([str(args.jdk / "bin/javac"), "-d", str(root / "classes"), *map(str,root.rglob("*.java"))],check=True)
    subprocess.run([str(args.jdk / "bin/java"), "-cp", str(root / "classes"), "org.lineageos.settings.thermal.Test"],check=True)
