package com.runtrip.domain.location.service;

import com.runtrip.global.error.ApiException;
import com.runtrip.global.error.ErrorCode;
import com.runtrip.global.tmap.TmapClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 좌표의 서울 판정. 코스 생성(#6)과 최소 입력 거리(#9)에서 같이 쓴다
@Component
@RequiredArgsConstructor
public class SeoulRegionResolver {

	private static final String SEOUL = "서울특별시";

	private final TmapClient tmapClient;

	// 서울이면 "서울 중구" 형식의 지역을 돌려주고, 아니면 OUT_OF_SEOUL
	// T map의 gu_gun은 필수 필드가 아니라서, 비어 있으면 "서울"만 돌려준다
	public String requireSeoulRegion(double lat, double lng) {
		return tmapClient.reverseGeocode(lat, lng)
				.filter(address -> SEOUL.equals(address.cityDo()))
				.map(address -> address.guGun() == null || address.guGun().isBlank() ? "서울" : "서울 " + address.guGun())
				.orElseThrow(() -> new ApiException(ErrorCode.OUT_OF_SEOUL));
	}
}
