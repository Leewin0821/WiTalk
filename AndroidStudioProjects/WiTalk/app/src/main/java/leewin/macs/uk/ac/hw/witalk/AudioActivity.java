package leewin.macs.uk.ac.hw.witalk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

import leewin.macs.uk.ac.hw.witalk.R;

public class AudioActivity extends Activity {
    private AudioManager audioManager = null;
    private AudioRecord audioRecord = null;
    private AudioTrack audioTrack = null;
    private boolean isSpeaker = false;
    private boolean isPlaying = true;
    private boolean isRun = true;
    private boolean micOn = true;
    private MulticastSocket multicastSocket = null;
    private static String BROADCAST_IP="224.0.0.1";
    private static int BROADCAST_PORT = 8988;
    private InetAddress inetAddress = null;
//    private Speex speex = new Speex();
    private byte[] processedData = new byte[1024];
    private short[] rawdata = new short[1024];
    private String TAG = "AudioActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio);
        try{
            inetAddress = InetAddress.getByName(BROADCAST_IP);
            multicastSocket = new MulticastSocket(BROADCAST_PORT);
            multicastSocket.setTimeToLive(1);
            multicastSocket.joinGroup(inetAddress);
        }catch (UnknownHostException uhe){
            Log.d(TAG, "Unknown host in Audio Activity");
        }catch (IOException ioe){
            Log.d(TAG,"IO exception in Audio Activity");
        }
        setVolumeControlStream(AudioManager.MODE_IN_COMMUNICATION);
        Intent intent = getIntent();
        String player_name = intent.getStringExtra("player_name");
        TextView playerNameTextView = (TextView) findViewById(R.id.player_name);
        playerNameTextView.setText(player_name);
        init();
        recordAndSend();
        receiveAndPlay();
    }

    private void init() {
//        speex.init();
        int min = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 8000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, min);

        int maxJitter = AudioTrack.getMinBufferSize(8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioTrack = new AudioTrack(AudioManager.MODE_IN_COMMUNICATION, 8000, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxJitter, AudioTrack.MODE_STREAM);
    }

    /**
    private void recordAndSend(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                    audioRecord.startRecording();
                    while (isRun){
                        if (micOn){
                            int readBytes = 0;
                            int len;
                            readBytes = audioRecord.read(rawdata,0,1024);
                            if (readBytes>0) {
                                len = speex.encode(rawdata, 0, processedData, 0);
                                byte[] encData = new byte[len + 1];
                                DatagramPacket packet = new DatagramPacket(encData,encData.length,inetAddress,BROADCAST_PORT);
                                multicastSocket.send(packet);
                                Log.i(TAG,"Start sending packet....");
                            }
                        }
                    }
                }catch (IOException ioe){
                    Log.d(TAG,"IO Exception in sending packet.");
                }
            }
        }).start();
    }

    private void receiveAndPlay(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    byte [] encData = new byte[1024];
                    short [] decData = new short[256];
                    audioTrack.play();
                    while (isRun){
                        int dec;
                        DatagramPacket packet = new DatagramPacket(encData,encData.length,inetAddress,BROADCAST_PORT);
                        multicastSocket.receive(packet);
                        Log.i(TAG,"Received packet...");
                        dec = speex.decode(encData, decData, encData.length);
                        if (dec > 0) {
                            audioTrack.write(decData, 0, dec);
                        }
                    }
                }catch (IOException ioe){
                    Log.d(TAG,"IO Exception in receiving packet.");
                }
            }
        }).start();
    }
     **/

    private void recordAndSend(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                    audioRecord.startRecording();
                    while (isRun){
                        if (micOn){
                            byte [] bytes = new byte[1024];
                            int readBytes = 0;
                            readBytes = audioRecord.read(bytes,0,1024);
                            if (readBytes>0) {
                                DatagramPacket packet = new DatagramPacket(bytes,bytes.length,inetAddress,BROADCAST_PORT);
                                multicastSocket.send(packet);
                                Log.i(TAG,"Start sending packet....");
                            }
                        }
                    }
                }catch (IOException ioe){
                    Log.d(TAG,"IO Exception in sending packet.");
                }
            }
        }).start();
    }

    private void receiveAndPlay(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    byte [] bytes = new byte[1024];
                    audioTrack.play();
                    while (isRun){
                        DatagramPacket packet = new DatagramPacket(bytes,bytes.length,inetAddress,BROADCAST_PORT);
                        multicastSocket.receive(packet);
                        Log.i(TAG,"Received packet...");
                        audioTrack.write(bytes,0,1024);
                    }
                }catch (IOException ioe){
                    Log.d(TAG,"IO Exception in receiving packet.");
                }
            }
        }).start();
    }

    public void modeChange(View view) {
        Button modeBtn=(Button) findViewById(R.id.btn_mode);
        if (isSpeaker == true) {
            audioManager.setSpeakerphoneOn(false);
            isSpeaker = false;
            modeBtn.setText(R.string.btn_mode_call);
        } else {
            audioManager.setSpeakerphoneOn(true);
            isSpeaker = true;
            modeBtn.setText(R.string.btn_mode_speaker);
        }
    }

    public void audioPlay(View view){
        Button playBtn=(Button) findViewById(R.id.btn_play);
        if(isPlaying){
            audioRecord.stop();
            audioTrack.pause();
            isPlaying=false;
            playBtn.setText(R.string.btn_play);
        }else{
            audioRecord.startRecording();
            audioTrack.play();
            isPlaying=true;
            playBtn.setText(R.string.btn_pause);
        }
    }

    public void audioStop(View view){
        if (isRun){
            isRun = false;
            multicastSocket.disconnect();
            multicastSocket.close();
            startActivity(new Intent(this, MainActivity.class));
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.audio, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
