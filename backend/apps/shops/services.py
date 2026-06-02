from .models import Shop


def get_primary_shop(user):
    if user is None or not getattr(user, "is_authenticated", False):
        return None
    return Shop.objects.filter(owner=user).order_by("id").first()
