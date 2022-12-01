package com.example.testcontainersbuildpacksexamples;

import dev.snowdrop.buildpack.Buildpack;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

import java.io.File;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class BuildpackTest {

    @Test
    void test() {
        var dockerClient = DockerClientFactory.lazyClient();

        File cwd;
        for (
                cwd = new File(".");
                !new File(cwd, "pom.xml").isFile();
                cwd = cwd.getParentFile()
        );

        Buildpack.builder().withDockerClient(dockerClient)
                .addNewFileContent(new File(cwd, "."))
                .withFinalImage("test-spring-app")
                .withEnvironment(Map.of("BP_JVM_VERSION", "17.*"))
                .withLogLevel("info")
                .build();

        try (GenericContainer<?> app = new GenericContainer<>("test-spring-app")
                .withExposedPorts(8080)) {
            app.start();
            given().baseUri("http://%s:%d".formatted(app.getHost(), app.getMappedPort(8080))).get("/greetings").then().assertThat().body(equalTo("Hello World"));
        }
    }

}
