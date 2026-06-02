from rest_framework import decorators, permissions, response, status, viewsets

from apps.users.permissions import role_permission

from .geo import shops_within_radius
from .models import Shop
from .permissions import IsShopOwnerOrAdmin
from .serializers import ShopSerializer
from .services import get_primary_shop


class ShopViewSet(viewsets.ModelViewSet):
    serializer_class = ShopSerializer
    queryset = Shop.objects.select_related("owner").order_by("-created_at")
    filterset_fields = ("kind", "is_approved")
    search_fields = ("name", "address")

    def get_permissions(self):
        if self.action in ("update", "partial_update", "destroy"):
            return [role_permission("shopkeeper", "wholesaler")(), IsShopOwnerOrAdmin()]
        if self.action == "create":
            return [role_permission("shopkeeper", "wholesaler")()]
        return [permissions.IsAuthenticated()]

    def perform_create(self, serializer):
        serializer.save(owner=self.request.user)

    def get_queryset(self):
        qs = self.queryset
        user = self.request.user
        if user.is_market_admin:
            return qs
        browse = self._truthy(self.request.query_params.get("browse"))
        if browse:
            return qs.filter(is_approved=True)
        return (qs.filter(is_approved=True) | qs.filter(owner=user)).distinct()

    def _truthy(self, value):
        return str(value or "").lower() in ("1", "true", "yes")

    @decorators.action(detail=False, methods=["get"], url_path="my-shop")
    def my_shop(self, request):
        shop = get_primary_shop(request.user)
        if shop is None:
            return response.Response(
                {"detail": "No shop found for this account."}, status=status.HTTP_404_NOT_FOUND
            )
        return response.Response(self.get_serializer(shop).data)

    @decorators.action(detail=False, methods=["get"])
    def nearby(self, request):
        try:
            lat = float(request.query_params.get("lat"))
            lng = float(request.query_params.get("lng"))
            radius_km = float(request.query_params.get("radius_km", 5))
        except (TypeError, ValueError):
            return response.Response(
                {"detail": "lat, lng, and radius_km must be numeric."}, status=status.HTTP_400_BAD_REQUEST
            )
        shops = list(self.get_queryset().filter(is_approved=True))
        kind = request.query_params.get("kind")
        if kind in (Shop.Kind.RETAIL, Shop.Kind.WHOLESALE):
            shops = [shop for shop in shops if shop.kind == kind]
        nearby_shops = shops_within_radius(shops, lat, lng, radius_km)
        return response.Response(self.get_serializer(nearby_shops, many=True).data)
