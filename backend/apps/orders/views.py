from rest_framework import decorators, permissions, response, status, viewsets

from apps.users.permissions import role_permission

from .models import BulkRequest, Order, Quotation
from .serializers import BulkRequestSerializer, OrderSerializer, QuotationSerializer


class OrderViewSet(viewsets.ModelViewSet):
    serializer_class = OrderSerializer
    queryset = Order.objects.select_related("customer", "shop").prefetch_related("items").order_by("-created_at")
    filterset_fields = ("status", "shop", "payment_status")
    search_fields = ("customer__username", "shop__name", "address")

    def get_permissions(self):
        if self.action in ("create",):
            return [permissions.IsAuthenticated()]
        if self.action in ("update", "partial_update", "destroy"):
            return [role_permission("customer")()]
        return [permissions.IsAuthenticated()]

    def get_queryset(self):
        user = self.request.user
        if user.is_market_admin:
            return self.queryset
        if user.role == "shopkeeper":
            return self.queryset.filter(shop__owner=user)
        return self.queryset.filter(customer=user)

    def perform_create(self, serializer):
        serializer.save(customer=self.request.user)

    @decorators.action(detail=True, methods=["post"], permission_classes=[role_permission("shopkeeper")])
    def transition(self, request, pk=None):
        order = self.get_object()
        if order.shop.owner_id != request.user.id and not request.user.is_market_admin:
            return response.Response(
                {"detail": "You can only update orders for your shop."}, status=status.HTTP_403_FORBIDDEN
            )
        new_status = request.data.get("status")
        if new_status not in Order.Status.values:
            return response.Response({"status": "Invalid order status."}, status=status.HTTP_400_BAD_REQUEST)
        order.status = new_status
        order.save(update_fields=["status", "updated_at"])
        return response.Response(self.get_serializer(order).data)


class BulkRequestViewSet(viewsets.ModelViewSet):
    serializer_class = BulkRequestSerializer
    queryset = BulkRequest.objects.select_related("customer", "product").order_by("-created_at")
    filterset_fields = ("status", "product")

    def get_queryset(self):
        user = self.request.user
        if user.is_market_admin:
            return self.queryset
        if user.role == "wholesaler":
            return self.queryset.filter(product__shop__owner=user)
        return self.queryset.filter(customer=user)

    def perform_create(self, serializer):
        serializer.save(customer=self.request.user)

    @decorators.action(
        detail=True,
        methods=["post"],
        permission_classes=[role_permission("wholesaler")],
        url_path="dispatch",
    )
    def mark_dispatched(self, request, pk=None):
        bulk_request = self.get_object()
        if bulk_request.status == BulkRequest.Status.CLOSED:
            return response.Response(
                {"detail": "This bulk request is already closed."},
                status=status.HTTP_400_BAD_REQUEST,
            )
        if bulk_request.status not in (BulkRequest.Status.ACCEPTED, BulkRequest.Status.QUOTED):
            return response.Response(
                {"detail": "Send a quotation and wait for acceptance, or dispatch a quoted request you own."},
                status=status.HTTP_400_BAD_REQUEST,
            )
        if not request.user.is_market_admin:
            owns_catalog = bulk_request.product.shop.owner_id == request.user.id
            if not owns_catalog:
                return response.Response(
                    {"detail": "You can only dispatch bulk requests for products in your catalog."},
                    status=status.HTTP_403_FORBIDDEN,
                )
            if bulk_request.status == BulkRequest.Status.QUOTED and not Quotation.objects.filter(
                bulk_request=bulk_request, wholesaler=request.user
            ).exists():
                return response.Response(
                    {"detail": "Send a quotation before dispatching this request."},
                    status=status.HTTP_403_FORBIDDEN,
                )
        bulk_request.status = BulkRequest.Status.CLOSED
        bulk_request.save(update_fields=["status"])
        Quotation.objects.filter(bulk_request=bulk_request, wholesaler=request.user).update(is_accepted=True)
        return response.Response(self.get_serializer(bulk_request).data)


class QuotationViewSet(viewsets.ModelViewSet):
    serializer_class = QuotationSerializer
    queryset = Quotation.objects.select_related("bulk_request", "wholesaler").order_by("-created_at")

    def get_permissions(self):
        if self.action == "create":
            return [role_permission("wholesaler")()]
        return [permissions.IsAuthenticated()]

    def get_queryset(self):
        user = self.request.user
        if user.is_market_admin:
            return self.queryset
        if user.role == "wholesaler":
            return self.queryset.filter(wholesaler=user)
        return self.queryset.filter(bulk_request__customer=user)

    def perform_create(self, serializer):
        quotation = serializer.save(wholesaler=self.request.user)
        quotation.bulk_request.status = BulkRequest.Status.QUOTED
        quotation.bulk_request.save(update_fields=["status"])

    @decorators.action(detail=True, methods=["post"])
    def accept(self, request, pk=None):
        quotation = self.get_object()
        if quotation.bulk_request.customer != request.user and not request.user.is_market_admin:
            return response.Response(status=status.HTTP_403_FORBIDDEN)
        quotation.is_accepted = True
        quotation.save(update_fields=["is_accepted"])
        quotation.bulk_request.status = BulkRequest.Status.ACCEPTED
        quotation.bulk_request.save(update_fields=["status"])
        return response.Response(self.get_serializer(quotation).data)
