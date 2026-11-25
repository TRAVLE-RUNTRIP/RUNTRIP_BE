package com.runtrip.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.runtrip.domain.course.dto.Preference;
import com.runtrip.domain.place.entity.Place;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReasonServiceTests {

	@Test
	void 근거는_분류와_야경명소와_취향_일치() {
		Place nightAttraction = place("관광 명소", true);

		assertThat(ReasonService.evidence(nightAttraction, List.of(Preference.NIGHT_VIEW, Preference.ATTRACTION)))
				.containsExactly("분류: 관광 명소", "night_spot", "취향 일치: 관광 명소");
		assertThat(ReasonService.evidence(nightAttraction, List.of(Preference.NATURE)))
				.containsExactly("분류: 관광 명소", "night_spot");
		assertThat(ReasonService.evidence(place("자연·공원", false), List.of(Preference.NATURE)))
				.containsExactly("분류: 자연·공원", "취향 일치: 자연·공원");
		// 야경 취향은 분류가 아니라 night_spot으로만 나타난다
		assertThat(ReasonService.evidence(place("기타", false), List.of(Preference.NIGHT_VIEW)))
				.containsExactly("분류: 기타");
	}

	@Test
	void 개요_첫_문장() {
		assertThat(ReasonService.overviewFirstSentence("돌담길로 유명하다. 밤에는 조명이 켜진다."))
				.isEqualTo("돌담길로 유명하다.");
		// 소수점은 문장 끝이 아니다
		assertThat(ReasonService.overviewFirstSentence("길이 1.1km의 산책로다! 다음 문장"))
				.isEqualTo("길이 1.1km의 산책로다!");
		assertThat(ReasonService.overviewFirstSentence("  마침표 없는 개요  ")).isEqualTo("마침표 없는 개요");
		assertThat(ReasonService.overviewFirstSentence(null)).isNull();
		assertThat(ReasonService.overviewFirstSentence(" ")).isNull();
	}

	@Test
	void 첫_문장이_80자를_넘으면_잘라서_말줄임표() {
		String sentence = ReasonService.overviewFirstSentence("가".repeat(100) + ". 둘째 문장.");

		assertThat(sentence).isEqualTo("가".repeat(80) + "…");
	}

	private Place place(String category, boolean nightSpot) {
		Place place = mock(Place.class);
		given(place.getCategory()).willReturn(category);
		given(place.isNightSpot()).willReturn(nightSpot);
		return place;
	}
}
