package com.xuan.app;

import android.graphics.drawable.Drawable;

public class AppInfo {
    public String packageName;
    public String appName;
    public String versionName;
    public int versionCode;
    public long installTime;
    public long apkSize;
    public String sourceDir;
    public boolean isSystemApp;
    public Drawable icon;
    public int targetSdk;
    public boolean isFrozen;

    public AppInfo(String packageName, String appName, String versionName,
                   int versionCode, long installTime, long apkSize,
                   String sourceDir, boolean isSystemApp, Drawable icon,
                   int targetSdk) {
        this.packageName = packageName;
        this.appName = appName;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.installTime = installTime;
        this.apkSize = apkSize;
        this.sourceDir = sourceDir;
        this.isSystemApp = isSystemApp;
        this.icon = icon;
        this.targetSdk = targetSdk;
        this.isFrozen = false;
    }

    public boolean getIsFrozen() {
        return isFrozen;
    }
}