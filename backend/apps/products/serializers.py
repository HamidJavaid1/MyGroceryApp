from rest_framework import serializers

from apps.shops.models import Shop
from apps.shops.services import get_primary_shop
from apps.users.models import User

from .models import Category, Product, ProductImage, Review


class CategorySerializer(serializers.ModelSerializer):
    class Meta:
        model = Category
        fields = ("id", "name", "slug", "image")


class ProductImageSerializer(serializers.ModelSerializer):
    class Meta:
        model = ProductImage
        fields = ("id", "image", "alt_text", "sort_order")


class ReviewSerializer(serializers.ModelSerializer):
    user_name = serializers.CharField(source="user.get_full_name", read_only=True)

    class Meta:
        model = Review
        fields = ("id", "product", "user", "user_name", "rating", "comment", "created_at")
        read_only_fields = ("id", "user", "created_at")


class ProductSerializer(serializers.ModelSerializer):
    images = ProductImageSerializer(many=True, required=False)
    rating = serializers.FloatField(read_only=True)
    shop_name = serializers.CharField(source="shop.name", read_only=True)
    category_name = serializers.CharField(source="category.name", read_only=True)

    class Meta:
        model = Product
        fields = (
            "id",
            "shop",
            "shop_name",
            "category",
            "category_name",
            "name",
            "description",
            "unit",
            "price",
            "compare_at_price",
            "stock_quantity",
            "low_stock_threshold",
            "is_bulk_available",
            "min_bulk_quantity",
            "is_active",
            "images",
            "rating",
            "created_at",
            "updated_at",
        )
        read_only_fields = ("id", "rating", "created_at", "updated_at")

    def validate_shop(self, shop):
        request = self.context.get("request")
        if request is None or request.user.is_market_admin:
            return shop
        if shop.owner_id != request.user.id:
            raise serializers.ValidationError("You can only manage products in your own shop.")
        if request.user.role == User.Role.SHOPKEEPER and shop.kind != Shop.Kind.RETAIL:
            raise serializers.ValidationError("Shopkeepers can only sell from retail shops.")
        if request.user.role == User.Role.WHOLESALER and shop.kind != Shop.Kind.WHOLESALE:
            raise serializers.ValidationError("Wholesalers can only sell from wholesale shops.")
        return shop

    def validate(self, attrs):
        request = self.context.get("request")
        if request is None or self.instance is not None:
            return attrs
        user = request.user
        shop = attrs.get("shop") or get_primary_shop(user)
        if shop is None:
            raise serializers.ValidationError({"shop": "No shop is linked to your account."})
        attrs["shop"] = shop
        if attrs.get("stock_quantity") is None:
            attrs["stock_quantity"] = 0
        if user.role == User.Role.WHOLESALER:
            attrs.setdefault("is_bulk_available", True)
            attrs.setdefault("min_bulk_quantity", attrs.get("min_bulk_quantity") or 1)
        else:
            attrs.setdefault("is_bulk_available", False)
        return attrs

    def create(self, validated_data):
        images = validated_data.pop("images", [])
        product = Product.objects.create(**validated_data)
        for image in images:
            ProductImage.objects.create(product=product, **image)
        return product

    def update(self, instance, validated_data):
        images = validated_data.pop("images", None)
        instance = super().update(instance, validated_data)
        if images is not None:
            instance.images.all().delete()
            for image in images:
                ProductImage.objects.create(product=instance, **image)
        return instance
