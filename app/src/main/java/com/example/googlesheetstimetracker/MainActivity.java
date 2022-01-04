package com.example.googlesheetstimetracker;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.material.snackbar.Snackbar;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private static File CREDENTIALS_FILE;
    private static String CREDENTIALS_FILE_PATH;
    private static Sheets service;

    // From https://developers.google.com/sheets/api/quickstart/java
    private static final String APPLICATION_NAME = "Google Sheets Time Tracker";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private final int REQUEST_CODE = 1;

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);


    private GoogleAccountCredential mCredential;
    private InternetDetector internetDetector;
    private String currSpreadsheetId;

    Button clockInBtn;
    Button clockOutBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
        initAuth();
        chooseAccount();

        // NOTE: This is the TEST NGYA spreadsheet
        currSpreadsheetId = "1_mXaaGUHur5d13X5NrK6U3fZcfEzVojH4ISCvtcTOcU";

        TextView tempTextView = (TextView) findViewById(R.id.tempTextView);

        findViewById(R.id.changeAccountBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check / request permissions if necessary
                if (Utils.checkPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    startActivityForResult(mCredential.newChooseAccountIntent(), Utils.REQUEST_ACCOUNT_PICKER);
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE);
                }
            }
        });

        clockInBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getResultsFromApi(v, true);
            }
        });
        clockOutBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getResultsFromApi(v, false);
            }
        });
    }

    private void init() {
        // Initializing Internet Checker
        internetDetector = new InternetDetector(getApplicationContext());

        clockInBtn = (Button) findViewById(R.id.clockInBtn);
        clockOutBtn = (Button) findViewById(R.id.clockOutBtn);
    }

    private void initAuth() {
        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), SCOPES)
                .setBackOff(new ExponentialBackOff());
        service = getSheetsService();
    }

    private void getResultsFromApi(View view, boolean clockIn) {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        }
        else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        }
        else if (!internetDetector.checkMobileInternetConn()) {
            showMessage(view, "No network connection available.");
        }
        /* else if (!Utils.isNotEmpty(edtToAddress)) {
            showMessage(view, "To address Required");
        } else if (!Utils.isNotEmpty(edtSubject)) {
            showMessage(view, "Subject Required");
        } else if (!Utils.isNotEmpty(edtMessage)) {
            showMessage(view, "Message Required");
        } */
        else {
            new UpdateSheet(clockIn, currSpreadsheetId).execute();
        }
    }

    // Method for Checking Google Play Service is Available
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    // Method to Show Info, If Google Play Service is Not Available.
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }

    // Method for Google Play Services Error Info
    void showGooglePlayServicesAvailabilityErrorDialog(final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                Utils.REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    // Storing Mail ID using Shared Preferences
    private void chooseAccount() {
        if (Utils.checkPermission(getApplicationContext(), Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE).getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(mCredential.newChooseAccountIntent(), Utils.REQUEST_ACCOUNT_PICKER);
            }
        } else {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.GET_ACCOUNTS}, Utils.REQUEST_PERMISSION_GET_ACCOUNTS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case Utils.REQUEST_PERMISSION_GET_ACCOUNTS:
                chooseAccount();
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case Utils.REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    showMessage(clockInBtn, "This app requires Google Play Services. Please install " +
                            "Google Play Services on your device and relaunch this app.");
                } else {
                    getResultsFromApi(clockInBtn, true);
                }
                break;
            case Utils.REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                    }
                }
                break;
        }
    }

    private void showMessage(View view, String message) {
        Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
    }

    private Sheets getSheetsService() {
        HttpTransport transport = new NetHttpTransport();
        Sheets service = new Sheets.Builder(transport, JSON_FACTORY, mCredential)
                .setApplicationName(APPLICATION_NAME)
                .build();

        return service;
    }


    private class UpdateSheet extends AsyncTask<Void, Void, Void> {
        // True if user wants to clock in, false if user wants to clock out
        private boolean clockIn;
        private String spreadsheetId;

        UpdateSheet(boolean clockIn, String spreadsheetId) {
            this.clockIn = clockIn;
            this.spreadsheetId = spreadsheetId;
        }


        @Override
        protected Void doInBackground(Void... params) {
            LocalDateTime now = LocalDateTime.now();
            if (clockIn) {
                // Before 5AM counts as the previous day
                if (now.getHour() <= 5) {
                    now = now.minusDays(1);
                }
                clockIn(now);
            }
            else {
                clockOut(now);
            }

            return null;
        }

        private void clockIn(LocalDateTime now) {
            String lastYear = getLastDataInColumn("A");
            // Same year
            if (Integer.parseInt(lastYear) == now.getYear()) {
                String lastMonth = getLastDataInColumn("B");
                // Same month
                if (lastMonth.equalsIgnoreCase(now.getMonth().toString())) {
                    int lastDayIndex = getLastRowInColumn("C");
                    String lastDay = getDataInCell("C", lastDayIndex);
                    int insertPos = lastDayIndex;
                    // Continues to try to search upwards until it finds a valid day.
                    // Can result in large number of API calls (though it shouldn't be an issue for normal use cases)
                    while (lastDay == null && lastDayIndex > 1) {
                        lastDayIndex--;
                        lastDay = getDataInCell("C", lastDayIndex);
                    }
                    // Same day
                    if (Integer.parseInt(lastDay) == now.getDayOfMonth()) {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");
                        String currTime = now.format(formatter);
                        appendRow("E" + (insertPos + 1), Arrays.asList(currTime), "INSERT_ROWS");
                    }
                }
            }
        }

        private void clockOut(LocalDateTime now) {
            // No need to check date, we can assume that the date exists already

            // Get current time as string
            int lastDayIndex = getLastRowInColumn("C");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");
            String currTime = now.format(formatter);

            // Get time difference between start and stop
            String startTime = getLastDataInColumn("E");
            System.out.println(startTime);
            LocalTime time = LocalTime.parse(startTime, formatter);
            LocalDateTime startDateTime = LocalDateTime.of(now.getYear(), now.getMonth(), now.getDayOfMonth(), time.getHour(), time.getMinute());
            long minutesBetween = ChronoUnit.MINUTES.between(startDateTime, now);

            appendRow("F" + (lastDayIndex), Arrays.asList(currTime, minutesBetween), "OVERWRITE");

            // TODO: Let user input notes here
        }

        @Override
        protected void onPostExecute(Void result) {
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected void onProgressUpdate(Void... values) {
        }

        // Appends the given row to the end of the given range
        private AppendValuesResponse appendRow(String range, List<Object> row, String insertDataOption) {
            String valueInputOption = "USER_ENTERED";
            List<List<Object>> values = Arrays.asList(row);
            ValueRange requestBody = new ValueRange();
            requestBody.setValues(values);

            AppendValuesResponse response = null;
            try {
                Sheets.Spreadsheets.Values.Append request = service.spreadsheets().values().append(spreadsheetId, range, requestBody);
                request.setValueInputOption(valueInputOption);
                request.setInsertDataOption(insertDataOption);
                response = request.execute();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (response == null) {
                showMessage(null, "API call failed, try again in a bit?");
            }
            return response;
        }

        // Given a column name (i.e. A, B, C, etc.) return the row number of the last non-blank entry in that column
        // Returns -1 if an error occurs
        // It does this by appending an empty row to the sheet and then analyzing the response
        private int getLastRowInColumn(String columnName) {
            AppendValuesResponse response = appendRow(columnName + "3:" + columnName, Arrays.asList(new String[]{}), "INSERT_ROWS");

            String updatedRange = response.getUpdates().getUpdatedRange();
            Pattern p = Pattern.compile("^.*![A-Z]+(\\d+)$");
            Matcher m = p.matcher(updatedRange);
            if (m.matches()) {
                int lastRow = Integer.parseInt(m.group(1)) - 1;
                return lastRow;
            }
            else {
                showMessage(clockInBtn, "ERROR: Regex failed to match");
                return -1;
            }
        }

        // Given a column name (i.e. A, B, C, etc.) return the data in the last non-blank entry in that column
        // Returns null if an error occurs
        private String getLastDataInColumn(String columnName) {
            int lastIndex = getLastRowInColumn(columnName);

            if (lastIndex == -1) {
                return null;
            }

            return getDataInCell(columnName, lastIndex);
        }

        private String getDataInCell(String columnName, int rowIndex) {
            ValueRange result = null;
            try {
                result = service.spreadsheets().values().get(spreadsheetId, columnName + rowIndex).execute();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (result == null) {
                showMessage(null, "API call failed, try again in a bit?");
            }

            List<List<Object>> values = result.getValues();
            if (values == null)
                return null;
            return result.getValues().get(0).get(0).toString();
        }
    }
}
