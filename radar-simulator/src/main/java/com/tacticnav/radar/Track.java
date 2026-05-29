package com.tacticnav.radar;

public record Track(short trackId, float lat, float lon, float alt, float speed, long timestamp) {
}
