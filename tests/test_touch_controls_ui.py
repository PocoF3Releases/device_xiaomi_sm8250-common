#!/usr/bin/env python3
"""Execute the Kotlin touch controller. Compose rendering requires APK/device validation."""
import argparse
import ast
from pathlib import Path
import tempfile
from kotlin_fixtures import execute

parser = argparse.ArgumentParser()
parser.add_argument("--jdk", type=Path, required=True)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
fixture = ast.parse((repo / "tests/test_alioth_touch_defaults.py").read_text())
files = ast.literal_eval(next(node.value for node in fixture.body if isinstance(node, ast.Assign)
                            and any(isinstance(t, ast.Name) and t.id == "files" for t in node.targets)))
files["android/content/SharedPreferences.java"] = files["android/content/SharedPreferences.java"].replace(
    "public void apply(){}", "public Editor remove(String k){values.remove(k);return this;}public void apply(){}")
path = "org/lineageos/settings/touchsampling/TouchSamplingUtils.java"
files[path] = files[path].replace("public static boolean isEnabled(Context c){return false;}",
    "public static boolean enabled; public static boolean isEnabled(Context c){return enabled;}")
path = "vendor/xiaomi/hardware/touchfeature/V1_0/ITouchFeature.java"
files[path] = files[path].replace("public int getTouchModeMinValue(int m) throws RemoteException{return m>=2&&m<=5?1:0;}",
    "public static boolean invalid;public int getTouchModeMinValue(int m) throws RemoteException{if(invalid)return -1;return m>=2&&m<=6?1:0;}").replace(
    "public int resetTouchMode", "public int getTouchModeDefValue(int m) throws RemoteException{return m==7?2:3;}public int resetTouchMode")
files["org/lineageos/settings/thermal/Test.java"] = r'''package org.lineageos.settings.thermal;
import android.content.*;import android.os.*;import org.lineageos.settings.utils.PartsPreferences;
import org.lineageos.settings.touchsampling.TouchSamplingUtils;import vendor.xiaomi.hardware.touchfeature.V1_0.ITouchFeature;
public class Test {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static TouchControls setup(String saved){
  Build.DEVICE="alioth";ITouchFeature.invalid=false;ITouchFeature.instance=new ITouchFeature();TouchSamplingUtils.enabled=false;
  PartsPreferences.prefs=new SharedPreferences();if(saved!=null)PartsPreferences.prefs.edit().putString("app",saved).apply();
  TouchControls c=new TouchControls(new Context(),"app");c.reload();return c;
 }
 public static void main(String[] args){
  TouchControls c=setup(null);check(!c.getEffective(),"off state active");check(c.getLimits()[1]==5,"missing HAL range");
  check(c.setValue(0,1),"enable rejected");check(c.getEffective()&&c.getManual(),"manual state inactive");
  check(PartsPreferences.prefs.getString("app","").equals("1,0,0,2"),"new edge default wrong");
  c.setValue(1,4);c.setExtra("touch_aim",5);c.setExtra("touch_expert",2);check(!c.getManual()&&c.getEffective(),"preset state wrong");
  c.setValue(3,1);c.setExtra("touch_expert",0);c.reload();
  check(c.getValues()[1]==4&&c.getAim()==5&&c.getValues()[3]==1,"preset switch lost manual/edge settings");
  check(!c.setExtra("touch_expert",4)&&!c.setValue(1,6),"invalid inputs accepted");
  TouchSamplingUtils.enabled=true;c.reload();check(c.getGlobalOverride()&&!c.getEffective(),"global override did not pause tuning");
  TouchSamplingUtils.enabled=false;c.reload();c.reset();check(c.getEnabled()&&c.getManual()&&c.getAim()==0,"reset lost enable or secondary defaults");
  check(PartsPreferences.prefs.getString("app","").equals("1,0,0,2"),"reset edge wrong");
  c.setValue(0,0);check(!c.getEffective(),"off state active after toggle");
  c=setup("1,5,4,0");PartsPreferences.prefs.edit().putInt(AliothTouchProfile.key("app","touch_expert"),3).apply();c.reload();
  check(c.getPreset()==3&&c.getValues()[3]==0,"legacy integer preset/edge off changed");
  ITouchFeature.invalid=true;c.reload();check(!c.getAvailable()&&!c.getEffective(),"invalid HAL enabled tuning");
  ITouchFeature.invalid=false;c.reload();check(c.getAvailable(),"HAL retry failed");
  PartsPreferences.prefs.values.put("app",42);c.reload();check(!c.getEnabled(),"invalid storage activated tuning");
  System.out.println("PASS: off/manual/preset/global states, HAL limits, legacy settings, reset, invalid inputs and HAL retry");
 }
}'''
with tempfile.TemporaryDirectory(prefix="touch-controller-test-") as tmp:
    root = Path(tmp)
    for name, content in files.items():
        path = root / name;path.parent.mkdir(parents=True, exist_ok=True);path.write_text(content)
    result = execute(root, args.jdk, repo,
        ["thermal/AliothTouchProfile.kt", "thermal/ThermalUtils.kt", "thermal/Constants.kt", "thermal/TouchControls.kt"],
        "org.lineageos.settings.thermal.Test")
    print(result.stdout, end="")
    if result.returncode: raise RuntimeError(result.stderr)
