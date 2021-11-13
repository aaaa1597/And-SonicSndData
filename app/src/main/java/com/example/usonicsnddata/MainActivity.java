package com.example.usonicsnddata;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.icu.text.MessageFormat;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
	private static final int SAMPLING_FRAQ = 48000;
	private static final float SEC_PER_SAMPLEPOINT = 1.0f / SAMPLING_FRAQ;
	private static final int AMP = 4000;
	private static final int FREQ_BASE = 400;
	private static final int FREQ_STEP = 20;
	private static final int FREQ_STARTSTOP = 300;
	private final short[] m300HzWave = new short[SAMPLING_FRAQ];
	private final short[][] mSignals = new short[256][SAMPLING_FRAQ/10];

	final int bufferSizeInBytes = AudioRecord.getMinBufferSize(SAMPLING_FRAQ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
	AudioTrack mAudioTrack = new AudioTrack.Builder()
			.setAudioAttributes(new AudioAttributes.Builder()
					.setUsage(AudioAttributes.USAGE_UNKNOWN)
					.setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
					.build())
			.setAudioFormat(new AudioFormat.Builder()
					.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
					.setSampleRate(SAMPLING_FRAQ)
					.setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
					.build())
			.setBufferSizeInBytes(bufferSizeInBytes)
			.build();

	/* サイン波データを生成 */
	private void createSineWave(short[] buf, int freq, int amplitude, boolean doClear) {
		if (doClear) {
			Arrays.fill(buf, (short) 0);
		}
		for (int i = 0; i < buf.length; i++) {
			float currentSec = i * SEC_PER_SAMPLEPOINT; // 現在位置の経過秒数
			double val = amplitude * Math.sin(2.0 * Math.PI * freq * currentSec);
			buf[i] += (short)val;
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		TLog.d("onCreate");

		/* 権限チェック アプリにAudio権限がなければ要求する。 */
		if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			/* RECORD_AUDIOの実行権限を要求 */
			requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 2222);
		}

		/* 先頭・終端の目印用信号データ */
		createSineWave(m300HzWave, FREQ_STARTSTOP, AMP, true);

		/* 256種類の信号データを生成 */
		for (int i = 0; i < 256; i++) {
			createSineWave(mSignals[i], (short) (FREQ_BASE + FREQ_STEP*i), AMP, true);
		}

		/* 送信ボタン押下イベント */
		findViewById(R.id.btnSndSonic).setOnClickListener(v -> {
			new Thread(() -> {
				TLog.d("OnClickListener() 送信処理 s");
				/* 送信データ取得 */
				String snddatastr = ((EditText)findViewById(R.id.edtSndData)).getText().toString();

				if(snddatastr.length() == 0)
					return;

                byte[] sndBytes = snddatastr.getBytes(StandardCharsets.UTF_8);
                TLog.d("送信データ={0}:{1}", snddatastr, bytesToHex(sndBytes));

				/* 送信開始 */
				TLog.d("送信開始");
				mAudioTrack.play();
                TLog.d("開始信号送信 1s");
				mAudioTrack.write(m300HzWave, 0, SAMPLING_FRAQ);	/* 開始信号送信 */
				TLog.d("データ送信 s");
				for (int i = 0; i < sndBytes.length; i++) {
                    mAudioTrack.write(mSignals[sndBytes[i]], 0, SAMPLING_FRAQ/10);
                }
				TLog.d("データ送信 e");
                TLog.d("終端信号送信 1s");
				mAudioTrack.write(m300HzWave, 0, SAMPLING_FRAQ); /* 終終端信号送信 */

				mAudioTrack.stop();
				mAudioTrack.flush();

				TLog.d("OnClickListener() 送信処理 e");
			}).start();
		});


	}

	private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		TLog.d("onDestroy");
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
		}
		if (mAudioTrack != null) {
			if (mAudioTrack.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) {
				TLog.d("cleanup mAudioTrack");
				mAudioTrack.stop();
				mAudioTrack.flush();
			}
			mAudioTrack = null;
		}
	}

	/* 権限取得コールバック */
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		TLog.d("s");
		/* 権限リクエストの結果を取得する. */
		if (requestCode == 2222) {
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				/* RECORD_AUDIOの権限を得た */
				TLog.d("RECORD_AUDIOの実行権限を取得!! OK.");
			} else {
				ErrPopUp.create(MainActivity.this).setErrMsg("失敗しました。\n\"許可\"を押下して、このアプリにAUDIO録音の権限を与えて下さい。\n終了します。").Show(MainActivity.this);
			}
		}
		/* 知らん応答なのでスルー。 */
		else {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
		TLog.d("e");
	}

	/* ログ出力 */
	public static class TLog {
		public static void d(String logstr) {
			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("{0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			Log.d("aaaaa", MessageFormat.format("{0} {1}",head, logstr));
		}

		public static void d(String fmt, Object... args) {
			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("{0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			String arglogstr =  MessageFormat.format(fmt, (Object[])args);
			Log.d("aaaaa", MessageFormat.format("{0} {1}",head, arglogstr));
		}

		public static void i(String logstr) {
			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("{0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			Log.i("aaaaa", MessageFormat.format("{0} {1}",head, logstr));
		}

		public static void i(String fmt, Object... args) {
			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("{0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			String arglogstr =  MessageFormat.format(fmt, (Object[])args);
			Log.i("aaaaa", MessageFormat.format("{0} {1}",head, arglogstr));
		}

		public static void w(String logstr) {
			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("{0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			Log.w("aaaaa", MessageFormat.format("{0} {1}",head, logstr));
		}

		public static void w(String fmt, Object... args) {
			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("{0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			String arglogstr =  MessageFormat.format(fmt, (Object[])args);
			Log.w("aaaaa", MessageFormat.format("{0} {1}",head, arglogstr));
		}

		public static void e(String logstr) {
			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("{0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			Log.e("aaaaa", MessageFormat.format("{0} {1}",head, logstr));
		}

		public static void e(String fmt, Object... args) {
			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("{0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			String arglogstr =  MessageFormat.format(fmt, (Object[])args);
			Log.e("aaaaa", MessageFormat.format("{0} {1}",head, arglogstr));
		}
	}

	/* エラーpopup */
	public static class ErrPopUp extends PopupWindow {
		/* コンストラクタ */
		private ErrPopUp(Activity activity) {
			super(activity);
		}

		/* windows生成 */
		public static ErrPopUp create(Activity activity) {
			ErrPopUp retwindow = new ErrPopUp(activity);
			View popupView = activity.getLayoutInflater().inflate(R.layout.popup_layout, null);
			popupView.findViewById(R.id.btnClose).setOnClickListener(v -> {
				android.os.Process.killProcess(android.os.Process.myPid());
			});
			retwindow.setContentView(popupView);
			/* 背景設定 */
			retwindow.setBackgroundDrawable(ResourcesCompat.getDrawable(activity.getResources(), R.drawable.popup_background, null));

			/* タップ時に他のViewでキャッチされないための設定 */
			retwindow.setOutsideTouchable(true);
			retwindow.setFocusable(true);

			/* 表示サイズの設定 今回は幅300dp */
			float width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300, activity.getResources().getDisplayMetrics());
			retwindow.setWidth((int)width);
			retwindow.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
			return retwindow;
		}

		/* 文字列設定 */
		public ErrPopUp setErrMsg(String errmsg) {
			((TextView)this.getContentView().findViewById(R.id.txtErrMsg)).setText(errmsg);
			return this;
		}

		/* 表示 */
		public void Show(Activity activity) {
			View anchor = ((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
			this.showAtLocation(anchor, Gravity.CENTER,0, 0);
		}
	}
}