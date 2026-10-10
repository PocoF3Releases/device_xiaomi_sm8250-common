#!/usr/bin/env python3
"""Host regression coverage for DisplayUtils; Android/sysfs boundaries are simulated."""
from pathlib import Path
import argparse
import shutil
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument("--jdk", type=Path, required=True)
parser.add_argument("--baseline", help="Verify the test detects the old bug at this Git revision")
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
files = {
"android/content/ContentResolver.java": "package android.content; public class ContentResolver {}",
"android/content/Context.java": "package android.content; public class Context { public ContentResolver getContentResolver(){return new ContentResolver();} }",
"android/content/SharedPreferences.java": """package android.content; import java.util.*; public class SharedPreferences { public Map<String,Object> values=new HashMap<>(); public boolean contains(String k){return values.containsKey(k);} public int getInt(String k,int d){return (int)values.getOrDefault(k,d);} public boolean getBoolean(String k,boolean d){return (boolean)values.getOrDefault(k,d);} public Editor edit(){return new Editor();} public class Editor { public Editor putInt(String k,int v){values.put(k,v);return this;} public Editor putBoolean(String k,boolean v){values.put(k,v);return this;} public Editor remove(String k){values.remove(k);return this;} public void apply(){} } }""",
"org/lineageos/settings/utils/PartsPreferences.java": "package org.lineageos.settings.utils; import android.content.*; public class PartsPreferences { public static SharedPreferences prefs=new SharedPreferences(); public static SharedPreferences getDefaultSharedPreferences(Context c){return prefs;} }",
"android/provider/Settings.java": "package android.provider; import android.content.*; public class Settings { public static class System { public static String SCREEN_BRIGHTNESS=\"brightness\"; public static int value=255; public static boolean fail; public static int getInt(ContentResolver r,String k,int d){return value;} public static boolean putInt(ContentResolver r,String k,int v){if(fail)return false;value=v;return true;} } }",
"android/util/Log.java": "package android.util; public class Log { public static int w(String t,String m){return 0;} }",
"org/lineageos/settings/utils/FileUtils.java": """package org.lineageos.settings.utils; import java.util.*; public class FileUtils {public static Map<String,String> values=new HashMap<>();public static boolean failWrite; public static boolean isFileWritable(String p){return true;} public static boolean isFileReadable(String p){return true;} public static String readOneLine(String p){return values.getOrDefault(p,"0");} public static boolean writeLine(String p,String v){if(failWrite)return false;values.put(p,v);return true;} }""",
"Test.java": """import android.content.*;import android.provider.Settings;import org.lineageos.settings.utils.PartsPreferences;import org.lineageos.settings.display.*;import org.lineageos.settings.utils.FileUtils;
public class Test {
 static Context context=new Context();static String backup="hbm_previous_brightness";
 static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 static void setup(){PartsPreferences.prefs=new SharedPreferences();PartsPreferences.prefs.edit().putInt(backup,77).putBoolean(DisplayNodes.getHbmEnableKey(),true).apply();FileUtils.values.clear();FileUtils.values.put(DisplayNodes.getHbmNode(),"1");FileUtils.failWrite=false;Settings.System.fail=false;Settings.System.value=255;}
 public static void main(String[] args){
 setup();Settings.System.fail=true;
 check(!DisplayUtils.setHbm(context,false),"failed brightness restore reported success");
 check(PartsPreferences.prefs.getInt(backup,-1)==77,"brightness backup lost");
 check(!PartsPreferences.prefs.getBoolean(DisplayNodes.getHbmEnableKey(),true),"HBM preference stayed on");
 check(!DisplayUtils.isHbmEnabled(),"HBM hardware stayed on");
 DisplayUtils.restore(context);check(PartsPreferences.prefs.contains(backup),"failed boot retry lost backup");
 Settings.System.fail=false;DisplayUtils.restore(context);
 check(Settings.System.value==77,"boot retry did not restore brightness");check(!PartsPreferences.prefs.contains(backup),"successful retry kept backup");
 setup();check(DisplayUtils.setHbm(context,false),"normal disable failed");check(Settings.System.value==77,"normal brightness not restored");
 setup();FileUtils.failWrite=true;check(!DisplayUtils.setHbm(context,false),"failed HBM node reported success");check(PartsPreferences.prefs.contains(backup),"node failure lost backup");check(PartsPreferences.prefs.getBoolean(DisplayNodes.getHbmEnableKey(),false),"node failure altered saved HBM");
 setup();PartsPreferences.prefs.edit().remove(backup).putBoolean(DisplayNodes.getHbmEnableKey(),false).apply();DisplayUtils.restore(context);check(Settings.System.value==255,"off restore changed brightness without backup");
 java.lang.System.out.println("PASS: settings failure, boot retry, normal disable, sysfs failure, no backup");
 }}""",
}
with tempfile.TemporaryDirectory(prefix="hbm-test-") as tmp:
    root = Path(tmp)
    for name, content in files.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
    from kotlin_fixtures import execute
    if args.baseline:
        raise SystemExit("Baseline Java checks belong to the pre-migration revision; use current Kotlin fixtures here")
    result = execute(root, args.jdk, repo, ["display/DisplayUtils.kt", "display/DisplayNodes.kt"], "Test")
    print(result.stdout, end="")
    if result.returncode:
        raise RuntimeError(result.stderr)
