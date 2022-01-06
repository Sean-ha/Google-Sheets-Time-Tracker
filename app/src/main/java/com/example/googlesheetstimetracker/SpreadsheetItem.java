package com.example.googlesheetstimetracker;

public class SpreadsheetItem {
    public String spreadsheetId;
    public boolean favorited;

    public SpreadsheetItem(String spreadsheetId, boolean favorited) {
        this.spreadsheetId = spreadsheetId;
        this.favorited = favorited;
    }
}
