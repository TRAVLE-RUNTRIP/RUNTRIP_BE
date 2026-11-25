package com.runtrip.domain.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runtrip.domain.course.service.CourseCleanupJob;
import com.runtrip.global.ai.AiClient;
import com.runtrip.global.ai.AiClient.Safety;
import com.runtrip.global.tmap.TmapClient;
import com.runtrip.global.tmap.TmapClient.Address;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// API 명세 11·12번
@SpringBootTest
@AutoConfigureMockMvc
class RunApiTests {

	// 자유 러닝: 구간 2개, 점 3 + 2개. 2026-11-25 21:30 서울 시각 시작
	static final String SEGMENTS = """
			[[[37.5658,126.9751],[37.5661,126.9755],[37.5665,126.976]],[[37.567,126.9765],[37.5675,126.977]]]""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	CourseCleanupJob courseCleanupJob;

	@MockitoBean
	TmapClient tmapClient;

	@MockitoBean
	AiClient aiClient;

	Long userId;
	Long otherUserId;

	@BeforeEach
	void setUp() {
		userId = insertUser();
		otherUserId = insertUser();
		given(tmapClient.reverseGeocode(anyDouble(), anyDouble()))
				.willReturn(Optional.of(new Address("서울특별시", "중구")));
	}

	@AfterEach
	void tearDown() {
		jdbcTemplate.update("delete from runs where user_id in (?, ?)", userId, otherUserId);
		jdbcTemplate.update("delete from courses where user_id in (?, ?)", userId, otherUserId);
		jdbcTemplate.update("delete from users where user_id in (?, ?)", userId, otherUserId);
	}

	@Test
	void 코스_러닝을_저장하면_코스도_저장된다() throws Exception {
		Long courseId = insertCourse(userId);
		UUID runId = UUID.randomUUID();

		mockMvc.perform(createRun(courseRun(runId, courseId, true)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.runId").value(runId.toString()))
				.andExpect(jsonPath("$.data.courseSaved").value(true))
				.andExpect(jsonPath("$.data.courseId").value(courseId));

		Map<String, Object> run = jdbcTemplate.queryForMap("select * from runs where run_id = ?", runId);
		assertThat(run.get("course_id")).isEqualTo(courseId);
		assertThat(run.get("type")).isEqualTo("COURSE");
		assertThat(run.get("title")).isEqualTo("덕수궁·청계천 코스");
		assertThat(run.get("distance_km").toString()).isEqualTo("5.21");
		assertThat(run.get("duration_sec")).isEqualTo(1934);
		assertThat(run.get("segments").toString()).contains("37.5658");
		mockMvc.perform(as(userId, get("/api/courses/saved")))
				.andExpect(jsonPath("$.data.courses[0].courseId").value(courseId));
	}

	@Test
	void 코스를_저장하지_않으면_기록만_연결된다() throws Exception {
		Long courseId = insertCourse(userId);
		UUID runId = UUID.randomUUID();

		mockMvc.perform(createRun(courseRun(runId, courseId, false)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.courseSaved").value(false))
				.andExpect(jsonPath("$.data.courseId").value(courseId));

		assertThat(jdbcTemplate.queryForObject("select course_id from runs where run_id = ?", Long.class, runId))
				.isEqualTo(courseId);
		assertThat(savedAt(courseId)).isNull();
	}

	@Test
	void 코스가_정리됐으면_기록만_저장한다() throws Exception {
		Long courseId = insertCourse(userId);
		jdbcTemplate.update("delete from courses where course_id = ?", courseId);
		UUID runId = UUID.randomUUID();

		mockMvc.perform(createRun(courseRun(runId, courseId, true)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.courseSaved").value(false))
				.andExpect(jsonPath("$.data.courseId").isEmpty());

		assertThat(jdbcTemplate.queryForObject("select course_id from runs where run_id = ?", Long.class, runId))
				.isNull();
	}

	@Test
	void 다른_계정의_코스는_연결하지도_저장하지도_않는다() throws Exception {
		Long othersCourse = insertCourse(otherUserId);
		UUID runId = UUID.randomUUID();

		mockMvc.perform(createRun(courseRun(runId, othersCourse, true)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.courseSaved").value(false))
				.andExpect(jsonPath("$.data.courseId").isEmpty());

		assertThat(savedAt(othersCourse)).isNull();
	}

	@Test
	void 자유_러닝을_저장하면_경로로_새_코스를_만든다() throws Exception {
		UUID runId = UUID.randomUUID();
		given(aiClient.safety(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyBoolean()))
				.willReturn(new Safety(70, "보통", 0.5, 0.2, false, List.of(), List.of()));

		String body = mockMvc.perform(createRun(freeRun(runId, true)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.courseSaved").value(true))
				.andExpect(jsonPath("$.data.courseId").isNumber())
				.andReturn().getResponse().getContentAsString();
		Long courseId = com.jayway.jsonpath.JsonPath.<Number>read(body, "$.data.courseId").longValue();

		Map<String, Object> course = jdbcTemplate.queryForMap("""
				select type, name, distance_km, region, preferences, saved_at, ST_NPoints(path) as points,
				       ST_Y(start_point) as start_lat, ST_Y(end_point) as end_lat
				from courses where course_id = ?""", courseId);
		assertThat(course.get("type")).isEqualTo("FREE");
		assertThat(course.get("name")).isEqualTo("11월 25일 자유 러닝 코스");
		assertThat(course.get("distance_km").toString()).isEqualTo("3.40");
		assertThat(course.get("region")).isEqualTo("서울 중구");
		assertThat(course.get("preferences")).isNull();
		assertThat(course.get("saved_at")).isNotNull();
		assertThat(course.get("points")).isEqualTo(5);
		assertThat(course.get("start_lat")).isEqualTo(37.5658);
		assertThat(course.get("end_lat")).isEqualTo(37.5675);
		assertThat(jdbcTemplate.queryForObject("select count(*) from course_places where course_id = ?",
				Integer.class, courseId)).isZero();
		assertThat(jdbcTemplate.queryForObject("select course_id from runs where run_id = ?", Long.class, runId))
				.isEqualTo(courseId);

		// 저장 코스 상세(9번)로 조회된다
		mockMvc.perform(as(userId, get("/api/courses/{id}", courseId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.type").value("FREE"))
				.andExpect(jsonPath("$.data.places").isEmpty())
				.andExpect(jsonPath("$.data.preferences").isEmpty())
				.andExpect(jsonPath("$.data.path.length()").value(5))
				.andExpect(jsonPath("$.data.safety.score").value(70))
				.andExpect(jsonPath("$.data.safety.level").value("보통"));
	}

	@Test
	void 자유_러닝_코스_이름은_서울_날짜() throws Exception {
		// UTC 11월 25일 15:30 = 서울 11월 26일 00:30
		String body = freeRun(UUID.randomUUID(), true)
				.replace("2026-11-25T21:30:00+09:00", "2026-11-25T15:30:00Z")
				.replace("2026-11-25T21:58:00+09:00", "2026-11-25T15:58:00Z");

		mockMvc.perform(createRun(body)).andExpect(status().isCreated());

		assertThat(jdbcTemplate.queryForObject("select name from courses where user_id = ?", String.class, userId))
				.isEqualTo("11월 26일 자유 러닝 코스");
	}

	@Test
	void 서울_밖이나_조회_실패면_지역은_비운다() throws Exception {
		given(tmapClient.reverseGeocode(anyDouble(), anyDouble()))
				.willReturn(Optional.of(new Address("경기도", "고양시")));
		mockMvc.perform(createRun(freeRun(UUID.randomUUID(), true))).andExpect(status().isCreated());

		given(tmapClient.reverseGeocode(anyDouble(), anyDouble()))
				.willThrow(new ResourceAccessException("timeout"));
		mockMvc.perform(createRun(freeRun(UUID.randomUUID(), true))).andExpect(status().isCreated());

		assertThat(jdbcTemplate.queryForList("select region from courses where user_id = ?", String.class, userId))
				.hasSize(2).containsOnlyNulls();
	}

	@Test
	void 자유_러닝을_저장하지_않으면_코스를_만들지_않는다() throws Exception {
		UUID runId = UUID.randomUUID();

		mockMvc.perform(createRun(freeRun(runId, false)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.courseSaved").value(false))
				.andExpect(jsonPath("$.data.courseId").isEmpty());

		assertThat(jdbcTemplate.queryForObject("select count(*) from courses where user_id = ?", Integer.class, userId))
				.isZero();
		assertThat(jdbcTemplate.queryForObject("select count(*) from runs where run_id = ?", Integer.class, runId))
				.isOne();
	}

	@Test
	void 같은_runId를_다시_보내면_200으로_같은_내용() throws Exception {
		UUID runId = UUID.randomUUID();
		String first = mockMvc.perform(createRun(freeRun(runId, true)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Number courseId = com.jayway.jsonpath.JsonPath.read(first, "$.data.courseId");

		mockMvc.perform(createRun(freeRun(runId, true)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.runId").value(runId.toString()))
				.andExpect(jsonPath("$.data.courseSaved").value(true))
				.andExpect(jsonPath("$.data.courseId").value(courseId));

		assertThat(jdbcTemplate.queryForObject("select count(*) from runs where user_id = ?", Integer.class, userId))
				.isOne();
		assertThat(jdbcTemplate.queryForObject("select count(*) from courses where user_id = ?", Integer.class, userId))
				.isOne();
	}

	@Test
	void 코스가_정리돼도_기록_제목은_남는다() throws Exception {
		Long courseId = insertCourse(userId);
		jdbcTemplate.update("update courses set created_at = now() - interval '49 hours' where course_id = ?", courseId);
		UUID runId = UUID.randomUUID();
		mockMvc.perform(createRun(courseRun(runId, courseId, false))).andExpect(status().isCreated());

		courseCleanupJob.deleteExpiredUnsavedCourses();

		Map<String, Object> run = jdbcTemplate.queryForMap("select course_id, title from runs where run_id = ?", runId);
		assertThat(run.get("course_id")).isNull();
		assertThat(run.get("title")).isEqualTo("덕수궁·청계천 코스");
		mockMvc.perform(as(userId, get("/api/runs")))
				.andExpect(jsonPath("$.data.runs[0].title").value("덕수궁·청계천 코스"));
	}

	@Test
	void 기록_목록은_내_기록만_시작_시각_최신순() throws Exception {
		UUID older = UUID.randomUUID();
		UUID newer = UUID.randomUUID();
		mockMvc.perform(createRun(freeRun(older, false))).andExpect(status().isCreated());
		mockMvc.perform(createRun(freeRun(newer, false)
				.replace("2026-11-25T21:30:00+09:00", "2026-11-26T07:00:00+09:00")
				.replace("2026-11-25T21:58:00+09:00", "2026-11-26T07:28:00+09:00")))
				.andExpect(status().isCreated());
		mockMvc.perform(as(otherUserId, post("/api/runs").contentType(MediaType.APPLICATION_JSON)
						.content(freeRun(UUID.randomUUID(), false))))
				.andExpect(status().isCreated());

		mockMvc.perform(as(userId, get("/api/runs")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.runs.length()").value(2))
				.andExpect(jsonPath("$.data.runs[0].runId").value(newer.toString()))
				.andExpect(jsonPath("$.data.runs[1].runId").value(older.toString()))
				.andExpect(jsonPath("$.data.runs[0].type").value("FREE"))
				.andExpect(jsonPath("$.data.runs[0].title").value("자유 러닝"))
				.andExpect(jsonPath("$.data.runs[0].durationSec").value(1680))
				.andExpect(jsonPath("$.data.runs[0].avgPaceSec").value(494))
				.andExpect(jsonPath("$.data.runs[0].distanceKm").value(3.4))
				.andExpect(jsonPath("$.data.runs[0].startedAt").isNotEmpty());
	}

	@Test
	void 입력값이_잘못되면_VALIDATION_ERROR() throws Exception {
		String course = courseRun(UUID.randomUUID(), 1L, true);
		String free = freeRun(UUID.randomUUID(), true);
		String courseIdMismatch = "코스 러닝은 courseId가 필요하고, 자유 러닝은 courseId가 없어야 해요.";

		assertValidationError(course.replace("\"courseId\":1,", "\"courseId\":null,"), courseIdMismatch);
		assertValidationError(free.replace("\"courseId\":null,", "\"courseId\":1,"), courseIdMismatch);
		assertValidationError(course.replace("2026-11-25T22:02:14+09:00", "2026-11-25T21:00:00+09:00"),
				"종료 시각은 시작 시각보다 뒤여야 해요.");
		assertValidationError(course.replace("덕수궁·청계천 코스", "가".repeat(101)), "제목은 100자까지예요.");
		assertValidationError(free.replace(SEGMENTS.strip(), "[[[37.5658,126.9751]]]"),
				"코스로 저장하려면 경로의 점이 2개 이상이어야 해요.");
		assertValidationError(free.replace("[37.5658,126.9751]", "[37.5658]"), "경로의 점은 [위도, 경도]예요.");
		assertValidationError(course.replace("\"saveCourse\":true", "\"saveCourse\":null"),
				"코스 저장 여부를 입력해 주세요.");
	}

	@Test
	void 로그인하지_않으면_UNAUTHENTICATED() throws Exception {
		mockMvc.perform(get("/api/runs"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		mockMvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON)
						.content(freeRun(UUID.randomUUID(), false)))
				.andExpect(status().isUnauthorized());
	}

	private void assertValidationError(String body, String message) throws Exception {
		mockMvc.perform(createRun(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.message").value(message));
	}

	private String courseRun(UUID runId, Long courseId, boolean saveCourse) {
		return """
				{"runId":"%s","type":"COURSE","courseId":%d,"title":"덕수궁·청계천 코스",
				"startedAt":"2026-11-25T21:30:00+09:00","endedAt":"2026-11-25T22:02:14+09:00",
				"distanceKm":5.21,"durationSec":1934,"avgPaceSec":371,"visitedPlaceIds":["T1116925"],
				"segments":[[[37.5658,126.9751],[37.5661,126.9755]]],"saveCourse":%s}"""
				.formatted(runId, courseId, saveCourse);
	}

	private String freeRun(UUID runId, boolean saveCourse) {
		return """
				{"runId":"%s","type":"FREE","courseId":null,"title":"자유 러닝",
				"startedAt":"2026-11-25T21:30:00+09:00","endedAt":"2026-11-25T21:58:00+09:00",
				"distanceKm":3.4,"durationSec":1680,"avgPaceSec":494,"visitedPlaceIds":[],
				"segments":%s,"saveCourse":%s}"""
				.formatted(runId, SEGMENTS.strip(), saveCourse);
	}

	private MockHttpServletRequestBuilder createRun(String body) {
		return as(userId, post("/api/runs").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private MockHttpServletRequestBuilder as(Long user, MockHttpServletRequestBuilder request) {
		return request.with(authentication(UsernamePasswordAuthenticationToken.authenticated(user, null, List.of())));
	}

	private Long insertUser() {
		return jdbcTemplate.queryForObject(
				"insert into users (email, password_hash, nickname) values (?, ?, ?) returning user_id",
				Long.class, "run-" + UUID.randomUUID() + "@runtrip.test", "hash", "기록테스트");
	}

	// 저장하지 않은 순환 코스
	private Long insertCourse(Long owner) {
		return jdbcTemplate.queryForObject("""
				insert into courses (user_id, name, type, distance_km, start_point, end_point, region, preferences, path,
				                     saved_at, created_at)
				values (?, '덕수궁·청계천 코스', 'LOOP', 5.12,
				        ST_SetSRID(ST_MakePoint(126.9751, 37.5658), 4326), ST_SetSRID(ST_MakePoint(126.9751, 37.5658), 4326),
				        '서울 중구', 'NIGHT_VIEW',
				        ST_SetSRID(ST_GeomFromText('LINESTRING(126.9751 37.5658, 126.976 37.57, 126.9751 37.5658)'), 4326),
				        null, ?)
				returning course_id""", Long.class, owner, OffsetDateTime.now());
	}

	private OffsetDateTime savedAt(Long courseId) {
		return jdbcTemplate.queryForObject("select saved_at from courses where course_id = ?", OffsetDateTime.class,
				courseId);
	}
}
