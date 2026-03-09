package io.github.kamill7779.qforge.gaokaoanalysis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "qforge.qdrant")
public class QdrantProperties {

    private String host;
    private int port;
    private String questionCollection;
    private String chunkCollection;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getQuestionCollection() {
        return questionCollection;
    }

    public void setQuestionCollection(String questionCollection) {
        this.questionCollection = questionCollection;
    }

    public String getChunkCollection() {
        return chunkCollection;
    }

    public void setChunkCollection(String chunkCollection) {
        this.chunkCollection = chunkCollection;
    }
}
