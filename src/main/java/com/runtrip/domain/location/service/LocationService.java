package com.runtrip.domain.location.service;

import com.runtrip.domain.location.dto.LocationResult;
import com.runtrip.global.tmap.TmapClient;
import com.runtrip.global.tmap.TmapClient.Poi;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocationService {

	private static final String SEOUL = "서울";

	private final TmapClient tmapClient;

	public List<LocationResult> search(String query) {
		return tmapClient.searchPois(query.strip()).stream()
				.filter(poi -> SEOUL.equals(poi.upperAddrName()))
				// 출발지로 쓰므로 POI 중심점 대신 입구 좌표를 쓰고, 입구 좌표가 없는 장소는 뺀다
				.filter(poi -> hasText(poi.frontLat()) && hasText(poi.frontLon()))
				.map(poi -> new LocationResult(poi.name(), address(poi),
						Double.parseDouble(poi.frontLat()), Double.parseDouble(poi.frontLon())))
				.toList();
	}

	// 도로명 주소가 있으면 "서울 종로구 사직로 161", 없으면 "서울 종로구 서린동 14"
	private String address(Poi poi) {
		String area = poi.upperAddrName() + " " + poi.middleAddrName();
		if (hasText(poi.roadName()) && hasText(poi.firstBuildNo())) {
			return area + " " + poi.roadName() + " " + number(poi.firstBuildNo(), poi.secondBuildNo());
		}
		return area + " " + poi.lowerAddrName() + " " + number(poi.firstNo(), poi.secondNo());
	}

	private String number(String main, String sub) {
		return hasText(sub) && !"0".equals(sub) ? main + "-" + sub : main;
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
