package io.github.kamill7779.qforge.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import java.io.IOException;
import java.io.InputStream;

public class CosQForgeStorageService implements QForgeStorageService {

    private final COSClient cosClient;
    private final QForgeStorageProperties properties;

    public CosQForgeStorageService(COSClient cosClient, QForgeStorageProperties properties) {
        this.cosClient = cosClient;
        this.properties = properties;
    }

    @Override
    public String putObject(String key, InputStream inputStream, long contentLength, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        if (contentLength >= 0) {
            metadata.setContentLength(contentLength);
        }
        if (contentType != null && !contentType.isBlank()) {
            metadata.setContentType(contentType);
        }
        String normalizedKey = buildObjectKey(key);
        PutObjectRequest request = new PutObjectRequest(
                properties.getCos().getBucket(),
                normalizedKey,
                inputStream,
                metadata
        );
        cosClient.putObject(request);
        return CosStorageRef.build(properties.getCos().getBucket(), properties.getCos().getRegion(), normalizedKey);
    }

    @Override
    public InputStream getObjectStream(String storageRef) throws IOException {
        CosStorageRef ref = parseStorageRef(storageRef);
        return cosClient.getObject(new GetObjectRequest(ref.bucket(), ref.key())).getObjectContent();
    }

    @Override
    public void deleteObject(String storageRef) {
        CosStorageRef ref = parseStorageRef(storageRef);
        cosClient.deleteObject(ref.bucket(), ref.key());
    }
}
