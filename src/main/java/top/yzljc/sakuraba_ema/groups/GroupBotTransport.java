package top.yzljc.sakuraba_ema.groups;

import top.yzljc.atribot.service.request.HttpService;

interface GroupBotTransport {

    Response post(String url, String json, String accessToken);

    Response delete(String url, String accessToken);

    static GroupBotTransport live() {
        return new GroupBotTransport() {
            @Override
            public Response post(String url, String json, String accessToken) {
                HttpService.PostResult result = HttpService.postJsonDetailed(
                        url, json, "Authorization", "QQBot " + accessToken);
                return new Response(result.status(), result.body());
            }

            @Override
            public Response delete(String url, String accessToken) {
                HttpService.GetResult result = HttpService.deleteRequestDetailed(
                        url, "Authorization", "QQBot " + accessToken);
                return new Response(result.status(), result.body());
            }
        };
    }

    record Response(int status, String body) {
        boolean successful() {
            return status >= 200 && status < 300;
        }
    }
}
