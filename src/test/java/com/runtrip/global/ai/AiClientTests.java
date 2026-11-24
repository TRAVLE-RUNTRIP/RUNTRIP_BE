package com.runtrip.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.runtrip.global.ai.AiClient.Candidate;
import com.runtrip.global.ai.AiClient.OptimizeResult;
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
		client = new AiClient(builder, "http://ai");
	}

	@Test
	void recommend_요청과_점수_순서() {
		server.expect(requestTo("http://ai/recommend"))
				.andExpect(method(POST))
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
	void optimize_요청과_결과() {
		server.expect(requestTo("http://ai/optimize"))
				.andExpect(content().json("""
						{"targetKm":5.0,"maxPlaces":3,"timeLimitMs":500,"placeIds":["T1","T2"],"scores":[0.92,0.88],
						"distanceMatrixM":[[0,320,410,0],[320,0,150,320],[410,150,0,410],[0,320,410,0]],
						"excludedCombos":[]}""", true))
				.andRespond(withSuccess("""
						{"found":true,"orderedPlaceIds":["T2","T1"],"totalKm":5.1}""", MediaType.APPLICATION_JSON));

		OptimizeResult result = client.optimize(5, 3, List.of("T1", "T2"), List.of(0.92, 0.88),
				new int[][] {{0, 320, 410, 0}, {320, 0, 150, 320}, {410, 150, 0, 410}, {0, 320, 410, 0}});

		assertThat(result).isEqualTo(new OptimizeResult(true, List.of("T2", "T1"), 5.1));
		server.verify();
	}

	@Test
	void optimize_해가_없으면_found_false() {
		server.expect(requestTo("http://ai/optimize"))
				.andRespond(withSuccess("""
						{"found":false,"orderedPlaceIds":[],"totalKm":null}""", MediaType.APPLICATION_JSON));

		OptimizeResult result = client.optimize(5, 3, List.of("T1"), List.of(0.5), new int[][] {{0, 1, 0}, {1, 0, 1}, {0, 1, 0}});

		assertThat(result).isEqualTo(new OptimizeResult(false, List.of(), null));
	}
}
