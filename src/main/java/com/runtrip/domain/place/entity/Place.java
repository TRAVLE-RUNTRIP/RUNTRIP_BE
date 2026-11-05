package com.runtrip.domain.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "places")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place {

	@Id
	@Column(name = "place_id", length = 20)
	private String id;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, length = 20)
	private String category;

	@Column(nullable = false)
	private boolean nightSpot;

	@Column(nullable = false)
	private Point location;

	private String overview;

	private String imageUrl;

	@Column(length = 50)
	private String imageCredit;
}
