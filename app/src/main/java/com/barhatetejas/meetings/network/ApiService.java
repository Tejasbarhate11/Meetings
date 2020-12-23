package com.barhatetejas.meetings.network;

import java.util.HashMap;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.HeaderMap;
import retrofit2.http.POST;

public interface ApiService {

    @POST("send")//send is the api endpoint
    Call<String> sendRemoteMessage(
            @HeaderMap HashMap<String,String> headers,
            @Body String remoteBody
            );

}
