package com.ershovdev.ideawrite;

import android.Manifest;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, EasyPermissions.PermissionCallbacks{
    private ViewPager slider;
    private LinearLayout dots;

    private Button next;
    private Button prev;
    private Button send;
    private int currentPage;
    private Sheets sheets_service;
    private Drive drive_service;
    private String spreadsheet_id;
    final String range = "A2:G";
    private ProgressBar bar;

    private static final String PREF_ACCOUNT_NAME = "accountName";

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    private static final SimpleDateFormat sdf = new SimpleDateFormat("dd.MM", Locale.US);

    private static final String APPLICATION_NAME = "IdeaWrite";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String[] SCOPES = {
            SheetsScopes.SPREADSHEETS,
            DriveScopes.DRIVE
    };

    public GoogleAccountCredential credential;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        slider = findViewById(R.id.slideViewPager);
        slider.setOffscreenPageLimit(5);
        dots = findViewById(R.id.dots);

        next = findViewById(R.id.next_btn);
        prev = findViewById(R.id.prev_btn);
        send = findViewById(R.id.ready_button);

        bar = findViewById(R.id.progressBar);
        bar.setVisibility(ProgressBar.INVISIBLE);

        SliderAdapter sliderAdapter = new SliderAdapter(this);
        slider.setAdapter(sliderAdapter);

        next.setOnClickListener(this);
        prev.setOnClickListener(this);
        send.setOnClickListener(this);

        addDotsIndicator(0);

        slider.addOnPageChangeListener(viewListener);
        get_credential();
    }
    public void addDotsIndicator(int position) {
        TextView[] mDots = new TextView[5];
        dots.removeAllViews();

        for(int i = 0; i < mDots.length; i++) {
            mDots[i] = new TextView(this);
            mDots[i].setText(Html.fromHtml("&#8226"));
            mDots[i].setTextSize(25);
            mDots[i].setTextColor(Color.parseColor("#cccccc"));

            dots.addView(mDots[i]);
        }

        if (mDots.length > 0) {
            mDots[position].setTextColor(Color.parseColor("#aaaaaa"));
        }
    }
    ViewPager.OnPageChangeListener viewListener = new ViewPager.OnPageChangeListener() {
        @Override
        public void onPageScrolled(int i, float v, int i1) {
        }

        @Override
        public void onPageSelected(int i) {
            addDotsIndicator(i);

            currentPage = i;

            if (i == 0) {
                next.setEnabled(true);
                prev.setEnabled(false);
                prev.setVisibility(View.INVISIBLE);

            } else if (i == 4) {
                next.setEnabled(false);
                prev.setEnabled(true);
                next.setVisibility(View.INVISIBLE);
            } else {
                next.setEnabled(true);
                prev.setEnabled(true);
                prev.setVisibility(View.VISIBLE);
                next.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onPageScrollStateChanged(int i) {

        }
    };
    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.next_btn:
                slider.setCurrentItem(currentPage + 1);
                break;
            case R.id.prev_btn:
                slider.setCurrentItem(currentPage - 1);
                break;
            case R.id.ready_button:
                try {
                    loading(1);
                    getResultsFromApi();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            default:
                break;
        }
    }
    private void getResultsFromApi() throws IOException {
        if (!isGooglePlayServicesAvailable()) acquireGooglePlayServices();
        else if (credential.getSelectedAccountName() == null) chooseAccount();
        else if (!isDeviceOnline()) Toast.makeText(this, "Интернета нету :(", Toast.LENGTH_SHORT).show();
        else make_request();
    }
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() throws IOException {
        if (EasyPermissions.hasPermissions(this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE).getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                credential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
            }
        } else {
            EasyPermissions.requestPermissions(this, "Нужен доступ к Google-аккаунту", REQUEST_PERMISSION_GET_ACCOUNTS, Manifest.permission.GET_ACCOUNTS);
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data){
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) Toast.makeText(this, "Установи Google Play Services", Toast.LENGTH_SHORT).show();
                else {
                    try {
                        getResultsFromApi();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        getPreferences(Context.MODE_PRIVATE).edit().putString(PREF_ACCOUNT_NAME, accountName).apply();
                        credential.setSelectedAccountName(accountName);
                        try {
                            getResultsFromApi();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                break;
            case 1:
                try {
                    getResultsFromApi();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
    }

    public void get_credential() {
        credential = GoogleAccountCredential.usingOAuth2(this, Arrays.asList(SCOPES)).setBackOff(new ExponentialBackOff());
    }
    private String get_text_from_field(Integer a) {
        return ((EditText)slider.getChildAt(a).findViewById(R.id.text_field)).getText().toString();
    }
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }
    void showGooglePlayServicesAvailabilityErrorDialog(final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }
    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {

    }
    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {

    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }
    private List<List<Object>> get_body() {
        ArrayList<List<Object>> values = new ArrayList<>();
        ArrayList<Object> listInner = new ArrayList<>();


        listInner.add(sdf.format(new Timestamp(System.currentTimeMillis())));
        listInner.add(sdf.format(new Timestamp(System.currentTimeMillis() + 1209600000))); //14 days in milliseconds

        for (int i = 0; i < 4; i++) {
            listInner.add(get_text_from_field(i));
        }

        listInner.add(((EditText)((LinearLayout)slider.getChildAt(4)).getChildAt(2)).getText().toString());
        listInner.add(((EditText)((LinearLayout)slider.getChildAt(4)).getChildAt(4)).getText().toString());

        values.add(listInner);
        return values;
    }

    private void clear_inputs() {
        for (int i = 0; i < 4; i++) {
            ((EditText)slider.getChildAt(i).findViewById(R.id.text_field)).setText("");
        }

        ((EditText)((LinearLayout)slider.getChildAt(4)).getChildAt(2)).setText("");
        ((EditText)((LinearLayout)slider.getChildAt(4)).getChildAt(4)).setText("");
    }

    private void loading(int state) {
        if (state == 1) {
            next.setEnabled(false);
            prev.setEnabled(false);
            send.setEnabled(false);

            next.setAlpha(0.25f);
            prev.setAlpha(0.25f);
            send.setAlpha(0.25f);

            bar.setVisibility(ProgressBar.VISIBLE);
        }

        if (state == 0) {
            next.setEnabled(true);
            prev.setEnabled(true);
            send.setEnabled(true);

            next.setAlpha(1);
            prev.setAlpha(1);
            send.setAlpha(1);

            bar.setVisibility(ProgressBar.INVISIBLE);
        }
    }

    private void make_request() throws IOException {
        final NetHttpTransport HTTP_TRANSPORT = new NetHttpTransport();

        sheets_service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
        drive_service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();

        Drive.Files.List files = drive_service.files().list().setQ("name='Таблица для идей' and trashed=false");
        (new DriveRequest()).execute(files);
    }

    @SuppressLint("StaticFieldLeak")
    private class AsyncRequest extends AsyncTask<Sheets.Spreadsheets.Values.Append, Void, AppendValuesResponse> {
        private Exception mLastError = null;

        @Override
        protected AppendValuesResponse doInBackground(Sheets.Spreadsheets.Values.Append... objects) {
            try {
                return objects[0].execute();
            } catch (IOException e) {
                e.printStackTrace();
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        @Override
        protected void onPostExecute(AppendValuesResponse o) {
            super.onPostExecute(o);
            Toast.makeText(MainActivity.this, "Успешно добавлено", Toast.LENGTH_SHORT).show();
            clear_inputs();
            loading(0);
        }

        @Override
        protected void onCancelled() {
            if (mLastError != null) {
                if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(((UserRecoverableAuthIOException) mLastError).getIntent(), 1);
                }
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class TableRequest extends AsyncTask<Sheets.Spreadsheets.Create, Void, Spreadsheet> {
        @Override
        protected Spreadsheet doInBackground(Sheets.Spreadsheets.Create... objects) {
            try {
                return objects[0].execute();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(Spreadsheet o) {
            super.onPostExecute(o);
            spreadsheet_id = o.getSpreadsheetId();
            Toast.makeText(MainActivity.this, "Таблицы для идей не было найдено, но я создал новую. Еще раз жмякни, будет норм все", Toast.LENGTH_LONG).show();
            loading(0);
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class DriveRequest extends AsyncTask<Drive.Files.List, Void, FileList> {
        private Exception mLastError = null;

        @Override
        protected FileList doInBackground(Drive.Files.List... objects) {
            try {
                return objects[0].execute();
            } catch (IOException e) {
                e.printStackTrace();
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        @Override
        protected void onPostExecute(FileList o) {
            super.onPostExecute(o);
            int files_num = o.getFiles().size();

            ValueRange body = new ValueRange().setValues(get_body());

            if (files_num == 1) {
                spreadsheet_id = o.getFiles().get(0).getId();
                Sheets.Spreadsheets.Values.Append response = null;
                try {
                    response = sheets_service.spreadsheets().values().append(spreadsheet_id, range, body).setValueInputOption("RAW");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                new AsyncRequest().execute(response);
            } else if (files_num == 0) {
                Spreadsheet requestBody = new Spreadsheet().setProperties(new SpreadsheetProperties().setTitle("Таблица для идей"));
                Sheets.Spreadsheets.Create spreadsheet_create = null;

                try {
                    spreadsheet_create = sheets_service.spreadsheets().create(requestBody);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                new TableRequest().execute(spreadsheet_create);
            } else {
                Toast.makeText(MainActivity.this, "Я нашел больше, чем одну таблицу для идей. Удали лишние (пустые) и возвращайся", Toast.LENGTH_SHORT).show();
                loading(0);
            }
        }

        @Override
        protected void onCancelled() {
            if (mLastError != null) {
                if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(((UserRecoverableAuthIOException) mLastError).getIntent(), 1);
                }
            }
        }
    }
}
