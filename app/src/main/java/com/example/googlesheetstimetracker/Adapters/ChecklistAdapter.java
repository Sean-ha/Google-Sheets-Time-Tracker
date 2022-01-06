package com.example.googlesheetstimetracker.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.googlesheetstimetracker.ActivateSpreadsheetsActivity;
import com.example.googlesheetstimetracker.Models.SpreadsheetItemModel;
import com.example.googlesheetstimetracker.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChecklistAdapter extends RecyclerView.Adapter<ChecklistAdapter.ViewHolder> {

    private List<SpreadsheetItemModel> sheetList;
    private ActivateSpreadsheetsActivity activity;

    private Set<String> chosenSheets = new HashSet<>();

    public ChecklistAdapter(ActivateSpreadsheetsActivity activity, Set<String> chosenSheets) {
        this.activity = activity;
        this.chosenSheets = chosenSheets;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.sheet_item_layout, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        final SpreadsheetItemModel item = sheetList.get(position);

        holder.task.setText(item.name);
        holder.task.setChecked(item.selected);
        holder.task.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    chosenSheets.add(buttonView.getText().toString());
                }
                else {
                    chosenSheets.remove(buttonView.getText().toString());
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return sheetList.size();
    }

    public void setList(List<SpreadsheetItemModel> sheetList) {
        this.sheetList = sheetList;
        notifyDataSetChanged();
    }

    public Set<String> getChosenSheets() {
        return chosenSheets;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox task;

        ViewHolder(View view) {
            super(view);
            task = view.findViewById(R.id.todoCheckBox);
        }
    }

}