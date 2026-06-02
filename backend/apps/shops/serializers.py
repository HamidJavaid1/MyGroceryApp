from rest_framework import serializers

from .models import Shop


class ShopSerializer(serializers.ModelSerializer):
    latitude = serializers.FloatField()
    longitude = serializers.FloatField()
    lat = serializers.SerializerMethodField()
    lng = serializers.SerializerMethodField()
    distance_km = serializers.SerializerMethodField()

    class Meta:
        model = Shop
        fields = (
            "id",
            "owner",
            "name",
            "kind",
            "description",
            "address",
            "latitude",
            "longitude",
            "lat",
            "lng",
            "opening_time",
            "closing_time",
            "is_approved",
            "distance_km",
        )
        read_only_fields = ("id", "owner", "is_approved", "lat", "lng", "distance_km")

    def validate(self, attrs):
        if "latitude" in attrs and not -90 <= attrs["latitude"] <= 90:
            raise serializers.ValidationError({"latitude": "Latitude must be between -90 and 90."})
        if "longitude" in attrs and not -180 <= attrs["longitude"] <= 180:
            raise serializers.ValidationError({"longitude": "Longitude must be between -180 and 180."})
        return attrs

    def get_lat(self, obj):
        return float(obj.latitude)

    def get_lng(self, obj):
        return float(obj.longitude)

    def get_distance_km(self, obj):
        distance_km = getattr(obj, "distance_km", None)
        return distance_km
