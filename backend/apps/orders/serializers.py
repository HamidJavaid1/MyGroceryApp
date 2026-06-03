from django.db import transaction
from rest_framework import serializers

from apps.products.models import Product
from apps.shops.models import Shop
from apps.users.models import User

from .models import BulkRequest, Order, OrderItem, Quotation


class OrderItemSerializer(serializers.ModelSerializer):
    class Meta:
        model = OrderItem
        fields = ("id", "product", "quantity", "unit_price", "line_total")
        read_only_fields = ("id", "unit_price", "line_total")


class OrderSerializer(serializers.ModelSerializer):
    class Meta:
        model = Order
        fields = (
            "id",
            "customer",
            "shop",
            "status",
            "address",
            "payment_method",
            "payment_status",
            "subtotal",
            "delivery_fee",
            "total",
            "created_at",
            "updated_at",
        )
        read_only_fields = ("id", "customer", "subtotal", "total", "created_at", "updated_at")

    def validate(self, attrs):
        request = self.context.get("request")
        # Only validate on create, not on list/retrieve
        if self.instance is not None:
            return attrs
        if request and not request.user.is_market_admin and request.user.role != User.Role.CUSTOMER:
            raise serializers.ValidationError({"detail": "Only customers can place retail orders."})
        shop = attrs.get("shop")
        if shop and not shop.is_approved:
            raise serializers.ValidationError({"shop": "This shop is not available for orders yet."})
        if shop and shop.kind != Shop.Kind.RETAIL:
            raise serializers.ValidationError({"shop": "Retail orders must target a retail shop."})
        return attrs

    def validate_items(self, items):
        if not items:
            raise serializers.ValidationError("Order must include at least one item.")
        shop = self.initial_data.get("shop")
        for item in items:
            product = item["product"]
            if shop is not None and product.shop_id != int(shop):
                raise serializers.ValidationError(
                    {"items": f"{product.name} does not belong to the selected shop."}
                )
            if item["quantity"] <= 0:
                raise serializers.ValidationError("Quantity must be greater than zero.")
            if product.stock_quantity < item["quantity"]:
                raise serializers.ValidationError(f"{product.name} does not have enough stock.")
        return items

    @transaction.atomic
    def create(self, validated_data):
        items = validated_data.pop("items")
        order = Order.objects.create(**validated_data)
        for item in items:
            product = Product.objects.select_for_update().get(pk=item["product"].pk)
            quantity = item["quantity"]
            product.stock_quantity -= quantity
            product.save(update_fields=["stock_quantity", "updated_at"])
            OrderItem.objects.create(order=order, product=product, quantity=quantity, unit_price=product.price)
        order.recalculate()
        return order


class BulkRequestSerializer(serializers.ModelSerializer):
    product_name = serializers.CharField(source="product.name", read_only=True)
    can_dispatch = serializers.SerializerMethodField()

    class Meta:
        model = BulkRequest
        fields = (
            "id",
            "customer",
            "product",
            "product_name",
            "quantity",
            "delivery_address",
            "notes",
            "status",
            "can_dispatch",
            "created_at",
        )
        read_only_fields = ("id", "customer", "status", "can_dispatch", "created_at")

    def get_can_dispatch(self, obj):
        request = self.context.get("request")
        if request is None or not request.user.is_authenticated:
            return False
        if request.user.is_market_admin:
            return obj.status in (BulkRequest.Status.ACCEPTED, BulkRequest.Status.QUOTED)
        if request.user.role != "wholesaler" or obj.product.shop.owner_id != request.user.id:
            return False
        if obj.status == BulkRequest.Status.ACCEPTED:
            return True
        if obj.status == BulkRequest.Status.QUOTED:
            return obj.quotations.filter(wholesaler=request.user).exists()
        return False

    def validate(self, attrs):
        request = self.context.get("request")
        user = request.user if request else None
        product = attrs["product"]
        quantity = attrs["quantity"]
        if user and not user.is_market_admin and user.role == User.Role.WHOLESALER:
            raise serializers.ValidationError({"detail": "Wholesalers receive bulk requests; they cannot create them."})
        if user and not user.is_market_admin and user.role not in (User.Role.CUSTOMER, User.Role.SHOPKEEPER):
            raise serializers.ValidationError({"detail": "Only customers and shopkeepers can request bulk stock."})
        if product.shop.kind != Shop.Kind.WHOLESALE:
            raise serializers.ValidationError({"product": "Bulk orders must target a wholesaler product."})
        if not product.shop.is_approved:
            raise serializers.ValidationError({"product": "This wholesaler is not approved yet."})
        if user and product.shop.owner_id == user.id:
            raise serializers.ValidationError({"product": "You cannot request bulk from your own catalog."})
        if not product.is_bulk_available:
            raise serializers.ValidationError({"product": "This product is not available for bulk orders."})
        if product.min_bulk_quantity and quantity < product.min_bulk_quantity:
            raise serializers.ValidationError({"quantity": f"Minimum bulk quantity is {product.min_bulk_quantity}."})
        return attrs


class QuotationSerializer(serializers.ModelSerializer):
    total = serializers.DecimalField(max_digits=12, decimal_places=2, read_only=True)
    bulk_request_product_name = serializers.CharField(source="bulk_request.product.name", read_only=True)
    wholesaler_name = serializers.CharField(source="wholesaler.get_full_name", read_only=True)

    class Meta:
        model = Quotation
        fields = (
            "id",
            "bulk_request",
            "bulk_request_product_name",
            "wholesaler",
            "wholesaler_name",
            "price_per_unit",
            "delivery_fee",
            "valid_until",
            "message",
            "is_accepted",
            "total",
            "created_at",
        )
        read_only_fields = ("id", "wholesaler", "is_accepted", "total", "created_at")

    def validate_bulk_request(self, bulk_request):
        request = self.context.get("request")
        if request is None or request.user.is_market_admin:
            return bulk_request
        if bulk_request.product.shop.owner_id != request.user.id:
            raise serializers.ValidationError("You can only quote bulk requests for products in your catalog.")
        return bulk_request
