package com.example.testcontainersbuildpacksexamples;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import dev.snowdrop.buildpack.Buildpack;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static java.util.Map.entry;
import static org.hamcrest.Matchers.equalTo;

public class BuildpackTest {

    private static DockerClient dockerClient = DockerClientFactory.lazyClient();

    private static Path buildpacks;

    private static final File cwd = getRootProject();

    private static File finalFile;

    @BeforeAll
    static void beforeAll() throws Exception {
        pullImage();

        buildpacks = Files.createTempDirectory("buildpacks");

        Files.walk(cwd.toPath())
                .filter(path -> (path.toFile().isDirectory() && path.toFile().getPath().equals("./src/main")) || (path.toFile().isFile() && path.toFile().getName().equals("pom.xml")))
                .forEach(path -> {
                    try {
                        Path targetPath = buildpacks.resolve(cwd.toPath().relativize(path));
                        if (path.toFile().isDirectory()) {
                            FileUtils.copyDirectory(path.toFile(), targetPath.toFile());
                        }
                        else {
                            FileUtils.copyFile(path.toFile(), targetPath.toFile());
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        finalFile = new File(buildpacks.toFile(), ".");
    }

    @AfterAll
    static void afterAll() throws IOException {
        FileUtils.deleteDirectory(buildpacks.toFile());
    }

    @Test
    void test()  {
        Buildpack.builder().withDockerClient(dockerClient)
                .addNewFileContent(finalFile)
                .withFinalImage("test-spring-app")
                .withLogLevel("info")
                .withNewSlf4jLogger("buildpack")
                .build();

        try (GenericContainer<?> app = new GenericContainer<>("test-spring-app")
                .withExposedPorts(8080)) {
            app.start();
            given().baseUri("http://%s:%d".formatted(app.getHost(), app.getMappedPort(8080))).get("/greetings").then().assertThat().body(equalTo("Hello World"));
        }
    }

    @Test
    void testNativeImage() {
        Buildpack.builder().withDockerClient(dockerClient)
                .addNewFileContent(finalFile)
                .withBuilderImage("paketobuildpacks/builder-jammy-tiny:latest")
                .withFinalImage("test-native-spring-app")
                .withEnvironment(Map.ofEntries(entry("BP_MAVEN_ACTIVE_PROFILES", "native"),
                        entry("BP_NATIVE_IMAGE", "true")))
                .withLogLevel("info")
                .withNewSlf4jLogger("buildpack")
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
        dockerClient.pullImageCmd("tianon/true:latest").exec(new PullImageResultCallback()).awaitCompletion();
    }

}
