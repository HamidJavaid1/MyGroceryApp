from django.contrib import admin

from .models import BulkRequest, Order, OrderItem, Quotation


class OrderItemInline(admin.TabularInline):
    model = OrderItem
    extra = 0


@admin.register(Order)
class OrderAdmin(admin.ModelAdmin):
    inlines = [OrderItemInline]
    list_display = ("id", "customer", "shop", "status", "total", "created_at")
    list_filter = ("status", "payment_method")
    search_fields = ("customer__username", "shop__name", "address")


@admin.register(BulkRequest)
class BulkRequestAdmin(admin.ModelAdmin):
    list_display = ("id", "customer", "product", "quantity", "status", "created_at")
    list_filter = ("status",)


@admin.register(Quotation)
class QuotationAdmin(admin.ModelAdmin):
    list_display = ("id", "bulk_request", "wholesaler", "price_per_unit", "delivery_fee", "is_accepted")
    list_filter = ("is_accepted",)
