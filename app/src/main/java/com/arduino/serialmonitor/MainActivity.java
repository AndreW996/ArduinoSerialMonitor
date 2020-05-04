package com.arduino.serialmonitor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.text.Html;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.BaseSeries;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.DataPointInterface;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.OnDataPointTapListener;
import com.jjoe64.graphview.series.PointsGraphSeries;
import com.jjoe64.graphview.series.Series;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private CheckBox doNotShowAgain;
    private CheckBox channel1;
    private CheckBox channel2;
    private CheckBox channel3;
    private Button connectButton;
    private Button startButton;
    private Button saveButton;
    private Button loadButton;
    private EditText editTextFrequency;
    private GraphView graphView;
    private PointsGraphSeries<DataPoint> pDataSeries1;
    private PointsGraphSeries<DataPoint> pDataSeries2;
    private PointsGraphSeries<DataPoint> pDataSeries3;
    private LineGraphSeries<DataPoint> lDataSeries1;
    private LineGraphSeries<DataPoint> lDataSeries2;
    private LineGraphSeries<DataPoint> lDataSeries3;
    private double dataX;
    private double frequency;
    private UsbManager usbManager;
    private UsbDevice myDevice;
    private UsbDeviceConnection connection;
    private UsbSerialDevice serialDevice;
    private String buffer;
    private boolean monitoring;
    private boolean connected;
    private boolean pointed;
    private boolean lastState;
    private int channels;
    private int selectedBaud;

    private UsbSerialInterface.UsbReadCallback callback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] data) {
            String dataUtf8 = new String(data, StandardCharsets.UTF_8);
            buffer += dataUtf8;
            int index;
            while ((index = buffer.indexOf('\n')) != -1) {
                final String dataStr = buffer.substring(0, index + 1).trim();
                buffer = buffer.length() == index ? "" : buffer.substring(index + 1);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onSerialDataReceived(dataStr);
                    }
                });
            }
        }
    };

    private final BroadcastReceiver usbDetachedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    Toast.makeText(getApplicationContext(), getString(R.string.usb_detached),
                            Toast.LENGTH_SHORT).show();
                    stopUsbConnection();
                }
            }
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Window window = getWindow();
        window.setStatusBarColor(getResources().getColor(R.color.black, null));

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(Html.fromHtml("<font color=\"white\">"
                + getString(R.string.app_name) + "</font>", 0));
        setSupportActionBar(toolbar);

        selectedBaud = Integer.parseInt(getString(R.string.baud_115200));

        usbManager = getSystemService(UsbManager.class);

        dataX = 0;
        frequency = 1;
        buffer = "";
        monitoring = false;
        connected = false;
        pointed = false;
        lastState = false;
        channels = 0;

        graphView = findViewById(R.id.graph);
        graphView.setBackgroundColor(getResources().getColor(R.color.backgroundColor, null));
        graphView.getViewport().setBackgroundColor(getResources().getColor(R.color.white, null));
        graphView.getViewport().setMinX(0);
        graphView.getViewport().setMaxX(10);
        graphView.getViewport().setScrollable(true);
        graphView.getViewport().setScalable(true);

        lDataSeries1 = new LineGraphSeries<>();
        lDataSeries2 = new LineGraphSeries<>();
        lDataSeries3 = new LineGraphSeries<>();

        pDataSeries1 = new PointsGraphSeries<>();
        pDataSeries2 = new PointsGraphSeries<>();
        pDataSeries3 = new PointsGraphSeries<>();

        initDataSeries();

        connectButton = findViewById(R.id.connectButton);
        connectButton.setEnabled(false);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectButtonFunc();
            }
        });

        startButton = findViewById(R.id.startButton);
        startButton.setEnabled(false);
        startButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startButtonFunc();
            }
        });

        editTextFrequency = findViewById(R.id.edit_text_freq);
        editTextFrequency.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                editTextFrequency.setCursorVisible(true);
                return false;
            }
        });
        editTextFrequency.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    editTextFrequency.setCursorVisible(false);
                }
                return false;
            }
        });

        Button confirmButton = findViewById(R.id.confirm_button);
        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmFreq();
            }
        });

        saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveButtonMethod();
            }
        });

        loadButton = findViewById(R.id.loadButton);
        loadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadButtonMethod();
            }
        });

        channel1 = findViewById(R.id.checkBox1);
        channel1.setEnabled(false);
        channel1.setTextColor(Color.BLUE);
        channel1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                channel1Handler();
            }
        });

        channel2 = findViewById(R.id.checkBox2);
        channel2.setEnabled(false);
        channel2.setTextColor(Color.RED);
        channel2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                channel2Handler();
            }
        });

        channel3 = findViewById(R.id.checkBox3);
        channel3.setEnabled(false);
        channel3.setTextColor(Color.GREEN);
        channel3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                channel3Handler();
            }
        });

        // Detach events are sent as a system-wide broadcast
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbDetachedReceiver, filter);

        startUsbConnection();

        hideSystemUI();

        showStartingDialog();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbDetachedReceiver);
        stopUsbConnection();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        hideSystemUI();
        switch (item.getItemId()) {
            case R.id.baud1:
                selectedBaud = Integer.parseInt(getString(R.string.baud_9600));
                return true;
            case R.id.baud2:
                selectedBaud = Integer.parseInt(getString(R.string.baud_57600));
                return true;
            case R.id.baud3:
                selectedBaud = Integer.parseInt(getString(R.string.baud_115200));
                return true;
            case R.id.baud4:
                selectedBaud = Integer.parseInt(getString(R.string.baud_230400));
                return true;
            case R.id.baud5:
                selectedBaud = Integer.parseInt(getString(R.string.baud_250000));
                return true;
            case R.id.info:
                showInfoDialog();
                return true;
            case R.id.pointed:
                pointed = true;
                if (!lastState) {
                    onDataSeriesChange();
                    lastState = true;
                }
                return true;
            case R.id.line:
                pointed = false;
                if (lastState) {
                    onDataSeriesChange();
                    lastState = false;
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private void showStartingDialog() {
        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        View eulaLayout = View.inflate(this, R.layout.starting_dialog, null);
        SharedPreferences settings = getSharedPreferences(getString(R.string.prefs_name), 0);
        String skipMessage = settings.getString(getString(R.string.skip_msg), getString(R.string.not_checked));

        doNotShowAgain = eulaLayout.findViewById(R.id.skip);
        adb.setView(eulaLayout);
        adb.setTitle(getString(R.string.attention));
        adb.setMessage(getString(R.string.start_dialog_text));

        adb.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String checkBoxResult = getString(R.string.not_checked);

                if (doNotShowAgain.isChecked()) {
                    checkBoxResult = getString(R.string.checked);
                }

                SharedPreferences settings = getSharedPreferences(getString(R.string.prefs_name), 0);
                SharedPreferences.Editor editor = settings.edit();

                editor.putString(getString(R.string.skip_msg), checkBoxResult);
                editor.apply();

                showInfoDialog();
            }
        });

        adb.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String checkBoxResult = getString(R.string.not_checked);

                if (doNotShowAgain.isChecked()) {
                    checkBoxResult = getString(R.string.checked);
                }

                SharedPreferences settings = getSharedPreferences(getString(R.string.prefs_name), 0);
                SharedPreferences.Editor editor = settings.edit();

                editor.putString(getString(R.string.skip_msg), checkBoxResult);
                editor.apply();
            }
        });

        if (!skipMessage.equals(getString(R.string.checked))) {
            adb.show();
        }
        super.onResume();
    }

    private void showInfoDialog() {
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle(R.string.alert_title);
        alertDialog.setMessage(getString(R.string.info_text));
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }

    private void confirmFreq() {
        String input = editTextFrequency.getText().toString();
        if (input.isEmpty() || 0 == Double.parseDouble(input)) {
            Toast.makeText(this, getString(R.string.freq_toast), Toast.LENGTH_LONG).show();
        } else {
            frequency = Double.parseDouble(input);
            connectButton.setEnabled(true);
        }
    }

    private void connectButtonFunc() {
        if (!connected) {
            startSerialConnection(myDevice);
            connectButton.setText(R.string.disconnect_button);
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            if (monitoring) {
                startButtonFunc();
            }
            stopUsbConnection();
            connectButton.setText(R.string.connect_button);
        }
        startButtonEnabler();
    }

    private void startButtonEnabler() {
        if (connected) {
            startButton.setEnabled(true);
        } else {
            startButton.setEnabled(false);
        }
    }

    private void startButtonFunc() {
        if (!monitoring) {
            startButton.setText(R.string.stop_button);
            dataX = 0;
            lDataSeries1.resetData(new DataPoint[]{});
            lDataSeries2.resetData(new DataPoint[]{});
            lDataSeries3.resetData(new DataPoint[]{});
            pDataSeries1.resetData(new DataPoint[]{});
            pDataSeries2.resetData(new DataPoint[]{});
            pDataSeries3.resetData(new DataPoint[]{});
            graphView.removeAllSeries();
            graphView.getViewport().setMinX(0);
            graphView.getViewport().setMaxX(10);
            checkChannels();
            saveButton.setEnabled(false);
            loadButton.setEnabled(false);
        } else {
            startButton.setText(R.string.start_button);
            saveButton.setEnabled(true);
            loadButton.setEnabled(true);
        }
        monitoring = !monitoring;
    }

    private void checkChannels() {
        switch (channels) {
            case 1:
                channel1.setEnabled(true);
                channel1.setChecked(true);
                channel2.setChecked(false);
                channel3.setChecked(false);
                channel2.setEnabled(false);
                channel3.setEnabled(false);
                break;
            case 2:
                channel1.setEnabled(true);
                channel1.setChecked(true);
                channel2.setEnabled(true);
                channel2.setChecked(true);
                channel3.setChecked(false);
                channel3.setEnabled(false);
                break;
            case 3:
                channel1.setEnabled(true);
                channel1.setChecked(true);
                channel2.setEnabled(true);
                channel2.setChecked(true);
                channel3.setEnabled(true);
                channel3.setChecked(true);
                break;
        }
        channel1Handler();
        channel2Handler();
        channel3Handler();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveToFile();
            }
        }
        if (requestCode == 2) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadFromFile();
            }
        }
    }

    private void saveButtonMethod() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            saveToFile();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    private boolean isExternalStorageWritable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    private void saveToFile() {
        if (isExternalStorageWritable()) {

            Iterator<DataPoint> iterator1;
            Iterator<DataPoint> iterator2;
            Iterator<DataPoint> iterator3;
            StringBuilder sb = new StringBuilder(getString(R.string.channels) + "\r\n");

            if (pointed) {
                iterator1 = pDataSeries1.getValues(pDataSeries1.getLowestValueX(), pDataSeries1.getHighestValueX());
                iterator2 = pDataSeries2.getValues(pDataSeries2.getLowestValueX(), pDataSeries2.getHighestValueX());
                iterator3 = pDataSeries3.getValues(pDataSeries3.getLowestValueX(), pDataSeries3.getHighestValueX());
            } else {
                iterator1 = lDataSeries1.getValues(lDataSeries1.getLowestValueX(), lDataSeries1.getHighestValueX());
                iterator2 = lDataSeries2.getValues(lDataSeries2.getLowestValueX(), lDataSeries2.getHighestValueX());
                iterator3 = lDataSeries3.getValues(lDataSeries3.getLowestValueX(), lDataSeries3.getHighestValueX());
            }
            while (iterator1.hasNext()) {
                DataPoint dp = iterator1.next();
                sb.append(dp.getX());
                sb.append(",");
                sb.append(dp.getY());

                if (iterator2.hasNext()) {
                    dp = iterator2.next();
                    sb.append(",");
                    sb.append(dp.getX());
                    sb.append(",");
                    sb.append(dp.getY());
                } else {
                    sb.append(",0,0");
                }

                if (iterator3.hasNext()) {
                    dp = iterator3.next();
                    sb.append(",");
                    sb.append(dp.getX());
                    sb.append(",");
                    sb.append(dp.getY());
                    sb.append("\r\n");
                } else {
                    sb.append(",0,0");
                    sb.append("\r\n");
                }
            }

            File textFile = new File(getExternalFilesDir(null), getString(R.string.file_name));
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(textFile);
                fileOutputStream.write(sb.toString().getBytes());
                fileOutputStream.close();

                Toast.makeText(this, getString(R.string.saved) + getExternalFilesDir(null)
                        + "/" + getString(R.string.file_name), Toast.LENGTH_LONG).show();
            } catch (FileNotFoundException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                Toast.makeText(this, "" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, getString(R.string.ext_storage_err), Toast.LENGTH_LONG).show();
        }
    }

    private void loadButtonMethod() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            loadFromFile();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 2);
        }
    }

    public void loadFromFile() {

        graphView.removeAllSeries();

        if (pointed) {
            pDataSeries1.resetData(new DataPoint[]{});
            pDataSeries2.resetData(new DataPoint[]{});
            pDataSeries3.resetData(new DataPoint[]{});
        } else {
            lDataSeries1.resetData(new DataPoint[]{});
            lDataSeries2.resetData(new DataPoint[]{});
            lDataSeries3.resetData(new DataPoint[]{});
        }

        try {
            File textFile = new File(getExternalFilesDir(null), getString(R.string.file_name));
            InputStream inputStream = new FileInputStream(textFile);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String receiveString;
            AtomicBoolean firstRow;
            firstRow = new AtomicBoolean(true);

            while ((receiveString = bufferedReader.readLine()) != null) {
                if (firstRow.get()) {
                    firstRow.set(false);
                } else {
                    dataFromFile(receiveString);
                }
            }

            inputStream.close();

        } catch (FileNotFoundException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "" + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        graphView.getViewport().setMinX(0);
        graphView.getViewport().setMaxX(10);
        channels = 3;
        checkChannels();
    }

    private void dataFromFile(String line) {
        String[] dataArray = line.split(",");

        if (pointed) {
            pDataSeries1.appendData(new DataPoint(Double.parseDouble(dataArray[0]),
                    Double.parseDouble(dataArray[1])), true, 20000);
            pDataSeries2.appendData(new DataPoint(Double.parseDouble(dataArray[2]),
                    Double.parseDouble(dataArray[3])), true, 20000);
            pDataSeries3.appendData(new DataPoint(Double.parseDouble(dataArray[4]),
                    Double.parseDouble(dataArray[5])), true, 20000);
        } else {
            lDataSeries1.appendData(new DataPoint(Double.parseDouble(dataArray[0]),
                    Double.parseDouble(dataArray[1])), true, 20000);
            lDataSeries2.appendData(new DataPoint(Double.parseDouble(dataArray[2]),
                    Double.parseDouble(dataArray[3])), true, 20000);
            lDataSeries3.appendData(new DataPoint(Double.parseDouble(dataArray[4]),
                    Double.parseDouble(dataArray[5])), true, 20000);
        }
    }

    private void onDataSeriesChange() {
        if (pointed) {

            pDataSeries1.resetData(new DataPoint[]{});
            pDataSeries2.resetData(new DataPoint[]{});
            pDataSeries3.resetData(new DataPoint[]{});

            Iterator<DataPoint> iterator1 = lDataSeries1.getValues(lDataSeries1.getLowestValueX(), lDataSeries1.getHighestValueX());
            while (iterator1.hasNext()) {
                pDataSeries1.appendData(iterator1.next(), false, 20000);
            }
            Iterator<DataPoint> iterator2 = lDataSeries2.getValues(lDataSeries2.getLowestValueX(), lDataSeries2.getHighestValueX());
            while (iterator2.hasNext()) {
                pDataSeries2.appendData(iterator2.next(), false, 20000);
            }
            Iterator<DataPoint> iterator3 = lDataSeries3.getValues(lDataSeries3.getLowestValueX(), lDataSeries3.getHighestValueX());
            while (iterator3.hasNext()) {
                pDataSeries3.appendData(iterator3.next(), false, 20000);
            }

            graphView.removeAllSeries();

            lDataSeries1.resetData(new DataPoint[]{});
            lDataSeries2.resetData(new DataPoint[]{});
            lDataSeries3.resetData(new DataPoint[]{});

            checkChannels();
        } else {

            lDataSeries1.resetData(new DataPoint[]{});
            lDataSeries2.resetData(new DataPoint[]{});
            lDataSeries3.resetData(new DataPoint[]{});

            Iterator<DataPoint> iterator1 = pDataSeries1.getValues(pDataSeries1.getLowestValueX(), pDataSeries1.getHighestValueX());
            while (iterator1.hasNext()) {
                lDataSeries1.appendData(iterator1.next(), false, 20000);
            }
            Iterator<DataPoint> iterator2 = pDataSeries2.getValues(pDataSeries2.getLowestValueX(), pDataSeries2.getHighestValueX());
            while (iterator2.hasNext()) {
                lDataSeries2.appendData(iterator2.next(), false, 20000);
            }
            Iterator<DataPoint> iterator3 = pDataSeries3.getValues(pDataSeries3.getLowestValueX(), pDataSeries3.getHighestValueX());
            while (iterator3.hasNext()) {
                lDataSeries3.appendData(iterator3.next(), false, 20000);
            }

            graphView.removeAllSeries();

            pDataSeries1.resetData(new DataPoint[]{});
            pDataSeries2.resetData(new DataPoint[]{});
            pDataSeries3.resetData(new DataPoint[]{});

            checkChannels();
        }
    }

    private void initDataSeries() {
        setOnTapListener(pDataSeries1);
        pDataSeries1.setSize(5);
        pDataSeries1.setColor(Color.BLUE);

        setOnTapListener(pDataSeries2);
        pDataSeries2.setSize(5);
        pDataSeries2.setColor(Color.RED);

        setOnTapListener(pDataSeries3);
        pDataSeries3.setSize(5);
        pDataSeries3.setColor(Color.GREEN);

        setOnTapListener(lDataSeries1);
        lDataSeries1.setColor(Color.BLUE);

        setOnTapListener(lDataSeries2);
        lDataSeries2.setColor(Color.RED);

        setOnTapListener(lDataSeries3);
        lDataSeries3.setColor(Color.GREEN);
    }

    private void setOnTapListener(BaseSeries baseSeries) {
        baseSeries.setOnDataPointTapListener(new OnDataPointTapListener() {
            @Override
            public void onTap(Series series, DataPointInterface dataPoint) {
                Toast.makeText(getApplicationContext(), getString(R.string.data)
                        + dataPoint, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void channel1Handler() {
        if (pointed) {
            if (channel1.isChecked()) {
                graphView.addSeries(pDataSeries1);
            } else {
                graphView.removeSeries(pDataSeries1);
            }
        } else {
            if (channel1.isChecked()) {
                graphView.addSeries(lDataSeries1);
            } else {
                graphView.removeSeries(lDataSeries1);
            }
        }
    }

    private void channel2Handler() {
        if (pointed) {
            if (channel2.isChecked()) {
                graphView.addSeries(pDataSeries2);
            } else {
                graphView.removeSeries(pDataSeries2);
            }
        } else {
            if (channel2.isChecked()) {
                graphView.addSeries(lDataSeries2);
            } else {
                graphView.removeSeries(lDataSeries2);
            }
        }
    }

    private void channel3Handler() {
        if (pointed) {
            if (channel3.isChecked()) {
                graphView.addSeries(pDataSeries3);
            } else {
                graphView.removeSeries(pDataSeries3);
            }
        } else {
            if (channel3.isChecked()) {
                graphView.addSeries(lDataSeries3);
            } else {
                graphView.removeSeries(lDataSeries3);
            }
        }
    }

    private void startUsbConnection() {
        Map<String, UsbDevice> connectedDevices = usbManager.getDeviceList();
        if (!connectedDevices.isEmpty()) {
            for (UsbDevice device : connectedDevices.values()) {
                Toast.makeText(getApplicationContext(), getString(R.string.device_found)
                        + device.getDeviceName(), Toast.LENGTH_LONG).show();
                myDevice = device;
                return;
            }
        }
        Toast.makeText(getApplicationContext(), getString(R.string.usb_connection_error),
                Toast.LENGTH_LONG).show();
    }

    private void startSerialConnection(UsbDevice device) {
        Toast.makeText(getApplicationContext(), getString(R.string.usb_ready),
                Toast.LENGTH_SHORT).show();
        connection = usbManager.openDevice(device);
        serialDevice = UsbSerialDevice.createUsbSerialDevice(device, connection);
        if (serialDevice != null) {
            if (serialDevice.open()) {
                serialDevice.setBaudRate(selectedBaud);
                serialDevice.setDataBits(UsbSerialInterface.DATA_BITS_8);
                serialDevice.setStopBits(UsbSerialInterface.STOP_BITS_1);
                serialDevice.setParity(UsbSerialInterface.PARITY_NONE);
                serialDevice.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                serialDevice.read(callback);
                Toast.makeText(getApplicationContext(), getString(R.string.serial_opened),
                        Toast.LENGTH_SHORT).show();
                connected = true;
            } else {
                Toast.makeText(getApplicationContext(), getString(R.string.serial_error),
                        Toast.LENGTH_SHORT).show();
                connected = false;
            }
        } else {
            Toast.makeText(getApplicationContext(), getString(R.string.usb_serial_error),
                    Toast.LENGTH_LONG).show();
            connected = false;
        }
    }

    private void onSerialDataReceived(String data) {
        if (monitoring) {
            String[] dataS = data.split(" ");
            channels = dataS.length;

            if (pointed) {
                if (channels == 1) {
                    pDataSeries1.appendData(new DataPoint(dataX,
                            Double.valueOf(dataS[0])), true, 20000);
                } else if (channels == 2) {
                    pDataSeries1.appendData(new DataPoint(dataX,
                            Double.valueOf(dataS[0])), true, 20000);
                    pDataSeries2.appendData(new DataPoint(dataX,
                            Double.valueOf(dataS[1])), true, 20000);
                } else if (channels == 3) {
                    pDataSeries1.appendData(new DataPoint(dataX,
                            Double.valueOf(dataS[0])), true, 20000);
                    pDataSeries2.appendData(new DataPoint(dataX,
                            Double.valueOf(dataS[1])), true, 20000);
                    pDataSeries3.appendData(new DataPoint(dataX,
                            Double.valueOf(dataS[2])), true, 20000);
                }
            } else {
                if (channels == 1) {
                    lDataSeries1.appendData(new DataPoint(dataX,
                            Double.valueOf(dataS[0])), true, 20000);
                } else if (channels == 2) {
                    lDataSeries1.appendData(new DataPoint(dataX,
                            Double.valueOf(dataS[0])), true, 20000);
                    lDataSeries2.appendData(new DataPoint(dataX,
                            Double.valueOf(dataS[1])), true, 20000);
                } else if (channels == 3) {
                    lDataSeries1.appendData(new DataPoint(dataX,
                            Double.valueOf(dataS[0])), true, 20000);
                    lDataSeries2.appendData(new DataPoint(dataX,
                            Double.valueOf(dataS[1])), true, 20000);
                    lDataSeries3.appendData(new DataPoint(dataX,
                            Double.valueOf(dataS[2])), true, 20000);
                }
            }
            dataX += 1 / frequency;
        }
    }

    private void stopUsbConnection() {
        try {
            if (serialDevice != null) {
                serialDevice.close();
            }

            if (connection != null) {
                connection.close();
            }
        } finally {
            serialDevice = null;
            connection = null;
        }
        connected = false;
    }
}