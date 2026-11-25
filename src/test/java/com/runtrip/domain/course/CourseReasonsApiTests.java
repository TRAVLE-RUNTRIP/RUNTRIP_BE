package com.runtrip.domain.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.runtrip.domain.course.dto.Preference;
import com.runtrip.domain.course.service.ReasonService;
import com.runtrip.global.ai.AiClient;
import com.runtrip.global.ai.AiClient.Candidate;
import com.runtrip.global.ai.AiClient.OptimizeResult;
import com.runtrip.global.ai.AiClient.Reason;
import com.runtrip.global.ai.AiClient.ReasonPlace;
import com.runtrip.global.osrm.OsrmClient;
import com.runtrip.global.osrm.OsrmClient.LatLng;
import com.runtrip.global.osrm.OsrmClient.Route;
import com.runtrip.global.tmap.TmapClient;
import com.runtrip.global.tmap.TmapClient.Address;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.HttpServerErrorException;

// API 명세 6번과 코스 생성 뒤 백그라운드 /reasons
@SpringBootTest
@AutoConfigureMockMvc
class CourseReasonsApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	ReasonService reasonService;

	@MockitoBean
	AiClient aiClient;

	@MockitoBean
	OsrmClient osrmClient;

	@MockitoBean
	TmapClient tmapClient;

	Long userId;
	Long otherUserId;
	// 개요가 있는 장소 2곳 (A, B)과 개요가 없는 장소 1곳 (C)
	String placeA;
	String placeB;
	String placeC;

	@BeforeEach
	void setUp() {
		userId = insertUser();
		otherUserId = insertUser();
		List<String> withOverview = jdbcTemplate.queryForList("""
				select place_id from places where overview like '%다. %' order by place_id limit 2""", String.class);
		placeA = withOverview.get(0);
		placeB = withOverview.get(1);
		placeC = jdbcTemplate.queryForObject(
				"select place_id from places where overview is null or overview = '' limit 1", String.class);
		given(tmapClient.reverseGeocode(anyDouble(), anyDouble()))
				.willReturn(Optional.of(new Address("서울특별시", "중구")));
	}

	@AfterEach
	void tearDown() {
		jdbcTemplate.update("delete from courses where user_id in (?, ?)", userId, otherUserId);
		jdbcTemplate.update("delete from users where user_id in (?, ?)", userId, otherUserId);
	}

	@Test
	void 코스_응답은_추천_이유를_기다리지_않고_완료되면_DONE() throws Exception {
		stubCourseCreation();
		given(aiClient.reasons(anyList(), anyBoolean(), anyList())).willAnswer(invocation -> {
			Thread.sleep(1500);
			List<ReasonPlace> places = invocation.getArgument(2);
			return places.stream().map(p -> new Reason(p.placeId(), p.name() + " 이유")).toList();
		});

		long startedAt = System.currentTimeMillis();
		String body = mockMvc.perform(as(userId, post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("""
						{"start":{"lat":37.5658,"lng":126.9751},"end":null,"targetKm":5,
						"preferences":["NIGHT_VIEW"],"excludedCombos":[]}""")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.places[0].reason").isEmpty())
				.andReturn().getResponse().getContentAsString();
		assertThat(System.currentTimeMillis() - startedAt).isLessThan(1500);
		long courseId = JsonPath.<Number>read(body, "$.data.courseId").longValue();

		mockMvc.perform(as(userId, get("/api/courses/{id}/reasons", courseId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.reasons").isEmpty());

		awaitDone(courseId);
		List<String> titles = jdbcTemplate.queryForList("""
				select p.title from course_places cp join places p on p.place_id = cp.place_id
				where cp.course_id = ? order by cp.visit_order""", String.class, courseId);
		mockMvc.perform(as(userId, get("/api/courses/{id}/reasons", courseId)))
				.andExpect(jsonPath("$.data.status").value("DONE"))
				.andExpect(jsonPath("$.data.reasons.length()").value(3))
				.andExpect(jsonPath("$.data.reasons[0].reason").value(titles.get(0) + " 이유"))
				.andExpect(jsonPath("$.data.reasons[0].source").value("GEMINI"));
		// 상세(9번)에도 나온다
		mockMvc.perform(as(userId, get("/api/courses/{id}", courseId)))
				.andExpect(jsonPath("$.data.places[0].reason").value(titles.get(0) + " 이유"));
	}

	@Test
	void reason이_null인_장소만_개요_첫_문장으로_대신한다() throws Exception {
		Long courseId = insertCourse(userId, List.of(placeA, placeB));
		given(aiClient.reasons(anyList(), anyBoolean(), anyList()))
				.willReturn(List.of(new Reason(placeA, "  돌담길이 예뻐요 "), new Reason(placeB, null)));

		reasonService.generateAsync(courseId, List.of(Preference.NIGHT_VIEW), true);
		awaitDone(courseId);

		mockMvc.perform(as(userId, get("/api/courses/{id}/reasons", courseId)))
				.andExpect(jsonPath("$.data.status").value("DONE"))
				.andExpect(jsonPath("$.data.reasons[0].placeId").value(placeA))
				.andExpect(jsonPath("$.data.reasons[0].reason").value("돌담길이 예뻐요"))
				.andExpect(jsonPath("$.data.reasons[0].source").value("GEMINI"))
				.andExpect(jsonPath("$.data.reasons[1].placeId").value(placeB))
				.andExpect(jsonPath("$.data.reasons[1].reason").value(firstSentence(placeB)))
				.andExpect(jsonPath("$.data.reasons[1].source").value("OVERVIEW"));
	}

	@Test
	void AI_서버가_실패하면_전부_개요이고_개요가_없으면_null() throws Exception {
		Long courseId = insertCourse(userId, List.of(placeA, placeC));
		given(aiClient.reasons(anyList(), anyBoolean(), anyList()))
				.willThrow(HttpServerErrorException.create(org.springframework.http.HttpStatus.BAD_GATEWAY, "REASON_FAILED",
						null, null, null));

		reasonService.generateAsync(courseId, List.of(Preference.NATURE), false);
		awaitDone(courseId);

		mockMvc.perform(as(userId, get("/api/courses/{id}/reasons", courseId)))
				.andExpect(jsonPath("$.data.status").value("DONE"))
				.andExpect(jsonPath("$.data.reasons[0].reason").value(firstSentence(placeA)))
				.andExpect(jsonPath("$.data.reasons[0].source").value("OVERVIEW"))
				.andExpect(jsonPath("$.data.reasons[1].reason").isEmpty())
				.andExpect(jsonPath("$.data.reasons[1].source").value("OVERVIEW"));
	}

	@Test
	void 근거와_개요를_AI_서버에_보낸다() throws Exception {
		Long courseId = insertCourse(userId, List.of(placeA));
		Map<String, Object> place = jdbcTemplate.queryForMap(
				"select title, category, overview, night_spot from places where place_id = ?", placeA);
		List<List<ReasonPlace>> sent = new java.util.concurrent.CopyOnWriteArrayList<>();
		given(aiClient.reasons(anyList(), anyBoolean(), anyList())).willAnswer(invocation -> {
			sent.add(invocation.getArgument(2));
			return List.of();
		});

		reasonService.generateAsync(courseId, List.of(Preference.NIGHT_VIEW), true);
		awaitDone(courseId);

		ReasonPlace reasonPlace = sent.getFirst().getFirst();
		assertThat(reasonPlace.placeId()).isEqualTo(placeA);
		assertThat(reasonPlace.name()).isEqualTo(place.get("title"));
		assertThat(reasonPlace.overview()).isEqualTo(place.get("overview"));
		assertThat(reasonPlace.evidence()).startsWith("분류: " + place.get("category"));
		assertThat(reasonPlace.evidence().contains("night_spot")).isEqualTo(place.get("night_spot"));
	}

	@Test
	void 다른_계정이나_없는_코스는_COURSE_NOT_FOUND() throws Exception {
		Long othersCourse = insertCourse(otherUserId, List.of(placeA));

		for (long id : new long[] {othersCourse, Long.MAX_VALUE}) {
			mockMvc.perform(as(userId, get("/api/courses/{id}/reasons", id)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("COURSE_NOT_FOUND"));
		}
	}

	@Test
	void 로그인하지_않으면_UNAUTHENTICATED() throws Exception {
		mockMvc.perform(get("/api/courses/1/reasons"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	// 백그라운드 작업이 끝날 때까지 최대 5초 기다린다
	private void awaitDone(Long courseId) throws InterruptedException {
		for (int i = 0; i < 50; i++) {
			Integer pending = jdbcTemplate.queryForObject(
					"select count(*) from course_places where course_id = ? and reason_source is null", Integer.class,
					courseId);
			if (pending == 0) {
				return;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("추천 이유가 5초 안에 끝나지 않았어요");
	}

	private String firstSentence(String placeId) {
		String overview = jdbcTemplate.queryForObject("select overview from places where place_id = ?", String.class,
				placeId);
		String sentence = overview.substring(0, overview.indexOf("다. ") + 2);
		return sentence.length() > 80 ? sentence.substring(0, 80).stripTrailing() + "…" : sentence;
	}

	private void stubCourseCreation() {
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
			return new OptimizeResult(true, placeIds.subList(0, 3), 5.0);
		});
		given(osrmClient.route(anyList())).willAnswer(invocation -> {
			List<LatLng> points = invocation.getArgument(0);
			return new Route(5000, points, points);
		});
	}

	private MockHttpServletRequestBuilder as(Long user, MockHttpServletRequestBuilder request) {
		return request.with(authentication(UsernamePasswordAuthenticationToken.authenticated(user, null, List.of())));
	}

	private Long insertUser() {
		return jdbcTemplate.queryForObject(
				"insert into users (email, password_hash, nickname) values (?, ?, ?) returning user_id",
				Long.class, "reason-" + UUID.randomUUID() + "@runtrip.test", "hash", "이유테스트");
	}

	private Long insertCourse(Long owner, List<String> placeIds) {
		Long courseId = jdbcTemplate.queryForObject("""
				insert into courses (user_id, name, type, distance_km, start_point, end_point, region, preferences, path,
				                     saved_at, created_at)
				values (?, '테스트 코스', 'LOOP', 5.0,
				        ST_SetSRID(ST_MakePoint(126.9751, 37.5658), 4326), ST_SetSRID(ST_MakePoint(126.9751, 37.5658), 4326),
				        '서울 중구', 'NIGHT_VIEW',
				        ST_SetSRID(ST_GeomFromText('LINESTRING(126.9751 37.5658, 126.976 37.57, 126.9751 37.5658)'), 4326),
				        null, ?)
				returning course_id""", Long.class, owner, OffsetDateTime.now());
		for (int i = 0; i < placeIds.size(); i++) {
			jdbcTemplate.update("""
					insert into course_places (course_id, place_id, visit_order, route_point)
					values (?, ?, ?, ST_SetSRID(ST_MakePoint(126.976, 37.57), 4326))""", courseId, placeIds.get(i), i + 1);
		}
		return courseId;
	}
}
