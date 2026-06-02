import math


def haversine_km(lat1, lon1, lat2, lon2):
    """Great-circle distance in kilometres between two WGS84 points."""
    lat1, lon1, lat2, lon2 = map(math.radians, (float(lat1), float(lon1), float(lat2), float(lon2)))
    dlat = lat2 - lat1
    dlon = lon2 - lon1
    a = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 6371.0 * 2 * math.asin(math.sqrt(a))


def shops_within_radius(shops, lat, lng, radius_km):
    """Filter and sort shops by distance; sets ``distance_km`` on each instance."""
    nearby = []
    for shop in shops:
        distance = haversine_km(lat, lng, shop.latitude, shop.longitude)
        if distance <= radius_km:
            shop.distance_km = round(distance, 2)
            nearby.append(shop)
    nearby.sort(key=lambda shop: shop.distance_km)
    return nearby
