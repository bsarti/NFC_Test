package com.example.agorn.nfc_test;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.os.Parcelable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private Button button_format;
    private EditText message_send;

    /* NTAG NDEF Formating constants */
    private final static byte USER_DATA_PAGE_OFFSET = 4;
    private final static byte CAPABILITY_CONTAINER_OFFSET = 3;
    private final static byte[] EMPTY_PAGE = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
    private final static byte[] EMPTY_NDEF_MESSAGE = {(byte) 0x03, (byte) 0x00, (byte) 0xfe, (byte) 0x00};

    private PendingIntent mPendingIntent;
    private NfcAdapter mNfcAdapter;
    private boolean isReadyToFormat = false;
    private boolean isReadyToRead = false;
    private Tag tag;
    private TextView tag_message_read;
    private final String TAG = "mytag";
    private Button button_read_tag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        button_read_tag = (Button) findViewById(R.id.buttonreadtag);
        button_format = (Button) findViewById(R.id.buttonformat);
        message_send = (EditText) findViewById(R.id.messagesend);
        tag_message_read = (TextView) findViewById(R.id.tagmessageread);
        prepareNfcAdapter();
        button_format.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isReadyToFormat = true;
                Toast toast = Toast.makeText(getApplicationContext(),"Pass your tag to format",Toast.LENGTH_SHORT);
                toast.show();
            }
        });
        button_read_tag.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isReadyToRead = true;
                Toast toast = Toast.makeText(getApplicationContext(),"Pass your tag to read",Toast.LENGTH_SHORT);
                toast.show();
            }
        });
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
            if (isReadyToFormat & !isReadyToRead) {
                isReadyToFormat = false;
                tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                Tag localTag = tag;
                formatNtag(localTag);

            } else if (isReadyToRead & !isReadyToFormat) {
                isReadyToRead = false;
                tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                readmyTag(intent, tag);
            } else{
                Toast toast = Toast.makeText(getApplicationContext(),"Decide if you want to format or read",Toast.LENGTH_SHORT);
                toast.show();
                isReadyToRead = false;
                isReadyToFormat = false;
            }

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
        Toast toast = Toast.makeText(getApplicationContext(),"Tag formated",Toast.LENGTH_SHORT);
        toast.show();
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
    private void readmyTag(Intent intent, Tag tag){
        Ndef ndef = Ndef.get(tag);
        try{
            ndef.connect();
            Parcelable[] messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);

            if (messages != null) {
                NdefMessage[] ndefMessages = new NdefMessage[messages.length];
                for (int i = 0; i < messages.length; i++) {
                    ndefMessages[i] = (NdefMessage) messages[i];
                }
                NdefRecord record = ndefMessages[0].getRecords()[0];

                byte[] payload = record.getPayload();
                String text = new String(payload);
                tag_message_read.setText(text);
                ndef.close();
            }
        }
        catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Cannot Read From Tag.", Toast.LENGTH_LONG).show();
        }
    }
}
