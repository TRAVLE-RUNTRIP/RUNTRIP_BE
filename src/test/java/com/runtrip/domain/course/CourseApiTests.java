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

import com.runtrip.domain.course.service.ReasonService;
import com.runtrip.global.ai.AiClient;
import com.runtrip.global.ai.AiClient.Candidate;
import com.runtrip.global.ai.AiClient.OptimizeResult;
import com.runtrip.global.osrm.OsrmClient;
import com.runtrip.global.osrm.OsrmClient.LatLng;
import com.runtrip.global.osrm.OsrmClient.Route;
import com.runtrip.global.tmap.TmapClient;
import com.runtrip.global.tmap.TmapClient.Address;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.web.client.ResourceAccessException;

@SpringBootTest
@AutoConfigureMockMvc
class CourseApiTests {

	// 서울시청 앞
	static final double LAT = 37.5658;
	static final double LNG = 126.9751;
	static final String LOOP_5KM = """
			{"start":{"lat":37.5658,"lng":126.9751},"end":null,"targetKm":5,
			"preferences":["NIGHT_VIEW","NATURE"],"excludedCombos":[]}""";
	// 서울시청 앞 → 경복궁 (직선 약 1.5km)
	static final double END_LAT = 37.5796;
	static final double END_LNG = 126.9770;
	static final String ONE_WAY_5KM = """
			{"start":{"lat":37.5658,"lng":126.9751,"name":"서울시청"},"end":{"lat":37.5796,"lng":126.9770,"name":"경복궁"},
			"targetKm":5,"preferences":["NIGHT_VIEW","NATURE"],"excludedCombos":[]}""";
	// 출발지→도착지 OSRM 최단 거리로 쓸 값. 2km × 1.1 = 2.2km라 목표 5km는 통과
	static final double SHORTEST_M = 2000;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	AiClient aiClient;

	@MockitoBean
	OsrmClient osrmClient;

	@MockitoBean
	TmapClient tmapClient;

	// 추천 이유 백그라운드 작업은 CourseReasonsApiTests에서 확인한다
	@MockitoBean
	ReasonService reasonService;

	Long userId;

	@BeforeEach
	void createUser() {
		userId = jdbcTemplate.queryForObject(
				"insert into users (email, password_hash, nickname) values (?, ?, ?) returning user_id",
				Long.class, "course-" + UUID.randomUUID() + "@runtrip.test", "hash", "코스테스트");
		given(tmapClient.reverseGeocode(anyDouble(), anyDouble()))
				.willReturn(Optional.of(new Address("서울특별시", "중구")));
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
				.andExpect(jsonPath("$.data.region").value("서울 중구"))
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
				select course_id, name, type, distance_km, region, preferences, saved_at,
				       ST_Equals(start_point, end_point) as loop, ST_NPoints(path) as points
				from courses where user_id = ?""", userId);
		assertThat(course.get("type")).isEqualTo("LOOP");
		assertThat(course.get("distance_km").toString()).isEqualTo("5.12");
		assertThat(course.get("region")).isEqualTo("서울 중구");
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
				.given(aiClient).optimize(anyDouble(), anyInt(), anyList(), anyList(), any(), anyList());

		mockMvc.perform(createCourse(LOOP_5KM))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("NO_PLACES"));
		verify(osrmClient, never()).route(anyList());
		assertThat(jdbcTemplate.queryForObject("select count(*) from courses where user_id = ?", Integer.class, userId))
				.isZero();
	}

	@Test
	void 출발지가_서울_밖이면_OUT_OF_SEOUL() throws Exception {
		given(tmapClient.reverseGeocode(anyDouble(), anyDouble()))
				.willReturn(Optional.of(new Address("경기도", "수원시 팔달구")));

		assertOutOfSeoul();
	}

	@Test
	void 주소가_없는_출발지는_OUT_OF_SEOUL() throws Exception {
		given(tmapClient.reverseGeocode(anyDouble(), anyDouble())).willReturn(Optional.empty());

		assertOutOfSeoul();
	}

	private void assertOutOfSeoul() throws Exception {
		mockMvc.perform(createCourse(LOOP_5KM))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("OUT_OF_SEOUL"));
		verify(aiClient, never()).recommend(anyList(), anyBoolean(), anyList());
		verify(osrmClient, never()).table(anyList());
		assertThat(jdbcTemplate.queryForObject("select count(*) from courses where user_id = ?", Integer.class, userId))
				.isZero();
	}

	@Test
	void 도착지를_넣으면_편도_코스를_만든다() throws Exception {
		stubAiAndOsrm(4876.5);

		mockMvc.perform(createCourse(ONE_WAY_5KM))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.type").value("ONE_WAY"))
				.andExpect(jsonPath("$.data.name").value("서울시청 → 경복궁 코스"))
				.andExpect(jsonPath("$.data.distanceKm").value(4.88))
				.andExpect(jsonPath("$.data.start.lat").value(LAT))
				.andExpect(jsonPath("$.data.end.lat").value(END_LAT))
				.andExpect(jsonPath("$.data.end.name").value("경복궁"))
				.andExpect(jsonPath("$.data.places.length()").value(3));

		Map<String, Object> course = jdbcTemplate.queryForMap("""
				select type, name, ST_Y(end_point) as end_lat, ST_X(end_point) as end_lng
				from courses where user_id = ?""", userId);
		assertThat(course.get("type")).isEqualTo("ONE_WAY");
		assertThat(course.get("name")).isEqualTo("서울시청 → 경복궁 코스");
		assertThat(course.get("end_lat")).isEqualTo(END_LAT);
		assertThat(course.get("end_lng")).isEqualTo(END_LNG);

		// 거리표와 경로는 출발지로 시작해 도착지로 끝난다
		ArgumentCaptor<List<LatLng>> table = ArgumentCaptor.captor();
		verify(osrmClient).table(table.capture());
		assertThat(table.getValue().getFirst()).isEqualTo(new LatLng(LAT, LNG));
		assertThat(table.getValue().getLast()).isEqualTo(new LatLng(END_LAT, END_LNG));
	}

	@Test
	void 출발지_도착지_이름이_없으면_첫_장소와_마지막_장소_이름() throws Exception {
		stubAiAndOsrm(5000);

		mockMvc.perform(createCourse(ONE_WAY_5KM.replace(",\"name\":\"서울시청\"", "")
						.replace(",\"name\":\"경복궁\"", "")))
				.andExpect(status().isCreated());

		Map<String, Object> course = jdbcTemplate.queryForMap("select course_id, name from courses where user_id = ?",
				userId);
		List<String> titles = jdbcTemplate.queryForList("""
				select p.title from course_places cp join places p on p.place_id = cp.place_id
				where cp.course_id = ? order by cp.visit_order""", String.class, course.get("course_id"));
		assertThat(course.get("name")).isEqualTo(titles.getFirst() + " → " + titles.getLast() + " 코스");
	}

	@Test
	void 편도_후보는_타원_범위_안이고_기타는_빠진다() throws Exception {
		stubAiAndOsrm(5000);
		double maxSumM = 5000 / 1.3;

		mockMvc.perform(createCourse(ONE_WAY_5KM)).andExpect(status().isCreated());

		ArgumentCaptor<List<Candidate>> captor = ArgumentCaptor.captor();
		verify(aiClient).recommend(anyList(), anyBoolean(), captor.capture());
		List<String> ids = captor.getValue().stream().map(Candidate::placeId).toList();
		// 우회 거리는 0 이상이고, 타원 범위라 (합 − 직선거리) ÷ 목표 거리를 넘지 않는다
		assertThat(captor.getValue()).allSatisfy(c -> assertThat(c.detourRatio()).isBetween(0.0, maxSumM / 5000));

		String sum = """
				ST_Distance(location::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography)
				+ ST_Distance(location::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) <= ?""";
		Integer expected = jdbcTemplate.queryForObject("select count(*) from places where category <> '기타' and " + sum,
				Integer.class, LNG, LAT, END_LNG, END_LAT, maxSumM);
		assertThat(ids).hasSize(expected).doesNotHaveDuplicates();
		assertThat(expected).isPositive();
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from places where category = '기타' and place_id = any(?)", Integer.class,
				(Object) ids.toArray(String[]::new))).isZero();
	}

	@Test
	void 목표_거리가_최소_입력_거리보다_짧으면_DISTANCE_TOO_SHORT() throws Exception {
		stubAiAndOsrm(5000);
		// 최단 4.6km × 1.1 = 5.06 → 5.1km > 목표 5km
		given(osrmClient.route(List.of(new LatLng(LAT, LNG), new LatLng(END_LAT, END_LNG))))
				.willReturn(new Route(4600, List.of(), List.of()));

		mockMvc.perform(createCourse(ONE_WAY_5KM))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("DISTANCE_TOO_SHORT"));
		verify(aiClient, never()).recommend(anyList(), anyBoolean(), anyList());
	}

	@Test
	void 도착지가_서울_밖이면_OUT_OF_SEOUL() throws Exception {
		given(tmapClient.reverseGeocode(END_LAT, END_LNG)).willReturn(Optional.of(new Address("경기도", "고양시")));

		mockMvc.perform(createCourse(ONE_WAY_5KM))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("OUT_OF_SEOUL"));
		verify(osrmClient, never()).route(anyList());
		verify(aiClient, never()).recommend(anyList(), anyBoolean(), anyList());
	}

	@Test
	void 다시_만들기는_excludedCombos를_optimize에_넘긴다() throws Exception {
		stubAiAndOsrm(5000);

		mockMvc.perform(createCourse(LOOP_5KM.replace("\"excludedCombos\":[]",
						"\"excludedCombos\":[[\"T1\",\"T2\"],[\"T3\"]]")))
				.andExpect(status().isCreated());

		verify(aiClient).optimize(anyDouble(), anyInt(), anyList(), anyList(), any(),
				eq(List.of(List.of("T1", "T2"), List.of("T3"))));
	}

	@Test
	void excludedCombos가_없으면_빈_배열로_넘긴다() throws Exception {
		stubAiAndOsrm(5000);

		mockMvc.perform(createCourse(LOOP_5KM.replace(",\"excludedCombos\":[]", "")))
				.andExpect(status().isCreated());

		verify(aiClient).optimize(anyDouble(), anyInt(), anyList(), anyList(), any(), eq(List.of()));
	}

	@Test
	void 다시_만들기에서_조합이_더_없으면_NO_MORE_COURSES() throws Exception {
		stubAiAndOsrm(5000);
		willReturn(new OptimizeResult(false, List.of(), null))
				.given(aiClient).optimize(anyDouble(), anyInt(), anyList(), anyList(), any(), anyList());

		mockMvc.perform(createCourse(LOOP_5KM.replace("\"excludedCombos\":[]", "\"excludedCombos\":[[\"T1\"]]")))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("NO_MORE_COURSES"));
	}

	@Test
	void 저장까지_10초를_넘기면_GENERATION_TIMEOUT이고_저장하지_않는다() throws Exception {
		stubAiAndOsrm(5000);
		given(osrmClient.route(anyList())).willAnswer(invocation -> {
			Thread.sleep(10_001);
			List<LatLng> points = invocation.getArgument(0);
			return new Route(5000, points, points);
		});

		mockMvc.perform(createCourse(LOOP_5KM))
				.andExpect(status().isGatewayTimeout())
				.andExpect(jsonPath("$.error.code").value("GENERATION_TIMEOUT"));
		assertThat(jdbcTemplate.queryForObject("select count(*) from courses where user_id = ?", Integer.class, userId))
				.isZero();
	}

	@Test
	void AI_서버가_실패하면_SERVER_ERROR() throws Exception {
		stubAiAndOsrm(5000);
		given(aiClient.recommend(anyList(), anyBoolean(), anyList()))
				.willThrow(new ResourceAccessException("Read timed out"));

		mockMvc.perform(createCourse(LOOP_5KM))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error.code").value("SERVER_ERROR"));
		assertThat(jdbcTemplate.queryForObject("select count(*) from courses where user_id = ?", Integer.class, userId))
				.isZero();
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
		given(aiClient.optimize(anyDouble(), anyInt(), anyList(), anyList(), any(), anyList())).willAnswer(invocation -> {
			List<String> placeIds = invocation.getArgument(2);
			return new OptimizeResult(true, placeIds.subList(0, 3), routeDistanceM / 1000);
		});
		// 지점 2개는 편도의 출발지→도착지 최단 거리 확인
		given(osrmClient.route(anyList())).willAnswer(invocation -> {
			List<LatLng> points = invocation.getArgument(0);
			return new Route(points.size() == 2 ? SHORTEST_M : routeDistanceM, points, points);
		});
	}
}
