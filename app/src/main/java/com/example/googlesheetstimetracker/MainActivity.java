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

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.googlesheetstimetracker.Utils.REQUEST_AUTHORIZATION;

public class MainActivity extends AppCompatActivity {
    private static Sheets service;
    public static Drive drive;

    // From https://developers.google.com/sheets/api/quickstart/java
    private static final String APPLICATION_NAME = "Google Sheets Time Tracker";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String PREF_CHOSEN_SHEETS = "savedChosenSheets";
    private static final String PREF_CLOCK_IN_MESSAGE = "savedClockInMsg";
    private static final String PREF_LAST_CHOSEN_SHEET = "lastChosenSheet";
    private final int REQUEST_CODE = 1;
    private final String[] DAY_OF_WEEK_NAMES = new String[] {"Mon", "Tues", "Wed", "Thurs", "Fri", "Sat", "Sun"};
    private final String[] ENCOURAGEMENT_PHRASES = new String[] {"Nice job!", "Good job!", "Great job!", "Nice work!",
            "Good work!", "Great work!",  "Well done!", "Nice!", "Let's go!", "Good stuff!", "Great stuff!", "Nicely done!", "You did good."};
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
    private String currSelectedTitle;

    // Array of the sheets that are currently available in the dropdown
    private String[] chosenArr;

    // Key: spreadsheet name, Value: spreadsheet id
    public static HashMap<String, String> spreadsheetMap = new HashMap<String, String>();
    private File spreadsheetMapFile;

    Button clockInBtn;
    Button clockOutBtn;
    EditText notesField;
    Spinner spreadsheetDropdown;
    TextView spreadsheetIdText;
    Button chooseSheetsBtn;

    TextView clockInMessageText;
    TextView clockOutMessageText;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // NOTE: This is the TEST NGYA spreadsheet
        testSpreadsheetId = "1_mXaaGUHur5d13X5NrK6U3fZcfEzVojH4ISCvtcTOcU";

        init();
        initAuth();
        chooseAccount();

        // Came back from ActivateSpreadsheetsActivity; set dropdown items and save it
        if (getIntent().hasExtra("sheetsChosen")) {
            chosenArr = getIntent().getExtras().getStringArray("sheetsChosen");
            Arrays.sort(chosenArr);
            setDropdownItems();

            // Save chosen array
            SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            JSONArray jArray = new JSONArray();
            try {
                jArray = new JSONArray(chosenArr);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            editor.putString(PREF_CHOSEN_SHEETS, jArray.toString());
            editor.apply();

            // Save id / name HashMap to internal memory
            ObjectOutputStream outputStream = null;
            try {
                outputStream = new ObjectOutputStream(new FileOutputStream(spreadsheetMapFile));
                outputStream.writeObject(spreadsheetMap);
                outputStream.flush();
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

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

    // Uses variable "chosenArr" to set the items of the dropdown menu
    private void setDropdownItems() {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                getApplicationContext(), R.layout.support_simple_spinner_dropdown_item, chosenArr);

        spreadsheetDropdown.setAdapter(adapter);
    }

    private void init() {
        spreadsheetMapFile = new File(getDir("data", MODE_PRIVATE), "map");
        // Initializing Internet Checker
        internetDetector = new InternetDetector(getApplicationContext());

        clockInBtn = findViewById(R.id.clockInBtn);
        clockOutBtn = findViewById(R.id.clockOutBtn);
        notesField = findViewById(R.id.notesField);
        spreadsheetDropdown = findViewById(R.id.spreadsheetDropdown);
        spreadsheetIdText = findViewById(R.id.spreadsheetIdText);
        chooseSheetsBtn = findViewById(R.id.chooseSheetsBtn);
        clockInMessageText = findViewById(R.id.clockInMessageText);
        clockOutMessageText = findViewById(R.id.clockOutMessageText);

        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);

        clockInMessageText.setText(settings.getString(PREF_CLOCK_IN_MESSAGE, ""));

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
        chooseSheetsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent startIntent = new Intent(getApplicationContext(), ActivateSpreadsheetsActivity.class);
                startIntent.putExtra("currentlyChosenSheets", chosenArr);
                startActivity(startIntent);
            }
        });

        AdapterView.OnItemSelectedListener onItemSelectedListener = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                ((TextView) parent.getChildAt(0)).setTextSize(18);
                ((TextView) parent.getChildAt(0)).setTranslationY(21);

                currSelectedTitle = parent.getItemAtPosition(pos).toString();
                currSpreadsheetId = spreadsheetMap.get(currSelectedTitle);

                spreadsheetIdText.setText("id: " + currSpreadsheetId);

                // If it contains, it means we are clocked in
                if (settings.contains(currSpreadsheetId)) {
                    showClockOutScreen();
                } else {
                    showClockInScreen();
                }

                // Save this sheet as being currently selected
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(PREF_LAST_CHOSEN_SHEET, currSelectedTitle);
                editor.apply();
            }

            public void onNothingSelected(AdapterView<?> parent) { }
        };
        spreadsheetDropdown.setOnItemSelectedListener(onItemSelectedListener);

        // Load previously chosen sheets and set dropdown menu accordingly
        JSONArray jArray = null;
        try {
            String retrieved = settings.getString(PREF_CHOSEN_SHEETS, null);
            if (retrieved != null) {
                jArray = new JSONArray(retrieved);
                chosenArr = new String[jArray.length()];
                for (int i = 0; i < jArray.length(); i++) {
                    chosenArr[i] = jArray.get(i).toString();
                }
                setDropdownItems();
            }
        } catch (JSONException e) {
            showMessage(clockInBtn, "Error while loading saved chosen sheets");
            e.printStackTrace();
        }

        // Set currently selected item from spinner (based on saved value in SharedPrefs)
        String selectedSheet = settings.getString(PREF_LAST_CHOSEN_SHEET, null);
        if (selectedSheet != null) {
            spreadsheetDropdown.setSelection(((ArrayAdapter)spreadsheetDropdown.getAdapter()).getPosition(selectedSheet));
        }

        // Read HashMap from internal memory
        ObjectInputStream inputStream = null;
        try {
            inputStream = new ObjectInputStream(new FileInputStream(spreadsheetMapFile));
            spreadsheetMap = (HashMap<String, String>) inputStream.readObject();
        } catch (FileNotFoundException e) {
            showMessage(clockInBtn, "Map file not found in memory. Should  resolve on restart");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void initAuth() {
        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), SCOPES)
                .setBackOff(new ExponentialBackOff());
        service = getSheetsService();
        drive = getDriveService();

        if (mCredential.getSelectedAccountName() != null) {
            // Makes sure proper permissions are available, and also sets up the dropdown spinner's items
            new GetPermissions(testSpreadsheetId).execute();
        }
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
                // TODO: Some issue when clocking in on a new year? (Can't seem to reproduce atm)
                insertPos += 2;
                appendRow("A" + insertPos, Arrays.asList(now.getYear(), getMonthName(now)), "INSERT_ROWS");
                newDay(insertPos + 1, now, currTime);
            }

            DateTimeFormatter prettyFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm a");
            String prettyDate = now.format(prettyFormatter);
            String msg = "[" + currSelectedTitle + "] IN at " + prettyDate;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    clockInMessageText.setText(msg);
                    clockOutMessageText.setText("");
                }
            });

            // Set current spreadsheet as clocked in
            SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(currSpreadsheetId, true);
            // Save clock in message
            editor.putString(PREF_CLOCK_IN_MESSAGE, msg);
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

            appendRow("F" + (lastDayIndex), Arrays.asList(currTime, minutesBetween, notes), "OVERWRITE");

            // Set clock out message
            DateTimeFormatter prettyFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm a");
            String prettyDate = now.format(prettyFormatter);
            String encouragement = ENCOURAGEMENT_PHRASES[new Random().nextInt(ENCOURAGEMENT_PHRASES.length)];
            String msg = "OUT at " + prettyDate + " (" + minutesBetween + " mins). " + encouragement;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notesField.getText().clear();
                    clockOutMessageText.setText(msg);
                }
            });

            // Set current spreadsheet as clocked out
            SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.remove(spreadsheetId);

            // Unsave clock in message
            editor.remove(PREF_CLOCK_IN_MESSAGE);
            editor.apply();
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

            return null;
        }
    }
}
