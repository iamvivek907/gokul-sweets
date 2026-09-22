package com.gokulsweets.restaurant.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
public class R2StorageConfig {

    @Bean
    public S3Client r2Client(
            @Value("${cloudflare.r2.account-id}")
            String accountId,

            @Value("${cloudflare.r2.access-key-id}")
            String accessKeyId,

            @Value("${cloudflare.r2.secret-access-key}")
            String secretAccessKey
    ) {

        AwsBasicCredentials credentials =
                AwsBasicCredentials.create(
                        accessKeyId,
                        secretAccessKey
                );

        return S3Client.builder()
                .endpointOverride(
                        URI.create(
                                "https://"
                                        + accountId
                                        + ".r2.cloudflarestorage.com"
                        )
                )
                .region(
                        Region.of("auto")
                )
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                credentials
                        )
                )
                .build();
    }
}