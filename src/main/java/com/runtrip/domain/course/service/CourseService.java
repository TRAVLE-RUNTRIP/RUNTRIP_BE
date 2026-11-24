package com.runtrip.domain.course.service;

import com.runtrip.domain.course.dto.CourseCreateRequest;
import com.runtrip.domain.course.dto.CourseResponse;
import com.runtrip.domain.course.dto.CourseResponse.CoursePlaceResponse;
import com.runtrip.domain.course.dto.Preference;
import com.runtrip.domain.course.entity.Course;
import com.runtrip.domain.course.entity.CoursePlace;
import com.runtrip.domain.course.repository.CoursePlaceRepository;
import com.runtrip.domain.course.repository.CourseRepository;
import com.runtrip.domain.place.entity.Place;
import com.runtrip.domain.place.repository.PlaceRepository;
import com.runtrip.domain.place.repository.PlaceRepository.Candidate;
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
import java.util.ArrayList;
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
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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
	private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);

	private final PlaceRepository placeRepository;
	private final CourseRepository courseRepository;
	private final CoursePlaceRepository coursePlaceRepository;
	private final UserRepository userRepository;
	private final AiClient aiClient;
	private final OsrmClient osrmClient;
	private final TransactionTemplate transactionTemplate;

	public CourseResponse createLoop(Long userId, CourseCreateRequest request) {
		long startedAt = System.currentTimeMillis();
		LatLng start = new LatLng(request.start().lat(), request.start().lng());
		double targetKm = request.targetKm();
		List<String> preferences = request.preferences().stream().map(Preference::name).toList();

		// 1. 원 범위 후보 (기타 제외)
		List<Candidate> candidates = placeRepository.findCandidates(start.lat(), start.lng(),
				targetKm * 1000 / (2 * ROAD_FACTOR));
		if (candidates.isEmpty()) {
			throw new ApiException(ErrorCode.NO_PLACES);
		}
		long candidatesAt = System.currentTimeMillis();

		// 2. 후보 전부 점수. detourRatio = 우회 거리 ÷ 목표 거리이고, 순환의 우회 거리는 왕복 직선거리
		List<Double> scores = aiClient.recommend(preferences, isNight(ZonedDateTime.now(SEOUL)),
				candidates.stream()
						.map(c -> new AiClient.Candidate(c.getPlaceId(), 2 * c.getDistanceM() / 1000 / targetKm))
						.toList());
		long recommendAt = System.currentTimeMillis();

		// 3. 점수 상위 40곳과 출발지로 거리표. 순환이라 출발지를 처음과 끝에 둔다
		List<Integer> top = IntStream.range(0, candidates.size()).boxed()
				.sorted(Comparator.comparing(scores::get).reversed())
				.limit(OPTIMIZE_CANDIDATES)
				.toList();
		List<String> topIds = top.stream().map(i -> candidates.get(i).getPlaceId()).toList();
		List<Double> topScores = top.stream().map(scores::get).toList();
		Map<String, Place> places = placeRepository.findAllById(topIds).stream()
				.collect(Collectors.toMap(Place::getId, Function.identity()));
		int[][] matrix = osrmClient.table(withStartAtBothEnds(start, topIds.stream().map(places::get).toList()));
		long tableAt = System.currentTimeMillis();

		// 4. 목표 거리 ±10% 안에서 점수 합이 최대인 조합과 순서
		OptimizeResult optimized = aiClient.optimize(targetKm, maxPlaces(targetKm), topIds, topScores, matrix);
		if (!optimized.found() || optimized.orderedPlaceIds().isEmpty()) {
			throw new ApiException(ErrorCode.NO_PLACES);
		}
		List<Place> ordered = optimized.orderedPlaceIds().stream().map(places::get).toList();
		long optimizeAt = System.currentTimeMillis();

		// 5. 실제 경로선. waypoints의 가운데가 장소별 스냅 좌표
		Route route = osrmClient.route(withStartAtBothEnds(start, ordered));
		List<LatLng> routePoints = route.waypoints().subList(1, route.waypoints().size() - 1);
		long routeAt = System.currentTimeMillis();

		// 6. 임시 저장
		BigDecimal distanceKm = BigDecimal.valueOf(route.distanceM() / 1000).setScale(2, RoundingMode.HALF_UP);
		Course course = transactionTemplate.execute(status -> {
			Course saved = courseRepository.save(Course.createLoop(userRepository.getReferenceById(userId),
					courseName(ordered), distanceKm, point(start), String.join(",", preferences),
					GEOMETRY.createLineString(route.path().stream().map(p -> new Coordinate(p.lng(), p.lat()))
							.toArray(Coordinate[]::new)),
					OffsetDateTime.now(SEOUL)));
			for (int i = 0; i < ordered.size(); i++) {
				coursePlaceRepository.save(CoursePlace.of(saved, ordered.get(i), (short) (i + 1),
						point(routePoints.get(i))));
			}
			return saved;
		});
		long savedAt = System.currentTimeMillis();

		log.info("코스 생성 {}ms (후보 {}곳 {}ms, recommend {}ms, table {}ms, optimize {}ms, route {}ms, 저장 {}ms)"
						+ " courseId={} targetKm={} distanceKm={}",
				savedAt - startedAt, candidates.size(), candidatesAt - startedAt, recommendAt - candidatesAt,
				tableAt - recommendAt, optimizeAt - tableAt, routeAt - optimizeAt, savedAt - routeAt,
				course.getId(), targetKm, distanceKm);

		return toResponse(course, request, route, ordered, routePoints);
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

	private List<LatLng> withStartAtBothEnds(LatLng start, List<Place> places) {
		List<LatLng> points = new ArrayList<>();
		points.add(start);
		places.forEach(place -> points.add(latLng(place.getLocation())));
		points.add(start);
		return points;
	}

	private CourseResponse toResponse(Course course, CourseCreateRequest request, Route route, List<Place> ordered,
			List<LatLng> routePoints) {
		List<CoursePlaceResponse> placeResponses = IntStream.range(0, ordered.size())
				.mapToObj(i -> {
					Place place = ordered.get(i);
					LatLng location = latLng(place.getLocation());
					return new CoursePlaceResponse(place.getId(), i + 1, place.getTitle(), place.getCategory(),
							location.lat(), location.lng(), routePoints.get(i).lat(), routePoints.get(i).lng(),
							place.getOverview(), place.getImageUrl(), place.getImageCredit(), null);
				})
				.toList();
		return new CourseResponse(course.getId(), course.getName(), course.getType().name(), course.getDistanceKm(),
				course.getDistanceKm().multiply(MINUTES_PER_KM).setScale(0, RoundingMode.HALF_UP).intValue(),
				request.start(), request.start(), null, request.preferences(),
				route.path().stream().map(p -> new double[] {p.lat(), p.lng()}).toList(),
				placeResponses, null, false, null);
	}

	private Point point(LatLng latLng) {
		return GEOMETRY.createPoint(new Coordinate(latLng.lng(), latLng.lat()));
	}

	private LatLng latLng(Point point) {
		return new LatLng(point.getY(), point.getX());
	}
}
