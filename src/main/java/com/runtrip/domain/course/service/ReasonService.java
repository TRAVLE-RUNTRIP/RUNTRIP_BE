package com.runtrip.domain.course.service;

import com.runtrip.domain.course.dto.CourseReasonsResponse;
import com.runtrip.domain.course.dto.CourseReasonsResponse.ReasonItem;
import com.runtrip.domain.course.dto.Preference;
import com.runtrip.domain.course.entity.CoursePlace;
import com.runtrip.domain.course.entity.ReasonSource;
import com.runtrip.domain.course.repository.CoursePlaceRepository;
import com.runtrip.domain.course.repository.CourseRepository;
import com.runtrip.domain.place.entity.Place;
import com.runtrip.global.ai.AiClient;
import com.runtrip.global.ai.AiClient.Reason;
import com.runtrip.global.ai.AiClient.ReasonPlace;
import com.runtrip.global.error.ApiException;
import com.runtrip.global.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// 장소별 추천 이유 (API 명세 6번, B3). 코스 응답 뒤 백그라운드로 만들고, 실패하면 장소 개요 첫 문장으로 대신한다
@Slf4j
@Service
@RequiredArgsConstructor
public class ReasonService {

	static final String NIGHT_SPOT = "night_spot";
	static final int MAX_OVERVIEW_LENGTH = 80;
	// 문장 끝: 마침표·느낌표·물음표 뒤가 공백이거나 글 끝. "1.1km" 같은 소수점은 문장 끝이 아니다
	private static final Pattern SENTENCE_END = Pattern.compile("[.!?](?=\\s|$)");
	// 취향과 같은 분류 (야경은 분류가 아니라 night_spot으로 판단)
	private static final Map<Preference, String> PREFERENCE_CATEGORY = Map.of(
			Preference.ATTRACTION, "관광 명소",
			Preference.NATURE, "자연·공원");

	private final AiClient aiClient;
	private final CourseRepository courseRepository;
	private final CoursePlaceRepository coursePlaceRepository;
	private final TransactionTemplate transactionTemplate;

	// 코스를 커밋한 뒤 불러야 한다. 어떤 실패든 장소 개요로 대신하고 예외를 밖으로 내지 않는다
	@Async("reasonExecutor")
	public void generateAsync(Long courseId, List<Preference> preferences, boolean isNight) {
		long startedAt = System.currentTimeMillis();
		List<CoursePlace> coursePlaces = coursePlaceRepository.findWithPlaceByCourseId(courseId);
		if (coursePlaces.isEmpty()) {
			return;
		}
		Map<String, String> reasons = Map.of();
		try {
			reasons = aiClient.reasons(preferences.stream().map(Preference::name).toList(), isNight,
							coursePlaces.stream().map(cp -> reasonPlace(cp.getPlace(), preferences)).toList())
					.stream()
					.filter(r -> r.reason() != null && !r.reason().isBlank())
					.collect(Collectors.toMap(Reason::placeId, Reason::reason, (a, b) -> a));
		} catch (RuntimeException e) {
			log.warn("추천 이유 생성 실패, 개요로 대체 courseId={}: {}", courseId, e.getMessage());
		}
		Map<String, String> generated = reasons;
		transactionTemplate.executeWithoutResult(status -> {
			// 그사이 코스가 지워졌으면(정리 작업) 저장할 것이 없다
			for (CoursePlace cp : coursePlaceRepository.findWithPlaceByCourseId(courseId)) {
				String reason = generated.get(cp.getPlace().getId());
				if (reason != null) {
					cp.applyReason(reason.strip(), ReasonSource.GEMINI);
				} else {
					cp.applyReason(overviewFirstSentence(cp.getPlace().getOverview()), ReasonSource.OVERVIEW);
				}
			}
		});
		log.info("추천 이유 {}ms courseId={} gemini={}/{}", System.currentTimeMillis() - startedAt, courseId,
				generated.size(), coursePlaces.size());
	}

	// 하나라도 아직 만들지 않은 장소가 있으면 PENDING
	@Transactional(readOnly = true)
	public CourseReasonsResponse reasons(Long userId, Long courseId) {
		courseRepository.findByIdAndUserId(courseId, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.COURSE_NOT_FOUND));
		List<CoursePlace> coursePlaces = coursePlaceRepository.findWithPlaceByCourseId(courseId);
		if (coursePlaces.stream().map(CoursePlace::getReasonSource).anyMatch(Objects::isNull)) {
			return new CourseReasonsResponse("PENDING", List.of());
		}
		return new CourseReasonsResponse("DONE", coursePlaces.stream()
				.map(cp -> new ReasonItem(cp.getPlace().getId(), cp.getReason(), cp.getReasonSource().name()))
				.toList());
	}

	private static ReasonPlace reasonPlace(Place place, List<Preference> preferences) {
		return new ReasonPlace(place.getId(), place.getTitle(), place.getOverview(), evidence(place, preferences));
	}

	// Gemini가 쓸 수 있는 근거. DB에 있는 사실만 넣는다 (항상 1개 이상)
	static List<String> evidence(Place place, List<Preference> preferences) {
		List<String> evidence = new ArrayList<>();
		evidence.add("분류: " + place.getCategory());
		if (place.isNightSpot()) {
			evidence.add(NIGHT_SPOT);
		}
		preferences.stream()
				.map(PREFERENCE_CATEGORY::get)
				.filter(place.getCategory()::equals)
				.findFirst()
				.ifPresent(category -> evidence.add("취향 일치: " + category));
		return evidence;
	}

	// 개요 첫 문장. 80자를 넘으면 잘라서 "…"를 붙인다. 개요가 없으면 null
	static String overviewFirstSentence(String overview) {
		if (overview == null || overview.isBlank()) {
			return null;
		}
		String text = overview.strip();
		Matcher end = SENTENCE_END.matcher(text);
		String sentence = end.find() ? text.substring(0, end.end()) : text;
		return sentence.length() > MAX_OVERVIEW_LENGTH
				? sentence.substring(0, MAX_OVERVIEW_LENGTH).stripTrailing() + "…"
				: sentence;
	}
}
