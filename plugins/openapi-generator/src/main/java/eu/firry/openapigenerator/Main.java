package eu.firry.openapigenerator;

import eu.firry.openapigenerator.api.DefaultApi;
import java.util.List;

public class Main {

    public static void main(String[] args) throws ApiException {
        var apiClient = new ApiClient();
        apiClient.updateBaseUri("http://api.example.com/v1");

        var api = new DefaultApi(apiClient);
        List<String> users = api.usersGet();
        System.out.println("Users: " + users);
    }
}
