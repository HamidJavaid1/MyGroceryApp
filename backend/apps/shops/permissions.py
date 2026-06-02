from rest_framework import permissions


class IsShopOwnerOrAdmin(permissions.BasePermission):
    def has_object_permission(self, request, view, obj):
        if request.user.is_market_admin:
            return True
        return obj.owner_id == request.user.id
