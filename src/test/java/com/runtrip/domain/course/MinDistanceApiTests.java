package com.runtrip.domain.course;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runtrip.global.osrm.OsrmClient;
import com.runtrip.global.osrm.OsrmClient.LatLng;
import com.runtrip.global.osrm.OsrmClient.Route;
import com.runtrip.global.tmap.TmapClient;
import com.runtrip.global.tmap.TmapClient.Address;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class MinDistanceApiTests {

	// 서울시청 → 경복궁
	static final String BODY = """
			{"start":{"lat":37.5658,"lng":126.9751},"end":{"lat":37.5796,"lng":126.977}}""";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	OsrmClient osrmClient;

	@MockitoBean
	TmapClient tmapClient;

	@BeforeEach
	void seoul() {
		given(tmapClient.reverseGeocode(anyDouble(), anyDouble()))
				.willReturn(Optional.of(new Address("서울특별시", "중구")));
	}

	@Test
	void 최단_거리와_최소_입력_거리() throws Exception {
		shortestM(2100);

		mockMvc.perform(minDistance(BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.shortestKm").value(2.1))
				.andExpect(jsonPath("$.data.minInputKm").value(2.4));
		verify(osrmClient).route(List.of(new LatLng(37.5658, 126.9751), new LatLng(37.5796, 126.977)));
	}

	@Test
	void 최소_입력_거리는_실수_오차_없이_올림() throws Exception {
		// double로 3.0 × 1.1을 계산하면 3.3000000000000003이라 3.4로 올라간다
		shortestM(3000);

		mockMvc.perform(minDistance(BODY))
				.andExpect(jsonPath("$.data.shortestKm").value(3.0))
				.andExpect(jsonPath("$.data.minInputKm").value(3.3));
	}

	@Test
	void 최소_입력_거리가_21km면_통과() throws Exception {
		// 19,090m × 1.1 = 20.999km → 21.0
		shortestM(19090);

		mockMvc.perform(minDistance(BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.minInputKm").value(21.0));
	}

	@Test
	void 최소_입력_거리가_21km를_넘으면_DESTINATION_TOO_FAR() throws Exception {
		// 19,100m × 1.1 = 21.01km → 21.1
		shortestM(19100);

		mockMvc.perform(minDistance(BODY))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("DESTINATION_TOO_FAR"));
	}

	@Test
	void 출발지가_서울_밖이면_OUT_OF_SEOUL() throws Exception {
		given(tmapClient.reverseGeocode(eq(37.5658), anyDouble()))
				.willReturn(Optional.of(new Address("경기도", "수원시 팔달구")));

		assertOutOfSeoul();
	}

	@Test
	void 도착지가_서울_밖이면_OUT_OF_SEOUL() throws Exception {
		given(tmapClient.reverseGeocode(eq(37.5796), anyDouble())).willReturn(Optional.empty());

		assertOutOfSeoul();
	}

	@Test
	void 도착지가_없으면_VALIDATION_ERROR() throws Exception {
		mockMvc.perform(minDistance("""
						{"start":{"lat":37.5658,"lng":126.9751}}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.message").value("도착지를 입력해 주세요."));
	}

	@Test
	void 좌표가_범위_밖이면_VALIDATION_ERROR() throws Exception {
		mockMvc.perform(minDistance(BODY.replace("37.5796", "91")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message").value("위도는 -90~90 사이여야 해요."));
	}

	@Test
	void 로그인하지_않으면_UNAUTHENTICATED() throws Exception {
		mockMvc.perform(post("/api/courses/min-distance").contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	private void assertOutOfSeoul() throws Exception {
		mockMvc.perform(minDistance(BODY))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("OUT_OF_SEOUL"));
		verify(osrmClient, never()).route(anyList());
	}

	private void shortestM(double distanceM) {
		given(osrmClient.route(anyList())).willReturn(new Route(distanceM, List.of(), List.of()));
	}

	private MockHttpServletRequestBuilder minDistance(String body) {
		return post("/api/courses/min-distance")
				.with(authentication(UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}
}
