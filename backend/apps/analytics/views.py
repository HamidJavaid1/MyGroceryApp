from rest_framework import response, viewsets


class AnalyticsViewSet(viewsets.ViewSet):
    def list(self, request):
        return response.Response({"message": "Analytics endpoint"})
