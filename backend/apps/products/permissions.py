from rest_framework import permissions


class IsProductShopOwnerOrAdmin(permissions.BasePermission):
    def has_object_permission(self, request, view, obj):
        if request.user.is_market_admin:
            return True
        return obj.shop.owner_id == request.user.id
