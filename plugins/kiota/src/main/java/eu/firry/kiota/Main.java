package eu.firry.kiota;

import eu.firry.kiota.client.SampleApiClient;
import io.kiota.http.jdk.JDKRequestAdapter;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        var requestAdapter = new JDKRequestAdapter();
        requestAdapter.setBaseUrl("http://api.example.com/v1");

        var client = new SampleApiClient(requestAdapter);
        List<String> users = client.users().get();
        System.out.println("Users: " + users);
    }
}
