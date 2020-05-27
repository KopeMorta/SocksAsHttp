package com.kopemorta.socksashttp.client;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;

public class Client {
    public static void main(String[] args) {
        System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2");
        Unirest.config()
                .proxy("localhost", 8080);

        HttpResponse<String> stringHttpResponse = Unirest.get("https://api6.ipify.org?format=json")
//                .header("Content-Type", "application/json")
//                .body("test45t3e45t3e45te45te45ts")
                .asString();
        System.out.println(stringHttpResponse.getStatus() + " " + stringHttpResponse.getStatusText());
        System.out.println(stringHttpResponse.getHeaders().toString());
        System.out.println(stringHttpResponse.getBody());
    }
}
