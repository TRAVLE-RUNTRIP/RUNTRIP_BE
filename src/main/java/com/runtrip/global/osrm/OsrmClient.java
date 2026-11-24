package com.runtrip.global.osrm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// OSRM(foot 프로필) 호출. 주소는 환경 변수 OSRM_BASE_URL
@Component
public class OsrmClient {

	// 길이 이어지지 않아 거리가 null인 칸. 솔버가 고르지 않도록 크게 둔다
	private static final int UNREACHABLE_M = 1_000_000;

	private final RestClient restClient;

	@Autowired
	public OsrmClient(@Value("${osrm.base-url}") String baseUrl) {
		this(RestClient.builder().requestFactory(requestFactory()), baseUrl);
	}

	// 테스트에서 MockRestServiceServer에 묶은 builder를 넘긴다
	public OsrmClient(RestClient.Builder builder, String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	// 지점 사이 도로 거리표 (m, 정수)
	public int[][] table(List<LatLng> points) {
		TableResponse response = restClient.get()
				.uri(uri -> uri.path("/table/v1/foot/" + coordinates(points)).queryParam("annotations", "distance").build())
				.retrieve()
				.body(TableResponse.class);
		return response.distances().stream()
				.map(row -> row.stream().mapToInt(d -> d == null ? UNREACHABLE_M : (int) Math.round(d)).toArray())
				.toArray(int[][]::new);
	}

	// 지점을 순서대로 잇는 경로. waypoints는 각 지점을 도로에 붙인(스냅) 좌표
	public Route route(List<LatLng> points) {
		RouteResponse response = restClient.get()
				.uri(uri -> uri.path("/route/v1/foot/" + coordinates(points))
						.queryParam("overview", "full")
						.queryParam("geometries", "geojson")
						.build())
				.retrieve()
				.body(RouteResponse.class);
		OsrmRoute route = response.routes().getFirst();
		return new Route(route.distance(), toLatLngs(route.geometry().coordinates()),
				response.waypoints().stream().map(waypoint -> toLatLng(waypoint.location())).toList());
	}

	// OSRM 좌표는 "경도,위도;경도,위도" 순서. 템플릿 변수로 넘기면 , ; 가 인코딩되어 OSRM이 못 읽으므로 경로에 바로 붙인다
	private String coordinates(List<LatLng> points) {
		return points.stream().map(p -> p.lng() + "," + p.lat()).collect(Collectors.joining(";"));
	}

	private List<LatLng> toLatLngs(List<List<Double>> lngLats) {
		return lngLats.stream().map(this::toLatLng).toList();
	}

	private LatLng toLatLng(List<Double> lngLat) {
		return new LatLng(lngLat.get(1), lngLat.get(0));
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(1));
		factory.setReadTimeout(Duration.ofSeconds(2));
		return factory;
	}

	public record LatLng(double lat, double lng) {
	}

	public record Route(double distanceM, List<LatLng> path, List<LatLng> waypoints) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TableResponse(List<List<Double>> distances) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RouteResponse(List<OsrmRoute> routes, List<Waypoint> waypoints) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record OsrmRoute(double distance, Geometry geometry) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Geometry(List<List<Double>> coordinates) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Waypoint(List<Double> location) {
	}
}
