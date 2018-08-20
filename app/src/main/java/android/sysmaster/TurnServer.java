package android.sysmaster;

import retrofit2.Call;
import retrofit2.http.GET;

/**
 * Webrtc_Step3
 * Created by vivek-3102 on 11/03/17.
 */


public interface TurnServer {
    @GET("?username=turn&password=turn")
    Call<TurnServerPojo> getIceCandidates();
}
