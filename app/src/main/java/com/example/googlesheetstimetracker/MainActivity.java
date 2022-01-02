package com.example.googlesheetstimetracker;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.ExtendedValue;
import com.google.api.services.sheets.v4.model.GridCoordinate;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.UpdateCellsRequest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static File CREDENTIALS_FILE;
    private static String CREDENTIALS_FILE_PATH;
    private static Sheets service;
    private GoogleAccountCredential credential;

    // From https://developers.google.com/sheets/api/quickstart/java
    private static final String APPLICATION_NAME = "Google Sheets Time Tracker";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        // requestSignIn();

        TextView tempTextView = (TextView) findViewById(R.id.tempTextView);

        // createCredentialsFile();
        // CREDENTIALS_FILE = new File(getApplicationContext().getExternalFilesDir(null), "credentials.json");
        // CREDENTIALS_FILE_PATH = CREDENTIALS_FILE.getAbsolutePath();

        // service = getSheetsService();

        Button btn = (Button) findViewById(R.id.clockInBtn);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // NOTE: This is the NGYA spreadsheet
                String spreadsheetId = "1ZbqRJErnD-GB8Nm9EKnfbkyG3j4z1tIoIIVf5OCq-04";

                /*
                List<Request> requests = new ArrayList<>();
                List<CellData> values = new ArrayList<>();

                values.add(new CellData()
                        .setUserEnteredValue(new ExtendedValue()
                                .setStringValue("Hello World!")));

                requests.add(new Request()
                        .setUpdateCells(new UpdateCellsRequest()
                                .setStart(new GridCoordinate()
                                        .setSheetId(0)
                                        .setRowIndex(0)
                                        .setColumnIndex(0))
                                .setRows(Arrays.asList(
                                        new RowData().setValues(values)))
                                .setFields("userEnteredValue,userEnteredFormat.backgroundColor")));

                BatchUpdateSpreadsheetRequest batchUpdateRequest = new BatchUpdateSpreadsheetRequest()
                        .setRequests(requests);
                try {
                    service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateRequest)
                            .execute();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                */
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                GoogleSignIn.getSignedInAccountFromIntent(data).addOnSuccessListener((account) -> {
                    GoogleAccountCredential.usingOAuth2(getApplicationContext(), SCOPES)
                            .setSelectedAccount(account.getAccount());

                    HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
                    service = new Sheets.Builder(httpTransport, JSON_FACTORY, credential)
                            .setApplicationName(getString(R.string.app_name))
                            .build();

                    // createSpreadsheet();
                    executeAction("1ZbqRJErnD-GB8Nm9EKnfbkyG3j4z1tIoIIVf5OCq-04");
                });
            }
        }
    }

    private void executeAction(String spreadsheetId) {
        new UpdateSheet().execute(spreadsheetId);
    }

    private void createSpreadsheet() {
        Spreadsheet spreadsheet = new Spreadsheet().setProperties(new SpreadsheetProperties().setTitle("aNewSpreadsheet!!"));

        try {
            spreadsheet = service.spreadsheets().create(spreadsheet).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void requestSignIn() {
        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(new Scope(SheetsScopes.SPREADSHEETS)).requestEmail()
                .build();
        GoogleSignInClient client = GoogleSignIn.getClient(getApplicationContext(), signInOptions);

        startActivityForResult(client.getSignInIntent(), 1);
    }


    private class UpdateSheet extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params) {
            String spreadsheetId = params[0];

            List<Request> requests = new ArrayList<>();
            List<CellData> values = new ArrayList<>();


            values.add(new CellData()
                    .setUserEnteredValue(new ExtendedValue()
                            .setStringValue("Hello World!")));
            requests.add(new Request()
                    .setUpdateCells(new UpdateCellsRequest()
                            .setStart(new GridCoordinate()
                                    .setSheetId(0)
                                    .setRowIndex(0)
                                    .setColumnIndex(0))
                            .setRows(Arrays.asList(
                                    new RowData().setValues(values)))
                            .setFields("userEnteredValue,userEnteredFormat.backgroundColor")));

            BatchUpdateSpreadsheetRequest batchUpdateRequest = new BatchUpdateSpreadsheetRequest()
                    .setRequests(requests);
            try {
                service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateRequest)
                        .execute();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
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
    }

        /**
         * Creates an authorized Credential object.
         *
         * @param HTTP_TRANSPORT The network HTTP Transport.
         * @return An authorized Credential object.
         * @throws IOException If the credentials.json file cannot be found.
         */
        private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
            // Load client secrets.
            InputStream in = getApplicationContext().getAssets().open("private/oauth_credentials.json");
            if (in == null) {
                throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
            }
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

            createTokenFolderIfMissing();

            // Build flow and trigger user authorization request.
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(getTokenFolder()))
                    .setAccessType("offline")
                    .build();
            LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();

            AuthorizationCodeInstalledApp ab = new AuthorizationCodeInstalledApp(flow, receiver) {
                protected void onAuthorization(AuthorizationCodeRequestUrl authorizationUrl) throws IOException {
                    String url = (authorizationUrl.build());
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getApplicationContext().startActivity(browserIntent);
                }
            };
            return ab.authorize("seanha2013@gmail.com");
            // return new AuthorizationCodeInstalledApp(flow, receiver).authorize("seanha2013@gmail.com");
        }

        private void createTokenFolderIfMissing() {
            File tokenFolder = getTokenFolder();
            if (!tokenFolder.exists()) {
                tokenFolder.mkdir();
            }
        }

        private File getTokenFolder() {
            return new File(getApplicationContext().getExternalFilesDir("").getAbsolutePath() + "/" + TOKENS_DIRECTORY_PATH);
        }

        private Sheets getSheetsService() {
            NetHttpTransport HTTP_TRANSPORT = null;
            Credential cred = null;
            try {
                HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
                cred = getCredentials(HTTP_TRANSPORT);
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            Sheets service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred)
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            return service;
        }
}
