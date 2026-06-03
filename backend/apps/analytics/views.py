from django.db import models
from rest_framework import decorators, permissions, response, viewsets

from apps.users.permissions import role_permission


class AnalyticsViewSet(viewsets.ViewSet):
    def list(self, request):
        return response.Response({"message": "Analytics endpoint"})

    @decorators.action(detail=False, methods=["get"], permission_classes=[role_permission("shopkeeper")])
    def shopkeeper_dashboard(self, request):
        from apps.orders.models import Order
        from apps.shops.models import Shop
        
        user = request.user
        shop = Shop.objects.filter(owner=user).first()
        
        if not shop:
            return response.Response({"error": "No shop found for this user"}, status=404)
        
        orders = Order.objects.filter(shop=shop)
        total_orders = orders.count()
        pending_orders = orders.filter(status=Order.Status.PENDING).count()
        delivered_orders = orders.filter(status=Order.Status.DELIVERED).count()
        total_revenue = orders.filter(status=Order.Status.DELIVERED).aggregate(
            total=models.Sum("total")
        )["total"] or 0
        
        return response.Response({
            "total_orders": total_orders,
            "pending_orders": pending_orders,
            "delivered_orders": delivered_orders,
            "total_revenue": total_revenue,
            "shop_name": shop.name,
        })

    @decorators.action(detail=False, methods=["get"], permission_classes=[role_permission("wholesaler")])
    def wholesaler_dashboard(self, request):
        from apps.orders.models import BulkRequest, Quotation
        from apps.shops.models import Shop
        
        user = request.user
        shop = Shop.objects.filter(owner=user, kind=Shop.Kind.WHOLESALE).first()
        
        if not shop:
            return response.Response({"error": "No wholesale shop found for this user"}, status=404)
        
        bulk_requests = BulkRequest.objects.filter(product__shop=shop)
        total_bulk_requests = bulk_requests.count()
        open_requests = bulk_requests.filter(status=BulkRequest.Status.OPEN).count()
        quotations_sent = Quotation.objects.filter(wholesaler=user).count()
        
        return response.Response({
            "total_bulk_requests": total_bulk_requests,
            "open_requests": open_requests,
            "quotations_sent": quotations_sent,
            "shop_name": shop.name,
        })
