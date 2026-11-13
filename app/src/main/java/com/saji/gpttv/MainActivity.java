package com.saji.gpttv;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
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
    private static final String PREF_CHATGPT_MIC = "chatgpt_website_mic_allowed";
    private FrameLayout root;
    private LinearLayout rail;
    private WebView web;
    private boolean browserOpen = true;
    private PermissionRequest pendingPermission;
    private boolean rememberWebsitePermission;
    private boolean pendingSettingsMicEnable;
    private TextView settingsButton;
    private View back, mic, live, speak, send, up, down, site, mobile;
    private TextView exit;
    private boolean railPressed;
    private boolean mobileMode;
    private boolean manualMicPending;
    private SpeechRecognizer speechRecognizer;
    private boolean nativeSpeechListening;
    private Runnable speechTimeoutRunnable;
    private final Handler speechHandler = new Handler(Looper.getMainLooper());
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
        settingsButton=new TextView(this);
        settingsButton.setText("⚙"); settingsButton.setTextColor(Color.WHITE); settingsButton.setTextSize(22);
        settingsButton.setGravity(Gravity.CENTER); settingsButton.setFocusable(true); settingsButton.setClickable(true);
        settingsButton.setId(View.generateViewId()); settingsButton.setBackground(buttonBg(false,0xFF9B8CFF));
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(dp(42),dp(38),Gravity.TOP|Gravity.END);
        sp.topMargin=dp(12); sp.rightMargin=dp(94); root.addView(settingsButton,sp);
        settingsButton.setOnFocusChangeListener((v,focused)->focusStyle(v,focused,0xFF9B8CFF));
        settingsButton.setOnClickListener(v->showSettingsMenu());
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
        exit.setNextFocusDownId(back.getId()); settingsButton.setNextFocusDownId(back.getId());
        settingsButton.setNextFocusRightId(exit.getId()); exit.setNextFocusLeftId(settingsButton.getId());
        back.setNextFocusRightId(settingsButton.getId());
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
            @Override public void onPermissionRequest(PermissionRequest request){runOnUiThread(()->handleWebsiteMicRequest(request));}
            @Override public void onPermissionRequestCanceled(PermissionRequest r){if(pendingPermission==r){pendingPermission=null;rememberWebsitePermission=false;}}
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
        if (!browserOpen || web == null) {
            Toast.makeText(this, "ChatGPT page pehle open karo.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            manualMicPending = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        startNativeSpeechInput();
    }

    // Android speech-to-text, as used by the earlier SAJI AI TV app.
    private void startNativeSpeechInput() {
        if (web == null || !browserOpen) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Is TV Box par speech recognition service available nahi. Google Speech Services check karo.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            stopNativeSpeechInput();
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    nativeSpeechListening = true;
                    scheduleSpeechTimeout(22000, "Mic sun raha tha lekin speech complete nahi hui. Dobara try karo.");
                    Toast.makeText(MainActivity.this, "Listening... ab bolo.", Toast.LENGTH_SHORT).show();
                }
                @Override public void onBeginningOfSpeech() {
                    scheduleSpeechTimeout(25000, "Voice recognition timeout. Dobara try karo.");
                }
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {
                    nativeSpeechListening = false;
                    scheduleSpeechTimeout(14000, "Speech text mein convert nahi hui. Network ya Speech Services check karo.");
                    Toast.makeText(MainActivity.this, "Awaaz mil gayi, text ban raha hai...", Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(int error) {
                    nativeSpeechListening = false;
                    stopNativeSpeechInput();
                    String message = error == SpeechRecognizer.ERROR_NO_MATCH
                            ? "Awaaz clear nahi mili. Mic ke paas bolkar dobara try karo."
                            : error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                            ? "Speech service ka network slow ya unavailable hai."
                            : error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                            ? "Android microphone permission allow karo."
                            : error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                            ? "Speech service busy hai. 2 second baad try karo."
                            : "Speech recognition ruk gayi. TV ki Speech Services check karo.";
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                }
                @Override public void onResults(Bundle results) {
                    nativeSpeechListening = false;
                    ArrayList<String> matches = results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String transcript = matches == null || matches.isEmpty() ? "" : matches.get(0);
                    stopNativeSpeechInput();
                    if (transcript.trim().isEmpty()) {
                        Toast.makeText(MainActivity.this, "Koi text recognize nahi hua. Dobara bolo.", Toast.LENGTH_LONG).show();
                    } else {
                        insertTranscriptIntoComposer(transcript.trim());
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ur-PK");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
            scheduleSpeechTimeout(15000, "Mic start nahi hua. TV ki Speech Services ya microphone permission check karo.");
            speechRecognizer.startListening(intent);
        } catch (Throwable error) {
            stopNativeSpeechInput();
            Toast.makeText(this, "Speech service start nahi hui. TV par Google Speech Services check karo.", Toast.LENGTH_LONG).show();
        }
    }

    private void scheduleSpeechTimeout(long delayMs, String message) {
        if (speechTimeoutRunnable != null) speechHandler.removeCallbacks(speechTimeoutRunnable);
        speechTimeoutRunnable = () -> {
            if (speechRecognizer != null) {
                stopNativeSpeechInput();
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        };
        speechHandler.postDelayed(speechTimeoutRunnable, delayMs);
    }

    private void stopNativeSpeechInput() {
        nativeSpeechListening = false;
        if (speechTimeoutRunnable != null) {
            speechHandler.removeCallbacks(speechTimeoutRunnable);
            speechTimeoutRunnable = null;
        }
        SpeechRecognizer recognizer = speechRecognizer;
        speechRecognizer = null;
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Throwable ignored) {}
            try { recognizer.destroy(); } catch (Throwable ignored) {}
        }
    }

    private void insertTranscriptIntoComposer(String transcript) {
        if (web == null || !browserOpen) return;
        final String quoted;
        try { quoted = org.json.JSONObject.quote(transcript); }
        catch (Throwable error) {
            Toast.makeText(this, "Transcript prepare nahi ho saka.", Toast.LENGTH_SHORT).show();
            return;
        }
        String js = "(function(){try{"
                + "var text=" + quoted + ";"
                + "var selectors=['textarea#prompt-textarea','[data-testid*=composer] [contenteditable=true]',"
                + "'[contenteditable=true][role=textbox]','[role=textbox]','textarea','[contenteditable=true]'];"
                + "var el=null,best=-1;"
                + "for(var i=0;i<selectors.length;i++){var list=document.querySelectorAll(selectors[i]);"
                + "for(var j=list.length-1;j>=0;j--){var n=list[j],r=n.getBoundingClientRect();"
                + "if(r.width<=0||r.height<=0||n.disabled||n.readOnly)continue;"
                + "var score=1+(n.id==='prompt-textarea'?100:0)"
                + "+((n.getAttribute('data-testid')||'').indexOf('composer')>=0?50:0)"
                + "+(n.getAttribute('role')==='textbox'?20:0);"
                + "if(score>best){best=score;el=n;}}}"
                + "if(!el)return 'missing';el.focus();"
                + "var edit=el.isContentEditable||el.getAttribute('contenteditable')==='true';"
                + "if(edit){var ok=false;try{ok=document.execCommand('insertText',false,text);}catch(e){}"
                + "if(!ok){var old=el.innerText||el.textContent||'';el.textContent=(old?old+' ':'')+text;}"
                + "}else{var proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;"
                + "var desc=Object.getOwnPropertyDescriptor(proto,'value');"
                + "if(desc&&desc.set)desc.set.call(el,(el.value?el.value+' ':'')+text);else el.value=(el.value?el.value+' ':'')+text;}"
                + "try{el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:text}));}"
                + "catch(e){el.dispatchEvent(new Event('input',{bubbles:true}));}"
                + "el.dispatchEvent(new Event('change',{bubbles:true}));return 'ok';"
                + "}catch(e){return 'error';}})()";
        web.evaluateJavascript(js, result -> {
            if (result != null && result.contains("ok")) {
                Toast.makeText(this, "Tumhari awaaz ka text message box mein aa gaya. Ab SEND dabao.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Voice text mila, lekin ChatGPT ka message box nahi mila. Page reload karke try karo.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void activateLive() {
        if (!browserOpen || web == null) {
            Toast.makeText(this, "ChatGPT page pehle open karo.", Toast.LENGTH_SHORT).show();
            return;
        }
        tryActivateLiveVoice(0);
    }

    private void tryActivateLiveVoice(int attempt) {
        if (web == null || !browserOpen) return;
        if (attempt >= 8) {
            Toast.makeText(this, "ChatGPT ka blue Live Voice button nahi mila. Page reload karke dobara try karo.", Toast.LENGTH_LONG).show();
            return;
        }
        // Prefer ChatGPT's accessible labels. Fallback: select the rightmost
        // visible action in the composer, which is the blue waveform control.
        String js = "(function(){try{"
                + "var bs=[].slice.call(document.querySelectorAll('button,[role=button]'));"
                + "var visible=function(b){var r=b.getBoundingClientRect(),st=getComputedStyle(b);"
                + "return r.width>0&&r.height>0&&st.display!=='none'&&st.visibility!=='hidden'"
                + "&&st.opacity!=='0'&&!b.disabled&&b.getAttribute('aria-disabled')!=='true';};"
                + "var label=function(b){return ((b.getAttribute('aria-label')||'')+' '+"
                + "(b.getAttribute('title')||'')+' '+(b.getAttribute('data-testid')||'')+' '+"
                + "(b.innerText||b.textContent||'')).toLowerCase().replace(/[-_]/g,' ');};"
                + "var named=bs.filter(function(b){var t=label(b);return visible(b)"
                + "&&/(start voice mode|voice mode|start voice conversation|voice conversation|voice chat)/.test(t)"
                + "&&!/dictat|microphone|record|mute|stop voice|end voice|close voice/.test(t);});"
                + "if(named.length){named[named.length-1].click();return 'clicked-labelled';}"
                + "var editors=[].slice.call(document.querySelectorAll('textarea,[contenteditable=true],[role=textbox]'));"
                + "editors=editors.filter(function(e){var r=e.getBoundingClientRect();return r.width>0&&r.height>0;});"
                + "if(!editors.length)return 'no-composer';"
                + "var ed=editors[editors.length-1],composer=ed.closest('form');"
                + "if(!composer){var p=ed;for(var k=0;k<7&&p;k++,p=p.parentElement){"
                + "var pr=p.getBoundingClientRect(),count=p.querySelectorAll('button,[role=button]').length;"
                + "if(pr.width>Math.min(320,innerWidth*.45)&&count>=2){composer=p;break;}}}"
                + "if(!composer)composer=ed.parentElement&&ed.parentElement.parentElement;"
                + "if(!composer)return 'no-composer';var cr=composer.getBoundingClientRect();"
                + "var near=bs.filter(function(b){var r=b.getBoundingClientRect();"
                + "return visible(b)&&r.width>0&&r.height>0&&r.bottom>=cr.top-35&&r.top<=cr.bottom+35"
                + "&&r.right<=cr.right+35&&r.left>=cr.left-20;});"
                + "near=near.filter(function(b){return !/dictat|microphone|record|attach|upload|add files|search/.test(label(b));});"
                + "if(!near.length)return 'no-voice-button';"
                + "near.sort(function(x,y){return y.getBoundingClientRect().right-x.getBoundingClientRect().right;});"
                + "var target=near[0],tr=target.getBoundingClientRect();"
                + "if(tr.right<cr.left+cr.width*.68)return 'no-right-button';"
                + "target.click();return 'clicked-rightmost-composer-button';"
                + "}catch(e){return 'error';}})()";
        web.evaluateJavascript(js, result -> {
            if (result != null && result.contains("clicked")) {
                Toast.makeText(this, "ChatGPT ka blue Live Voice button open kiya.", Toast.LENGTH_SHORT).show();
            } else {
                speechHandler.postDelayed(() -> tryActivateLiveVoice(attempt + 1), 700);
            }
        });
    }

    private void readLatest() { readLatestAttempt(0); }

    private void readLatestAttempt(int attempt) {
        if (web == null || !browserOpen) return;
        String js = "(function(){try{"
                + "var host=(location.hostname||'').toLowerCase();"
                + "if(host!=='chatgpt.com'&&host!=='chat.openai.com')return 'not-chatgpt';"
                + "var msgs=[].slice.call(document.querySelectorAll('[data-message-author-role=assistant]'));"
                + "if(!msgs.length)return 'no-reply';var latest=msgs[msgs.length-1];"
                + "var label=function(b){return ((b.getAttribute('aria-label')||'')+' '+"
                + "(b.getAttribute('title')||'')+' '+(b.getAttribute('data-testid')||'')+' '+"
                + "(b.innerText||b.textContent||'')).toLowerCase().replace(/[-_]/g,' ');};"
                + "var visible=function(b){var r=b.getBoundingClientRect(),st=getComputedStyle(b);"
                + "return r.width>0&&r.height>0&&st.display!=='none'&&st.visibility!=='hidden'&&st.opacity!=='0';};"
                + "var scopes=[],p=latest;"
                + "for(var k=0;k<7&&p;k++,p=p.parentElement){"
                + "if(p.matches&&(p.matches('article')||p.matches('[data-testid*=conversation-turn]')||p.matches('[data-message-author-role=assistant]')))scopes.push(p);}"
                + "scopes.push(latest.closest('article')||latest.parentElement||document);scopes.push(document);"
                + "var seen=[],all=[];scopes.forEach(function(sc){[].slice.call(sc.querySelectorAll('button,[role=button]')).forEach(function(b){if(seen.indexOf(b)<0){seen.push(b);all.push(b);}});});"
                + "var match=all.filter(function(b){var t=label(b);return visible(b)&&"
                + "/read\\s*aloud|read\\s*response|voice play turn action button|stop\\s*reading|stop\\s*read\\s*aloud/.test(t);});"
                + "if(!match.length){var speaker=all.filter(function(b){if(!visible(b))return false;"
                + "var t=label(b),svg=b.querySelector('svg');if(/copy|share|thumb|feedback|more|edit|regenerate/.test(t))return false;"
                + "if(/speaker|read|listen|audio|play/.test(t))return true;"
                + "if(!svg)return false;var markup=(svg.outerHTML||'').toLowerCase();"
                + "return /speaker|volume|audio|sound/.test(markup)||(/path/.test(markup)&&/(m3\\s*9|m3\\s*10|l7\\s*9|l7\\s*10|12\\s*5)/.test(markup));});"
                + "if(speaker.length)match=speaker;}"
                + "if(!match.length){var groups=[].slice.call(document.querySelectorAll('[data-testid*=conversation-turn],article'));"
                + "var turn=groups.filter(function(g){return g.contains(latest);}).pop()||latest.parentElement;"
                + "if(turn){var buttons=[].slice.call(turn.querySelectorAll('button,[role=button]')).filter(visible);"
                + "var toolbar=buttons.filter(function(b){var t=label(b);return /copy|thumb|share|more|edit|read|listen/.test(t)||!!b.querySelector('svg');});"
                + "if(toolbar.length>=4){var candidate=toolbar[2];if(candidate&&!/copy|thumb|share|more|edit/.test(label(candidate)))match=[candidate];else if(toolbar.length>=5)match=[toolbar[2]];}}}"
                + "if(!match.length)return 'button-missing';match[match.length-1].click();return 'clicked';"
                + "}catch(e){return 'error';}})()";
        web.evaluateJavascript(js, result -> {
            if (result != null && result.contains("clicked")) {
                Toast.makeText(this, "ChatGPT reply ka Read Aloud button press kiya.", Toast.LENGTH_SHORT).show();
            } else if (attempt < 4) {
                speechHandler.postDelayed(() -> readLatestAttempt(attempt + 1), 500);
            } else {
                Toast.makeText(this, "Reply toolbar ka speaker button detect nahi hua. ChatGPT page reload karke try karo.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void sendMessage() {
        if (web == null || !browserOpen) return;
        String js = "(function(){try{"
                + "var selectors=['textarea#prompt-textarea','[data-testid*=composer] [contenteditable=true]',"
                + "'[contenteditable=true][role=textbox]','[role=textbox]','textarea','[contenteditable=true]'];"
                + "var editor=null;"
                + "for(var i=0;i<selectors.length&&!editor;i++){var list=document.querySelectorAll(selectors[i]);"
                + "for(var j=list.length-1;j>=0;j--){var n=list[j],r=n.getBoundingClientRect();"
                + "var t=(n.innerText||n.textContent||n.value||'').trim();"
                + "if(r.width>0&&r.height>0&&!n.disabled&&!n.readOnly&&t){editor=n;break;}}}"
                + "if(!editor)return 'empty';var form=editor.closest('form');"
                + "var buttons=[].slice.call((form||document).querySelectorAll('button,[role=button]'));"
                + "var best=null,bestScore=0;"
                + "buttons.forEach(function(b){var r=b.getBoundingClientRect();"
                + "if(r.width<=0||r.height<=0||b.disabled||b.getAttribute('aria-disabled')==='true')return;"
                + "var a=((b.getAttribute('aria-label')||'')+' '+(b.getAttribute('data-testid')||'')+' '+"
                + "(b.getAttribute('title')||'')+' '+(b.innerText||b.textContent||'')).toLowerCase();"
                + "var score=0;if(/send-button|send prompt/.test(a))score+=20;if(/send|submit/.test(a))score+=10;"
                + "if(/stop|voice|microphone|attach|dictat/.test(a))score-=15;"
                + "if(score>bestScore){best=b;bestScore=score;}});"
                + "if(best&&bestScore>=10){best.click();return 'sent';}"
                + "if(form&&typeof form.requestSubmit==='function'){form.requestSubmit();return 'sent';}"
                + "return 'button-missing';}catch(e){return 'error';}})()";
        web.evaluateJavascript(js, result -> {
            if (result != null && result.contains("sent")) {
                Toast.makeText(this, "Message ChatGPT ko send kar diya.", Toast.LENGTH_SHORT).show();
            } else if (result != null && result.contains("empty")) {
                Toast.makeText(this, "Pehle message box mein text likho ya MIC se bolo.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Send control detect nahi hua. Message box mein text check karo.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void scroll(int dir) {
        if (web == null || !browserOpen) return;
        String js = "(function(){try{"
                + "var dir=" + (dir < 0 ? "-1" : "1") + ";"
                + "var target=window.__sajiScrollTarget;"
                + "if(!target||!target.isConnected||target.scrollHeight<=target.clientHeight+30){"
                + "target=null;var active=document.activeElement,best=-1;"
                + "var nodes=document.querySelectorAll('main,[role=main],[data-testid*=conversation],[class*=conversation],[class*=chat],[class*=scroll],div,section');"
                + "for(var i=0;i<nodes.length;i++){var el=nodes[i];"
                + "if(!el||el===document.body||el===document.documentElement)continue;"
                + "var r=el.getBoundingClientRect();"
                + "if(r.width<100||r.height<100||r.bottom<0||r.top>innerHeight)continue;"
                + "if(el.scrollHeight<=el.clientHeight+30)continue;"
                + "var st=getComputedStyle(el),oy=st.overflowY||'',cl=((el.className||'')+'').toLowerCase();"
                + "var semantic=el.tagName==='MAIN'||el.getAttribute('role')==='main'||/conversation|chat|scroll/.test(cl);"
                + "var score=Math.min(r.width,innerWidth)*Math.min(r.height,innerHeight)+(semantic?500000:0)+(active&&el.contains(active)?200000:0);"
                + "if(oy==='hidden')score-=100000;if(score>best){best=score;target=el;}}"
                + "window.__sajiScrollTarget=target;}"
                + "if(target){target.scrollTop+=dir*Math.max(260,Math.floor(target.clientHeight*.68));return 'scrolled';}"
                + "window.scrollBy(0,dir*Math.max(300,Math.floor(innerHeight*.65)));return 'page';"
                + "}catch(e){return 'error';}})()";
        web.evaluateJavascript(js, result -> {
            if (result != null && result.contains("error")) Toast.makeText(this, "Conversation scroll nahi ho saki.", Toast.LENGTH_SHORT).show();
        });
    }

    private boolean hasAndroidMicPermission(){return Build.VERSION.SDK_INT<23||checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;}
    private boolean isChatGptWebsiteMicAllowed(){return getPreferences(MODE_PRIVATE).getBoolean(PREF_CHATGPT_MIC,false);}
    private void showSettingsMenu(){
        String[] items={"MICROPHONE PERMISSIONS","ANDROID APP PERMISSIONS","OPEN ANDROID APP SETTINGS","TEST MICROPHONE"};
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("SAJI GPT SETTINGS").setItems(items,(d,w)->{
            if(w==0)showMicSettings();else if(w==1)showAndroidPermissionStatus();else if(w==2)openAndroidAppSettings();else activateMic();
        }).setNegativeButton("CLOSE",null).create();
        dialog.setOnShowListener(v->{if(dialog.getListView()!=null)dialog.getListView().requestFocus();});dialog.show();
    }
    private void showAndroidPermissionStatus(){
        boolean allowed=hasAndroidMicPermission();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("ANDROID APP PERMISSIONS")
            .setMessage("SAJI GPT microphone permission: "+(allowed?"ALLOWED":"BLOCKED")+
                "\n\nChatGPT website permission: "+(isChatGptWebsiteMicAllowed()?"ALLOWED":"NOT ALLOWED")+
                "\n\nDono permissions allow honi zaroori hain. Android permission app ke liye hai, website permission ChatGPT ke liye.")
            .setPositiveButton(allowed?"OK":"ALLOW",(d,w)->{if(!allowed)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);})
            .setNeutralButton("APP SETTINGS",(d,w)->openAndroidAppSettings()).setNegativeButton("CLOSE",null).create();dialog.show();
    }
    private void openAndroidAppSettings(){
        try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));}
        catch(Throwable error){try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Throwable ignored){Toast.makeText(this,"Android Settings open nahi hui.",Toast.LENGTH_LONG).show();}}
    }
    private void showMicSettings(){
        boolean androidAllowed=hasAndroidMicPermission(),siteAllowed=isChatGptWebsiteMicAllowed();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("CHATGPT MICROPHONE")
            .setMessage("Android app permission: "+(androidAllowed?"ALLOWED":"BLOCKED")+
                "\nChatGPT website permission: "+(siteAllowed?"ALLOWED":"BLOCKED")+
                "\n\nDono ON hone chahiye. Allow ke baad ChatGPT page reload karke Live/MIC try karo.")
            .setPositiveButton("ALLOW MICROPHONE",(d,w)->{
                pendingSettingsMicEnable=true;
                if(!hasAndroidMicPermission())requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);
                else{getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_CHATGPT_MIC,true).apply();pendingSettingsMicEnable=false;Toast.makeText(this,"ChatGPT website microphone allowed. Page reload karke try karo.",Toast.LENGTH_LONG).show();}
            })
            .setNeutralButton("BLOCK WEBSITE MIC",(d,w)->{
                getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_CHATGPT_MIC,false).apply();
                if(pendingPermission!=null){try{pendingPermission.deny();}catch(Throwable ignored){}pendingPermission=null;}
                Toast.makeText(this,"ChatGPT website microphone blocked.",Toast.LENGTH_SHORT).show();
            }).setNegativeButton("ANDROID SETTINGS",(d,w)->openAndroidAppSettings()).create();dialog.show();
    }
    private void handleWebsiteMicRequest(PermissionRequest request){
        if(request==null)return;boolean audio=false,unsupported=false;
        String[] resources=request.getResources();if(resources!=null)for(String resource:resources){if(PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource))audio=true;else unsupported=true;}
        String origin=request.getOrigin()==null?"":request.getOrigin().toString().toLowerCase();
        boolean trusted=origin.startsWith("https://chatgpt.com")||origin.startsWith("https://chat.openai.com");
        if(!audio||unsupported||!trusted){try{request.deny();}catch(Throwable ignored){}return;}
        if(!isChatGptWebsiteMicAllowed()){
            new AlertDialog.Builder(this).setTitle("Allow ChatGPT microphone?")
                .setMessage("ChatGPT wants to use the microphone. Allow it for future voice conversations?")
                .setPositiveButton("ALLOW ONCE",(d,w)->allowPendingWebsiteMic(request,false))
                .setNeutralButton("ALWAYS ALLOW",(d,w)->allowPendingWebsiteMic(request,true))
                .setNegativeButton("BLOCK",(d,w)->{try{request.deny();}catch(Throwable ignored){}})
                .setOnCancelListener(d->{try{request.deny();}catch(Throwable ignored){}}).show();return;
        }
        allowPendingWebsiteMic(request,true);
    }
    private void allowPendingWebsiteMic(PermissionRequest request,boolean remember){
        if(!hasAndroidMicPermission()){pendingPermission=request;rememberWebsitePermission=remember;requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return;}
        if(remember)getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_CHATGPT_MIC,true).apply();
        try{request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});}catch(Throwable ignored){}
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
        if(code==REQ_MIC){
            boolean granted=g!=null&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED;
            if(pendingPermission!=null){PermissionRequest request=pendingPermission;pendingPermission=null;
                try{if(granted){if(rememberWebsitePermission)getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_CHATGPT_MIC,true).apply();request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});}else request.deny();}catch(Throwable ignored){}
                rememberWebsitePermission=false;
            }
            if(pendingSettingsMicEnable){pendingSettingsMicEnable=false;if(granted){getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_CHATGPT_MIC,true).apply();Toast.makeText(this,"Android aur ChatGPT website microphone permissions enabled. Page reload karo.",Toast.LENGTH_LONG).show();}else Toast.makeText(this,"Android microphone permission allow nahi hui.",Toast.LENGTH_LONG).show();}
            if(manualMicPending){manualMicPending=false;if(granted)startNativeSpeechInput();else Toast.makeText(this,"Microphone permission allow nahi hui.",Toast.LENGTH_LONG).show();}
        }
    }

    @Override protected void onResume(){super.onResume();enterImmersive();if(web!=null)web.onResume();}
    @Override protected void onPause(){
        stopNativeSpeechInput();
        if(web!=null)web.onPause();
        super.onPause();
    }
    @Override protected void onDestroy(){
        stopNativeSpeechInput();
        speechHandler.removeCallbacksAndMessages(null);
        if(pendingPermission!=null){try{pendingPermission.deny();}catch(Throwable ignored){}pendingPermission=null;}
        if(web!=null){
            try { web.stopLoading(); web.setWebChromeClient(null); web.setWebViewClient(null); web.destroy(); }
            catch(Throwable ignored) { }
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
