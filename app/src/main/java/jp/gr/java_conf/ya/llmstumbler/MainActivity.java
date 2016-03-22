package jp.gr.java_conf.ya.llmstumbler; // Copyright (c) 2016 YA <ya.androidapp@gmail.com> All rights reserved.

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    public Set<String> bsAps = new HashSet<>();
    public Set<String> lteAps = new HashSet<>();
    public Set<String> wifiAps = new HashSet<>();

    private BluetoothAdapter mBluetoothAdapter;
    private boolean mBackKeyPressed = false;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.v("LLMStumbler", "getScannedBs()");
                if ((!device.getName().equals("")) && (!device.getAddress().equals(""))) {
                    final String bsResult = "B:" + device.getName() + ":" + device.getAddress();

                    if (white_list_bs.getText().toString().equals("")) {
                        bsAps.add(bsResult);
                    } else if (("," + white_list_bs.getText().toString() + ",").contains("," + device.getName() + ",")) {
                        bsAps.add(bsResult);
                    }

                    runOnUiThread(new Runnable() {
                        public void run() {
                            list_bs.setText(bsResult);
                        }
                    });
                }
            }
        }
    };
    private CountDownTimer mCountDownTimer;
    private static final String DUMP_FILE = "llmstumbler.txt";
    private final String SEPARATOR = "|";
    private TelephonyManager mTelephonyManager;
    private static final ThreadLocal<SimpleDateFormat> mFilenameFormater = new ThreadLocal<SimpleDateFormat>() {
        private static final String FILENAME_PATTERN = "yyyyMMddHHmmss";

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat(FILENAME_PATTERN, Locale.ENGLISH);
        }
    };
    private WifiManager mWifiManager;

    private EditText location_name, result, white_list_bs, white_list_lte, white_list_wifi;
    private TextView list_bs, list_lte, list_wifi;
    private FloatingActionButton fab;

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (!mBackKeyPressed) {
                mCountDownTimer.cancel();
                mCountDownTimer.start();

                Toast.makeText(this, getString(R.string.press_again), Toast.LENGTH_SHORT).show();
                mBackKeyPressed = true;
                return false;
            }
            return super.dispatchKeyEvent(event);
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mCountDownTimer = new CountDownTimer(1000, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                mBackKeyPressed = false;
            }
        };

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scan();
            }
        });
        list_bs = (TextView) findViewById(R.id.list_bs);
        list_lte = (TextView) findViewById(R.id.list_lte);
        list_wifi = (TextView) findViewById(R.id.list_wifi);
        location_name = (EditText) findViewById(R.id.location_name);
        result = (EditText) findViewById(R.id.result);
        white_list_bs = (EditText) findViewById(R.id.white_list_bs);
        white_list_lte = (EditText) findViewById(R.id.white_list_lte);
        white_list_wifi = (EditText) findViewById(R.id.white_list_wifi);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_save) {
            save();
        } else if (id == R.id.action_scan) {
            scan();
        } else if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void save() {
        Log.v("LLMStumbler", "save()");

        StringBuilder sb = new StringBuilder();
        if (location_name.getText().toString().equals("")) {
            sb.append(mFilenameFormater.get().format(new Date()));
        } else {
            sb.append(location_name.getText());
        }
        Log.v("LLMStumbler", "save() join(lteAps)");
        sb.append(join(lteAps));
        Log.v("LLMStumbler", "save() join(wifiAps)");
        sb.append(join(wifiAps));
        Log.v("LLMStumbler", "save() join(bsAps)");
        sb.append(join(bsAps));
        sb.append("\n");
        final String content = sb.toString()+"\n";
        Log.v("LLMStumbler", "save() content:");
        Log.v("LLMStumbler", content);

        result.setText(content);

        try {
            FileOutputStream outStream = openFileOutput(DUMP_FILE, Context.MODE_APPEND);
            OutputStreamWriter writer = new OutputStreamWriter(outStream);
            writer.write(content);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.cannot_write_file), Toast.LENGTH_SHORT).show();
            Log.v("LLMStumbler", getString(R.string.cannot_write_file));
        }
    }

    private String join(Set<String> aps) {
        Log.v("LLMStumbler", "join()");
        StringBuilder sb = new StringBuilder();
        for (String str : aps) {
            sb.append(SEPARATOR);
            sb.append(str);
        }

        Log.v("LLMStumbler", "join() " + sb.toString());
        return sb.toString();
    }

    private void scan() {
        Log.v("LLMStumbler", "scan()");
        scanBs();
        scanWifi();
        scanLte();
    }

    private void scanBs() {
        Log.v("LLMStumbler", "scanBs()");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                registerReceiver(mBroadcastReceiver, filter);
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (!mBluetoothAdapter.isEnabled()) {
                    mBluetoothAdapter.enable();
                }
                mBluetoothAdapter.startDiscovery();
                sleep(10000L);
                if (mBluetoothAdapter.isDiscovering()) {
                    mBluetoothAdapter.cancelDiscovery();
                }
                unregisterReceiver(mBroadcastReceiver);
            }
        }).start();
    }

    private void scanLte() {
        Log.v("LLMStumbler", "scanLte()");
        if (mTelephonyManager == null)
            mTelephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        List<CellInfo> cellInfoList = mTelephonyManager.getAllCellInfo();
        if (cellInfoList != null) {
            Log.v("LLMStumbler", "getScannedLte() (cellInfoList != null)");
            if (cellInfoList.size() > 0) {
                Log.v("LLMStumbler", "getScannedLte() (cellInfoList.size() > 0)");
                for (CellInfo cellInfo : cellInfoList) {
                    String lteResult = "";
                    if (cellInfo instanceof CellInfoLte) {
                        final CellInfoLte cellInfoLte = (CellInfoLte) cellInfo;
                        final CellIdentityLte cellIdentityLte = cellInfoLte.getCellIdentity();
                        if ((cellIdentityLte.getCi() < Integer.MAX_VALUE) && (cellIdentityLte.getCi() > -1) &&
                                (cellIdentityLte.getMcc() < Integer.MAX_VALUE) && (cellIdentityLte.getMcc() > -1) &&
                                (cellIdentityLte.getMnc() < Integer.MAX_VALUE) && (cellIdentityLte.getMnc() > -1)) {
                            lteResult = Integer.toString(cellIdentityLte.getCi()) + ":" + Integer.toString(cellIdentityLte.getMcc()) + ":" + Integer.toString(cellIdentityLte.getMnc());
                            if (white_list_lte.getText().toString().equals("")) {
                                lteAps.add(lteResult);
                            } else if (("," + white_list_lte.getText().toString() + ",").contains("," + Integer.toString(cellIdentityLte.getCi()) + ",")) {
                                lteAps.add(lteResult);
                            }

                            final String finalLteResult = lteResult;
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    list_lte.setText(finalLteResult);
                                }
                            });
                        }
                    } else if (cellInfo instanceof CellInfoGsm) {
                        final CellInfoGsm cellInfoGsm = (CellInfoGsm) cellInfo;
                        final CellIdentityGsm cellIdentityGsm = cellInfoGsm.getCellIdentity();
                        if ((cellIdentityGsm.getCid() < Integer.MAX_VALUE) && (cellIdentityGsm.getCid() > -1) &&
                                (cellIdentityGsm.getMcc() < Integer.MAX_VALUE) && (cellIdentityGsm.getMcc() > -1) &&
                                (cellIdentityGsm.getMnc() < Integer.MAX_VALUE) && (cellIdentityGsm.getMnc() > -1)) {
                            lteResult = Integer.toString(cellIdentityGsm.getCid()) + ":" + Integer.toString(cellIdentityGsm.getMcc()) + ":" + Integer.toString(cellIdentityGsm.getMnc());
                            if (white_list_lte.getText().toString().equals("")) {
                                lteAps.add(lteResult);
                            } else if (("," + white_list_lte.getText().toString() + ",").contains("," + Integer.toString(cellIdentityGsm.getCid()) + ",")) {
                                lteAps.add(lteResult);
                            }

                            final String finalLteResult = lteResult;
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    list_lte.setText(finalLteResult);
                                }
                            });
                        }
                    }
                }
            }
        }
    }

    private void scanWifi() {
        Log.v("LLMStumbler", "scanWifi()");
        if (mWifiManager == null)
            mWifiManager = (WifiManager) getSystemService(WIFI_SERVICE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                mWifiManager.startScan();
                final List<ScanResult> aps = mWifiManager.getScanResults();
                for (final ScanResult sr : aps) {
                    if ((!sr.BSSID.equals("")) && (!sr.SSID.equals(""))) {
                        final String wifiResult = "M:" + sr.BSSID + "@" + (sr.SSID).replaceAll("-", "\\d");

                        if (white_list_wifi.getText().toString().equals("")) {
                            wifiAps.add(wifiResult);
                        } else if (("," + white_list_wifi.getText().toString() + ",").contains("," + sr.SSID + ",")) {
                            wifiAps.add(wifiResult);
                        }

                        runOnUiThread(new Runnable() {
                            public void run() {
                                list_wifi.setText(wifiResult);
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
        }
    }
}
