package com.kk.jsbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** On-screen browser chrome theme previews, named custom themes and interactive HSV picker. */
final class ThemeStudio {
    interface SaveTheme { void save(JSONObject theme); }
    private static final String KEY="custom_styles_v130";
    private static final String[] NAMES={
        "經典淺色","經典深色","極簡白","AMOLED 黑","柔和圓角",
        "科技藍","森林綠","暖色米白","玻璃感","緊湊工具型"
    };
    private static final String[] BACKGROUNDS={
        "#FFFFFF","#202329","#FAFAFA","#000000","#F2EFFA",
        "#121D32","#EAF2E9","#F8F1E7","#DCE8F3","#292D33"
    };
    private static final String[] FOREGROUNDS={
        "#141614","#F5F6F8","#222222","#FFFFFF","#342C50",
        "#F7FAFF","#24462C","#4E3522","#264362","#FFFFFF"
    };
    private static final String[] BARS={
        "#E5E6E9","#39404A","#EEEEEE","#181818","#DED8F5",
        "#24446C","#CEE3D0","#E8DAC7","#B3CDDF","#424B55"
    };
    private final Activity activity;
    private final SharedPreferences prefs;
    private final SaveTheme save;
    ThemeStudio(Activity activity,SharedPreferences prefs,SaveTheme save){
        this.activity=activity;this.prefs=prefs;this.save=save;
    }
    private JSONObject customTheme(JSONObject base,String name,String background,String foreground,String bar,int index){
        JSONObject theme;
        try{theme=new JSONObject(base.toString());}
        catch(Exception e){theme=new JSONObject();}
        try{
            theme.put("name",name);
            theme.put("preset",index);
            theme.put("backgroundColor",background);
            theme.put("textColor",foreground);
            theme.put("toolbarColor",bar);
            theme.put("borderRadius",index==9?6:18);
            theme.put("toolbarHeight",index==9?48:62);
        }catch(Exception ignored){}
        return theme;
    }
    private JSONArray customs(){
        try{return new JSONArray(prefs.getString(KEY,"[]"));}
        catch(Exception e){return new JSONArray();}
    }
    void showGallery(JSONObject current){
        ScrollView scroll=new ScrollView(activity);
        LinearLayout list=new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12),dp(10),dp(12),dp(10));
        scroll.addView(list);
        TextView intro=new TextView(activity);
        intro.setText("選擇風格可先預覽，再決定是否套用。");
        intro.setPadding(dp(8),0,dp(8),dp(9));
        list.addView(intro);
        final AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("介面風格")
            .setView(scroll).setNegativeButton("完成",null).create();
        for(int i=0;i<NAMES.length;i++){
            final int index=i;
            JSONObject candidate=customTheme(current,NAMES[i],BACKGROUNDS[i],FOREGROUNDS[i],BARS[i],i);
            showItem(list,candidate,()->preview(candidate,index,dialog));
        }
        JSONArray customs=customs();
        for(int i=0;i<customs.length();i++){
            JSONObject candidate=customs.optJSONObject(i);
            if(candidate==null)continue;
            final int index=i;
            showItem(list,candidate,()->previewCustom(candidate,index,dialog));
        }
        Button create=new Button(activity);
        create.setAllCaps(false);create.setText("＋ 儲存目前外觀為自訂風格");
        list.addView(create);
        create.setOnClickListener(v->{
            EditText name=new EditText(activity);name.setSingleLine(true);name.setHint("風格名稱");
            new AlertDialog.Builder(activity).setTitle("新增自訂風格").setView(name)
                .setPositiveButton("新增",(d,w)->{
                    String title=name.getText().toString().trim();
                    if(title.isEmpty()){toast("請輸入名稱");return;}
                    JSONArray all=customs();
                    JSONObject entry;
                    try{
                        entry=new JSONObject(current.toString());
                        entry.put("name",title);
                        entry.put("preset",-1);
                        all.put(entry);
                        prefs.edit().putString(KEY,all.toString()).apply();
                    }catch(Exception e){toast("無法儲存");return;}
                    dialog.dismiss();
                    showGallery(current);
                }).setNegativeButton("取消",null).show();
        });
        dialog.show();
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
            (int)(activity.getResources().getDisplayMetrics().heightPixels*.84));
    }
    private void showItem(LinearLayout list,JSONObject data,Runnable click){
        int foreground=color(data.optString("textColor"),Color.BLACK);
        int background=color(data.optString("backgroundColor"),Color.WHITE);
        int barColor=color(data.optString("toolbarColor"),0xffdddddd);
        LinearLayout item=new LinearLayout(activity);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(9),dp(9),dp(9),dp(9));
        LinearLayout.LayoutParams itemParams=new LinearLayout.LayoutParams(-1,-2);
        itemParams.bottomMargin=dp(8);
        list.addView(item,itemParams);
        item.setBackground(stroke(Color.LTGRAY,12));
        TextView title=new TextView(activity);
        title.setText(data.optString("name","自訂風格")+"  ("+
            ("bottom".equals(data.optString("toolbarPosition"))?"底部":"頂部")+")");
        title.setTextSize(16);title.setTypeface(null,Typeface.BOLD);
        item.addView(title);
        FrameLayout preview=new FrameLayout(activity);
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,dp(107));
        layout.topMargin=dp(7);
        item.addView(preview,layout);
        preview.setBackground(rounded(background,12));
        LinearLayout fakePage=new LinearLayout(activity);
        fakePage.setOrientation(LinearLayout.VERTICAL);fakePage.setPadding(dp(13),dp(10),dp(12),dp(6));
        FrameLayout.LayoutParams pageParams=new FrameLayout.LayoutParams(-1,-1);
        preview.addView(fakePage,pageParams);
        TextView pageTitle=new TextView(activity);
        pageTitle.setText("Web page");pageTitle.setTextColor(foreground);pageTitle.setTextSize(13);
        fakePage.addView(pageTitle);
        View line=new View(activity);
        line.setBackgroundColor(foreground);
        LinearLayout.LayoutParams lineParams=new LinearLayout.LayoutParams(dp(108),dp(2));
        lineParams.topMargin=dp(8);
        fakePage.addView(line,lineParams);
        LinearLayout bar=new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12),0,dp(7),0);
        FrameLayout.LayoutParams pos=new FrameLayout.LayoutParams(-1,dp(33),
            "bottom".equals(data.optString("toolbarPosition"))?Gravity.BOTTOM:Gravity.TOP);
        preview.addView(bar,pos);
        bar.setBackground(rounded(barColor,10));
        TextView url=new TextView(activity);
        url.setText("example.com");url.setTextColor(foreground);url.setTextSize(12);
        bar.addView(url,new LinearLayout.LayoutParams(0,-2,1));
        for(String symbol:new String[]{"⌕","⋮","×"}){
            TextView button=new TextView(activity);
            button.setText(symbol);button.setTextColor(foreground);
            button.setGravity(Gravity.CENTER);button.setTextSize(19);
            bar.addView(button,new LinearLayout.LayoutParams(dp(27),-1));
        }
        item.setOnClickListener(v->click.run());
    }
    private void preview(JSONObject theme,int index,AlertDialog parent){
        new AlertDialog.Builder(activity).setTitle(theme.optString("name"))
            .setMessage("是否套用這個瀏覽器介面風格？")
            .setPositiveButton("套用",(d,w)->{save.save(theme);parent.dismiss();})
            .setNeutralButton("切換頂部／底部",(d,w)->{
                try{
                    JSONObject alternate=new JSONObject(theme.toString());
                    alternate.put("toolbarPosition",
                        "bottom".equals(theme.optString("toolbarPosition"))?"top":"bottom");
                    preview(alternate,index,parent);
                }catch(Exception ignored){}
            }).setNegativeButton("取消",null).show();
    }
    private void previewCustom(JSONObject candidate,int index,AlertDialog parent){
        new AlertDialog.Builder(activity).setTitle(candidate.optString("name","自訂風格"))
            .setItems(new String[]{"預覽與套用","重新命名","刪除風格"},(d,which)->{
                if(which==0)preview(candidate,index,parent);
                else if(which==1){
                    EditText edit=new EditText(activity);edit.setText(candidate.optString("name"));
                    new AlertDialog.Builder(activity).setTitle("重新命名").setView(edit)
                        .setPositiveButton("儲存",(a,b)->{
                            try{
                                JSONArray all=customs();
                                all.getJSONObject(index).put("name",edit.getText().toString());
                                prefs.edit().putString(KEY,all.toString()).apply();
                                parent.dismiss();showGallery(candidate);
                            }catch(Exception e){toast("無法儲存");}
                        }).setNegativeButton("取消",null).show();
                }else {
                    new AlertDialog.Builder(activity).setMessage("確定刪除這個自訂風格？")
                        .setPositiveButton("刪除",(a,b)->{
                            JSONArray all=customs(),remaining=new JSONArray();
                            for(int i=0;i<all.length();i++)if(i!=index)remaining.put(all.opt(i));
                            prefs.edit().putString(KEY,remaining.toString()).apply();
                            parent.dismiss();showGallery(candidate);
                        }).setNegativeButton("取消",null).show();
                }
            }).show();
    }
    /** Color picker with live HSV preview, three SeekBars, and editable hexadecimal value. */
    void pickColor(String title,int initial,java.util.function.IntConsumer preview,java.util.function.IntConsumer commit){
        LinearLayout body=new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20),dp(8),dp(20),dp(4));
        View swatch=new View(activity);
        body.addView(swatch,new LinearLayout.LayoutParams(-1,dp(60)));
        EditText hex=new EditText(activity);
        hex.setSingleLine(true);hex.setInputType(InputType.TYPE_CLASS_TEXT);
        hex.setText(String.format(Locale.US,"#%06X",initial&0xffffff));
        body.addView(hex);
        float[] hsv=new float[3];
        Color.colorToHSV(initial,hsv);
        int[] current=new int[]{initial};
        int[] ranges={360,100,100};
        String[] labels={"色相","飽和度","明度"};
        boolean[] locked={false};
        for(int i=0;i<3;i++){
            final int idx=i;
            TextView label=new TextView(activity);label.setText(labels[i]);body.addView(label);
            SeekBar seek=new SeekBar(activity);
            seek.setMax(ranges[i]);
            seek.setProgress(Math.round(hsv[i]*(i==0?1:100)));
            body.addView(seek);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onStartTrackingTouch(SeekBar b){}
                public void onStopTrackingTouch(SeekBar b){}
                public void onProgressChanged(SeekBar b,int p,boolean fromUser){
                    if(!fromUser)return;
                    hsv[idx]=idx==0?p:p/100f;
                    current[0]=Color.HSVToColor(hsv);
                    swatch.setBackgroundColor(current[0]);
                    locked[0]=true;
                    hex.setText(String.format(Locale.US,"#%06X",current[0]&0xffffff));
                    locked[0]=false;
                    preview.accept(current[0]);
                }
            });
        }
        swatch.setBackgroundColor(initial);
        hex.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count){
                if(locked[0])return;
                try{
                    int color=Color.parseColor(s.toString().trim());
                    current[0]=color;swatch.setBackgroundColor(color);preview.accept(color);
                }catch(Exception ignored){}
            }
            public void afterTextChanged(android.text.Editable e){}
        });
        new AlertDialog.Builder(activity).setTitle(title).setView(body)
            .setPositiveButton("儲存",(d,w)->commit.accept(current[0]))
            .setNegativeButton("取消",(d,w)->preview.accept(initial))
            .setOnCancelListener(d->preview.accept(initial)).show();
    }
    /** Preview numeric layout attributes using both a slider and an exact-value field. */
    void pickNumber(String title,double initial,double min,double max,double step,
                    java.util.function.DoubleConsumer preview,
                    java.util.function.DoubleConsumer save) {
        LinearLayout panel=new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20),dp(10),dp(20),dp(5));
        TextView readout=new TextView(activity);
        readout.setText(String.format(Locale.US,"目前 %.2f",initial));
        panel.addView(readout);
        SeekBar slider=new SeekBar(activity);
        int steps=Math.max(1,(int)Math.round((max-min)/step));
        slider.setMax(steps);
        slider.setProgress((int)Math.round((initial-min)/step));
        panel.addView(slider);
        EditText input=new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|
                           InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(String.format(Locale.US,"%.2f",initial));
        panel.addView(input);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
            public void onProgressChanged(SeekBar b,int value,boolean fromUser){
                if(!fromUser)return;
                double n=min+step*value;
                input.setText(String.format(Locale.US,"%.2f",n));
                readout.setText(String.format(Locale.US,"預覽 %.2f",n));
                preview.accept(n);
            }
        });
        final AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(title)
            .setView(panel).setPositiveButton("儲存",null)
            .setNegativeButton("取消",(d,w)->preview.accept(initial))
            .create();
        dialog.setOnCancelListener(d->preview.accept(initial));
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                double number=Double.parseDouble(input.getText().toString().trim());
                if(!Double.isFinite(number)||number<min||number>max)throw new IllegalArgumentException();
                save.accept(number);
                dialog.dismiss();
            }catch(Exception e){toast("數值範圍需介於 "+min+" 和 "+max);}
        }));
        dialog.show();
    }
    private GradientDrawable rounded(int color,int radius){
        GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(radius));return bg;
    }
    private GradientDrawable stroke(int color,int radius){
        GradientDrawable bg=rounded(Color.WHITE,radius);bg.setStroke(dp(1),color);return bg;
    }
    private int color(String value,int fallback){
        try{return Color.parseColor(value);}catch(Exception e){return fallback;}
    }
    private int dp(int value){return (int)(value*activity.getResources().getDisplayMetrics().density+.5f);}
    private void toast(String message){Toast.makeText(activity,message,Toast.LENGTH_SHORT).show();}
}
