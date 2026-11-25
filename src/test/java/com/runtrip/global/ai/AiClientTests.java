package com.runtrip.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.runtrip.global.ai.AiClient.Candidate;
import com.runtrip.global.ai.AiClient.OptimizeResult;
import com.runtrip.global.ai.AiClient.Reason;
import com.runtrip.global.ai.AiClient.ReasonPlace;
import com.runtrip.global.ai.AiClient.Safety;
import com.runtrip.global.ai.AiClient.SafetySegment;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiClientTests {

	MockRestServiceServer server;
	AiClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new AiClient(builder, "http://ai", "test-key");
	}

	@Test
	void recommend_요청과_점수_순서() {
		server.expect(requestTo("http://ai/recommend"))
				.andExpect(method(POST))
				.andExpect(header("X-API-Key", "test-key"))
				.andExpect(content().json("""
						{"preferences":["NIGHT_VIEW","NATURE"],"isNight":true,
						"candidates":[{"placeId":"T1","detourRatio":0.42},{"placeId":"T2","detourRatio":0.1}]}""", true))
				.andRespond(withSuccess("""
						{"scores":[{"placeId":"T1","score":0.92},{"placeId":"T2","score":0.31}]}""",
						MediaType.APPLICATION_JSON));

		List<Double> scores = client.recommend(List.of("NIGHT_VIEW", "NATURE"), true,
				List.of(new Candidate("T1", 0.42), new Candidate("T2", 0.1)));

		assertThat(scores).containsExactly(0.92, 0.31);
		server.verify();
	}

	@Test
	void optimize_요청과_결과_excludedCombos_전달() {
		server.expect(requestTo("http://ai/optimize"))
				.andExpect(content().json("""
						{"targetKm":5.0,"maxPlaces":3,"timeLimitMs":500,"placeIds":["T1","T2"],"scores":[0.92,0.88],
						"distanceMatrixM":[[0,320,410,0],[320,0,150,320],[410,150,0,410],[0,320,410,0]],
						"excludedCombos":[["T1","T3"]]}""", true))
				.andRespond(withSuccess("""
						{"found":true,"orderedPlaceIds":["T2","T1"],"totalKm":5.1}""", MediaType.APPLICATION_JSON));

		OptimizeResult result = client.optimize(5, 3, List.of("T1", "T2"), List.of(0.92, 0.88),
				new int[][] {{0, 320, 410, 0}, {320, 0, 150, 320}, {410, 150, 0, 410}, {0, 320, 410, 0}},
				List.of(List.of("T1", "T3")));

		assertThat(result).isEqualTo(new OptimizeResult(true, List.of("T2", "T1"), 5.1));
		server.verify();
	}

	@Test
	void reasons_요청과_결과() {
		server.expect(requestTo("http://ai/reasons"))
				.andExpect(method(POST))
				.andExpect(header("X-API-Key", "test-key"))
				.andExpect(content().json("""
						{"preferences":["NIGHT_VIEW"],"isNight":true,
						"places":[{"placeId":"T1","name":"덕수궁 돌담길","overview":"개요","evidence":["night_spot"]},
						          {"placeId":"T2","name":"청계천","overview":null,"evidence":["분류: 자연·공원"]}]}""", true))
				.andRespond(withSuccess("""
						{"reasons":[{"placeId":"T1","reason":"조명이 이어져요","usedEvidence":["night_spot"]},
						            {"placeId":"T2","reason":null,"usedEvidence":[]}]}""", MediaType.APPLICATION_JSON));

		List<Reason> reasons = client.reasons(List.of("NIGHT_VIEW"), true, List.of(
				new ReasonPlace("T1", "덕수궁 돌담길", "개요", List.of("night_spot")),
				new ReasonPlace("T2", "청계천", null, List.of("분류: 자연·공원"))));

		assertThat(reasons).containsExactly(new Reason("T1", "조명이 이어져요"), new Reason("T2", null));
		server.verify();
	}

	@Test
	void safety_요청과_결과() {
		server.expect(requestTo("http://ai/safety"))
				.andExpect(method(POST))
				.andExpect(header("X-API-Key", "test-key"))
				.andExpect(content().json("""
						{"path":[[37.5658,126.9751],[37.5661,126.9755]],"isNight":true}""", true))
				.andRespond(withSuccess("""
						{"score":81,"level":"좋음","coverage":0.59,"estimated":0.41,"hasLowSegment":true,
						"segments":[{"fromIndex":0,"toIndex":1,"grade":"적음"}],"warnings":["라이트를 챙기세요"]}""",
						MediaType.APPLICATION_JSON));

		Safety safety = client.safety(List.of(new double[] {37.5658, 126.9751}, new double[] {37.5661, 126.9755}), true);

		assertThat(safety).isEqualTo(new Safety(81, "좋음", 0.59, 0.41, true,
				List.of(new SafetySegment(0, 1, "적음")), List.of("라이트를 챙기세요")));
		server.verify();
	}

	@Test
	void safety_판정할_구간이_없으면_score_null() {
		server.expect(requestTo("http://ai/safety"))
				.andRespond(withSuccess("""
						{"score":null,"level":null,"coverage":0.0,"estimated":0.0,"hasLowSegment":false,
						"segments":[],"warnings":[]}""", MediaType.APPLICATION_JSON));

		Safety safety = client.safety(List.of(new double[] {37.5, 127.0}, new double[] {37.5, 127.0}), false);

		assertThat(safety.score()).isNull();
		assertThat(safety.level()).isNull();
	}

	@Test
	void optimize_해가_없으면_found_false() {
		server.expect(requestTo("http://ai/optimize"))
				.andRespond(withSuccess("""
						{"found":false,"orderedPlaceIds":[],"totalKm":null}""", MediaType.APPLICATION_JSON));

		OptimizeResult result = client.optimize(5, 3, List.of("T1"), List.of(0.5), new int[][] {{0, 1, 0}, {1, 0, 1}, {0, 1, 0}},
				List.of());

		assertThat(result).isEqualTo(new OptimizeResult(false, List.of(), null));
	}
}
