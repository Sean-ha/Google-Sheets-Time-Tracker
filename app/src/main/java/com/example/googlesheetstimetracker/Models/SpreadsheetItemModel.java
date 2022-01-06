package com.example.googlesheetstimetracker.Models;

public class SpreadsheetItemModel {
    public String id;
    public String name;
    public boolean selected;

    public SpreadsheetItemModel(String id, String name, boolean selected) {
        this.id = id;
        this.name = name;
        this.selected = selected;
    }
}
