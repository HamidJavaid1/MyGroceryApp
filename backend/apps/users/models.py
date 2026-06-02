from django.contrib.auth.models import AbstractUser
from django.db import models


class User(AbstractUser):
    class Role(models.TextChoices):
        CUSTOMER = "customer", "Customer"
        SHOPKEEPER = "shopkeeper", "Shopkeeper"
        WHOLESALER = "wholesaler", "Wholesaler"
        ADMIN = "admin", "Admin"

    role = models.CharField(max_length=20, choices=Role.choices, default=Role.CUSTOMER)
    shop_name = models.CharField(max_length=160, blank=True, default="")
    firebase_uid = models.CharField(max_length=128, blank=True, unique=True, null=True)
    phone_number = models.CharField(max_length=32, blank=True)
    avatar = models.ImageField(upload_to="avatars/", blank=True, null=True)
    fcm_token = models.TextField(blank=True)
    notification_enabled = models.BooleanField(default=True)
    inventory_alerts_enabled = models.BooleanField(default=True)
    two_factor_enabled = models.BooleanField(default=False)
    biometric_access_enabled = models.BooleanField(default=False)
    is_verified_provider = models.BooleanField(default=False)

    @property
    def is_market_admin(self):
        return self.is_staff or self.role == self.Role.ADMIN
