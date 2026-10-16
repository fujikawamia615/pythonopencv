package com.xiaoxi.facegate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class FaceClient {
    private final RestClient client;
    private final ObjectMapper json;
    public FaceClient(@Value("${app.face-url}") String url, @Value("${app.face-key}") String key, ObjectMapper json) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(30000);
        client = RestClient.builder().baseUrl(url).requestFactory(factory).defaultHeader("X-Service-Key", key).build();
        this.json = json;
    }
    public Models.Feature extract(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw new ApiException(400, "请选择不超过 5 MB 的人脸图片");
        try {
            var parts = new LinkedMultiValueMap<String, Object>();
            parts.add("file", new ByteArrayResource(file.getBytes()) {
                @Override public String getFilename() { return "upload.jpg"; }
            });
            var result = client.post().uri("/extract").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts).retrieve().body(Models.Feature.class);
            if (result == null || result.feature() == null || result.feature().size() != 128)
                throw new ApiException(502, "识别服务返回了无效特征");
            return result;
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (RestClientException e) {
            throw new ApiException(503, "人脸识别服务不可用，请检查 Python 服务和模型文件");
        } catch (IOException e) { throw new ApiException(400, "读取图片失败"); }
    }
    public Models.Match match(List<Double> feature, List<Models.Candidate> gallery, double threshold, double margin) {
        try {
            Models.Match result = client.post().uri("/match").contentType(MediaType.APPLICATION_JSON)
                .body(new Models.MatchInput(feature, gallery, threshold, margin)).retrieve().body(Models.Match.class);
            if (result == null) throw new ApiException(502, "识别服务未返回结果");
            return result;
        } catch (RestClientResponseException e) { throw translate(e); }
        catch (RestClientException e) { throw new ApiException(503, "人脸识别服务暂时不可用"); }
    }
    public boolean healthy() {
        try { return "ok".equals(client.get().uri("/health").retrieve().body(Map.class).get("status")); }
        catch (Exception e) { return false; }
    }
    private ApiException translate(RestClientResponseException e) {
        if (e.getStatusCode().is4xxClientError() && e.getStatusCode().value() != 401) {
            try {
                var detail = json.readTree(e.getResponseBodyAsString()).path("detail");
                if (detail.isTextual()) return new ApiException(422, detail.asText());
            } catch (IOException ignored) {}
            return new ApiException(422, "图片无法识别，请上传清晰的单人正面照");
        }
        return new ApiException(503, "识别服务配置或运行异常");
    }
}
