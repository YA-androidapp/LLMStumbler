package jp.gr.java_conf.ya.llmstumbler; // Copyright (c) 2016-2017 YA <ya.androidapp@gmail.com> All rights reserved.

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final long BsScanTime = 1000;
    private static final String DUMP_FILE = "llmstumbler.txt";
    private static final ThreadLocal<SimpleDateFormat> mFilenameFormater = new ThreadLocal<SimpleDateFormat>() {
        private static final String FILENAME_PATTERN = "yyyyMMddHHmmss";

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat(FILENAME_PATTERN, Locale.ENGLISH);
        }
    };
    private final String SEPARATOR = "|";
    public Set<String> bsAps = new HashSet<>();
    public Set<String> lteAps = new HashSet<>();
    public Set<String> wifiAps = new HashSet<>();
    private BluetoothAdapter mBluetoothAdapter;
    private boolean isRunning = false, mBackKeyPressed = false;
    private CountDownTimer mCountDownTimer;
    private String pre_location_name = "#FIRST_LOCATION_NAME#";
    private TelephonyManager mTelephonyManager;
    private WifiManager mWifiManager;
    private EditText location_name, result, white_list_bs, white_list_lte, white_list_wifi;
    private TextView list_bs, list_lte, list_wifi;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            try {
                final String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    // Log.i("LLMStumbler", "(BluetoothDevice.ACTION_FOUND.equals(action))");
                    scannedBs(device);
                }
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    };
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            try {
                // Log.i("LLMStumbler", "onLeScan()");
                scannedBs(device);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    };
    private FloatingActionButton fab;

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (!mBackKeyPressed) {
                mCountDownTimer.cancel();
                mCountDownTimer.start();

                Toast.makeText(this, getString(R.string.press_again), Toast.LENGTH_LONG).show();
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


        try {
            final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }

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
                // Android M以降はWifiの情報を取得するだけでも位置情報Permissionが必要
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if ((checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                            || (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                            || (checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED)) {
                        requestPermissions(
                                new String[]{
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_WIFI_STATE
                                },
                                1);
                        return;
                    }
                }

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

        readLogFile();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onDestroy() {
        if (mBroadcastReceiver != null) {
            try {
                unregisterReceiver(mBroadcastReceiver);
            } catch (Exception e) {
            }
        }

        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_scan) {
            // Android M以降はWifiの情報を取得するだけでも位置情報Permissionが必要
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                            new String[]{
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_WIFI_STATE
                            },
                            1);
                    return true;
                }
            }

            scan();
        } else if (id == R.id.action_clear) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.action_clear)
                    .setMessage(R.string.action_clear_message)
                    .setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    result.setText("");
                                }
                            })
                    .setNegativeButton(R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                    .setCancelable(true)
                    .create()
                    .show();
        } else if (id == R.id.action_load_default_whitelist) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.action_load_default_whitelist)
                    .setMessage(R.string.action_load_default_whitelist_message)
                    .setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    loadDefaultWhitelist();
                                }
                            })
                    .setNegativeButton(R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                    .setCancelable(true)
                    .create()
                    .show();
        } else if (id == R.id.action_log_clear) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.action_log_clear)
                    .setMessage(R.string.action_log_clear_message)
                    .setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    clearLogFile();
                                }
                            })
                    .setNegativeButton(R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                    .setCancelable(true)
                    .create()
                    .show();
        } else if (id == R.id.action_log_load) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.action_log_load)
                    .setMessage(R.string.action_log_load_message)
                    .setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    readLogFile();
                                }
                            })
                    .setNegativeButton(R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                    .setCancelable(true)
                    .create()
                    .show();
        } else if (id == R.id.action_log_save) {
            saveLogFile();
        } else if (id == R.id.action_result_copy) {
            resultCopy();
        } else if (id == R.id.action_result_paste) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.action_result_paste)
                    .setMessage(R.string.action_result_paste_message)
                    .setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    resultPaste();
                                }
                            })
                    .setNegativeButton(R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                    .setCancelable(true)
                    .create()
                    .show();
        } else if (id == R.id.action_result_shaping) {
            resultShaping();
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            scan();
        } else {
            Toast.makeText(MainActivity.this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show();
        }
    }

    private void resultCopy() {
        try {
            final ClipData.Item clipItem = new ClipData.Item(result.getText());
            final String[] mimeType = new String[1];
            mimeType[0] = ClipDescription.MIMETYPE_TEXT_PLAIN;
            final ClipData clipData = new ClipData(new ClipDescription("text", mimeType), clipItem);
            final ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboardManager.setPrimaryClip(clipData);
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void resultPaste() {
        try {
            final ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

            if (clipboardManager != null) {
                if (clipboardManager.hasPrimaryClip()) {
                    if (clipboardManager.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                        final ClipData.Item clipItem = clipboardManager.getPrimaryClip().getItemAt(0);
                        if (clipItem != null) {
                            final String pasteData = clipItem.getText().toString();
                            if (pasteData != null) {
                                final int start = result.getSelectionStart();
                                final int end = result.getSelectionEnd();
                                Editable editable = result.getText();
                                editable.replace(Math.min(start, end), Math.max(start, end), pasteData);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void resultShaping() {
        String shapedResult = "";

        // 1行1地点
        final Editable resultEditable = result.getText();
        if (resultEditable.length() > 0) {
            final String resultText = resultEditable.toString();
            final String[] lines = resultText.split("\n");
            for (final String line : lines) {
                try {
                    // Log.i("LLMStumbler", "resultShaping() line" + Integer.toString(++count1) + ":" + line);

                    if (!line.equals("")) {
                        // 1要素1AP
                        final String[] parts = line.split("\\|");

                        // 先頭は地点名
                        final StringBuilder sb = new StringBuilder();
                        // Log.i("LLMStumbler", "resultShaping() parts[0]:" + parts[0]);
                        sb.append(parts[0]);

                        // 重複を削除
                        final HashSet<String> part = new HashSet<>();
                        for (int i = 1; i < parts.length - 1; i++) {
                            // Log.i("LLMStumbler", "resultShaping() parts[" + Integer.toString(i) + "]:" + parts[i]);
                            part.add(parts[i]);
                        }

                        // join
                        for (final String p : part) {
                            sb.append("|");
                            sb.append(p);
                        }
                        shapedResult = shapedResult + sb.toString() + "\n";
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }

        result.setText(shapedResult);
    }

    @Override
    protected void onPause() {
        savePref();

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        try {
            final SharedPreferences data = getSharedPreferences("LLMS", Context.MODE_PRIVATE);
            result.setText(data.getString("result", ""));
            white_list_bs.setText(data.getString("white_list_bs", getString(R.string.white_list_bs_value)));
            white_list_lte.setText(data.getString("white_list_lte", getString(R.string.white_list_lte_value)));
            white_list_wifi.setText(data.getString("white_list_wifi", getString(R.string.white_list_wifi_value)));
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void clearLogFile() {
        result.setText("");

        try {
            final FileOutputStream fileOutputStream = openFileOutput(DUMP_FILE, MODE_PRIVATE);
            final String writeString = "";
            fileOutputStream.write(writeString.getBytes());
        } catch (FileNotFoundException e) {
            Toast.makeText(this, getString(R.string.file_not_found), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.cannot_read_file), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadDefaultWhitelist() {
        try {
            white_list_bs.setText(getString(R.string.white_list_bs_value));
            white_list_lte.setText(getString(R.string.white_list_lte_value));
            white_list_wifi.setText(getString(R.string.white_list_wifi_value));
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void readLogFile() {
        try {
            final FileInputStream fileInputStream = openFileInput(DUMP_FILE);
            final byte[] readBytes = new byte[fileInputStream.available()];
            fileInputStream.read(readBytes);
            final String readString = new String(readBytes);
            // Log.i("LLMStumbler", "readLogFile() readString:" + readString);
            result.setText(readString);
        } catch (FileNotFoundException e) {
            Toast.makeText(this, getString(R.string.file_not_found), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.cannot_write_file), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveLogFile() {
        try {
            final FileOutputStream fileOutputStream = openFileOutput(DUMP_FILE, MODE_PRIVATE);
            final String writeString = result.getText().toString();
            fileOutputStream.write(writeString.getBytes());
        } catch (FileNotFoundException e) {
            Toast.makeText(this, getString(R.string.file_not_found), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.cannot_read_file), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void save() {
        // Log.i("LLMStumbler", "save()");

        try {
            final StringBuilder sb = new StringBuilder();
            if (!pre_location_name.equals(location_name.getText().toString())) {
                pre_location_name = location_name.getText().toString();
                if (!result.getText().toString().equals(""))
                    sb.append("\n");

                if (!location_name.getText().toString().equals(""))
                    sb.append(location_name.getText());

                final String now = mFilenameFormater.get().format(new Date());
                // Log.i("LLMStumbler", "save() now:" + now);
                sb.append("(");
                sb.append(now);
                sb.append(")");
            }
            // Log.i("LLMStumbler", "save() join(lteAps)");
            sb.append(join(lteAps));
            // Log.i("LLMStumbler", "save() join(wifiAps)");
            sb.append(join(wifiAps));
            // Log.i("LLMStumbler", "save() join(bsAps)");
            sb.append(join(bsAps));
            final String content = sb.toString();
            // Log.i("LLMStumbler", "save() content:");
            // Log.i("LLMStumbler", content);

            result.setText(result.getText().toString() + content);
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }

        try {
            result.setSelection(result.getText().length());
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }

        savePref();
    }

    private void savePref() {
        try {
            final SharedPreferences data = getSharedPreferences("LLMS", Context.MODE_PRIVATE);
            final SharedPreferences.Editor editor = data.edit();

            if (result.getText().length() > 0) {
                editor.putString("result", result.getText().toString());
            } else {
                editor.putString("result", "");
            }

            if (white_list_bs.getText().length() > 0) {
                editor.putString("white_list_bs", white_list_bs.getText().toString());
            } else {
                editor.putString("white_list_bs", "");
            }

            if (white_list_lte.getText().length() > 0) {
                editor.putString("white_list_lte", white_list_lte.getText().toString());
            } else {
                editor.putString("white_list_lte", "");
            }

            if (white_list_wifi.getText().length() > 0) {
                editor.putString("white_list_wifi", white_list_wifi.getText().toString());
            } else {
                editor.putString("white_list_wifi", "");
            }

            editor.apply();
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String join(Set<String> aps) {
        try {
            // Log.i("LLMStumbler", "join()");
            final StringBuilder sb = new StringBuilder();
            for (String str : aps) {
                sb.append(SEPARATOR);
                sb.append(str);
            }

            // Log.i("LLMStumbler", "join() " + sb.toString());
            return sb.toString();
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return "";
    }

    private void scan() {
        // Log.i("LLMStumbler", "scan()");

        try {
            bsAps.clear();
            lteAps.clear();
            wifiAps.clear();
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }

        try {
            scanBs();
            scanWifi();
            scanLte();
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }

        if (!isRunning) {
            try {
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        isRunning = true;
                        try {
                            save();
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                        isRunning = false;
                    }
                }, 2 * BsScanTime);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void scanBs() {
        try {
            // Log.i("LLMStumbler", "scanBs()");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                    registerReceiver(mBroadcastReceiver, filter);
                    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    if (mBluetoothAdapter != null) {
                        if (!mBluetoothAdapter.isEnabled()) {
                            mBluetoothAdapter.enable();
                        }
                        mBluetoothAdapter.startDiscovery();
                        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                            mBluetoothAdapter.startLeScan(mLeScanCallback);
                        }
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (mBluetoothAdapter.isDiscovering()) {
                                    mBluetoothAdapter.cancelDiscovery();
                                }
                                if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                                }
                                unregisterReceiver(mBroadcastReceiver);
                            }
                        }, BsScanTime);
                    }
                }
            }).start();
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void scannedBs(BluetoothDevice device) {
        try {
            // ホワイトリスト
            Editable whiteListBsEditable = white_list_lte.getText();
            String whiteListBsString = "";
            if (whiteListBsEditable.length() > 0)
                whiteListBsString = whiteListBsEditable.toString();

            if ((!device.getName().equals("")) && (!device.getAddress().equals(""))) {
                final String bsResult = "B:" + device.getName() + ":" + device.getAddress();
                if ((whiteListBsString.equals("")) || (("," + whiteListBsString.toLowerCase(Locale.ENGLISH) + ",").contains("," + device.getName().toLowerCase(Locale.ENGLISH) + ","))) {
                    bsAps.add(bsResult);

                    runOnUiThread(new Runnable() {
                        public void run() {
                            list_bs.setText(bsResult);
                        }
                    });
                }
            }
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void scanLte() {
        try {
            // Log.i("LLMStumbler", "scanLte()");
            if (mTelephonyManager == null)
                mTelephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    // ホワイトリスト
                    Editable whiteListLteEditable = white_list_lte.getText();
                    String whiteListLteString = "";
                    if (whiteListLteEditable.length() > 0)
                        whiteListLteString = whiteListLteEditable.toString();

                    final List<CellInfo> cellInfoList = mTelephonyManager.getAllCellInfo();
                    if (cellInfoList != null) {
                        // Log.i("LLMStumbler", "getScannedLte() (cellInfoList != null)");
                        if (cellInfoList.size() > 0) {
                            // Log.i("LLMStumbler", "getScannedLte() (cellInfoList.size() > 0)");
                            for (CellInfo cellInfo : cellInfoList) {
                                try {
                                    if (cellInfo instanceof CellInfoLte) {
                                        final CellInfoLte cellInfoLte = (CellInfoLte) cellInfo;
                                        final CellIdentityLte cellIdentityLte = cellInfoLte.getCellIdentity();
                                        if ((cellIdentityLte.getCi() < Integer.MAX_VALUE) && (cellIdentityLte.getCi() > -1) &&
                                                (cellIdentityLte.getMcc() < Integer.MAX_VALUE) && (cellIdentityLte.getMcc() > -1) &&
                                                (cellIdentityLte.getMnc() < Integer.MAX_VALUE) && (cellIdentityLte.getMnc() > -1)) {
                                            final String lteResult = Integer.toString(cellIdentityLte.getCi()) + ":" + Integer.toString(cellIdentityLte.getMcc()) + ":" + Integer.toString(cellIdentityLte.getMnc());
                                            if ((whiteListLteString.equals("")) || (("," + whiteListLteString.toLowerCase(Locale.ENGLISH) + ",").contains("," + Integer.toString(cellIdentityLte.getCi()).toLowerCase(Locale.ENGLISH) + ","))) {
                                                lteAps.add(lteResult);

                                                runOnUiThread(new Runnable() {
                                                    public void run() {
                                                        list_lte.setText(lteResult);
                                                    }
                                                });
                                            }
                                        }
                                    } else if (cellInfo instanceof CellInfoGsm) {
                                        final CellInfoGsm cellInfoGsm = (CellInfoGsm) cellInfo;
                                        final CellIdentityGsm cellIdentityGsm = cellInfoGsm.getCellIdentity();
                                        if ((cellIdentityGsm.getCid() < Integer.MAX_VALUE) && (cellIdentityGsm.getCid() > -1) &&
                                                (cellIdentityGsm.getMcc() < Integer.MAX_VALUE) && (cellIdentityGsm.getMcc() > -1) &&
                                                (cellIdentityGsm.getMnc() < Integer.MAX_VALUE) && (cellIdentityGsm.getMnc() > -1)) {
                                            final String gsmResult = Integer.toString(cellIdentityGsm.getCid()) + ":" + Integer.toString(cellIdentityGsm.getMcc()) + ":" + Integer.toString(cellIdentityGsm.getMnc());
                                            if ((whiteListLteString.equals("")) || (("," + whiteListLteString.toLowerCase(Locale.ENGLISH) + ",").contains("," + Integer.toString(cellIdentityGsm.getCid()).toLowerCase(Locale.ENGLISH) + ","))) {
                                                lteAps.add(gsmResult);

                                                runOnUiThread(new Runnable() {
                                                    public void run() {
                                                        list_lte.setText(gsmResult);
                                                    }
                                                });
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            }
                        }
                    }
                }
            }).start();
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void scanWifi() {
        try {
            // Log.i("LLMStumbler", "scanWifi()");
            if (mWifiManager == null)
                mWifiManager = (WifiManager) (this.getApplicationContext()).getSystemService(WIFI_SERVICE);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    // ホワイトリスト
                    final Editable whiteListWifiEditable = white_list_wifi.getText();
                    String whiteListWifiString = "";
                    if (whiteListWifiEditable.length() > 0)
                        whiteListWifiString = whiteListWifiEditable.toString();

                    mWifiManager.startScan();
                    final List<ScanResult> aps = mWifiManager.getScanResults();
                    Log.i("LLMStumbler", "scanWifi() aps.size():" + aps.size());
                    for (final ScanResult sr : aps) {
                        try {
                            // Log.i("LLMStumbler", "scanWifi() ScanResult:" + sr.toString());
                            final String bssid = ((sr.BSSID != null) ? sr.BSSID : "");
                            final String ssid = ((sr.SSID != null) ? sr.SSID : "");

                            if (!bssid.equals("")) {
                                final String wifiResult = "M:" + bssid + "@" + ssid;
                                // Log.i("LLMStumbler", "scanWifi() wifiResult:" + wifiResult.toLowerCase() + " : " + (("," + whiteListWifiString.toLowerCase() + ",").indexOf("," + ssid.toLowerCase() + ",")));
                                if ((whiteListWifiString.equals("")) || (("," + whiteListWifiString.toLowerCase() + ",").indexOf("," + ssid.toLowerCase() + ",")>-1)) {
                                    wifiAps.add(wifiResult);

                                    runOnUiThread(new Runnable() {
                                        public void run() {
                                            list_wifi.setText(wifiResult);
                                        }
                                    });
                                }
                            }
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "1:" + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                }
            }).start();
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, "0:" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
