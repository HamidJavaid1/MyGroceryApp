from django.contrib.auth import get_user_model
from django.contrib.auth.password_validation import validate_password
from rest_framework import serializers

from apps.shops.services import get_primary_shop

User = get_user_model()


class UserSerializer(serializers.ModelSerializer):
    shop_id = serializers.SerializerMethodField()
    shop_kind = serializers.SerializerMethodField()
    is_shop_approved = serializers.SerializerMethodField()

    class Meta:
        model = User
        fields = (
            "id",
            "username",
            "email",
            "first_name",
            "last_name",
            "role",
            "shop_name",
            "shop_id",
            "shop_kind",
            "is_shop_approved",
            "phone_number",
            "avatar",
            "notification_enabled",
            "inventory_alerts_enabled",
            "two_factor_enabled",
            "biometric_access_enabled",
            "is_verified_provider",
        )
        read_only_fields = ("id", "is_verified_provider", "shop_id", "shop_kind", "is_shop_approved")

    def get_shop_id(self, user):
        shop = get_primary_shop(user)
        return shop.id if shop else None

    def get_shop_kind(self, user):
        shop = get_primary_shop(user)
        return shop.kind if shop else None

    def get_is_shop_approved(self, user):
        shop = get_primary_shop(user)
        return bool(shop and shop.is_approved)


class RegisterSerializer(serializers.ModelSerializer):
    password = serializers.CharField(write_only=True, validators=[validate_password])
    phone_number = serializers.CharField()
    shop_name = serializers.CharField(required=False, allow_blank=True)

    class Meta:
        model = User
        fields = (
            "id",
            "username",
            "email",
            "password",
            "first_name",
            "last_name",
            "role",
            "shop_name",
            "phone_number",
            "firebase_uid",
        )
        read_only_fields = ("id",)

    def validate_role(self, value):
        allowed = {User.Role.CUSTOMER, User.Role.SHOPKEEPER, User.Role.WHOLESALER}
        if value not in allowed:
            raise serializers.ValidationError("Invalid account type for registration.")
        return value

    def validate(self, attrs):
        role = attrs.get("role", User.Role.CUSTOMER)
        if not attrs.get("phone_number"):
            raise serializers.ValidationError({"phone_number": "Phone number is required."})
        if role in (User.Role.SHOPKEEPER, User.Role.WHOLESALER) and not attrs.get("shop_name"):
            raise serializers.ValidationError(
                {"shop_name": "Shop name is required for shopkeepers and wholesalers."}
            )
        return attrs

    def create(self, validated_data):
        password = validated_data.pop("password")
        user = User(**validated_data)
        user.set_password(password)
        user.save()
        return user


class LoginSerializer(serializers.Serializer):
    username = serializers.CharField()
    password = serializers.CharField(write_only=True)


class FcmTokenSerializer(serializers.Serializer):
    token = serializers.CharField(max_length=4096)
