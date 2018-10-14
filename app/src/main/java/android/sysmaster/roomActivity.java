package android.sysmaster;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
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
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.File;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
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
    Button openFrontCamBtn, closeBtn, openBackCamBtn, openSoundBtn, screenshotBtn;
    SurfaceViewRenderer remoteVv;
    VideoRenderer remoteRenderer;
    DataChannel localDataChannel;

    int incomingFileSize;
    int currentIndexPointer;
    byte[] imageFileBytes;
    boolean receivingFile;

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
            showToast("clse");
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

    // end cmd method

    // segnaling interface methods
    @Override
    public void onCreatedRoom() {
    }


    @Override
    public void onOfferReceived(final JSONObject data) {
        showToast("Received Offer");
        runOnUiThread(() -> {
            if (!SignallingClient.getInstance().isInitiator && !SignallingClient.getInstance().isStarted || true) {
                onTryToStart();
            }

            try {
                showToast("set offer");
                localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.OFFER, data.getString("sdp")));
                doAnswer();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onAnswerReceived(JSONObject data) {
        showToast("Received Answer");
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
        showToast("cmd is: " + cmd + " : " + getserverIp());
        if (cmd.equalsIgnoreCase("openCam") ){
            //openPeerCon();
            showToast("cmd:" + cmd);
        }
        if (cmd.equalsIgnoreCase("closeCam") ){
            //closePeerCon();
            showToast("cmd:" + cmd);
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
                    return InetAddress.getByName("bestchoice.live").getHostAddress();
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
                showToast("onTryToStart");
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
                showToast("Received Remote stream");
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
    private void saveScreenshot(Bitmap bitmap) {
        // mediapro

        // end

        Log.d("saveScreenshot", "1");
        try {
            // image naming and path  to include sd card  appending name you choose for file
            String mPath = Environment.getExternalStorageDirectory().toString() + "/" + "testScreenshot" + ".jpg";



            File imageFile = new File(mPath);

            FileOutputStream outputStream = new FileOutputStream(imageFile);
            int quality = 100;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
            outputStream.flush();
            outputStream.close();

            //openScreenshot(imageFile);
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
        showToast("do call");
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
        showToast("do ansr");
        localPeer.createAnswer(new CustomSdpObserver("localCreateAns") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocal"), sessionDescription);
                SignallingClient.getInstance().emitMessage(sessionDescription);

                showToast("send back");
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
                showToast("got screenshot");
                // Incoming messages, ignore
                // Only outcoming messages used in this example
            }
        });
    }

    // end webrtc methods

} // end class
