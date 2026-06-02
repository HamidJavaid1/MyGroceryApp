from django.contrib.auth import authenticate, get_user_model
from rest_framework import decorators, mixins, permissions, response, status, viewsets
from rest_framework_simplejwt.tokens import RefreshToken

from apps.shops.models import Shop

from .permissions import IsAdminOrSelf
from .serializers import FcmTokenSerializer, LoginSerializer, RegisterSerializer, UserSerializer

User = get_user_model()


class AuthViewSet(viewsets.GenericViewSet):
    permission_classes = [permissions.AllowAny]

    def ensure_seller_shop(self, user):
        if user.role not in (User.Role.SHOPKEEPER, User.Role.WHOLESALER):
            return None
        shop_name = (user.shop_name or user.username or "Shop").strip()
        kind = Shop.Kind.RETAIL if user.role == User.Role.SHOPKEEPER else Shop.Kind.WHOLESALE
        shop = Shop.objects.filter(owner=user, name=shop_name).order_by("id").first()
        if shop is None:
            shop = Shop.objects.filter(owner=user).order_by("id").first()
            if shop is None:
                shop = Shop.objects.create(
                    owner=user,
                    name=shop_name,
                    kind=kind,
                    description="",
                    address=shop_name,
                    latitude=0,
                    longitude=0,
                    is_approved=True,
                )
        if shop.name != shop_name or shop.kind != kind or shop.address != shop_name:
            shop.name = shop_name
            shop.kind = kind
            shop.address = shop_name
            shop.save(update_fields=["name", "kind", "address"])
        return shop

    def get_serializer_class(self):
        if self.action == "login":
            return LoginSerializer
        if self.action == "register":
            return RegisterSerializer
        return super().get_serializer_class()

    @decorators.action(detail=False, methods=["post"], serializer_class=LoginSerializer)
    def login(self, request):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = authenticate(
            request=request,
            username=serializer.validated_data["username"],
            password=serializer.validated_data["password"],
        )
        if user is None:
            return response.Response({"detail": "Invalid username or password."}, status=status.HTTP_400_BAD_REQUEST)
        self.ensure_seller_shop(user)
        refresh = RefreshToken.for_user(user)
        return response.Response(
            {
                "user": UserSerializer(user, context={"request": request}).data,
                "access": str(refresh.access_token),
                "refresh": str(refresh),
            },
            status=status.HTTP_200_OK,
        )

    @decorators.action(detail=False, methods=["post"], serializer_class=RegisterSerializer)
    def register(self, request):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()
        self.ensure_seller_shop(user)
        refresh = RefreshToken.for_user(user)
        return response.Response(
            {
                "user": UserSerializer(user, context={"request": request}).data,
                "access": str(refresh.access_token),
                "refresh": str(refresh),
            },
            status=status.HTTP_201_CREATED,
        )


class UserViewSet(
    mixins.RetrieveModelMixin,
    mixins.UpdateModelMixin,
    mixins.ListModelMixin,
    viewsets.GenericViewSet,
):
    serializer_class = UserSerializer
    queryset = User.objects.all().order_by("-date_joined")
    permission_classes = [permissions.IsAuthenticated, IsAdminOrSelf]
    search_fields = ("username", "email", "phone_number")
    filterset_fields = ("role", "is_verified_provider")

    def get_queryset(self):
        if self.request.user.is_market_admin:
            return self.queryset
        return self.queryset.filter(id=self.request.user.id)

    @decorators.action(detail=False, methods=["get", "patch"], url_path="me")
    def me(self, request):
        if request.method == "PATCH":
            serializer = self.get_serializer(request.user, data=request.data, partial=True)
            serializer.is_valid(raise_exception=True)
            serializer.save()
            return response.Response(serializer.data)
        return response.Response(self.get_serializer(request.user).data)

    @decorators.action(detail=False, methods=["post"], serializer_class=FcmTokenSerializer, url_path="fcm-token")
    def fcm_token(self, request):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        request.user.fcm_token = serializer.validated_data["token"]
        request.user.save(update_fields=["fcm_token"])
        return response.Response(status=status.HTTP_204_NO_CONTENT)
