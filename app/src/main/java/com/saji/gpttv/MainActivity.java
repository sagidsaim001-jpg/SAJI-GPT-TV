package com.saji.gpttv;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://chatgpt.com/";
    private static final int REQ_MIC = 4107;
    private FrameLayout root;
    private LinearLayout rail;
    private WebView web;
    private PermissionRequest pendingPermission;
    private View back, mic, live, speak, send, up, down, site, mobile;
    private TextView exit;
    private boolean railPressed;
    private boolean mobileMode;
    private boolean manualMicPending;
    private String defaultAgent;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            enterImmersive();
            buildUi();
            buildWebView();
            web.loadUrl(HOME_URL);
            web.requestFocus();
        } catch (Throwable error) {
            showStartupError(error);
        }
    }

    private void enterImmersive() {
        try {
            Window w=getWindow();
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT>=30) {
                w.setDecorFitsSystemWindows(false);
                WindowInsetsController c=w.getInsetsController();
                if(c!=null){c.hide(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}
            } else {
                w.getDecorView().setSystemUiVisibility(5894|1024|512|4096);
            }
        } catch (Throwable ignored) {
            try { getWindow().getDecorView().setSystemUiVisibility(5894|1024|512|4096); }
            catch (Throwable ignoredAgain) { }
        }
    }

    private void showStartupError(Throwable error) {
        String detail=error.getClass().getSimpleName();
        if(error.getMessage()!=null) detail += "\\n" + error.getMessage();
        TextView message=new TextView(this);
        message.setText("SAJI GPT STARTUP ERROR\\n\\n"+detail+
            "\\n\\nAndroid System WebView ya device compatibility issue ho sakta hai.\\n"+
            "Is screen ka screenshot bhejo, error identify kar lenge.");
        message.setTextColor(Color.WHITE);
        message.setTextSize(18);
        message.setGravity(Gravity.CENTER);
        message.setPadding(dp(28),dp(28),dp(28),dp(28));
        message.setBackgroundColor(0xFF070B16);
        message.setFocusable(true);
        message.setOnClickListener(v -> recreate());
        setContentView(message);
    }

    private void buildUi() {
        root=new FrameLayout(this);
        root.setBackgroundColor(0xFF070B16);
        setContentView(root);

        rail=new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER);
        rail.setPadding(dp(5),dp(5),dp(5),dp(5));
        rail.setBackground(panel(false));
        FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(dp(72),-1,Gravity.START|Gravity.CENTER_VERTICAL);
        rp.topMargin=dp(7); rp.bottomMargin=dp(7); rp.leftMargin=dp(5);
        root.addView(rail,rp);

        back=addButton(0,"BACK");
        mic=addButton(1,"MIC");
        live=addButton(7,"LIVE");
        speak=addButton(8,"SPEAK");
        send=addButton(3,"SEND");
        up=addButton(4,"UP");
        down=addButton(5,"DOWN");
        site=addButton(9,"SITE");
        mobile=addButton(6,"MOBILE");

        addToRail(back); addToRail(mic); addToRail(live); addToRail(speak);
        addToRail(send); addToRail(up); addToRail(down); addToRail(site); addToRail(mobile);

        back.setOnClickListener(v -> goBack());
        mic.setOnClickListener(v -> activateMic());
        live.setOnClickListener(v -> activateLive());
        speak.setOnClickListener(v -> readLatest());
        send.setOnClickListener(v -> sendMessage());
        up.setOnClickListener(v -> scroll(-1));
        down.setOnClickListener(v -> scroll(1));
        site.setOnClickListener(v -> showMicSettings());
        mobile.setOnClickListener(v -> toggleLayout());

        View[] order={back,mic,live,speak,send,up,down,site,mobile};
        for(int i=0;i<order.length;i++){
            View v=order[i];
            v.setNextFocusUpId(order[Math.max(0,i-1)].getId());
            v.setNextFocusDownId(order[Math.min(order.length-1,i+1)].getId());
            v.setNextFocusRightId(View.NO_ID);
        }
        exit=new TextView(this);
        exit.setText("EXIT  ×");
        exit.setTextColor(Color.WHITE);
        exit.setTextSize(12);
        exit.setTypeface(Typeface.DEFAULT_BOLD);
        exit.setGravity(Gravity.CENTER);
        exit.setFocusable(true);
        exit.setClickable(true);
        exit.setPadding(dp(15),dp(9),dp(15),dp(9));
        exit.setBackground(buttonBg(false,0xFFB9C8F8));
        FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(-2,dp(38),Gravity.TOP|Gravity.END);
        ep.topMargin=dp(12); ep.rightMargin=dp(14);
        root.addView(exit,ep);
        exit.setOnFocusChangeListener((v,f)->focusStyle(v,f,0xFFB9C8F8));
        exit.setOnClickListener(v -> finish());
        exit.setNextFocusDownId(back.getId());
        back.setNextFocusRightId(exit.getId());
        exit.setNextFocusLeftId(back.getId());
    }

    private void addToRail(View v) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,0,1f);
        p.setMargins(dp(1),dp(1),dp(1),dp(1));
        rail.addView(v,p);
    }

    private View addButton(int mode,String label) {
        LinearLayout b=new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true); b.setFocusableInTouchMode(true); b.setClickable(true);
        b.setId(View.generateViewId());
        b.setBackground(buttonBg(false,accent(mode)));
        Icon icon=new Icon(mode);
        b.addView(icon,new LinearLayout.LayoutParams(dp(22),dp(22)));
        TextView t=new TextView(this);
        t.setText(label); t.setTextColor(0xFFE7ECFF); t.setTextSize(7.5f);
        t.setTypeface(Typeface.DEFAULT_BOLD); t.setLetterSpacing(.10f);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);
        tp.topMargin=dp(2); b.addView(t,tp);
        b.setContentDescription(label);
        b.setOnFocusChangeListener((v,f)->{
            v.animate().scaleX(f?1.08f:1f).scaleY(f?1.08f:1f).setDuration(135).start();
            v.setBackground(buttonBg(f,accent(mode)));
            v.setElevation(dp(f?10:2)); icon.focused=f; icon.invalidate();
        });
        b.setOnKeyListener((v,key,event)->{
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){
                if(event.getAction()==KeyEvent.ACTION_DOWN){railPressed=true;return true;}
                if(event.getAction()==KeyEvent.ACTION_UP){if(railPressed){railPressed=false;v.performClick();}return true;}
            }
            return false;
        });
        return b;
    }

    private int accent(int m) {
        if(m==1)return 0xFF56A5FF;
        if(m==7)return 0xFFFFC45C;
        if(m==8)return 0xFF9B8CFF;
        if(m==3)return 0xFF4AD7B0;
        if(m==4||m==5)return 0xFF80B7FF;
        if(m==9)return 0xFFFF8D9B;
        if(m==6)return 0xFFB4C4FF;
        return 0xFFE0E8FF;
    }
    private GradientDrawable panel(boolean focused) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xE52B3B5C,0xE811192E});
        d.setCornerRadius(dp(22)); d.setStroke(dp(1),focused?0xFFB9CCFF:0x557F9AE0); return d;
    }
    private GradientDrawable buttonBg(boolean focused,int color) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                focused?new int[]{0xFF526A9C,0xFF26395F}:new int[]{0xD9364A72,0xD91A2946});
        d.setCornerRadius(dp(14));
        d.setStroke(focused?dp(2):dp(1),focused?Color.WHITE:((color&0x00FFFFFF)|0x66000000));
        return d;
    }
    private void focusStyle(View v,boolean f,int color){
        v.animate().scaleX(f?1.08f:1f).scaleY(f?1.08f:1f).setDuration(140).start();
        v.setBackground(buttonBg(f,color)); v.setElevation(dp(f?10:2));
    }

    private void buildWebView() {
        web=new WebView(this);
        web.setId(View.generateViewId());
        web.setBackgroundColor(0xFF070B16);
        web.setFocusable(true); web.setFocusableInTouchMode(true);
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true); s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false); s.setBuiltInZoomControls(false); s.setDisplayZoomControls(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true); s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT); s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        defaultAgent=s.getUserAgentString().replace("; wv","").replace("Version/4.0 ","");
        s.setUserAgentString(defaultAgent);
        CookieManager cm=CookieManager.getInstance(); cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web,true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return false;}
            @Override public void onPageFinished(WebView v,String url){enterImmersive();super.onPageFinished(v,url);}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onPermissionRequest(PermissionRequest request){
                boolean audio=false;
                for(String r:request.getResources()) if(PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r))audio=true;
                if(!audio){request.deny();return;}
                if(Build.VERSION.SDK_INT<23||checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){
                    request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                } else {
                    pendingPermission=request;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);
                }
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest r){if(pendingPermission==r)pendingPermission=null;}
        });
        FrameLayout.LayoutParams wp=new FrameLayout.LayoutParams(-1,-1);
        wp.leftMargin=dp(82);
        root.addView(web,0,wp);
        web.setNextFocusLeftId(mobile.getId());
        back.setNextFocusRightId(web.getId()); mic.setNextFocusRightId(web.getId());
        live.setNextFocusRightId(web.getId()); speak.setNextFocusRightId(web.getId());
        send.setNextFocusRightId(web.getId()); up.setNextFocusRightId(web.getId());
        down.setNextFocusRightId(web.getId()); site.setNextFocusRightId(web.getId());
        mobile.setNextFocusRightId(web.getId());
    }

    private void runJs(String js, android.webkit.ValueCallback<String> cb) {
        if(web!=null) web.evaluateJavascript("(function(){try{"+js+"}catch(e){return 'error';}})()",cb);
    }
    private void activateMic() {
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            manualMicPending=true; requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC); return;
        }
        runJs("var bs=[].slice.call(document.querySelectorAll('button,[role=button]'));"
            +"var b=bs.find(x=>{var a=((x.getAttribute('aria-label')||'')+' '+(x.title||'')+' '+(x.innerText||'')).toLowerCase();"
            +"var r=x.getBoundingClientRect();return r.width>0&&r.height>0&&/dictat|microphone|voice input|start voice/.test(a)&&!/voice mode|advanced voice/.test(a)});"
            +"if(b){b.click();return 'ok'}return 'not-found';",this::micResult);
    }
    private void micResult(String r){
        if(r!=null&&r.contains("not-found"))Toast.makeText(this,"ChatGPT ka mic button page par nahi mila. Website se mic select karo.",Toast.LENGTH_LONG).show();
    }
    private void activateLive(){
        runJs("var bs=[].slice.call(document.querySelectorAll('button,[role=button]'));"
            +"var b=bs.find(x=>{var a=((x.getAttribute('aria-label')||'')+' '+(x.title||'')+' '+(x.innerText||'')).toLowerCase();"
            +"var r=x.getBoundingClientRect();return r.width>0&&r.height>0&&/voice mode|start voice|advanced voice|voice conversation/.test(a)});"
            +"if(b){b.click();return 'ok'}return 'not-found';",r->{if(r!=null&&r.contains("not-found"))Toast.makeText(this,"ChatGPT Voice button page par available nahi mila.",Toast.LENGTH_SHORT).show();});
    }
    private void readLatest(){
        runJs("var ms=[].slice.call(document.querySelectorAll('[data-message-author-role=assistant]'));"
            +"if(!ms.length)return 'no-reply';var m=ms[ms.length-1];var scope=m.closest('article')||m.parentElement||document;"
            +"var bs=[].slice.call(scope.querySelectorAll('button,[role=button]'));"
            +"var b=bs.find(x=>/read aloud|read response|voice-play-turn-action-button|stop reading/i.test((x.getAttribute('aria-label')||'')+' '+(x.title||'')+' '+(x.getAttribute('data-testid')||'')));"
            +"if(b){b.click();return 'ok'}return 'not-found';",r->{if(r!=null&&r.contains("not-found"))Toast.makeText(this,"Reply ka Read Aloud button nahi mila.",Toast.LENGTH_SHORT).show();});
    }
    private void sendMessage(){
        runJs("var e=document.querySelector('textarea#prompt-textarea,[contenteditable=true][role=textbox],[role=textbox],textarea');"
            +"if(!e)return 'empty';var t=(e.innerText||e.value||'').trim();if(!t)return 'empty';"
            +"var f=e.closest('form');var bs=[].slice.call(document.querySelectorAll('button,[role=button]'));"
            +"var b=bs.find(x=>{var a=((x.getAttribute('aria-label')||'')+' '+(x.getAttribute('data-testid')||'')+' '+(x.title||'')).toLowerCase();"
            +"return /send|submit/.test(a)&&!x.disabled&&x.getAttribute('aria-disabled')!=='true'});"
            +"if(b){b.click();return 'sent'}if(f&&f.requestSubmit){f.requestSubmit();return 'sent'}return 'missing';",
            r->{if(r!=null&&r.contains("empty"))Toast.makeText(this,"Pehle message type karo.",Toast.LENGTH_SHORT).show();});
    }
    private void scroll(int dir){
        runJs("var dir="+dir+";var t=window.__sajiScrollTarget;"
            +"if(!t||!t.isConnected||t.scrollHeight<=t.clientHeight+20){t=null;var ns=document.querySelectorAll('main,[role=main],[class*=conversation],[class*=scroll],div,section');var best=-1;"
            +"for(var i=0;i<ns.length;i++){var e=ns[i],r=e.getBoundingClientRect();if(r.width<100||r.height<100||e.scrollHeight<=e.clientHeight+20)continue;"
            +"var c=((e.className||'')+'').toLowerCase();var score=Math.min(r.width,innerWidth)*Math.min(r.height,innerHeight)+( /main|conversation|scroll/.test(c)?500000:0);if(score>best){best=score;t=e}}window.__sajiScrollTarget=t}"
            +"if(t){t.scrollTop+=dir*Math.max(280,Math.floor(t.clientHeight*.7));return 'ok'}window.scrollBy(0,dir*Math.max(300,innerHeight*.65));return 'page';",null);
    }
    private void showMicSettings(){
        boolean granted=Build.VERSION.SDK_INT<23||checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
        new AlertDialog.Builder(this).setTitle("SAJI GPT • MICROPHONE")
            .setMessage("Android microphone permission: "+(granted?"ALLOWED":"NOT ALLOWED")+
                "\n\nChatGPT website ko bhi apne browser prompt par microphone access allow karna hoga.")
            .setPositiveButton(granted?"OK":"ALLOW",(d,w)->{
                if(!granted&&Build.VERSION.SDK_INT>=23)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);
            }).setNegativeButton("CLOSE",null).show();
    }
    private void toggleLayout(){
        mobileMode=!mobileMode;
        WebSettings s=web.getSettings();
        if(mobileMode){
            s.setUserAgentString("Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            s.setUseWideViewPort(true); s.setLoadWithOverviewMode(true);
            ((TextView)((LinearLayout)mobile).getChildAt(1)).setText("TV");
        }else{
            s.setUserAgentString(defaultAgent); s.setUseWideViewPort(true); s.setLoadWithOverviewMode(false);
            ((TextView)((LinearLayout)mobile).getChildAt(1)).setText("MOBILE");
        }
        String u=web.getUrl(); if(u!=null)web.loadUrl(u);
        Toast.makeText(this,mobileMode?"Mobile layout enabled":"TV layout restored",Toast.LENGTH_SHORT).show();
    }
    private void goBack(){if(web!=null&&web.canGoBack())web.goBack();else web.loadUrl(HOME_URL);}
    @Override public void onBackPressed(){goBack();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        int k=e.getKeyCode();
        if(k==KeyEvent.KEYCODE_PAGE_UP&&e.getAction()==KeyEvent.ACTION_DOWN){scroll(-1);return true;}
        if(k==KeyEvent.KEYCODE_PAGE_DOWN&&e.getAction()==KeyEvent.ACTION_DOWN){scroll(1);return true;}
        if(k==KeyEvent.KEYCODE_BACK&&e.getAction()==KeyEvent.ACTION_UP){goBack();return true;}
        return super.dispatchKeyEvent(e);
    }
    @Override public void onRequestPermissionsResult(int code,String[] p,int[] g){
        super.onRequestPermissionsResult(code,p,g);
        if(code==REQ_MIC&&pendingPermission!=null){
            if(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)pendingPermission.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            else pendingPermission.deny();
            pendingPermission=null;
        }
    }
    @Override protected void onResume(){super.onResume();enterImmersive();if(web!=null)web.onResume();}
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onDestroy(){
        if(pendingPermission!=null){pendingPermission.deny();pendingPermission=null;}
        if(web!=null){
            try { web.stopLoading(); web.setWebChromeClient(null); web.setWebViewClient(null); web.destroy(); }
            catch (Throwable ignored) { }
            web=null;
        }
        super.onDestroy();
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}

    private class Icon extends View {
        final Paint p=new Paint(3); final Path path=new Path(); final int mode; boolean focused;
        Icon(int m){super(MainActivity.this);mode=m;}
        @Override protected void onDraw(Canvas c){
            float s=Math.min(getWidth(),getHeight())/48f;c.save();c.translate((getWidth()-48*s)/2,(getHeight()-48*s)/2);c.scale(s,s);
            p.setColor(Color.WHITE);p.setStrokeWidth(3.2f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
            if(mode==0){path.reset();path.moveTo(28,9);path.lineTo(13,24);path.lineTo(28,39);path.moveTo(14,24);path.lineTo(39,24);c.drawPath(path,p);}
            else if(mode==1){c.drawRoundRect(new RectF(18,5,30,27),6,6,p);path.reset();path.moveTo(12,22);path.cubicTo(12,40,36,40,36,22);c.drawPath(path,p);c.drawLine(24,36,24,43,p);c.drawLine(17,43,31,43,p);}
            else if(mode==3){path.reset();path.moveTo(7,23);path.lineTo(41,7);path.lineTo(30,41);path.lineTo(23,27);path.lineTo(7,23);path.moveTo(23,27);path.lineTo(41,7);c.drawPath(path,p);}
            else if(mode==4){path.reset();path.moveTo(10,29);path.lineTo(24,15);path.lineTo(38,29);path.moveTo(24,16);path.lineTo(24,40);c.drawPath(path,p);}
            else if(mode==5){path.reset();path.moveTo(10,19);path.lineTo(24,33);path.lineTo(38,19);path.moveTo(24,8);path.lineTo(24,32);c.drawPath(path,p);}
            else if(mode==6){c.drawRoundRect(new RectF(14,4,34,44),4,4,p);c.drawLine(20,9,28,9,p);c.drawCircle(24,38,1.8f,p);}
            else if(mode==7){c.drawArc(new RectF(9,9,39,39),-42,84,false,p);c.drawArc(new RectF(15,15,33,33),-42,84,false,p);path.reset();path.moveTo(8,19);path.lineTo(15,19);path.lineTo(25,11);path.lineTo(25,37);path.lineTo(15,29);path.lineTo(8,29);path.close();p.setStyle(Paint.Style.FILL);c.drawPath(path,p);}
            else if(mode==8){path.reset();path.moveTo(8,19);path.lineTo(15,19);path.lineTo(25,11);path.lineTo(25,37);path.lineTo(15,29);path.lineTo(8,29);path.close();p.setStyle(Paint.Style.FILL);c.drawPath(path,p);p.setStyle(Paint.Style.STROKE);c.drawArc(new RectF(22,13,39,35),-55,110,false,p);c.drawArc(new RectF(22,7,45,41),-55,110,false,p);}
            else {c.drawCircle(24,24,16,p);c.drawCircle(24,24,9,p);c.drawCircle(24,24,2,p);}
            c.restore();
        }
    }
}
