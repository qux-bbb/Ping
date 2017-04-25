package com.neo.ping;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.media.TransportMediator;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {
    ArrayList<String> IPs = new ArrayList();
    Button btnStart;
    Context mContext;
    MyTimerTask myTimerTask;
    boolean pingError = false;
    SharedPreferences prefs;
    boolean running = false;
    ScrollView scrollViewMain;
    ArrayAdapter<String> spinnerAdapter;
    Spinner spinnerHosts;
    Timer timer;
    EditText txtHost;
    TextView txtResult;

    class MyTimerTask extends TimerTask {
        MyTimerTask() {
        }

        public void run() {
            final String r = MainActivity.this.ping(MainActivity.this.txtHost.getText().toString());
            MainActivity.this.runOnUiThread(new Runnable() {
                public void run() {
                    MainActivity.this.txtResult.setText(MainActivity.this.txtResult.getText() + r);
                    if (MainActivity.this.pingError) {
                        MainActivity.this.stopTimer();
                    }
                    MainActivity.this.scrollViewMain.post(new Runnable() {
                        public void run() {
                            MainActivity.this.scrollViewMain.fullScroll(TransportMediator.KEYCODE_MEDIA_RECORD);
                        }
                    });
                }
            });
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.mContext = this;
        this.prefs = getSharedPreferences(getPackageName(), 0);
        this.spinnerHosts = (Spinner) findViewById(R.id.spinnerHosts);
        this.spinnerHosts.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> adapterView, View arg1, int arg2, long arg3) {
                MainActivity.this.txtHost.setText(MainActivity.this.spinnerHosts.getSelectedItem().toString());
            }

            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        this.txtHost = (EditText) findViewById(R.id.editTextHost);
        this.btnStart = (Button) findViewById(R.id.btnStart);
        this.txtResult = (TextView) findViewById(R.id.txtResult);
        this.scrollViewMain = (ScrollView) findViewById(R.id.scrollViewMain);
        this.btnStart.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                if (MainActivity.this.running) {
                    MainActivity.this.stopTimer();
                } else if (MainActivity.this.txtHost.getText().toString().equals("")) {
                    MainActivity.this.txtHost.setError("This field cannot be empty.");
                } else {
                    MainActivity.this.txtResult.setText("");
                    MainActivity.this.running = true;
                    MainActivity.this.timer = new Timer();
                    MainActivity.this.myTimerTask = new MyTimerTask();
                    MainActivity.this.timer.schedule(MainActivity.this.myTimerTask, 1000, 1000);
                    ((Button) view).setText(MainActivity.this.mContext.getResources().getString(R.string.stop));
                    String host = MainActivity.this.txtHost.getText().toString();
                    MainActivity.this.prefs.edit().putString("host", host).commit();
                    String hosts = "";
                    if (MainActivity.this.IPs.contains(host)) {
                        MainActivity.this.IPs.remove(host);
                    }
                    MainActivity.this.IPs.add(0, host);
                    MainActivity.this.spinnerAdapter = new ArrayAdapter(MainActivity.this.mContext, R.layout.support_simple_spinner_dropdown_item, (String[]) MainActivity.this.IPs.toArray(new String[MainActivity.this.IPs.size()]));
                    MainActivity.this.spinnerHosts.setAdapter(MainActivity.this.spinnerAdapter);
                    MainActivity.this.spinnerAdapter.notifyDataSetChanged();
                    int i = 0;
                    while (i < MainActivity.this.IPs.size() && i < 10) {
                        hosts = new StringBuilder(String.valueOf(hosts)).append(",").append((String) MainActivity.this.IPs.get(i)).toString();
                        i++;
                    }
                    if (hosts != "") {
                        hosts = hosts.substring(1);
                    }
                    MainActivity.this.prefs.edit().putString("hosts", hosts).commit();
                }
            }
        });
        setDefaults();
    }

    public void setDefaults() {
        String hosts = "127.0.0.1,192.168.1.1,192.168.0.1,8.8.8.8,8.8.4.4,4.2.2.4,www.baidu.com";
        for (String x : hosts.split(",")) {
            this.IPs.add(x);
        }
        this.spinnerAdapter = new ArrayAdapter(this, R.layout.support_simple_spinner_dropdown_item, (String[]) this.IPs.toArray(new String[this.IPs.size()]));
        this.spinnerHosts.setAdapter(this.spinnerAdapter);
    }

    public void stopTimer() {
        this.running = false;
        this.timer.cancel();
        this.btnStart.setText(this.mContext.getResources().getString(R.string.start));
        this.prefs.edit().putString("host", this.txtHost.getText().toString()).commit();
    }

    public String ping(String host) {
        try {
            Process p = executeCmd("ping -c 1 -w 5 " + host, false);
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            int mExitValue = p.waitFor();
            this.pingError = false;
            String output = "";
            String s;
            if (mExitValue != 0) {
                if (mExitValue != 1) {
                    this.pingError = true;
                    while (true) {
                        s = stdError.readLine();
                        if (s == null) {
                            break;
                        }
                        output = new StringBuilder(String.valueOf(output)).append(s).append("\n").toString();
                    }
                } else {
                    output = new StringBuilder(String.valueOf(output)).append("Request timed out\n").toString();
                }
            } else {
                int i = 0;
                while (true) {
                    s = stdInput.readLine();
                    if (s == null) {
                        break;
                    } else if (i == 1) {
                        break;
                    } else {
                        i++;
                    }
                }
                output = new StringBuilder(String.valueOf(output)).append(s).append("\n").toString();
            }
            p.destroy();
            return output;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static Process executeCmd(String cmd, boolean sudo) throws IOException {
        if (sudo) {
            return Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
        }
        try {
            return Runtime.getRuntime().exec(cmd);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}