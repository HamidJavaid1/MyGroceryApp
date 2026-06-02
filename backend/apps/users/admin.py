from django.contrib import admin
from django.contrib import messages
from django.contrib.auth.admin import UserAdmin
from django.utils.translation import ngettext

from .models import User


@admin.register(User)
class BazarLinkUserAdmin(UserAdmin):
    fieldsets = UserAdmin.fieldsets + (
        ("BazarLink", {"fields": ("role", "firebase_uid", "phone_number", "avatar", "fcm_token", "is_verified_provider")}),
    )
    list_display = ("username", "email", "role", "is_verified_provider", "is_staff", "is_active")
    list_filter = ("role", "is_verified_provider", "is_staff", "is_active")
    search_fields = ("username", "email", "phone_number", "firebase_uid")
    actions = ("approve_selected_users",)

    @admin.action(description="Approve selected users")
    def approve_selected_users(self, request, queryset):
        updated = queryset.update(is_verified_provider=True)
        self.message_user(
            request,
            ngettext(
                "%d user was approved successfully.",
                "%d users were approved successfully.",
                updated,
            )
            % updated,
            level=messages.SUCCESS,
        )
