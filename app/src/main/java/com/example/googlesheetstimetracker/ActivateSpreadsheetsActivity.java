package com.example.googlesheetstimetracker;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.googlesheetstimetracker.Adapters.ChecklistAdapter;
import com.example.googlesheetstimetracker.Models.SpreadsheetItemModel;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.example.googlesheetstimetracker.Utils.REQUEST_AUTHORIZATION;

public class ActivateSpreadsheetsActivity extends AppCompatActivity {

    private GoogleAccountCredential mCredential;


    private RecyclerView sheetsRecyclerView;
    private ChecklistAdapter checklistAdapter;

    private List<SpreadsheetItemModel> sheetsList;

    private Set<String> chosenSheets;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activate_spreadsheets);

        chosenSheets = new HashSet<String>();
        if (getIntent().hasExtra("currentlyChosenSheets")) {
            String[] chosenArr = getIntent().getExtras().getStringArray("currentlyChosenSheets");

            if (chosenArr != null) {
                for (int i = 0; i < chosenArr.length; i++) {
                    chosenSheets.add(chosenArr[i]);
                }
            }
        }

        init();
    }

    private void init() {
        sheetsRecyclerView = findViewById(R.id.sheetsRecyclerView);
        sheetsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        checklistAdapter = new ChecklistAdapter(this, chosenSheets);
        sheetsRecyclerView.setAdapter(checklistAdapter);

        sheetsList = new ArrayList<>();
        checklistAdapter.setList(sheetsList);

        findViewById(R.id.saveBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // and also store the selected options somehow (SharedPrefs)
                Set<String> chosen = checklistAdapter.getChosenSheets();
                Intent toMainIntent = new Intent(getApplicationContext(), MainActivity.class);
                String[] chosenArr = new String[chosen.size()];
                System.arraycopy(chosen.toArray(), 0, chosenArr, 0, chosen.size());
                toMainIntent.putExtra("sheetsChosen", chosenArr);
                startActivity(toMainIntent);
            }
        });

        new GetAllSpreadsheets().execute();
    }

    private class GetAllSpreadsheets extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            FileList listResponse = null;
            try {
                Drive.Files.List listRequest = MainActivity.drive.files().list();
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
                if (chosenSheets.contains(file.getName())) {
                    sheetsList.add(new SpreadsheetItemModel(file.getId(), file.getName(), true));
                }
                else {
                    sheetsList.add(new SpreadsheetItemModel(file.getId(), file.getName(), false));
                }
                MainActivity.spreadsheetMap.put(file.getName(), file.getId());
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    checklistAdapter.setList(sheetsList);
                }
            });


            return null;
        }
    }
}
