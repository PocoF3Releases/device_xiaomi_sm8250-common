#!/usr/bin/env python3
"""Execute the real touch-profile Java sources against a FocalTech-range HAL fixture.

Only javac/java run, in a temporary directory; no Android build commands are used.
"""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument("--jdk", type=Path, required=True)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
files = {
"android/content/Context.java": "package android.content; public class Context { public Context getApplicationContext(){return this;} public <T> T getSystemService(Class<T> c){return null;} public void startService(Intent i){} }",
"android/content/Intent.java": "package android.content; public class Intent {public Intent(Context c,Class<?> t){}}",
"android/content/SharedPreferences.java": "package android.content; import java.util.*; public class SharedPreferences { public Map<String,Object> values=new HashMap<>(); public String getString(String k,String d){return (String)values.getOrDefault(k,d);} public int getInt(String k,int d){return (int)values.getOrDefault(k,d);} public Editor edit(){return new Editor();} public class Editor {public Editor putString(String k,String v){values.put(k,v);return this;} public Editor putInt(String k,int v){values.put(k,v);return this;} public void apply(){}} }",
"androidx/preference/PreferenceManager.java": "package androidx.preference; import android.content.*; public class PreferenceManager {public static SharedPreferences prefs=new SharedPreferences(); public static SharedPreferences getDefaultSharedPreferences(Context c){return prefs;}}",
"android/os/Build.java": "package android.os; public class Build {public static String DEVICE=\"alioth\";}",
"android/os/RemoteException.java": "package android.os; public class RemoteException extends Exception {}",
"android/os/Looper.java": "package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}",
"android/os/Handler.java": "package android.os; public class Handler {public Handler(Looper l){} public void postDelayed(Runnable r,long n){}}",
"android/os/SystemProperties.java": "package android.os; public class SystemProperties {public static String get(String k,String d){return d;}public static void set(String k,String v){}}",
"android/hardware/display/DisplayManager.java": "package android.hardware.display; import android.view.Display; public class DisplayManager {public Display getDisplay(int i){return null;}}",
"android/view/Display.java": "package android.view; public class Display {public static int DEFAULT_DISPLAY=0;public int getRotation(){return 0;}}",
"android/view/Surface.java": "package android.view; public class Surface {public static final int ROTATION_0=0,ROTATION_90=1,ROTATION_180=2,ROTATION_270=3;}",
"android/util/Log.java": "package android.util; public class Log {public static int w(String t,String m){return 0;}public static int w(String t,String m,Throwable e){return 0;}public static int d(String t,String m){return 0;}public static int d(String t,String m,Throwable e){return 0;}}",
"org/lineageos/settings/utils/FileUtils.java": "package org.lineageos.settings.utils; public class FileUtils {public static boolean isFileWritable(String p){return true;}public static boolean fileExists(String p){return false;}public static boolean writeLine(String p,String v){return true;}}",
"org/lineageos/settings/touchsampling/TouchSamplingUtils.java": "package org.lineageos.settings.touchsampling; import android.content.Context; public class TouchSamplingUtils {public static boolean isEnabled(Context c){return false;}}",
"org/lineageos/settings/thermal/ThermalService.java": "package org.lineageos.settings.thermal; public class ThermalService {}",
"org/lineageos/settings/thermal/ThermalProfiles.java": "package org.lineageos.settings.thermal; public class ThermalProfiles {public static final int REGION_INDIA=1,REGION_GLOBAL=0;public static class Profile {public int sconfig;}public static Profile findBySconfig(int r,int s){return new Profile();}public static Profile findByStorageState(int r,int s){return new Profile();}}",
"vendor/xiaomi/hardware/touchfeature/V1_0/ITouchFeature.java": """package vendor.xiaomi.hardware.touchfeature.V1_0;
import android.os.RemoteException; import java.util.*;
public class ITouchFeature {
 public static ITouchFeature instance=new ITouchFeature();
 public Map<Integer,Integer> values=new HashMap<>();public Set<Integer> resets=new HashSet<>();
 public static ITouchFeature getService() throws RemoteException{return instance;}
 public int getTouchModeMinValue(int m) throws RemoteException{return m>=2&&m<=5?1:0;}
 public int getTouchModeMaxValue(int m) throws RemoteException{return m>=2&&m<=5?5:3;}
 public int setTouchMode(int m,int v) throws RemoteException{values.put(m,Math.max(getTouchModeMinValue(m),Math.min(getTouchModeMaxValue(m),v)));return 0;}
 public int resetTouchMode(int m) throws RemoteException{resets.add(m);values.put(m,m>=2&&m<=5?3:0);return 0;}
}""",
"org/lineageos/settings/thermal/Test.java": """package org.lineageos.settings.thermal;
import android.content.*;import android.os.Build;import androidx.preference.PreferenceManager;
import vendor.xiaomi.hardware.touchfeature.V1_0.ITouchFeature;
public class Test {
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 static ITouchFeature apply(String device,String profile){
  Build.DEVICE=device;ITouchFeature.instance=new ITouchFeature();
  PreferenceManager.prefs=new SharedPreferences();PreferenceManager.prefs.edit().putString("app",profile).apply();
  new ThermalUtils(new Context()).setThermalProfile("app");return ITouchFeature.instance;
 }
 public static void main(String[] args) throws Exception{
  for(String device:new String[]{"alioth","aliothin"}){
   ITouchFeature hal=apply(device,"1,0,0,0");
   check(hal.values.get(2)==3&&hal.values.get(3)==3,"zero defaults clamped to minimum for "+device);
   check(hal.resets.contains(2)&&hal.resets.contains(3),"defaults did not reset firmware modes");
   hal=apply(device,"1,5,1,0");check(hal.values.get(2)==5&&hal.values.get(3)==1,"explicit range endpoints changed");
   hal=apply(device,"1,3,4,0");check(hal.values.get(2)==3&&hal.values.get(3)==4,"explicit tuning changed");
  }
  ITouchFeature hal=apply("other","1,0,0,0");
  check(hal.values.get(2)==1&&hal.values.get(3)==1,"legacy path changed");
  System.out.println("PASS: Alioth/India defaults, explicit endpoints, explicit tuning, legacy dispatch");
 }
}""",
}
with tempfile.TemporaryDirectory(prefix="alioth-touch-test-") as tmp:
    root = Path(tmp)
    for name, content in files.items():
        dest = root / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(content)
    for name in ["AliothTouchProfile.java", "ThermalUtils.java", "Constants.java"]:
        dest = root / "org/lineageos/settings/thermal" / name
        shutil.copy(repo / "parts/src/org/lineageos/settings/thermal" / name, dest)
    subprocess.run([str(args.jdk / "bin/javac"), "-d", str(root / "classes"), *map(str, root.rglob("*.java"))], check=True)
    subprocess.run([str(args.jdk / "bin/java"), "-cp", str(root / "classes"), "org.lineageos.settings.thermal.Test"], check=True)
