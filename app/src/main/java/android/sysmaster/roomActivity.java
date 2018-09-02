package android.sysmaster;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
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

import java.net.InetAddress;
import java.net.UnknownHostException;
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
    boolean gotUserMedia;
    VideoCapturer videoCapturerAndroid;
    List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();
    TextView chosenRoomTv;
    Bundle extra;
    SignallingClient sc;
    String chosenRoom;
    Button openCamBtn, closeCamBtn;
    SurfaceViewRenderer remoteVv;
    VideoRenderer remoteRenderer;

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
        openCamBtn = (Button) findViewById(R.id.openCamBtn);
        openCamBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCam();
            }
        });

        closeCamBtn = (Button) findViewById(R.id.closeCamBtn);
        closeCamBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                closeCam();
            }
        });

    }

    @Override
    protected void onDestroy() {
        sc.leaveRoom(chosenRoom);
        closeCam();
        super.onDestroy();
    }

    // segnaling interface methods
    @Override
    public void onCreatedRoom() {
    }


    @Override
    public void onOfferReceived(final JSONObject data) {
        showToast("Received Offer");
        runOnUiThread(() -> {
            if (!SignallingClient.getInstance().isInitiator && !SignallingClient.getInstance().isStarted) {
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

    // cmd method
    public void openCam(){
        remoteVv.setZOrderMediaOverlay(true);
        openPeerCon();
        sc.cmd("openCam");

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
    }

    // end cmd method
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
                createPeerConnection();
                if (true) {
                    //doCall();
                }
            }
        });
    }

    /**
     * Creating the local peerconnection instance
     */
    private void createPeerConnection() {
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
        });
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
        localPeer.createAnswer(new CustomSdpObserver("localCreateAns") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocal"), sessionDescription);
                SignallingClient.getInstance().emitMessage(sessionDescription);
            }
        }, new MediaConstraints());
    }

    // end webrtc methods

} // end class
