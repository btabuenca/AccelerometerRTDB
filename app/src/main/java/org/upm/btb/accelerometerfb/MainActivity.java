package org.upm.btb.accelerometerfb;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity implements SensorEventListener {

	private static final String TAG = "btb";

	FirebaseDatabase database;

	private SensorManager sensorManager;
	private Sensor accelerometer;

	private float lastX, lastY, lastZ;

	private float deltaXMax = 0;
	private float deltaYMax = 0;
	private float deltaZMax = 0;

	private float deltaX = 0;
	private float deltaY = 0;
	private float deltaZ = 0;

	private float vibrateThreshold = 0;
	public Vibrator v;

	private TextView currentX, currentY, currentZ, maxX, maxY, maxZ;
	private Button butReset, butSaveMax, butSaveCurrent, butDelete;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initializeViews();

		database = FirebaseDatabase.getInstance();
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

		if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
			Log.e(TAG, "Success! we have an accelerometer");

			accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
			vibrateThreshold = accelerometer.getMaximumRange() / 2;

		} else {
			// failed, we dont have an accelerometer!
			Log.e(TAG, "Failed. Unfortunately we do not have an accelerometer");
		}
		
		// Initialize vibration
		v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

	}

	public void initializeViews() {
		currentX = (TextView) findViewById(R.id.currentX);
		currentY = (TextView) findViewById(R.id.currentY);
		currentZ = (TextView) findViewById(R.id.currentZ);

		maxX = (TextView) findViewById(R.id.maxX);
		maxY = (TextView) findViewById(R.id.maxY);
		maxZ = (TextView) findViewById(R.id.maxZ);

		// Reset button listener
		butReset = (Button) findViewById(R.id.buttonReset);
		butReset.setOnClickListener(new View.OnClickListener(){
			public void onClick(View view) {
				deltaXMax = 0;
				deltaYMax = 0;
				deltaZMax = 0;

				deltaX = 0;
				deltaY = 0;
				deltaZ = 0;

				// display empty values
				displayReset();

			}
		});

		// Save max button listener
		butSaveMax = (Button) findViewById(R.id.buttonSaveMax);
		butSaveMax.setOnClickListener(new View.OnClickListener(){
			public void onClick(View view) {

				// update values on FireBase realtime database
				// See documentation on how to save data in realtime database
				// https://firebase.google.com/docs/database/admin/save-data
				DatabaseReference myRef = database.getReference("valoresmaximos");
				Map<String,Object> m = new HashMap<>();
				m.put("x", deltaXMax);
				m.put("y", deltaYMax);
				m.put("z", deltaZMax);
				myRef.setValue(m);


			}
		});


		butSaveCurrent = (Button) findViewById(R.id.buttonSaveCurrent);
		butSaveCurrent.setOnClickListener(new View.OnClickListener(){
			public void onClick(View view) {
				DatabaseReference myRef = database.getReference();
				Date d = new Date();
				SimpleDateFormat sdfD = new SimpleDateFormat("dd");
				SimpleDateFormat sdfM = new SimpleDateFormat("MMMM");
				SimpleDateFormat sdfY = new SimpleDateFormat("yyyy");
				SimpleDateFormat sdfHMS = new SimpleDateFormat("HH:mm:ss");

				//String strDate = sm.format(d);
				// ref.child("timestamps").push().setValue(strDate);
				Map<String,Object> m = new HashMap<>();
				m.put("dia", sdfD.format(d));
				m.put("mes", sdfM.format(d));
				m.put("anio", sdfY.format(d));
				m.put("hora", sdfHMS.format(d));
				m.put("timestamp", d.getTime());

				myRef.child("timestamps").push().setValue(m);

			}
		});


		butDelete = (Button) findViewById(R.id.buttonDeleteItem);
		butDelete.setOnClickListener(new View.OnClickListener(){
			public void onClick(View view) {

				Date d = new Date();
				SimpleDateFormat sdfM = new SimpleDateFormat("MMMM");
				String sFilter = sdfM.format(d).toString();

				Log.i(TAG, "About to remove item in collection timestamps in the current month "+sFilter);

				DatabaseReference ref = database.getReference();
				ref = FirebaseDatabase.getInstance().getReference();
				Query query = ref.child("timestamps").orderByChild("mes").equalTo(sFilter);
				query.addListenerForSingleValueEvent(new ValueEventListener() {
					@Override
					public void onDataChange(DataSnapshot dataSnapshot) {
						for (DataSnapshot dSnap: dataSnapshot.getChildren()) {
							dSnap.getRef().removeValue();
						}
					}

					@Override
					public void onCancelled(DatabaseError databaseError) {
						Log.e(TAG, "onCancelled", databaseError.toException());
					}
				});

			}
		});

	}

	//onResume() register the accelerometer to start listening to events
	protected void onResume() {
		super.onResume();
		sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
	}

	//onPause() unregister the accelerometer to stop listening to events
	protected void onPause() {
		super.onPause();
		sensorManager.unregisterListener(this);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		
	}

	@Override
	public void onSensorChanged(SensorEvent event) {

		// clean current values
		displayCleanValues();
		// display the current x,y,z accelerometer values
		displayCurrentValues();
		// display the max x,y,z accelerometer values when applicable
		displayMaxValues();

		// get the change of the x,y,z values of the accelerometer
		deltaX = Math.abs(lastX - event.values[0]);
		deltaY = Math.abs(lastY - event.values[1]);
		deltaZ = Math.abs(lastZ - event.values[2]);

		// if the change is below 2, it is just plain noise. Discard it!
		if (deltaX < 2) deltaX = 0;
		if (deltaY < 2) deltaY = 0;
		if (deltaZ < 2) deltaZ = 0;

		// set the last know values of x,y,z
		lastX = event.values[0];
		lastY = event.values[1];
		lastZ = event.values[2];

		vibrate();

	}

	// if the change in the accelerometer value is big enough, then vibrate!
	// our threshold is MaxValue/2
	public void vibrate() {
		if ((deltaX > vibrateThreshold) || (deltaY > vibrateThreshold) || (deltaZ > vibrateThreshold)) {
			v.vibrate(50);
		}
	}

	public void displayCleanValues() {
		currentX.setText("0.0");
		currentY.setText("0.0");
		currentZ.setText("0.0");
	}

	public void displayReset() {
		currentX.setText("0.0");
		currentY.setText("0.0");
		currentZ.setText("0.0");

		maxX.setText("0.0");
		maxY.setText("0.0");
		maxZ.setText("0.0");
	}

	// display the current x,y,z accelerometer values
	public void displayCurrentValues() {
		currentX.setText(Float.toString(deltaX));
		currentY.setText(Float.toString(deltaY));
		currentZ.setText(Float.toString(deltaZ));
	}

	// display the max x,y,z accelerometer values
	public void displayMaxValues() {
		if (deltaX > deltaXMax) {
			deltaXMax = deltaX;
			maxX.setText(Float.toString(deltaXMax));
		}
		if (deltaY > deltaYMax) {
			deltaYMax = deltaY;
			maxY.setText(Float.toString(deltaYMax));
		}
		if (deltaZ > deltaZMax) {
			deltaZMax = deltaZ;
			maxZ.setText(Float.toString(deltaZMax));
		}

	}

}