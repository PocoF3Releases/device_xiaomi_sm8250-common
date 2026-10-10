#!/usr/bin/env python3
"""Run the real haptic owner against simulated settings, clock and vibrator boundaries."""
import argparse
from pathlib import Path
import tempfile
from kotlin_fixtures import execute

parser = argparse.ArgumentParser()
parser.add_argument("--jdk", type=Path, required=True)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
files = {
"android/content/ContentResolver.java": "package android.content; public class ContentResolver {}",
"android/content/Context.java": """package android.content;
public class Context { public final android.os.VibratorManager manager=new android.os.VibratorManager();
public ContentResolver getContentResolver(){return new ContentResolver();}
public <T>T getSystemService(Class<T> c){return c.cast(manager);} }""",
"android/os/SystemClock.java": "package android.os; public class SystemClock {public static long now;public static long uptimeMillis(){return now;}}",
"android/os/VibrationEffect.java": """package android.os;
public class VibrationEffect {public long[] times;public int[] amplitudes;public int repeat;
public static VibrationEffect createWaveform(long[] t,int[] a,int r){VibrationEffect e=new VibrationEffect();e.times=t;e.amplitudes=a;e.repeat=r;return e;}}""",
"android/os/VibrationAttributes.java": """package android.os;
public class VibrationAttributes {public static final int USAGE_TOUCH=18;public int usage;
public static class Builder {private int u;public Builder setUsage(int value){u=value;return this;}
public VibrationAttributes build(){VibrationAttributes a=new VibrationAttributes();a.usage=u;return a;}}}""",
"android/os/VibratorManager.java": """package android.os;
public class VibratorManager {public final Vibrator vibrator=new Vibrator();public Vibrator getDefaultVibrator(){return vibrator;}}""",
"android/os/Vibrator.java": """package android.os;
public class Vibrator {public boolean present=true,amplitude=true,fail;public int calls;
public VibrationEffect effect;public VibrationAttributes attributes;
public boolean hasVibrator(){return present;}public boolean hasAmplitudeControl(){return amplitude;}
public void vibrate(VibrationEffect e,VibrationAttributes a){if(fail)throw new IllegalStateException();calls++;effect=e;attributes=a;}}""",
"android/provider/Settings.java": """package android.provider;
public class Settings {public static class System {public static final String HAPTIC_FEEDBACK_ENABLED="haptics";public static int enabled=1;
public static int getInt(android.content.ContentResolver r,String key,int fallback){return enabled;}}}""",
"android/view/HapticFeedbackConstants.java": "package android.view; public class HapticFeedbackConstants {public static final int CONTEXT_CLICK=1,SEGMENT_FREQUENT_TICK=2;}",
"android/view/View.java": """package android.view;
public class View {public final android.content.Context context=new android.content.Context();public boolean enabled=true;public int fallback,selection;
public android.content.Context getContext(){return context;}public boolean isHapticFeedbackEnabled(){return enabled;}
public boolean performHapticFeedback(int type){if(type==HapticFeedbackConstants.CONTEXT_CLICK)selection++;else fallback++;return true;}}""",
"org/lineageos/settings/compose/Test.java": r'''package org.lineageos.settings.compose;
import android.os.*;import android.view.*;import android.provider.Settings;
public class Test {
static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
static View setup(){SystemClock.now=0;Settings.System.enabled=1;return new View();}
public static void main(String[] args){
View v=setup();PartsHaptics h=new PartsHaptics(v);Vibrator device=v.context.manager.vibrator;
h.detent();check(device.calls==1,"supported waveform");
check(device.attributes.usage==VibrationAttributes.USAGE_TOUCH,"touch settings/intensity apply");
check(device.effect.repeat==-1,"never repeating");
long duration=0;for(long t:device.effect.times){check(t>=0,"nonnegative timing");duration+=t;}
check(duration>0&&duration<=20,"brief detent");
check(device.effect.times.length==device.effect.amplitudes.length,"waveform dimensions");
for(int a:device.effect.amplitudes)check(a>=0&&a<=255,"valid amplitude");
SystemClock.now=20;h.detent();check(device.calls==1,"rapid events throttled");
SystemClock.now=40;h.detent();check(device.calls==2,"next detent accepted");
Settings.System.enabled=0;SystemClock.now=80;h.detent();check(device.calls==2&&v.fallback==0,"user disabled respected");
Settings.System.enabled=1;v.enabled=false;SystemClock.now=120;h.detent();check(device.calls==2,"view disabled respected");
v=setup();device=v.context.manager.vibrator;device.amplitude=false;h=new PartsHaptics(v);h.detent();check(device.calls==0&&v.fallback==1,"no amplitude control uses platform");
v=setup();device=v.context.manager.vibrator;device.present=false;h=new PartsHaptics(v);h.detent();check(device.calls==0&&v.fallback==1,"no actuator uses platform");
v=setup();device=v.context.manager.vibrator;device.fail=true;h=new PartsHaptics(v);h.detent();check(v.fallback==1,"actuator failure does not crash UI");
h.selection();check(v.selection==1,"selection delegated to platform policy");
System.out.println("PASS: bounded waveforms, detent throttling, user/view settings and capability/failure fallback");
}}'''
}
with tempfile.TemporaryDirectory(prefix="parts-haptics-") as directory:
    root = Path(directory)
    for name, content in files.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
    result = execute(root, args.jdk.resolve(), repo, ["compose/PartsHaptics.kt"],
                     "org.lineageos.settings.compose.Test")
    print(result.stdout, end="")
    if result.returncode:
        print(result.stderr)
        raise SystemExit(result.returncode)
