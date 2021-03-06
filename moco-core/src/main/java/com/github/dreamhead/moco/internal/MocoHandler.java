package com.github.dreamhead.moco.internal;

import com.github.dreamhead.moco.RequestMatcher;
import com.github.dreamhead.moco.ResponseHandler;
import com.github.dreamhead.moco.setting.BaseSetting;
import com.google.common.eventbus.EventBus;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.util.List;

public class MocoHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final EventBus eventBus = new EventBus();

    private final List<BaseSetting> settings;
    private final RequestMatcher anyRequestMatcher;
    private final ResponseHandler anyResponseHandler;

    public MocoHandler(ActualHttpServer server) {
        this.settings = server.getSettings();
        this.anyRequestMatcher = server.getAnyRequestMatcher();
        this.anyResponseHandler = server.getAnyResponseHandler();
        this.eventBus.register(new MocoEventListener());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest message) throws Exception {
        eventBus.post(message);
        httpRequestReceived(ctx, message);
    }

    private void httpRequestReceived(ChannelHandlerContext ctx, FullHttpRequest request) {
        HttpResponse response = getResponse(request);
        eventBus.post(response);
        ctx.writeAndFlush(response);
        ctx.disconnect();
        ctx.close();
    }

    private FullHttpResponse getResponse(FullHttpRequest request) {
        try {
            return doGetHttpResponse(request);
        } catch (RuntimeException e) {
            eventBus.post(e);
            return defaultResponse(request, HttpResponseStatus.BAD_REQUEST);
        } catch (Exception e) {
            eventBus.post(e);
            return defaultResponse(request, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private FullHttpResponse doGetHttpResponse(FullHttpRequest request) {
        FullHttpResponse response = defaultResponse(request, HttpResponseStatus.OK);

        for (BaseSetting setting : settings) {
            if (setting.match(request)) {
                setting.writeToResponse(request, response);
                return response;
            }
        }

        if (anyResponseHandler != null) {
            if (anyRequestMatcher.match(request)) {
                anyResponseHandler.writeToResponse(request, response);
                return response;
            }
        }

        return defaultResponse(request, HttpResponseStatus.BAD_REQUEST);
    }

    private FullHttpResponse defaultResponse(HttpRequest request, HttpResponseStatus status) {
        return new DefaultFullHttpResponse(request.getProtocolVersion(), status);
    }
}
