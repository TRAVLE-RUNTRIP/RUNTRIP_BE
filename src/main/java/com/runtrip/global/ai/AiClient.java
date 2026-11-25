package com.runtrip.global.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// Python AI 서버 호출 (API 명세 B파트). 주소는 환경 변수 AI_BASE_URL, 키는 AI_API_KEY (X-API-Key 헤더)
@Component
public class AiClient {

	// 솔버 제한 시간. 호출 제한 시간 1초 안에 응답이 오도록 둔다
	private static final int OPTIMIZE_TIME_LIMIT_MS = 500;
	// 호출 제한 시간 (API 명세 B-1). 넘기면 예외 → GlobalExceptionHandler가 SERVER_ERROR로 응답
	private static final Duration RECOMMEND_TIMEOUT = Duration.ofMillis(500);
	private static final Duration OPTIMIZE_TIMEOUT = Duration.ofSeconds(1);

	private final RestClient recommendClient;
	private final RestClient optimizeClient;

	@Autowired
	public AiClient(@Value("${ai.base-url}") String baseUrl, @Value("${ai.api-key}") String apiKey) {
		this(RestClient.builder().requestFactory(requestFactory(RECOMMEND_TIMEOUT)),
				RestClient.builder().requestFactory(requestFactory(OPTIMIZE_TIMEOUT)), baseUrl, apiKey);
	}

	// 테스트에서 MockRestServiceServer에 묶은 builder를 넘긴다
	public AiClient(RestClient.Builder builder, String baseUrl, String apiKey) {
		this(builder, builder.clone(), baseUrl, apiKey);
	}

	private AiClient(RestClient.Builder recommendBuilder, RestClient.Builder optimizeBuilder, String baseUrl,
			String apiKey) {
		this.recommendClient = recommendBuilder.baseUrl(baseUrl).defaultHeader("X-API-Key", apiKey).build();
		this.optimizeClient = optimizeBuilder.baseUrl(baseUrl).defaultHeader("X-API-Key", apiKey).build();
	}

	// 점수는 candidates와 같은 순서로 온다
	public List<Double> recommend(List<String> preferences, boolean isNight, List<Candidate> candidates) {
		RecommendResponse response = recommendClient.post()
				.uri("/recommend")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new RecommendRequest(preferences, isNight, candidates))
				.retrieve()
				.body(RecommendResponse.class);
		return response.scores().stream().map(Score::score).toList();
	}

	// distanceMatrixM은 첫 줄 출발지, 마지막 줄 도착지, 가운데는 placeIds 순서.
	// excludedCombos는 다시 만들기에서 이미 보여준 조합 (조합의 장소가 모두 다시 들어가지 않음)
	public OptimizeResult optimize(double targetKm, int maxPlaces, List<String> placeIds, List<Double> scores,
			int[][] distanceMatrixM, List<List<String>> excludedCombos) {
		return optimizeClient.post()
				.uri("/optimize")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new OptimizeRequest(targetKm, maxPlaces, OPTIMIZE_TIME_LIMIT_MS, placeIds, scores,
						distanceMatrixM, excludedCombos))
				.retrieve()
				.body(OptimizeResult.class);
	}

	private static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(timeout);
		factory.setReadTimeout(timeout);
		return factory;
	}

	public record Candidate(String placeId, double detourRatio) {
	}

	// 해를 찾지 못하면 found=false, orderedPlaceIds=[], totalKm=null
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record OptimizeResult(boolean found, List<String> orderedPlaceIds, Double totalKm) {
	}

	record RecommendRequest(List<String> preferences, boolean isNight, List<Candidate> candidates) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RecommendResponse(List<Score> scores) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Score(String placeId, double score) {
	}

	record OptimizeRequest(double targetKm, int maxPlaces, int timeLimitMs, List<String> placeIds,
			List<Double> scores, int[][] distanceMatrixM, List<List<String>> excludedCombos) {
	}
}
