package com.xuan.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import android.content.Context;

public class RootHelper {

    // Called by MainActivity after its own root check
    public static void setHasRoot(boolean val) { hasRoot = val; }
    public static boolean hasRoot() { return hasRoot; }

    private static boolean hasRoot = false;

    public static boolean checkRoot() {
        hasRoot = false;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "echo root_ok"});
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder(); String l;
            while ((l = r.readLine()) != null) sb.append(l);
            r.close(); p.waitFor();
            hasRoot = sb.toString().contains("root_ok");
        } catch (Exception e) {
            hasRoot = false;
        }
        return hasRoot;
    }
    
    public static String runRootCommand(String command) {
        if (!hasRoot && !checkRoot()) return "ERROR: No root access";
        StringBuilder result = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String line;
            while ((line = reader.readLine()) != null) result.append(line).append("\n");
            reader.close();
            while ((line = err.readLine()) != null) result.append(line).append("\n");
            err.close();
            // Timeout: 15 seconds
            Thread t = new Thread(){public void run(){try{p.waitFor();}catch(Exception e){}}};
            t.start(); t.join(15000);
            if (t.isAlive()) { p.destroy(); return "ERROR: timeout"; }
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
        return result.toString().trim();
    }

    public static String runShellCommand(String command) {
        StringBuilder result = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }
            reader.close();
            p.waitFor();
        } catch (Exception e) {
            result.append("ERROR: ").append(e.getMessage());
        }
        return result.toString().trim();
    }

    // Uninstall app - handles both user and system apps
    public static String uninstallApp(String packageName, boolean isSystemApp) {
        if (isSystemApp) {
            String result = runRootCommand("pm uninstall --user 0 " + packageName);
            if (result.contains("Success")) return result;
            result = runRootCommand("pm uninstall " + packageName);
            if (result.contains("Success")) return result;
            runRootCommand("pm disable " + packageName);
            String path = runRootCommand("pm path " + packageName + " | head -1 | cut -d: -f2");
            if (path != null && !path.isEmpty() && !path.startsWith("ERROR")) {
                path = path.trim();
                if (!path.isEmpty()) runRootCommand("rm -rf \"" + path + "\"");
            }
            return result;
        } else {
            return runRootCommand("pm uninstall " + packageName);
        }
    }

    // Extract APK - copy APK file to given path
    public static String extractApk(String packageName, String outputPath, boolean isSystemApp, Context ctx) {
        try {
            // Get APK path via pm
            String pathCmd = "pm path " + packageName + " 2>/dev/null | head -1 | cut -d: -f2";
            String apkPath = (isSystemApp && checkRoot()) ? runRootCommand(pathCmd) : runShellCommand(pathCmd);
            if (apkPath == null || apkPath.isEmpty() || apkPath.startsWith("ERROR")) {
                return "ERROR: cannot find APK path";
            }
            apkPath = apkPath.trim();
            java.io.File src = new java.io.File(apkPath);
            java.io.File dst = new java.io.File(outputPath);
            if (src.exists() && src.canRead()) {
                java.io.FileInputStream fis = new java.io.FileInputStream(src);
                java.io.FileOutputStream fos = new java.io.FileOutputStream(dst);
                byte[] buf = new byte[8192]; int n;
                while ((n = fis.read(buf)) > 0) fos.write(buf, 0, n);
                fis.close(); fos.close();
            } else {
                String catCmd = (isSystemApp && checkRoot()) ?
                    runRootCommand("cat \"" + apkPath + "\" > \"" + dst.getAbsolutePath() + "\"") :
                    runShellCommand("cat \"" + apkPath + "\" > \"" + dst.getAbsolutePath() + "\"");
            }
            return (dst.exists() && dst.length() > 0) ? "OK" : "ERROR: copy failed";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // Freeze app (disable)
    public static String freezeApp(String packageName) {
        return runRootCommand("pm disable " + packageName);
    }

    // Unfreeze app (enable)
    public static String unfreezeApp(String packageName) {
        return runRootCommand("pm enable " + packageName);
    }

    // Force stop app
    public static String forceStopApp(String packageName, Context ctx) {
        return runRootCommand("am force-stop " + packageName);
    }

    // Clear app data
    public static String clearAppData(String packageName, Context ctx) {
        return runRootCommand("pm clear " + packageName);
    }

    // List permissions
    public static String listPermissions(String packageName) {
        return runShellCommand("dumpsys package " + packageName + " | grep -E 'android.permission' | grep 'granted=true'");
    }

    // Get APK size - fast batch version, called from loadApps
    public static String getAllApkPaths() {
        return runShellCommand("pm list packages -f 2>/dev/null");
    }

    // Get frozen apps list - batch
    public static String getFrozenPackages() {
        return runShellCommand("pm list packages -d 2>/dev/null");
    }

    // Parse APK size from path
    public static long getApkSizeFromPath(String apkPath) {
        try {
            if (apkPath == null || apkPath.isEmpty()) return 0;
            String sizeStr = runShellCommand("stat -c%s \"" + apkPath + "\" 2>/dev/null");
            if (sizeStr != null && !sizeStr.isEmpty() && !sizeStr.startsWith("ERROR")) {
                return Long.parseLong(sizeStr.trim());
            }
        } catch (Exception e) {}
        return 0;
    }

    // Keep old method for compatibility
    public static long getApkFileSize(String packageName) {
        try {
            String result = runShellCommand("pm path " + packageName + " | head -1 | cut -d: -f2");
            if (result != null && !result.isEmpty() && !result.startsWith("ERROR")) {
                result = result.trim();
                return getApkSizeFromPath(result);
            }
        } catch (Exception e) {}
        return 0;
    }

    // Check if app is frozen
    public static boolean isAppFrozen(String packageName) {
        String result = runShellCommand("pm list packages -d | grep " + packageName);
        return result != null && !result.isEmpty();
    }

    // Get package info JSON-like
    public static String getPackageInfo(String packageName) {
        return runShellCommand("dumpsys package " + packageName + " | head -80");
    }

    // Get sign info
    public static String getSignInfo(String apkPath) {
        return runShellCommand("apksigner verify --print-certs " + apkPath + " 2>&1");
    }
}