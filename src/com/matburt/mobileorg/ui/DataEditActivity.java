package com.matburt.mobileorg.ui;

import java.io.IOException;
import java.io.StringWriter;
import java.util.regex.Matcher;

import org.kvj.bravo7.ControllerConnector;
import org.kvj.bravo7.ControllerConnector.ControllerReceiver;
import org.kvj.bravo7.SuperActivity;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import com.markupartist.android.widget.ActionBar;
import com.matburt.mobileorg.App;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.service.DataController;
import com.matburt.mobileorg.service.DataService;
import com.matburt.mobileorg.service.DataWriter;
import com.matburt.mobileorg.service.NoteNG;
import com.matburt.mobileorg.service.OrgNGParser;

public class DataEditActivity extends FragmentActivity implements
		ControllerReceiver<DataController> {

	public static final String DECRYPT_AND_RETURN = "org.thialfihar.android.apg.intent.DECRYPT_AND_RETURN";
	public static final String ENCRYPT_AND_RETURN = "org.thialfihar.android.apg.intent.ENCRYPT_AND_RETURN";
	public static final int DECRYPT_MESSAGE = 103;
	public static final int ENCRYPT_MESSAGE = 104;

	public static final String EXTRA_TEXT = "text";
	public static final String EXTRA_DATA = "data";
	public static final String EXTRA_DECRYPTED_MESSAGE = "decryptedMessage";
	public static final String EXTRA_ENCRYPTED_MESSAGE = "encryptedMessage";

	// private static final String mApgPackageName =
	// "org.thialfihar.android.apg";

	private static final String TAG = "DataEdit";
	private static final String EXTRA_KEY_IDS = "encryptionKeyIds";
	EditText edit = null;
	ImageButton togglePanel = null;
	Button save = null;
	DataEditOptionsPanel panel = null;
	DataController controller = null;
	ControllerConnector<App, DataController, DataService> conn = null;
	Bundle data = null;
	int textIndent = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (android.os.Build.VERSION.SDK_INT < 11) {// <3.0
			requestWindowFeature(Window.FEATURE_NO_TITLE);
		}
		// Log.i(TAG,
		// "onCreate: "+getResources().getConfiguration().screenLayout+", "+getResources().getDisplayMetrics().densityDpi);
		setContentView(R.layout.data_edit);
		if (null != findViewById(R.id.actionbar)) {
			ActionBar bar = (ActionBar) findViewById(R.id.actionbar);
			bar.setHomeLogo(R.drawable.logo_72);
			getMenuInflater().inflate(R.menu.editor_menu, bar.asMenu());
		}
		edit = (EditText) findViewById(R.id.data_edit_text);
		togglePanel = (ImageButton) findViewById(R.id.data_edit_button);
		panel = (DataEditOptionsPanel) getSupportFragmentManager()
				.findFragmentById(R.id.data_edit_panel);
		if (null == savedInstanceState) {
			data = getIntent().getExtras();
			if (null == data) {
				data = new Bundle();
				data.putString("type", "title");
			}
		} else {
			data = savedInstanceState;
		}
		togglePanel.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (panel.getView().getVisibility() == View.VISIBLE) {
					panel.getView().setVisibility(View.GONE);
				} else {
					panel.getView().setVisibility(View.VISIBLE);
				}
			}
		});
	}

	@Override
	protected void onStart() {
		super.onStart();
		conn = new ControllerConnector<App, DataController, DataService>(this,
				this);
		conn.connectController(DataService.class);
		edit.requestFocusFromTouch();
	}

	@Override
	protected void onStop() {
		super.onStop();
		conn.disconnectController();
	}

	private boolean startEncrypt(String text) {
		Intent intent = new Intent(ENCRYPT_AND_RETURN);
		intent.setType("text/plain");
		try {
			intent.putExtra(EXTRA_TEXT, text);
			String cryptKey = App.getInstance().getStringPreference(
					R.string.cryptKey, R.string.cryptKeyDefault);
			if (!"".equals(cryptKey)) {
				long keyLong = Long.parseLong(cryptKey, 16);
				intent.putExtra(EXTRA_KEY_IDS, new long[] { keyLong });
				// SuperActivity.notifyUser(this, "Key: " + cryptKey + ", "
				// + keyLong);
			}
			startActivityForResult(intent, ENCRYPT_MESSAGE);
			return true;
		} catch (ActivityNotFoundException e) {
			Log.e("MobileOrg", "Error: " + e.getMessage()
					+ " while launching APG intent");
		}
		return false;
	}

	private boolean startDecrypt() {
		String text = data.getString("text");
		if (null == text) {
			return false;
		}
		if (!text.startsWith("-----BEGIN")) {
			return false;
		}
		Intent intent = new Intent(DECRYPT_AND_RETURN);
		intent.setType("text/plain");
		try {
			Log.i(TAG, "Decrypt: " + data.getString("text"));
			intent.putExtra(EXTRA_TEXT, data.getString("text"));
			startActivityForResult(intent, DECRYPT_MESSAGE);
			return true;
		} catch (ActivityNotFoundException e) {
			Log.e("MobileOrg", "Error: " + e.getMessage()
					+ " while launching APG intent");
		}
		return false;
	}

	@Override
	public void onController(DataController controller) {
		if (null != this.controller) {
			return;
		}
		controller.setInEdit(true);
		// Log.i(TAG, "Restoring editor state here " + data.getString("text"));
		if (!"title".equals(data.getString("type"))) {
			togglePanel.setVisibility(View.GONE);
			edit.setSingleLine(false);
		} else {
			edit.setSingleLine(true);
		}
		panel.getView().setVisibility(
				data.getBoolean("panel", false) ? View.VISIBLE : View.GONE);
		panel.loadData(controller, data);
		if (null == this.controller) {
			this.controller = controller;
			if (data.getBoolean("crypt", false)
					&& !data.getBoolean("decrypted", false)) {
				data.putBoolean("decrypted", true);
				if (startDecrypt()) {
					return;
				}
			}
		}
		edit.setText(data.getString("text"));
		edit.setSelection(edit.getText().length());
	}

	@Override
	protected void onActivityResult(int req, int res, Intent intent) {
		super.onActivityResult(req, res, intent);
		if (res != Activity.RESULT_OK) {
			return;
		}
		if (DECRYPT_MESSAGE == req) {
			String text = intent.getStringExtra(EXTRA_DECRYPTED_MESSAGE);
			if (null == text) {
				edit.setText("");
				return;
			}
			String[] lines = text.split("\\n");
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < lines.length; i++) {
				if (i == 0) {
					int spaces = 0;
					while (spaces < lines[i].length()
							&& lines[i].charAt(spaces) == ' ') {
						spaces++;
					}
					textIndent = spaces;
				} else {
					sb.append('\n');
				}
				sb.append(lines[i].trim());
			}
			edit.setText(sb.toString());
			edit.setSelection(edit.getText().length());
		}
		if (ENCRYPT_MESSAGE == req) {
			Log.i(TAG,
					"Encrypted; "
							+ intent.getStringExtra(EXTRA_ENCRYPTED_MESSAGE));
			int noteID = data.getInt("noteID", -1);
			String error = editEntry(noteID, "*** Encrypted (modified) ***",
					intent.getStringExtra(EXTRA_ENCRYPTED_MESSAGE));
			if (null != error) {
				SuperActivity.notifyUser(this, error);
				return;
			}
			controller.notifyChangesHaveBeenMade();
			setResult(RESULT_OK);
			finish();
		}

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (null != this.controller) {
			controller.setInEdit(false);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Log.i(TAG, "Saving editor state here " + edit.getText());
		data.putBoolean("panel",
				panel.getView().getVisibility() == View.VISIBLE);
		data.putString("text", edit.getText().toString().trim());
		outState.putAll(data);
		panel.saveData(outState);
	}

	private String createNewEntry(String text) {
		NoteNG note = new NoteNG();
		note.level = 1;
		String changeBody = null;
		int parentID = data.getInt("parentID", -1);
		note.title = text;
		Log.i(TAG, "Create new entry " + parentID + ", " + text);
		NoteNG change = null;
		if ("title".equals(data.getString("type"))) {
			// This is outline - only capture
			note.priority = data.getString("priority");
			note.tags = data.getString("tags");
			note.todo = data.getString("todo");
			note.type = NoteNG.TYPE_OUTLINE;
			note.fileID = -1;
			if (null == controller.createNewNote(note)) {
				return "DB error";
			}
			controller.addChange(note.id, "data", null, null);
			return null;
		}
		if (-1 == parentID) {
			// Should be always
			return "Invalid entry";
		}

		NoteNG parent = controller.findNoteByID(parentID);
		change = controller.findNoteByID(data.getInt("changeID", -1));
		if (null == parent || null == change) {
			return "Invalid entry";
		}
		note.parentID = parent.id;
		note.fileID = parent.fileID;
		// Save body
		StringWriter sw = new StringWriter();
		DataWriter dw = new DataWriter(controller);
		try {
			dw.writeOutlineWithChildren(change, sw, false);
			changeBody = sw.toString();
		} catch (IOException e) {
			e.printStackTrace();
			return "Error writing";
		}
		if ("text".equals(data.getString("type"))) {
			// New text
			Matcher m = OrgNGParser.listPattern.matcher(text);
			note.type = NoteNG.TYPE_TEXT;
			if (m.find()) {
				// Convert to list
				note.before = m.group(2);
				note.title = m.group(4);
				note.type = NoteNG.TYPE_SUBLIST;
			}
		}
		if ("sublist".equals(data.getString("type"))) {
			// New text
			Matcher m = OrgNGParser.listPattern.matcher(text);
			note.type = NoteNG.TYPE_SUBLIST;
			if (m.find()) {
				// Convert to list
				note.before = m.group(2);
				note.title = m.group(4);
			} else {
				note.before = data.getString("before");
			}
		}
		note.raw = note.title;
		if (null == controller.addData(note)) {
			return "DB error";
		}
		Log.i(TAG, "Updating body...");
		sw = new StringWriter();
		try {
			dw.writeOutlineWithChildren(change, sw, false);
			controller.addChange(change.id, "body", changeBody, sw.toString());
		} catch (IOException e) {
			e.printStackTrace();
			return "Error writing";
		}
		return null;
	}

	private boolean writeChange(String field, String type, String oldValue,
			String newValue, NoteNG note) {
		if (!stringChanged(oldValue, newValue)) {
			return true;
		}
		if (!controller.updateData(note, field, newValue)) {
			return false;
		}
		if (null != note.noteID
				&& !controller.updateData("original_id", note.noteID, field,
						newValue)) {
			return false;
		}
		if (!controller.addChange(note.id, type, oldValue, newValue)) {
			return false;
		}
		return true;
	}

	private boolean stringChanged(String s1, String s2) {
		if (null == s1 && null == s2) {
			return false;
		}
		if (null != s1 && null != s2) {
			return !s1.trim().equals(s2.trim());
		}
		return true;
	}

	private String editEntry(int noteID, String text, String raw) {
		NoteNG change = controller.findNoteByID(data.getInt("changeID", -1));
		NoteNG note = controller.findNoteByID(noteID);
		if (null == note || null == change) {
			Log.w(TAG, "Invalid entry: " + note + ", " + change);
			return "Invalid entry";
		}
		if ("title".equals(data.getString("type"))) {
			if (!writeChange("title", "heading", note.title, text, change)) {
				return "DB error";
			}
			if (!writeChange("todo", "todo", note.todo, data.getString("todo"),
					change)) {
				return "DB error";
			}
			if (!writeChange("priority", "priority", note.priority,
					data.getString("priority"), change)) {
				return "DB error";
			}
			if (!writeChange("tags", "tags", note.tags, data.getString("tags"),
					change)) {
				return "DB error";
			}
			return null;
		}
		String changeBody = null;
		StringWriter sw = new StringWriter();
		DataWriter dw = new DataWriter(controller);
		try {
			dw.writeOutlineWithChildren(change, sw, false);
			changeBody = sw.toString();
		} catch (IOException e) {
			e.printStackTrace();
			return "Error writing";
		}
		boolean updateType = false;
		boolean updateBefore = false;
		if ("text".equals(data.getString("type"))) {
			// New text
			Matcher m = OrgNGParser.listPattern.matcher(text);
			note.type = NoteNG.TYPE_TEXT;
			if (m.find()) {
				// Convert to list
				note.before = m.group(2);
				updateBefore = true;
				note.title = m.group(4);
				note.type = NoteNG.TYPE_SUBLIST;
				updateType = true;
				Log.i(TAG, "Edit text => sublist");
			} else {
				note.title = text;
				Log.i(TAG, "Edit text => text");
			}
		}
		if ("sublist".equals(data.getString("type"))) {
			// New text
			Matcher m = OrgNGParser.listPattern.matcher(text);
			note.type = NoteNG.TYPE_SUBLIST;
			if (m.find()) {
				Log.i(TAG, "is list: [" + m.group(2) + "] [" + m.group(4) + "]");
				// Convert to list
				note.before = m.group(2);
				note.title = m.group(4);
			} else {
				Log.i(TAG, "not is list: [" + text + "]");
				note.before = data.getString("before");
				note.title = text;
			}
			updateBefore = true;
		}
		note.raw = note.title;
		if (null != raw) {
			note.raw = raw;
		}
		if (!controller.updateData(note, "title", note.title)
				|| !controller.updateData(note, "raw", note.raw)) {
			return "DB error";
		}
		if (updateType && !controller.updateData(note, "type", note.type)) {
			return "DB error";
		}
		if (updateBefore && !controller.updateData(note, "before", note.before)) {
			return "DB error";
		}
		Log.i(TAG, "Updating body... " + text);
		sw = new StringWriter();
		try {
			dw.writeOutlineWithChildren(change, sw, false);
			controller.addChange(change.id, "body", changeBody, sw.toString());
		} catch (IOException e) {
			e.printStackTrace();
			return "Error writing";
		}
		return null;
	}

	private void onSave() {
		panel.saveData(data);
		String text = edit.getText().toString().trim();
		Log.i(TAG, "onSave [" + text + "] [" + edit.getText() + "]");
		if ("".equals(text)) {
			SuperActivity.notifyUser(this, "Text is empty");
			return;
		}
		int noteID = data.getInt("noteID", -1);
		String error = null;
		if (data.getBoolean("crypt", false)) {
			String[] lines = text.split("\\n");
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < lines.length; i++) {
				if (i > 0) {
					sb.append('\n');
				}
				for (int j = 0; j < textIndent; j++) {
					sb.append(' ');
				}
				sb.append(lines[i].trim());
			}
			if (startEncrypt(sb.toString())) {
				return;
			}
		}
		if (-1 == noteID) {
			// New note - create
			error = createNewEntry(text);
		} else {
			error = editEntry(noteID, text, null);
		}
		if (null != error) {
			SuperActivity.notifyUser(this, error);
			return;
		}
		controller.notifyChangesHaveBeenMade();
		setResult(RESULT_OK);
		finish();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.editor_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_save:
			onSave();
			break;
		}
		return true;
	}
}
