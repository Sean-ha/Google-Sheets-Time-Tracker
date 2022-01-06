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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.material.snackbar.Snackbar;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.googlesheetstimetracker.Utils.REQUEST_AUTHORIZATION;

public class MainActivity extends AppCompatActivity {
    private static File CREDENTIALS_FILE;
    private static String CREDENTIALS_FILE_PATH;
    private static Sheets service;
    private static Drive drive;

    // From https://developers.google.com/sheets/api/quickstart/java
    private static final String APPLICATION_NAME = "Google Sheets Time Tracker";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private final int REQUEST_CODE = 1;
    private final String[] DAY_OF_WEEK_NAMES = new String[] {"Mon", "Tues", "Wed", "Thurs", "Fri", "Sat", "Sun"};
    // The time that a day officially begins(e.g. 5 means 5AM)
    private final int TIME_NEXT_DAY_START = 5;

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private final List<String> SCOPES = Arrays.asList(SheetsScopes.SPREADSHEETS, SheetsScopes.DRIVE);


    private GoogleAccountCredential mCredential;
    private InternetDetector internetDetector;
    private String testSpreadsheetId;

    private String currSpreadsheetId;

    private HashMap<String, SpreadsheetItem> spreadsheets = new HashMap<String, SpreadsheetItem>();


    Button clockInBtn;
    Button clockOutBtn;
    EditText notesField;
    Spinner spreadsheetDropdown;
    TextView spreadsheetIdText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // NOTE: This is the TEST NGYA spreadsheet
        testSpreadsheetId = "1_mXaaGUHur5d13X5NrK6U3fZcfEzVojH4ISCvtcTOcU";

        init();
        initAuth();
        chooseAccount();

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
                showClockOutScreen();
            }
        });
        clockOutBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getResultsFromApi(v, false);
                showClockInScreen();
            }
        });

        showClockInScreen();
    }

    private void showClockInScreen() {
        clockInBtn.setVisibility(View.VISIBLE);
        clockOutBtn.setVisibility(View.GONE);
        notesField.setVisibility(View.GONE);
    }

    private void showClockOutScreen() {
        clockOutBtn.setVisibility(View.VISIBLE);
        clockInBtn.setVisibility(View.GONE);
        notesField.setVisibility(View.VISIBLE);
    }

    private void init() {
        // Initializing Internet Checker
        internetDetector = new InternetDetector(getApplicationContext());

        clockInBtn = findViewById(R.id.clockInBtn);
        clockOutBtn = findViewById(R.id.clockOutBtn);
        notesField = findViewById(R.id.notesField);
        spreadsheetDropdown = findViewById(R.id.spreadsheetDropdown);
        spreadsheetIdText = findViewById(R.id.spreadsheetIdText);

        AdapterView.OnItemSelectedListener OnCatSpinnerCL = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                ((TextView) parent.getChildAt(0)).setTextSize(18);
                ((TextView) parent.getChildAt(0)).setTranslationY(21);

                String selectedTitle = parent.getItemAtPosition(pos).toString();
                currSpreadsheetId = spreadsheets.get(selectedTitle).spreadsheetId;
                spreadsheetIdText.setText("id: " + currSpreadsheetId);

                SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
                // If it contains, it means we are clocked in
                if (settings.contains(currSpreadsheetId)) {
                    showClockOutScreen();
                } else {
                    showClockInScreen();
                }
            }

            public void onNothingSelected(AdapterView<?> parent) { }
        };
        spreadsheetDropdown.setOnItemSelectedListener(OnCatSpinnerCL);

        // TODO: Load HashMap on app load, save HashMap on app close (or other places too)
    }

    private void initAuth() {
        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), SCOPES)
                .setBackOff(new ExponentialBackOff());
        service = getSheetsService();
        drive = getDriveService();

        // Makes sure proper permissions are available, and also sets up the dropdown spinner's items
        new GetPermissions(testSpreadsheetId).execute();
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

    private Drive getDriveService() {
        HttpTransport transport = new NetHttpTransport();
        Drive service = new Drive.Builder(transport, JSON_FACTORY, mCredential)
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
                if (now.getHour() < TIME_NEXT_DAY_START) {
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
            // Gets the end of the spreadsheet
            int lastDayIndex = getLastRowInColumn("C");
            String lastDay = getDataInCell("C", lastDayIndex);
            int insertPos = lastDayIndex;
            // Continues to try to search upwards until it finds a valid day.
            // Can result in large number of API calls (though it shouldn't be an issue for normal use cases)
            while (lastDay == null && lastDayIndex > 1) {
                lastDayIndex--;
                lastDay = getDataInCell("C", lastDayIndex);
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");
            String currTime = now.format(formatter);

            String lastYear = getLastDataInColumn("A");
            // Same year
            if (Integer.parseInt(lastYear) == now.getYear()) {
                String lastMonth = getLastDataInColumn("B");
                // Same month
                if (lastMonth.equalsIgnoreCase(now.getMonth().toString())) {
                    // Same day
                    if (Integer.parseInt(lastDay) == now.getDayOfMonth()) {
                        appendRow("E" + (insertPos + 1), Arrays.asList(currTime), "INSERT_ROWS");
                    }
                    // Different day
                    else {
                        newDay(insertPos + 1, now, currTime);
                    }
                }
                // Different month
                else {
                    insertPos += 2;
                    newMonth(insertPos, now);
                    newDay(insertPos + 1, now, currTime);
                }
            }
            // Different year
            else {
                // TODO: Some issue with new year (reproduce: try inserting into actual NGYA timesheet)
                insertPos += 2;
                appendRow("A" + insertPos, Arrays.asList(now.getYear(), getMonthName(now)), "INSERT_ROWS");
                newDay(insertPos + 1, now, currTime);
            }

            // Set current spreadsheet as clocked in
            SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(currSpreadsheetId, true);
            editor.apply();
        }

        private void newDay(int insertPos, LocalDateTime now, String formattedTime) {
            appendRow("C" + insertPos, Arrays.asList(now.getDayOfMonth() + "",
                    DAY_OF_WEEK_NAMES[now.getDayOfWeek().getValue() - 1],
                    formattedTime), "INSERT_ROWS");
        }

        private void newMonth(int insertPos, LocalDateTime now) {
            appendRow("B" + (insertPos), Arrays.asList(getMonthName(now)),
                    "INSERT_ROWS");
        }

        private String getMonthName(LocalDateTime date) {
            return date.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault());
        }

        private void clockOut(LocalDateTime now) {
            // No need to check date, we can assume that the date exists already

            // Get current time as string
            int lastDayIndex = getLastRowInColumn("C");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");
            String currTime = now.format(formatter);

            // Get time difference between start and stop
            String startTime = getLastDataInColumn("E");
            LocalTime time = LocalTime.parse(startTime, formatter);
            LocalDateTime startDateTime = LocalDateTime.of(now.getYear(), now.getMonth(), now.getDayOfMonth(), time.getHour(), time.getMinute());
            // Start time is before 12AM and end time is after 12AM: offset start time by a day backwards
            if (startDateTime.getHour() >= TIME_NEXT_DAY_START && now.getHour() < TIME_NEXT_DAY_START) {
                startDateTime = startDateTime.minusDays(1);
            }
            long minutesBetween = ChronoUnit.MINUTES.between(startDateTime, now);

            // Get notes
            String notes = notesField.getText().toString();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notesField.getText().clear();
                }
            });

            appendRow("F" + (lastDayIndex), Arrays.asList(currTime, minutesBetween, notes), "OVERWRITE");

            // Set current spreadsheet as clocked out
            SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.remove(spreadsheetId);
            editor.apply();
        }

        @Override
        protected void onPostExecute(Void result) {
            // TODO: Display text messages for successful clock in / clock out (and also the time at which they have been done)
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
                showMessage(clockInBtn, "API call failed, try again in a bit?");
            }
            return response;
        }

        // Given a column name (i.e. A, B, C, etc.) return the row number of the last non-blank entry in that column
        // Returns -1 if an error occurs
        // It does this by appending an empty row to the sheet and then analyzing the response
        private int getLastRowInColumn(String columnName) {
            AppendValuesResponse response = appendRow(columnName + "3:" + columnName, Arrays.asList(new String[]{}), "INSERT_ROWS");

            if (response == null)
                return -1;

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
                showMessage(clockInBtn, "API call failed, try again in a bit?");
            }

            List<List<Object>> values = result.getValues();
            if (values == null)
                return null;
            return result.getValues().get(0).get(0).toString();
        }
    }

    private class GetPermissions extends AsyncTask<Void, Void, Void> {
        private String spreadsheetId;

        GetPermissions(String spreadsheetId) {
            this.spreadsheetId = spreadsheetId;
        }

        @Override
        protected Void doInBackground(Void... params) {
            // Try to do a trivial edit to a Google Sheet to make sure proper permissions are in place
            String valueInputOption = "USER_ENTERED";
            String insertDataOption = "OVERWRITE";
            List<List<Object>> values = Arrays.asList();
            ValueRange requestBody = new ValueRange();
            requestBody.setValues(values);

            AppendValuesResponse response = null;
            try {
                Sheets.Spreadsheets.Values.Append request = service.spreadsheets().values().append(spreadsheetId, "A1", requestBody);
                request.setValueInputOption(valueInputOption);
                request.setInsertDataOption(insertDataOption);
                response = request.execute();
            } catch (UserRecoverableAuthIOException e) {
                startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
            } catch (IOException e) {
                e.printStackTrace();
            }

            FileList listResponse = null;
            try {
                Drive.Files.List listRequest = drive.files().list();
                listRequest.setQ("mimeType='application/vnd.google-apps.spreadsheet' and visibility = 'limited' and trashed = false");
                listResponse = listRequest.execute();
            } catch (UserRecoverableAuthIOException e) {
                startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
            } catch (IOException e) {
                e.printStackTrace();
            }

            List<com.google.api.services.drive.model.File> files = listResponse.getFiles();
            List<String> fileTitles = new ArrayList<String>();

            for (com.google.api.services.drive.model.File file : files) {
                fileTitles.add(file.getName());
                // TODO: Favorite spreadsheets
                spreadsheets.put(file.getName(), new SpreadsheetItem(file.getId(), false));
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                    getApplicationContext(), R.layout.support_simple_spinner_dropdown_item, fileTitles);

            // Set the dropdown items
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    spreadsheetDropdown.setAdapter(adapter);
                }
            });

            return null;
        }
    }
}
