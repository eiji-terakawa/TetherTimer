package com.gmail.system.pro.et.tethertimer;

import java.lang.reflect.Method;
import java.util.Timer;
import java.util.TimerTask;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class MainService extends Service{

    //ログ用のタグ
	private final static String TAB = "MainService";
	
	//インテント用定数
	public final static String INTENT_ACTION = "internal_thread_broadcast";
	public final static String INTENT_WIFI_ORG_STAT = "internal_thread_wifi_original_status";
    public final static String INTENT_WIFI_STAT = "internal_thread_wifi_status";
    public final static String INTENT_WIFIAP_STAT = "internal_thread_wifiap_status";
    public final static String INTENT_REMAIN = "internal_thread_left_timer";
    public final static String INTENT_TIMER_LEFT = "internal_thread_timer_left";
    

	// 定数
	private final static int TIMER_PERIOD = 1000;		//タイマー間隔(m秒)
	public	static int TIMER_LEFT = 3600;				//終了待ち時間のデフォルト(秒)

	// 変数
	static	int left_time = TIMER_LEFT;					//残り時間
	static	boolean timer_status = false;				//タイマー稼働状態
	static	int ServiceId;								//サービスｉｄ(生存チェック用)
	static	WifiState State = WifiState.wakeup;			//Wifi接続状態
	static	SharedPreferences sharedPref;				//プリファレンス
	static boolean org_wifi_stat;						//起動時のWifi状態
	public	static long target_time = 0;				//目標時刻

	// ハンドラを生成
	Handler handler;
	Timer timer;

	//WIFI関連メソッド
	WifiManager wifi;
	Method method1;
	Method method2;
	boolean	tether = false;

    // スレッドの通知を受けるためのレシーバ
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAB, "service onReceive(" + context + ", " + intent + ")");
            //メインアクティビティからのインテントアクション
            if (intent.getAction().equals(MainActivity.INTENT_ACTION)) {
                Log.v(TAB, "service intent val: " + intent.getIntExtra(MainActivity.INTENT_ACTION_TYPE, 0));
                //停止を押したとき
                if(intent.getStringExtra(MainActivity.INTENT_ACTION_TYPE).equals("STOP")){
                	left_time = 1;
                	target_time = System.currentTimeMillis() + left_time * 1000;
                	cancelService();
                }
                //延長を押したとき
                if(intent.getStringExtra(MainActivity.INTENT_ACTION_TYPE).equals("EXTEND")){
                	left_time = TIMER_LEFT;
                	target_time = System.currentTimeMillis() + left_time * 1000;
                	cancelService();
                    scheduleService();
                }
                //復帰時Wifiを変更したとき
                if(intent.getStringExtra(MainActivity.INTENT_ACTION_TYPE).equals("ORG_CHANGE")){
                	org_wifi_stat=intent.getBooleanExtra(MainActivity.INTENT_ORG_WIFI,false);

                }
                //時間設定したとき
                if(intent.getStringExtra(MainActivity.INTENT_ACTION_TYPE).equals("SETTING")){
                	TIMER_LEFT=intent.getIntExtra(MainActivity.INTENT_TIME_SETTING,3600);
                	// プリファレンスに書き込むためのEditorオブジェクト取得 //
                    Editor editor = sharedPref.edit();
                    // "TIMER_LEFT" というキーで時間を登録
                    editor.putInt( "TIMER_LEFT", TIMER_LEFT );
                    // 書き込みの確定（実際にファイルに書き込む）
                    editor.commit();
                }
            }
        }
    };

	public void onPause(){
		Log.v(TAB, "service onPause");
	    // レシーバの解除
        unregisterReceiver(mReceiver);
    }

	@Override
	public void onDestroy(){
		super.onDestroy();
		Log.v(TAB, "service onDestroy");
	    // レシーバの解除
        unregisterReceiver(mReceiver);
        cancelNotification();

        // プリファレンスに書き込むためのEditorオブジェクト取得 //
        Editor editor = sharedPref.edit();
        // "TIMER_LEFT" というキーで時間を登録
        editor.putInt( "TIMER_LEFT", TIMER_LEFT );
        // 書き込みの確定（実際にファイルに書き込む）
        editor.commit();
	}

    @Override
    public void onCreate() {
        Log.v(TAB, "service onCreate");
        Toast.makeText(this, (String) getText(R.string.toast_service_start), Toast.LENGTH_SHORT).show();

    	handler = new Handler();
    	timer = new Timer(false);

		// タイムアップかつstop状態なら終了状態からの再起動と見なして初期化
		if((left_time < 0) && (State == WifiState.finish)){
	        Log.v(TAB, "initalize timer!!");
			State = WifiState.wakeup;
		}

		// プリファレンスから待ち時間を取得
    	sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		TIMER_LEFT = sharedPref.getInt("TIMER_LEFT", TIMER_LEFT);
		left_time = TIMER_LEFT;
    	target_time = System.currentTimeMillis() + left_time * 1000;
		
		getMethod();		
        scheduleService();

    	org_wifi_stat = wifi.isWifiEnabled();
        Log.v(TAB, "org_wifi_stat="+org_wifi_stat);
		
		// スケジュール(タイマースレッド)を設定
//		if(!timer_status)
		{
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					handler.post(new Runnable() {
						@Override
						public void run() {
							// 描画処理を指示
							checkTimeUp();
						}
					});
				}
			}, TIMER_PERIOD, TIMER_PERIOD); // 初回起動の遅延(1sec)と周期(1sec)指定
		}
    }
	
	@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
		Log.v(TAB, "service onStart "+startId);
		ServiceId = startId;

        // レシーバの登録
        registerReceiver(mReceiver, new IntentFilter(MainActivity.INTENT_ACTION));        
        setNotification();
        return START_STICKY;

	}

	// 通知領域に常駐させる
	private void setNotification() {
		Log.v(TAB, "setNotification");
	    NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
	    Notification notification = new Notification(R.drawable.icon, getString(R.string.app_name), System.currentTimeMillis());

	    Intent intent = new Intent(this, MainActivity.class);
	    notification.flags = Notification.FLAG_ONGOING_EVENT; // 常駐
	    PendingIntent contextIntent = PendingIntent.getActivity(this, 0, intent, 0);
	    notification.setLatestEventInfo(getApplicationContext(), getString(R.string.app_name), getString(R.string.notify_summary), contextIntent);
	    notificationManager.notify(R.string.app_name, notification);
	}
    private void cancelNotification() {
		Log.v(TAB, "cancelNotification");
        NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancel(R.string.app_name);
    }

	private void getMethod(){
		Log.v(TAB, "service getMethod");

		wifi = (WifiManager)getSystemService(WIFI_SERVICE);

		try{
			method1 = wifi.getClass().getMethod("isWifiApEnabled");
		}
		catch (Exception e) {
		    e.printStackTrace();
		}
		try {
			method2 = wifi.getClass().getMethod("setWifiApEnabled",WifiConfiguration.class, boolean.class);
        }
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void checkTimeUp(){
		Log.v(TAB, "service checkTimeUp state="+State+" left_time="+left_time+" target_time="+target_time/1000+"now="+System.currentTimeMillis()/1000);

		boolean	wifi_state = wifi.isWifiEnabled();
		boolean	wifiAp_state = false;
		try{
			if("true".equals(method1.invoke(wifi).toString())){
				Log.v(TAB, "WifiAP-Enable!");
				wifiAp_state = true;
			}
		}
    	catch (Exception e) {
   		    e.printStackTrace();
   	    }
		
		// ★★★　カウントダウン　★★★
		left_time = (int)(target_time - System.currentTimeMillis())/1000;
//		left_time --;

		// WIFIがONの場合、OFFにする。　state 0→1（WIFI-OFF待機状態）
		if((State == WifiState.wakeup) && (wifi_state)){
			Log.v(TAB, "Wifi turn off");
			wifi.setWifiEnabled(false);
			State = WifiState.waitforWifiOff;
		}
		if((State == WifiState.wakeup) && (!wifi_state)){
			Log.v(TAB, "awready Wifi turn off");
			State = WifiState.waitforWifiOff;
		}
		// WIFI-OFF待機状態でWIFIがOFFになったらテザリングをONにする。　state 1→2（テザリング-ON待機状態）
		if((State == WifiState.waitforWifiOff) && (wifi_state==false)){
			Log.v(TAB, "waitforWifiOff");			
			if(!tether){
			try {
					Log.v(TAB, "set WIFI-AP mode");
					method2.invoke(wifi, null,true);
		        }
			catch (Exception e) {
		         e.printStackTrace();
				}
				tether = true;
				State = WifiState.waitforWifiApOn;
			}
		}
		// テザリング待機状態でテザリングがONになったらテザリングON状態にする。　state 2→3（テザリング-ON状態）
		if(State == WifiState.waitforWifiApOn){
			Log.v(TAB, "waitforWifiApOn");
    		if(wifiAp_state){
    			Log.v(TAB, "WifiAP-Enable!");
    			State = WifiState.normal;
    		}
		}
		// テザリング状態でテザリングがOFFになったらWIFI-OFF待機状態にする。　state 3→1（WIFI-OFF待機状態）
		if(State == WifiState.normal){
			Log.v(TAB, "normal");			
    		if(!wifiAp_state){
				Log.v(TAB, "WifiAP-Enable!");
				State = WifiState.waitforWifiOff;
				tether = false;
    		}
		}
		// タイムアップした場合、テザリングをOFFにする。　state 3→4（テザリング-OFF待機状態）
		if((left_time <= 0) && (State != WifiState.timeUp)){
			Log.v(TAB, "TimeUp!!");
//			cancelService();
			boolean toBeEnabled;
			toBeEnabled = false;
			tether = false;
			try {
				Log.v(TAB, "set WIFI-AP mode");
				method2.invoke(wifi, null,toBeEnabled);
	        }
			catch (Exception e) {
				e.printStackTrace();
			}
			if(org_wifi_stat){
				wifi.setWifiEnabled(true);
			}
			State = WifiState.timeUp;
		}
		// サービス終了迄は状態監視する。
		if(left_time >= -10){
			Log.v(TAB, "left_time");
            Intent intent = new Intent(INTENT_ACTION);
            intent.putExtra(INTENT_REMAIN, left_time);
            intent.putExtra(INTENT_TIMER_LEFT, TIMER_LEFT);            	
            if(org_wifi_stat){
                intent.putExtra(INTENT_WIFI_ORG_STAT, (String) getText(R.string.wifi_stat_msg_exec));            	
            }
            else{
                intent.putExtra(INTENT_WIFI_ORG_STAT, (String) getText(R.string.wifi_stat_msg_stop));            	
            }
    		if(wifi.isWifiEnabled()) {
    			Log.v(TAB, "Wifi-Enable");
                intent.putExtra(INTENT_WIFI_STAT, (String) getText(R.string.wifi_stat_msg_exec));
    		}
    		else {
    			Log.v(TAB, "Wifi-Disable");
                intent.putExtra(INTENT_WIFI_STAT, (String) getText(R.string.wifi_stat_msg_stop));
    		}
    		try{
    			if("true".equals(method1.invoke(wifi).toString())){
    				Log.v(TAB, "WifiAP-Enable");
                    intent.putExtra(INTENT_WIFIAP_STAT, (String) getText(R.string.wifi_stat_msg_exec));
    			}else{
    				Log.v(TAB, "WifiAP-Disable");
                    intent.putExtra(INTENT_WIFIAP_STAT, (String) getText(R.string.wifi_stat_msg_stop));
    			}
    		}
    		catch (Exception e) {
    		    e.printStackTrace();
    	    }
            sendBroadcast(intent);
		}
		// タイムアップしてさらに5秒経過後にサービス（アクティビティも）を終了する。　state 4→5（アプリ終了状態）
		if(left_time < -10){
			Log.v(TAB, "service Stop!!");
	        if(timer!=null){
		        timer.cancel();
		        timer = null;
	        }
			tether = false;
			timer_status = false;
	        State = WifiState.finish;
			boolean result = stopSelfResult(ServiceId);
			Log.v(TAB, "stopSelfResult="+result);
	        Toast.makeText(this, (String) getText(R.string.toast_service_stop), Toast.LENGTH_SHORT).show();
			return;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.v(TAB, "onBind");
		// TODO Auto-generated method stub
		return null;
	}

	protected void scheduleService(){
		Log.v(TAB, "★scheduleService state="+State+" target_time="+target_time+" now="+System.currentTimeMillis());
		Context context = getBaseContext();
		Intent intent = new Intent(context, MainService.class);
		PendingIntent pendingIntent = PendingIntent.getService(context, -1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		AlarmManager alarmManager = (AlarmManager)context.getSystemService(ALARM_SERVICE);
		alarmManager.set(AlarmManager.RTC_WAKEUP,target_time, pendingIntent);
	}
	protected void cancelService(){
		Log.v(TAB, "★cancelService state="+State+" left_time="+left_time+" now="+System.currentTimeMillis());
		Context context = getBaseContext();
		Intent intent = new Intent(context, MainService.class);
		PendingIntent pendingIntent = PendingIntent.getService(context, -1, intent,PendingIntent.FLAG_UPDATE_CURRENT);
		AlarmManager alarmManager = (AlarmManager)context.getSystemService(ALARM_SERVICE);
		alarmManager.cancel(pendingIntent);
	}
}
