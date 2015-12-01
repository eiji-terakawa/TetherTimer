package com.gmail.system.pro.et.tethertimer2;

import com.gmail.system.pro.et.tethertimer2.R;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.util.*;

 public class MainActivity extends Activity implements View.OnClickListener,GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

    private final static String TAB = "MainActivity";

	//Activity→Serviceのブロードキャストインテント用定数
	public final static String INTENT_ACTION = "internal_service_broadcast";
    public final static String INTENT_ACTION_TYPE = "internal_service_type";
    public final static String INTENT_TIME_SETTING = "internal_service_timer_setting";
    public final static String INTENT_ORG_WIFI = "internal_service_org_wifi";
    
    // リソースアクセス用
	Button btn_stop;
	Button btn_extend;
	Button btn_setting;
	TextView text_org_wifi_stat;
	TextView text_wifi_stat;
	TextView text_wifi_ap_stat;
	TextView text_remain;
	TextView text_time_left;
	ToggleButton toggleButton_org_wifi_stat;
	ToggleButton toggleButton_wifi_stat;
	ToggleButton toggleButton_wifi_ap_stat;

	static boolean org_wifi_stat;						//起動時のWifi状態
	
	//時間設定用
	static int	TIME_LEFT_SETTING;
	static boolean setting_flag = false;
	
	//ジェスチャー検出
    private GestureDetector mGestureDetector;	
    
    // スレッドの通知を受けるためのレシーバ
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAB, "  onReceive(" + context + ", " + intent + ")");
            if (intent.getAction().equals(MainService.INTENT_ACTION)) {
 
            	
            	Log.v(TAB, "intent val1: " + intent.getIntExtra(MainService.INTENT_REMAIN, 0));
                Log.v(TAB, "intent val2: " + intent.getStringExtra(MainService.INTENT_WIFI_STAT));
                Log.v(TAB, "intent val3: " + intent.getStringExtra(MainService.INTENT_WIFIAP_STAT));

                text_wifi_stat.setText(intent.getStringExtra(MainService.INTENT_WIFI_STAT));
                if(intent.getStringExtra(MainService.INTENT_WIFI_STAT).equals((String) getText(R.string.wifi_stat_msg_exec))){
                	toggleButton_wifi_stat.setChecked(true);
                }else{
                	toggleButton_wifi_stat.setChecked(false);                	
                }

                text_wifi_ap_stat.setText(intent.getStringExtra(MainService.INTENT_WIFIAP_STAT));
                if(intent.getStringExtra(MainService.INTENT_WIFIAP_STAT).equals((String) getText(R.string.wifi_stat_msg_exec))){
                	toggleButton_wifi_ap_stat.setChecked(true);
                }else{
                	toggleButton_wifi_ap_stat.setChecked(false);                	
                }

                text_org_wifi_stat.setText(intent.getStringExtra(MainService.INTENT_WIFI_ORG_STAT));
                if(intent.getStringExtra(MainService.INTENT_WIFI_ORG_STAT).equals((String) getText(R.string.wifi_stat_msg_exec))){
                	org_wifi_stat = true;
                }else{
                	org_wifi_stat = false;
                }
            	toggleButton_org_wifi_stat.setChecked(org_wifi_stat);                	

                int remain = intent.getIntExtra(MainService.INTENT_REMAIN, 0);
                if(remain >= 3600){
                    text_remain.setText(String.format((String) getText(R.string.time_disp_h),remain / 3600,(remain % 3600) / 60,(remain % 3600) % 60));                	
                }else if(remain >= 60){
                    text_remain.setText(String.format((String) getText(R.string.time_disp_m),(remain % 3600) / 60,(remain % 3600) % 60));                	
                }else if(remain >= 0){
                    text_remain.setText(String.format((String) getText(R.string.time_disp_s),remain));                	
                }else{
                    text_remain.setText((String) getText(R.string.time_disp_up));
                }

                if(!setting_flag){
                	int timer_left = intent.getIntExtra(MainService.INTENT_TIMER_LEFT, 0);
                	TIME_LEFT_SETTING=timer_left;
                	if(timer_left >= 3600){
                		text_time_left.setText(String.format((String) getText(R.string.time_disp_h),timer_left / 3600,(timer_left % 3600) / 60,(timer_left % 3600) % 60));                	
                	}else if(timer_left >= 60){
                		text_time_left.setText(String.format((String) getText(R.string.time_disp_m),(timer_left % 3600) / 60,(timer_left % 3600) % 60));                	
                	}else if(timer_left >= 0){
                		text_time_left.setText(String.format((String) getText(R.string.time_disp_s),timer_left));                	
                	}
                }
                
                if(intent.getIntExtra(MainService.INTENT_REMAIN, 0)==-10){
                	finish();
                }
            }
        }
    };
	
	@Override
	public void onStart(){
			super.onStart();
			Log.v(TAB, "onStart");
	}
	@Override
	public void onResume(){
		super.onResume();
		Log.v(TAB, "★★★★★onResume");
        // レシーバの登録
        registerReceiver(mReceiver, new IntentFilter(MainService.INTENT_ACTION));
        
        // サービスが動いてなかったら止まる
        if(!isMyServiceRunning()){
    		Log.v(TAB, "★★★★★サービスが無かったので終了");
        	finish();
        }
	}

	@Override
	public void onPause(){
		super.onPause();
		Log.v(TAB, "onPause");
        // レシーバの解除
        unregisterReceiver(mReceiver);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.v(TAB, "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// いろんなことの初期化
		setInstance();
		// 
        Intent intent = new Intent(this, MainService.class);
        startService(intent);
        
        TextView textview = (TextView)findViewById(R.id.textView_time_left);
        mGestureDetector = new GestureDetector(textview.getContext(), this); 
        textview.setOnTouchListener(new OnTouchListener() {
		    @Override
		    public boolean onTouch(View v, MotionEvent event) {
				// GestureDetectorにイベントを委譲する
				boolean result = mGestureDetector.onTouchEvent(event);
				return result;
		    }
		});

	}

	@Override
	//画面を押した時
	public boolean onDown(MotionEvent e) {
		return true;
	}
	@Override
	//1タップで画面から指が離れた
	public boolean onSingleTapUp(MotionEvent e) {
		return false;
	}
	@Override
	//2連続でタップした
	public boolean onDoubleTap(MotionEvent e) {
		TIME_LEFT_SETTING = 3600;
		setting_flag = true;
		text_time_left.setTextColor(Color.RED);
        if(TIME_LEFT_SETTING >= 3600){
    		text_time_left.setText(String.format((String) getText(R.string.time_disp_h),TIME_LEFT_SETTING / 3600,(TIME_LEFT_SETTING % 3600) / 60,(TIME_LEFT_SETTING % 3600) % 60));                	
    	}else if(TIME_LEFT_SETTING >= 60){
    		text_time_left.setText(String.format((String) getText(R.string.time_disp_m),(TIME_LEFT_SETTING % 3600) / 60,(TIME_LEFT_SETTING % 3600) % 60));                	
    	}else if(TIME_LEFT_SETTING >= 0){
    		text_time_left.setText(String.format((String) getText(R.string.time_disp_s),TIME_LEFT_SETTING));                	
    	}		
		Log.v(TAB, "        TIME_LEFT_SETTING="+TIME_LEFT_SETTING);        	
		return false;
	}
	@Override
	//シングルタップと認識された時
	public boolean onSingleTapConfirmed(MotionEvent e) {
		TIME_LEFT_SETTING = TIME_LEFT_SETTING / 60 * 60;
		if(TIME_LEFT_SETTING<30){
			TIME_LEFT_SETTING = 30;
		}
		setting_flag = true;
		text_time_left.setTextColor(Color.RED);
        if(TIME_LEFT_SETTING >= 3600){
    		text_time_left.setText(String.format((String) getText(R.string.time_disp_h),TIME_LEFT_SETTING / 3600,(TIME_LEFT_SETTING % 3600) / 60,(TIME_LEFT_SETTING % 3600) % 60));                	
    	}else if(TIME_LEFT_SETTING >= 60){
    		text_time_left.setText(String.format((String) getText(R.string.time_disp_m),(TIME_LEFT_SETTING % 3600) / 60,(TIME_LEFT_SETTING % 3600) % 60));                	
    	}else if(TIME_LEFT_SETTING >= 0){
    		text_time_left.setText(String.format((String) getText(R.string.time_disp_s),TIME_LEFT_SETTING));                	
    	}		
		Log.v(TAB, "        TIME_LEFT_SETTING="+TIME_LEFT_SETTING);        	
		return false;
	}
	@Override
	//ダブルタップ時の処理
	public boolean onDoubleTapEvent(MotionEvent e) {
		return false;
	}
	@Override
	//画面を長押しした
	public void onShowPress(MotionEvent e) {
		text_time_left.setTextColor(Color.WHITE);
		setting_flag = false;
	}
	@Override
	//同じ箇所を長押しした時の処理
	public void onLongPress(MotionEvent e) {
	}
	@Override
	//画面を押したまま指を動かした
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		Log.v(TAB, "★move★ X="+distanceX+" Y="+distanceY);        	

		TIME_LEFT_SETTING -= ((int)distanceX * 5);
		if(TIME_LEFT_SETTING<30){
			TIME_LEFT_SETTING = 30;
		}
		if(TIME_LEFT_SETTING>36000){
			TIME_LEFT_SETTING = 36000;
		}
		setting_flag = true;
		text_time_left.setTextColor(Color.RED);
        if(TIME_LEFT_SETTING >= 3600){
    		text_time_left.setText(String.format((String) getText(R.string.time_disp_h),TIME_LEFT_SETTING / 3600,(TIME_LEFT_SETTING % 3600) / 60,(TIME_LEFT_SETTING % 3600) % 60));                	
    	}else if(TIME_LEFT_SETTING >= 60){
    		text_time_left.setText(String.format((String) getText(R.string.time_disp_m),(TIME_LEFT_SETTING % 3600) / 60,(TIME_LEFT_SETTING % 3600) % 60));                	
    	}else if(TIME_LEFT_SETTING >= 0){
    		text_time_left.setText(String.format((String) getText(R.string.time_disp_s),TIME_LEFT_SETTING));                	
    	}		
		Log.v(TAB, "        TIME_LEFT_SETTING="+TIME_LEFT_SETTING);        	
		
		return false;
	}
        @Override
        //フリックした
        public boolean onFling(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {
    		Log.v(TAB, "★Flick★");        	
        return true;
        }

	
	private boolean isMyServiceRunning() {
	    ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if (MainService.class.getName().equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}
	
	private void setInstance(){
		Log.v(TAB, "setInstance");

		btn_stop    = (Button)findViewById(R.id.button_stop);
		btn_extend  = (Button)findViewById(R.id.button_extend);
		btn_setting = (Button)findViewById(R.id.button_setting);
		text_wifi_stat     = (TextView)findViewById(R.id.textView_wifi_stat); 
		text_wifi_ap_stat  = (TextView)findViewById(R.id.textView_wifi_ap_stat);
		text_remain        = (TextView)findViewById(R.id.textView_remain);
		text_time_left     = (TextView)findViewById(R.id.textView_time_left);
		text_org_wifi_stat = (TextView)findViewById(R.id.textView_org_wifi_stat);
		toggleButton_org_wifi_stat = (ToggleButton)findViewById(R.id.toggleButton_org_wifi_stat);
		toggleButton_wifi_stat     = (ToggleButton)findViewById(R.id.toggleButton_wifi_stat);
		toggleButton_wifi_ap_stat  = (ToggleButton)findViewById(R.id.toggleButton_wifi_ap_stat);

		btn_stop.setOnClickListener(this);
		btn_extend.setOnClickListener(this);
		btn_setting.setOnClickListener(this);

		toggleButton_org_wifi_stat.setOnClickListener(this);
		toggleButton_wifi_stat.setOnClickListener(this);
		toggleButton_wifi_ap_stat.setOnClickListener(this);
		
		//クリック禁止に
		toggleButton_wifi_stat.setClickable(false);
		toggleButton_wifi_ap_stat.setClickable(false);
	}

	public void onClick(View v) {
		
		Log.v(TAB, "onClick");
		if(v == btn_stop) {
            Intent intent = new Intent(INTENT_ACTION);
            intent.putExtra(INTENT_ACTION_TYPE, "STOP");
            sendBroadcast(intent);
		}
		if(v == btn_extend) {
            Intent intent = new Intent(INTENT_ACTION);
            intent.putExtra(INTENT_ACTION_TYPE, "EXTEND");
            sendBroadcast(intent);
		}
		if(v == btn_setting) {
			if(!setting_flag){
				Toast.makeText(this, (String) getText(R.string.toast_button_msg), Toast.LENGTH_SHORT).show();
			}else{
				Intent intent = new Intent(INTENT_ACTION);
            	intent.putExtra(INTENT_ACTION_TYPE, "SETTING");
            	intent.putExtra(INTENT_TIME_SETTING, TIME_LEFT_SETTING);
            	sendBroadcast(intent);
            	setting_flag = false;
    			text_time_left.setTextColor(Color.WHITE);
			}
		}
		if(v == toggleButton_org_wifi_stat) {
//			Toast.makeText(this, "手動ではON/OFFで来ません。", Toast.LENGTH_SHORT).show();
			org_wifi_stat = toggleButton_org_wifi_stat.isChecked();
			Intent intent = new Intent(INTENT_ACTION);	
        	intent.putExtra(INTENT_ACTION_TYPE, "ORG_CHANGE");
        	intent.putExtra(INTENT_ORG_WIFI, org_wifi_stat);
        	sendBroadcast(intent);
		}
		if(v == toggleButton_wifi_stat) {
			Toast.makeText(this, (String) getText(R.string.toast_button_error), Toast.LENGTH_SHORT).show();
		}
		if(v == toggleButton_wifi_ap_stat) {
			Toast.makeText(this, (String) getText(R.string.toast_button_error), Toast.LENGTH_SHORT).show();
		}
	}
		@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		return true;
	}
}
