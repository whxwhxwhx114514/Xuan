package com.xuan.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import android.content.ContentValues;

public class MainActivity extends Activity {

    private ListView appList;
    private EditText searchInput;
    private TextView rootStatus, permHint;
    private TextView tabUser, tabSystem;
    private TextView btnBatch;
    
    
    
    
    

    private List<AppInfo> allApps = new ArrayList<>();
    private List<AppInfo> filteredApps = new ArrayList<>();
    private AppListAdapter adapter;
    private int currentFilter = 0;
    private boolean batchMode = false;
    private Set<String> selectedPackages = new HashSet<>();
    private volatile boolean hasRoot = false;
    private volatile boolean rootChecked = false;
    private boolean appsLoaded = false;
    private boolean pendingRefresh = false;
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        w.setStatusBarColor(Color.parseColor("#F2F2F7"));
        View decor = w.getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        appList = (ListView) findViewById(R.id.app_list);
        searchInput = (EditText) findViewById(R.id.search_input);
        rootStatus = (TextView) findViewById(R.id.root_status);
        permHint = (TextView) findViewById(R.id.perm_hint);
        tabUser = (TextView) findViewById(R.id.tab_user);
        tabSystem = (TextView) findViewById(R.id.tab_system);
        btnBatch = (TextView) findViewById(R.id.btn_batch);

        setupUI();
        checkPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingRefresh) {
            pendingRefresh = false;
            appsLoaded = false;
            loadApps();
        } else if (!appsLoaded) {
            loadApps();
        }
    }

    private void checkPermissions() {
        // Detect Root
        appList.postDelayed(new Runnable(){public void run(){
            rootStatus.setText("检测中…");
            rootStatus.setTextColor(Color.parseColor("#FF9500"));
            new Thread(new Runnable() { public void run() {
                boolean rootOk = false;
                try {
                    Process check = Runtime.getRuntime().exec(new String[]{"which","su"});
                    if (check.waitFor() == 0) {
                        Process su = Runtime.getRuntime().exec("su");
                        java.io.DataOutputStream os = new java.io.DataOutputStream(su.getOutputStream());
                        os.writeBytes("echo root_ok\nexit\n"); os.flush(); os.close();
                        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(su.getInputStream()));
                        StringBuilder sb = new StringBuilder(); String l;
                        while((l=r.readLine())!=null) sb.append(l); r.close();
                        su.waitFor();
                        rootOk = sb.toString().contains("root_ok");
                    }
                } catch(Exception e) {}
                final boolean hasRootResult = rootOk;
                RootHelper.setHasRoot(rootOk);
                runOnUiThread(new Runnable(){public void run(){
                    hasRoot = hasRootResult;
                    rootChecked = true;
                    updatePermissionStatus();
                }});
            }}).start();
        }}, 800);
    }

    private void setupUI() {
        adapter = new AppListAdapter();
        appList.setAdapter(adapter);
        searchInput.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){filterApps();}
            public void afterTextChanged(Editable s){}
        });
        tabUser.setOnClickListener(new View.OnClickListener(){public void onClick(View v){setFilter(0);}});
        tabSystem.setOnClickListener(new View.OnClickListener(){public void onClick(View v){setFilter(1);}});
        // Magisk button - click to open Magisk or re-request root
        rootStatus.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
            if(hasRoot){
                Intent i=getPackageManager().getLaunchIntentForPackage("com.topjohnwu.magisk");
                if(i!=null)startActivity(i);
                else Toast.makeText(MainActivity.this,"未找到 Magisk 应用",Toast.LENGTH_SHORT).show();
            } else {
                checkPermissions();
                Toast.makeText(MainActivity.this,"正在重新检测权限…",Toast.LENGTH_SHORT).show();
            }
        }});
        btnBatch.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
            batchMode=!batchMode; selectedPackages.clear();
            btnBatch.setText(batchMode?"完成":"批量操作");
            btnBatch.setTextColor(Color.parseColor(batchMode?"#FF3B30":"#007AFF"));
            if(!batchMode) adapter.notifyDataSetChanged();
            Toast.makeText(MainActivity.this,batchMode?"批量模式：点击选择应用":"已退出批量模式",Toast.LENGTH_SHORT).show();
        }});
        appList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener(){
            public boolean onItemLongClick(AdapterView<?> p,View v,int pos,long id){
                if(!batchMode) showActionSheet(filteredApps.get(pos)); return true;
            }
        });
        appList.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            public void onItemClick(AdapterView<?> p,View v,int pos,long id){
                if(batchMode){
                    AppInfo a=filteredApps.get(pos);
                    if(selectedPackages.contains(a.packageName)) selectedPackages.remove(a.packageName);
                    else selectedPackages.add(a.packageName);
                    adapter.notifyDataSetChanged();
                    if(selectedPackages.size()>0) showBatchActions();
                } else showActionSheet(filteredApps.get(pos));
            }
        });
        
    }

    private void setFilter(int f){
        currentFilter=f;
        tabUser.setBackgroundResource(f==0?R.drawable.ios_btn_bg:R.drawable.search_bar_bg);
        tabUser.setTextColor(f==0?Color.WHITE:Color.parseColor("#007AFF"));
        tabSystem.setBackgroundResource(f==1?R.drawable.ios_btn_bg:R.drawable.search_bar_bg);
        tabSystem.setTextColor(f==1?Color.WHITE:Color.parseColor("#007AFF"));
filterApps();
    }

    private void updatePermissionStatus(){
        if (hasRoot) {
            rootStatus.setText("Root \u2713");
            rootStatus.setTextColor(Color.parseColor("#34C759"));
            permHint.setVisibility(View.GONE);
        } else {
            rootStatus.setText("无权限");
            rootStatus.setTextColor(Color.parseColor("#FF3B30"));
            permHint.setVisibility(View.VISIBLE);
        }
    }

    private void runRootAction(final String label, final Runnable action){
        final ProgressDialog pd=ProgressDialog.show(this,"","执行中…",true,false);
        new Thread(new Runnable(){public void run(){
            try{action.run();}catch(Exception e){
                runOnUiThread(new Runnable(){public void run(){if(pd.isShowing())pd.dismiss();
                    Toast.makeText(MainActivity.this,"错误:"+e.getMessage(),Toast.LENGTH_LONG).show();}});
                return;
            }
            runOnUiThread(new Runnable(){public void run(){if(pd.isShowing())pd.dismiss();
                Toast.makeText(MainActivity.this,label+" 完成",Toast.LENGTH_SHORT).show();appsLoaded=false;loadApps();}});
        }}).start();
    }

    private void runRootActionNoReload(final String label, final Runnable action){
        final ProgressDialog pd=ProgressDialog.show(this,"","执行中…",true,false);
        new Thread(new Runnable(){public void run(){
            try{action.run();}catch(Exception e){
                runOnUiThread(new Runnable(){public void run(){if(pd.isShowing())pd.dismiss();
                    Toast.makeText(MainActivity.this,"错误:"+e.getMessage(),Toast.LENGTH_LONG).show();}});
                return;
            }
            runOnUiThread(new Runnable(){public void run(){if(pd.isShowing())pd.dismiss();
                Toast.makeText(MainActivity.this,label+" 完成",Toast.LENGTH_SHORT).show();}});
        }}).start();
    }

    private String runShell(String cmd){
        try{
            Process p=Runtime.getRuntime().exec(new String[]{"sh","-c",cmd});
            java.io.BufferedReader r=new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            StringBuilder sb=new StringBuilder();String l;
            while((l=r.readLine())!=null)sb.append(l).append("\n");
            r.close();p.waitFor();return sb.toString().trim();
        }catch(Exception e){return"";}
    }

    private void loadApps(){
        if(allApps.size()>0&&appsLoaded){filterApps();return;}
        final ProgressDialog d=ProgressDialog.show(this,"","加载应用中…",true,false);
        new Thread(new Runnable(){public void run(){
            List<AppInfo> apps=new ArrayList<>();
            PackageManager pm=getPackageManager();
            Map<String,String> pkgToApk=new HashMap<>();
            Set<String> allPkgs=new LinkedHashSet<>();
            try{
                String r=runShell("pm list packages -f 2>/dev/null");
                if(r!=null)for(String l:r.split("\n")){
                    l=l.trim();if(!l.startsWith("package:"))continue;
                    int eq=l.lastIndexOf("="); if(eq<=0)continue;
                    String pkg=l.substring(eq+1),path=l.substring(8,eq);
                    pkgToApk.put(pkg,path); allPkgs.add(pkg);
                }
            }catch(Exception e){}
            Set<String> frozen=new HashSet<>();
            try{
                String r=runShell("pm list packages -d 2>/dev/null");
                if(r!=null)for(String l:r.split("\n")){
                    l=l.trim();if(l.startsWith("package:"))frozen.add(l.substring(8).trim());
                }
            }catch(Exception e){}
            if(allPkgs.isEmpty())try{
                for(PackageInfo pi:pm.getInstalledPackages(0))allPkgs.add(pi.packageName);
            }catch(Exception e){}
            for(String pkg:allPkgs){
                try{
                    PackageInfo pi=pm.getPackageInfo(pkg,0);
                    if(pi==null)continue;
                    ApplicationInfo ai=pi.applicationInfo;
                    boolean isSys=(ai.flags&ApplicationInfo.FLAG_SYSTEM)!=0||(ai.flags&ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0;
                    String apkPath=pkgToApk.get(pkg);
                    if(apkPath==null)apkPath=ai.sourceDir;
                    long sz=0;if(apkPath!=null&&!apkPath.isEmpty())sz=new File(apkPath).length();
                    AppInfo info=new AppInfo(pkg,pm.getApplicationLabel(ai).toString(),
                        pi.versionName!=null?pi.versionName:"?",pi.versionCode,
                        pi.firstInstallTime,sz,ai.sourceDir!=null?ai.sourceDir:"",
                        isSys,pm.getApplicationIcon(ai),ai.targetSdkVersion);
                    info.isFrozen=frozen.contains(pkg);
                    apps.add(info);
                }catch(Exception e){
                    if(pkgToApk.containsKey(pkg)){
                        AppInfo info=new AppInfo(pkg,pkg,"?",0,0,0,"",false,null,0);
                        info.isFrozen=frozen.contains(pkg); apps.add(info);
                    }
                }
            }
            Collections.sort(apps,new Comparator<AppInfo>(){public int compare(AppInfo a,AppInfo b){
                if(a.isFrozen!=b.isFrozen)return a.isFrozen?1:-1;
                return a.appName.compareToIgnoreCase(b.appName);
            }});
            runOnUiThread(new Runnable(){public void run(){if(d.isShowing())d.dismiss();allApps=apps;appsLoaded=true;filterApps();}});
        }}).start();
    }

    private void filterApps(){
        String q=searchInput.getText().toString().toLowerCase().trim();
        filteredApps.clear();
        for(AppInfo a:allApps){
            if(currentFilter==0&&a.isSystemApp)continue;
            if(currentFilter==1&&!a.isSystemApp)continue;
            if(!q.isEmpty()&&!a.appName.toLowerCase().contains(q)&&!a.packageName.toLowerCase().contains(q))continue;
            filteredApps.add(a);
        }
        adapter.notifyDataSetChanged();
    }








private int dp(int dp){return (int)(dp*getResources().getDisplayMetrics().density);}

    private void showIosConfirm(String title,String message,String confirmText,String colorHex,final Runnable action){
        final Dialog d=new Dialog(this,R.style.IOSSheetDialog);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundResource(R.drawable.ios_sheet_bg);
        root.setPadding(0,dp(18),0,dp(28));
        if(title!=null&&!title.isEmpty()){
            TextView tv=new TextView(this);tv.setText(title);tv.setTextSize(17);
            tv.setTextColor(Color.parseColor("#1C1C1E"));tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(20),0,dp(20),dp(8));root.addView(tv);
        }
        if(message!=null&&!message.isEmpty()){
            TextView tv=new TextView(this);tv.setText(message);tv.setTextSize(14);
            tv.setTextColor(Color.parseColor("#3C3C43"));tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(20),dp(4),dp(20),dp(16));root.addView(tv);
        }
        View div=new View(this);div.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(1)));
        div.setBackgroundColor(Color.parseColor("#E5E5EA"));root.addView(div);
        LinearLayout btns=new LinearLayout(this);btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(50)));
        TextView cancel=new TextView(this);cancel.setText("取消");cancel.setTextSize(17);
        cancel.setTextColor(Color.parseColor("#007AFF"));cancel.setGravity(Gravity.CENTER);
        cancel.setLayoutParams(new LinearLayout.LayoutParams(0,-1,1));
        cancel.setOnClickListener(new View.OnClickListener(){public void onClick(View v){d.dismiss();}});
        btns.addView(cancel);
        View vd=new View(this);vd.setLayoutParams(new LinearLayout.LayoutParams(dp(1),-1));
        vd.setBackgroundColor(Color.parseColor("#E5E5EA"));btns.addView(vd);
        TextView ok=new TextView(this);ok.setText(confirmText);ok.setTextSize(17);
        ok.setTextColor(Color.parseColor(colorHex));ok.setGravity(Gravity.CENTER);
        ok.setLayoutParams(new LinearLayout.LayoutParams(0,-1,1));
        ok.setOnClickListener(new View.OnClickListener(){public void onClick(View v){d.dismiss();action.run();}});
        btns.addView(ok);root.addView(btns);
        d.setContentView(root);Window w=d.getWindow();
        if(w!=null){w.setLayout(-1,-2);w.setGravity(Gravity.BOTTOM);
            w.setWindowAnimations(R.style.IOSSheetAnimation);w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));}
        d.show();
    }

    private void showActionSheet(final AppInfo app){
        final Dialog d=new Dialog(this,R.style.IOSSheetDialog);
        View v=LayoutInflater.from(this).inflate(R.layout.ios_sheet,null);
        TextView title = (TextView) v.findViewById(R.id.sheet_title);
        LinearLayout list = (LinearLayout) v.findViewById(R.id.sheet_actions);
        TextView cancel = (TextView) v.findViewById(R.id.sheet_cancel);
        title.setText(app.appName);
        // Always available
        addAction(list,"\u25b6 启动","#007AFF",false,null,new Runnable(){public void run(){d.dismiss();launchApp(app);}});
        addAction(list,"\ud83d\udce6 提取APK","#007AFF",false,null,new Runnable(){public void run(){d.dismiss();extractApk(app);}});
        addAction(list,"\u2139 详情","#007AFF",false,null,new Runnable(){public void run(){d.dismiss();showDetail(app);}});
        addAction(list,"\u23f9 强制停止","#FF9500",true,"需Root",new Runnable(){public void run(){d.dismiss();runRootActionNoReload("强制停止",new Runnable(){public void run(){RootHelper.forceStopApp(app.packageName,MainActivity.this);}});}});
        addAction(list,"\ud83d\uddd1 清除数据","#FF9500",true,"需Root",new Runnable(){public void run(){d.dismiss();showIosConfirm("清除数据","确定清除"+app.appName+"的数据？","清除","#FF3B30",new Runnable(){public void run(){runRootActionNoReload("清除数据",new Runnable(){public void run(){RootHelper.clearAppData(app.packageName,MainActivity.this);}});}});}});
        addAction(list,"\ud83d\udd12 冻结","#FF9500",true,"需Root",new Runnable(){public void run(){d.dismiss();runRootAction("冻结",new Runnable(){public void run(){RootHelper.freezeApp(app.packageName);}});}});
        addAction(list,"\ud83d\udd13 解冻","#34C759",true,"需Root",new Runnable(){public void run(){d.dismiss();runRootAction("解冻",new Runnable(){public void run(){RootHelper.unfreezeApp(app.packageName);}});}});
        addAction(list,"\u26a1 Root\u6743\u9650","#FF9500",true,"需Root",new Runnable(){public void run(){d.dismiss();grantRoot(app);}});
        addAction(list,"\u274c \u5378\u8f7d(Root)","#FF3B30",true,"需Root",new Runnable(){public void run(){d.dismiss();uninstallRoot(app);}});
        if(!hasRoot&&!app.isSystemApp)addAction(list,"\u274c \u5378\u8f7d","#FF3B30",false,null,new Runnable(){public void run(){d.dismiss();uninstallNormal(app);}});
        cancel.setOnClickListener(new View.OnClickListener(){public void onClick(View v){d.dismiss();}});
        d.setContentView(v);Window w=d.getWindow();
        if(w!=null){w.setLayout(-1,-2);w.setGravity(Gravity.BOTTOM);
            w.setWindowAnimations(R.style.IOSSheetAnimation);w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));}
        d.show();
    }

    private void addAction(LinearLayout p,String text,String color,boolean needsRoot,String badgeLabel,final Runnable action){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(20,0,20,0);
        row.setBackgroundResource(R.drawable.ios_sheet_item_bg);
        TextView tv=new TextView(this);tv.setText(text);tv.setTextSize(16);
        tv.setTextColor(Color.parseColor(color));
        tv.setLayoutParams(new LinearLayout.LayoutParams(0,dp(56),1));
        tv.setGravity(Gravity.CENTER_VERTICAL);row.addView(tv);
        if(needsRoot){TextView lk=new TextView(this);lk.setText(badgeLabel!=null?badgeLabel:"需Root");lk.setTextSize(10);
        lk.setTextColor(Color.parseColor("#FF9500"));lk.setPadding(dp(6),dp(2),dp(6),dp(2));
        lk.setBackgroundResource(R.drawable.ios_btn_bg);row.addView(lk);}
        row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
            if(needsRoot&&!hasRoot){
                showIosConfirm("需要权限","此功能需要 Root 权限，请先获取 Root 后重试。","确定","#FF9500",new Runnable(){public void run(){}});return;
            }
            action.run();
        }});
        View div=new View(this);div.setLayoutParams(new LinearLayout.LayoutParams(-1,1));
        div.setBackgroundColor(Color.parseColor("#F2F2F7"));p.addView(row);p.addView(div);
    }

    private void launchApp(AppInfo a){Intent i=getPackageManager().getLaunchIntentForPackage(a.packageName);if(i!=null)startActivity(i);else Toast.makeText(this,"无法启动",Toast.LENGTH_SHORT).show();}

    private void extractApk(final AppInfo a){
        if(a.isSystemApp && !hasRoot){Toast.makeText(this,"提取系统应用需要Root",Toast.LENGTH_LONG).show();return;}
        final ProgressDialog pd=ProgressDialog.show(this,"","提取中…",true,false);
        new Thread(new Runnable(){public void run(){
            String fn=a.appName.replaceAll("[^a-zA-Z0-9._-]","_")+"_"+a.versionName+".apk";
            File tmp=new File(getExternalFilesDir("apks"),fn);
            String r=RootHelper.extractApk(a.packageName,tmp.getAbsolutePath(),a.isSystemApp,MainActivity.this);
            if(!tmp.exists()||tmp.length()==0){
                runOnUiThread(new Runnable(){public void run(){pd.dismiss();Toast.makeText(MainActivity.this,"提取失败："+r,Toast.LENGTH_LONG).show();}});
                return;
            }
            // Try1: shell cp to Download/Xuan
            String destDir="/sdcard/Download/Xuan";
            String destPath=destDir+"/"+fn;
            runShell("mkdir -p "+destDir+" 2>/dev/null; cp "+tmp.getAbsolutePath()+" "+destPath+" 2>/dev/null");
            boolean ok=new File(destPath).exists()&&new File(destPath).length()>0;
            // Try2: MediaStore Files with relative_path
            if(!ok){try{
                ContentValues cv=new ContentValues();
                cv.put("_display_name",fn);
                cv.put("mime_type","application/vnd.android.package-archive");
                cv.put("relative_path","Download/Xuan/");
                if(Build.VERSION.SDK_INT>=29)cv.put("is_pending",1);
                Uri uri=getContentResolver().insert(Uri.parse("content://media/external/file"),cv);
                if(uri!=null){
                    OutputStream os=getContentResolver().openOutputStream(uri);
                    FileInputStream fis=new FileInputStream(tmp);
                    byte[] b=new byte[8192];int n;
                    while((n=fis.read(b))>0)os.write(b,0,n);
                    fis.close();os.close();
                    if(Build.VERSION.SDK_INT>=29){ContentValues up=new ContentValues();up.put("is_pending",0);getContentResolver().update(uri,up,null,null);}
                    ok=true;destPath="Download/Xuan/"+fn;
                }
            }catch(Exception e){}}
            // Try3: direct file write
            if(!ok){try{
                File df=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"Xuan/"+fn);
                df.getParentFile().mkdirs();
                FileInputStream fis2=new FileInputStream(tmp);
                FileOutputStream fos2=new FileOutputStream(df);
                byte[] b2=new byte[8192];int n2;
                while((n2=fis2.read(b2))>0)fos2.write(b2,0,n2);
                fis2.close();fos2.close();
                ok=df.exists();if(ok)destPath="Download/Xuan/"+fn;
            }catch(Exception e){}}
            final boolean finalOk=ok;final String finalPath=destPath;
            tmp.delete();
            runOnUiThread(new Runnable(){public void run(){pd.dismiss();
                Toast.makeText(MainActivity.this,finalOk?"\u63d0\u53d6\u5b8c\u6210\uff1a"+finalPath:"\u5199\u5165\u5931\u8d25\uff0c\u8bf7\u5728\u8bbe\u7f6e\u4e2d\u6388\u4e88\u201c\u6240\u6709\u6587\u4ef6\u8bbf\u95ee\u201d\u6743\u9650",Toast.LENGTH_LONG).show();
            }});
        }}).start();
    }

    private void uninstallRoot(final AppInfo a){
        showIosConfirm("确认卸载","使用Root卸载"+a.appName+"？","卸载","#FF3B30",new Runnable(){public void run(){
            runRootAction("卸载",new Runnable(){public void run(){RootHelper.uninstallApp(a.packageName,a.isSystemApp);}});
        }});
    }

    private void uninstallNormal(final AppInfo a){
        showIosConfirm("确认卸载","确定要卸载"+a.appName+"吗？","卸载","#FF3B30",new Runnable(){public void run(){
            pendingRefresh = true;
            Intent i=new Intent(Intent.ACTION_DELETE);i.setData(Uri.parse("package:"+a.packageName));startActivity(i);
        }});
    }

    private void showDetail(AppInfo a){
        String s=formatSize(a.apkSize);
        String t=new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(a.installTime));
        String d="包名:"+a.packageName+"\n版本:"+a.versionName+"("+a.versionCode+")\nAPK大小:"+s+"\n安装时间:"+t+"\n目标SDK:"+a.targetSdk+"\n"+(a.isSystemApp?"系统应用":"用户应用")+"\n"+(a.isFrozen?"状态:已冻结":"状态:正常");
        showIosConfirm(a.appName,d,"关闭","#007AFF",new Runnable(){public void run(){}});
    }

    private void showSign(final AppInfo a){
        new AsyncTask<Void,Void,String>(){ProgressDialog pd;
            protected void onPreExecute(){pd=ProgressDialog.show(MainActivity.this,"","解析签名…");}
            protected String doInBackground(Void...v){
                File dir=new File(getExternalFilesDir("apks"),".tmp_sign.apk");
                RootHelper.extractApk(a.packageName,dir.getAbsolutePath(),a.isSystemApp,MainActivity.this);
                String r=RootHelper.getSignInfo(dir.getAbsolutePath());dir.delete();
                return r.isEmpty()?"无法获取":r;
            }
            protected void onPostExecute(String r){if(pd!=null&&pd.isShowing())pd.dismiss();showIosConfirm("签名信息",r,"关闭","#007AFF",new Runnable(){public void run(){}});}
        }.execute();
    }

    private void grantRoot(final AppInfo a){
        showIosConfirm("\u26a1 Root\u6743\u9650","\u7ed9 "+a.appName+" \u6c38\u4e45Root\u6743\u9650\uff0c\u786e\u5b9a\uff1f","\u786e\u5b9a\u6388\u6743","#FF9500",new Runnable(){public void run(){
            runRootAction("\u6388\u4e88Root",new Runnable(){public void run(){
                try{
                    int uid=getPackageManager().getPackageInfo(a.packageName,0).applicationInfo.uid;
                    // Try both Magisk new and old DB schemas
                    String r=RootHelper.runRootCommand(
                        "magisk --sqlite \"INSERT OR REPLACE INTO policies (uid,policy,until,logging,notification) VALUES ("+uid+",2,0,1,1);\" 2>&1");
                    if(r.contains("error")||r.contains("not found")||r.contains("No root")){
                        // Fallback: grant via su directly (Magisk will remember)
                        r=RootHelper.runRootCommand("echo 'granted' && pm grant "+a.packageName+" android.permission.ACCESS_SUPERUSER 2>&1 || true");
                    }
                    final boolean ok=!r.toLowerCase().contains("error")&&!r.contains("not found")&&!r.contains("No root");
                    final String msg=ok?"\u2705 "+a.appName+" UID="+uid+" \u5df2\u6388\u4e88":"\u274c \u5931\u8d25:"+r;
                    runOnUiThread(new Runnable(){public void run(){
                        Toast.makeText(MainActivity.this,msg,Toast.LENGTH_LONG).show();
                    }});
                }catch(Exception e){
                    runOnUiThread(new Runnable(){public void run(){Toast.makeText(MainActivity.this,"\u9519\u8bef:"+e.getMessage(),Toast.LENGTH_LONG).show();}});
                }
            }});
        }});
    }

    private void showPerms(final AppInfo a){
        final ProgressDialog pd=ProgressDialog.show(this,"","获取权限…",true,false);
        new Thread(new Runnable(){public void run(){
            String r="";
            try{
                Process p=Runtime.getRuntime().exec(new String[]{"sh","-c","dumpsys package "+a.packageName+" 2>/dev/null | grep 'granted=true' | sed 's/.*permission://;s/ granted=true//'"});
                java.io.BufferedReader br=new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                StringBuilder sb=new StringBuilder();String l;
                while((l=br.readLine())!=null){l=l.trim();if(!l.isEmpty())sb.append("\u2022 ").append(l).append("\n");}
                br.close();p.waitFor();r=sb.toString().trim();
            }catch(Exception e){r="";}
            if(r.isEmpty())r="无已授予的权限";
            final String fr=r;
            runOnUiThread(new Runnable(){public void run(){if(pd.isShowing())pd.dismiss();showIosConfirm("已授予权限",fr,"关闭","#007AFF",new Runnable(){public void run(){}});}});
        }}).start();
    }

    private void renamePkg(final AppInfo a){
        final Dialog d=new Dialog(this,R.style.IOSSheetDialog);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.ios_sheet_bg);root.setPadding(0,dp(18),0,dp(24));
        TextView tv=new TextView(this);tv.setText("改包名");tv.setTextSize(17);
        tv.setTextColor(Color.parseColor("#1C1C1E"));tv.setGravity(Gravity.CENTER);tv.setPadding(0,0,0,dp(8));root.addView(tv);
        final EditText et=new EditText(this);et.setText(a.packageName);et.setTextSize(16);
        et.setTextColor(Color.parseColor("#1C1C1E"));et.setPadding(dp(16),dp(10),dp(16),dp(10));
        et.setBackgroundResource(R.drawable.search_bar_bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(16),dp(8),dp(16),dp(16));et.setLayoutParams(lp);root.addView(et);
        View div=new View(this);div.setLayoutParams(new LinearLayout.LayoutParams(-1,1));div.setBackgroundColor(Color.parseColor("#E5E5EA"));root.addView(div);
        LinearLayout btns=new LinearLayout(this);btns.setOrientation(LinearLayout.HORIZONTAL);btns.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(50)));
        TextView cancel=new TextView(this);cancel.setText("取消");cancel.setTextSize(17);cancel.setTextColor(Color.parseColor("#007AFF"));cancel.setGravity(Gravity.CENTER);cancel.setLayoutParams(new LinearLayout.LayoutParams(0,-1,1));cancel.setOnClickListener(new View.OnClickListener(){public void onClick(View v){d.dismiss();}});btns.addView(cancel);
        View vd=new View(this);vd.setLayoutParams(new LinearLayout.LayoutParams(1,-1));vd.setBackgroundColor(Color.parseColor("#E5E5EA"));btns.addView(vd);
        TextView ok=new TextView(this);ok.setText("修改");ok.setTextSize(17);ok.setTextColor(Color.parseColor("#FF9500"));ok.setGravity(Gravity.CENTER);ok.setLayoutParams(new LinearLayout.LayoutParams(0,-1,1));
        ok.setOnClickListener(new View.OnClickListener(){public void onClick(View v){d.dismiss();final String np=et.getText().toString().trim();if(!np.isEmpty()&&!np.equals(a.packageName))runRootAction("改包名",new Runnable(){public void run(){RootHelper.runRootCommand("pm rename "+a.packageName+" "+np);}});}});
        btns.addView(ok);root.addView(btns);
        d.setContentView(root);Window w=d.getWindow();if(w!=null){w.setLayout(-1,-2);w.setGravity(Gravity.BOTTOM);w.setWindowAnimations(R.style.IOSSheetAnimation);w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));}d.show();
    }

    private void shareApk(final AppInfo a){
        runRootActionNoReload("提取APK",new Runnable(){public void run(){
            final File dir=new File(getExternalFilesDir("apks"),a.appName.replaceAll("[^a-zA-Z0-9._-]","_")+"_"+a.versionName+".apk");
            String r=RootHelper.extractApk(a.packageName,dir.getAbsolutePath(),a.isSystemApp,MainActivity.this);
            runOnUiThread(new Runnable(){public void run(){
                if(dir.exists()){
                    Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(Uri.fromFile(dir),"resource/folder");
                    try{startActivity(i);}catch(Exception e){Toast.makeText(MainActivity.this,"已保存到应用私有目录",Toast.LENGTH_LONG).show();}
                }else Toast.makeText(MainActivity.this,"提取失败",Toast.LENGTH_SHORT).show();
            }});
        }});
    }

    private void showBatchActions(){
        final Dialog d=new Dialog(this,R.style.IOSSheetDialog);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundResource(R.drawable.ios_sheet_bg);root.setPadding(0,dp(14),0,dp(28));
        TextView tv=new TextView(this);tv.setText("批量操作("+selectedPackages.size()+"个)");tv.setTextSize(13);tv.setTextColor(Color.parseColor("#8E8E93"));tv.setGravity(Gravity.CENTER);tv.setPadding(0,0,0,dp(8));root.addView(tv);
        View div=new View(this);div.setLayoutParams(new LinearLayout.LayoutParams(-1,1));div.setBackgroundColor(Color.parseColor("#E5E5EA"));root.addView(div);
        String[] items={"\ud83d\udce6批量提取","\u274c批量卸载","\ud83d\udd12批量冻结","\ud83d\udd13批量解冻","\ud83d\uddd1批量清除数据"};
        for(int i=0;i<items.length;i++){final int idx=i;TextView item=new TextView(this);item.setText(items[i]);item.setTextSize(16);item.setTextColor(Color.parseColor("#007AFF"));item.setGravity(Gravity.CENTER);item.setPadding(0,dp(14),0,dp(14));item.setBackgroundResource(R.drawable.ios_sheet_item_bg);
            item.setOnClickListener(new View.OnClickListener(){public void onClick(View v){d.dismiss();switch(idx){case 0: batchExtract(); break; case 1: batchUninstall(); break; case 2: batchFreeze(); break; case 3: batchUnfreeze(); break; case 4: batchClear(); break;}}});
            root.addView(item);if(i<items.length-1){View sep=new View(this);sep.setLayoutParams(new LinearLayout.LayoutParams(-1,1));sep.setBackgroundColor(Color.parseColor("#F2F2F7"));root.addView(sep);}}
        View gap=new View(this);gap.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(8)));gap.setBackgroundColor(Color.TRANSPARENT);root.addView(gap);
        TextView cancel=new TextView(this);cancel.setText("取消");cancel.setTextSize(17);cancel.setTextColor(Color.parseColor("#007AFF"));cancel.setGravity(Gravity.CENTER);cancel.setPadding(0,dp(14),0,dp(14));cancel.setBackgroundResource(R.drawable.ios_sheet_item_bg);cancel.setOnClickListener(new View.OnClickListener(){public void onClick(View v){d.dismiss();}});root.addView(cancel);
        d.setContentView(root);Window w=d.getWindow();if(w!=null){w.setLayout(-1,-2);w.setGravity(Gravity.BOTTOM);w.setWindowAnimations(R.style.IOSSheetAnimation);w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));}d.show();
    }

    private void batchExtract(){runRootActionNoReload("批量提取",new Runnable(){public void run(){
        File tmpDir=getExternalFilesDir("apks");
        for(String p:selectedPackages){
            AppInfo a=findApp(p);if(a==null)continue;
            String fn=a.appName.replaceAll("[^a-zA-Z0-9._-]","_")+"_"+a.versionName+".apk";
            File tmp=new File(tmpDir,fn);
            RootHelper.extractApk(p,tmp.getAbsolutePath(),a.isSystemApp,MainActivity.this);
            if(!tmp.exists()||tmp.length()==0)continue;
            try{
                ContentValues cv=new ContentValues();
                cv.put("_display_name",fn);
                cv.put("mime_type","application/vnd.android.package-archive");
                cv.put("relative_path","Download/Xuan");
                Uri uri=getContentResolver().insert(Uri.parse("content://media/external/downloads"),cv);
                if(uri!=null){
                    OutputStream os=getContentResolver().openOutputStream(uri);
                    FileInputStream fis=new FileInputStream(tmp);
                    byte[] b=new byte[8192];int n;
                    while((n=fis.read(b))>0)os.write(b,0,n);
                    fis.close();os.close();
                }
                tmp.delete();
            }catch(Exception e){}
        }
    }});resetBatch();}
    private void batchUninstall(){runRootAction("批量卸载",new Runnable(){public void run(){for(String p:selectedPackages){AppInfo a=findApp(p);RootHelper.uninstallApp(p,a!=null&&a.isSystemApp);}}});resetBatch();}
    private void batchFreeze(){runRootAction("批量冻结",new Runnable(){public void run(){for(String p:selectedPackages)RootHelper.freezeApp(p);}});resetBatch();}
    private void batchUnfreeze(){runRootAction("批量解冻",new Runnable(){public void run(){for(String p:selectedPackages)RootHelper.unfreezeApp(p);}});resetBatch();}
    private void batchClear(){runRootActionNoReload("批量清除数据",new Runnable(){public void run(){for(String p:selectedPackages)RootHelper.clearAppData(p,MainActivity.this);}});resetBatch();}
    private void resetBatch(){selectedPackages.clear();batchMode=false;btnBatch.setText("批量操作");btnBatch.setTextColor(Color.parseColor("#007AFF"));}

    private AppInfo findApp(String pkg){for(AppInfo a:allApps)if(a.packageName.equals(pkg))return a;return null;}
    private String formatSize(long sz){if(sz<=0)return"?";if(sz<1024)return sz+"B";if(sz<1048576)return String.format("%.1fKB",sz/1024.0);return String.format("%.1fMB",sz/1048576.0);}
    
class AppListAdapter extends BaseAdapter {
        public int getCount(){return filteredApps.size();}
        public Object getItem(int p){return filteredApps.get(p);}
        public long getItemId(int p){return p;}
        public View getView(int p,View cv,ViewGroup parent){
            if(cv==null)cv=LayoutInflater.from(MainActivity.this).inflate(R.layout.item_app,parent,false);
            final AppInfo a=filteredApps.get(p);
            ImageView icon = (ImageView) cv.findViewById(R.id.app_icon); icon.setImageDrawable(a.icon);
            TextView name = (TextView) cv.findViewById(R.id.app_name); name.setText(a.appName);
            TextView pkg = (TextView) cv.findViewById(R.id.app_package); pkg.setText(a.packageName);
            TextView meta = (TextView) cv.findViewById(R.id.app_meta);
            String mt=(a.isFrozen?"\u2744冻结 ":"")+(a.isSystemApp?"\ud83d\udd12系统":"\ud83d\udc64用户")+" \u00b7 "+formatSize(a.apkSize);
            meta.setText(mt);
            View extract=cv.findViewById(R.id.btn_extract);extract.setOnClickListener(new View.OnClickListener(){public void onClick(View v){extractApk(a);}});
            View menu=cv.findViewById(R.id.btn_menu);menu.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showActionSheet(a);}});
            return cv;
        }
    }
}