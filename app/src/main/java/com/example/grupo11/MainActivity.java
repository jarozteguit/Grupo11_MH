package com.example.grupo11;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;


import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.RetryPolicy;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.DefaultValueFormatter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private EditText angleEditText;
    private ToggleButton modeToggleButton;
    private Button rotateButton;
    private SwipeRefreshLayout swipeRefreshLayout;
    private static final String BASE_URL_ANGLE = "http://192.168.4.1/?angle=";
    private static final String BASE_URL_SENSOR = "http://192.168.4.1/?sensor1";
    private static final String BASE_URL_SENSOR_2 = "http://192.168.4.1/?sensor2";
    private static final String BASE_URL_AUTO = "http://192.168.4.1/?auto";
    private static final String BASE_URL_MANUAL = "http://192.168.4.1/?manual";
    private static final String BASE_URL_CODIF = "http://192.168.4.3/?codif";
    private static final String BASE_URL_VEL1 = "http://192.168.4.1/?vel1";
    private static final String BASE_URL_VEL2 = "http://192.168.4.1/?vel2";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(this::restartApp);

        angleEditText = findViewById(R.id.angleEditText);
        modeToggleButton = findViewById(R.id.modeToggleButton);
        rotateButton = findViewById(R.id.rotateButton);
        Button velocity1Button = findViewById(R.id.velocity1Button);
        Button velocity2Button = findViewById(R.id.velocity2Button);

        rotateButton.setOnClickListener(v -> {
            String angleText = angleEditText.getText().toString().trim();
            if (!angleText.isEmpty()) {
                int angle = Integer.parseInt(angleText);
                sendRotation(angle);
            } else {
                Toast.makeText(MainActivity.this, "Please enter rotation angle", Toast.LENGTH_SHORT).show();
            }
        });

        modeToggleButton.setOnClickListener(v -> {
            boolean autoMode = modeToggleButton.isChecked();
            angleEditText.setEnabled(!autoMode); // Enable/disable EditText based on mode
            rotateButton.setEnabled(!autoMode); // Enable/disable Rotate button based on mode
            if (autoMode) {
                angleEditText.setText(""); // Clear EditText when auto mode is selected
                autoRotation();
            } else {
                stopAutoRotation(); // Stop auto rotation when auto mode is turned off
            }
        });

        // Set OnClickListener for velocity1Button
        velocity1Button.setOnClickListener(v -> sendVelocityRequest(BASE_URL_VEL1));

        // Set OnClickListener for velocity2Button
        velocity2Button.setOnClickListener(v -> sendVelocityRequest(BASE_URL_VEL2));

        // Start receiving data from Arduino and update the graph
        LineChart lineChart = findViewById(R.id.lineChart);
        LineChart lineChart1 = findViewById(R.id.lineChart1); // New LineChart for sensor 2
        LineChart lineChart2 = findViewById(R.id.lineChart2); // New LineChart for encoder
        DataReceiverTask dataReceiverTask = new DataReceiverTask(lineChart, lineChart1, lineChart2);
        dataReceiverTask.fetchDataFromServer(); // Start fetching data
    }

    private void restartApp() {
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    private void sendRotation(final int angle) {
        try {
            String encodedAngle = URLEncoder.encode(String.valueOf(angle), "UTF-8");
            String url = BASE_URL_ANGLE + encodedAngle;

            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                try {
                    URL requestUrl = new URL(url);
                    HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
                    connection.setRequestMethod("GET");

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = in.readLine()) != null) {
                            response.append(line);
                        }
                        in.close();
                        String responseBody = response.toString();
                        // Log the response
                        Log.d("Response", "Response: " + responseBody);

                        // Handle response
                        if (responseBody.equals("success")) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Rotation command sent successfully", Toast.LENGTH_SHORT).show());
                        } else {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Unexpected response content: " + responseBody, Toast.LENGTH_SHORT).show());
                        }
                    } else {
                        // Handle HTTP error
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to send rotation command. HTTP error code: " + responseCode, Toast.LENGTH_SHORT).show());
                    }
                    // Close connection
                    connection.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                    // Handle IO error
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to send rotation command: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });

            // Shutdown the executor after completion
            executor.shutdown();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private ScheduledExecutorService executor;

    // Use a single executor for handling all requests
    private ExecutorService requestExecutor;


    private void autoRotation() {
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            sendAutoRotationRequest();
            executor.shutdown(); // Shutdown the executor after the first execution
        }, 0, TimeUnit.MILLISECONDS); // Execute immediately
    }

    private void sendAutoRotationRequest() {
        // Use the requestExecutor to handle the request
        if (requestExecutor == null || requestExecutor.isShutdown()) {
            requestExecutor = Executors.newFixedThreadPool(5); // Adjust the number of threads as needed
        }

        // Execute the request asynchronously
        requestExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(BASE_URL_AUTO);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                // Set timeout and retry policy
                int socketTimeout = 2000; // 2 seconds
                connection.setConnectTimeout(socketTimeout);
                connection.setReadTimeout(socketTimeout);

                // Get response
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // Handle response
                if (response.toString().equals("success")) {
                    Log.d(TAG, "Auto mode activated successfully");
                    // You can update UI or take other actions here
                } else {
                    Log.e(TAG, "Unexpected response content: " + response);
                    // Handle unexpected response content
                }
                // Close connection
                connection.disconnect();
            } catch (IOException e) {
                // Handle error
                Log.e(TAG, "Failed to activate auto mode: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect(); // Close the connection
                }
            }
        });
    }

    private void stopAutoRotation() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow(); // Shutdown the executor immediately
        }
        // Shutdown the requestExecutor when stopping auto rotation
        if (requestExecutor != null && !requestExecutor.isShutdown()) {
            requestExecutor.shutdownNow();
        }
        // Send GET request to switch to manual mode
        sendManualModeRequest();
    }

    private void sendManualModeRequest() {
        // Create a new executor to handle the request
        ExecutorService manualModeExecutor = Executors.newSingleThreadExecutor();
        manualModeExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(BASE_URL_MANUAL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "Switched to manual mode successfully");
                    // You can update UI or take other actions here
                } else {
                    Log.e(TAG, "Failed to switch to manual mode. HTTP error code: " + responseCode);
                    // Handle HTTP error
                }
                // Close connection
                connection.disconnect();
            } catch (IOException e) {
                Log.e(TAG, "Failed to switch to manual mode: " + e.getMessage());
                // Handle error
            } finally {
                if (connection != null) {
                    connection.disconnect(); // Close the connection
                }
            }
        });

        // Shutdown the executor after completion
        manualModeExecutor.shutdown();
    }

    protected void onDestroy() {
        super.onDestroy();
        stopAutoRotation();

        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    private void sendVelocityRequest(String baseUrl) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                URL requestUrl = new URL(baseUrl);
                HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();
                    String responseBody = response.toString();
                    // Log the response
                    Log.d("Response", "Response: " + responseBody);

                    // Handle response
                    if (responseBody.equals("success")) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Velocity command sent successfully", Toast.LENGTH_SHORT).show());
                    } else {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Unexpected response content: " + responseBody, Toast.LENGTH_SHORT).show());
                    }
                } else {
                    // Handle HTTP error
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to send velocity command. HTTP error code: " + responseCode, Toast.LENGTH_SHORT).show());
                }
                // Close connection
                connection.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
                // Handle IO error
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to send velocity command: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });

        // Shutdown the executor after completion
        executor.shutdown();
    }
    public class DataReceiverTask {
        private final List<Float> rawValues = new ArrayList<>();
        private final List<Float> rawValues2 = new ArrayList<>(); // Raw values for sensor 2
        private final List<Float> rawValues3 = new ArrayList<>(); // Raw values for encoder
        private final List<Long> timestamps = new ArrayList<>();
        private final List<Long> timestamps2 = new ArrayList<>(); // Timestamps for sensor 2
        private final List<Long> timestamps3 = new ArrayList<>(); // Timestamps for encoder
        private final LineChart lineChart;
        private final LineChart lineChart1; // LineChart for sensor 2
        private final LineChart lineChart2; // LineChart for rotatory encoder

        public DataReceiverTask(LineChart lineChart, LineChart lineChart1, LineChart lineChart2) {
            this.lineChart = lineChart;
            this.lineChart1 = lineChart1;
            this.lineChart2 = lineChart2;
        }

        public void fetchDataFromServer() {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> future = executor.submit(new Callable<Void>() {
                @Override
                public Void call() {
                    try {
                        // URL of your Arduino server
                        while(true){
                            // Send request to fetch sensor data from first sensor
                            fetchDataFromSensor(BASE_URL_SENSOR, true, false); // Fetch data from sensor 1
                            // Optionally, you can send requests to fetch data from other sensors if needed
                            fetchDataFromSensor(BASE_URL_SENSOR_2, false, false); // Fetch data from sensor 2
                            // Send request to fetch data from rotatory encoder (codif)
                            fetchDataFromSensor(BASE_URL_CODIF, false, true); // Fetch data from rotatory encoder
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            });
            // Shutdown executor after task completion
            executor.shutdown();
        }

        // Method to fetch data from a specific sensor endpoint
        private void fetchDataFromSensor(String url, boolean isSensor1, boolean isCodif) throws IOException {
            // Open connection
            URL sensorUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) sensorUrl.openConnection();
            connection.setRequestMethod("GET");

            // Get response
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;

            if ((line = reader.readLine()) != null) {
                response.append(line);
                Log.d(TAG, "Response: " + response);
                // Parse sensor reading from response
                float sensorReading = Float.parseFloat(response.toString());

                // Process sensor reading
                long currentTime = System.currentTimeMillis();
                processData(currentTime, sensorReading, isSensor1, isCodif); // Pass the information about which sensor the data is from
            }
            reader.close();

            // Close connection
            connection.disconnect();
        }



        private void processData(long time, float value, boolean isSensor1, boolean isCodif) {
            if (isCodif){
                rawValues3.add(value);
                timestamps3.add(time);
                if (rawValues3.size() > 5) {
                    rawValues3.remove(0);
                    timestamps3.remove(0);
                }
                updateGraph3();
            } else {
                if (isSensor1) {
                    rawValues.add(value);
                    timestamps.add(time);
                    if (rawValues.size() > 5) {
                        rawValues.remove(0);
                        timestamps.remove(0);
                    }
                    updateGraph();
                } else {
                    rawValues2.add(value);
                    timestamps2.add(time);
                    if (rawValues2.size() > 5) {
                        rawValues2.remove(0);
                        timestamps2.remove(0);
                    }
                    updateGraph2();
                }
            }
        }

        private void customizeChart(LineChart chart, boolean isRotatoryEncoder) {
            // Customize vertical axis range
            YAxis yAxis = chart.getAxisLeft();
            if (isRotatoryEncoder) {
                yAxis.setAxisMinimum(-180f);
                yAxis.setAxisMaximum(180f); // Change y-axis limits to 0 to 360 for Rotatory Encoder
            } else {
                yAxis.setAxisMinimum(0f);
                yAxis.setAxisMaximum(20f); // Default y-axis limits
            }

            // Customize X-axis appearance
            XAxis xAxis = chart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM); // Position X-axis at the bottom
            xAxis.setDrawAxisLine(true); // Enable drawing X-axis line
            xAxis.setAxisLineColor(Color.BLACK); // Set color of the X-axis line
            xAxis.setDrawGridLines(false); // Hide X-axis grid lines

            // Customize chart appearance
            chart.getDescription().setEnabled(false); // Disable chart description
            chart.getAxisRight().setEnabled(false); // Disable right y-axis
            chart.getAxisLeft().setDrawGridLines(false); // Hide left y-axis grid lines
            chart.getLegend().setEnabled(false); // Disable legend
        }

        private void updateGraph(LineChart chart, ArrayList<Float> rawValues, String dataSetLabel, boolean isRotatoryEncoder) {
            ArrayList<Entry> entries = new ArrayList<>();

            // Populate entries with the values
            for (int i = 0; i < rawValues.size(); i++) {
                entries.add(new Entry(i, rawValues.get(i))); // Use index as x-value
            }

            LineDataSet dataSet = new LineDataSet(entries, dataSetLabel);

            // Customize data points
            dataSet.setDrawCircles(true);
            dataSet.setCircleColor(Color.BLUE);
            dataSet.setCircleRadius(5f);
            dataSet.setValueTextSize(12f);
            dataSet.setValueTextColor(Color.BLACK);
            dataSet.setDrawValues(true); // Enable displaying values on data points
            dataSet.setValueFormatter(new DefaultValueFormatter(2)); // Format value to two decimal places

            // Customize line appearance
            dataSet.setColor(Color.BLUE);
            dataSet.setLineWidth(2f);
            dataSet.setDrawFilled(true); // Fill the area under the line

            LineData lineData = new LineData(dataSet);
            chart.setData(lineData);
            chart.invalidate(); // Refresh the chart

            // Customize the chart based on whether it's for Rotatory Encoder or not
            customizeChart(chart, isRotatoryEncoder);
        }

        private void updateGraph() {
            updateGraph(lineChart, (ArrayList<Float>) rawValues, "Averaged Readings", false);
        }

        private void updateGraph2() {
            updateGraph(lineChart1, (ArrayList<Float>) rawValues2, "Sensor 2 Readings", false);
        }

        private void updateGraph3() {
            updateGraph(lineChart2, (ArrayList<Float>) rawValues3, "Rotatory Encoder Readings", true);
        }



    }

}
