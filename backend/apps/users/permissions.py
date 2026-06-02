from rest_framework.permissions import BasePermission


class IsAdminOrSelf(BasePermission):
    def has_object_permission(self, request, view, obj):
        return request.user.is_market_admin or obj == request.user


class HasRole(BasePermission):
    roles = ()

    def has_permission(self, request, view):
        return request.user.is_authenticated and (
            request.user.is_market_admin or request.user.role in self.roles
        )


def role_permission(*roles):
    return type("RolePermission", (HasRole,), {"roles": roles})
