package org.acme.ses;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.EnabledService;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

public class SesResource implements QuarkusTestResourceLifecycleManager {

    private static final DockerImageName LOCALSTACK_IMAGE_NAME = DockerImageName.parse("localstack/localstack")
            .withTag("0.12.17");

    public final static String FROM_EMAIL = "from-quarkus@example.com";
    public final static String TO_EMAIL = "to-quarkus@example.com";

    private LocalStackContainer container;
    private SesClient client;

    @Override
    public Map<String, String> start() {
        DockerClientFactory.instance().client();
        try {
            container = new LocalStackContainer(LOCALSTACK_IMAGE_NAME).withServices(Service.SES);
            container.start();

            URI endpointOverride = container.getEndpointOverride(EnabledService.named(Service.SES.getName()));

            StaticCredentialsProvider staticCredentials = StaticCredentialsProvider
                .create(AwsBasicCredentials.create("accesskey", "secretKey"));

            client = SesClient.builder()
                .endpointOverride(endpointOverride)
                .credentialsProvider(staticCredentials)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .region(Region.US_EAST_1).build();

            client.verifyEmailIdentity(req -> req.emailAddress(FROM_EMAIL));
            client.verifyEmailIdentity(req -> req.emailAddress(TO_EMAIL));

            Map<String, String> properties = new HashMap<>();
            properties.put("quarkus.ses.endpoint-override", endpointOverride.toString());
            properties.put("quarkus.ses.aws.region", "us-east-1");
            properties.put("quarkus.ses.aws.credentials.type", "static");
            properties.put("quarkus.ses.aws.credentials.static-provider.access-key-id", "accessKey");
            properties.put("quarkus.ses.aws.credentials.static-provider.secret-access-key", "secretKey");

            return properties;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Could not start localstack server", e);
        }
    }

    @Override
    public void stop() {
        if (container != null) {
            container.close();
        }
    }
}
