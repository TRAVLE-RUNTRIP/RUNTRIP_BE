package com.runtrip.domain.course.service;

import com.runtrip.domain.course.dto.CourseCreateRequest;
import com.runtrip.domain.course.dto.CourseResponse;
import com.runtrip.domain.course.dto.CourseResponse.CoursePlaceResponse;
import com.runtrip.domain.course.dto.CourseSaveResponse;
import com.runtrip.domain.course.dto.MinDistanceRequest;
import com.runtrip.domain.course.dto.MinDistanceResponse;
import com.runtrip.domain.course.dto.Preference;
import com.runtrip.domain.course.dto.SavedCourseListResponse;
import com.runtrip.domain.course.dto.SavedCourseListResponse.SavedCourse;
import com.runtrip.domain.course.entity.Course;
import com.runtrip.domain.course.entity.CoursePlace;
import com.runtrip.domain.course.repository.CoursePlaceRepository;
import com.runtrip.domain.course.repository.CourseRepository;
import com.runtrip.domain.location.service.SeoulRegionResolver;
import com.runtrip.domain.place.entity.Place;
import com.runtrip.domain.place.repository.PlaceRepository;
import com.runtrip.domain.user.entity.User;
import com.runtrip.domain.user.repository.UserRepository;
import com.runtrip.global.ai.AiClient;
import com.runtrip.global.ai.AiClient.OptimizeResult;
import com.runtrip.global.error.ApiException;
import com.runtrip.global.error.ErrorCode;
import com.runtrip.global.osrm.OsrmClient;
import com.runtrip.global.osrm.OsrmClient.LatLng;
import com.runtrip.global.osrm.OsrmClient.Route;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

// 코스 생성 (API 명세 5번, B-4 내부 순서)
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	// 실제 도로는 직선보다 약 1.3배 길다. 순환은 2 × 직선거리 × 1.3 ≤ 목표 거리
	private static final double ROAD_FACTOR = 1.3;
	// OR-Tools에 넘기는 점수 상위 장소 수
	private static final int OPTIMIZE_CANDIDATES = 40;
	private static final BigDecimal MINUTES_PER_KM = BigDecimal.valueOf(6);
	// 최단 거리 그대로면 범위 안 후보가 0곳이라 10% 여유를 둔다
	private static final BigDecimal MIN_DISTANCE_FACTOR = new BigDecimal("1.1");
	// 목표 거리 상한. CourseCreateRequest의 targetKm 검증과 같은 값
	private static final BigDecimal MAX_TARGET_KM = BigDecimal.valueOf(21);
	private static final BigDecimal M_PER_KM = BigDecimal.valueOf(1000);
	// 코스 생성 제한 시간 (GENERATION_TIMEOUT)
	private static final long GENERATION_LIMIT_MS = 10_000;
	private static final double EARTH_RADIUS_M = 6_371_008.8;
	private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);

	private final PlaceRepository placeRepository;
	private final CourseRepository courseRepository;
	private final CoursePlaceRepository coursePlaceRepository;
	private final UserRepository userRepository;
	private final AiClient aiClient;
	private final OsrmClient osrmClient;
	private final SeoulRegionResolver seoulRegionResolver;
	private final TransactionTemplate transactionTemplate;

	// end가 null이면 순환, 있으면 편도. 다시 만들기는 excludedCombos로 이미 보여준 조합을 뺀다
	public CourseResponse create(Long userId, CourseCreateRequest request) {
		long startedAt = System.currentTimeMillis();
		boolean oneWay = request.end() != null;
		LatLng start = new LatLng(request.start().lat(), request.start().lng());
		LatLng end = oneWay ? new LatLng(request.end().lat(), request.end().lng()) : start;
		double targetKm = request.targetKm();
		List<String> preferences = request.preferences().stream().map(Preference::name).toList();

		// 1. 서울 판정. 서울 밖이면 AI·OSRM을 부르기 전에 OUT_OF_SEOUL. 편도는 최소 입력 거리도 확인
		String region = seoulRegionResolver.requireSeoulRegion(start.lat(), start.lng());
		if (oneWay) {
			seoulRegionResolver.requireSeoulRegion(end.lat(), end.lng());
			if (minInputKm(shortestM(start, end)).compareTo(BigDecimal.valueOf(targetKm)) > 0) {
				throw new ApiException(ErrorCode.DISTANCE_TOO_SHORT);
			}
		}
		long regionAt = System.currentTimeMillis();

		// 2. 후보 (기타 제외). 순환은 원, 편도는 출발지·도착지를 초점으로 하는 타원.
		// detourRatio = 우회 거리 ÷ 목표 거리. 순환은 왕복 직선거리, 편도는 장소를 들르며 늘어나는 직선거리
		List<AiClient.Candidate> candidates = oneWay
				? oneWayCandidates(start, end, targetKm)
				: placeRepository.findCandidates(start.lat(), start.lng(), targetKm * 1000 / (2 * ROAD_FACTOR))
						.stream()
						.map(c -> new AiClient.Candidate(c.getPlaceId(), 2 * c.getDistanceM() / 1000 / targetKm))
						.toList();
		if (candidates.isEmpty()) {
			throw new ApiException(ErrorCode.NO_PLACES);
		}
		long candidatesAt = System.currentTimeMillis();

		// 3. 후보 전부 점수
		List<Double> scores = aiClient.recommend(preferences, isNight(ZonedDateTime.now(SEOUL)), candidates);
		long recommendAt = System.currentTimeMillis();

		// 4. 점수 상위 40곳과 출발지·도착지로 거리표. 순환은 출발지를 처음과 끝에 둔다
		List<Integer> top = IntStream.range(0, candidates.size()).boxed()
				.sorted(Comparator.comparing(scores::get).reversed())
				.limit(OPTIMIZE_CANDIDATES)
				.toList();
		List<String> topIds = top.stream().map(i -> candidates.get(i).placeId()).toList();
		List<Double> topScores = top.stream().map(scores::get).toList();
		Map<String, Place> places = placeRepository.findAllById(topIds).stream()
				.collect(Collectors.toMap(Place::getId, Function.identity()));
		int[][] matrix = osrmClient.table(withEnds(start, end, topIds.stream().map(places::get).toList()));
		long tableAt = System.currentTimeMillis();

		// 5. 목표 거리 ±10% 안에서 점수 합이 최대인 조합과 순서. 다시 만들기에서 더 없으면 NO_MORE_COURSES
		OptimizeResult optimized = aiClient.optimize(targetKm, maxPlaces(targetKm), topIds, topScores, matrix,
				request.excludedCombos());
		if (!optimized.found() || optimized.orderedPlaceIds().isEmpty()) {
			throw new ApiException(request.excludedCombos().isEmpty() ? ErrorCode.NO_PLACES : ErrorCode.NO_MORE_COURSES);
		}
		List<Place> ordered = optimized.orderedPlaceIds().stream().map(places::get).toList();
		long optimizeAt = System.currentTimeMillis();

		// 6. 실제 경로선. waypoints의 가운데가 장소별 스냅 좌표
		Route route = osrmClient.route(withEnds(start, end, ordered));
		List<LatLng> routePoints = route.waypoints().subList(1, route.waypoints().size() - 1);
		long routeAt = System.currentTimeMillis();

		// 7. 임시 저장
		BigDecimal distanceKm = BigDecimal.valueOf(route.distanceM() / 1000).setScale(2, RoundingMode.HALF_UP);
		LineString path = GEOMETRY.createLineString(route.path().stream().map(p -> new Coordinate(p.lng(), p.lat()))
				.toArray(Coordinate[]::new));
		String joinedPreferences = String.join(",", preferences);
		Course course = transactionTemplate.execute(status -> {
			User user = userRepository.getReferenceById(userId);
			OffsetDateTime now = OffsetDateTime.now(SEOUL);
			Course saved = courseRepository.save(oneWay
					? Course.createOneWay(user, oneWayName(request.start().name(), request.end().name(), ordered),
							distanceKm, point(start), point(end), region, joinedPreferences, path, now)
					: Course.createLoop(user, courseName(ordered), distanceKm, point(start), region, joinedPreferences,
							path, now));
			for (int i = 0; i < ordered.size(); i++) {
				coursePlaceRepository.save(CoursePlace.of(saved, ordered.get(i), (short) (i + 1),
						point(routePoints.get(i))));
			}
			// 저장까지 10초를 넘기면 롤백하고 GENERATION_TIMEOUT
			long elapsedMs = System.currentTimeMillis() - startedAt;
			if (elapsedMs > GENERATION_LIMIT_MS) {
				log.warn("코스 생성 시간 초과 {}ms", elapsedMs);
				throw new ApiException(ErrorCode.GENERATION_TIMEOUT);
			}
			return saved;
		});
		long savedAt = System.currentTimeMillis();

		// estimatedKm은 거리표 기준 솔버 값, distanceKm는 실제 경로 거리. 둘의 차이는 #24에서 확인한다
		log.info("코스 생성 {}ms (서울 판정 {}ms, 후보 {}곳 {}ms, recommend {}ms, table {}ms, optimize {}ms, route {}ms, 저장 {}ms)"
						+ " courseId={} type={} targetKm={} excluded={} estimatedKm={} distanceKm={}",
				savedAt - startedAt, regionAt - startedAt, candidates.size(), candidatesAt - regionAt,
				recommendAt - candidatesAt, tableAt - recommendAt, optimizeAt - tableAt, routeAt - optimizeAt, savedAt - routeAt,
				course.getId(), course.getType(), targetKm, request.excludedCombos().size(), optimized.totalKm(), distanceKm);

		return toResponse(course, request, route, ordered, routePoints);
	}

	// 출발지→장소 + 장소→도착지 직선거리 합 ≤ 목표 거리 ÷ 1.3 인 타원 범위 후보
	private List<AiClient.Candidate> oneWayCandidates(LatLng start, LatLng end, double targetKm) {
		double directM = distanceM(start, end);
		return placeRepository.findOneWayCandidates(start.lat(), start.lng(), end.lat(), end.lng(),
						targetKm * 1000 / ROAD_FACTOR)
				.stream()
				.map(c -> new AiClient.Candidate(c.getPlaceId(),
						Math.max(0, c.getToStartM() + c.getToEndM() - directM) / 1000 / targetKm))
				.toList();
	}

	// 도착지를 지정할 때 입력할 수 있는 최소 목표 거리 (API 명세 4번)
	public MinDistanceResponse minDistance(MinDistanceRequest request) {
		LatLng start = new LatLng(request.start().lat(), request.start().lng());
		LatLng end = new LatLng(request.end().lat(), request.end().lng());
		seoulRegionResolver.requireSeoulRegion(start.lat(), start.lng());
		seoulRegionResolver.requireSeoulRegion(end.lat(), end.lng());

		BigDecimal shortestM = shortestM(start, end);
		BigDecimal shortestKm = shortestM.divide(M_PER_KM).setScale(1, RoundingMode.HALF_UP);
		BigDecimal minInputKm = minInputKm(shortestM);
		if (minInputKm.compareTo(MAX_TARGET_KM) > 0) {
			throw new ApiException(ErrorCode.DESTINATION_TOO_FAR);
		}
		return new MinDistanceResponse(shortestKm, minInputKm);
	}

	// API 명세 7번. 여러 번 불러도 처음 저장 시각을 그대로 준다
	@Transactional
	public CourseSaveResponse save(Long userId, Long courseId) {
		Course course = findOwnCourse(userId, courseId);
		// DB(timestamptz)는 마이크로초까지 저장한다. 첫 응답과 다시 부른 응답의 시각이 같도록 맞춘다
		course.save(OffsetDateTime.now(SEOUL).truncatedTo(ChronoUnit.MICROS));
		return new CourseSaveResponse(course.getId(), true, course.getSavedAt());
	}

	// API 명세 10번. 이미 저장 취소한 내 코스도 그대로 성공
	@Transactional
	public void unsave(Long userId, Long courseId) {
		findOwnCourse(userId, courseId).unsave();
	}

	// API 명세 8번. 저장 시각 최신순
	@Transactional(readOnly = true)
	public SavedCourseListResponse savedCourses(Long userId) {
		List<SavedCourse> courses = courseRepository.findByUserIdAndSavedAtIsNotNullOrderBySavedAtDesc(userId).stream()
				.map(c -> new SavedCourse(c.getId(), c.getName(), c.getType().name(), c.getDistanceKm(), c.getRegion(),
						preferences(c), c.getSavedAt()))
				.toList();
		return new SavedCourseListResponse(courses.size(), courses);
	}

	// API 명세 9번. safety는 BE-13에서 채운다. 저장하지 않은 내 코스도 조회할 수 있다
	@Transactional(readOnly = true)
	public CourseResponse detail(Long userId, Long courseId) {
		Course course = findOwnCourse(userId, courseId);
		List<CoursePlaceResponse> places = coursePlaceRepository.findWithPlaceByCourseId(courseId).stream()
				.map(cp -> placeResponse(cp.getPlace(), cp.getVisitOrder(), latLng(cp.getRoutePoint()), cp.getReason()))
				.toList();
		List<double[]> path = Arrays.stream(course.getPath().getCoordinates())
				.map(c -> new double[] {c.getY(), c.getX()})
				.toList();
		return courseResponse(course, coordinate(course.getStartPoint()), coordinate(course.getEndPoint()),
				preferences(course), path, places);
	}

	// 다른 계정 코스는 없는 코스와 같이 COURSE_NOT_FOUND
	private Course findOwnCourse(Long userId, Long courseId) {
		return courseRepository.findByIdAndUserId(courseId, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.COURSE_NOT_FOUND));
	}

	// DB에는 "NIGHT_VIEW,NATURE"로 저장. 자유 러닝 코스는 null
	private static List<Preference> preferences(Course course) {
		if (!StringUtils.hasText(course.getPreferences())) {
			return List.of();
		}
		return Arrays.stream(course.getPreferences().split(",")).map(Preference::valueOf).toList();
	}

	private BigDecimal shortestM(LatLng start, LatLng end) {
		return BigDecimal.valueOf(osrmClient.route(List.of(start, end)).distanceM());
	}

	// OSRM 최단 거리 × 1.1을 0.1km 단위로 올린 값.
	// double로 곱하면 3.0 × 1.1 = 3.3000000000000003이 3.4로 올라가므로 BigDecimal로 계산한다
	private static BigDecimal minInputKm(BigDecimal shortestM) {
		return shortestM.multiply(MIN_DISTANCE_FACTOR).divide(M_PER_KM).setScale(1, RoundingMode.CEILING);
	}

	// Asia/Seoul 기준 18:00 ~ 다음 날 06:00 전이 야간
	static boolean isNight(ZonedDateTime now) {
		int hour = now.withZoneSameInstant(SEOUL).getHour();
		return hour >= 18 || hour < 6;
	}

	// 러닝 중 너무 자주 멈추지 않도록 거리별 최대 장소 수
	static int maxPlaces(double targetKm) {
		if (targetKm <= 3) {
			return 2;
		}
		if (targetKm <= 7) {
			return 3;
		}
		if (targetKm <= 14) {
			return 4;
		}
		return 5;
	}

	// 방문 순서 앞 2곳: "덕수궁·청계천 코스"
	static String courseName(List<Place> ordered) {
		return ordered.stream().limit(2).map(Place::getTitle).collect(Collectors.joining("·")) + " 코스";
	}

	// 편도 이름: "경복궁 → 광화문 코스". 출발지·도착지 이름이 없으면 첫 장소·마지막 장소 이름으로 대신 쓴다
	static String oneWayName(String startName, String endName, List<Place> ordered) {
		String from = StringUtils.hasText(startName) ? startName.strip() : ordered.getFirst().getTitle();
		String to = StringUtils.hasText(endName) ? endName.strip() : ordered.getLast().getTitle();
		return from + " → " + to + " 코스";
	}

	// 출발지, 장소들, 도착지 순서. 순환이면 end가 start와 같다
	private List<LatLng> withEnds(LatLng start, LatLng end, List<Place> places) {
		List<LatLng> points = new ArrayList<>();
		points.add(start);
		places.forEach(place -> points.add(latLng(place.getLocation())));
		points.add(end);
		return points;
	}

	// 두 지점 사이 구면 직선거리 (m). 후보 조회의 ST_Distance(geography)와 몇 m 안에서 같다
	static double distanceM(LatLng a, LatLng b) {
		double dLat = Math.toRadians(b.lat() - a.lat());
		double dLng = Math.toRadians(b.lng() - a.lng());
		double h = Math.pow(Math.sin(dLat / 2), 2)
				+ Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat())) * Math.pow(Math.sin(dLng / 2), 2);
		return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
	}

	private CourseResponse toResponse(Course course, CourseCreateRequest request, Route route, List<Place> ordered,
			List<LatLng> routePoints) {
		List<CoursePlaceResponse> placeResponses = IntStream.range(0, ordered.size())
				.mapToObj(i -> placeResponse(ordered.get(i), i + 1, routePoints.get(i), null))
				.toList();
		return courseResponse(course, request.start(), request.end() != null ? request.end() : request.start(),
				request.preferences(), route.path().stream().map(p -> new double[] {p.lat(), p.lng()}).toList(),
				placeResponses);
	}

	// 생성(5번)과 상세(9번)가 같이 쓴다. safety는 BE-13에서 채운다
	private CourseResponse courseResponse(Course course, com.runtrip.domain.course.dto.Coordinate start,
			com.runtrip.domain.course.dto.Coordinate end, List<Preference> preferences, List<double[]> path,
			List<CoursePlaceResponse> places) {
		return new CourseResponse(course.getId(), course.getName(), course.getType().name(), course.getDistanceKm(),
				course.getDistanceKm().multiply(MINUTES_PER_KM).setScale(0, RoundingMode.HALF_UP).intValue(),
				start, end, course.getRegion(), preferences, path, places, null, course.getSavedAt() != null,
				course.getSavedAt());
	}

	private CoursePlaceResponse placeResponse(Place place, int order, LatLng routePoint, String reason) {
		LatLng location = latLng(place.getLocation());
		return new CoursePlaceResponse(place.getId(), order, place.getTitle(), place.getCategory(), location.lat(),
				location.lng(), routePoint.lat(), routePoint.lng(), place.getOverview(), place.getImageUrl(),
				place.getImageCredit(), reason);
	}

	// 상세 응답의 출발지·도착지. 이름은 저장하지 않으므로 null
	private com.runtrip.domain.course.dto.Coordinate coordinate(Point point) {
		return new com.runtrip.domain.course.dto.Coordinate(point.getY(), point.getX(), null);
	}

	private Point point(LatLng latLng) {
		return GEOMETRY.createPoint(new Coordinate(latLng.lng(), latLng.lat()));
	}

	private LatLng latLng(Point point) {
		return new LatLng(point.getY(), point.getX());
	}
}
