from django.db.models import Avg
from rest_framework import permissions, viewsets

from apps.shops.models import Shop
from apps.shops.services import get_primary_shop
from apps.users.models import User
from apps.users.permissions import role_permission

from .models import Category, Product, Review
from .permissions import IsProductShopOwnerOrAdmin
from .serializers import CategorySerializer, ProductSerializer, ReviewSerializer


class CategoryViewSet(viewsets.ModelViewSet):
    queryset = Category.objects.order_by("name")
    serializer_class = CategorySerializer
    permission_classes = [permissions.IsAuthenticatedOrReadOnly]
    search_fields = ("name",)

    def get_permissions(self):
        if self.action in ("create", "update", "partial_update", "destroy"):
            return [permissions.IsAdminUser()]
        return super().get_permissions()


class ProductViewSet(viewsets.ModelViewSet):
    queryset = (
        Product.objects.select_related("shop", "category")
        .prefetch_related("images", "reviews")
        .order_by("-created_at")
    )
    serializer_class = ProductSerializer
    filterset_fields = ("category", "shop", "is_active", "is_bulk_available")
    search_fields = ("name", "description", "shop__name", "category__name")
    ordering_fields = ("price", "created_at", "stock_quantity")

    def get_permissions(self):
        if self.action in ("create", "update", "partial_update", "destroy"):
            return [role_permission("shopkeeper", "wholesaler")(), IsProductShopOwnerOrAdmin()]
        return [permissions.IsAuthenticated()]

    def _truthy(self, value):
        return str(value or "").lower() in ("1", "true", "yes")

    def get_queryset(self):
        user = self.request.user
        qs = self.queryset
        mine = self._truthy(self.request.query_params.get("mine"))
        bulk = self.request.query_params.get("is_bulk_available") == "true"
        shop_id = self.request.query_params.get("shop")

        if user.is_market_admin:
            pass
        elif mine:
            qs = qs.filter(shop__owner=user)
        elif shop_id:
            qs = qs.filter(shop_id=shop_id, shop__is_approved=True, is_active=True)
        elif bulk and user.role in (User.Role.SHOPKEEPER, User.Role.CUSTOMER):
            qs = qs.filter(
                shop__is_approved=True,
                is_active=True,
                shop__kind=Shop.Kind.WHOLESALE,
                is_bulk_available=True,
            ).exclude(shop__owner=user)
        elif user.role in (User.Role.SHOPKEEPER, User.Role.WHOLESALER):
            qs = qs.filter(shop__owner=user)
        else:
            qs = qs.filter(shop__is_approved=True, is_active=True, shop__kind=Shop.Kind.RETAIL)

        min_price = self.request.query_params.get("min_price")
        max_price = self.request.query_params.get("max_price")
        rating = self.request.query_params.get("rating")

        if min_price:
            qs = qs.filter(price__gte=min_price)
        if max_price:
            qs = qs.filter(price__lte=max_price)
        if rating:
            qs = qs.annotate(avg_rating=Avg("reviews__rating")).filter(avg_rating__gte=rating)
        return qs

    def perform_create(self, serializer):
        serializer.save()


class ReviewViewSet(viewsets.ModelViewSet):
    queryset = Review.objects.select_related("product", "user").order_by("-created_at")
    serializer_class = ReviewSerializer
    permission_classes = [permissions.IsAuthenticated]
    filterset_fields = ("product", "rating")

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)
