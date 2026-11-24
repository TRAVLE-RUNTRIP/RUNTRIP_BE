package com.runtrip.domain.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runtrip.global.ai.AiClient;
import com.runtrip.global.ai.AiClient.Candidate;
import com.runtrip.global.ai.AiClient.OptimizeResult;
import com.runtrip.global.osrm.OsrmClient;
import com.runtrip.global.osrm.OsrmClient.LatLng;
import com.runtrip.global.osrm.OsrmClient.Route;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class CourseApiTests {

	// 서울시청 앞
	static final double LAT = 37.5658;
	static final double LNG = 126.9751;
	static final String LOOP_5KM = """
			{"start":{"lat":37.5658,"lng":126.9751},"end":null,"targetKm":5,
			"preferences":["NIGHT_VIEW","NATURE"],"excludedCombos":[]}""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	AiClient aiClient;

	@MockitoBean
	OsrmClient osrmClient;

	Long userId;

	@BeforeEach
	void createUser() {
		userId = jdbcTemplate.queryForObject(
				"insert into users (email, password_hash, nickname) values (?, ?, ?) returning user_id",
				Long.class, "course-" + UUID.randomUUID() + "@runtrip.test", "hash", "코스테스트");
	}

	@AfterEach
	void deleteUser() {
		jdbcTemplate.update("delete from courses where user_id = ?", userId);
		jdbcTemplate.update("delete from users where user_id = ?", userId);
	}

	@Test
	void 순환_5km_코스를_만들어_임시_저장한다() throws Exception {
		stubAiAndOsrm(5123.4);

		mockMvc.perform(createCourse(LOOP_5KM))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.courseId").isNumber())
				.andExpect(jsonPath("$.data.type").value("LOOP"))
				.andExpect(jsonPath("$.data.distanceKm").value(5.12))
				.andExpect(jsonPath("$.data.estimatedMinutes").value(31))
				.andExpect(jsonPath("$.data.start.lat").value(LAT))
				.andExpect(jsonPath("$.data.end.lng").value(LNG))
				.andExpect(jsonPath("$.data.region").isEmpty())
				.andExpect(jsonPath("$.data.preferences[1]").value("NATURE"))
				.andExpect(jsonPath("$.data.path[0][0]").value(LAT))
				.andExpect(jsonPath("$.data.path[0][1]").value(LNG))
				.andExpect(jsonPath("$.data.places.length()").value(3))
				.andExpect(jsonPath("$.data.places[0].order").value(1))
				.andExpect(jsonPath("$.data.places[0].routeLat").isNumber())
				.andExpect(jsonPath("$.data.places[0].reason").isEmpty())
				.andExpect(jsonPath("$.data.safety").isEmpty())
				.andExpect(jsonPath("$.data.saved").value(false))
				.andExpect(jsonPath("$.data.savedAt").isEmpty());

		Map<String, Object> course = jdbcTemplate.queryForMap("""
				select course_id, name, type, distance_km, preferences, saved_at,
				       ST_Equals(start_point, end_point) as loop, ST_NPoints(path) as points
				from courses where user_id = ?""", userId);
		assertThat(course.get("type")).isEqualTo("LOOP");
		assertThat(course.get("distance_km").toString()).isEqualTo("5.12");
		assertThat(course.get("preferences")).isEqualTo("NIGHT_VIEW,NATURE");
		assertThat(course.get("saved_at")).isNull();
		assertThat(course.get("loop")).isEqualTo(true);
		assertThat(course.get("points")).isEqualTo(5);

		List<String> titles = jdbcTemplate.queryForList("""
				select p.title from course_places cp join places p on p.place_id = cp.place_id
				where cp.course_id = ? order by cp.visit_order""", String.class, course.get("course_id"));
		assertThat(titles).hasSize(3);
		assertThat(course.get("name")).isEqualTo(titles.get(0) + "·" + titles.get(1) + " 코스");
		assertThat(jdbcTemplate.queryForObject("""
				select count(*) from course_places
				where course_id = ? and route_point is not null and reason_source is null""",
				Integer.class, course.get("course_id"))).isEqualTo(3);
	}

	@Test
	void 후보는_원_범위_안이고_기타는_빠진다() throws Exception {
		stubAiAndOsrm(5000);
		double radiusM = 5000 / 2.6;

		mockMvc.perform(createCourse(LOOP_5KM)).andExpect(status().isCreated());

		ArgumentCaptor<List<Candidate>> captor = ArgumentCaptor.captor();
		verify(aiClient).recommend(eq(List.of("NIGHT_VIEW", "NATURE")), anyBoolean(), captor.capture());
		List<String> ids = captor.getValue().stream().map(Candidate::placeId).toList();
		assertThat(captor.getValue())
				.allSatisfy(c -> assertThat(c.detourRatio()).isBetween(0.0, 2 * radiusM / 5000 + 1e-9));

		String inRadius = "ST_DWithin(location::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)";
		Integer expected = jdbcTemplate.queryForObject(
				"select count(*) from places where category <> '기타' and " + inRadius, Integer.class, LNG, LAT, radiusM);
		assertThat(ids).hasSize(expected).doesNotHaveDuplicates();
		// 범위 안에 기타 장소가 실제로 있어야 이 테스트가 의미 있다
		assertThat(jdbcTemplate.queryForObject("select count(*) from places where category = '기타' and " + inRadius,
				Integer.class, LNG, LAT, radiusM)).isPositive();
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from places where category = '기타' and place_id = any(?)", Integer.class,
				(Object) ids.toArray(String[]::new))).isZero();
	}

	@Test
	void 조합을_못_찾으면_NO_PLACES() throws Exception {
		stubAiAndOsrm(5000);
		willReturn(new OptimizeResult(false, List.of(), null))
				.given(aiClient).optimize(anyDouble(), anyInt(), anyList(), anyList(), any());

		mockMvc.perform(createCourse(LOOP_5KM))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("NO_PLACES"));
		verify(osrmClient, never()).route(anyList());
		assertThat(jdbcTemplate.queryForObject("select count(*) from courses where user_id = ?", Integer.class, userId))
				.isZero();
	}

	@Test
	void 도착지를_넣으면_VALIDATION_ERROR() throws Exception {
		assertValidationError(LOOP_5KM.replace("\"end\":null", "\"end\":{\"lat\":37.57,\"lng\":126.97}"),
				"도착지 지정은 아직 지원하지 않아요.");
	}

	@Test
	void 좌표가_범위_밖이면_VALIDATION_ERROR() throws Exception {
		assertValidationError(LOOP_5KM.replace("\"lat\":37.5658", "\"lat\":91"), "위도는 -90~90 사이여야 해요.");
		assertValidationError(LOOP_5KM.replace("\"lng\":126.9751", "\"lng\":-181"), "경도는 -180~180 사이여야 해요.");
	}

	@Test
	void 목표_거리가_범위_밖이면_VALIDATION_ERROR() throws Exception {
		assertValidationError(LOOP_5KM.replace("\"targetKm\":5", "\"targetKm\":22"), "목표 거리는 1~21km예요.");
		assertValidationError(LOOP_5KM.replace("\"targetKm\":5", "\"targetKm\":0.5"), "목표 거리는 1~21km예요.");
	}

	@Test
	void 취향이_없거나_중복이면_VALIDATION_ERROR() throws Exception {
		assertValidationError(LOOP_5KM.replace("[\"NIGHT_VIEW\",\"NATURE\"]", "[]"), "취향을 1개 이상 골라 주세요.");
		assertValidationError(LOOP_5KM.replace("[\"NIGHT_VIEW\",\"NATURE\"]", "[\"NATURE\",\"NATURE\"]"),
				"취향이 중복됐어요.");
		mockMvc.perform(createCourse(LOOP_5KM.replace("NATURE", "FOOD")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void 로그인하지_않으면_UNAUTHENTICATED() throws Exception {
		mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(LOOP_5KM))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	private void assertValidationError(String body, String message) throws Exception {
		mockMvc.perform(createCourse(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.message").value(message));
	}

	private MockHttpServletRequestBuilder createCourse(String body) {
		return post("/api/courses")
				.with(authentication(UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}

	// 점수는 후보 순서대로 내려가고, 솔버는 상위 3곳을 고르고, 경로는 지점을 그대로 잇는다고 둔다
	private void stubAiAndOsrm(double routeDistanceM) {
		given(aiClient.recommend(anyList(), anyBoolean(), anyList())).willAnswer(invocation -> {
			List<Candidate> candidates = invocation.getArgument(2);
			return IntStream.range(0, candidates.size()).mapToObj(i -> 1.0 - i * 0.0001).toList();
		});
		given(osrmClient.table(anyList())).willAnswer(invocation -> {
			int size = invocation.<List<LatLng>>getArgument(0).size();
			return new int[size][size];
		});
		given(aiClient.optimize(anyDouble(), anyInt(), anyList(), anyList(), any())).willAnswer(invocation -> {
			List<String> placeIds = invocation.getArgument(2);
			return new OptimizeResult(true, placeIds.subList(0, 3), routeDistanceM / 1000);
		});
		given(osrmClient.route(anyList())).willAnswer(invocation -> {
			List<LatLng> points = invocation.getArgument(0);
			return new Route(routeDistanceM, points, points);
		});
	}
}
