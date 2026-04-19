package com.financialbuddy.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    public static final String ACTION_PAYMENT_DETECTED = "com.financialbuddy.PAYMENT_DETECTED";
    public static final String EXTRA_AMOUNT = "amount";
    public static final String EXTRA_MERCHANT = "merchant";

    public static final String EXTRA_NOTIF_TEXT = "notif_text";
    public static final String EXTRA_NOTIF_SOURCE = "notif_source";

    private TextView tvMonth;
    private ListView lvCategories;
    private Calendar currentMonth;
    private List<JSONObject> categoryList = new ArrayList<>();
    private CategoryAdapter adapter;
    private SharedPreferences prefs;

    // Catches background payments from Google Pay
    private final BroadcastReceiver paymentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_PAYMENT_DETECTED.equals(intent.getAction())) {
                String amount = intent.getStringExtra(EXTRA_AMOUNT);
                String merchant = intent.getStringExtra(EXTRA_MERCHANT);
                showLogTransactionDialog(amount, merchant);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("FB_NATIVE_DATA", MODE_PRIVATE);
        currentMonth = Calendar.getInstance();

        tvMonth = findViewById(R.id.tvMonth);
        lvCategories = findViewById(R.id.lvCategories);
        adapter = new CategoryAdapter();
        lvCategories.setAdapter(adapter);

        findViewById(R.id.btnPrevMonth).setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, -1);
            updateMonthDisplay();
        });

        findViewById(R.id.btnNextMonth).setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, 1);
            updateMonthDisplay();
        });

        findViewById(R.id.btnAddCategory).setOnClickListener(v -> showCategoryDialog(null, -1));

        checkNotificationListenerPermission();
        updateMonthDisplay();
    }

    private void updateMonthDisplay() {
        String monthStr = android.text.format.DateFormat.format("MMM yyyy", currentMonth).toString();
        tvMonth.setText(monthStr);
        loadCategories();
    }

    private String getMonthKey() {
        return currentMonth.get(Calendar.YEAR) + "_" + currentMonth.get(Calendar.MONTH);
    }

    // --- DATA MANAGEMENT ---
    private void loadCategories() {
        categoryList.clear();
        try {
            JSONArray array = new JSONArray(prefs.getString("cats_" + getMonthKey(), "[]"));
            for (int i = 0; i < array.length(); i++) {
                categoryList.add(array.getJSONObject(i));
            }
        } catch (JSONException e) { e.printStackTrace(); }
        adapter.notifyDataSetChanged();
    }

    private void saveCategories() {
        JSONArray array = new JSONArray();
        for (JSONObject obj : categoryList) array.put(obj);
        prefs.edit().putString("cats_" + getMonthKey(), array.toString()).apply();
    }

    // --- NATIVE DIALOGS ---
    private void showCategoryDialog(JSONObject categoryToEdit, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkAlertDialog);
        builder.setTitle(categoryToEdit == null ? R.string.add_category : R.string.edit);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText inputName = new EditText(this);
        inputName.setHint(R.string.category_name);
        inputName.setTextColor(0xFFFFFFFF);

        final EditText inputLimit = new EditText(this);
        inputLimit.setHint(R.string.monthly_limit);
        inputLimit.setTextColor(0xFFFFFFFF);
        inputLimit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        if (categoryToEdit != null) {
            inputName.setText(categoryToEdit.optString("name"));
            inputLimit.setText(String.valueOf(categoryToEdit.optDouble("limit", 0)));
        }

        layout.addView(inputName);
        layout.addView(inputLimit);
        builder.setView(layout);

        builder.setPositiveButton(R.string.save, (dialog, which) -> {
            try {
                String name = inputName.getText().toString();
                double limit = inputLimit.getText().toString().isEmpty() ? 0 : Double.parseDouble(inputLimit.getText().toString());

                if (position >= 0) {
                    categoryList.get(position).put("name", name);
                    categoryList.get(position).put("limit", limit);
                } else {
                    JSONObject newCat = new JSONObject();
                    newCat.put("name", name);
                    newCat.put("limit", limit);
                    newCat.put("emoji", "💰");
                    categoryList.add(newCat);
                }
                saveCategories();
                adapter.notifyDataSetChanged();
            } catch (Exception e) { e.printStackTrace(); }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void deleteCategory(int position) {
        new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                .setMessage(R.string.delete_confirm)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    categoryList.remove(position);
                    saveCategories();
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void showLogTransactionDialog(String prefillAmount, String prefillMerchant) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DarkAlertDialog);
        builder.setTitle(R.string.log_transaction);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText inputAmount = new EditText(this);
        inputAmount.setHint(R.string.amount);
        inputAmount.setTextColor(0xFFFFFFFF);
        if (prefillAmount != null) inputAmount.setText(prefillAmount);

        final EditText inputMerchant = new EditText(this);
        inputMerchant.setHint(R.string.merchant);
        inputMerchant.setTextColor(0xFFFFFFFF);
        if (prefillMerchant != null) inputMerchant.setText(prefillMerchant);

        layout.addView(inputAmount);
        layout.addView(inputMerchant);
        builder.setView(layout);

        builder.setPositiveButton(R.string.save, (dialog, which) -> {
            Toast.makeText(this, R.string.tx_saved, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void checkNotificationListenerPermission() {
        Set<String> enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(this);
        if (!enabledListeners.contains(getPackageName())) {
            new AlertDialog.Builder(this, R.style.DarkAlertDialog)
                    .setTitle(R.string.notification_access_title)
                    .setMessage(R.string.notification_access_message)
                    .setPositiveButton(R.string.allow, (d, w) -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                    .setNegativeButton(R.string.later, null)
                    .show();
        }
    }

    // --- NATIVE LIST ADAPTER ---
    class CategoryAdapter extends ArrayAdapter<JSONObject> {
        public CategoryAdapter() { super(MainActivity.this, R.layout.item_category, categoryList); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_category, parent, false);
            }
            JSONObject cat = getItem(position);
            TextView tvEmoji = convertView.findViewById(R.id.tvEmoji);
            TextView tvName = convertView.findViewById(R.id.tvCatName);
            TextView tvLimit = convertView.findViewById(R.id.tvLimit);

            try {
                tvEmoji.setText(cat.optString("emoji", "💰"));
                tvName.setText(cat.optString("name"));
                tvLimit.setText(getString(R.string.monthly_limit) + ": $" + cat.optDouble("limit", 0));
            } catch (Exception e) {}

            convertView.findViewById(R.id.btnEdit).setOnClickListener(v -> showCategoryDialog(cat, position));
            convertView.findViewById(R.id.btnDelete).setOnClickListener(v -> deleteCategory(position));

            return convertView;
        }
    }

    // --- LIFECYCLE ---
    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(ACTION_PAYMENT_DETECTED);
        LocalBroadcastManager.getInstance(this).registerReceiver(paymentReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(paymentReceiver);
    }
}
