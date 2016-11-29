package com.example.agorn.nfc_test;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.util.Arrays;

import static android.R.attr.button;
import static android.R.attr.privateImeOptions;

public class MainActivity extends AppCompatActivity {
    private Button button_read_send_message;
    private Button button_format;
    private EditText message_send;
    private TextView message_send_view;

    /* NTAG NDEF Formating constants */
    private final static byte USER_DATA_PAGE_OFFSET = 4;
    private final static byte CAPABILITY_CONTAINER_OFFSET = 3;
    private final static byte[] EMPTY_PAGE = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
    private final static byte[] EMPTY_NDEF_MESSAGE = {(byte) 0x03, (byte) 0x00, (byte) 0xfe, (byte) 0x00};

    private PendingIntent mPendingIntent;
    private NfcAdapter mNfcAdapter;
    private boolean isReadyToFormat = false;
    private Tag tag;

    private EditText inputText;
    private TextView textStatus;
    private final String TAG = "mytag";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        button_read_send_message = (Button) findViewById(R.id.buttonreadmessagesend);
        button_format = (Button) findViewById(R.id.buttonformat);
        message_send = (EditText) findViewById(R.id.messagesend);
        message_send_view = (TextView) findViewById(R.id.messagesendview);
        textStatus = (TextView) findViewById(R.id.textstatus);
        prepareNfcAdapter();
        button_format.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
        // Update status text
                setStatus("READY TO FORMAT\nPASS A TAG");

        //Allow to format when a tag is discovered */
                isReadyToFormat = true;
            }
        });
    }

    public void onClickReadMessageSend(View view){
        String message;
        message = message_send.getText().toString();
        message_send_view.setText(message);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mNfcAdapter.enableForegroundDispatch(this, mPendingIntent,
                new IntentFilter[]{
                        new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                        new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
                }, null);
    }
    @Override
    protected void onPause() {
        super.onPause();
        mNfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (intent.getAction().equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {
            Log.d(TAG, "TAG DISCOVERED received");
            if (isReadyToFormat) {
                isReadyToFormat = false;
                tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

/* Launch async task to process NTAG21x formatting and writing */
                Tag localTag = tag;
                formatNtag(localTag);
                //new LocalTask().execute();
            } else
                Log.d(TAG, "Press the button before the tag discovering");
        }
    }
    /**
     * This method is used to initialize Nfc
     */
    private void prepareNfcAdapter() {
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mPendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
    /**
     * This async task is used to launch tag formatting and writing
     */
    /*private class LocalTask extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String params) {
            Tag localTag = tag;

        //Update status text
            publishProgress(0);

        // Erase, format and write data on NTAG21x
            if (formatNtag(localTag)) {
                Log.d(TAG, "Format TAG : SUCCESS");
                publishProgress(1);
            } else {
                Log.d(TAG, "Format TAG : FAIL");
                publishProgress(2);
            }
            return null;
        }

        // Update status text
        protected void onProgressUpdate(Integer… values) {
            switch (values[0]) {
                case 0:
                    setStatus("WRITING…");
                    break;
                case 1:
                    setStatus("SUCCESS");
                    break;
                case 2:
                    setStatus("FAIL");
                    break;
            }

        }
    } */

    /**
     * This method is used to format manually a NTAG to NDEF format
     *
     * @param tag A Tag value coming from ACTION_TAG_DISCOVERED intent
     * @return A boolean value which describes the result of the Tag formatting
     */
    private boolean formatNtag(Tag tag) {
        boolean result = false;
        try {
            MifareUltralight muTag = MifareUltralight.get(tag);
            if (muTag != null) {
                try {
                    Log.d(TAG, "Start to format");
                    muTag.connect();
                    byte[] cc = muTag.readPages(CAPABILITY_CONTAINER_OFFSET); // Capability Container (page 3)
                    int maxNdefMessages = cc[2] * 8; // Byte 2 in the capability container defines the available memory size for NDEF messages divided by 8.

                    // Erase user memory
                    for (int i = USER_DATA_PAGE_OFFSET; i < (maxNdefMessages / MifareUltralight.PAGE_SIZE) + USER_DATA_PAGE_OFFSET; i++) {
                        muTag.writePage(i, EMPTY_PAGE);
                    }

                    // Write the empty NDEF Message TLV
                    muTag.writePage(4, EMPTY_NDEF_MESSAGE);

                    // Generate NDEF message from EditTextView content
                    NdefMessage message = buildMessage();

                    // Initialize the number of required page //
                    int pageNb = (message.getByteArrayLength() + 2) / 4;
                    if ((message.getByteArrayLength() + 2) % 4 > 0)
                        pageNb++;

                    // Initialize to 0 data to write in pages //
                    byte[] msg = new byte[2 + message.getByteArrayLength() + (message.getByteArrayLength() + 2) % 4];
                    Arrays.fill(msg, (byte) 0x00);
                    // Set page content in msg byte array //
                    msg[0] = (byte) 3; // NDEF defined
                    msg[1] = (byte) ((message.getByteArrayLength()) & 0x00FF); // Size of the following content

                    // Copy the message into msg byte array after the header
                    System.arraycopy(message.toByteArray(), 0, msg, 2, message.getByteArrayLength());

                    // Write pages
                    for (int j = 0; j < pageNb; j++) {
                        muTag.writePage(j + USER_DATA_PAGE_OFFSET, Arrays.copyOfRange(msg, j * 4, j * 4 + 4));
                    }

                    result = true;
                    Log.d(TAG, "Format done");
                } catch (IOException ioe) {
                    Log.e(TAG, "Exception when formatting tag", ioe);
                } finally {
                    muTag.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception when formatting tag", e);
        }

        return result;
    }
    /**
     * Build a NdefMessage
     *
     * @return A NdefMessage
     */
    private NdefMessage buildMessage() {
        // Get the message_send text.
        String temp = message_send.getText().toString();
        NdefMessage message = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            message = new NdefMessage(
                    new NdefRecord[]{
                            NdefRecord.createMime("text/plain", temp.getBytes())});
        }
        return message;
    }

    private void setStatus(String status) {
        textStatus.setText(status);
    }


}
