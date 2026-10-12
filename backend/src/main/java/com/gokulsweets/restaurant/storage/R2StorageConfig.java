package com.gokulsweets.restaurant.storage;

import com.gokulsweets.restaurant.observability.MethodTiming;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/** Backend r2 storage config contract and implementation. */
@Configuration
public class R2StorageConfig {

    /**
     * R2s client.
     *
     * @param accountId the account id
     * @param accessKeyId the access key id
     * @param secretAccessKey the secret access key
     * @return the r2 client result
     */
    @Bean
    public S3Client r2Client(
            @Value("${cloudflare.r2.account-id}") String accountId,
            @Value("${cloudflare.r2.access-key-id}") String accessKeyId,
            @Value("${cloudflare.r2.secret-access-key}") String secretAccessKey) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(R2StorageConfig.class, "r2Client(String,String,String)");
        try {
            AwsBasicCredentials credentials =
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey);
            return S3Client.builder()
                    .endpointOverride(
                            URI.create("https://" + accountId + ".r2.cloudflarestorage.com"))
                    .region(Region.of("auto"))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    R2StorageConfig.class,
                    "r2Client(String,String,String)");
        }
    }
}
