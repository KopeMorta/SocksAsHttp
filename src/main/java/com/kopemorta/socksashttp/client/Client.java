package com.kopemorta.socksashttp.client;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;

public class Client {
    public static void main(String[] args) {
        System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2");
        Unirest.config()
                .proxy("localhost", 8080);

        HttpResponse<String> stringHttpResponse = Unirest.post("http://postman-echo.com/post")
                .header("Content-Type", "application/json")
                .body("tests")
                .asString();
        System.out.println(stringHttpResponse.getStatus() + " " + stringHttpResponse.getStatusText());
        System.out.println(stringHttpResponse.getHeaders().toString());
        System.out.println(stringHttpResponse.getBody());
    }
}
