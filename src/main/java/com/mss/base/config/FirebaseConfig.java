package com.mss.base.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Sets up the Firebase Admin SDK and exposes its clients as Spring beans, so other classes
 * receive {@link FirebaseAuth} and {@link Storage} by injection instead of static lookups.
 * <p>
 * Skipped with {@code FIREBASE_ENABLED=false} ({@code firebase.enabled}): the application then starts without
 * these beans, and features that need Firebase throw
 * {@link com.mss.base.exception.FirebaseNotConfiguredException}. Tests use the same switch to supply mocks.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseConfig {

    private static final String DISABLE_HINT = ", or set FIREBASE_ENABLED=false in .env to start without Firebase.";

    @Value("${firebase.config.path}")
    private String firebaseConfigPath;

    @Value("${firebase.storage.bucket}")
    private String bucketName;

    /**
     * Loads the service account credentials from {@code firebase.config.path}.
     *
     * @throws IllegalStateException if the file is missing or not a valid service account key
     */
    @Bean
    public GoogleCredentials googleCredentials() {
        try (InputStream serviceAccount = new FileInputStream(firebaseConfigPath)) {
            return GoogleCredentials.fromStream(serviceAccount);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Firebase service account file not found: " + firebaseConfigPath
                    + ". Download it from Firebase Console > Project settings > Service accounts"
                    + DISABLE_HINT, e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Firebase service account file: " + firebaseConfigPath
                    + ". It must be the real key from Firebase Console > Project settings > Service accounts"
                    + " > Generate new private key, not a copy of firebase-service-account.example.json"
                    + DISABLE_HINT, e);
        }
    }

    /**
     * Initializes the default Firebase app, or reuses it if it already exists
     * (for example when the application context is refreshed in tests).
     */
    @Bean
    public FirebaseApp firebaseApp(GoogleCredentials googleCredentials) {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException("BUCKET_NAME must be set when Firebase is enabled" + DISABLE_HINT);
        }
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(googleCredentials)
                .setStorageBucket(bucketName)
                .build();
        FirebaseApp app = FirebaseApp.initializeApp(options);
        log.info("Firebase Initialized.");
        return app;
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }

    /**
     * Google Cloud Storage client for the Firebase Storage bucket.
     */
    @Bean
    public Storage firebaseStorage(GoogleCredentials googleCredentials) {
        return StorageOptions.newBuilder()
                .setCredentials(googleCredentials)
                .build()
                .getService();
    }
}
