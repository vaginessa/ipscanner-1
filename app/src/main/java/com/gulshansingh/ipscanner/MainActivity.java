package com.gulshansingh.ipscanner;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;

import org.apache.http.conn.util.InetAddressUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends FragmentActivity {

    private static final String NMAP_DIR = "nmap";
    private static final String NMAP_VERSION = "5.61TEST4";
    private static final String INSTALL_DIALOG_TAG = "install_dialog";
    private static final String RUN_DIALOG_TAG = "run_dialog";
    private static boolean installing = false;

    private static final Pattern ipPattern = Pattern.compile("([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])");

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TabHost tabHost = (TabHost) findViewById(R.id.tabhost);
        tabHost.setup();

        TabSpec basicSpec = tabHost
            .newTabSpec("Basic")
            .setIndicator("Basic")
            .setContent(R.id.basic_tab);
        TabSpec advancedSpec = tabHost
            .newTabSpec("Advanced")
            .setIndicator("Advanced")
            .setContent(R.id.advanced_tab);

        tabHost.addTab(basicSpec);
        tabHost.addTab(advancedSpec);

        TextView command = (TextView) findViewById(R.id.command);
        TextView results = (TextView) findViewById(R.id.results);

        if (Build.VERSION.SDK_INT >= 11) {
            command.setTextIsSelectable(true);
            results.setTextIsSelectable(true);
        }

        initNmap();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_man_nmap:
            Intent intent = new Intent(this, ManActivity.class);
            startActivity(intent);
            break;
        case R.id.action_show_ip: {
            String ipAddress = getIpAddress();
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            String message;
            if (ipAddress != null) {
                message = "Your IP address is " + ipAddress;
            } else {
                message = "Could not get IP address. Please check if you have a WiFi or data connection.";
            }
            b.setTitle("IP Address").setMessage(message).create().show();
            break;
        }
        case R.id.action_about:
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            String message = "";
            message += "IPScanner v0.1 by Gulshan Singh\n\n";
            message += "This is an open source application that uses an ARM port of Nmap.\n\n";
            message += "Issues should be reported on the Github page: https://github.com/gsingh93/ipscanner/issues\n\n";
            message += "If there are any options you would like to see added to basic mode, let me know and if there are enough requests, I will add it. Pull requests are welcome.";
            SpannableString s = new SpannableString(message);
            Linkify.addLinks(s, Linkify.ALL);
            AlertDialog d = b.setTitle("About").setMessage(s).create();
            d.show();
            ((TextView) d.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
            break;
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ArgumentToggleButton.resetArgumentGenerator();
    }

    // Need to remove the new fragment that is created on a rotate
    private void initNmap() {
        SharedPreferences sp = PreferenceManager
            .getDefaultSharedPreferences(this);
        String version = sp.getString("NMAP_VERSION", "");
        if (!installing && !version.equals(NMAP_VERSION)) {
            installing = true;
            showProgressDialog(
                               "Installing Nmap",
                               "Please wait while Nmap is installed. This should only happen once.",
                               INSTALL_DIALOG_TAG);
            new Thread(new InstallNmapTask()).start();
        }
    }

    private void showProgressDialog(String title, String message, String tag) {
        ProgressDialogFragment p = ProgressDialogFragment.newInstance(title,
                                                                      message);
        p.setCancelable(false);
        p.setRetainInstance(true);
        p.show(getSupportFragmentManager(), tag);
    }

    private void dismissDialogFragment(String tag) {
        DialogFragment dialog = (DialogFragment) getSupportFragmentManager()
            .findFragmentByTag(tag);
        if (dialog != null) {
            dialog.dismissAllowingStateLoss();
        }
    }

    private class InstallNmapTask implements Runnable {
        @Override
        public void run() {
            SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(MainActivity.this);

            try {
                FileUtils.extractAssetDirectory(MainActivity.this, NMAP_DIR);
                FileUtils.chmod(getFilesDir().getCanonicalPath() + '/'
                                + NMAP_DIR, "744", true);
                boolean result = sp.edit()
                    .putString("NMAP_VERSION", NMAP_VERSION).commit();
                if (!result) {
                    Log.e("MainActivity", "Writing new version failed");
                    throw new IOException();
                }
            } catch (IOException e) {
                e.printStackTrace();

                runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            new AlertDialog.Builder(MainActivity.this)
                                .setMessage(
                                            "Nmap installation failed, please try again. If the problem continues, please contact the developer. Exiting.")
                                .setPositiveButton("Exit",
                                                   new OnClickListener() {
                                                       @Override
                                                       public void onClick(
                                                                           DialogInterface dialog,
                                                                           int which) {
                                                           finish();
                                                       }
                                                   }).create().show();
                        }
                    });
            } finally {
                dismissDialogFragment(INSTALL_DIALOG_TAG);
                installing = false;
            }
        }
    }

    public void runSudoClicked(View v) {
        run(v, true);
    }

    public void runClicked(View v) {
        run(v, false);
    }

    private void run(View v, boolean isSudo) {
        try {
            List<String> args = new ArrayList<String>();
            String internalDirPath = getFilesDir().getCanonicalPath();
            if (isSudo) {
                args.add("su");
                args.add("-c");
            }
            args.add(internalDirPath + "/nmap/bin/nmap");

            boolean advanced = v.getId() == R.id.advanced_run || v.getId() == R.id.advanced_run_sudo;
            if (!advanced) {
                EditText editText = (EditText) findViewById(R.id.hostname);
                String host = editText.getText().toString();

                List<String> argList = ArgumentToggleButton.getArguments();
                args.add(host);
                args.addAll(argList);
            } else {
                EditText editText = (EditText) findViewById(R.id.arguments);
                String arguments = editText.getText().toString();
                args.addAll(Arrays.asList(arguments.split(" ")));
            }

            showProgressDialog("Running command", "Running command: "
                               + getCommandText(args), RUN_DIALOG_TAG);
            new Thread(new RunNmap(args, advanced)).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * If host is a valid IP address, return it. If host is a valid
     * hostname, lookup it's IP address and return that. If an IP
     * address cannot be found, return host.
     */
    public String getIP(String host) {
        Matcher matcher = ipPattern.matcher(host);
        if (matcher.matches()) {
            return host;
        } else {
            try {
                InetAddress address = InetAddress.getByName(host);
                return address.getHostAddress();
            } catch (UnknownHostException e) {
                return host;
            }
        }
    }

    public class RunNmap implements Runnable {
        private List<String> mArgs;
        private boolean mAdvanced;

        public RunNmap(List<String> args, boolean advanced) {
            mArgs = args;
            mAdvanced = advanced;
        }

        private void showErrorDialog() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
                    String message = "Something went wrong. Check the command output if this was an Nmap error, otherwise if you are running with \"Run sudo\" make sure your device is properly rooted.";
                    b.setTitle("Error").setMessage(message).create().show();
                }
            });
        }

        @Override
        public void run() {
            int hostIndex = 1;
            if (mArgs.get(0).equals("su")) {
                hostIndex = 3;
            }
            String host = mArgs.get(hostIndex);
            mArgs.set(hostIndex, getIP(host));
            try {
                ProcessBuilder pb = new ProcessBuilder(mArgs);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                final StringBuilder sb = new StringBuilder();
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView command, results;
                        if (mAdvanced) {
                            command = (TextView) findViewById(R.id.command_advanced);
                            results = (TextView) findViewById(R.id.results_advanced);
                        } else {
                            command = (TextView) findViewById(R.id.command);
                            results = (TextView) findViewById(R.id.results);
                        }
                        results.setTypeface(Typeface.MONOSPACE);
                        command.setTypeface(Typeface.MONOSPACE);
                        String resultString = sb.toString();
                        if (resultString.equals("")) {
                            resultString = "No output, an error may have occurred";
                        }
                        results.setText(resultString);

                        command.setText("$ " + getCommandText(mArgs));
                    }
                });
            } catch (IOException e) {
                showErrorDialog();
                e.printStackTrace();
            } finally {
                dismissDialogFragment(RUN_DIALOG_TAG);
            }
        }
    }

    private String getCommandText(List<String> args) {
        StringBuilder sb = new StringBuilder();
        boolean appendedNmap = false;
        for (String arg : args) {
            if (!appendedNmap) {
                if (arg.equals("su") || arg.equals("-c")) {
                    sb.append(arg);
                } else {
                    sb.append("nmap");
                    appendedNmap = true;
                }
            } else {
                sb.append(arg);
            }
            sb.append(" ");
        }

        return sb.toString();
    }

    private String getIpAddress() {
        // Fallback address
        String ipv6Addr = null;

        try {
            List<NetworkInterface> interfaces = Collections
                .list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf
                                                           .getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress().toUpperCase(
                                                                         Locale.US);
                        boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        if (isIPv4) {
                            return sAddr;
                        } else {
                            ipv6Addr = sAddr;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ipv6Addr;
    }
}
