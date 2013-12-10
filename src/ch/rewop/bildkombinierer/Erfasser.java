package ch.rewop.bildkombinierer;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;

public class Erfasser extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_erfasser);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.erfasser, menu);
		return true;
	}

}
