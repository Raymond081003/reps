package com.example.reps;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;

public class ExportReport extends AppCompatActivity {

    private EditText etItemName, etAmount;
    private Button btnSubmit, btnExport;
    private TextView tvStatus;
    private Spinner reportTypeSpinner;
    private DatabaseReference expenseDatabaseReference;
    private DatabaseReference reportDatabaseReference;
    private String selectedReportType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        etItemName = findViewById(R.id.et_item_name);
        etAmount = findViewById(R.id.et_amount);
        btnSubmit = findViewById(R.id.btn_submit);
        tvStatus = findViewById(R.id.tv_status);
        reportTypeSpinner = findViewById(R.id.reportTypeSpinner);
        btnExport = findViewById(R.id.exportButton);

        // Initialize Firebase Database references
        expenseDatabaseReference = FirebaseDatabase.getInstance().getReference("expenses");
        reportDatabaseReference = FirebaseDatabase.getInstance().getReference("sales_inventory");

        // Set up the spinner for report types
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.report_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        reportTypeSpinner.setAdapter(adapter);

        reportTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedReportType = parent.getItemAtPosition(position).toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedReportType = "Daily"; // Default to daily if nothing is selected
            }
        });

        // Button click listeners
        btnSubmit.setOnClickListener(v -> submitExpense());
        btnExport.setOnClickListener(v -> fetchAndExportData());
    }

    private void submitExpense() {
        String itemName = etItemName.getText().toString().trim();
        String amountString = etAmount.getText().toString().trim();

        if (TextUtils.isEmpty(itemName)) {
            etItemName.setError("Item name is required");
            return;
        }

        if (TextUtils.isEmpty(amountString)) {
            etAmount.setError("Amount is required");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountString);
        } catch (NumberFormatException e) {
            etAmount.setError("Enter a valid amount");
            return;
        }

        // Create a unique key for each expense entry
        String expenseId = expenseDatabaseReference.push().getKey();

        // Create an Expense object
        Expense expense = new Expense(expenseId, itemName, amount);

        // Save to Firebase
        expenseDatabaseReference.child(expenseId).setValue(expense).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                tvStatus.setText("Expense saved successfully!");
                Toast.makeText(this, "Expense saved to database", Toast.LENGTH_SHORT).show();

                // Clear input fields
                etItemName.setText("");
                etAmount.setText("");
            } else {
                tvStatus.setText("Failed to save expense.");
                Toast.makeText(this, "Failed to save expense", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchAndExportData() {
        reportDatabaseReference.child(selectedReportType).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                StringBuilder data = new StringBuilder();
                for (DataSnapshot childSnapshot : snapshot.getChildren()) {
                    String key = childSnapshot.getKey();
                    String value = childSnapshot.getValue(String.class);
                    data.append(key).append(": ").append(value).append("\n");
                }

                generateAndSaveImage(data.toString());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ExportReport.this, "Error fetching data: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void generateAndSaveImage(String data) {
        Bitmap bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        canvas.drawColor(android.graphics.Color.WHITE);
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(android.graphics.Color.BLACK);
        paint.setTextSize(24);

        // Draw the data text on the bitmap
        String[] lines = data.split("\n");
        int y = 50;
        for (String line : lines) {
            canvas.drawText(line, 50, y, paint);
            y += 30;
        }

        try {
            // Save the image in the app-specific directory
            File directory = new File(getExternalFilesDir(null), "Reports");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new Exception("Failed to create directory");
            }

            File file = new File(directory, selectedReportType + "_Report.png");
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            }

            // Notify user
            Toast.makeText(this, "Report saved to " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();

            // Use FileProvider to create a content URI
            Uri fileUri = FileProvider.getUriForFile(this, "com.example.reps.fileprovider", file);

            // Open the file with an intent
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, "image/png");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open Report"));
        } catch (Exception e) {
            Log.e("ExportError", "Error saving report image", e);
            Toast.makeText(this, "Error saving report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


    // Expense class to model the data
    public static class Expense {
        private String id;
        private String itemName;
        private double amount;

        public Expense() {
            // Default constructor required for calls to DataSnapshot.getValue(Expense.class)
        }

        public Expense(String id, String itemName, double amount) {
            this.id = id;
            this.itemName = itemName;
            this.amount = amount;
        }

        public String getId() {
            return id;
        }

        public String getItemName() {
            return itemName;
        }

        public double getAmount() {
            return amount;
        }
    }
}
