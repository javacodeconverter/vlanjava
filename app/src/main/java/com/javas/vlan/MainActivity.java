package com.javas.vlan;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int VPN_REQUEST_CODE = 0x0F;
    private EditText etServerIp, etNetworkName, etPassword;
    private TextView tvStatus;
    private Button btnCreateNetwork, btnConnectNetwork, btnToggleVpn;
    private boolean isVpnActive = false;
    private String selectedRole = "join";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etServerIp = findViewById(R.id.etServerIp);
        etNetworkName = findViewById(R.id.etNetworkName);
        etPassword = findViewById(R.id.etPassword);
        tvStatus = findViewById(R.id.tvStatus);
        
        btnCreateNetwork = findViewById(R.id.btnCreateNetwork);
        btnConnectNetwork = findViewById(R.id.btnConnectNetwork);
        btnToggleVpn = findViewById(R.id.btnToggleVpn);

        btnCreateNetwork.setOnClickListener(v -> {
            selectedRole = "create";
            tvStatus.setText("Selected Mode: Create Network");
            tvStatus.setTextColor(0xFF00FF00);
        });

        btnConnectNetwork.setOnClickListener(v -> {
            selectedRole = "join";
            tvStatus.setText("Selected Mode: Join Network");
            tvStatus.setTextColor(0xFF00FFFF);
        });

        btnToggleVpn.setOnClickListener(v -> {
            if (isVpnActive) {
                stopVpn();
            } else {
                startVpn();
            }
        });
    }

    private void startVpn() {
        String serverIpInput = etServerIp.getText().toString().trim();
        String netName = etNetworkName.getText().toString().trim();
        String netPass = etPassword.getText().toString().trim();

        if (serverIpInput.isEmpty() || netName.isEmpty() || netPass.isEmpty()) {
            Toast.makeText(this, "Please fill in all configuration fields", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE);
        } else {
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
        }
    }

    private void stopVpn() {
        Intent intent = new Intent(this, JvlanVpnService.class);
        stopService(intent);
        isVpnActive = false;
        btnToggleVpn.setText("Turn On");
        btnToggleVpn.setBackgroundColor(0xFFFF0000);
        tvStatus.setText("Status: DISCONNECTED");
        tvStatus.setTextColor(0xFFFF0000);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            Intent intent = new Intent(this, JvlanVpnService.class);
            
            // Resolve port defaults if port is omitted (default to 8888 or parsing playit port formats)
            String rawIp = etServerIp.getText().toString().trim();
            String resolvedHost = rawIp;
            int resolvedPort = 8888;
            
            if (rawIp.contains(":")) {
                String[] parts = rawIp.split(":");
                resolvedHost = parts[0];
                try {
                    resolvedPort = Integer.parseInt(parts[1]);
                } catch(NumberFormatException e) {
                    resolvedPort = 8888;
                }
            }
            
            intent.putExtra("serverIp", resolvedHost);
            intent.putExtra("serverPort", resolvedPort);
            intent.putExtra("netName", etNetworkName.getText().toString().trim());
            intent.putExtra("netPass", etPassword.getText().toString().trim());
            intent.putExtra("role", selectedRole);
            startService(intent);

            isVpnActive = true;
            btnToggleVpn.setText("Turn Off");
            btnToggleVpn.setBackgroundColor(0xFF333333);
            tvStatus.setText("Status: CONNECTED (java's VLAN Running)");
            tvStatus.setTextColor(0xFF00FF00);
        }
    }
}
