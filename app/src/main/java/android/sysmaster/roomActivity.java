package android.sysmaster;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoTrack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class roomActivity extends AppCompatActivity implements SignallingClient.SignalingInterface{
    // variables
    Context context;
    Handler handler;

    PeerConnectionFactory peerConnectionFactory;
    PeerConnection localPeer;
    List<IceServer> iceServers;
    EglBase rootEglBase;
    List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();
    TextView chosenRoomTv;
    Bundle extra;
    SignallingClient sc;
    String chosenRoom;
    Button openFrontCamBtn, closeBtn, openBackCamBtn, openSoundBtn, screenshotBtn, getLocationBtn;
    SurfaceViewRenderer remoteVv;
    VideoRenderer remoteRenderer;
    DataChannel localDataChannel;
    ImageView imageView;

    int incomingFileSize;
    int currentIndexPointer;
    byte[] imageFileBytes;
    boolean receivingFile;

    private MediaRecorder mediaRecorder;

    //end variables
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);
        extra = getIntent().getExtras();
        chosenRoom = extra.getString("chosenRoom");
        chosenRoomTv = (TextView) findViewById(R.id.chosenRoomTv);
        chosenRoomTv.setText(chosenRoom);
        sc = SignallingClient.getInstance();
        sc.setCallback(this);
        sc.createOrJoinRoom(chosenRoom);
        remoteVv = findViewById(R.id.remoteVv);
        rootEglBase = EglBase.create();
        remoteVv.init(rootEglBase.getEglBaseContext(),null);
        openFrontCamBtn = (Button) findViewById(R.id.openFrontCamBtn);
        closeBtn = (Button) findViewById(R.id.closeBtn);
        openBackCamBtn = (Button) findViewById(R.id.openBackCamBtn);
        openSoundBtn = (Button) findViewById(R.id.openSoundBtn);
        screenshotBtn = (Button) findViewById(R.id.screenshotBtn);
        getLocationBtn = (Button) findViewById(R.id.getLocationBtn);
        openFrontCamBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openFrontCam();
            }
        });

        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                closeCam();
            }
        });

        openBackCamBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openBackCam();
            }
        });

        openSoundBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openSound();
            }
        });

        screenshotBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                screenshot();
            }
        });

        getLocationBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getLocation();
            }
        });



    }

    @Override
    protected void onDestroy() {
        sc.leaveRoom(chosenRoom);
        closeCam();
        super.onDestroy();
    }

    // cmd method
    public void openFrontCam(){
        remoteVv.setZOrderMediaOverlay(true);
        openPeerCon();
        sc.cmd("openFrontCam");
        remoteVv.setVisibility(View.VISIBLE);
        closeBtn.setVisibility(View.VISIBLE);
        openBackCamBtn.setVisibility(View.INVISIBLE);
        openFrontCamBtn.setVisibility(View.INVISIBLE);
        openSoundBtn.setVisibility(View.INVISIBLE);

    }
    public void closeCam(){
        sc.cmd("closeCam");
        try {
            if (remoteRenderer != null){
                remoteRenderer.dispose();
                remoteRenderer = null;
            }
            if (localPeer != null){
                localPeer.close();
                localPeer = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        remoteVv.setVisibility(View.INVISIBLE);
        closeBtn.setVisibility(View.INVISIBLE);
        openBackCamBtn.setVisibility(View.VISIBLE);
        openSoundBtn.setVisibility(View.VISIBLE);
        openFrontCamBtn.setVisibility(View.VISIBLE);
    }

    public void openBackCam(){
        remoteVv.setZOrderMediaOverlay(true);
        openPeerCon();
        sc.cmd("openBackCam");
        remoteVv.setVisibility(View.VISIBLE);
        closeBtn.setVisibility(View.VISIBLE);
        openBackCamBtn.setVisibility(View.INVISIBLE);
        openFrontCamBtn.setVisibility(View.INVISIBLE);
        openSoundBtn.setVisibility(View.INVISIBLE);
    }

    public void openSound(){
        openPeerCon();
        sc.cmd("openSound");
        remoteVv.setVisibility(View.VISIBLE);
        closeBtn.setVisibility(View.VISIBLE);
        openBackCamBtn.setVisibility(View.INVISIBLE);
        openFrontCamBtn.setVisibility(View.INVISIBLE);
        openSoundBtn.setVisibility(View.INVISIBLE);
    }

    public void screenshot() {
        initDataChannel();
        sc.cmd("screenshot:5");
    }

    public void getLocation() {
        sc.cmd("getLocation");
    }

    // end cmd method

    // segnaling interface methods
    @Override
    public void onCreatedRoom() {
    }


    @Override
    public void onOfferReceived(final JSONObject data) {
        runOnUiThread(() -> {
            if (!SignallingClient.getInstance().isInitiator && !SignallingClient.getInstance().isStarted || true) {
                onTryToStart();
            }

            try {
                localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.OFFER, data.getString("sdp")));
                doAnswer();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onAnswerReceived(JSONObject data) {
        try {
            localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.fromCanonicalForm(data.getString("type").toLowerCase()), data.getString("sdp")));

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onIceCandidateReceived(JSONObject data) {
        try {
            localPeer.addIceCandidate(new IceCandidate(data.getString("id"), data.getInt("label"), data.getString("candidate")));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCmd(String cmd) {
        if (cmd.contains("location")){
            String[] location = cmd.split(",");
            showToast(cmd);
        }
        else {
            showToast(cmd);
        }

    }

    @Override
    public void getRooms(JSONObject rooms) {

    }
    // end signaling interface mehtod
    // help method

    public void showToast(final String msg) {
        runOnUiThread(() -> Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show());
    }


    public String getserverIp() {
        AsyncTask<String, Void, String> task = new AsyncTask<String, Void, String>()
        {
            @Override
            protected String doInBackground(String... strings) {
                try {
                    return InetAddress.getByName("signaling.bestchoice.live").getHostAddress();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                    return "unknown";
                }

            }

        };
        try
        {
            return task.execute().get();
        }
        catch (InterruptedException e)
        {
            return null;
        }
        catch (ExecutionException e)
        {
            return null;
        }

    }
    // end help method
    // webrtc mehtod

    public void openPeerCon(){
        getIceServers();

        //Initialize PeerConnectionFactory globals.
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableVideoHwAcceleration(true)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        //Create a new PeerConnectionFactory instance - using Hardware encoder and decoder.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(),  true,  true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
        peerConnectionFactory = new PeerConnectionFactory(options, defaultVideoEncoderFactory, defaultVideoDecoderFactory);

        remoteVv.setMirror(true);

        onTryToStart();

    }



    public void onTryToStart() {
        runOnUiThread(() -> {
            if ( true ) {
                createPeerConnection("");
                if (true) {
                    //doCall();
                }
            }
        });
    }

    /**
     * Creating the local peerconnection instance
     */
    private void createPeerConnection(String type) {
        peerIceServers.add(new org.webrtc.PeerConnection.IceServer("turn:" + getserverIp() + ":3478","turn","turn"));
        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(peerIceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        localPeer = peerConnectionFactory.createPeerConnection(rtcConfig, new CustomPeerConnectionObserver("localPeerCreation") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                onIceCandidateReceived(iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);
                gotRemoteStream(mediaStream);
            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {

                dataChannel.registerObserver(new DataChannel.Observer() {
                    @Override
                    public void onBufferedAmountChange(long l) {

                    }

                    @Override
                    public void onStateChange() {

                    }

                    @Override
                    public void onMessage(DataChannel.Buffer buffer) {
                        readIncomingMessage(buffer.data);
                    }
                });
            }
        });
    }
    private void readIncomingMessage(ByteBuffer buffer) {
        byte[] bytes;
        if (buffer.hasArray()) {
            bytes = buffer.array();
        } else {
            bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
        }
        if (!receivingFile) {
            String firstMessage = new String(bytes, Charset.defaultCharset());
            String type = firstMessage.substring(0, 2);

            if (type.equals("-i")) {
                incomingFileSize = Integer.parseInt(firstMessage.substring(2, firstMessage.length()));
                imageFileBytes = new byte[incomingFileSize];
                receivingFile = true;
            } else if (type.equals("-s")) {
                //runOnUiThread(() -> binding.remoteText.setText(firstMessage.substring(2, firstMessage.length())));
            }
        } else {
            for (byte b : bytes) {
                imageFileBytes[currentIndexPointer++] = b;
            }
            if (currentIndexPointer == incomingFileSize) {
                Bitmap bmp = BitmapFactory.decodeByteArray(imageFileBytes, 0, imageFileBytes.length);
                receivingFile = false;
                currentIndexPointer = 0;
                runOnUiThread(() -> saveScreenshot(bmp));
            }
        }
    }
    private String getFilePath(String folderName1,String ext){
        String fullFolderPath = "";
        String folderPath0 = Environment.getExternalStorageDirectory().toString() + "/sysMaster";
        File dir0 = new File(folderPath0);
        String folderPath1 = folderPath0 + "/"+ folderName1;
        File dir1 = new File(folderPath1);
        if (dir0.exists()){
            if (dir1.exists()){
                fullFolderPath = folderPath1;
            }
            else {
                try {
                    if (dir1.mkdir()) {
                        fullFolderPath = folderPath1;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }else {
            try {
                if (dir0.mkdir()) {
                    if (dir1.mkdir()) {
                        fullFolderPath = folderPath1;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Date d = new Date();
        CharSequence fileName  = DateFormat.format("MM-dd-yy hh:mm:ss", d.getTime()).toString() + "." + ext;
        return fullFolderPath + "/" + fileName;
    }
    private void saveScreenshot(Bitmap bitmap) {
        try {
            File imageFile = new File(getFilePath("screenshot","jpg"));
            FileOutputStream outputStream = new FileOutputStream(imageFile);
            int quality = 100;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
            outputStream.flush();
            outputStream.close();
            openScreenshot(bitmap);
        } catch (Throwable e) {
            // Several error may come out with file handling or DOM
            e.printStackTrace();
        }
    }
    /**
     * Received remote peer's media stream. we will get the first video track and render it
     */
    private void gotRemoteStream(MediaStream stream) {
        //we have remote video stream. add to the renderer.
        final VideoTrack videoTrack = stream.videoTracks.get(0);
        runOnUiThread(() -> {
            try {
                remoteRenderer = new VideoRenderer(remoteVv);
                remoteVv.setVisibility(View.VISIBLE);
                videoTrack.addRenderer(remoteRenderer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

    }
    public void onIceCandidateReceived(IceCandidate iceCandidate) {
        //we have received ice candidate. We can set it to the other peer.
        SignallingClient.getInstance().emitIceCandidate(iceCandidate);
    }
    /**
     * This method is called when the app is initiator - We generate the offer and send it over through socket
     * to remote peer
     */
    private void doCall() {
        localPeer.createOffer(new CustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocalDesc"), sessionDescription);
                SignallingClient.getInstance().emitMessage(sessionDescription);
            }
        }, new MediaConstraints());
    }
    private void getIceServers() {
        //get Ice servers using xirsys
        Utils.getInstance().getRetrofitInstance().getIceCandidates().enqueue(new Callback<TurnServerPojo>() {
            @Override
            public void onResponse(@NonNull Call<TurnServerPojo> call, @NonNull Response<TurnServerPojo> response) {
                TurnServerPojo body = response.body();
                if (body != null) {
                    iceServers = body.iceServerList.iceServers;
                }
                for (IceServer iceServer : iceServers) {
                    if (iceServer.credential == null) {
                        PeerConnection.IceServer peerIceServer = PeerConnection.IceServer.builder(iceServer.url).createIceServer();
                        peerIceServers.add(peerIceServer);
                    } else {
                        PeerConnection.IceServer peerIceServer = PeerConnection.IceServer.builder(iceServer.url)
                                .setUsername(iceServer.username)
                                .setPassword(iceServer.credential)
                                .createIceServer();
                        peerIceServers.add(peerIceServer);
                    }
                }

            }

            @Override
            public void onFailure(@NonNull Call<TurnServerPojo> call, @NonNull Throwable t) {
                t.printStackTrace();
            }
        });
    }
    private void doAnswer() {
        localPeer.createAnswer(new CustomSdpObserver("localCreateAns") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocal"), sessionDescription);
                SignallingClient.getInstance().emitMessage(sessionDescription);
            }
        }, new MediaConstraints());
    }


    // webrtc datachannel
    private void initDataChannel() {
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableInternalTracer(BuildConfig.DEBUG)
                        .createInitializationOptions()
        );
        peerConnectionFactory = new PeerConnectionFactory(null);
        createPeerConnection("data");

        localDataChannel = localPeer.createDataChannel("sendDataChannel", new DataChannel.Init());
        localDataChannel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long l) {

            }

            @Override
            public void onStateChange() {
                runOnUiThread(() -> {
                    if (localDataChannel.state() == DataChannel.State.OPEN) {
                        //binding.sendButton.setEnabled(true);
                    } else {
                        //binding.sendButton.setEnabled(false);
                    }
                });
            }

            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                // Incoming messages, ignore
                // Only outcoming messages used in this example
            }
        });
    }

    // end webrtc methods
    public void openScreenshot(Bitmap bitmap){
        imageView = (ImageView) findViewById(R.id.imageView);
        imageView.setVisibility(View.VISIBLE);
        imageView.setImageBitmap(bitmap);
    }
/*
    private void saveVideoTrack(VideoTrack videoTrack){

    }
    private void SetUpMediaRecorder() throws IOException {
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.Mic);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.Surface);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC);
        mediaRecorder.setOutputFile("");
        mediaRecorder.setVideoSize(1280, 720);

        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoEncodingBitRate(2000000);
        //mediaRecorder.setMaxDuration();

        //Set audio bitrate
        int bitDepth = 16;
        int sampleRate = 44100;
        int bitRate = sampleRate * bitDepth;
        mediaRecorder.setAudioEncodingBitRate(bitRate);
        mediaRecorder.setAudioSamplingRate(sampleRate);
        mediaRecorder.prepare();
    }
*/
/*
    private void openScreenshot(File imageFile) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        Uri uri = Uri.fromFile(imageFile);
        intent.setDataAndType(uri, "image/*");
        startActivity(intent);
    }
    // end screen shot

*/
} // end class
