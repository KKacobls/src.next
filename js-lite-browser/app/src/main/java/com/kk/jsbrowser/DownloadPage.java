package com.kk.jsbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/** Native DownloadManager-based history with persisted metadata and per-item actions. */
final class DownloadPage {
    private static final String KEY="download_items_v130";
    private final Activity activity;
    private final SharedPreferences prefs;
    private final DownloadManager manager;
    private AlertDialog shown;
    private LinearLayout items;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshTask=new Runnable(){
        @Override public void run(){
            if(shown!=null && shown.isShowing()){
                populate();
                handler.postDelayed(this,1200);
            }
        }
    };
    DownloadPage(Activity activity){
        this.activity=activity;
        prefs=activity.getSharedPreferences("browser_settings",Activity.MODE_PRIVATE);
        manager=(DownloadManager)activity.getSystemService(Context.DOWNLOAD_SERVICE);
    }
    private JSONArray entries(){
        try{return new JSONArray(prefs.getString(KEY,"[]"));}
        catch(Exception e){return new JSONArray();}
    }
    void record(long id,String name,String url){
        JSONArray old=entries(),next=new JSONArray();
        try{
            JSONObject entry=new JSONObject();
            entry.put("id",id);entry.put("name",name);entry.put("url",url);
            entry.put("time",System.currentTimeMillis());
            next.put(entry);
            for(int i=0;i<old.length() && next.length()<150;i++)next.put(old.get(i));
            prefs.edit().putString(KEY,next.toString()).apply();
        }catch(Exception ignored){}
    }
    void show(){
        ScrollView scroll=new ScrollView(activity);
        items=new LinearLayout(activity);
        items.setPadding(dp(16),dp(12),dp(16),dp(10));
        items.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(items);
        shown=new AlertDialog.Builder(activity).setTitle("下載")
           .setView(scroll).setPositiveButton("下載資料夾",(d,w)->{
                try{
                    Intent open=new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
                    activity.startActivity(open);
                }catch(Exception e){toast("無法開啟下載資料夾");}
           }).setNegativeButton("關閉",null).create();
        shown.setOnDismissListener(d->{handler.removeCallbacks(refreshTask);items=null;shown=null;});
        shown.show();
        if(shown.getWindow()!=null)shown.getWindow().setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (int)(activity.getResources().getDisplayMetrics().heightPixels*.82));
        populate();
        handler.postDelayed(refreshTask,1200);
    }
    private void populate(){
        if(items==null)return;
        items.removeAllViews();
        JSONArray data=entries();
        if(data.length()==0){
            TextView empty=text("尚無下載紀錄",16);
            items.addView(empty);
            return;
        }
        for(int i=0;i<data.length();i++){
            JSONObject info=data.optJSONObject(i);
            if(info==null)continue;
            long id=info.optLong("id");
            String name=info.optString("name","下載檔案");
            String source=info.optString("url","");
            int status=-1,progress=0;
            String error="";
            long bytes=0,total=0;
            Uri local=null;
            try(Cursor cursor=manager.query(new DownloadManager.Query().setFilterById(id))){
                if(cursor!=null && cursor.moveToFirst()){
                    status=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    bytes=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    total=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    if(total>0)progress=(int)Math.min(100,bytes*100/total);
                    String localString=cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
                    if(localString!=null)local=Uri.parse(localString);
                    if(status==DownloadManager.STATUS_FAILED)
                        error=" · 錯誤碼 "+cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                }
            }catch(Exception ignored){}
            String state=status==DownloadManager.STATUS_SUCCESSFUL?"已完成":
                status==DownloadManager.STATUS_FAILED?"下載失敗":
                status==DownloadManager.STATUS_PAUSED?"已暫停":
                status==DownloadManager.STATUS_RUNNING?"下載中":"等待中";
            LinearLayout card=new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10),dp(10),dp(10),dp(10));
            items.addView(card,new LinearLayout.LayoutParams(-1,-2));
            TextView title=text(name,15);
            card.addView(title);
            String date=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,Locale.TAIWAN)
                .format(new Date(info.optLong("time",0)));
            TextView subtitle=text(state+" · "+progress+"% · "+human(bytes)+(total>0?" / "+human(total):"")+error+"\n"+date,12);
            card.addView(subtitle);
            if(status==DownloadManager.STATUS_RUNNING || status==DownloadManager.STATUS_PENDING){
                ProgressBar bar=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
                bar.setIndeterminate(total<=0);
                if(total>0){bar.setMax(100);bar.setProgress(progress);}
                card.addView(bar,new LinearLayout.LayoutParams(-1,dp(5)));
            }
            LinearLayout actions=new LinearLayout(activity);
            actions.setGravity(Gravity.END);
            card.addView(actions);
            final Uri completed=local;
            if(status==DownloadManager.STATUS_SUCCESSFUL){
                addButton(actions,"開啟",()->{
                    try{
                        Uri uri=manager.getUriForDownloadedFile(id);
                        if(uri==null)uri=completed;
                        if(uri==null)throw new IllegalStateException("檔案已不存在");
                        Intent open=new Intent(Intent.ACTION_VIEW);
                        open.setDataAndType(uri,manager.getMimeTypeForDownloadedFile(id));
                        open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        activity.startActivity(open);
                    }catch(Exception e){toast("無法開啟檔案");}
                });
            }else if(status==DownloadManager.STATUS_RUNNING || status==DownloadManager.STATUS_PENDING || status==DownloadManager.STATUS_PAUSED)
                addButton(actions,"取消",()->{manager.remove(id);remove(id);populate();});
            addButton(actions,"資訊",()->new AlertDialog.Builder(activity)
                .setTitle(name).setMessage("來源：\n"+source+"\n\n下載編號："+id+"\n狀態："+state+"\n時間："+date)
                .setPositiveButton("關閉",null).show());
            addButton(actions,"移除紀錄",()->{remove(id);populate();});
            View divider=new View(activity);
            divider.setBackgroundColor(0xffe3e3e3);
            card.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        }
    }
    private void remove(long id){
        JSONArray entries=entries(),remaining=new JSONArray();
        for(int i=0;i<entries.length();i++){
            JSONObject row=entries.optJSONObject(i);
            if(row!=null && row.optLong("id")!=id)remaining.put(row);
        }
        prefs.edit().putString(KEY,remaining.toString()).apply();
    }
    private void addButton(LinearLayout parent,String label,Runnable task){
        Button button=new Button(activity);button.setAllCaps(false);button.setText(label);button.setTextSize(11);
        parent.addView(button,new LinearLayout.LayoutParams(-2,dp(42)));
        button.setOnClickListener(v->task.run());
    }
    private String human(long size){
        if(size<1024)return size+" B";
        if(size<1024*1024)return String.format(Locale.US,"%.1f KB",size/1024.0);
        return String.format(Locale.US,"%.1f MB",size/1048576.0);
    }
    private TextView text(String msg,int size){
        TextView t=new TextView(activity);t.setText(msg);t.setTextSize(size);
        t.setPadding(0,dp(4),0,dp(4));return t;
    }
    private void toast(String msg){Toast.makeText(activity,msg,Toast.LENGTH_SHORT).show();}
    private int dp(int v){return (int)(v*activity.getResources().getDisplayMetrics().density+.5f);}
}
