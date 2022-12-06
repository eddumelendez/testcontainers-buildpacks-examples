package com.example.testcontainersbuildpacksexamples;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import dev.snowdrop.buildpack.Buildpack;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

import java.io.File;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static java.util.Map.entry;
import static org.hamcrest.Matchers.equalTo;

public class BuildpackTest {

    private static DockerClient dockerClient = DockerClientFactory.lazyClient();

    @Test
    void test() throws InterruptedException {
        pullImage();

        File cwd = getRootProject();

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

    @Test
    void testNativeImage() throws InterruptedException {
        pullImage();

        File cwd = getRootProject();

        Buildpack.builder().withDockerClient(dockerClient)
                .addNewFileContent(new File(cwd, "."))
                .withBuilderImage("paketobuildpacks/builder:tiny")
                .withFinalImage("test-native-spring-app")
                .withEnvironment(Map.ofEntries(entry("BP_JVM_VERSION", "17.*"),
                        entry("BP_NATIVE_IMAGE", "true"),
                        entry("BP_MAVEN_BUILD_ARGUMENTS", "-Pnative -Dmaven.test.skip=true --no-transfer-progress package")))
                .withLogLevel("info")
                .build();

        try (GenericContainer<?> app = new GenericContainer<>("test-native-spring-app")
                .withExposedPorts(8080)) {
            app.start();
            given().baseUri("http://%s:%d".formatted(app.getHost(), app.getMappedPort(8080))).get("/greetings").then().assertThat().body(equalTo("Hello World"));
        }
    }

    private static File getRootProject() {
        File cwd;
        for (
                cwd = new File(".");
                !new File(cwd, "pom.xml").isFile();
                cwd = cwd.getParentFile()
        );
        return cwd;
    }

    private static void pullImage() throws InterruptedException {
        dockerClient.pullImageCmd("tianon/true").exec(new PullImageResultCallback()).awaitCompletion();
    }

}
