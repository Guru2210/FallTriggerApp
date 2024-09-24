package com.example.FallTriggerApp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private Button saveBtn;
    private Switch smsSendSwitch;
    private Integer sensitivityPercentage;
    private SeekBar seekBar;
    private Integer sensitivity;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        saveBtn = findViewById(R.id.saveButton);
        smsSendSwitch = (Switch) findViewById(R.id.smsAlertSwitch);
        seekBar=findViewById(R.id.fallSensitivitySeekBar);
        Bundle bundle = getIntent().getExtras();
        sensitivity = bundle != null ? bundle.getInt("sensitivity", 0) : 50;
        seekBar.setProgress(sensitivity);
        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        // Display the current progress of the SeekBar
                        sensitivityPercentage=progress;
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        // This method can be used to perform actions when the user starts moving the slider
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        // This method can be used to perform actions after the user releases the slider
                    }
                }
        );

        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent nextActivity = new Intent(SettingsActivity.this, MainActivity.class);
                nextActivity.putExtra("sensitivity",sensitivityPercentage);
                if (smsSendSwitch.isChecked()) {
                    nextActivity.putExtra("switch", "ON");
                } else {
                    nextActivity.putExtra("switch", "OFF");
                }
                startActivity(nextActivity);
            }
        });
    }
}