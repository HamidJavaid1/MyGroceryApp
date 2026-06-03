from django.conf import settings
from django.conf.urls.static import static
from django.contrib import admin
from django.http import JsonResponse
from django.urls import include, path
from django.views.decorators.csrf import csrf_exempt
from drf_spectacular.views import SpectacularAPIView, SpectacularSwaggerView
from rest_framework.routers import DefaultRouter

from apps.analytics.views import AnalyticsViewSet
from apps.notifications.views import NotificationViewSet
from apps.orders.views import BulkRequestViewSet, OrderViewSet, QuotationViewSet
from apps.products.views import CategoryViewSet, ProductViewSet, ReviewViewSet
from apps.shops.views import ShopViewSet
from apps.users.views import AuthViewSet, UserViewSet
from rest_framework_simplejwt.views import TokenRefreshView


@csrf_exempt
def api_root(request):
    return JsonResponse({
        "message": "BazarLink API",
        "version": "1.0.0",
        "endpoints": {
            "api": "/api/v1/",
            "docs": "/api/v1/docs/",
            "schema": "/api/v1/schema/",
            "admin": "/admin/"
        }
    })

# Temporarily disable router to isolate 400 error
# router = DefaultRouter()
# router.register("auth", AuthViewSet, basename="auth")
# router.register("users", UserViewSet, basename="users")
# router.register("categories", CategoryViewSet)
# router.register("products", ProductViewSet)
# router.register("reviews", ReviewViewSet)
# router.register("shops", ShopViewSet)
# router.register("orders", OrderViewSet)
# router.register("bulk-requests", BulkRequestViewSet)
# router.register("quotations", QuotationViewSet)
# router.register("notifications", NotificationViewSet)
# router.register("analytics", AnalyticsViewSet, basename="analytics")

urlpatterns = [
    path("", api_root),
    # path("admin/", admin.site.urls),
    # path("api/v1/auth/refresh/", TokenRefreshView.as_view(), name="token_refresh"),
    # path("api/v1/", include(router.urls)),
    # path("api/v1/schema/", SpectacularAPIView.as_view(), name="schema"),
    # path("api/v1/docs/", SpectacularSwaggerView.as_view(url_name="schema"), name="swagger-ui"),
] + static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
